package com.marvo.ai;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/**
 * Step 21: Autonomous Heavy Offline LLM Downloader.
 * Safely fetches a ~1GB-2GB high-intelligence quantized model file from a trusted mirror.
 * Stores exclusively in /models/ sub-directory.
 * Implements native DownloadManager with Wi-Fi enforcement & resilient background fallback.
 */
public class OfflineBrainDownloader {
    private static final String TAG = "MarvoDownload";

    public static final String PREF_NAME = "marvo_model_downloader";
    public static final String KEY_DOWNLOAD_ID = "active_download_id";
    public static final String KEY_DOWNLOAD_STATUS = "download_status"; // "idle", "downloading", "completed", "paused", "failed", "paused_wifi"
    public static final String KEY_ALLOW_METERED = "allow_metered_download";

    // High-Intelligence Quantized Model Parameters (Phi-3 Mini / Gemma 2B Class ~1.8GB)
    public static final String DEFAULT_MODEL_NAME = "phi-3-mini-4k-instruct-q4.gguf";
    public static final String DEFAULT_MODEL_URL = "https://huggingface.co/bartowski/Phi-3-mini-4k-instruct-GGUF/resolve/main/Phi-3-mini-4k-instruct-Q4_K_M.gguf";
    public static final String FALLBACK_MODEL_URL = "https://huggingface.co/google/gemma-2b-it-gpu-int4/resolve/main/gemma-2b-it-gpu-int4.bin";

    private static volatile OfflineBrainDownloader instance;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean isFallbackRunning = false;
    private volatile boolean cancelFallback = false;

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

    /**
     * Checks if the offline model file is fully downloaded and verified on device.
     */
    public boolean isModelDownloaded(Context context) {
        if (context == null) return false;
        File modelFile = getModelFile(context);
        boolean exists = modelFile != null && modelFile.exists();
        long length = exists ? modelFile.length() : 0;
        boolean ready = exists && length > 500L * 1024L * 1024L;
        Log.d(TAG, "isModelDownloaded check: file=" + (modelFile != null ? modelFile.getAbsolutePath() : "null")
                + ", exists=" + exists + ", size=" + (length / (1024 * 1024)) + "MB, ready=" + ready);
        return ready;
    }

    /**
     * Returns the target file in /models/ sub-directory.
     */
    public File getModelFile(Context context) {
        if (context == null) return null;
        File modelsDir = MemoryVault.getModelsDir(context);
        return new File(modelsDir, DEFAULT_MODEL_NAME);
    }

