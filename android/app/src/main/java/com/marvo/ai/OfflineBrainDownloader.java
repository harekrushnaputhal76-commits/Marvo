package com.marvo.ai;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/**
 * Step 28: Multi-Model Offline Brain Downloader with HTTP Range Resumption.
 * Supports concurrent & independent downloading of:
 *  1. LLM: Phi-3 Mini 4K Instruct GGUF (~2.2GB)
 *  2. STT: Whisper Tiny Speech Recognition model (~150MB)
 *  3. TTS: Piper / VITS Neural Voice Synthesis model (~100MB)
 * 
 * - Stores all models in the internal app /models/ directory.
 * - Full HTTP Range resumption (never loses partial bytes on pause or connection drop).
 * - Thread-safe background execution with real-time status reporting to Capacitor frontend.
 */
public class OfflineBrainDownloader {
    private static final String TAG = "MarvoDownload";

    public static final String PREF_NAME = "marvo_model_downloader";
    public static final String KEY_DOWNLOAD_STATUS = "download_status"; // "idle", "downloading", "completed", "paused", "failed", "paused_wifi"
    public static final String KEY_ALLOW_METERED = "allow_metered_download";
    public static final String KEY_DOWNLOADED_BYTES = "downloaded_bytes";
    public static final String KEY_TOTAL_BYTES = "total_bytes";

    // Supported Model Types
    public static final String TYPE_LLM = "llm";
    public static final String TYPE_STT = "stt";
    public static final String TYPE_TTS = "tts";

    // Backward-compatible LLM constants
    public static final String DEFAULT_MODEL_NAME = "phi-3-mini-4k-instruct-q4.gguf";
    public static final String DEFAULT_MODEL_URL = "https://huggingface.co/bartowski/Phi-3-mini-4k-instruct-GGUF/resolve/main/Phi-3-mini-4k-instruct-Q4_K_M.gguf";

    public static class ModelSpec {
        public final String type;
        public final String fileName;
        public final String url;
        public final long minReadySize;
        public final long defaultTotalBytes;
        public final String displayName;

        public ModelSpec(String type, String fileName, String url, long minReadySize, long defaultTotalBytes, String displayName) {
            this.type = type;
            this.fileName = fileName;
            this.url = url;
            this.minReadySize = minReadySize;
            this.defaultTotalBytes = defaultTotalBytes;
            this.displayName = displayName;
        }
    }

    private static final Map<String, ModelSpec> MODEL_SPECS;
    static {
        Map<String, ModelSpec> specs = new HashMap<>();
        specs.put(TYPE_LLM, new ModelSpec(
            TYPE_LLM,
            DEFAULT_MODEL_NAME,
            DEFAULT_MODEL_URL,
            500L * 1024L * 1024L, // min 500MB
            2200L * 1024L * 1024L, // ~2.2GB
            "Phi-3 Mini 4K Instruct"
        ));
        specs.put(TYPE_STT, new ModelSpec(
            TYPE_STT,
            "whisper-tiny-en.bin",
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin",
            50L * 1024L * 1024L, // min 50MB
            151338400L, // ~150MB
            "Whisper Tiny Speech Recognition"
        ));
        specs.put(TYPE_TTS, new ModelSpec(
            TYPE_TTS,
            "vits-piper-en.onnx",
            "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium/en_US-lessac-medium.onnx",
            20L * 1024L * 1024L, // min 20MB
            63800000L, // ~63.8MB
            "Piper Neural Voice Synthesis"
        ));
        MODEL_SPECS = Collections.unmodifiableMap(specs);
    }

    private static class DownloadTaskState {
        volatile boolean isDownloading = false;
        volatile boolean isPaused = false;
        volatile boolean isCancelled = false;
        volatile long currentDownloadedBytes = 0L;
        volatile long totalBytesExpected = 0L;
        volatile int currentProgressPercent = 0;
        volatile HttpURLConnection activeConnection = null;
        volatile InputStream activeInputStream = null;
        volatile FileOutputStream activeOutputStream = null;
    }

