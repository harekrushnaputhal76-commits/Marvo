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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/**
 * Robust Offline LLM Downloader with HTTP Range Resumption & Internal Storage Handling.
 * - Stores files exclusively in internal /models/ sub-directory.
 * - Supports pausing and resuming without losing partially downloaded bytes (HTTP Range header).
 * - Writes incrementally in chunks and renames .part file upon 100% completion.
 * - Runs seamlessly with ModelDownloadService foreground service.
 */
public class OfflineBrainDownloader {
    private static final String TAG = "MarvoDownload";

    public static final String PREF_NAME = "marvo_model_downloader";
    public static final String KEY_DOWNLOAD_STATUS = "download_status"; // "idle", "downloading", "completed", "paused", "failed", "paused_wifi"
    public static final String KEY_ALLOW_METERED = "allow_metered_download";
    public static final String KEY_DOWNLOADED_BYTES = "downloaded_bytes";
    public static final String KEY_TOTAL_BYTES = "total_bytes";

    // Quantized Model Parameters (Phi-3 Mini 4K Instruct ~1.8GB - 2.2GB GGUF)
    public static final String DEFAULT_MODEL_NAME = "phi-3-mini-4k-instruct-q4.gguf";
    public static final String DEFAULT_MODEL_URL = "https://huggingface.co/bartowski/Phi-3-mini-4k-instruct-GGUF/resolve/main/Phi-3-mini-4k-instruct-Q4_K_M.gguf";

    private static volatile OfflineBrainDownloader instance;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Active state flags
    private volatile boolean isDownloading = false;
    private volatile boolean isPaused = false;
    private volatile boolean isCancelled = false;

    // Progress metrics
    private volatile long currentDownloadedBytes = 0L;
    private volatile long totalBytesExpected = 2200L * 1024L * 1024L; // fallback estimate ~2.2GB
    private volatile int currentProgressPercent = 0;

    // Stream references for safe pause/cancellation
    private volatile HttpURLConnection activeConnection = null;
    private volatile InputStream activeInputStream = null;
    private volatile FileOutputStream activeOutputStream = null;

    private OfflineBrainDownloader() {}

    public static synchronized OfflineBrainDownloader getInstance() {
        if (instance == null) {
            instance = new OfflineBrainDownloader();
        }
        return instance;
    }

    private SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public File getModelsDir(Context context) {
        if (context == null) return null;
        File dir = MemoryVault.getModelsDir(context);
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public File getModelFile(Context context) {
        File dir = getModelsDir(context);
        return dir != null ? new File(dir, DEFAULT_MODEL_NAME) : null;
    }

    public File getPartFile(Context context) {
        File dir = getModelsDir(context);
        return dir != null ? new File(dir, DEFAULT_MODEL_NAME + ".part") : null;
    }

    public boolean isModelDownloaded(Context context) {
        if (context == null) return false;
        File modelFile = getModelFile(context);
        boolean exists = modelFile != null && modelFile.exists();
        long length = exists ? modelFile.length() : 0L;
        boolean ready = exists && length > 500L * 1024L * 1024L;
        return ready;
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
     * Starts or resumes the offline brain download with HTTP Range support.
     */
    public synchronized boolean startDownload(final Context context, boolean allowMetered) {
        if (context == null) return false;

        if (isModelDownloaded(context)) {
            Log.d(TAG, "Offline brain already fully downloaded & verified.");
            getPrefs(context).edit().putString(KEY_DOWNLOAD_STATUS, "completed").apply();
            return true;
        }

        if (isDownloading) {
            Log.d(TAG, "Download is already running.");
            return true;
        }

        getPrefs(context).edit().putBoolean(KEY_ALLOW_METERED, allowMetered).apply();
        boolean unmetered = isUnmeteredConnection(context);
        if (!allowMetered && !unmetered) {
            Log.d(TAG, "Metered connection detected and allowMetered is false. Pausing until Wi-Fi.");
            getPrefs(context).edit().putString(KEY_DOWNLOAD_STATUS, "paused_wifi").apply();
            return false;
        }

        isPaused = false;
        isCancelled = false;
        isDownloading = true;

        final Context appContext = context.getApplicationContext();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                executeDownloadTask(appContext);
            }
        });

