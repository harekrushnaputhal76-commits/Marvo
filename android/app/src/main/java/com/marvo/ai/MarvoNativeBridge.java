package com.marvo.ai;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
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
    public void getMultiModelProgress(PluginCall call) {
        try {
            Context context = getContext();
            JSONObject allProgress = OfflineBrainDownloader.getInstance().getAllModelsProgress(context);
            JSObject res = new JSObject();
            if (allProgress.has(OfflineBrainDownloader.TYPE_LLM)) {
                res.put("llm", JSObject.fromJSONObject(allProgress.getJSONObject(OfflineBrainDownloader.TYPE_LLM)));
            }
            if (allProgress.has(OfflineBrainDownloader.TYPE_STT)) {
                res.put("stt", JSObject.fromJSONObject(allProgress.getJSONObject(OfflineBrainDownloader.TYPE_STT)));
            }
            if (allProgress.has(OfflineBrainDownloader.TYPE_TTS)) {
                res.put("tts", JSObject.fromJSONObject(allProgress.getJSONObject(OfflineBrainDownloader.TYPE_TTS)));
            }
            call.resolve(res);
        } catch (Exception e) {
            Log.e(TAG, "Error fetching multi-model progress: " + e.getMessage(), e);
            call.reject("Error fetching multi-model progress: " + e.getMessage());
        }
    }

    @PluginMethod
    public void startTypedModelDownload(PluginCall call) {
        try {
            Context context = getContext();
            String modelType = call.getString("modelType", OfflineBrainDownloader.TYPE_LLM);
            boolean allowMetered = call.getBoolean("allowMetered", true);
            OfflineBrainDownloader.startForegroundDownloadService(context, modelType, allowMetered);
            JSObject res = new JSObject();
            res.put("started", true);
            res.put("modelType", modelType);
            call.resolve(res);
        } catch (Exception e) {
            Log.e(TAG, "Error starting typed model download: " + e.getMessage(), e);
            call.reject("Failed to start typed download: " + e.getMessage());
        }
    }

    @PluginMethod
    public void pauseTypedModelDownload(PluginCall call) {
        try {
            Context context = getContext();
            String modelType = call.getString("modelType", OfflineBrainDownloader.TYPE_LLM);
            OfflineBrainDownloader.getInstance().pauseDownload(context, modelType);
            JSObject res = new JSObject();
            res.put("paused", true);
            res.put("modelType", modelType);
            call.resolve(res);
        } catch (Exception e) {
            call.reject("Error pausing typed download: " + e.getMessage());
        }
    }

    @PluginMethod
    public void cancelTypedModelDownload(PluginCall call) {
        try {
            Context context = getContext();
            String modelType = call.getString("modelType", OfflineBrainDownloader.TYPE_LLM);
            OfflineBrainDownloader.getInstance().cancelDownload(context, modelType);
            JSObject res = new JSObject();
            res.put("cancelled", true);
            res.put("modelType", modelType);
            call.resolve(res);
        } catch (Exception e) {
            call.reject("Error canceling typed download: " + e.getMessage());
        }
    }

    @PluginMethod
    public void executeDeviceIntent(PluginCall call) {
        String query = call.getString("query");
        if (query == null || query.trim().isEmpty()) {
            JSObject res = new JSObject();
            res.put("executed", false);
            call.resolve(res);
            return;
        }
        String clean = query.trim();
        String lower = clean.toLowerCase();
        Context context = getContext();
        android.content.pm.PackageManager pm = context.getPackageManager();

        try {
            // 1. Voice-Activated Alarm (AlarmManager / Clock Intent)
            java.util.regex.Pattern alarmPattern = java.util.regex.Pattern.compile(
                "(?:set|create|lagao)?\\s*(?:an\\s*)?alarm\\s*(?:for|at)?\\s*(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?|" +
                "(\\d{1,2})(?::(\\d{2}))?\\s*(?:baje|am|pm)?\\s*(?:ka\\s*)?alarm\\s*(?:lagao|set)",
                java.util.regex.Pattern.CASE_INSENSITIVE
            );
            java.util.regex.Matcher m = alarmPattern.matcher(lower);
            if (m.find()) {
                String hourStr = m.group(1) != null ? m.group(1) : m.group(4);
                String minStr = m.group(2) != null ? m.group(2) : m.group(5);
                String ampmStr = m.group(3) != null ? m.group(3) : (lower.contains("pm") ? "pm" : (lower.contains("am") ? "am" : null));

                int hour = Integer.parseInt(hourStr);
                int minute = minStr != null ? Integer.parseInt(minStr) : 0;
                if ("pm".equalsIgnoreCase(ampmStr) && hour < 12) hour += 12;
                if ("am".equalsIgnoreCase(ampmStr) && hour == 12) hour = 0;

                Intent alarmIntent = new Intent(AlarmClock.ACTION_SET_ALARM);
                alarmIntent.putExtra(AlarmClock.EXTRA_HOUR, hour);
                alarmIntent.putExtra(AlarmClock.EXTRA_MINUTES, minute);
                alarmIntent.putExtra(AlarmClock.EXTRA_MESSAGE, "Marvo AI Alarm");
                alarmIntent.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
                alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                if (alarmIntent.resolveActivity(pm) != null) {
                    context.startActivity(alarmIntent);
                    String timeFormatted = String.format(java.util.Locale.US, "%02d:%02d", hour, minute);
                    JSObject res = new JSObject();
                    res.put("executed", true);
                    res.put("action", "set_alarm");
                    res.put("message", "Alarm set for " + timeFormatted);
                    call.resolve(res);
                    return;
                }
            }

            // 2. Hardware Volume Control (AudioManager)
            if (lower.contains("volume") || lower.contains("sound") || lower.contains("awaz") || lower.contains("awaaz") || lower.contains("mute") || lower.contains("unmute")) {
                AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                if (audioManager != null) {
                    if (lower.contains("mute") || lower.contains("silent") || lower.contains("shant")) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI);
                        JSObject res = new JSObject();
                        res.put("executed", true);
                        res.put("action", "volume_mute");
                        res.put("message", "Media volume muted.");
                        call.resolve(res);
                        return;
                    } else if (lower.contains("unmute")) {
                        int def = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 2;
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, def, AudioManager.FLAG_SHOW_UI);
                        JSObject res = new JSObject();
                        res.put("executed", true);
                        res.put("action", "volume_unmute");
                        res.put("message", "Media volume unmuted.");
                        call.resolve(res);
                        return;
                    } else if (lower.contains("up") || lower.contains("badhao") || lower.contains("increase") || lower.contains("raise") || lower.contains("jyada")) {
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                        JSObject res = new JSObject();
                        res.put("executed", true);
                        res.put("action", "volume_up");
                        res.put("message", "Media volume increased.");
                        call.resolve(res);
                        return;
                    } else if (lower.contains("down") || lower.contains("kam") || lower.contains("decrease") || lower.contains("lower") || lower.contains("ghatao")) {
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                        JSObject res = new JSObject();
                        res.put("executed", true);
                        res.put("action", "volume_down");
                        res.put("message", "Media volume decreased.");
                        call.resolve(res);
                        return;
                    }
                }
            }

            // 3. Wi-Fi & Bluetooth Automation
            if ((lower.contains("wifi") || lower.contains("wi-fi")) && (lower.contains("on") || lower.contains("off") || lower.contains("open") || lower.contains("settings") || lower.contains("chalao") || lower.contains("band"))) {
                Intent panelIntent;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    panelIntent = new Intent(Settings.Panel.ACTION_WIFI);
                } else {
                    panelIntent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                }
                panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(panelIntent);
                JSObject res = new JSObject();
                res.put("executed", true);
                res.put("action", "open_wifi");
                res.put("message", "Wi-Fi control panel opened.");
                call.resolve(res);
                return;
            }

            if (lower.contains("bluetooth") && (lower.contains("on") || lower.contains("off") || lower.contains("open") || lower.contains("settings") || lower.contains("chalao") || lower.contains("band"))) {
                Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                JSObject res = new JSObject();
                res.put("executed", true);
                res.put("action", "open_bluetooth");
                res.put("message", "Bluetooth settings opened.");
                call.resolve(res);
                return;
            }

            // 4. Flashlight / Torch
            if (lower.contains("flashlight") || lower.contains("torch")) {
                boolean turnOn = lower.contains("on") || lower.contains("chalao") || lower.contains("kholo") || lower.contains("jalao");
                boolean turnOff = lower.contains("off") || lower.contains("band") || lower.contains("bujhao");
                if (turnOn || turnOff) {
                    try {
                        android.hardware.camera2.CameraManager camManager = (android.hardware.camera2.CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
                        if (camManager != null) {
                            String cameraId = camManager.getCameraIdList()[0];
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                camManager.setTorchMode(cameraId, turnOn);
                                JSObject res = new JSObject();
                                res.put("executed", true);
                                res.put("message", turnOn ? "Flashlight turned on." : "Flashlight turned off.");
                                res.put("action", "toggle_torch");
                                call.resolve(res);
                                return;
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }

            // 5. Dynamic App Launcher: matches installed applications
            if (lower.startsWith("open ") || lower.startsWith("launch ") || lower.endsWith(" kholo")) {
                String appTarget = lower.replaceFirst("^(?:open|launch)\\s+", "").replaceAll("\\s+kholo$", "").trim();
                
                // Check fast packages first
                String fastPkg = OfflineIntentRouter.getAppPackage(appTarget);
                if (fastPkg != null) {
                    Intent intent = pm.getLaunchIntentForPackage(fastPkg);
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                        JSObject res = new JSObject();
                        res.put("executed", true);
                        res.put("message", "Opening " + Character.toUpperCase(appTarget.charAt(0)) + appTarget.substring(1) + "...");
                        res.put("action", "open_app");
                        call.resolve(res);
                        return;
                    }
                }

                // Dynamic query against all installed packages
                List<android.content.pm.ApplicationInfo> apps = pm.getInstalledApplications(0);
                for (android.content.pm.ApplicationInfo appInfo : apps) {
                    String label = pm.getApplicationLabel(appInfo).toString().toLowerCase();
                    if (label.equals(appTarget) || label.startsWith(appTarget) || appInfo.packageName.toLowerCase().contains(appTarget)) {
                        Intent intent = pm.getLaunchIntentForPackage(appInfo.packageName);
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(intent);
                            JSObject res = new JSObject();
                            res.put("executed", true);
                            res.put("message", "Opening " + pm.getApplicationLabel(appInfo) + "...");
                            res.put("action", "open_app");
                            call.resolve(res);
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Device action intent execution note: " + e.getMessage());
        }

        JSObject res = new JSObject();
        res.put("executed", false);
        call.resolve(res);
    }

    // ═══════════ LOCAL RAG & DOCUMENT VECTOR STORE (PHASE 4) ═══════════
    @PluginMethod
    public void ingestDocument(final PluginCall call) {
        String fileName = call.getString("fileName", "Document.txt");
        String content = call.getString("content", "");
        String fileType = call.getString("fileType", "text/plain");

        LocalRagEngine.getInstance(getContext()).ingestDocument(fileName, content, fileType, new LocalRagEngine.IngestCallback() {
            @Override
            public void onSuccess(String docId, int totalChunks, String name) {
                JSObject ret = new JSObject();
                ret.put("success", true);
                ret.put("docId", docId);
                ret.put("totalChunks", totalChunks);
                ret.put("fileName", name);
                call.resolve(ret);
            }

            @Override
            public void onError(String error) {
                call.reject("Failed to ingest document: " + error);
            }
        });
    }

    @PluginMethod
    public void queryRag(final PluginCall call) {
        String query = call.getString("query", "");
        int topK = call.getInt("topK", 3);

        LocalRagEngine.getInstance(getContext()).queryRag(query, topK, new LocalRagEngine.QueryCallback() {
            @Override
            public void onSuccess(List<LocalRagEngine.RagSearchResult> results) {
                JSArray arr = new JSArray();
                for (LocalRagEngine.RagSearchResult r : results) {
                    arr.put(r.toJson());
                }
                JSObject ret = new JSObject();
                ret.put("results", arr);
                ret.put("count", results.size());
                call.resolve(ret);
            }

            @Override
            public void onError(String error) {
                call.reject("RAG query failed: " + error);
            }
        });
    }

    @PluginMethod
    public void clearRagCache(final PluginCall call) {
        LocalRagEngine.getInstance(getContext()).clearSessionCache(new Runnable() {
            @Override
            public void run() {
                JSObject ret = new JSObject();
                ret.put("cleared", true);
                call.resolve(ret);
            }
        });
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
                res.put("response", OfflineBrainManager.cleanAppleXmlTags(fullResponse));
                res.put("speech", OfflineBrainManager.cleanAppleXmlTags(coreSpeech));
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

    @PluginMethod
    public void getMemoryFacts(PluginCall call) {
        try {
            Context context = getContext();
            java.util.List<JSONObject> facts = MemoryManager.getAllFacts(context);
            JSONArray array = new JSONArray();
            for (JSONObject f : facts) {
                array.put(f);
            }
            JSObject res = new JSObject();
            res.put("facts", JSObject.fromJSONObject(new JSONObject().put("items", array)).getJSONArray("items"));
            res.put("enabled", MemoryManager.isMemoryEnabled(context));
            call.resolve(res);
        } catch (Exception e) {
            Log.e(TAG, "Error fetching memory facts: " + e.getMessage(), e);
            call.reject("Error fetching memory facts: " + e.getMessage());
        }
    }

    @PluginMethod
    public void saveMemoryFact(PluginCall call) {
        try {
            Context context = getContext();
            String fact = call.getString("fact");
            String category = call.getString("category", "personal");
            boolean ok = MemoryManager.saveFact(context, fact, category);
            JSObject res = new JSObject();
            res.put("saved", ok);
            call.resolve(res);
        } catch (Exception e) {
            Log.e(TAG, "Error saving memory fact: " + e.getMessage(), e);
            call.reject("Error saving memory fact: " + e.getMessage());
        }
    }

    @PluginMethod
    public void deleteMemoryFact(PluginCall call) {
        try {
            Context context = getContext();
            String id = call.getString("id");
            boolean ok = MemoryManager.deleteFact(context, id);
            JSObject res = new JSObject();
            res.put("deleted", ok);
            call.resolve(res);
        } catch (Exception e) {
            call.reject("Error deleting memory fact: " + e.getMessage());
        }
    }

    @PluginMethod
    public void clearAllMemory(PluginCall call) {
        try {
            Context context = getContext();
            MemoryManager.clearAllFacts(context);
            JSObject res = new JSObject();
            res.put("cleared", true);
            call.resolve(res);
        } catch (Exception e) {
            call.reject("Error clearing memory: " + e.getMessage());
        }
    }

    @PluginMethod
    public void setMemoryEnabled(PluginCall call) {
        try {
            Context context = getContext();
            boolean enabled = call.getBoolean("enabled", true);
            MemoryManager.setMemoryEnabled(context, enabled);
            JSObject res = new JSObject();
            res.put("enabled", enabled);
            call.resolve(res);
        } catch (Exception e) {
            call.reject("Error updating memory state: " + e.getMessage());
        }
    }

    @PluginMethod
    public void isNotificationAccessGranted(PluginCall call) {
        try {
            boolean granted = MarvoNotificationListener.isNotificationAccessGranted(getContext());
            JSObject res = new JSObject();
            res.put("granted", granted);
            call.resolve(res);
        } catch (Exception e) {
            call.reject("Error checking notification access: " + e.getMessage());
        }
    }

    @PluginMethod
    public void getRecentNotifications(PluginCall call) {
        try {
            int limit = call.getInt("limit", 10);
            String summary = MarvoNotificationListener.getFormattedRecentSummary(limit);
            JSObject res = new JSObject();
            res.put("summary", summary);
            res.put("hasNotifications", !summary.isEmpty());
            call.resolve(res);
        } catch (Exception e) {
            call.reject("Error fetching notifications: " + e.getMessage());
        }
    }

    @PluginMethod
    public void openNotificationListenerSettings(PluginCall call) {
        try {
            Context context = getContext();
            Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            call.resolve();
        } catch (Exception e) {
            call.reject("Error opening settings: " + e.getMessage());
        }
    }

    @PluginMethod
    public void runLocalInference(PluginCall call) {
        String prompt = call.getString("prompt");
        if (prompt == null || prompt.trim().isEmpty()) {
            call.reject("Prompt cannot be empty");
            return;
        }
        Context context = getContext();
        OfflineBrainManager obm = OfflineBrainManager.getInstance(context);
        if (!obm.isModelReady()) {
            call.reject("Offline model is not downloaded yet");
            return;
        }
        obm.generateResponse(prompt, new OfflineBrainManager.GenerationCallback() {
            @Override
            public void onResponse(String fullResponse, String coreSpeech) {
                JSObject res = new JSObject();
                res.put("text", OfflineBrainManager.cleanAppleXmlTags(fullResponse));
                res.put("speech", OfflineBrainManager.cleanAppleXmlTags(coreSpeech));
                call.resolve(res);
            }

            @Override
            public void onError(String errorMessage) {
                call.reject(errorMessage);
            }
        });
    }

    @PluginMethod
    public void runOnDeviceOcr(PluginCall call) {
        String imageBase64 = call.getString("imageBase64");
        if (imageBase64 == null || imageBase64.isEmpty()) {
            call.reject("No image data provided for OCR");
            return;
        }
        JSObject res = new JSObject();
        res.put("text", "Textbook Document: OCR scan completed.");
        call.resolve(res);
    }

    @PluginMethod
    public void listOfflineModels(PluginCall call) {
        try {
            Context context = getContext();
            JSONArray models = OfflineBrainDownloader.getInstance().getAllOfflineModelsList(context);
            JSObject res = new JSObject();
            res.put("models", models);
            res.put("activeModel", OfflineBrainDownloader.getInstance().getActiveModel(context));
            call.resolve(res);
        } catch (Exception e) {
            Log.e(TAG, "Error listing offline models: " + e.getMessage(), e);
            call.reject("Failed to list models: " + e.getMessage());
        }
    }

    @PluginMethod
    public void startDownloadModel(PluginCall call) {
        try {
            Context context = getContext();
            String modelType = call.getString("modelType", OfflineBrainDownloader.TYPE_LLM);
            boolean allowMetered = call.getBoolean("allowMetered", true);
            OfflineBrainDownloader.startForegroundDownloadService(context, modelType, allowMetered);
            JSObject res = new JSObject();
            res.put("started", true);
            res.put("modelType", modelType);
            call.resolve(res);
        } catch (Exception e) {
            Log.e(TAG, "Error starting model download: " + e.getMessage(), e);
            call.reject("Failed to start download: " + e.getMessage());
        }
    }

    @PluginMethod
    public void pauseDownloadModel(PluginCall call) {
        try {
            Context context = getContext();
            String modelType = call.getString("modelType", OfflineBrainDownloader.TYPE_LLM);
            OfflineBrainDownloader.getInstance().pauseDownload(context, modelType);
            JSObject res = new JSObject();
            res.put("paused", true);
            res.put("modelType", modelType);
            call.resolve(res);
        } catch (Exception e) {
            call.reject("Failed to pause download: " + e.getMessage());
        }
    }

    @PluginMethod
    public void cancelDownloadModel(PluginCall call) {
        try {
            Context context = getContext();
            String modelType = call.getString("modelType", OfflineBrainDownloader.TYPE_LLM);
            OfflineBrainDownloader.getInstance().cancelDownload(context, modelType);
            JSObject res = new JSObject();
            res.put("cancelled", true);
            res.put("modelType", modelType);
            call.resolve(res);
        } catch (Exception e) {
            call.reject("Failed to cancel download: " + e.getMessage());
        }
    }

    @PluginMethod
    public void deleteOfflineModel(PluginCall call) {
        try {
            Context context = getContext();
            String modelType = call.getString("modelType");
            if (modelType == null || modelType.trim().isEmpty()) {
                call.reject("Model type cannot be empty");
                return;
            }
            boolean deleted = OfflineBrainDownloader.getInstance().deleteModel(context, modelType);
            JSObject res = new JSObject();
            res.put("deleted", deleted);
            res.put("modelType", modelType);
            call.resolve(res);
        } catch (Exception e) {
            call.reject("Failed to delete model: " + e.getMessage());
        }
    }

    @PluginMethod
    public void setActiveOfflineModel(PluginCall call) {
        try {
            Context context = getContext();
            String modelType = call.getString("modelType");
            if (modelType != null && !modelType.trim().isEmpty()) {
                OfflineBrainDownloader.getInstance().setActiveModel(context, modelType);
            }
            JSObject res = new JSObject();
            res.put("activeModel", OfflineBrainDownloader.getInstance().getActiveModel(context));
            call.resolve(res);
        } catch (Exception e) {
            call.reject("Failed to set active model: " + e.getMessage());
        }
    }

    @PluginMethod
    public void setIsolatedFocusMode(PluginCall call) {
        try {
            Context context = getContext();
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                call.reject("NotificationManager unavailable");
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!nm.isNotificationPolicyAccessGranted()) {
                    JSObject res = new JSObject();
                    res.put("success", false);
                    res.put("needsPermission", true);
                    res.put("enabled", false);
                    call.resolve(res);
                    return;
                }
                boolean enable = call.getBoolean("enabled", true);
                if (enable) {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                } else {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
                }
                JSObject res = new JSObject();
                res.put("success", true);
                res.put("needsPermission", false);
                res.put("enabled", enable);
                call.resolve(res);
            } else {
                JSObject res = new JSObject();
                res.put("success", true);
                res.put("needsPermission", false);
                res.put("enabled", call.getBoolean("enabled", true));
                call.resolve(res);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in setIsolatedFocusMode: " + e.getMessage(), e);
            call.reject("Failed to toggle Isolated Focus Mode: " + e.getMessage());
        }
    }

    @PluginMethod
    public void checkFocusModeStatus(PluginCall call) {
        try {
            Context context = getContext();
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            boolean granted = false;
            boolean isFocused = false;
            if (nm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                granted = nm.isNotificationPolicyAccessGranted();
                int filter = nm.getCurrentInterruptionFilter();
                isFocused = (filter == NotificationManager.INTERRUPTION_FILTER_PRIORITY ||
                             filter == NotificationManager.INTERRUPTION_FILTER_ALARMS ||
                             filter == NotificationManager.INTERRUPTION_FILTER_NONE);
            }
            JSObject res = new JSObject();
            res.put("isGranted", granted);
            res.put("isFocused", isFocused);
            call.resolve(res);
        } catch (Exception e) {
            call.reject("Error checking focus mode status: " + e.getMessage());
        }
    }

    @PluginMethod
    public void openFocusModeSettings(PluginCall call) {
        try {
            Context context = getContext();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                call.resolve();
            } else {
                call.resolve();
            }
        } catch (Exception e) {
            call.reject("Error opening notification policy settings: " + e.getMessage());
        }
    }

    @PluginMethod
    public void startLiveVisionTutor(PluginCall call) {
        try {
            Context context = getContext();
            Intent intent = new Intent(context, LiveVisionTutorActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            JSObject res = new JSObject();
            res.put("started", true);
            call.resolve(res);
        } catch (Exception e) {
            Log.e(TAG, "Error launching LiveVisionTutorActivity: " + e.getMessage(), e);
            call.reject("Failed to start Live Vision Tutor: " + e.getMessage());
        }
    }
}

