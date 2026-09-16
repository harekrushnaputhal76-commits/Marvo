package com.marvo.ai;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowManager;
import androidx.core.content.ContextCompat;

/**
 * Traffic Police V3/V4: Offline Device Controller.
 * Controls Flashlight, WiFi, Bluetooth, Volume, and Screen Brightness locally
 * with 0ms network latency and zero network battery consumption.
 */
public class OfflineDeviceController {
    private static final String TAG = "OfflineDeviceController";

    public static class DeviceActionResult {
        public final boolean handled;
        public final String spokenResponse;
        public final String pillMessage;
        public final int pillIcon;

        public DeviceActionResult(boolean handled, String spokenResponse, String pillMessage, int pillIcon) {
            this.handled = handled;
            this.spokenResponse = spokenResponse;
            this.pillMessage = pillMessage;
            this.pillIcon = pillIcon;
        }

        public static DeviceActionResult notHandled() {
            return new DeviceActionResult(false, null, null, 0);
        }

        public static DeviceActionResult success(String spokenResponse, String pillMessage, int pillIcon) {
            return new DeviceActionResult(true, spokenResponse, pillMessage, pillIcon);
        }
    }

    private static boolean isFlashlightOn = false;

    /**
     * Executes local hardware control intent.
     */
    public static DeviceActionResult execute(Activity activity, String query) {
        if (activity == null || query == null) return DeviceActionResult.notHandled();
        String lower = query.trim().toLowerCase();

        // 1. Flashlight / Torch
        if (lower.contains("flashlight") || lower.contains("torch")) {
            return handleFlashlight(activity, lower);
        }

        // 2. WiFi
        if (lower.contains("wifi") || lower.contains("wi-fi")) {
            return handleWifi(activity, lower);
        }

        // 3. Bluetooth
        if (lower.contains("bluetooth")) {
            return handleBluetooth(activity, lower);
        }

        // 4. Volume / Sound / Awaz
        if (lower.contains("volume") || lower.contains("sound") || lower.contains("awaz") ||
            lower.contains("awaaz") || lower.contains("mute") || lower.contains("unmute")) {
            return handleVolume(activity, lower);
        }

        // 5. Screen Brightness / Roshni
        if (lower.contains("brightness") || lower.contains("roshni")) {
            return handleBrightness(activity, lower);
        }

        return DeviceActionResult.notHandled();
    }

    // --- Flashlight Control ---
    private static DeviceActionResult handleFlashlight(Context context, String lower) {
        try {
            CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cm == null) {
                return DeviceActionResult.success("Camera service uplabdh nahi hai.", "Flashlight Error", android.R.drawable.stat_notify_error);
            }

            String cameraId = null;
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics c = cm.getCameraCharacteristics(id);
                Boolean flashAvailable = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (flashAvailable != null && flashAvailable && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id;
                    break;
                }
            }

            if (cameraId == null && cm.getCameraIdList().length > 0) {
                cameraId = cm.getCameraIdList()[0];
            }

            if (cameraId == null) {
                return DeviceActionResult.success("Flashlight nahi mila.", "No Flashlight", android.R.drawable.stat_notify_error);
            }

            boolean turnOn = !lower.contains("off") && !lower.contains("band") &&
                             (lower.contains("on") || lower.contains("chalao") || lower.contains("start") || !isFlashlightOn);

            cm.setTorchMode(cameraId, turnOn);
            isFlashlightOn = turnOn;

