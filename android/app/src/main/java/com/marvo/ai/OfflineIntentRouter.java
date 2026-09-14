package com.marvo.ai;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Step 7 - Part 2: Massive Offline OS Brain & Entity Alias Dictionary.
 * Intercepts user voice queries locally with high-performance regex NLP.
 * Executes native Android system actions without requiring internet connection.
 */
public class OfflineIntentRouter {
    private static final String TAG = "OfflineIntentRouter";

    private final AssistantActivity activity;

    // Entity Alias Dictionary (Smart Contacts & Relationships)
    private static final Map<String, String> ALIAS_MAP = new HashMap<>();
    private static final Map<String, String> DEFAULT_PHONE_NUMBERS = new HashMap<>();

    static {
        // Father Aliases
        ALIAS_MAP.put("papa", "Papa");
        ALIAS_MAP.put("bapa", "Papa");
        ALIAS_MAP.put("father", "Papa");
        ALIAS_MAP.put("dad", "Papa");
        ALIAS_MAP.put("daddy", "Papa");
        ALIAS_MAP.put("pitaji", "Papa");
        ALIAS_MAP.put("bapuji", "Papa");
        ALIAS_MAP.put("baba", "Papa");

        // Mother Aliases
        ALIAS_MAP.put("maa", "Maa");
        ALIAS_MAP.put("mother", "Maa");
        ALIAS_MAP.put("mom", "Maa");
        ALIAS_MAP.put("mummy", "Maa");
        ALIAS_MAP.put("mataji", "Maa");
        ALIAS_MAP.put("mommy", "Maa");
        ALIAS_MAP.put("aai", "Maa");

        // Brother Aliases
        ALIAS_MAP.put("jatin", "Jatin");
        ALIAS_MAP.put("brother", "Jatin");
        ALIAS_MAP.put("bhai", "Jatin");
        ALIAS_MAP.put("bhaina", "Jatin");
        ALIAS_MAP.put("bro", "Jatin");
        ALIAS_MAP.put("bhaiya", "Jatin");

        // Sister Aliases
        ALIAS_MAP.put("sister", "Sister");
        ALIAS_MAP.put("didi", "Sister");
        ALIAS_MAP.put("behen", "Sister");
        ALIAS_MAP.put("sis", "Sister");

        // Home & Office
        ALIAS_MAP.put("home", "Home");
        ALIAS_MAP.put("ghar", "Home");
        ALIAS_MAP.put("work", "Office");
        ALIAS_MAP.put("office", "Office");
        ALIAS_MAP.put("boss", "Boss");

        // Fallback default contacts from User Profile
        DEFAULT_PHONE_NUMBERS.put("Papa", "+919437000001");
        DEFAULT_PHONE_NUMBERS.put("Maa", "+919437000002");
        DEFAULT_PHONE_NUMBERS.put("Jatin", "+919437000003");
    }

    public OfflineIntentRouter(AssistantActivity activity) {
        this.activity = activity;
    }

    /**
     * Primary Offline Routing Engine.
     * Evaluates commands locally. If handled, executes action, provides feedback, and returns true.
     * Returns false if the query requires online knowledge or Gemini AI.
     */
    public boolean routeOffline(String command) {
        if (command == null || command.trim().isEmpty()) return false;
        String lower = command.trim().toLowerCase();

        // 1. Complex Online Bypasses (Must go online to Gemini AI)
        if (isComplexOnlineQuery(lower)) {
            return false;
        }

        // 2. Native Hardware Tools
        if (handleCamera(lower)) return true;
        if (handleFlashlight(lower)) return true;
        if (handleAlarmAndTimer(command, lower)) return true;
        if (handleSettings(lower)) return true;

        // 3. Smart Calling with Entity Alias Resolution
        if (handleCalling(command, lower)) return true;

        // 4. OS Integrations (Navigation, Music, Apps, Battery)
        if (handleNavigation(command, lower)) return true;
        if (handleMusic(command, lower)) return true;
        if (handleBattery(lower)) return true;
        if (handleApps(command, lower)) return true;

        return false;
    }

