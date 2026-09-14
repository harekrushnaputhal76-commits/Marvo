package com.marvo.ai;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Arrays;
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
    private static final Map<String, String> FAST_APP_MAP = new HashMap<>();

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

    // Step 9 - Part 5: Compound Request Sequential Processor State
    private volatile boolean isCompoundRunning = false;
    private final List<String> compoundSpeeches = Collections.synchronizedList(new ArrayList<String>());
    private final List<String> compoundPills = Collections.synchronizedList(new ArrayList<String>());

    public boolean isCompoundRunning() {
        return isCompoundRunning;
    }

    public void recordCompoundSpeech(String speech) {
        if (speech != null && !speech.trim().isEmpty()) {
            compoundSpeeches.add(speech.trim());
        }
    }

    public void recordCompoundPill(String pill) {
        if (pill != null && !pill.trim().isEmpty()) {
            compoundPills.add(pill.trim());
        }
    }

    /**
     * Step 9 - Part 5: Splits compound user commands on common conjunctions.
     * Conjunctions handled: " and ", " then ", " aur ", " and then ", " aur phir ", " ke baad ".
     */
    public List<String> splitCompoundCommand(String command) {
        if (command == null || command.trim().isEmpty()) return Collections.emptyList();
        Pattern pattern = Pattern.compile("(?i)\\s*(?:,|;)?\\s+(?:and then|aur phir|ke baad|and|then|aur)\\s+");
        String[] tokens = pattern.split(command.trim());
        List<String> list = new ArrayList<>();
        for (String t : tokens) {
            String trimmed = t.trim();
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }
        return list;
    }

    /**
     * Checks whether a sub-phrase has actionable semantics (command verbs, fixed tools, or query interrogatives).
     * Guards against false splits like "difference between java and python" or "lion and mouse".
     */
    public boolean isActionableSubIntent(String text) {
        if (text == null) return false;
        String lower = text.trim().toLowerCase();
        if (lower.isEmpty()) return false;

        String[] words = lower.split("\\s+");
        if (words.length < 2 && !lower.equals("camera") && !lower.equals("flashlight") && !lower.equals("time") && !lower.equals("date") && !lower.equals("battery")) {
            return false;
        }

        // 1. Offline fixed tool keywords
        if (lower.contains("time") || lower.contains("samay") || lower.contains("date") || lower.contains("tarikh") ||
            lower.contains("battery") || lower.contains("wifi") || lower.contains("bluetooth") ||
            lower.contains("call") || lower.contains("phone") || lower.contains("camera") ||
            lower.contains("flashlight") || lower.contains("torch") || lower.contains("brightness") ||
            lower.contains("volume") || lower.contains("sound") || lower.contains("awaz") ||
            lower.contains("alarm") || lower.contains("timer") || lower.contains("navigate") ||
            lower.contains("direction") || lower.contains("rasta") || lower.contains("play") ||
            lower.contains("open") || lower.contains("launch") || lower.contains("kholo") ||
            lower.contains("chalao") || lower.contains("batao") || lower.contains("on karo") ||
            lower.contains("off karo") || lower.contains("turn on") || lower.contains("turn off") ||
            lower.contains("set") || lower.contains("calculate") || lower.contains("plus") ||
            lower.contains("minus") || lower.contains("multiply") || lower.contains("divide")) {
            return true;
        }

        // 2. Online question / query keywords
        if (lower.startsWith("who ") || lower.startsWith("what ") || lower.startsWith("why ") ||
            lower.startsWith("how ") || lower.startsWith("where ") || lower.startsWith("when ") ||
            lower.startsWith("kaun ") || lower.startsWith("kya ") || lower.startsWith("kyun ") ||
            lower.startsWith("kaise ") || lower.startsWith("kahan ") || lower.startsWith("kab ") ||
            lower.startsWith("explain ") || lower.startsWith("tell me ") || lower.startsWith("search ") ||
            lower.startsWith("define ") || lower.contains("joke") || lower.contains("story") ||
            lower.contains("kahani") || lower.contains("news") || lower.contains("samachar")) {
            return true;
        }

        return false;
    }

    /**
     * Executes a single offline intent safely.
     * Returns true if handled locally, false if it should be delegated to online AI.
     */
    public boolean executeSingleOfflineIntent(String subQuery) {
        if (subQuery == null || subQuery.trim().isEmpty()) return false;
        String lower = subQuery.trim().toLowerCase();

        if (handleDateTime(lower)) return true;
        if (handleMath(subQuery, lower)) return true;
        if (handleBattery(lower)) return true;
        if (handleSettings(lower)) return true;
        if (handleCalling(subQuery, lower)) return true;
        if (handleChitChat(subQuery, lower)) return true;
        if (handleBrightness(lower)) return true;
        if (handleVolume(lower)) return true;
        if (handleCamera(lower)) return true;
        if (handleFlashlight(lower)) return true;
        if (handleAlarmAndTimer(subQuery, lower)) return true;
        if (handleNavigation(subQuery, lower)) return true;
        if (handleMusic(subQuery, lower)) return true;
        if (handleApps(subQuery, lower)) return true;

        // Step 9 - Part 8: Strict Routing Wall for Compound sub-queries (Zero Cloud Leakage)
        if (isStrictDeviceUtilityQuery(lower)) {
            handleDeviceUtilityFallback(subQuery, lower);
            return true;
        }

        return false;
    }

    /**
     * Formats multiple speech parts into one fluid, grammatical sentence in Hindi/Hinglish.
     */
    public static String joinSpeechParts(List<String> parts) {
        if (parts == null || parts.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i).trim().replaceAll("\\.+$", "");
            if (part.isEmpty()) continue;
            if (sb.length() > 0) {
                if (i == parts.size() - 1) {
                    sb.append(", aur ");
                } else {
                    sb.append(", ");
                }
            }
            sb.append(part);
        }
        sb.append(".");
        return sb.toString();
    }

    /**
     * Formats multiple dynamic pill messages into a concise status string.
     */
    public static String joinPillParts(List<String> parts) {
        if (parts == null || parts.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i).trim();
            if (part.isEmpty()) continue;
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(part);
        }
        return sb.toString();
    }

    /**
     * Step 9 - Part 5: Sequential Compound Request Processor.
     * Handles sequential multi-intent queries like:
     * "Turn on flashlight and tell me the time"
     * "Flashlight on karo aur battery batao"
     * "Turn on flashlight and who is the prime minister of India" (hybrid)
     */
    public boolean handleCompoundRequest(String rawCommand, String lower) {
        if (isCompoundRunning) return false;

        List<String> subQueries = splitCompoundCommand(rawCommand);
        if (subQueries == null || subQueries.size() < 2) return false;

        int actionableCount = 0;
        for (String sq : subQueries) {
            if (isActionableSubIntent(sq)) {
                actionableCount++;
            }
        }
        if (actionableCount < 2) {
            return false;
        }

        isCompoundRunning = true;
        compoundSpeeches.clear();
        compoundPills.clear();

        try {
            String pendingOnlineQuery = null;
            for (int i = 0; i < subQueries.size(); i++) {
                String subQuery = subQueries.get(i).trim();
                if (subQuery.isEmpty()) continue;

                try {
                    boolean handledOffline = executeSingleOfflineIntent(subQuery);
                    if (!handledOffline) {
                        if (pendingOnlineQuery == null) {
                            pendingOnlineQuery = subQuery;
                        } else {
                            pendingOnlineQuery += " and " + subQuery;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "[COMPOUND ROUTER] Error executing sub-intent: " + subQuery, e);
                    recordCompoundSpeech("Koshish ki lekin " + subQuery + " poora nahi ho paya");
                }
            }

            // Release compound flag so subsequent UI calls are not intercepted
            isCompoundRunning = false;

            if (pendingOnlineQuery != null) {
                String prefixSpeech = joinSpeechParts(compoundSpeeches);
                String prefixPill = joinPillParts(compoundPills);
                String domain = detectDomain(pendingOnlineQuery);
                Log.i(TAG, "[COMPOUND ROUTER] Hybrid execution: calling Gemini with prefix for: " + pendingOnlineQuery);
                activity.askGeminiOnlineWithPrefix(prefixSpeech, prefixPill, pendingOnlineQuery, domain);
                return true;
            }

            String combinedSpeech = joinSpeechParts(compoundSpeeches);
            String combinedPill = joinPillParts(compoundPills);
            if (combinedSpeech.isEmpty() || combinedSpeech.equals(".")) {
                combinedSpeech = "Aapke bataye sabhi tasks poore kar diye gaye hain.";
            }
            if (combinedPill.isEmpty()) {
                combinedPill = "Sequential Tasks Completed";
            }

            activity.showResponse(combinedSpeech);
            activity.showDynamicPill(combinedPill, android.R.drawable.ic_dialog_info);
            return true;
        } finally {
            isCompoundRunning = false;
        }
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

        // Step 9 - Part 4: Fast App Package Dictionary (<5ms Instant Launch)
        FAST_APP_MAP.put("whatsapp", "com.whatsapp");
        FAST_APP_MAP.put("youtube", "com.google.android.youtube");
        FAST_APP_MAP.put("instagram", "com.instagram.android");
        FAST_APP_MAP.put("insta", "com.instagram.android");
        FAST_APP_MAP.put("facebook", "com.facebook.katana");
        FAST_APP_MAP.put("chrome", "com.android.chrome");
        FAST_APP_MAP.put("browser", "com.android.chrome");
        FAST_APP_MAP.put("maps", "com.google.android.apps.maps");
        FAST_APP_MAP.put("google maps", "com.google.android.apps.maps");
        FAST_APP_MAP.put("gmail", "com.google.android.gm");
        FAST_APP_MAP.put("mail", "com.google.android.gm");
        FAST_APP_MAP.put("email", "com.google.android.gm");
        FAST_APP_MAP.put("spotify", "com.spotify.music");
        FAST_APP_MAP.put("music", "com.spotify.music");
        FAST_APP_MAP.put("gaana", "com.spotify.music");
        FAST_APP_MAP.put("twitter", "com.twitter.android");
        FAST_APP_MAP.put("x", "com.twitter.android");
        FAST_APP_MAP.put("telegram", "org.telegram.messenger");
        FAST_APP_MAP.put("play store", "com.android.vending");
        FAST_APP_MAP.put("playstore", "com.android.vending");
        FAST_APP_MAP.put("calculator", "com.google.android.calculator");
        FAST_APP_MAP.put("clock", "com.google.android.deskclock");
        FAST_APP_MAP.put("alarm", "com.google.android.deskclock");
        FAST_APP_MAP.put("calendar", "com.google.android.calendar");
        FAST_APP_MAP.put("photos", "com.google.android.apps.photos");
        FAST_APP_MAP.put("gallery", "com.google.android.apps.photos");
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

        // Step 9 - Part 5: Multi-Intent Sequential Compound Request Processor
        if (handleCompoundRequest(rawCommand, rawCommand.trim().toLowerCase())) {
            Log.i(TAG, "[HYBRID ROUTER] Solved as SEQUENTIAL COMPOUND: " + rawCommand);
            return true;
        }

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

        // 7. Native Hardware & System Tools (Brightness, Volume, Camera, Flashlight, Alarms, Navigation, Media, Apps)
        if (handleBrightness(lower)) {
            Log.i(TAG, "[HYBRID ROUTER] Solved OFFLINE (Fixed Tools - Brightness): " + command);
            return true;
        }
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

        // Step 9 - Part 8: Strict Routing Wall (Zero Cloud Leakage for Hardware/Device Controls)
        if (isStrictDeviceUtilityQuery(lower)) {
            Log.i(TAG, "[STRICT ROUTING WALL] Caught device utility intent, resolving locally: " + command);
            handleDeviceUtilityFallback(command, lower);
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
            lower.equals("start camera") || lower.equals("launch camera") || lower.contains("camera on") ||
            lower.contains("camera kholo") || lower.contains("photo khincho") || lower.contains("selfie lo") ||
            lower.contains("camera chalu") || lower.contains("picture lo") || lower.contains("click picture")) {

            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, AssistantActivity.PERMISSION_REQUEST_CAMERA);
            }

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
     * FLASHLIGHT TOOL: "Turn on flashlight" / "Torch on" / "Flashlight off" / "Torch jalao" / "Flash light chalu karo"
     */
    private boolean handleFlashlight(String lower) {
        boolean isOnCommand = lower.contains("flashlight on") || lower.contains("torch on") ||
                              lower.contains("turn on flashlight") || lower.contains("turn on torch") ||
                              lower.contains("torch jalao") || lower.contains("light on") ||
                              lower.contains("flash on") || lower.contains("torch chalu") ||
                              lower.contains("flash chalu") || lower.contains("light jalao") ||
                              lower.contains("light chalu") || lower.contains("flash light chalu") ||
                              lower.contains("flashlight chalu") || lower.contains("flash light on") ||
                              lower.contains("flash light jalao") || lower.contains("torch chalu karo") ||
                              lower.equals("torch") || lower.equals("flashlight");

        boolean isOffCommand = lower.contains("flashlight off") || lower.contains("torch off") ||
                               lower.contains("turn off flashlight") || lower.contains("turn off torch") ||
                               lower.contains("torch band karo") || lower.contains("torch band") ||
                               lower.contains("light off") || lower.contains("light band") ||
                               lower.contains("light band karo") || lower.contains("flash off") ||
                               lower.contains("flash band") || lower.contains("flash light off") ||
                               lower.contains("flash light band") || lower.contains("flashlight band");

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
     * ALARMS & TIMERS: "Wake me up at 7 AM" / "Set alarm for 6:30" / "Set timer for 5 minutes" / "Timer lagao"
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
            } else if (lower.contains("lagao") || lower.contains("set") || lower.contains("chalu") || lower.contains("start") || lower.contains("on")) {
                activity.showResponse("Aap kitne samay ka timer lagana chahte hain?", true);
                activity.showDynamicPill("Set Timer", android.R.drawable.ic_lock_idle_alarm);
                activity.setOrbState("IDLE");
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
            } else if (lower.contains("lagao") || lower.contains("set") || lower.contains("wake me up") || lower.contains("jaga dena")) {
                activity.showResponse("Aap kitne baje ka alarm lagana chahte hain?", true);
                activity.showDynamicPill("Set Alarm", android.R.drawable.ic_lock_idle_alarm);
                activity.setOrbState("IDLE");
                return true;
            }
        }

        return false;
    }

    /**
     * SETTINGS TOOL: "Open WiFi", "Turn on WiFi", "Bluetooth on", "Open settings", "Display settings"
     */
    private boolean handleSettings(String lower) {
        Intent intent = null;
        String actionTitle = null;

        if (lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("turn on wifi") || lower.contains("wifi on") || lower.contains("open wifi") || lower.contains("wifi kholo")) {
            intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
            actionTitle = "Wi-Fi Settings";
        } else if (lower.contains("bluetooth") || lower.contains("turn on bluetooth") || lower.contains("bluetooth on") || lower.contains("open bluetooth") || lower.contains("bluetooth kholo")) {
            intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            actionTitle = "Bluetooth Settings";
        } else if (lower.contains("airplane mode") || lower.contains("flight mode")) {
            intent = new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
            actionTitle = "Airplane Mode";
        } else if (lower.contains("display settings") || lower.contains("brightness settings")) {
            intent = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
            actionTitle = "Display Settings";
        } else if (lower.contains("sound settings") || lower.contains("volume settings") || lower.contains("audio settings")) {
            intent = new Intent(Settings.ACTION_SOUND_SETTINGS);
            actionTitle = "Sound Settings";
        } else if (lower.equals("open settings") || lower.equals("phone settings") || lower.equals("device settings") ||
                   lower.equals("settings") || lower.equals("settings open") || lower.contains("settings kholo")) {
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
     * Step 9 - Part 4: Indian & Hinglish Phonetic Normalizer.
     * Maps spoken speech variations to standard phonetic representation.
     */
    public static String toPhoneticKey(String text) {
        if (text == null) return "";
        String s = text.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (s.isEmpty()) return "";

        // Normalize common Indian speech-to-text vowel variants
        s = s.replace("ee", "i");
        s = s.replace("oo", "u");
        s = s.replace("aa", "a");
        s = s.replace("ai", "e");
        s = s.replace("ay", "e");

        // Normalize consonant variants
        s = s.replace("ph", "f");
        s = s.replace("sh", "s");
        s = s.replace("zh", "j");
        s = s.replace("z", "j");
        s = s.replace("w", "v");
        s = s.replace("b", "v");
        s = s.replace("th", "t");
        s = s.replace("dh", "d");
        s = s.replace("kh", "k");
        s = s.replace("gh", "g");
        s = s.replace("bh", "b");
        s = s.replace("ch", "c");

        // Collapse repeated identical adjacent letters
        StringBuilder sb = new StringBuilder();
        char prev = 0;
        for (int i = 0; i < s.length(); i++) {
            char curr = s.charAt(i);
            if (curr != prev) {
                sb.append(curr);
                prev = curr;
            }
        }
        String res = sb.toString();
        if (res.length() > 3 && (res.endsWith("a") || res.endsWith("h"))) {
            res = res.substring(0, res.length() - 1);
        }
        return res;
    }

    /**
     * Computes the Levenshtein distance between two strings.
     */
    public static int levenshteinDistance(String s1, String s2) {
        if (s1 == null) s1 = "";
        if (s2 == null) s2 = "";
        int len1 = s1.length();
        int len2 = s2.length();
        int[][] dp = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) dp[i][0] = i;
        for (int j = 0; j <= len2; j++) dp[0][j] = j;

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[len1][len2];
    }

    /**
     * Calculates a similarity score between 0.0 and 1.0 based on Levenshtein distance.
     */
    public static double fuzzyScore(String s1, String s2) {
        if (s1 == null || s2 == null || s1.isEmpty() || s2.isEmpty()) return 0.0;
        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;
        int dist = levenshteinDistance(s1, s2);
        return 1.0 - ((double) dist / maxLen);
    }

    /**
     * Step 9 - Part 4: On-Device Phonetic & Fuzzy Contact Resolution Engine.
     * Resolves spoken names using Phonetic keys, Levenshtein distance, Entity Aliases,
     * and Android Contacts database.
     */
    public ContactResolution resolveContact(String query) {
        if (query == null || query.trim().isEmpty()) return null;
        String lowerQuery = query.trim().toLowerCase();

        // 1. Resolve alias via Entity Dictionary
        String canonicalName = ALIAS_MAP.get(lowerQuery);
        if (canonicalName == null) {
            for (Map.Entry<String, String> entry : ALIAS_MAP.entrySet()) {
                if (lowerQuery.contains(entry.getKey())) {
                    canonicalName = entry.getValue();
                    break;
                }
            }
        }

        boolean isFav = isFamilyFavorite(canonicalName, lowerQuery);
        String searchName = (canonicalName != null) ? canonicalName : query.trim();
        String cleanQuery = searchName.toLowerCase().replaceAll("[^a-z0-9]", "");
        String queryPhonetic = toPhoneticKey(searchName);

        // 2. Query device contacts database with Phonetic & Fuzzy evaluation
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                new String[]{Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE},
                AssistantActivity.PERMISSION_REQUEST_CONTACTS_CALL);
            activity.handleStructuredError(
                AssistantActivity.ErrorCategory.PERMISSION_REQUIRED,
                "Call karne ke liye contacts aur phone permission ki zaroorat hai.",
                "Permission Needed"
            );
            return null;
        }

        try {
            Uri contactUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
            Cursor cursor = activity.getContentResolver().query(contactUri,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER},
                null, null, null);

            if (cursor != null) {
                int nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);

                String bestMatchName = null;
                String bestMatchNumber = null;
                double bestScore = 0.0;

                while (cursor.moveToNext()) {
                    String dName = cursor.getString(nameIdx);
                    String num = cursor.getString(numIdx);
                    if (dName == null || num == null) continue;

                    String lowerDName = dName.trim().toLowerCase();
                    String cleanDName = lowerDName.replaceAll("[^a-z0-9]", "");

                    // Exact match
                    if (cleanDName.equals(cleanQuery)) {
                        bestMatchName = dName;
                        bestMatchNumber = num;
                        bestScore = 2.0;
                        break;
                    }

                    // Substring match
                    if (cleanDName.contains(cleanQuery) || cleanQuery.contains(cleanDName)) {
                        double score = 0.90 + (cleanQuery.length() / (double) Math.max(cleanDName.length(), 1)) * 0.08;
                        if (score > bestScore) {
                            bestScore = score;
                            bestMatchName = dName;
                            bestMatchNumber = num;
                        }
                    }

                    // Token-level check (e.g., "Rohit" matching "Rohit Sharma")
                    String[] tokens = lowerDName.split("\\s+");
                    for (String token : tokens) {
                        String cleanToken = token.replaceAll("[^a-z0-9]", "");
                        if (cleanToken.isEmpty()) continue;

                        if (cleanToken.equals(cleanQuery)) {
                            double score = 0.95;
                            if (score > bestScore) {
                                bestScore = score;
                                bestMatchName = dName;
                                bestMatchNumber = num;
                            }
                        }

                        // Phonetic match on token
                        String tokenPhonetic = toPhoneticKey(cleanToken);
                        if (!queryPhonetic.isEmpty() && queryPhonetic.equals(tokenPhonetic)) {
                            double score = 0.92;
                            if (score > bestScore) {
                                bestScore = score;
                                bestMatchName = dName;
                                bestMatchNumber = num;
                            }
                        }

                        // Fuzzy match on token
                        double fScore = fuzzyScore(cleanQuery, cleanToken);
                        if (fScore >= 0.70) {
                            double combinedScore = fScore * 0.85;
                            if (!queryPhonetic.isEmpty() && fuzzyScore(queryPhonetic, tokenPhonetic) >= 0.70) {
                                combinedScore += 0.10;
                            }
                            if (combinedScore > bestScore) {
                                bestScore = combinedScore;
                                bestMatchName = dName;
                                bestMatchNumber = num;
                            }
                        }
                    }

                    // Whole name phonetic match
                    String wholePhonetic = toPhoneticKey(cleanDName);
                    if (!queryPhonetic.isEmpty() && queryPhonetic.equals(wholePhonetic)) {
                        double score = 0.92;
                        if (score > bestScore) {
                            bestScore = score;
                            bestMatchName = dName;
                            bestMatchNumber = num;
                        }
                    }

                    // Whole name fuzzy
                    double wholeFuzzy = fuzzyScore(cleanQuery, cleanDName);
                    if (wholeFuzzy >= 0.70 && wholeFuzzy > bestScore) {
                        bestScore = wholeFuzzy;
                        bestMatchName = dName;
                        bestMatchNumber = num;
                    }
                }
                cursor.close();

                if (bestMatchName != null && bestScore >= 0.68) {
                    boolean finalFav = isFav || isFamilyFavorite(bestMatchName, lowerQuery);
                    return new ContactResolution(bestMatchName, bestMatchNumber, finalFav);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Contacts query permission or error: " + e.getMessage());
        }

        // 3. Fallback to default user profile numbers with fuzzy/phonetic search
        if (canonicalName != null && DEFAULT_PHONE_NUMBERS.containsKey(canonicalName)) {
            return new ContactResolution(canonicalName, DEFAULT_PHONE_NUMBERS.get(canonicalName), true);
        }

        for (Map.Entry<String, String> entry : DEFAULT_PHONE_NUMBERS.entrySet()) {
            String defName = entry.getKey();
            if (defName.equalsIgnoreCase(searchName) ||
                toPhoneticKey(defName).equals(queryPhonetic) ||
                fuzzyScore(cleanQuery, defName.toLowerCase()) >= 0.75) {
                return new ContactResolution(defName, entry.getValue(), true);
            }
        }

        return null;
    }

    /**
     * SCREEN BRIGHTNESS PRESETS TOOL (Step 9 - Part 4)
     * Executes instant on-device brightness changes in under 10ms.
     */
    private boolean handleBrightness(String lower) {
        if (!lower.contains("brightness") && !lower.contains("screen tej") && !lower.contains("screen kam") &&
            !lower.contains("dim screen") && !lower.contains("screen light")) {
            return false;
        }

        if (lower.contains("100") || lower.contains("full") || lower.contains("max") || lower.contains("highest") ||
            lower.contains("screen tej") || lower.contains("badhao") || lower.contains("pura")) {
            activity.setScreenBrightness(1.0f, "100%");
            return true;
        } else if (lower.contains("0%") || lower.contains("min") || lower.contains("lowest") || lower.contains("dim") ||
                   lower.contains("kam") || lower.contains("dheere") || lower.contains("10%") || lower.contains("20%")) {
            activity.setScreenBrightness(0.15f, "15%");
            return true;
        } else if (lower.contains("50%") || lower.contains("medium") || lower.contains("half") || lower.contains("normal") ||
                   lower.contains("aadha") || lower.contains("adha")) {
            activity.setScreenBrightness(0.50f, "50%");
            return true;
        } else if (lower.contains("75%") || lower.contains("80%")) {
            activity.setScreenBrightness(0.75f, "75%");
            return true;
        } else if (lower.contains("25%") || lower.contains("30%")) {
            activity.setScreenBrightness(0.30f, "30%");
            return true;
        }
        return false;
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
     * OFFLINE TIME & DATE ENGINE IN HINDI (Step 7 - Part 4 & Step 9 - Part 4)
     */
    private boolean handleDateTime(String lower) {
        boolean isTime = lower.contains("time") || lower.contains("samay") || lower.contains("kitne baje") ||
                         lower.contains("kya baje") || lower.contains("ghadi") || lower.contains("time batao") ||
                         lower.contains("samay batao") || lower.equals("what time is it") || lower.contains("what time") ||
                         lower.contains("what is the time") || lower.contains("whats the time") || lower.contains("current time");
        boolean isTomorrow = lower.contains("kal kaun sa din") || lower.contains("kal kya din") ||
                             lower.contains("tomorrow date") || lower.contains("kal ki tarikh") ||
                             lower.contains("kal ki tareekh") || lower.contains("kal konsa din") ||
                             lower.contains("tomorrow's date") || lower.contains("kal kya date");
        boolean isYesterday = lower.contains("kal kya date thi") || lower.contains("yesterday date") ||
                              lower.contains("yesterday's date") || lower.contains("kal kya tarikh thi") ||
                              lower.contains("yesterday");
        boolean isDayAfter = lower.contains("parson") || lower.contains("day after tomorrow");
        boolean isMonth = lower.contains("current month") || lower.contains("kon sa mahina") ||
                          lower.contains("kaun sa mahina") || lower.contains("mahina kaun sa") || lower.contains("which month");
        boolean isYear = lower.contains("current year") || lower.contains("kaun sa saal") ||
                         lower.contains("kon sa saal") || lower.contains("saal kaun sa") || lower.contains("which year");
        boolean isDate = lower.contains("date") || lower.contains("tarikh") || lower.contains("tareekh") ||
                         lower.contains("aaj ka din") || lower.contains("kon sa din") || lower.contains("today date") ||
                         lower.contains("aaj ki date") || lower.contains("what is the date") || lower.contains("what's the date") ||
                         lower.contains("today's date") || lower.contains("aaj kya date") || lower.contains("aaj kya tarikh") ||
                         lower.contains("aaj kaun sa din");

        String[] daysHindi = {"Ravivar", "Somvar", "Mangalvar", "Budhvar", "Guruvar", "Shukravar", "Shanivar"};
        String[] monthsHindi = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};

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

        if (isTomorrow) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, 1);
            String dayName = daysHindi[cal.get(Calendar.DAY_OF_WEEK) - 1];
            int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
            String monthName = monthsHindi[cal.get(Calendar.MONTH)];
            int year = cal.get(Calendar.YEAR);
            String response = "Kal " + dayName + ", " + dayOfMonth + " " + monthName + " " + year + " hoga.";
            activity.showDynamicPill("Kal: " + dayOfMonth + " " + monthName, android.R.drawable.ic_menu_my_calendar);
            activity.showResponse(response, true);
            activity.setOrbState("IDLE");
            return true;
        }

        if (isDayAfter) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, 2);
            String dayName = daysHindi[cal.get(Calendar.DAY_OF_WEEK) - 1];
            int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
            String monthName = monthsHindi[cal.get(Calendar.MONTH)];
            int year = cal.get(Calendar.YEAR);
            String response = "Parson " + dayName + ", " + dayOfMonth + " " + monthName + " " + year + " hoga.";
            activity.showDynamicPill("Parson: " + dayOfMonth + " " + monthName, android.R.drawable.ic_menu_my_calendar);
            activity.showResponse(response, true);
            activity.setOrbState("IDLE");
            return true;
        }

        if (isYesterday) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_YEAR, -1);
            String dayName = daysHindi[cal.get(Calendar.DAY_OF_WEEK) - 1];
            int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
            String monthName = monthsHindi[cal.get(Calendar.MONTH)];
            int year = cal.get(Calendar.YEAR);
            String response = "Kal " + dayName + ", " + dayOfMonth + " " + monthName + " " + year + " tha.";
            activity.showDynamicPill("Beeta Kal: " + dayOfMonth + " " + monthName, android.R.drawable.ic_menu_my_calendar);
            activity.showResponse(response, true);
            activity.setOrbState("IDLE");
            return true;
        }

        if (isMonth) {
            Calendar cal = Calendar.getInstance();
            String monthName = monthsHindi[cal.get(Calendar.MONTH)];
            activity.showDynamicPill(monthName, android.R.drawable.ic_menu_my_calendar);
            activity.showResponse("Yeh mahina " + monthName + " hai.", true);
            activity.setOrbState("IDLE");
            return true;
        }

        if (isYear) {
            Calendar cal = Calendar.getInstance();
            int year = cal.get(Calendar.YEAR);
            activity.showDynamicPill("Year: " + year, android.R.drawable.ic_menu_my_calendar);
            activity.showResponse("Yeh saal " + year + " hai.", true);
            activity.setOrbState("IDLE");
            return true;
        }

        if (isDate) {
            Calendar cal = Calendar.getInstance();
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
     * Step 9 - Part 8: Normalizes English and Hindi word numbers into numeric digits.
     * e.g. "five plus five" -> "5 plus 5", "fifty divided by two" -> "50 divided by 2"
     */
    private String normalizeWordNumbers(String text) {
        if (text == null || text.trim().isEmpty()) return "";
        String s = text.toLowerCase();

        // Hindi number words
        s = s.replaceAll("\\bshunya\\b", "0");
        s = s.replaceAll("\\bek\\b", "1");
        s = s.replaceAll("\\bdo\\b", "2");
        s = s.replaceAll("\\bteen\\b", "3");
        s = s.replaceAll("\\bchaar\\b|\\bchar\\b", "4");
        s = s.replaceAll("\\bpaanch\\b|\\bpanch\\b", "5");
        s = s.replaceAll("\\bchhe\\b|\\bche\\b", "6");
        s = s.replaceAll("\\bsaat\\b", "7");
        s = s.replaceAll("\\baath\\b|\\bath\\b", "8");
        s = s.replaceAll("\\bnau\\b", "9");
        s = s.replaceAll("\\bdas\\b", "10");
        s = s.replaceAll("\\bgyarah\\b", "11");
        s = s.replaceAll("\\bbaarah\\b|\\bbarah\\b", "12");
        s = s.replaceAll("\\bterah\\b", "13");
        s = s.replaceAll("\\bchaudah\\b", "14");
        s = s.replaceAll("\\bpandrah\\b", "15");
        s = s.replaceAll("\\bsolah\\b", "16");
        s = s.replaceAll("\\bsatrah\\b", "17");
        s = s.replaceAll("\\bathaarah\\b|\\batharah\\b", "18");
        s = s.replaceAll("\\bunnees\\b|\\bunnis\\b", "19");
        s = s.replaceAll("\\bbees\\b|\\bbis\\b", "20");
        s = s.replaceAll("\\btees\\b|\\btis\\b", "30");
        s = s.replaceAll("\\bchaalees\\b|\\bchalis\\b", "40");
        s = s.replaceAll("\\bpachaas\\b|\\bpachas\\b", "50");
        s = s.replaceAll("\\bsaath\\b|\\bsath\\b", "60");
        s = s.replaceAll("\\bsattar\\b", "70");
        s = s.replaceAll("\\bassi\\b", "80");
        s = s.replaceAll("\\bnabbey\\b|\\bnabbe\\b", "90");
        s = s.replaceAll("\\bsau\\b", "100");
        s = s.replaceAll("\\bhazaar\\b|\\bhazar\\b", "1000");
        s = s.replaceAll("\\blakh\\b|\\blac\\b", "100000");
        s = s.replaceAll("\\bcrore\\b|\\bkrod\\b", "10000000");

        // English number words
        s = s.replaceAll("\\bzero\\b", "0");
        s = s.replaceAll("\\bone\\b", "1");
        s = s.replaceAll("\\btwo\\b", "2");
        s = s.replaceAll("\\bthree\\b", "3");
        s = s.replaceAll("\\bfour\\b", "4");
        s = s.replaceAll("\\bfive\\b", "5");
        s = s.replaceAll("\\bsix\\b", "6");
        s = s.replaceAll("\\bseven\\b", "7");
        s = s.replaceAll("\\beight\\b", "8");
        s = s.replaceAll("\\bnine\\b", "9");
        s = s.replaceAll("\\bten\\b", "10");
        s = s.replaceAll("\\beleven\\b", "11");
        s = s.replaceAll("\\btwelve\\b", "12");
        s = s.replaceAll("\\bthirteen\\b", "13");
        s = s.replaceAll("\\bfourteen\\b", "14");
        s = s.replaceAll("\\bfifteen\\b", "15");
        s = s.replaceAll("\\bsixteen\\b", "16");
        s = s.replaceAll("\\bseventeen\\b", "17");
        s = s.replaceAll("\\beighteen\\b", "18");
        s = s.replaceAll("\\bnineteen\\b", "19");
        s = s.replaceAll("\\btwenty\\b", "20");
        s = s.replaceAll("\\bthirty\\b", "30");
        s = s.replaceAll("\\bforty\\b", "40");
        s = s.replaceAll("\\bfifty\\b", "50");
        s = s.replaceAll("\\bsixty\\b", "60");
        s = s.replaceAll("\\bseventy\\b", "70");
        s = s.replaceAll("\\beighty\\b", "80");
        s = s.replaceAll("\\bninety\\b", "90");
        s = s.replaceAll("\\bhundred\\b", "100");
        s = s.replaceAll("\\bthousand\\b", "1000");
        s = s.replaceAll("\\bmillion\\b", "1000000");

        // Merge tens and units (e.g., "20 5" -> "25", "50 2" -> "52")
        Matcher tm = Pattern.compile("\\b(20|30|40|50|60|70|80|90)\\s+([1-9])\\b").matcher(s);
        StringBuffer sb = new StringBuffer();
        while (tm.find()) {
            int tens = Integer.parseInt(tm.group(1));
            int ones = Integer.parseInt(tm.group(2));
            tm.appendReplacement(sb, String.valueOf(tens + ones));
        }
        tm.appendTail(sb);
        return sb.toString();
    }

    /**
     * OFFLINE MATH CALCULATOR ENGINE IN HINDI & ENGLISH (Step 7 - Part 4 & Step 9 - Part 8)
     * Handles arithmetic, percentages, square roots, squares, powers, and cubes in <5ms.
     * Integrates word number normalization ("five plus five" -> "5 plus 5").
     */
    private boolean handleMath(String command, String lower) {
        String normLower = normalizeWordNumbers(lower);

        // 1. Percentage: "20 percent of 500" / "500 ka 20 percent" / "15% of 200"
        Pattern pctPattern = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:%|percent|pratishat)\\s*(?:of|ka)?\\s*(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
        Matcher pctMatcher = pctPattern.matcher(normLower);
        if (pctMatcher.find()) {
            try {
                double pct = Double.parseDouble(pctMatcher.group(1));
                double total = Double.parseDouble(pctMatcher.group(2));
                double res = (pct * total) / 100.0;
                String resultStr = (res == (long) res) ? String.format(Locale.getDefault(), "%d", (long) res) : String.format(Locale.getDefault(), "%.2f", res);
                String speech = (long) total + " ka " + (long) pct + " percent hai " + resultStr + ".";
                activity.showDynamicPill("Math: " + resultStr, android.R.drawable.ic_menu_compass);
                activity.showResponse(speech, true);
                activity.setOrbState("IDLE");
                return true;
            } catch (Exception ignored) {}
        }
        Pattern pctPatternAlt = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*ka\\s*(\\d+(?:\\.\\d+)?)\\s*(?:%|percent|pratishat)", Pattern.CASE_INSENSITIVE);
        Matcher pctMatcherAlt = pctPatternAlt.matcher(normLower);
        if (pctMatcherAlt.find()) {
            try {
                double total = Double.parseDouble(pctMatcherAlt.group(1));
                double pct = Double.parseDouble(pctMatcherAlt.group(2));
                double res = (pct * total) / 100.0;
                String resultStr = (res == (long) res) ? String.format(Locale.getDefault(), "%d", (long) res) : String.format(Locale.getDefault(), "%.2f", res);
                String speech = (long) total + " ka " + (long) pct + " percent hai " + resultStr + ".";
                activity.showDynamicPill("Math: " + resultStr, android.R.drawable.ic_menu_compass);
                activity.showResponse(speech, true);
                activity.setOrbState("IDLE");
                return true;
            } catch (Exception ignored) {}
        }

        // 2. Square Root: "square root of 144" / "root of 144" / "144 ka root" / "144 ka square root"
        Pattern rootPattern = Pattern.compile("(?:square root of|root of|under root of)\\s*(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
        Matcher rootMatcher = rootPattern.matcher(normLower);
        if (rootMatcher.find()) {
            try {
                double val = Double.parseDouble(rootMatcher.group(1));
                double res = Math.sqrt(val);
                String resultStr = (res == (long) res) ? String.format(Locale.getDefault(), "%d", (long) res) : String.format(Locale.getDefault(), "%.2f", res);
                activity.showDynamicPill("Root: " + resultStr, android.R.drawable.ic_menu_compass);
                activity.showResponse("Iska square root hai " + resultStr + ".", true);
                activity.setOrbState("IDLE");
                return true;
            } catch (Exception ignored) {}
        }
        Pattern rootPatternHindi = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*ka\\s*(?:square root|root)", Pattern.CASE_INSENSITIVE);
        Matcher rootMatcherHindi = rootPatternHindi.matcher(normLower);
        if (rootMatcherHindi.find()) {
            try {
                double val = Double.parseDouble(rootMatcherHindi.group(1));
                double res = Math.sqrt(val);
                String resultStr = (res == (long) res) ? String.format(Locale.getDefault(), "%d", (long) res) : String.format(Locale.getDefault(), "%.2f", res);
                activity.showDynamicPill("Root: " + resultStr, android.R.drawable.ic_menu_compass);
                activity.showResponse("Iska square root hai " + resultStr + ".", true);
                activity.setOrbState("IDLE");
                return true;
            } catch (Exception ignored) {}
        }

        // 3. Square & Cube: "square of 25" / "25 ka square" / "cube of 5" / "5 ka cube"
        Pattern sqPattern = Pattern.compile("(?:square of)\\s*(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
        Matcher sqMatcher = sqPattern.matcher(normLower);
        if (sqMatcher.find()) {
            try {
                double val = Double.parseDouble(sqMatcher.group(1));
                double res = val * val;
                String resultStr = (res == (long) res) ? String.format(Locale.getDefault(), "%d", (long) res) : String.format(Locale.getDefault(), "%.2f", res);
                activity.showDynamicPill("Square: " + resultStr, android.R.drawable.ic_menu_compass);
                activity.showResponse("Iska square hai " + resultStr + ".", true);
                activity.setOrbState("IDLE");
                return true;
            } catch (Exception ignored) {}
        }
        Pattern sqPatternHindi = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*ka\\s*square", Pattern.CASE_INSENSITIVE);
        Matcher sqMatcherHindi = sqPatternHindi.matcher(normLower);
        if (sqMatcherHindi.find()) {
            try {
                double val = Double.parseDouble(sqMatcherHindi.group(1));
                double res = val * val;
                String resultStr = (res == (long) res) ? String.format(Locale.getDefault(), "%d", (long) res) : String.format(Locale.getDefault(), "%.2f", res);
                activity.showDynamicPill("Square: " + resultStr, android.R.drawable.ic_menu_compass);
                activity.showResponse("Iska square hai " + resultStr + ".", true);
                activity.setOrbState("IDLE");
                return true;
            } catch (Exception ignored) {}
        }
        Pattern cubePattern = Pattern.compile("(?:cube of)\\s*(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
        Matcher cubeMatcher = cubePattern.matcher(normLower);
        if (cubeMatcher.find()) {
            try {
                double val = Double.parseDouble(cubeMatcher.group(1));
                double res = val * val * val;
                String resultStr = (res == (long) res) ? String.format(Locale.getDefault(), "%d", (long) res) : String.format(Locale.getDefault(), "%.2f", res);
                activity.showDynamicPill("Cube: " + resultStr, android.R.drawable.ic_menu_compass);
                activity.showResponse("Iska cube hai " + resultStr + ".", true);
                activity.setOrbState("IDLE");
                return true;
            } catch (Exception ignored) {}
        }
        Pattern cubePatternHindi = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*ka\\s*cube", Pattern.CASE_INSENSITIVE);
        Matcher cubeMatcherHindi = cubePatternHindi.matcher(normLower);
        if (cubeMatcherHindi.find()) {
            try {
                double val = Double.parseDouble(cubeMatcherHindi.group(1));
                double res = val * val * val;
                String resultStr = (res == (long) res) ? String.format(Locale.getDefault(), "%d", (long) res) : String.format(Locale.getDefault(), "%.2f", res);
                activity.showDynamicPill("Cube: " + resultStr, android.R.drawable.ic_menu_compass);
                activity.showResponse("Iska cube hai " + resultStr + ".", true);
                activity.setOrbState("IDLE");
                return true;
            } catch (Exception ignored) {}
        }

        // 4. Sum / Difference / Product phrasing
        Pattern sumPhrase = Pattern.compile("(?:sum of|addition of|add)\\s*(\\d+(?:\\.\\d+)?)\\s*(?:and|aur|plus|\\+)\\s*(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
        Matcher sumM = sumPhrase.matcher(normLower);
        if (sumM.find()) {
            try {
                double num1 = Double.parseDouble(sumM.group(1));
                double num2 = Double.parseDouble(sumM.group(2));
                double result = num1 + num2;
                String resultStr = (result == (long) result) ? String.format(Locale.getDefault(), "%d", (long) result) : String.format(Locale.getDefault(), "%.2f", result);
                String speech = "Iska jod hai " + resultStr + ".";
                activity.showDynamicPill("Math: " + resultStr, android.R.drawable.ic_menu_compass);
                activity.showResponse(speech, true);
                activity.setOrbState("IDLE");
                return true;
            } catch (Exception ignored) {}
        }

        // 5. Basic Arithmetic Operations (+, -, *, /)
        Pattern p = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:plus|\\+|jod|minus|\\-|ghatao|ghata|times|multiplied by|\\*|x|into|guna|divided by|\\/|bhag)\\s*(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(normLower);
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
     * DEEP HARDWARE VOLUME & AUDIO SETTINGS IN HINDI (Step 7 - Part 4 & Step 9 - Part 4)
     */
    private boolean handleVolume(String lower) {
        // Percentage / Preset volume
        if (lower.contains("volume 100") || lower.contains("full volume") || lower.contains("max volume") ||
            lower.contains("maximum volume") || lower.contains("awaz full")) {
            activity.setDeviceVolumeLevel(100);
            return true;
        } else if (lower.contains("volume 50") || lower.contains("half volume") || lower.contains("volume aadha") ||
                   lower.contains("awaz aadhi") || lower.contains("medium volume")) {
            activity.setDeviceVolumeLevel(50);
            return true;
        } else if (lower.contains("volume 0") || lower.contains("volume zero") || lower.contains("awaz zero")) {
            activity.setDeviceVolumeLevel(0);
            return true;
        } else if (lower.contains("volume 80") || lower.contains("volume 75")) {
            activity.setDeviceVolumeLevel(80);
            return true;
        } else if (lower.contains("volume 20") || lower.contains("volume 25") || lower.contains("volume 30")) {
            activity.setDeviceVolumeLevel(25);
            return true;
        }

        // Stepped Volume Up/Down
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
     * Step 9 - Part 4: Fast App Launcher (<5ms) using FAST_APP_MAP with fallback.
     */
    private boolean handleApps(String command, String lower) {
        if (lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("start app ") ||
            lower.startsWith("kholo ") || lower.startsWith("start ")) {
            String appName = command.replaceAll("(?i)(open app|launch app|start app|open|launch|start|kholo)\\s*", "").trim();
            if (!appName.isEmpty()) {
                String cleanApp = appName.toLowerCase().replaceAll("[^a-z0-9\\s]", "").trim();
                String targetPkg = FAST_APP_MAP.get(cleanApp);
                if (targetPkg != null) {
                    try {
                        Intent launchIntent = activity.getPackageManager().getLaunchIntentForPackage(targetPkg);
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            activity.startActivity(launchIntent);
                            String capName = cleanApp.substring(0, 1).toUpperCase() + cleanApp.substring(1);
                            activity.showDynamicPill(capName + " Opened", android.R.drawable.ic_menu_compass);
                            activity.showResponse(capName + " khol raha hoon.", true);
                            activity.setOrbState("IDLE");
                            return true;
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Fast launch error for " + targetPkg + ": " + e.getMessage());
                    }
                }
                activity.launchAppByName(appName);
                return true;
            }
        }
        return false;
    }

    /**
     * Step 9 - Part 8: Strict Routing Wall.
     * Evaluates if a query belongs to device hardware/system control domains.
     * Hardware/system queries must NEVER leak to the Gemini Cloud LLM.
     */
    public static boolean isStrictDeviceUtilityQuery(String lower) {
        if (lower == null || lower.trim().isEmpty()) return false;
        String s = lower.toLowerCase();

        // Flashlight / Torch
        if (s.contains("torch") || s.contains("flashlight") || s.contains("flash light")) return true;

        // Camera / Photo capture
        if (s.contains("camera") || s.contains("selfie") || s.contains("photo lo") || s.contains("photo kheecho") ||
            s.contains("photo khincho") || s.contains("picture lo") || s.contains("click photo") || s.contains("take photo")) return true;

        // Alarm & Timer
        if (s.contains("alarm") || s.contains("timer") || s.contains("wake me up") || s.contains("jaga dena") ||
            s.contains("utha dena") || s.contains("minute ka timer") || s.contains("second ka timer")) return true;

        // Volume / Audio
        if (s.contains("volume") || s.contains("awaz badhao") || s.contains("awaz kam") || s.contains("awaz full") ||
            s.contains("awaz tez") || s.contains("awaz dheemi") || s.contains("mute kar") || s.contains("sound badhao") ||
            s.contains("sound kam")) return true;

        // Brightness / Display
        if (s.contains("brightness") || s.contains("screen light") || s.contains("roshni badhao") || s.contains("roshni kam")) return true;

        // Settings / Connectivity
        if (s.contains("wifi") || s.contains("wi-fi") || s.contains("bluetooth") || s.contains("hotspot") ||
            s.contains("airplane mode") || s.contains("flight mode") || s.contains("open settings") ||
            s.contains("phone setting") || s.contains("device setting") || s.contains("settings kholo")) return true;

        // Battery
        if (s.contains("battery") || s.contains("battery percentage") || s.contains("battery kitni") ||
            s.contains("charge kitna") || s.contains("charging kitni")) return true;

        // Calling / Phone
        if (s.startsWith("call ") || s.startsWith("phone ") || s.startsWith("dial ") ||
            s.contains("ko call karo") || s.contains("ko phone lagao") || s.contains("ko call lagao")) return true;

        return false;
    }

    /**
     * Step 9 - Part 8: Localized On-Device Fallback for Device Utility Queries.
     * Prevents hardware/system utility commands from leaking to the online Gemini cloud LLM.
     */
    private void handleDeviceUtilityFallback(String command, String lower) {
        // Flashlight / Torch
        if (lower.contains("torch") || lower.contains("flashlight") || lower.contains("flash light")) {
            boolean turnOn = !lower.contains("off") && !lower.contains("band");
            activity.toggleFlashlight(turnOn);
            if (turnOn) {
                activity.showDynamicPill("Torch On", android.R.drawable.ic_menu_camera);
                activity.showResponse("Torch on kar di gayi hai.", true);
            } else {
                activity.showDynamicPill("Torch Off", android.R.drawable.ic_menu_camera);
                activity.showResponse("Torch band kar di gayi hai.", true);
            }
            activity.setOrbState("IDLE");
            return;
        }

        // Camera / Selfie / Photos
        if (lower.contains("camera") || lower.contains("selfie") || lower.contains("photo") || lower.contains("picture")) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA}, AssistantActivity.PERMISSION_REQUEST_CAMERA);
            }
            try {
                Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
                activity.showDynamicPill("Camera Opened", android.R.drawable.ic_menu_camera);
                activity.showResponse("Opening camera.", true);
                activity.setOrbState("IDLE");
            } catch (Exception e) {
                activity.showDynamicPill("Camera", android.R.drawable.ic_menu_camera);
                activity.showResponse("Camera open nahi ho saka.", true);
                activity.setOrbState("IDLE");
            }
            return;
        }

        // Alarm Clock
        if (lower.contains("alarm") || lower.contains("wake me up") || lower.contains("jaga dena") || lower.contains("utha dena")) {
            activity.showDynamicPill("Alarm Clock", android.R.drawable.ic_lock_idle_alarm);
            activity.showResponse("Aap kitne baje ka alarm lagana chahte hain? Kripya samay batayein.", true);
            activity.setOrbState("IDLE");
            return;
        }

        // Timer
        if (lower.contains("timer")) {
            activity.showDynamicPill("Timer", android.R.drawable.ic_menu_recent_history);
            activity.showResponse("Aap kitne minute ka timer lagana chahte hain?", true);
            activity.setOrbState("IDLE");
            return;
        }

        // Volume / Sound / Audio
        if (lower.contains("volume") || lower.contains("awaz") || lower.contains("sound")) {
            if (lower.contains("badhao") || lower.contains("tez") || lower.contains("up") || lower.contains("high") || lower.contains("increase")) {
                activity.adjustDeviceVolume(AudioManager.ADJUST_RAISE);
            } else if (lower.contains("kam") || lower.contains("dheemi") || lower.contains("down") || lower.contains("low") || lower.contains("decrease")) {
                activity.adjustDeviceVolume(AudioManager.ADJUST_LOWER);
            } else if (lower.contains("mute") || lower.contains("silent") || lower.contains("band")) {
                activity.setDeviceMute(true);
            } else if (lower.contains("unmute") || lower.contains("kholo")) {
                activity.setDeviceMute(false);
            } else {
                activity.showDynamicPill("Volume", android.R.drawable.ic_lock_silent_mode_off);
                activity.showResponse("Volume settings open kar raha hoon.", true);
                activity.setOrbState("IDLE");
            }
            return;
        }

        // Brightness / Display
        if (lower.contains("brightness") || lower.contains("screen light") || lower.contains("roshni") || lower.contains("screen tej") || lower.contains("screen kam")) {
            if (lower.contains("badhao") || lower.contains("tez") || lower.contains("up") || lower.contains("full") || lower.contains("max")) {
                activity.setScreenBrightness(1.0f, "100%");
            } else if (lower.contains("kam") || lower.contains("dheemi") || lower.contains("down") || lower.contains("dim")) {
                activity.setScreenBrightness(0.15f, "15%");
            } else {
                activity.setScreenBrightness(0.50f, "50%");
            }
            return;
        }

        // Settings / Connectivity (Wi-Fi, Bluetooth)
        if (lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("bluetooth") || lower.contains("setting") || lower.contains("hotspot") || lower.contains("airplane")) {
            Intent intent;
            String title;
            if (lower.contains("wifi") || lower.contains("wi-fi")) {
                intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                title = "Wi-Fi Settings";
            } else if (lower.contains("bluetooth")) {
                intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                title = "Bluetooth Settings";
            } else if (lower.contains("airplane")) {
                intent = new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
                title = "Airplane Mode";
            } else {
                intent = new Intent(Settings.ACTION_SETTINGS);
                title = "Settings";
            }
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
                activity.showDynamicPill(title, android.R.drawable.ic_menu_preferences);
                activity.showResponse("Opening " + title + ".", true);
                activity.setOrbState("IDLE");
            } catch (Exception e) {
                activity.showResponse("Settings kholne mein samasya aayi.", true);
                activity.setOrbState("IDLE");
            }
            return;
        }

        // Battery
        if (lower.contains("battery") || lower.contains("charge")) {
            activity.getDeviceBatteryLevel();
            return;
        }

        // Calling / Phone
        if (lower.contains("call") || lower.contains("phone") || lower.contains("dial")) {
            activity.showDynamicPill("Call Request", android.R.drawable.ic_menu_call);
            activity.showResponse("Aap kise call lagana chahte hain? Kripya naam batayein.", true);
            activity.setOrbState("IDLE");
            return;
        }

        // Generic local safety fallback
        activity.showDynamicPill("Device Utility", android.R.drawable.ic_menu_info_details);
        activity.showResponse("Mujhe samajh nahi aaya, kripya dobara kahein.", true);
        activity.setOrbState("IDLE");
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