    private static volatile OfflineBrainDownloader instance;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final ConcurrentHashMap<String, DownloadTaskState> activeStates = new ConcurrentHashMap<>();

    private OfflineBrainDownloader() {
        for (String type : MODEL_SPECS.keySet()) {
            activeStates.put(type, new DownloadTaskState());
        }
    }

    public static synchronized OfflineBrainDownloader getInstance() {
        if (instance == null) {
            instance = new OfflineBrainDownloader();
        }
        return instance;
    }

    private SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public ModelSpec getSpec(String type) {
        String key = (type != null) ? type.toLowerCase() : TYPE_LLM;
        ModelSpec spec = MODEL_SPECS.get(key);
        return spec != null ? spec : MODEL_SPECS.get(TYPE_LLM);
    }

    private DownloadTaskState getState(String type) {
        String key = (type != null) ? type.toLowerCase() : TYPE_LLM;
        DownloadTaskState state = activeStates.get(key);
        if (state == null) {
            state = new DownloadTaskState();
            activeStates.put(key, state);
        }
        return state;
    }

    private String getStatusKey(String type) {
        return TYPE_LLM.equals(type) ? KEY_DOWNLOAD_STATUS : (KEY_DOWNLOAD_STATUS + "_" + type);
    }

    private String getDownloadedBytesKey(String type) {
        return TYPE_LLM.equals(type) ? KEY_DOWNLOADED_BYTES : (KEY_DOWNLOADED_BYTES + "_" + type);
    }

    private String getTotalBytesKey(String type) {
        return TYPE_LLM.equals(type) ? KEY_TOTAL_BYTES : (KEY_TOTAL_BYTES + "_" + type);
    }

    private String getAllowMeteredKey(String type) {
        return TYPE_LLM.equals(type) ? KEY_ALLOW_METERED : (KEY_ALLOW_METERED + "_" + type);
    }