    /**
     * Identifies queries that must be handled online by Gemini AI.
     */
    private boolean isComplexOnlineQuery(String lower) {
        // URLs or Web Content
        if (lower.contains("http://") || lower.contains("https://") ||
            lower.contains(".com") || lower.contains(".org") || lower.contains(".net") ||
            lower.contains(".ai") || lower.contains(".io") ||
            lower.startsWith("summarize url") || lower.startsWith("digest url") ||
            lower.startsWith("read url") || lower.startsWith("summarize link")) {
            return true;
        }

        // Real-time Web Search
        if (lower.startsWith("search web") || lower.startsWith("web search") ||
            lower.startsWith("search live") || lower.startsWith("live search") ||
            lower.startsWith("real-time search") || lower.startsWith("realtime search") ||
            lower.startsWith("google search") || lower.startsWith("search online") ||
            lower.startsWith("online search")) {
            return true;
        }

        // General Knowledge, Explanations, Factual Inquiries
        if (lower.contains("wikipedia") || lower.startsWith("who is ") || lower.startsWith("what is ") ||
            lower.startsWith("why is ") || lower.startsWith("why do ") || lower.startsWith("why does ") ||
            lower.startsWith("how to ") || lower.startsWith("how do ") || lower.startsWith("how does ") ||
            lower.startsWith("how can ") || lower.startsWith("tell me about ") || lower.startsWith("explain ") ||
            lower.startsWith("define ") || lower.startsWith("meaning of ") || lower.startsWith("what are ") ||
            lower.startsWith("who was ") || lower.startsWith("where is ") || lower.startsWith("difference between ") ||
            lower.startsWith("kya hai ") || lower.startsWith("kaun hai ") || lower.startsWith("kyun ") ||
            lower.startsWith("kaise ") || lower.startsWith("kahan hai ")) {
            return true;
        }

        return false;
    }