            String msg = turnOn ? "Flashlight on kar diya gaya hai." : "Flashlight band kar diya gaya hai.";
            String pill = turnOn ? "Torch ON" : "Torch OFF";
            return DeviceActionResult.success(msg, pill, android.R.drawable.ic_lock_power_off);
        } catch (Exception e) {
            Log.e(TAG, "Error toggling flashlight: " + e.getMessage());
            return DeviceActionResult.success("Flashlight control karne mein dikkat aayi.", "Error", android.R.drawable.stat_notify_error);
        }
    }

    // --- WiFi Control ---
    private static DeviceActionResult handleWifi(Context context, String lower) {
        try {
            boolean turnOn = lower.contains("on") || lower.contains("chalao") || lower.contains("enable");
            boolean turnOff = lower.contains("off") || lower.contains("band") || lower.contains("disable");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ restricts direct wifi toggling without system app status, launch panel
                Intent panelIntent;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    panelIntent = new Intent(Settings.Panel.ACTION_WIFI);
                } else {
                    panelIntent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                }
                panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(panelIntent);
                return DeviceActionResult.success("Wi-Fi settings open kar di gayi hain.", "Wi-Fi Settings", android.R.drawable.ic_menu_preferences);
            } else {
                WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    wm.setWifiEnabled(turnOn || !turnOff);
                    String msg = turnOff ? "Wi-Fi band kar diya gaya hai." : "Wi-Fi chalu kar diya gaya hai.";
                    return DeviceActionResult.success(msg, turnOff ? "Wi-Fi OFF" : "Wi-Fi ON", android.R.drawable.ic_menu_compass);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling WiFi: " + e.getMessage());
        }
        return DeviceActionResult.success("Wi-Fi settings open kar raha hoon.", "Wi-Fi", android.R.drawable.ic_menu_preferences);
    }

    // --- Bluetooth Control ---
    private static DeviceActionResult handleBluetooth(Context context, String lower) {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) {
                return DeviceActionResult.success("Is device mein Bluetooth support nahi hai.", "No Bluetooth", android.R.drawable.stat_notify_error);
            }

            boolean turnOff = lower.contains("off") || lower.contains("band") || lower.contains("disable");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    return DeviceActionResult.success("Bluetooth settings open kar di gayi hain.", "Bluetooth Settings", android.R.drawable.ic_menu_preferences);
                }
            }

            if (turnOff) {
                adapter.disable();
                return DeviceActionResult.success("Bluetooth band kar diya gaya hai.", "Bluetooth OFF", android.R.drawable.ic_lock_power_off);
            } else {
                adapter.enable();
                return DeviceActionResult.success("Bluetooth chalu kar diya gaya hai.", "Bluetooth ON", android.R.drawable.ic_menu_compass);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling Bluetooth: " + e.getMessage());
            Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return DeviceActionResult.success("Bluetooth settings open kar di gayi hain.", "Bluetooth Settings", android.R.drawable.ic_menu_preferences);
        }
    }

    // --- Volume Control ---
    private static DeviceActionResult handleVolume(Context context, String lower) {
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) {
                return DeviceActionResult.success("Audio service uplabdh nahi hai.", "Audio Error", android.R.drawable.stat_notify_error);
            }

            if (lower.contains("mute") || lower.contains("silent") || lower.contains("shant")) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI);
                return DeviceActionResult.success("Volume mute kar diya gaya hai.", "Muted", android.R.drawable.ic_lock_silent_mode);
            }

            if (lower.contains("unmute")) {
                int defaultVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 2;
                am.setStreamVolume(AudioManager.STREAM_MUSIC, defaultVol, AudioManager.FLAG_SHOW_UI);
                return DeviceActionResult.success("Volume unmute kar diya gaya hai.", "Unmuted", android.R.drawable.ic_lock_silent_mode_off);
            }

            // Check for percentage
            int percent = extractPercent(lower);
            if (percent >= 0) {
                int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int target = (int) Math.round((percent / 100.0) * max);
                am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI);
                return DeviceActionResult.success("Volume " + percent + "% set kar diya gaya hai.", "Volume: " + percent + "%", android.R.drawable.ic_lock_silent_mode_off);
            }

            if (lower.contains("up") || lower.contains("badhao") || lower.contains("jyada") || lower.contains("increase") || lower.contains("raise")) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                return DeviceActionResult.success("Volume badha diya gaya hai.", "Volume Up", android.R.drawable.ic_lock_silent_mode_off);
            }

            if (lower.contains("down") || lower.contains("kam") || lower.contains("ghatao") || lower.contains("decrease") || lower.contains("lower")) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                return DeviceActionResult.success("Volume kam kar diya gaya hai.", "Volume Down", android.R.drawable.ic_lock_silent_mode_off);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling volume: " + e.getMessage());
        }
        return DeviceActionResult.notHandled();
    }

    // --- Screen Brightness Control ---
    private static DeviceActionResult handleBrightness(Activity activity, String lower) {
        try {
            int percent = extractPercent(lower);
            float targetFloat = -1f;

            if (percent >= 0) {
                targetFloat = Math.max(0.01f, Math.min(1.0f, percent / 100.0f));
            } else if (lower.contains("max") || lower.contains("full") || lower.contains("poori") || lower.contains("zyada")) {
                targetFloat = 1.0f;
            } else if (lower.contains("min") || lower.contains("zero") || lower.contains("kam") || lower.contains("low")) {
                targetFloat = 0.05f;
            } else if (lower.contains("medium") || lower.contains("half") || lower.contains("aadha")) {
                targetFloat = 0.5f;
            }

            if (targetFloat >= 0f) {
                final float b = targetFloat;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
                        lp.screenBrightness = b;
                        activity.getWindow().setAttributes(lp);
                    }
                });
                int displayPercent = Math.round(targetFloat * 100);
                return DeviceActionResult.success("Screen brightness " + displayPercent + "% set kar di gayi hai.", "Brightness: " + displayPercent + "%", android.R.drawable.ic_menu_view);
            }

            // Fallback: Open Display Settings
            Intent intent = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            return DeviceActionResult.success("Display settings open kar di gayi hain.", "Brightness Settings", android.R.drawable.ic_menu_preferences);
        } catch (Exception e) {
            Log.e(TAG, "Error adjusting brightness: " + e.getMessage());
            return DeviceActionResult.notHandled();
        }
    }

    private static int extractPercent(String text) {
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("(\\d{1,3})\\s*(?:%|percent|pratishat)");
            java.util.regex.Matcher m = p.matcher(text);
            if (m.find()) {
                int val = Integer.parseInt(m.group(1));
                return Math.min(100, Math.max(0, val));
            }
        } catch (Exception ignored) {}
        return -1;
    }
}

