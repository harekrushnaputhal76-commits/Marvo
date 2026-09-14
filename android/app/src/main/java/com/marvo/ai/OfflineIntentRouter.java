package com.marvo.ai;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    private static final Map<String, String> CHIT_CHAT_MAP = new HashMap<>();

    // Step 9 - Part 3: Advanced Semantic Intent Parser & Context Stack (Tracks last 3-5 user interactions)
    private static final int MAX_CONTEXT_STACK = 5;
    public static final List<String> recentIntents = Collections.synchronizedList(new ArrayList<String>());

    public static synchronized void pushContextIntent(String query) {
        if (query == null || query.trim().isEmpty()) return;
        recentIntents.add(query.trim());
        while (recentIntents.size() > MAX_CONTEXT_STACK) {
            recentIntents.remove(0);
        }
    }

    public static synchronized void clearContextStack() {
        recentIntents.clear();
    }

    /**
     * Step 9 - Part 3: Advanced Semantic Intent Parser & Context Resolution.
     * When a vague query arrives ("Uske baare mein aur batao", "Isme kya khas hai?", "Explain more"),
     * analyzes the Context Stack to dynamically resolve pronouns ("uske", "isme", "iska", "unke", "it")
     * before routing.
     */
    public String resolveContextualPronouns(String command) {
        if (command == null || command.trim().isEmpty()) return command;
        String lower = command.trim().toLowerCase();

        boolean isVague = lower.contains("uske") || lower.contains("isme") || lower.contains("iska") ||
                          lower.contains("uski") || lower.contains("unke") || lower.contains("isse") ||
                          lower.contains("iske") || lower.equals("aur batao") || lower.startsWith("aur batao") ||
                          lower.contains("aur samjhao") || lower.contains("more about it") ||
                          lower.contains("what about it") || lower.contains("tell me more") ||
                          lower.contains("explain more") || lower.contains("kya khas hai") ||
                          lower.contains("isme kya") || lower.contains("why is that") ||
                          lower.contains("how does it work") || lower.contains("what does it mean");

        if (isVague && !recentIntents.isEmpty()) {
            String lastTopic = null;
            synchronized (recentIntents) {
                for (int i = recentIntents.size() - 1; i >= 0; i--) {
                    String prev = recentIntents.get(i).trim();
                    String prevLower = prev.toLowerCase();
                    if (!prevLower.contains("uske") && !prevLower.contains("isme") &&
                        !prevLower.equals("aur batao") && prev.length() > 3 &&
                        !prevLower.equals("hello") && !prevLower.equals("hi")) {
                        lastTopic = prev;
                        break;
                    }
                }
            }

            if (lastTopic != null && !lastTopic.isEmpty()) {
                Log.i(TAG, "[HYBRID ROUTER] Semantic Pronoun Resolution: bound \"" + command + "\" with context topic: \"" + lastTopic + "\"");
                return command + " (Referring to: " + lastTopic + ")";
            }
        }
        return command;
    }

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

        // Step 7 - Part 4: Offline Chit-Chat & Identity Dictionary in Hindi
        CHIT_CHAT_MAP.put("hello", "Namaste! Main Marvo hoon. Main aapki kya madad kar sakta hoon?");
        CHIT_CHAT_MAP.put("hi", "Namaste! Main Marvo hoon. Main aapki kya madad kar sakta hoon?");
        CHIT_CHAT_MAP.put("namaste", "Namaste! Main Marvo hoon. Main aapki kya madad kar sakta hoon?");
        CHIT_CHAT_MAP.put("namaskar", "Namaskar! Main aapki kya madad kar sakta hoon?");
        CHIT_CHAT_MAP.put("hey", "Hello! Main Marvo hoon. Bataiye main kya kar sakta hoon?");
        CHIT_CHAT_MAP.put("pranam", "Pranam! Main Marvo hoon. Kahiye kya madad karoon?");

        CHIT_CHAT_MAP.put("who are you", "Main Marvo hoon, aapka personal offline aur online AI assistant.");
        CHIT_CHAT_MAP.put("tum kaun ho", "Main Marvo hoon, aapka personal offline aur online AI assistant.");
        CHIT_CHAT_MAP.put("aap kaun hain", "Main Marvo hoon, aapka personal offline aur online AI assistant.");
        CHIT_CHAT_MAP.put("who made you", "Mujhe DeepMind aur Marvo team ne design kiya hai.");
        CHIT_CHAT_MAP.put("tumhe kisne banaya", "Mujhe DeepMind aur Marvo team ne banaya hai.");
        CHIT_CHAT_MAP.put("what is your name", "Mera naam Marvo hai.");
        CHIT_CHAT_MAP.put("tumhara naam kya hai", "Mera naam Marvo hai.");
        CHIT_CHAT_MAP.put("apna naam batao", "Mera naam Marvo hai.");

        CHIT_CHAT_MAP.put("how are you", "Main bilkul theek hoon. Aap bataiye?");
        CHIT_CHAT_MAP.put("kaise ho", "Main bilkul theek hoon. Aap bataiye?");
        CHIT_CHAT_MAP.put("aap kaise hain", "Main bilkul theek hoon. Aap bataiye?");
        CHIT_CHAT_MAP.put("kya haal hai", "Sab badhiya hai! Aap bataiye?");

        CHIT_CHAT_MAP.put("thank you", "Aapka swagat hai! Mujhe aapki madad karke khushi hui.");
        CHIT_CHAT_MAP.put("thanks", "Aapka swagat hai! Mujhe aapki madad karke khushi hui.");
        CHIT_CHAT_MAP.put("dhanyavad", "Aapka swagat hai! Mujhe aapki madad karke khushi hui.");
        CHIT_CHAT_MAP.put("shukriya", "Aapka swagat hai! Mujhe aapki madad karke khushi hui.");

        CHIT_CHAT_MAP.put("bye", "Alvida! Apna khayal rakhiyega.");
        CHIT_CHAT_MAP.put("goodbye", "Alvida! Apna khayal rakhiyega.");
        CHIT_CHAT_MAP.put("alvida", "Alvida! Phir milenge.");
    }

    public OfflineIntentRouter(AssistantActivity activity) {
        this.activity = activity;
    }

    /**
     * Primary Hybrid Routing Engine (Step 9: Apple Intelligence Hybrid Router Optimization).
     * Strictly executes offline-first strategy:
     * CATEGORY A (Handle Locally): Time, Date, Math calculations, Battery percentage,
     * device settings (WiFi/Bluetooth), direct contact lookups (e.g., "Call Papa"), volume,
     * hardware tools. Executes locally via native managers & triggers TTS immediately.
     * CATEGORY B (Route to Online Gemini API): General knowledge, web facts, coding help,
     * complex analysis.
     */
    public boolean routeOffline(String rawCommand) {
        if (rawCommand == null || rawCommand.trim().isEmpty()) return false;
        // Step 9 - Part 3: Semantic Context Resolution
        String command = resolveContextualPronouns(rawCommand);
        pushContextIntent(rawCommand);
        String lower = command.trim().toLowerCase();

        // CATEGORY A (Handle Locally - Fixed Tools):
        // 1. Time & Date
        if (handleDateTime(lower)) {
            Log.i(TAG, "[HYBRID ROUTER] Solved OFFLINE (Fixed Tools - Time/Date): " + command);
            return true;
        }

        // 2. Math Calculations
        if (handleMath(command, lower)) {
            Log.i(TAG, "[HYBRID ROUTER] Solved OFFLINE (Fixed Tools - Math): " + command);
            return true;
        }

        // 3. Battery Percentage
        if (handleBattery(lower)) {
            Log.i(TAG, "[HYBRID ROUTER] Solved OFFLINE (Fixed Tools - Battery): " + command);
            return true;
        }

        // 4. Device Settings (WiFi, Bluetooth, etc.)
        if (handleSettings(lower)) {
            Log.i(TAG, "[HYBRID ROUTER] Solved OFFLINE (Fixed Tools - Device Settings): " + command);
            return true;
        }

        // 5. Direct Contact Lookups & Calling (e.g., "Call Papa")
        if (handleCalling(command, lower)) {
            Log.i(TAG, "[HYBRID ROUTER] Solved OFFLINE (Fixed Tools - Contacts/Calling): " + command);
            return true;
        }

        // 6. Offline Hindi Chit-Chat & Identity
        if (handleChitChat(command, lower)) {
            Log.i(TAG, "[HYBRID ROUTER] Solved OFFLINE (Fixed Tools - ChitChat): " + command);
            return true;
        }

        // 7. Native Hardware & System Tools (Volume, Camera, Flashlight, Alarms, Navigation, Media, Apps)
        if (handleVolume(lower)) {
            Log.i(TAG, "[HYBRID ROUTER] Solved OFFLINE (Fixed Tools - Volume): " + command);
            return true;
        }
        if (handleCamera(lower)) {
            Log.i(TAG, "[HYBRID ROUTER] Solved OFFLINE (Fixed Tools - Camera): " + command);
            return true;
        }
        if (handleFlashlight(lower)) {
            Log.i(TAG, "[HYBRID ROUTER] Solved OFFLINE (Fixed Tools - Flashlight): " + command);
            return true;
        }
        if (handleAlarmAndTimer(command, lower)) {
            Log.i(TAG, "[HYBRID ROUTER] Solved OFFLINE (Fixed Tools - Alarm/Timer): " + command);
            return true;
        }
        if (handleNavigation(command, lower)) {
            Log.i(TAG, "[HYBRID ROUTER] Solved OFFLINE (Fixed Tools - Navigation): " + command);
            return true;
        }
        if (handleMusic(command, lower)) {
            Log.i(TAG, "[HYBRID ROUTER] Solved OFFLINE (Fixed Tools - Music): " + command);
            return true;
        }
        if (handleApps(command, lower)) {
            Log.i(TAG, "[HYBRID ROUTER] Solved OFFLINE (Fixed Tools - Apps): " + command);
            return true;
        }

        // CATEGORY B (Route to Online Gemini API):
        // Step 9 - Part 2 & Part 3: Multi-Step Reasoning & Domain Classification
        String domain = detectDomain(command);
        Log.i(TAG, "[HYBRID ROUTER] Solved ONLINE (Gemini API - " + domain + "): " + command);
        activity.askGeminiOnline(command, domain);
        return true;
    }

    /**
     * Step 9 - Part 3: Deep Domain Intelligence Routing.
     * Checks if a query requires deep academic, scientific, or analytical research.
     */
    public static boolean isSpecializedDeepQuery(String lower) {
        if (lower == null) return false;
        return lower.contains("research") || lower.contains("study") || lower.contains("paper") ||
               lower.contains("history of") || lower.contains("science of") || lower.contains("theory of") ||
               lower.contains("concept of") || lower.contains("deep dive") || lower.contains("analysis") ||
               lower.contains("explain in detail") || lower.contains("detailed explanation") ||
               lower.contains("scientific") || lower.contains("academic") || lower.contains("quantum") ||
               lower.contains("physics") || lower.contains("chemistry") || lower.contains("biology") ||
               lower.contains("formula") || lower.contains("derivation") || lower.contains("algorithm") ||
               lower.contains("technical") || lower.contains("mechanism of") || lower.contains("how does") ||
               lower.contains("why does") || lower.contains("difference between") || lower.contains("kaise kaam karta") ||
               lower.contains("kyun hota hai") || lower.contains("ke baare mein deep") || lower.contains("deep research");
    }

    /**
     * Step 9 - Part 2 & Part 3: Web Knowledge Domain Classifier.
     * Categorizes queries into specialized domains: Deep Reasoning, Wikipedia/General Knowledge,
     * News, Study & Research, Comedy & Jokes, Stories, or General.
     */
    public static String detectDomain(String query) {
        if (query == null) return "GENERAL";
        String lower = query.trim().toLowerCase();

        // 0. DEEP REASONING: Deep research topics, scientific theories, multi-part analytical questions
        if (isSpecializedDeepQuery(lower)) {
            return "DEEP_REASONING";
        }

        // 1. COMEDY & JOKES: "Tell me a joke", "Kuch hasao", "chutkula", funny prompts
        if (lower.contains("joke") || lower.contains("chutkula") || lower.contains("hasao") ||
            lower.contains("hasi") || lower.contains("funny") || lower.contains("make me laugh") ||
            lower.contains("kuch funny") || lower.contains("koi chutkula") || lower.contains("haso") ||
            lower.contains("joke sunao")) {
            return "COMEDY_AND_JOKES";
        }

        // 2. STORIES: "Tell me a story", "Ek kahani sunao", tales, bedtime stories
        if (lower.contains("story") || lower.contains("kahani") || lower.contains("katha") ||
            lower.contains("dastan") || lower.contains("ek kahani") || lower.contains("tell me a story") ||
            lower.contains("bedtime story") || lower.contains("koi kahani") || lower.contains("kahani sunao")) {
            return "STORIES";
        }

        // 3. NEWS: Current events, headlines, "What's happening in [Topic]"
        if (lower.contains("news") || lower.contains("samachar") || lower.contains("headline") ||
            lower.contains("what's happening") || lower.contains("whats happening") ||
            lower.contains("current events") || lower.contains("breaking news") || lower.contains("latest news") ||
            lower.contains("khabar") || lower.contains("aaj ki khabar") || lower.contains("kya chal raha hai")) {
            return "NEWS";
        }

        // 4. STUDY & RESEARCH: Academic inquiries, science papers, free learning topics
        if (lower.contains("research") || lower.contains("study") || lower.contains("paper") ||
            lower.contains("academic") || lower.contains("thesis") || lower.contains("science paper") ||
            lower.contains("teach me") || lower.contains("learn about") || lower.contains("educational") ||
            lower.contains("formula of") || lower.contains("derivation") || lower.contains("notes on") ||
            lower.contains("syllabus") || lower.contains("exam prep") || lower.contains("concept of") ||
            lower.contains("theory of") || lower.contains("physics") || lower.contains("chemistry") ||
            lower.contains("biology") || lower.contains("mathematics") || lower.contains("quantum") ||
            lower.contains("photosynthesis") || lower.contains("algorithm") || lower.contains("coding") ||
            lower.contains("program for") || lower.contains("program to") || lower.contains("solve")) {
            return "STUDY_AND_RESEARCH";
        }

        // 5. WIKIPEDIA / GENERAL KNOWLEDGE: History, science, definitions, "Who is X?"
        if (lower.contains("wikipedia") || lower.startsWith("who is ") || lower.startsWith("what is ") ||
            lower.startsWith("who was ") || lower.startsWith("where is ") || lower.startsWith("define ") ||
            lower.startsWith("meaning of ") || lower.startsWith("history of ") || lower.startsWith("science of ") ||
            lower.startsWith("tell me about ") || lower.startsWith("difference between ") ||
            lower.startsWith("kya hai ") || lower.startsWith("kaun hai ") || lower.startsWith("kyun ") ||
            lower.startsWith("kaise ") || lower.startsWith("kahan hai ") || lower.startsWith("kisko kehte hain") ||
            lower.startsWith("facts about") || lower.startsWith("information about")) {
            return "WIKIPEDIA_AND_KNOWLEDGE";
        }

        return "GENERAL";
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
                if (resolution.isFavorite) {
                    // Step 8: DIRECT CALLS (Favorites)
                    activity.showDynamicPill("Calling " + resolution.displayName, android.R.drawable.stat_sys_phone_call);
                    activity.showResponse("Calling " + resolution.displayName + "...", true);
                    activity.makeCall(resolution.phoneNumber);
                    activity.setOrbState("IDLE");
                } else {
                    // Step 8: VERBAL CONFIRMATION (Others)
                    // Sleek text overlay above the orb: "Contact: [Name] - [Number]"
                    activity.pendingCallName = resolution.displayName;
                    activity.pendingCallNumber = resolution.phoneNumber;
                    activity.showDynamicPill("Contact: " + resolution.displayName, android.R.drawable.stat_sys_phone_call);
                    activity.speakAndListen(
                        "Kya aap " + resolution.displayName + " ko call karna chahte hain?",
                        "Contact: " + resolution.displayName + " - " + resolution.phoneNumber,
                        "CALL_CONFIRMATION"
                    );
                }
                return true;
            } else {
                // Step 8: If contact not found
                activity.showDynamicPill("Not Found", android.R.drawable.ic_menu_close_clear_cancel);
                activity.showResponse("Mujhe yeh number aapke phone mein nahi mila.", true);
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if contact target matches predefined family aliases / favorites.
     */
    private boolean isFamilyFavorite(String canonicalName, String rawQuery) {
        String lowerQuery = (rawQuery != null ? rawQuery.toLowerCase() : "");
        String lowerCanon = (canonicalName != null ? canonicalName.toLowerCase() : "");
        String[] familyKeywords = new String[]{
            "papa", "father", "dad", "daddy", "pitaji", "bapa", "bapuji", "baba",
            "maa", "mother", "mom", "mummy", "mataji", "mommy", "aai",
            "jatin", "brother", "bhai", "bhaina", "bro", "bhaiya",
            "sister", "didi", "behen", "sis"
        };
        for (String kw : familyKeywords) {
            if (lowerCanon.equals(kw) || lowerCanon.contains(kw) || lowerQuery.contains(kw)) {
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

        boolean isFav = isFamilyFavorite(canonicalName, lowerQuery);
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
                    return new ContactResolution(foundName, foundNumber, isFav);
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "Contacts query permission or error: " + e.getMessage());
        }

        // 3. Fallback to default user profile numbers
        if (canonicalName != null && DEFAULT_PHONE_NUMBERS.containsKey(canonicalName)) {
            return new ContactResolution(canonicalName, DEFAULT_PHONE_NUMBERS.get(canonicalName), true);
        }

        // Check raw query in defaults
        for (Map.Entry<String, String> entry : DEFAULT_PHONE_NUMBERS.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(searchName)) {
                return new ContactResolution(entry.getKey(), entry.getValue(), true);
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
     * OFFLINE CHIT-CHAT & IDENTITY ENGINE IN HINDI (Step 7 - Part 4)
     */
    private boolean handleChitChat(String command, String lower) {
        // Step 9 - Part 3: Deep Domain Intelligence Routing.
        // Specialized queries (research, science, history, academic, technical)
        // must bypass simple chit-chat and receive full analytical processing from Gemini.
        if (isSpecializedDeepQuery(lower)) {
            return false;
        }

        String clean = lower.replaceAll("[^a-zA-Z0-9\\s]", "")
                            .replaceAll("\\b(marvo|assistant|please|batao|bataiye|ji|karo)\\b", "")
                            .trim();
        clean = clean.replaceAll("\\s+", " ").trim();

        // Limit length to avoid intercepting complex queries that begin with greetings
        if (clean.split("\\s+").length > 8) return false;

        for (Map.Entry<String, String> entry : CHIT_CHAT_MAP.entrySet()) {
            String key = entry.getKey();
            if (clean.equals(key) || clean.matches(".*\\b" + Pattern.quote(key) + "\\b.*")) {
                String reply = entry.getValue();
                String pillTitle = key.substring(0, 1).toUpperCase() + key.substring(1);
                activity.showDynamicPill(pillTitle, android.R.drawable.ic_dialog_info);
                activity.showResponse(reply, true);
                activity.setOrbState("IDLE");
                return true;
            }
        }
        return false;
    }

    /**
     * OFFLINE TIME & DATE ENGINE IN HINDI (Step 7 - Part 4)
     */
    private boolean handleDateTime(String lower) {
        boolean isTime = lower.contains("time") || lower.contains("samay") || lower.contains("kitne baje") ||
                         lower.contains("kya baje") || lower.contains("ghadi") || lower.contains("time batao") ||
                         lower.contains("samay batao");
        boolean isDate = lower.contains("date") || lower.contains("tarikh") || lower.contains("tareekh") ||
                         lower.contains("aaj ka din") || lower.contains("kon sa din") || lower.contains("today date");

        if (isTime) {
            Calendar cal = Calendar.getInstance();
            int hour = cal.get(Calendar.HOUR);
            if (hour == 0) hour = 12;
            int minute = cal.get(Calendar.MINUTE);
            String response = (minute == 0)
                ? "Abhi samay theek " + hour + " baje hain."
                : "Abhi samay " + hour + " baj kar " + minute + " minute ho raha hai.";

            activity.showDynamicPill("Time: " + String.format(Locale.getDefault(), "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), minute), android.R.drawable.ic_menu_recent_history);
            activity.showResponse(response, true);
            activity.setOrbState("IDLE");
            return true;
        }

        if (isDate) {
            Calendar cal = Calendar.getInstance();
            String[] daysHindi = {"Ravivar", "Somvar", "Mangalvar", "Budhvar", "Guruvar", "Shukravar", "Shanivar"};
            String[] monthsHindi = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
            String dayName = daysHindi[cal.get(Calendar.DAY_OF_WEEK) - 1];
            int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
            String monthName = monthsHindi[cal.get(Calendar.MONTH)];
            int year = cal.get(Calendar.YEAR);
            String response = "Aaj " + dayName + ", " + dayOfMonth + " " + monthName + " " + year + " hai.";

            activity.showDynamicPill(dayOfMonth + " " + monthName, android.R.drawable.ic_menu_my_calendar);
            activity.showResponse(response, true);
            activity.setOrbState("IDLE");
            return true;
        }

        return false;
    }

    /**
     * OFFLINE MATH CALCULATOR ENGINE IN HINDI (Step 7 - Part 4)
     */
    private boolean handleMath(String command, String lower) {
        Pattern p = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:plus|\\+|jod|minus|\\-|ghatao|ghata|times|multiplied by|\\*|x|into|guna|divided by|\\/|bhag)\\s*(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(lower);
        if (m.find()) {
            try {
                double num1 = Double.parseDouble(m.group(1));
                double num2 = Double.parseDouble(m.group(2));
                String fullMatch = m.group(0).toLowerCase();
                double result = 0;

                if (fullMatch.contains("+") || fullMatch.contains("plus") || fullMatch.contains("jod")) {
                    result = num1 + num2;
                } else if (fullMatch.contains("-") || fullMatch.contains("minus") || fullMatch.contains("ghata")) {
                    result = num1 - num2;
                } else if (fullMatch.contains("*") || fullMatch.contains("x") || fullMatch.contains("into") || fullMatch.contains("times") || fullMatch.contains("guna") || fullMatch.contains("multiplied")) {
                    result = num1 * num2;
                } else if (fullMatch.contains("/") || fullMatch.contains("divided") || fullMatch.contains("bhag")) {
                    if (num2 == 0) {
                        activity.showResponse("Zero se divide nahi kiya ja sakta.", true);
                        activity.setOrbState("IDLE");
                        return true;
                    }
                    result = num1 / num2;
                }

                String resultStr = (result == (long) result) ? String.format(Locale.getDefault(), "%d", (long) result) : String.format(Locale.getDefault(), "%.2f", result);
                String speech = "Iska jawab hai " + resultStr + ".";
                activity.showDynamicPill("Math: " + resultStr, android.R.drawable.ic_menu_compass);
                activity.showResponse(speech, true);
                activity.setOrbState("IDLE");
                return true;
            } catch (Exception e) {
                Log.w(TAG, "Math parsing error: " + e.getMessage());
            }
        }
        return false;
    }

    /**
     * DEEP HARDWARE VOLUME & AUDIO SETTINGS IN HINDI (Step 7 - Part 4)
     */
    private boolean handleVolume(String lower) {
        if (lower.contains("volume up") || lower.contains("volume badhao") || lower.contains("awaz badhao") ||
            lower.contains("increase volume") || lower.contains("sound badhao") || lower.contains("awaz tej") || lower.contains("volume tej")) {
            activity.adjustDeviceVolume(AudioManager.ADJUST_RAISE);
            return true;
        } else if (lower.contains("volume down") || lower.contains("volume kam") || lower.contains("awaz kam") ||
                   lower.contains("decrease volume") || lower.contains("lower volume") || lower.contains("sound kam") || lower.contains("volume dheere")) {
            activity.adjustDeviceVolume(AudioManager.ADJUST_LOWER);
            return true;
        } else if (lower.equals("mute") || lower.contains("mute phone") || lower.contains("phone silent") ||
                   lower.contains("awaz band") || lower.contains("silent karo") || lower.contains("mute karo")) {
            activity.setDeviceMute(true);
            return true;
        } else if (lower.equals("unmute") || lower.contains("unmute phone") || lower.contains("sound on") ||
                   lower.contains("unmute karo") || lower.contains("awaz kholo")) {
            activity.setDeviceMute(false);
            return true;
        }
        return false;
    }

    /**
     * BATTERY TOOL: "Battery level" / "Charge" / "Kitna charge" (Step 7 - Part 4 in Hindi)
     */
    private boolean handleBattery(String lower) {
        if (lower.contains("battery") || lower.contains("charge") || lower.contains("kitna charge") || lower.contains("battery kitni")) {
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
        public final boolean isFavorite;

        public ContactResolution(String displayName, String phoneNumber, boolean isFavorite) {
            this.displayName = displayName;
            this.phoneNumber = phoneNumber;
            this.isFavorite = isFavorite;
        }

        public ContactResolution(String displayName, String phoneNumber) {
            this(displayName, phoneNumber, false);
        }
    }
}