    /**
     * CAMERA TOOL: "Open camera" / "Take a photo" / "Take selfie"
     */
    private boolean handleCamera(String lower) {
        if (lower.equals("open camera") || lower.equals("camera") || lower.equals("camera open") ||
            lower.equals("take a photo") || lower.equals("take photo") || lower.equals("click a photo") ||
            lower.equals("click photo") || lower.equals("capture photo") || lower.equals("take selfie") ||
            lower.contains("camera kholo") || lower.contains("photo khincho") || lower.contains("selfie lo")) {

            try {
                Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
                activity.showDynamicPill("Camera Opened", android.R.drawable.ic_menu_camera);
                activity.showResponse("Opening camera.", true);
                activity.setOrbState("IDLE");
                return true;
            } catch (ActivityNotFoundException e) {
                try {
                    Intent fallback = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(fallback);
                    activity.showDynamicPill("Camera Opened", android.R.drawable.ic_menu_camera);
                    activity.showResponse("Opening camera.", true);
                    activity.setOrbState("IDLE");
                    return true;
                } catch (Exception ex) {
                    activity.showResponse("Unable to open camera app.", true);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * FLASHLIGHT TOOL: "Turn on flashlight" / "Torch on" / "Flashlight off"
     */
    private boolean handleFlashlight(String lower) {
        boolean isOnCommand = lower.contains("flashlight on") || lower.contains("torch on") ||
                              lower.contains("turn on flashlight") || lower.contains("turn on torch") ||
                              lower.contains("torch jalao") || lower.contains("light on") ||
                              lower.contains("flash on");

        boolean isOffCommand = lower.contains("flashlight off") || lower.contains("torch off") ||
                               lower.contains("turn off flashlight") || lower.contains("turn off torch") ||
                               lower.contains("torch band karo") || lower.contains("light off") ||
                               lower.contains("flash off");

        if (isOnCommand) {
            activity.toggleFlashlight(true);
            activity.showDynamicPill("Flashlight On", android.R.drawable.ic_lock_idle_charging);
            activity.showResponse("Flashlight turned on.", true);
            activity.setOrbState("IDLE");
            return true;
        } else if (isOffCommand) {
            activity.toggleFlashlight(false);
            activity.showDynamicPill("Flashlight Off", android.R.drawable.ic_lock_idle_low_battery);
            activity.showResponse("Flashlight turned off.", true);
            activity.setOrbState("IDLE");
            return true;
        }
        return false;
    }

    /**
     * ALARMS & TIMERS: "Wake me up at 7 AM" / "Set alarm for 6:30" / "Set timer for 5 minutes"
     */
    private boolean handleAlarmAndTimer(String command, String lower) {
        // 1. Timer Detection
        if (lower.contains("timer") || lower.contains("countdown")) {
            Pattern timerPattern = Pattern.compile("(\\d+)\\s*(second|sec|minute|min|hour|hr)s?", Pattern.CASE_INSENSITIVE);
            Matcher matcher = timerPattern.matcher(command);
            if (matcher.find()) {
                int value = Integer.parseInt(matcher.group(1));
                String unit = matcher.group(2).toLowerCase();
                int totalSeconds = value;
                if (unit.startsWith("min")) {
                    totalSeconds = value * 60;
                } else if (unit.startsWith("hour") || unit.startsWith("hr")) {
                    totalSeconds = value * 3600;
                }
                activity.setTimer(totalSeconds);
                return true;
            }
        }

        // 2. Alarm Detection
        if (lower.contains("alarm") || lower.contains("wake me up") || lower.contains("jaga dena")) {
            Pattern timePattern = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", Pattern.CASE_INSENSITIVE);
            Matcher matcher = timePattern.matcher(command);
            if (matcher.find()) {
                int hour = Integer.parseInt(matcher.group(1));
                int minute = (matcher.group(2) != null) ? Integer.parseInt(matcher.group(2)) : 0;
                String ampm = matcher.group(3);

                if (ampm != null) {
                    if (ampm.equalsIgnoreCase("pm") && hour < 12) {
                        hour += 12;
                    } else if (ampm.equalsIgnoreCase("am") && hour == 12) {
                        hour = 0;
                    }
                }
                activity.setAlarm(hour, minute, "Marvo Alarm");
                return true;
            }
        }

        return false;
    }

    /**
     * SETTINGS TOOL: "Open WiFi", "Bluetooth on", "Open settings", "Display settings"
     */
    private boolean handleSettings(String lower) {
        Intent intent = null;
        String actionTitle = null;

        if (lower.contains("wifi") || lower.contains("wi-fi")) {
            intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
            actionTitle = "Wi-Fi Settings";
        } else if (lower.contains("bluetooth")) {
            intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            actionTitle = "Bluetooth Settings";
        } else if (lower.contains("airplane mode") || lower.contains("flight mode")) {
            intent = new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
            actionTitle = "Airplane Mode";
        } else if (lower.contains("display") || lower.contains("brightness")) {
            intent = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
            actionTitle = "Display Settings";
        } else if (lower.contains("sound") || lower.contains("volume") || lower.contains("audio settings")) {
            intent = new Intent(Settings.ACTION_SOUND_SETTINGS);
            actionTitle = "Sound Settings";
        } else if (lower.equals("open settings") || lower.equals("phone settings") || lower.equals("device settings") || lower.contains("settings kholo")) {
            intent = new Intent(Settings.ACTION_SETTINGS);
            actionTitle = "Settings";
        }

        if (intent != null) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
                activity.showDynamicPill(actionTitle, android.R.drawable.ic_menu_preferences);
                activity.showResponse("Opening " + actionTitle + ".", true);
                activity.setOrbState("IDLE");
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Error opening setting: " + e.getMessage());
            }
        }
        return false;
    }

    /**
     * ENTITY ALIAS DICTIONARY & SMART CALLING TOOL
     * Resolves aliases ("Papa", "Maa", "Jatin") and initiates offline calls instantly.
     */
    private boolean handleCalling(String command, String lower) {
        if (lower.startsWith("call ") || lower.equals("call") ||
            lower.startsWith("phone ") || lower.startsWith("dial ") ||
            lower.contains("ko call") || lower.contains("call lagao") ||
            lower.contains("ko phone") || lower.contains("call karo")) {

            String rawTarget = "";
            if (lower.startsWith("call ")) {
                rawTarget = command.substring(5).trim();
            } else if (lower.startsWith("phone ")) {
                rawTarget = command.substring(6).trim();
            } else if (lower.startsWith("dial ")) {
                rawTarget = command.substring(5).trim();
            } else if (lower.contains("ko call")) {
                int idx = lower.indexOf("ko call");
                rawTarget = command.substring(0, idx).trim();
            } else if (lower.contains("call lagao")) {
                int idx = lower.indexOf("call lagao");
                rawTarget = (idx > 0) ? command.substring(0, idx).trim() : command.substring(idx + 10).trim();
            } else if (lower.contains("ko phone")) {
                int idx = lower.indexOf("ko phone");
                rawTarget = command.substring(0, idx).trim();
            } else if (lower.contains("call karo")) {
                int idx = lower.indexOf("call karo");
                rawTarget = command.substring(0, idx).trim();
            }

            if (rawTarget.isEmpty()) {
                activity.showResponse("Who would you like to call?", true);
                return true;
            }

            // Clean up target name
            String cleanTarget = rawTarget.replaceAll("(?i)(please|karo|lagao|ko)\\s*", "").trim();
            ContactResolution resolution = resolveContact(cleanTarget);

            if (resolution != null && resolution.phoneNumber != null) {
                activity.showDynamicPill("Calling " + resolution.displayName, android.R.drawable.stat_sys_phone_call);
                activity.showResponse("Calling " + resolution.displayName + "...", true);
                activity.makeCall(resolution.phoneNumber);
                activity.setOrbState("IDLE");
                return true;
            } else {
                activity.showResponse("Could not find contact for " + cleanTarget + ".", true);
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves a spoken name using the Entity Alias Dictionary and Android Contacts.
     */
    public ContactResolution resolveContact(String query) {
        if (query == null || query.trim().isEmpty()) return null;
        String lowerQuery = query.trim().toLowerCase();

        // 1. Resolve alias via Entity Dictionary
        String canonicalName = ALIAS_MAP.get(lowerQuery);
        if (canonicalName == null) {
            // Check contains
            for (Map.Entry<String, String> entry : ALIAS_MAP.entrySet()) {
                if (lowerQuery.contains(entry.getKey())) {
                    canonicalName = entry.getValue();
                    break;
                }
            }
        }

        String searchName = (canonicalName != null) ? canonicalName : query.trim();

        // 2. Query device contacts database
        try {
            Uri contactUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
            String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?";
            String[] selectionArgs = new String[]{"%" + searchName + "%"};

            Cursor cursor = activity.getContentResolver().query(contactUri,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER},
                selection, selectionArgs, null);

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                    int numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                    String foundName = cursor.getString(nameIdx);
                    String foundNumber = cursor.getString(numIdx);
                    cursor.close();
                    return new ContactResolution(foundName, foundNumber);
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "Contacts query permission or error: " + e.getMessage());
        }

        // 3. Fallback to default user profile numbers
        if (canonicalName != null && DEFAULT_PHONE_NUMBERS.containsKey(canonicalName)) {
            return new ContactResolution(canonicalName, DEFAULT_PHONE_NUMBERS.get(canonicalName));
        }

        // Check raw query in defaults
        for (Map.Entry<String, String> entry : DEFAULT_PHONE_NUMBERS.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(searchName)) {
                return new ContactResolution(entry.getKey(), entry.getValue());
            }
        }

        return null;
    }

    /**
     * NAVIGATION TOOL: "Navigate to [place]" / "Directions to [place]"
     */
    private boolean handleNavigation(String command, String lower) {
        if (lower.startsWith("navigate to ") || lower.startsWith("take me to ") ||
            lower.startsWith("directions to ") || lower.contains("rasta dikhao")) {
            String destination = command.replaceAll("(?i)(navigate to|take me to|directions to|ka rasta dikhao|rasta dikhao)\\s*", "").trim();
            if (!destination.isEmpty()) {
                activity.startNavigation(destination);
                return true;
            }
        }
        return false;
    }

    /**
     * MEDIA / MUSIC TOOL: "Play [song]" / "Gana bajao [song]"
     */
    private boolean handleMusic(String command, String lower) {
        if (lower.startsWith("play ") || lower.startsWith("gana bajao ") || lower.startsWith("bajao ")) {
            String query = command.replaceAll("(?i)(play|gana bajao|bajao)\\s*", "").trim();
            if (!query.isEmpty()) {
                activity.playMedia(query, "auto");
                return true;
            }
        }
        return false;
    }

    /**
     * BATTERY TOOL: "Battery level" / "Charge" / "Kitna charge"
     */
    private boolean handleBattery(String lower) {
        if (lower.contains("battery") || lower.contains("charge") || lower.contains("phone status") || lower.contains("kitna charge")) {
            activity.getDeviceBatteryLevel();
            return true;
        }
        return false;
    }

    /**
     * APP LAUNCHER TOOL: "Open WhatsApp" / "Launch YouTube" / "Kholo Calculator"
     */
    private boolean handleApps(String command, String lower) {
        if (lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("start app ") || lower.startsWith("kholo ")) {
            String appName = command.replaceAll("(?i)(open app|launch app|start app|open|launch|kholo)\\s*", "").trim();
            if (!appName.isEmpty()) {
                activity.launchAppByName(appName);
                return true;
            }
        }
        return false;
    }

    public static class ContactResolution {
        public final String displayName;
        public final String phoneNumber;

        public ContactResolution(String displayName, String phoneNumber) {
            this.displayName = displayName;
            this.phoneNumber = phoneNumber;
        }
    }
}