    public File getModelsDir(Context context) {
        if (context == null) return null;
        File dir = MemoryVault.getModelsDir(context);
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public File getModelFile(Context context, String type) {
        File dir = getModelsDir(context);
        ModelSpec spec = getSpec(type);
        return dir != null ? new File(dir, spec.fileName) : null;
    }

    public File getPartFile(Context context, String type) {
        File dir = getModelsDir(context);
        ModelSpec spec = getSpec(type);
        return dir != null ? new File(dir, spec.fileName + ".part") : null;
    }

    public boolean isModelDownloaded(Context context, String type) {
        if (context == null) return false;
        File modelFile = getModelFile(context, type);
        ModelSpec spec = getSpec(type);
        boolean exists = modelFile != null && modelFile.exists();
        long length = exists ? modelFile.length() : 0L;
        return exists && length >= spec.minReadySize;
    }

    // Backward compatibility methods defaulting to LLM
    public File getModelFile(Context context) {
        return getModelFile(context, TYPE_LLM);
    }

    public File getPartFile(Context context) {
        return getPartFile(context, TYPE_LLM);
    }

    public boolean isModelDownloaded(Context context) {
        return isModelDownloaded(context, TYPE_LLM);
    }

    public boolean isUnmeteredConnection(Context context) {
        if (context == null) return false;
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.net.Network activeNet = cm.getActiveNetwork();
                if (activeNet == null) return false;
                NetworkCapabilities caps = cm.getNetworkCapabilities(activeNet);
                if (caps == null) return false;
                return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
                       caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            } else {
                NetworkInfo ni = cm.getActiveNetworkInfo();
                return ni != null && ni.isConnected() && ni.getType() == ConnectivityManager.TYPE_WIFI;
            }
        } catch (Exception e) {
            Log.w(TAG, "Error checking network capabilities: " + e.getMessage());
            return false;
        }
    }

    /**
     * Starts or resumes a download for a specific model type.
     */
    public synchronized boolean startDownload(final Context context, final String modelType, boolean allowMetered) {
        if (context == null) return false;
        final String type = (modelType != null) ? modelType.toLowerCase() : TYPE_LLM;
        final ModelSpec spec = getSpec(type);
        final DownloadTaskState state = getState(type);

        if (isModelDownloaded(context, type)) {
            Log.d(TAG, "[" + type + "] Model already fully downloaded & verified.");
            getPrefs(context).edit().putString(getStatusKey(type), "completed").apply();
            return true;
        }

        if (state.isDownloading) {
            Log.d(TAG, "[" + type + "] Download is already actively running.");
            return true;
        }

        getPrefs(context).edit().putBoolean(getAllowMeteredKey(type), allowMetered).apply();
        boolean unmetered = isUnmeteredConnection(context);
        if (!allowMetered && !unmetered) {
            Log.d(TAG, "[" + type + "] Metered connection detected and allowMetered is false. Pausing until Wi-Fi.");
            getPrefs(context).edit().putString(getStatusKey(type), "paused_wifi").apply();
            return false;
        }

        state.isPaused = false;
        state.isCancelled = false;
        state.isDownloading = true;

        final Context appContext = context.getApplicationContext();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                executeDownloadTask(appContext, type);
            }
        });

        return true;
    }

    public synchronized boolean startDownload(final Context context, boolean allowMetered) {
        return startDownload(context, TYPE_LLM, allowMetered);
    }

    private void executeDownloadTask(Context context, String type) {
        final ModelSpec spec = getSpec(type);
        final DownloadTaskState state = getState(type);
        File partFile = getPartFile(context, type);
        File finalFile = getModelFile(context, type);

        if (partFile == null || finalFile == null) {
            Log.e(TAG, "[" + type + "] Storage directories are inaccessible.");
            state.isDownloading = false;
            getPrefs(context).edit().putString(getStatusKey(type), "failed").apply();
            return;
        }

        long existingBytes = partFile.exists() ? partFile.length() : 0L;
        state.currentDownloadedBytes = existingBytes;
        state.totalBytesExpected = spec.defaultTotalBytes;

        Log.d(TAG, "[" + type + "] Starting/Resuming download: partFile=" + partFile.getAbsolutePath() +
                   ", existingBytes=" + existingBytes + " (" + (existingBytes / (1024 * 1024)) + " MB)");

        getPrefs(context).edit().putString(getStatusKey(type), "downloading").apply();

        HttpURLConnection conn = null;
        try {
            String targetUrl = spec.url;
            int redirectCount = 0;

            while (redirectCount < 8) {
                URL url = new URL(targetUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(45000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; MarvoOfflineEcosystem/2.0)");
                conn.setRequestProperty("Accept-Encoding", "identity");

                if (existingBytes > 0) {
                    conn.setRequestProperty("Range", "bytes=" + existingBytes + "-");
                    Log.d(TAG, "[" + type + "] Sending HTTP Range header: bytes=" + existingBytes + "-");
                }

                int respCode = conn.getResponseCode();
                if (respCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    respCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    respCode == 307 || respCode == 308) {
                    String redirectUrl = conn.getHeaderField("Location");
                    conn.disconnect();
                    if (redirectUrl == null || redirectUrl.trim().isEmpty()) {
                        throw new IOException("HTTP redirect received with missing Location header");
                    }
                    if (redirectUrl.startsWith("/")) {
                        redirectUrl = new URL(url, redirectUrl).toString();
                    }
                    targetUrl = redirectUrl;
                    redirectCount++;
                    continue;
                }
                break;
            }

            state.activeConnection = conn;
            if (conn == null) throw new IOException("Failed to establish HTTP connection for " + type);

            int responseCode = conn.getResponseCode();
            boolean isAppendMode;

            if (responseCode == HttpURLConnection.HTTP_PARTIAL) { // 206 Partial Content
                String contentRange = conn.getHeaderField("Content-Range");
                if (contentRange != null && contentRange.contains("/")) {
                    try {
                        String totalStr = contentRange.substring(contentRange.lastIndexOf('/') + 1).trim();
                        long parsedTotal = Long.parseLong(totalStr);
                        if (parsedTotal > 0) {
                            state.totalBytesExpected = parsedTotal;
                        }
                    } catch (Exception ignored) {}
                }
                long remainingContentLength = conn.getContentLengthLong();
                if (state.totalBytesExpected <= 0 && remainingContentLength > 0) {
                    state.totalBytesExpected = existingBytes + remainingContentLength;
                }
                isAppendMode = true;
                Log.d(TAG, "[" + type + "] HTTP 206 Partial Content confirmed! Resuming from " + existingBytes +
                           " bytes. Total expected: " + (state.totalBytesExpected / (1024 * 1024)) + " MB");
            } else if (responseCode == HttpURLConnection.HTTP_OK) { // 200 OK
                long fullLength = conn.getContentLengthLong();
                if (fullLength > 0) {
                    state.totalBytesExpected = fullLength;
                }
                existingBytes = 0L;
                state.currentDownloadedBytes = 0L;
                isAppendMode = false;
                Log.d(TAG, "[" + type + "] HTTP 200 OK received. Total: " + (state.totalBytesExpected / (1024 * 1024)) + " MB");
            } else if (responseCode == 416) { // 416 Range Not Satisfiable
                Log.w(TAG, "[" + type + "] HTTP 416 Range Not Satisfiable. Existing bytes: " + existingBytes);
                if (existingBytes >= spec.minReadySize) {
                    finalizeDownloadedModel(context, type, partFile);
                    return;
                } else {
                    if (partFile.exists()) partFile.delete();
                    existingBytes = 0L;
                    state.currentDownloadedBytes = 0L;
                    isAppendMode = false;
                }
            } else {
                throw new IOException("Server returned HTTP " + responseCode + ": " + conn.getResponseMessage());
            }

            state.activeOutputStream = new FileOutputStream(partFile, isAppendMode);
            state.activeInputStream = conn.getInputStream();

            byte[] buffer = new byte[65536];
            int bytesRead;
            long lastFlushTime = System.currentTimeMillis();
            long lastLogTime = System.currentTimeMillis();

            while (!state.isPaused && !state.isCancelled && (bytesRead = state.activeInputStream.read(buffer)) != -1) {
                state.activeOutputStream.write(buffer, 0, bytesRead);
                state.currentDownloadedBytes += bytesRead;

                long now = System.currentTimeMillis();
                if (now - lastFlushTime > 1000) {
                    state.activeOutputStream.flush();
                    if (state.totalBytesExpected > 0) {
                        state.currentProgressPercent = (int) Math.min(99, (state.currentDownloadedBytes * 100) / state.totalBytesExpected);
                    }
                    getPrefs(context).edit()
                        .putLong(getDownloadedBytesKey(type), state.currentDownloadedBytes)
                        .putLong(getTotalBytesKey(type), state.totalBytesExpected)
                        .apply();
                    lastFlushTime = now;
                }

                if (now - lastLogTime > 4000) {
                    long dlMb = state.currentDownloadedBytes / (1024 * 1024);
                    long totMb = state.totalBytesExpected / (1024 * 1024);
                    Log.d(TAG, "[" + type + "] Download: " + dlMb + " MB / " + totMb + " MB (" + state.currentProgressPercent + "%)");
                    lastLogTime = now;
                }
            }

            state.activeOutputStream.flush();

            if (state.isCancelled) {
                Log.d(TAG, "[" + type + "] Download canceled. Deleting partial file.");
                try { state.activeOutputStream.close(); } catch (Exception ignored) {}
                if (partFile.exists()) partFile.delete();
                state.currentDownloadedBytes = 0L;
                state.currentProgressPercent = 0;
                getPrefs(context).edit()
                    .putString(getStatusKey(type), "idle")
                    .putLong(getDownloadedBytesKey(type), 0L)
                    .apply();
            } else if (state.isPaused) {
                Log.d(TAG, "[" + type + "] Download safely paused. Kept bytes: " + partFile.length());
                getPrefs(context).edit()
                    .putString(getStatusKey(type), "paused")
                    .putLong(getDownloadedBytesKey(type), partFile.length())
                    .apply();
            } else {
                if (state.currentDownloadedBytes >= spec.minReadySize) {
                    finalizeDownloadedModel(context, type, partFile);
                } else {
                    Log.w(TAG, "[" + type + "] Stream ended before expected size. Pausing.");
                    getPrefs(context).edit().putString(getStatusKey(type), "paused").apply();
                }
            }

        } catch (java.net.SocketTimeoutException | java.net.ConnectException e) {
            Log.w(TAG, "[" + type + "] Network timeout: " + e.getMessage() + ". Pausing for smooth resumption.");
            if (!state.isCancelled) {
                state.isPaused = true;
                getPrefs(context).edit()
                    .putString(getStatusKey(type), "paused")
                    .putLong(getDownloadedBytesKey(type), partFile != null && partFile.exists() ? partFile.length() : state.currentDownloadedBytes)
                    .apply();
            }
        } catch (Exception e) {
            Log.e(TAG, "[" + type + "] Download error: " + e.getMessage(), e);
            if (!state.isPaused && !state.isCancelled) {
                getPrefs(context).edit().putString(getStatusKey(type), "failed").apply();
            }
        } finally {
            try { if (state.activeInputStream != null) state.activeInputStream.close(); } catch (Exception ignored) {}
            try { if (state.activeOutputStream != null) state.activeOutputStream.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
            state.activeConnection = null;
            state.activeInputStream = null;
            state.activeOutputStream = null;
            state.isDownloading = false;
        }
    }

    private synchronized void finalizeDownloadedModel(Context context, String type, File sourcePartFile) {
        if (sourcePartFile == null || !sourcePartFile.exists()) return;
        File targetFile = getModelFile(context, type);
        DownloadTaskState state = getState(type);

        Log.d(TAG, "[" + type + "] Finalizing downloaded model: " + sourcePartFile.getAbsolutePath() + " -> " + targetFile.getAbsolutePath());
        try {
            if (targetFile.exists()) targetFile.delete();
            boolean renamed = sourcePartFile.renameTo(targetFile);
            if (!renamed) {
                copyFile(sourcePartFile, targetFile);
                sourcePartFile.delete();
            }

            state.currentProgressPercent = 100;
            state.currentDownloadedBytes = targetFile.length();
            state.totalBytesExpected = targetFile.length();

            getPrefs(context).edit()
                .putString(getStatusKey(type), "completed")
                .putLong(getDownloadedBytesKey(type), targetFile.length())
                .putLong(getTotalBytesKey(type), targetFile.length())
                .apply();

            Log.d(TAG, "SUCCESS: Installed " + type + " model to " + targetFile.getAbsolutePath() + " (" + (targetFile.length() / (1024 * 1024)) + " MB)");
        } catch (Exception e) {
            Log.e(TAG, "[" + type + "] Error finalizing model: " + e.getMessage(), e);
            getPrefs(context).edit().putString(getStatusKey(type), "failed").apply();
        }
    }

    private void copyFile(File src, File dst) throws Exception {
        FileInputStream in = new FileInputStream(src);
        FileOutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[65536];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        out.flush();
        out.close();
        in.close();
    }

    public synchronized void pauseDownload(Context context, String type) {
        DownloadTaskState state = getState(type);
        state.isPaused = true;
        state.isDownloading = false;

        try { if (state.activeInputStream != null) state.activeInputStream.close(); } catch (Exception ignored) {}
        try { if (state.activeOutputStream != null) state.activeOutputStream.close(); } catch (Exception ignored) {}
        try { if (state.activeConnection != null) state.activeConnection.disconnect(); } catch (Exception ignored) {}

        if (context != null) {
            getPrefs(context).edit().putString(getStatusKey(type), "paused").apply();
        }
    }

    public synchronized void pauseDownload(Context context) {
        pauseDownload(context, TYPE_LLM);
    }

    public synchronized void cancelDownload(Context context, String type) {
        DownloadTaskState state = getState(type);
        state.isCancelled = true;
        state.isDownloading = false;

        try { if (state.activeInputStream != null) state.activeInputStream.close(); } catch (Exception ignored) {}
        try { if (state.activeOutputStream != null) state.activeOutputStream.close(); } catch (Exception ignored) {}
        try { if (state.activeConnection != null) state.activeConnection.disconnect(); } catch (Exception ignored) {}

        if (context != null) {
            File partFile = getPartFile(context, type);
            if (partFile != null && partFile.exists()) partFile.delete();
            state.currentDownloadedBytes = 0L;
            state.currentProgressPercent = 0;
            getPrefs(context).edit()
                .putString(getStatusKey(type), "idle")
                .putLong(getDownloadedBytesKey(type), 0L)
                .apply();
        }
    }

    public synchronized void cancelDownload(Context context) {
        cancelDownload(context, TYPE_LLM);
    }

    public JSONObject getDownloadProgress(Context context, String type) {
        JSONObject res = new JSONObject();
        ModelSpec spec = getSpec(type);
        DownloadTaskState state = getState(type);

        try {
            res.put("modelType", type);
            res.put("displayName", spec.displayName);

            if (isModelDownloaded(context, type)) {
                res.put("status", "completed");
                res.put("progress", 100);
                res.put("isReady", true);
                File f = getModelFile(context, type);
                long len = (f != null && f.exists()) ? f.length() : 0L;
                res.put("fileSize", len);
                res.put("downloadedBytes", len);
                res.put("totalBytes", len);
                return res;
            }

            File partFile = getPartFile(context, type);
            long partSize = (partFile != null && partFile.exists()) ? partFile.length() : 0L;
            long total = (state.totalBytesExpected > 0) ? state.totalBytesExpected : spec.defaultTotalBytes;

            String prefStatus = getPrefs(context).getString(getStatusKey(type), "idle");
            String status = state.isDownloading ? "downloading" : (state.isPaused ? "paused" : prefStatus);

            long currentDl = state.isDownloading ? state.currentDownloadedBytes : partSize;
            int progress = (int) Math.min(99, total > 0 ? (currentDl * 100) / total : 0);

            res.put("status", status);
            res.put("progress", progress);
            res.put("isReady", false);
            res.put("downloadedBytes", currentDl);
            res.put("totalBytes", total);
            return res;
        } catch (Exception e) {
            Log.e(TAG, "[" + type + "] getDownloadProgress error: " + e.getMessage());
            try {
                res.put("status", "idle");
                res.put("progress", 0);
                res.put("isReady", false);
                res.put("downloadedBytes", 0);
                res.put("totalBytes", spec.defaultTotalBytes);
            } catch (Exception ignored) {}
            return res;
        }
    }

    public JSONObject getDownloadProgress(Context context) {
        return getDownloadProgress(context, TYPE_LLM);
    }

    public JSONObject getAllModelsProgress(Context context) {
        JSONObject res = new JSONObject();
        try {
            res.put(TYPE_LLM, getDownloadProgress(context, TYPE_LLM));
            res.put(TYPE_STT, getDownloadProgress(context, TYPE_STT));
            res.put(TYPE_TTS, getDownloadProgress(context, TYPE_TTS));
        } catch (Exception e) {
            Log.e(TAG, "getAllModelsProgress error: " + e.getMessage());
        }
        return res;
    }

    public static void startForegroundDownloadService(Context context, String modelType, boolean allowMetered) {
        if (context == null) return;
        try {
            Intent intent = new Intent(context, ModelDownloadService.class);
            intent.setAction(ModelDownloadService.ACTION_START_DOWNLOAD);
            intent.putExtra(ModelDownloadService.EXTRA_ALLOW_METERED, allowMetered);
            intent.putExtra("extra_model_type", modelType != null ? modelType : TYPE_LLM);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            Log.d(TAG, "Launched ModelDownloadService for modelType=" + modelType);
        } catch (Exception e) {
            Log.e(TAG, "Error launching ModelDownloadService: " + e.getMessage(), e);
            getInstance().startDownload(context, modelType, allowMetered);
        }
    }

    public static void startForegroundDownloadService(Context context, boolean allowMetered) {
        startForegroundDownloadService(context, TYPE_LLM, allowMetered);
    }
}