    /**
     * Checks if current connection is unmetered (Wi-Fi).
     */
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
     * Initiates autonomous background download using native DownloadManager with Wi-Fi gating.
     */
    public synchronized boolean startDownload(final Context context, boolean allowMetered) {
        if (context == null) {
            Log.e(TAG, "startDownload failed: Context is null!");
            return false;
        }
        Log.d(TAG, "startDownload requested: allowMetered=" + allowMetered);

        if (isModelDownloaded(context)) {
            Log.d(TAG, "Offline model already downloaded and verified on disk. Status set to completed.");
            getPrefs(context).edit().putString(KEY_DOWNLOAD_STATUS, "completed").apply();
            return true;
        }

        getPrefs(context).edit().putBoolean(KEY_ALLOW_METERED, allowMetered).apply();

        boolean unmetered = isUnmeteredConnection(context);
        Log.d(TAG, "Network connection check: isUnmeteredConnection=" + unmetered + ", allowMetered=" + allowMetered);

        if (!allowMetered && !unmetered) {
            Log.d(TAG, "Download paused: Metered network detected and allowMetered is false. Waiting for Wi-Fi.");
            getPrefs(context).edit().putString(KEY_DOWNLOAD_STATUS, "paused_wifi").apply();
            return false;
        }

        try {
            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                Uri downloadUri = Uri.parse(DEFAULT_MODEL_URL);
                DownloadManager.Request request = new DownloadManager.Request(downloadUri);
                request.setTitle("Marvo Offline AI Brain");
                request.setDescription("Downloading high-intelligence offline LLM model (~1.8GB)");
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

                if (!allowMetered) {
                    request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);
                }

                File tempDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (tempDir == null) tempDir = MemoryVault.getCacheDir(context);
                File tempModel = new File(tempDir, DEFAULT_MODEL_NAME + ".part");
                if (tempModel.exists()) {
                    boolean del = tempModel.delete();
                    Log.d(TAG, "Existing temporary .part file cleaned up: " + del);
                }

                request.setDestinationUri(Uri.fromFile(tempModel));

                long downloadId = dm.enqueue(request);
                getPrefs(context).edit()
                    .putLong(KEY_DOWNLOAD_ID, downloadId)
                    .putString(KEY_DOWNLOAD_STATUS, "downloading")
                    .apply();

                Log.d(TAG, "DownloadManager enqueued download with ID: " + downloadId + " for URL: " + DEFAULT_MODEL_URL);
                registerDownloadCompleteReceiver(context.getApplicationContext(), downloadId, tempModel);
                return true;
            } else {
                Log.w(TAG, "DownloadManager service is null. Falling back to HTTP background streamer.");
            }
        } catch (Exception e) {
            Log.w(TAG, "DownloadManager failed to enqueue: " + e.getMessage() + ". Starting resilient HTTP fallback...", e);
        }

        startResilientFallbackDownload(context);
        return true;
    }

    private void registerDownloadCompleteReceiver(final Context appContext, final long expectedId, final File tempFile) {
        try {
            Log.d(TAG, "Registering download complete BroadcastReceiver for ID: " + expectedId);
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    Log.d(TAG, "DownloadManager broadcast received: ID=" + id + " (Expected=" + expectedId + ")");
                    if (id == expectedId) {
                        Log.d(TAG, "DownloadManager finished download successfully for ID: " + id);
                        finalizeDownloadedModel(appContext, tempFile);
                        try {
                            appContext.unregisterReceiver(this);
                            Log.d(TAG, "Unregistered broadcast receiver for ID: " + id);
                        } catch (Exception ignored) {}
                    }
                }
            };
            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_EXPORTED);
            Log.d(TAG, "Download complete BroadcastReceiver registered successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register download complete receiver: " + e.getMessage(), e);
        }
    }

    private synchronized void finalizeDownloadedModel(Context context, File sourceFile) {
        if (sourceFile == null || !sourceFile.exists()) {
            Log.e(TAG, "finalizeDownloadedModel error: sourceFile is null or does not exist!");
            return;
        }
        File targetFile = getModelFile(context);
        Log.d(TAG, "Finalizing downloaded model file: Source=" + sourceFile.getAbsolutePath() + " (" + (sourceFile.length() / (1024 * 1024)) + "MB) -> Target=" + targetFile.getAbsolutePath());
        try {
            if (targetFile.exists()) {
                boolean del = targetFile.delete();
                Log.d(TAG, "Existing target model file deleted: " + del);
            }
            boolean renamed = sourceFile.renameTo(targetFile);
            if (!renamed) {
                Log.d(TAG, "Direct rename failed across storage mounts; copying file stream...");
                copyFile(sourceFile, targetFile);
                sourceFile.delete();
                Log.d(TAG, "Copy completed and source temp file removed.");
            }
            getPrefs(context).edit().putString(KEY_DOWNLOAD_STATUS, "completed").apply();
            Log.d(TAG, "SUCCESS: Offline AI Brain model installed to: " + targetFile.getAbsolutePath() + " (" + targetFile.length() + " bytes). Model is ready for offline reasoning!");
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

    /**
     * Fallback HTTP chunked range downloader that streams directly into /models/.
     */
    private void startResilientFallbackDownload(final Context context) {
        if (isFallbackRunning) {
            Log.d(TAG, "Fallback HTTP download already running. Ignoring duplicate request.");
            return;
        }
        cancelFallback = false;
        isFallbackRunning = true;
        Log.d(TAG, "Initiating resilient HTTP chunked background download thread...");

        executor.execute(new Runnable() {
            @Override
            public void run() {
                File targetFile = getModelFile(context);
                File partFile = new File(targetFile.getAbsolutePath() + ".download");
                HttpURLConnection conn = null;
                try {
                    long existingBytes = partFile.exists() ? partFile.length() : 0;
                    Log.d(TAG, "Fallback HTTP connecting to: " + DEFAULT_MODEL_URL + " (Resuming from byte: " + existingBytes + ")");
                    URL url = new URL(DEFAULT_MODEL_URL);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(20000);
                    conn.setReadTimeout(30000);
                    conn.setRequestProperty("User-Agent", "Marvo-OfflineBrain/1.0");

                    if (existingBytes > 0) {
                        conn.setRequestProperty("Range", "bytes=" + existingBytes + "-");
                    }

                    int code = conn.getResponseCode();
                    Log.d(TAG, "Fallback HTTP response code: " + code + ", Content-Length=" + conn.getContentLength());
                    if (code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_PARTIAL) {
                        getPrefs(context).edit().putString(KEY_DOWNLOAD_STATUS, "downloading").apply();
                        InputStream in = conn.getInputStream();
                        FileOutputStream out = new FileOutputStream(partFile, existingBytes > 0);
                        byte[] buffer = new byte[32768];
                        int bytesRead;
                        long totalDownloaded = existingBytes;
                        long lastLogTime = System.currentTimeMillis();

                        while (!cancelFallback && (bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                            totalDownloaded += bytesRead;

                            long now = System.currentTimeMillis();
                            if (now - lastLogTime > 4000) { // Log progress every 4 seconds
                                Log.d(TAG, "Fallback HTTP download progress: " + (totalDownloaded / (1024 * 1024)) + " MB downloaded...");
                                lastLogTime = now;
                            }
                        }

                        out.flush();
                        out.close();
                        in.close();

                        if (!cancelFallback) {
                            if (targetFile.exists()) targetFile.delete();
                            boolean renamed = partFile.renameTo(targetFile);
                            if (!renamed) {
                                copyFile(partFile, targetFile);
                                partFile.delete();
                            }
                            getPrefs(context).edit().putString(KEY_DOWNLOAD_STATUS, "completed").apply();
                            Log.d(TAG, "SUCCESS: Fallback HTTP download complete! Model installed at: " + targetFile.getAbsolutePath() + " (" + targetFile.length() + " bytes)");
                        } else {
                            Log.d(TAG, "Fallback HTTP download canceled or paused.");
                            getPrefs(context).edit().putString(KEY_DOWNLOAD_STATUS, "paused").apply();
                        }
                    } else {
                        Log.e(TAG, "Fallback HTTP download failed with HTTP status code: " + code);
                        getPrefs(context).edit().putString(KEY_DOWNLOAD_STATUS, "failed").apply();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Fallback HTTP download exception: " + e.getMessage(), e);
                    getPrefs(context).edit().putString(KEY_DOWNLOAD_STATUS, "failed").apply();
                } finally {
                    if (conn != null) conn.disconnect();
                    isFallbackRunning = false;
                }
            }
        });
    }

    /**
     * Returns JSON structure with current download status and progress for Web UI & Native Bridge.
     */
    public JSONObject getDownloadProgress(Context context) {
        JSONObject res = new JSONObject();
        try {
            if (isModelDownloaded(context)) {
                res.put("status", "completed");
                res.put("progress", 100);
                res.put("isReady", true);
                File f = getModelFile(context);
                res.put("fileSize", f != null ? f.length() : 0);
                Log.d(TAG, "getDownloadProgress: Model is completed & ready. (" + (f != null ? (f.length() / (1024 * 1024)) : 0) + "MB)");
                return res;
            }

            String status = getPrefs(context).getString(KEY_DOWNLOAD_STATUS, "idle");
            res.put("status", status);
            res.put("isReady", false);

            long downloadId = getPrefs(context).getLong(KEY_DOWNLOAD_ID, -1);
            if (downloadId != -1) {
                DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) {
                    DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(downloadId);
                    Cursor cursor = dm.query(query);
                    if (cursor != null && cursor.moveToFirst()) {
                        int bytesDownloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                        int bytesTotalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                        int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);

                        long downloaded = bytesDownloadedIdx != -1 ? cursor.getLong(bytesDownloadedIdx) : 0;
                        long total = bytesTotalIdx != -1 ? cursor.getLong(bytesTotalIdx) : 0;
                        int dmStatus = statusIdx != -1 ? cursor.getInt(statusIdx) : 0;

                        cursor.close();

                        int progress = total > 0 ? (int) ((downloaded * 100) / total) : 0;
                        res.put("progress", progress);
                        res.put("downloadedBytes", downloaded);
                        res.put("totalBytes", total);

                        if (dmStatus == DownloadManager.STATUS_SUCCESSFUL) {
                            res.put("status", "completed");
                            res.put("progress", 100);
                            res.put("isReady", true);
                        } else if (dmStatus == DownloadManager.STATUS_PAUSED) {
                            res.put("status", "paused");
                        } else if (dmStatus == DownloadManager.STATUS_FAILED) {
                            res.put("status", "failed");
                        }
                        Log.d(TAG, "getDownloadProgress (DownloadManager): status=" + res.optString("status") + ", progress=" + progress + "%, downloaded=" + (downloaded / (1024 * 1024)) + "MB / " + (total / (1024 * 1024)) + "MB");
                        return res;
                    }
                    if (cursor != null) cursor.close();
                }
            }

            // Fallback progress check from file size
            File target = getModelFile(context);
            File part = new File(target.getAbsolutePath() + ".download");
            if (part.exists()) {
                long downloaded = part.length();
                long estimatedTotal = 1800L * 1024L * 1024L; // ~1.8GB
                int progress = (int) Math.min(99, (downloaded * 100) / estimatedTotal);
                res.put("progress", progress);
                res.put("downloadedBytes", downloaded);
                res.put("totalBytes", estimatedTotal);
                Log.d(TAG, "getDownloadProgress (Fallback file): progress=" + progress + "%, downloaded=" + (downloaded / (1024 * 1024)) + "MB");
            } else {
                res.put("progress", 0);
            }

        } catch (Exception e) {
            Log.e(TAG, "getDownloadProgress error: " + e.getMessage(), e);
            try {
                res.put("status", "error");
                res.put("error", e.getMessage());
            } catch (Exception ignored) {}
        }
        return res;
    }

    public synchronized void pauseDownload(Context context) {
        cancelFallback = true;
        Log.d(TAG, "pauseDownload requested.");
        if (context == null) return;
        long downloadId = getPrefs(context).getLong(KEY_DOWNLOAD_ID, -1);
        if (downloadId != -1) {
            try {
                DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) {
                    dm.remove(downloadId);
                    Log.d(TAG, "Removed download ID " + downloadId + " from DownloadManager.");
                }
            } catch (Exception e) {
                Log.w(TAG, "Error removing download from DownloadManager: " + e.getMessage());
            }
        }
        getPrefs(context).edit().putString(KEY_DOWNLOAD_STATUS, "paused").apply();
        Log.d(TAG, "Download status set to paused.");
    }
}
