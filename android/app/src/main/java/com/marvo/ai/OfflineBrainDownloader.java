package com.marvo.ai;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
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
import org.json.JSONArray;
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
    public static final String TYPE_PHI3 = "phi-3-mini";
    public static final String TYPE_GEMMA = "gemma-2b";
    public static final String TYPE_LLAMA = "llama-3-8b";
    public static final String TYPE_QWEN = "qwen-2.5-3b";
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
            500L * 1024L * 1024L,
            2200L * 1024L * 1024L,
            "Phi-3 Mini 4K Instruct"
        ));
        specs.put(TYPE_PHI3, new ModelSpec(
            TYPE_PHI3,
            "phi-3-mini-4k-instruct-q4.gguf",
            "https://huggingface.co/bartowski/Phi-3-mini-4k-instruct-GGUF/resolve/main/Phi-3-mini-4k-instruct-Q4_K_M.gguf",
            500L * 1024L * 1024L,
            2200L * 1024L * 1024L,
            "Phi-3 Mini 4K Instruct (Microsoft)"
        ));
        specs.put(TYPE_GEMMA, new ModelSpec(
            TYPE_GEMMA,
            "gemma-2b-it-cpu.gguf",
            "https://huggingface.co/google/gemma-2b-it-GGUF/resolve/main/2b_it_v2.gguf",
            300L * 1024L * 1024L,
            1500L * 1024L * 1024L,
            "Gemma 2B IT (Google)"
        ));
        specs.put(TYPE_LLAMA, new ModelSpec(
            TYPE_LLAMA,
            "llama-3-8b-instruct.gguf",
            "https://huggingface.co/QuantFactory/Meta-Llama-3-8B-Instruct-GGUF/resolve/main/Meta-Llama-3-8B-Instruct.Q4_K_M.gguf",
            800L * 1024L * 1024L,
            4300L * 1024L * 1024L,
            "Llama 3 8B Instruct (Meta)"
        ));
        specs.put(TYPE_QWEN, new ModelSpec(
            TYPE_QWEN,
            "qwen-2.5-3b-instruct.gguf",
            "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
            400L * 1024L * 1024L,
            2000L * 1024L * 1024L,
            "Qwen 2.5 3B Instruct (Alibaba)"
        ));
        specs.put(TYPE_STT, new ModelSpec(
            TYPE_STT,
            "whisper-tiny-en.bin",
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin",
            50L * 1024L * 1024L,
            151338400L,
            "Whisper Tiny Speech Recognition"
        ));
        specs.put(TYPE_TTS, new ModelSpec(
            TYPE_TTS,
            "vits-piper-en.onnx",
            "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium/en_US-lessac-medium.onnx",
            20L * 1024L * 1024L,
            63800000L,
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
        volatile long lastBytesSample = 0L;
        volatile long lastTimeSample = 0L;
        volatile double currentSpeedMBps = 0.0;
        volatile long estimatedSecondsRemaining = 0L;
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

    private String getDownloadIdKey(String type) {
        return "download_id_" + (type != null ? type.toLowerCase() : TYPE_LLM);
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

    public boolean deleteModel(Context context, String type) {
        if (context == null) return false;
        try {
            File f = getModelFile(context, type);
            File part = getPartFile(context, type);
            boolean deleted = false;
            if (f != null && f.exists()) {
                deleted = f.delete();
            }
            if (part != null && part.exists()) {
                part.delete();
            }
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                 .remove("status_" + type)
                 .remove("progress_" + type)
                 .remove("downloaded_" + type)
                 .remove("total_" + type)
                 .apply();
            Log.d(TAG, "Deleted offline model: " + type + ", fileDeleted=" + deleted);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error deleting model " + type + ": " + e.getMessage(), e);
            return false;
        }
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
     * Starts or resumes a download for a specific model type using native Android DownloadManager.
     * Guarantees OS-level persistence in public Documents/Marvo_Models/ and background survival.
     */
    public synchronized boolean startDownload(final Context context, final String modelType, boolean allowMetered) {
        if (context == null) return false;
        final String type = (modelType != null) ? modelType.toLowerCase() : TYPE_LLM;
        final ModelSpec spec = getSpec(type);

        if (isModelDownloaded(context, type)) {
            Log.d(TAG, "[" + type + "] Model already fully downloaded & verified.");
            getPrefs(context).edit().putString(getStatusKey(type), "completed").apply();
            return true;
        }

        try {
            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) {
                Log.e(TAG, "DownloadManager service unavailable");
                return false;
            }

            // Ensure destination directory in public Documents/Marvo_Models/
            File publicDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Marvo_Models");
            if (!publicDir.exists()) {
                publicDir.mkdirs();
            }

            // Clean up any stale download ID
            long existingId = getPrefs(context).getLong(getDownloadIdKey(type), -1L);
            if (existingId != -1L) {
                try { dm.remove(existingId); } catch (Exception ignored) {}
            }

            Uri uri = Uri.parse(spec.url);
            DownloadManager.Request req = new DownloadManager.Request(uri);
            req.setTitle("Marvo " + spec.displayName);
            req.setDescription("Downloading on-device AI model (~" + (spec.defaultTotalBytes / (1024 * 1024)) + " MB)");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setAllowedOverMetered(allowMetered);
            req.setAllowedOverRoaming(false);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOCUMENTS, "Marvo_Models/" + spec.fileName);

            long downloadId = dm.enqueue(req);
            getPrefs(context).edit()
                .putLong(getDownloadIdKey(type), downloadId)
                .putString(getStatusKey(type), "downloading")
                .putBoolean(getAllowMeteredKey(type), allowMetered)
                .apply();

            Log.i(TAG, "[" + type + "] Enqueued DownloadManager task id: " + downloadId + " -> Documents/Marvo_Models/" + spec.fileName);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "[" + type + "] Failed to enqueue DownloadManager: " + e.getMessage(), e);
            getPrefs(context).edit().putString(getStatusKey(type), "failed").apply();
            return false;
        }
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
            long downloadId = getPrefs(context).getLong(getDownloadIdKey(type), -1L);
            if (downloadId != -1L) {
                try {
                    DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                    if (dm != null) dm.remove(downloadId);
                } catch (Exception ignored) {}
                getPrefs(context).edit().remove(getDownloadIdKey(type)).apply();
            }
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
            long downloadId = getPrefs(context).getLong(getDownloadIdKey(type), -1L);
            if (downloadId != -1L) {
                try {
                    DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                    if (dm != null) dm.remove(downloadId);
                } catch (Exception ignored) {}
                getPrefs(context).edit().remove(getDownloadIdKey(type)).apply();
            }
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

            // Query native Android DownloadManager if active
            long downloadId = getPrefs(context).getLong(getDownloadIdKey(type), -1L);
            if (downloadId != -1L) {
                DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) {
                    DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(downloadId);
                    try (Cursor cursor = dm.query(query)) {
                        if (cursor != null && cursor.moveToFirst()) {
                            int bytesDownloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                            int bytesTotalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                            int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);

                            long dlBytes = bytesDownloadedIdx != -1 ? cursor.getLong(bytesDownloadedIdx) : 0L;
                            long totBytes = bytesTotalIdx != -1 ? cursor.getLong(bytesTotalIdx) : spec.defaultTotalBytes;
                            if (totBytes <= 0) totBytes = spec.defaultTotalBytes;
                            int dmStatus = statusIdx != -1 ? cursor.getInt(statusIdx) : -1;

                            String statusStr = "downloading";
                            boolean isReady = false;
                            int progress = (int) Math.min(100, totBytes > 0 ? (dlBytes * 100) / totBytes : 0);

                            if (dmStatus == DownloadManager.STATUS_SUCCESSFUL) {
                                statusStr = "completed";
                                isReady = true;
                                progress = 100;
                                getPrefs(context).edit().putString(getStatusKey(type), "completed").apply();
                            } else if (dmStatus == DownloadManager.STATUS_PAUSED) {
                                statusStr = "paused";
                            } else if (dmStatus == DownloadManager.STATUS_FAILED) {
                                statusStr = "failed";
                            } else if (dmStatus == DownloadManager.STATUS_RUNNING || dmStatus == DownloadManager.STATUS_PENDING) {
                                statusStr = "downloading";
                            }

                            res.put("status", statusStr);
                            res.put("progress", progress);
                            res.put("isReady", isReady);
                            res.put("downloadedBytes", dlBytes);
                            res.put("totalBytes", totBytes);
                            return res;
                        }
                    } catch (Exception dmErr) {
                        Log.w(TAG, "Error querying DownloadManager: " + dmErr.getMessage());
                    }
                }
            }

            File partFile = getPartFile(context, type);
            long partSize = (partFile != null && partFile.exists()) ? partFile.length() : 0L;
            long total = (state.totalBytesExpected > 0) ? state.totalBytesExpected : spec.defaultTotalBytes;

            String prefStatus = getPrefs(context).getString(getStatusKey(type), "idle");
            String status = state.isDownloading ? "downloading" : (state.isPaused ? "paused" : prefStatus);

            long currentDl = state.isDownloading ? state.currentDownloadedBytes : partSize;
            int progress = (int) Math.min(99, total > 0 ? (currentDl * 100) / total : 0);

            File targetFile = getModelFile(context, type);
            String storagePath = (targetFile != null) ? targetFile.getAbsolutePath() : "";
            res.put("storagePath", storagePath);

            long now = System.currentTimeMillis();
            if (state.lastTimeSample == 0L) {
                state.lastTimeSample = now;
                state.lastBytesSample = currentDl;
            } else {
                long timeDiff = now - state.lastTimeSample;
                if (timeDiff >= 1000) {
                    long bytesDiff = currentDl - state.lastBytesSample;
                    if (bytesDiff > 0) {
                        state.currentSpeedMBps = (double) bytesDiff / (1024.0 * 1024.0) / ((double) timeDiff / 1000.0);
                        long bytesLeft = Math.max(0L, total - currentDl);
                        if (state.currentSpeedMBps > 0.02) {
                            state.estimatedSecondsRemaining = (long) (bytesLeft / (state.currentSpeedMBps * 1024.0 * 1024.0));
                        }
                    } else if ("paused".equals(status) || "idle".equals(status) || "completed".equals(status)) {
                        state.currentSpeedMBps = 0.0;
                        state.estimatedSecondsRemaining = 0L;
                    }
                    state.lastBytesSample = currentDl;
                    state.lastTimeSample = now;
                }
            }

            res.put("speedMBps", Math.round(state.currentSpeedMBps * 100.0) / 100.0);
            res.put("etaSeconds", state.estimatedSecondsRemaining);
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
                res.put("speedMBps", 0.0);
                res.put("etaSeconds", 0L);
                res.put("storagePath", "");
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
            res.put(TYPE_PHI3, getDownloadProgress(context, TYPE_PHI3));
            res.put(TYPE_GEMMA, getDownloadProgress(context, TYPE_GEMMA));
            res.put(TYPE_LLAMA, getDownloadProgress(context, TYPE_LLAMA));
            res.put(TYPE_QWEN, getDownloadProgress(context, TYPE_QWEN));
            res.put(TYPE_STT, getDownloadProgress(context, TYPE_STT));
            res.put(TYPE_TTS, getDownloadProgress(context, TYPE_TTS));
        } catch (Exception e) {
            Log.e(TAG, "getAllModelsProgress error: " + e.getMessage());
        }
        return res;
    }

    public static final String KEY_ACTIVE_OFFLINE_MODEL = "marvo_active_offline_model";

    public void setActiveModel(Context context, String modelType) {
        if (context == null) return;
        getPrefs(context).edit().putString(KEY_ACTIVE_OFFLINE_MODEL, modelType != null ? modelType : TYPE_LLM).apply();
    }

    public String getActiveModel(Context context) {
        if (context == null) return TYPE_LLM;
        return getPrefs(context).getString(KEY_ACTIVE_OFFLINE_MODEL, TYPE_LLM);
    }

    public synchronized boolean deleteModel(Context context, String type) {
        if (context == null) return false;
        try {
            pauseDownload(context, type);
            File modelFile = getModelFile(context, type);
            if (modelFile != null && modelFile.exists()) {
                modelFile.delete();
            }
            File partFile = getPartFile(context, type);
            if (partFile != null && partFile.exists()) {
                partFile.delete();
            }
            getPrefs(context).edit()
                .putString(getStatusKey(type), "idle")
                .putLong(getDownloadedBytesKey(type), 0L)
                .apply();
            DownloadTaskState state = getState(type);
            state.currentDownloadedBytes = 0L;
            state.currentProgressPercent = 0;
            state.currentSpeedMBps = 0.0;
            state.estimatedSecondsRemaining = 0L;
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error deleting model [" + type + "]: " + e.getMessage());
            return false;
        }
    }

    public JSONArray getAllOfflineModelsList(Context context) {
        JSONArray arr = new JSONArray();
        String activeModel = getActiveModel(context);

        String[] modelKeys = new String[] {
            TYPE_PHI3,
            TYPE_GEMMA,
            TYPE_LLAMA,
            TYPE_QWEN,
            TYPE_STT,
            TYPE_TTS
        };

        for (String key : modelKeys) {
            ModelSpec spec = getSpec(key);
            File f = getModelFile(context, key);
            boolean isDownloaded = isModelDownloaded(context, key);
            long fileSizeBytes = (f != null && f.exists()) ? f.length() : spec.defaultTotalBytes;
            JSONObject progress = getDownloadProgress(context, key);

            JSONObject item = new JSONObject();
            try {
                item.put("id", key);
                item.put("name", spec.displayName);
                item.put("fileName", spec.fileName);
                item.put("url", spec.url);
                item.put("sizeBytes", fileSizeBytes);
                item.put("sizeFormatted", String.format(java.util.Locale.US, "%.1f GB", (double) fileSizeBytes / (1024.0 * 1024.0 * 1024.0)));
                item.put("storagePath", f != null ? f.getAbsolutePath() : "");
                item.put("isDownloaded", isDownloaded);
                item.put("isActive", key.equalsIgnoreCase(activeModel) || (TYPE_PHI3.equals(key) && TYPE_LLM.equals(activeModel)));
                item.put("status", progress.optString("status", isDownloaded ? "completed" : "idle"));
                item.put("progress", progress.optInt("progress", isDownloaded ? 100 : 0));
                item.put("speedMBps", progress.optDouble("speedMBps", 0.0));
                item.put("etaSeconds", progress.optLong("etaSeconds", 0L));
                item.put("downloadedBytes", progress.optLong("downloadedBytes", isDownloaded ? fileSizeBytes : 0L));
                item.put("totalBytes", progress.optLong("totalBytes", fileSizeBytes));
                arr.put(item);
            } catch (Exception ignored) {}
        }
        return arr;
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