        return true;
    }

    private void executeDownloadTask(Context context) {
        File partFile = getPartFile(context);
        File finalFile = getModelFile(context);

        if (partFile == null || finalFile == null) {
            Log.e(TAG, "Storage directories are inaccessible.");
            isDownloading = false;
            getPrefs(context).edit().putString(KEY_DOWNLOAD_STATUS, "failed").apply();
            return;
        }

        long existingBytes = partFile.exists() ? partFile.length() : 0L;
        currentDownloadedBytes = existingBytes;
        Log.d(TAG, "Starting/Resuming download: partFile=" + partFile.getAbsolutePath() + ", existingBytes=" + existingBytes + " (" + (existingBytes / (1024 * 1024)) + " MB)");

        getPrefs(context).edit().putString(KEY_DOWNLOAD_STATUS, "downloading").apply();

        HttpURLConnection conn = null;
        try {
            String targetUrl = DEFAULT_MODEL_URL;
            int redirectCount = 0;

            // Follow HTTP redirects while carrying forward Range request headers
            while (redirectCount < 8) {
                URL url = new URL(targetUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(45000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; MarvoOfflineBrain/2.0)");
                conn.setRequestProperty("Accept-Encoding", "identity");

                if (existingBytes > 0) {
                    conn.setRequestProperty("Range", "bytes=" + existingBytes + "-");
                    Log.d(TAG, "Sending HTTP Range header: bytes=" + existingBytes + "-");
                }

                int respCode = conn.getResponseCode();
                Log.d(TAG, "HTTP Response Code: " + respCode + " from " + targetUrl);

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
                    Log.d(TAG, "Following redirect (" + redirectCount + ") -> " + targetUrl);
                    continue;
                }
                break;
            }

            activeConnection = conn;
            if (conn == null) throw new IOException("Failed to establish HTTP connection.");

            int responseCode = conn.getResponseCode();
            boolean isAppendMode;

            if (responseCode == HttpURLConnection.HTTP_PARTIAL) { // 206 Partial Content
                String contentRange = conn.getHeaderField("Content-Range");
                if (contentRange != null && contentRange.contains("/")) {
                    try {
                        String totalStr = contentRange.substring(contentRange.lastIndexOf('/') + 1).trim();
                        long parsedTotal = Long.parseLong(totalStr);
                        if (parsedTotal > 0) {
                            totalBytesExpected = parsedTotal;
                        }
                    } catch (Exception ignored) {}
                }
                long remainingContentLength = conn.getContentLengthLong();
                if (totalBytesExpected <= 0 && remainingContentLength > 0) {
                    totalBytesExpected = existingBytes + remainingContentLength;
                }
                isAppendMode = true;
                Log.d(TAG, "HTTP 206 Partial Content confirmed! Resuming from " + existingBytes + " bytes. Total expected: " + totalBytesExpected + " bytes (" + (totalBytesExpected / (1024 * 1024)) + " MB)");
            } else if (responseCode == HttpURLConnection.HTTP_OK) { // 200 OK
                long fullLength = conn.getContentLengthLong();
                if (fullLength > 0) {
                    totalBytesExpected = fullLength;
                }
                // Server does not support range or file is starting fresh
                existingBytes = 0L;
                currentDownloadedBytes = 0L;
                isAppendMode = false;
                Log.d(TAG, "HTTP 200 OK received. Downloading entire file from byte 0. Total: " + totalBytesExpected + " bytes (" + (totalBytesExpected / (1024 * 1024)) + " MB)");
            } else if (responseCode == 416) { // 416 Requested Range Not Satisfiable
                Log.w(TAG, "HTTP 416 Range Not Satisfiable. Existing bytes: " + existingBytes);
                if (existingBytes > 500L * 1024L * 1024L) {
                    Log.d(TAG, "Existing partial file appears complete. Finalizing model...");
                    finalizeDownloadedModel(context, partFile);
                    return;
                } else {
                    Log.w(TAG, "Partial file corrupt or invalid range. Deleting and restarting from 0...");
                    if (partFile.exists()) partFile.delete();
                    existingBytes = 0L;
                    currentDownloadedBytes = 0L;
                    isAppendMode = false;
                }
            } else {
                throw new IOException("Server returned HTTP " + responseCode + ": " + conn.getResponseMessage());
            }

            activeOutputStream = new FileOutputStream(partFile, isAppendMode);
            activeInputStream = conn.getInputStream();

            byte[] buffer = new byte[65536]; // 64KB chunks
            int bytesRead;
            long lastFlushTime = System.currentTimeMillis();
            long lastLogTime = System.currentTimeMillis();

            while (!isPaused && !isCancelled && (bytesRead = activeInputStream.read(buffer)) != -1) {
                activeOutputStream.write(buffer, 0, bytesRead);
                currentDownloadedBytes += bytesRead;

                long now = System.currentTimeMillis();
                if (now - lastFlushTime > 1000) {
                    activeOutputStream.flush();
                    if (totalBytesExpected > 0) {
                        currentProgressPercent = (int) Math.min(99, (currentDownloadedBytes * 100) / totalBytesExpected);
                    }
                    getPrefs(context).edit()
                        .putLong(KEY_DOWNLOADED_BYTES, currentDownloadedBytes)
                        .putLong(KEY_TOTAL_BYTES, totalBytesExpected)
                        .apply();
                    lastFlushTime = now;
                }

                if (now - lastLogTime > 4000) {
                    long dlMb = currentDownloadedBytes / (1024 * 1024);
                    long totMb = totalBytesExpected / (1024 * 1024);
                    Log.d(TAG, "Download progress: " + dlMb + " MB / " + totMb + " MB (" + currentProgressPercent + "%)");
                    lastLogTime = now;
                }
            }

            activeOutputStream.flush();

            // Evaluate state upon exiting loop
            if (isCancelled) {
                Log.d(TAG, "Download canceled by user. Cleaning up .part file...");
                try {
                    activeOutputStream.close();
                } catch (Exception ignored) {}
                if (partFile.exists()) {
                    boolean del = partFile.delete();
                    Log.d(TAG, "Partial file deleted: " + del);
                }
                currentDownloadedBytes = 0L;
                currentProgressPercent = 0;
                getPrefs(context).edit().putString(KEY_DOWNLOAD_STATUS, "idle").apply();
            } else if (isPaused) {
                Log.d(TAG, "Download safely paused. File size retained on disk: " + partFile.length() + " bytes (" + (partFile.length() / (1024 * 1024)) + " MB)");
                getPrefs(context).edit()
                    .putString(KEY_DOWNLOAD_STATUS, "paused")
                    .putLong(KEY_DOWNLOADED_BYTES, partFile.length())
                    .apply();
            } else {
                // Stream ended normally
                if (currentDownloadedBytes > 500L * 1024L * 1024L) {
                    finalizeDownloadedModel(context, partFile);
                } else {
                    Log.w(TAG, "Stream ended prematurely without error. Pausing for resumption.");
                    getPrefs(context).edit().putString(KEY_DOWNLOAD_STATUS, "paused").apply();
                }
            }

        } catch (java.net.SocketTimeoutException | java.net.ConnectException e) {
            Log.w(TAG, "Network timeout during download: " + e.getMessage() + ". Pausing for smooth resumption.");
            if (!isCancelled) {
                isPaused = true;
                getPrefs(context).edit()
                    .putString(KEY_DOWNLOAD_STATUS, "paused")
                    .putLong(KEY_DOWNLOADED_BYTES, partFile != null && partFile.exists() ? partFile.length() : currentDownloadedBytes)
                    .apply();
            }
        } catch (Exception e) {
            Log.e(TAG, "Download error encountered: " + e.getMessage(), e);
            if (!isPaused && !isCancelled) {
                getPrefs(context).edit().putString(KEY_DOWNLOAD_STATUS, "failed").apply();
            }
        } finally {
            try {
                if (activeInputStream != null) activeInputStream.close();
            } catch (Exception ignored) {}
            try {
                if (activeOutputStream != null) activeOutputStream.close();
            } catch (Exception ignored) {}
            if (conn != null) {
                conn.disconnect();
            }
            activeConnection = null;
            activeInputStream = null;
            activeOutputStream = null;
            isDownloading = false;
        }
    }

    private synchronized void finalizeDownloadedModel(Context context, File sourcePartFile) {
        if (sourcePartFile == null || !sourcePartFile.exists()) {
            Log.e(TAG, "finalizeDownloadedModel error: sourcePartFile is missing.");
            return;
        }

        File targetFile = getModelFile(context);
        Log.d(TAG, "Finalizing downloaded model: " + sourcePartFile.getAbsolutePath() + " (" + (sourcePartFile.length() / (1024 * 1024)) + " MB) -> " + targetFile.getAbsolutePath());

        try {
            if (targetFile.exists()) {
                targetFile.delete();
            }

            boolean renamed = sourcePartFile.renameTo(targetFile);
            if (!renamed) {
                Log.d(TAG, "Direct rename failed, copying byte stream...");
                copyFile(sourcePartFile, targetFile);
                sourcePartFile.delete();
            }

            currentProgressPercent = 100;
            currentDownloadedBytes = targetFile.length();
            totalBytesExpected = targetFile.length();

            getPrefs(context).edit()
                .putString(KEY_DOWNLOAD_STATUS, "completed")
                .putLong(KEY_DOWNLOADED_BYTES, targetFile.length())
                .putLong(KEY_TOTAL_BYTES, targetFile.length())
                .apply();

            Log.d(TAG, "SUCCESS: Offline AI Brain model installed to: " + targetFile.getAbsolutePath() + " (" + (targetFile.length() / (1024 * 1024)) + " MB). Ready for offline inference!");
        } catch (Exception e) {
            Log.e(TAG, "Error finalizing model file: " + e.getMessage(), e);
            getPrefs(context).edit().putString(KEY_DOWNLOAD_STATUS, "failed").apply();
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

    public synchronized void pauseDownload(Context context) {
        Log.d(TAG, "pauseDownload requested.");
        isPaused = true;
        isDownloading = false;

        try {
            if (activeInputStream != null) activeInputStream.close();
        } catch (Exception ignored) {}
        try {
            if (activeOutputStream != null) {
                activeOutputStream.flush();
                activeOutputStream.close();
            }
        } catch (Exception ignored) {}
        try {
            if (activeConnection != null) activeConnection.disconnect();
        } catch (Exception ignored) {}

        if (context != null) {
            getPrefs(context).edit().putString(KEY_DOWNLOAD_STATUS, "paused").apply();
        }
    }

    public synchronized void cancelDownload(Context context) {
        Log.d(TAG, "cancelDownload requested.");
        isCancelled = true;
        isDownloading = false;

        try {
            if (activeInputStream != null) activeInputStream.close();
        } catch (Exception ignored) {}
        try {
            if (activeOutputStream != null) activeOutputStream.close();
        } catch (Exception ignored) {}
        try {
            if (activeConnection != null) activeConnection.disconnect();
        } catch (Exception ignored) {}

        if (context != null) {
            File partFile = getPartFile(context);
            if (partFile != null && partFile.exists()) {
                partFile.delete();
                Log.d(TAG, "Deleted partial file on cancellation.");
            }
            currentDownloadedBytes = 0L;
            currentProgressPercent = 0;
            getPrefs(context).edit()
                .putString(KEY_DOWNLOAD_STATUS, "idle")
                .putLong(KEY_DOWNLOADED_BYTES, 0L)
                .apply();
        }
    }

    public JSONObject getDownloadProgress(Context context) {
        JSONObject res = new JSONObject();
        try {
            if (isModelDownloaded(context)) {
                res.put("status", "completed");
                res.put("progress", 100);
                res.put("isReady", true);
                File f = getModelFile(context);
                long len = (f != null && f.exists()) ? f.length() : 0L;
                res.put("fileSize", len);
                res.put("downloadedBytes", len);
                res.put("totalBytes", len);
                return res;
            }

            File partFile = getPartFile(context);
            long partSize = (partFile != null && partFile.exists()) ? partFile.length() : 0L;
            long total = (totalBytesExpected > 0) ? totalBytesExpected : (2200L * 1024L * 1024L);

            String prefStatus = getPrefs(context).getString(KEY_DOWNLOAD_STATUS, "idle");
            String status = isDownloading ? "downloading" : (isPaused ? "paused" : prefStatus);

            long currentDl = isDownloading ? currentDownloadedBytes : partSize;
            int progress = (int) Math.min(99, total > 0 ? (currentDl * 100) / total : 0);

            res.put("status", status);
            res.put("progress", progress);
            res.put("isReady", false);
            res.put("downloadedBytes", currentDl);
            res.put("totalBytes", total);
            return res;
        } catch (Exception e) {
            Log.e(TAG, "getDownloadProgress error: " + e.getMessage());
            try {
                res.put("status", "idle");
                res.put("progress", 0);
                res.put("isReady", false);
                res.put("downloadedBytes", 0);
                res.put("totalBytes", 2200L * 1024L * 1024L);
            } catch (Exception ignored) {}
            return res;
        }
    }

    public static void startForegroundDownloadService(Context context, boolean allowMetered) {
        if (context == null) return;
        try {
            Intent intent = new Intent(context, ModelDownloadService.class);
            intent.setAction(ModelDownloadService.ACTION_START_DOWNLOAD);
            intent.putExtra(ModelDownloadService.EXTRA_ALLOW_METERED, allowMetered);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            Log.d(TAG, "Launched ModelDownloadService with live foreground notification.");
        } catch (Exception e) {
            Log.e(TAG, "Error launching ModelDownloadService: " + e.getMessage(), e);
            getInstance().startDownload(context, allowMetered);
        }
    }
}
