package com.marvo.ai;

import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Step 21: Capacitor Native Bridge (MarvoNativeBridge).
 * Seamlessly connects the Capacitor frontend (JavaScript) with native Android systems.
 * Provides bridge methods for settings, model download monitoring, and offline inference.
 */
@CapacitorPlugin(name = "MarvoNativeBridge")
public class MarvoNativeBridge extends Plugin {
    private static final String TAG = "MarvoNativeBridge";

    @PluginMethod
    public void openSettings(PluginCall call) {
        try {
            Context context = getContext();
            Intent intent = new Intent(context, NativeSettingsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            call.resolve();
        } catch (Exception e) {
            Log.e(TAG, "Error opening settings from JS bridge: " + e.getMessage(), e);
            call.reject("Failed to open native settings: " + e.getMessage());
        }
    }

    @PluginMethod
    public void getModelDownloadProgress(PluginCall call) {
        try {
            Context context = getContext();
            JSONObject progressJson = OfflineBrainDownloader.getInstance().getDownloadProgress(context);
            JSObject res = new JSObject();
            res.put("status", progressJson.optString("status", "idle"));
            res.put("progress", progressJson.optInt("progress", 0));
            res.put("isReady", progressJson.optBoolean("isReady", false));
            res.put("downloadedBytes", progressJson.optLong("downloadedBytes", 0));
            res.put("totalBytes", progressJson.optLong("totalBytes", 0));
            call.resolve(res);
        } catch (Exception e) {
            Log.e(TAG, "Error checking download progress: " + e.getMessage(), e);
            call.reject("Error fetching progress: " + e.getMessage());
        }
    }

    @PluginMethod
    public void startModelDownload(PluginCall call) {
        try {
            Context context = getContext();
            boolean allowMetered = call.getBoolean("allowMetered", false);
            OfflineBrainDownloader.startForegroundDownloadService(context, allowMetered);
            JSObject res = new JSObject();
            res.put("started", true);
            call.resolve(res);
        } catch (Exception e) {
            Log.e(TAG, "Error starting download: " + e.getMessage(), e);
            call.reject("Failed to start download: " + e.getMessage());
        }
    }

    @PluginMethod
    public void pauseModelDownload(PluginCall call) {
        try {
            Context context = getContext();
            OfflineBrainDownloader.getInstance().pauseDownload(context);
            call.resolve();
        } catch (Exception e) {
            call.reject("Error pausing download: " + e.getMessage());
        }
    }

    @PluginMethod
    public void cancelModelDownload(PluginCall call) {
        try {
            Context context = getContext();
            OfflineBrainDownloader.getInstance().cancelDownload(context);
            call.resolve();
        } catch (Exception e) {
            call.reject("Error canceling download: " + e.getMessage());
        }
    }

    @PluginMethod
    public void getCustomQA(PluginCall call) {
        try {
            Context context = getContext();
            String qaJson = MemoryVault.getAllCustomQA(context);
            JSObject res = new JSObject();
            res.put("data", new JSONArray(qaJson));
            call.resolve(res);
        } catch (Exception e) {
            call.reject("Error fetching custom QA: " + e.getMessage());
        }
    }

    @PluginMethod
    public void saveCustomQA(PluginCall call) {
        try {
            String question = call.getString("question");
            String answer = call.getString("answer");
            if (question == null || answer == null) {
                call.reject("Question and answer are required");
                return;
            }
            Context context = getContext();
            MemoryVault.saveCustomQA(context, question, answer);
            JSObject res = new JSObject();
            res.put("success", true);
            call.resolve(res);
        } catch (Exception e) {
            call.reject("Error saving custom QA: " + e.getMessage());
        }
    }

    @PluginMethod
    public void isModelReady(PluginCall call) {
        try {
            Context context = getContext();
            boolean ready = OfflineBrainManager.getInstance(context).isModelReady();
            JSObject res = new JSObject();
            res.put("ready", ready);
            call.resolve(res);
        } catch (Exception e) {
            call.reject("Error checking model state: " + e.getMessage());
        }
    }

    @PluginMethod
    public void triggerOfflineQuery(PluginCall call) {
        String query = call.getString("query");
        if (query == null || query.trim().isEmpty()) {
            call.reject("Empty query");
            return;
        }
        Context context = getContext();
        OfflineBrainManager.getInstance(context).generateResponse(query, new OfflineBrainManager.GenerationCallback() {
            @Override
            public void onResponse(String fullResponse, String coreSpeech) {
                JSObject res = new JSObject();
                res.put("response", fullResponse);
                res.put("speech", coreSpeech);
                call.resolve(res);
            }

            @Override
            public void onError(String errorMessage) {
                call.reject("Offline inference failed: " + errorMessage);
            }
        });
    }

    @PluginMethod
    public void saveToUnifiedDownloads(PluginCall call) {
        try {
            String base64Data = call.getString("base64Data");
            String fileName = call.getString("fileName");
            String mimeType = call.getString("mimeType", "image/png");

            if (fileName == null || fileName.trim().isEmpty()) {
                fileName = "Marvo_" + System.currentTimeMillis() + ".png";
            }
            fileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");

            File downloadsDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Marvo");
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs();
            }

            File targetFile = new File(downloadsDir, fileName);

            if (base64Data != null && !base64Data.trim().isEmpty()) {
                if (base64Data.contains(",")) {
                    base64Data = base64Data.substring(base64Data.indexOf(",") + 1);
                }
                byte[] decoded = Base64.decode(base64Data, Base64.DEFAULT);
                try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                    fos.write(decoded);
                    fos.flush();
                }
            } else {
                String textContent = call.getString("textContent", "");
                try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                    fos.write(textContent.getBytes("UTF-8"));
                    fos.flush();
                }
            }

            final Context context = getContext();
            MediaScannerConnection.scanFile(context,
                    new String[]{targetFile.getAbsolutePath()},
                    new String[]{mimeType},
                    null);

            JSObject res = new JSObject();
            res.put("success", true);
            res.put("filePath", targetFile.getAbsolutePath());
            res.put("fileName", targetFile.getName());
            call.resolve(res);
        } catch (Exception e) {
            Log.e(TAG, "Error saving to unified downloads: " + e.getMessage(), e);
            call.reject("Failed to save file: " + e.getMessage());
        }
    }

    @PluginMethod
    public void openUnifiedDownloadsFolder(PluginCall call) {
        try {
            Context context = getContext();
            Intent intent = new Intent(Intent.ACTION_VIEW);
            File downloadsDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Marvo");
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs();
            }
            intent.setDataAndType(android.net.Uri.parse(downloadsDir.getAbsolutePath()), "*/*");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(Intent.createChooser(intent, "Open Marvo Downloads").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            call.resolve();
        } catch (Exception e) {
            Log.e(TAG, "Error opening downloads folder: " + e.getMessage(), e);
            call.reject("Failed to open downloads folder: " + e.getMessage());
        }
    }
}

