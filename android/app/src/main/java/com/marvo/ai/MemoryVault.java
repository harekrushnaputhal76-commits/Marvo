package com.marvo.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Calendar;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Step 9 - Part 10 & Step 13.5: Local User Profile Vault (MemoryVault).
 * Securely caches personal parameters, family aliases, active time window, and contextual memory locally on-device.
 * Zero cloud dependency.
 */
public class MemoryVault {
    private static final String PREF_NAME = "marvo_user_vault";
    private static final String KEY_USER_LOCATION = "user_location";
    private static final String KEY_MOM_NUMBER = "mom_number";
    private static final String KEY_DAD_NUMBER = "dad_number";
    private static final String KEY_RECENT_TOPIC = "recent_topic";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_VOICE_PROFILE = "voice_profile";
    private static final String KEY_ACTIVE_START_TIME = "active_start_time";
    private static final String KEY_ACTIVE_END_TIME = "active_end_time";
    private static final String KEY_CUSTOM_QA = "custom_qa_database";
    private static final String KEY_CONVERSATION_HISTORY = "conversation_history_turns";
    private static final int MAX_SAVED_TURNS = 10;

    public static final String DEFAULT_MOM_NUMBER = "+919437000002";
    public static final String DEFAULT_DAD_NUMBER = "+919437000001";
    public static final int DEFAULT_VOICE_PROFILE = 2; // Profile 2: Male Hindi
    public static final String DEFAULT_ACTIVE_START_TIME = "06:00";
    public static final String DEFAULT_ACTIVE_END_TIME = "23:00";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // User Location (Weather, Maps, Local Search)
    public static String getUserLocation(Context context) {
        if (context == null) return "";
        return getPrefs(context).getString(KEY_USER_LOCATION, "");
    }

    public static void setUserLocation(Context context, String location) {
        if (context == null || location == null) return;
        getPrefs(context).edit().putString(KEY_USER_LOCATION, location.trim()).apply();
    }

    // Mother Contact Number
    public static String getMomNumber(Context context) {
        if (context == null) return DEFAULT_MOM_NUMBER;
        return getPrefs(context).getString(KEY_MOM_NUMBER, DEFAULT_MOM_NUMBER);
    }

    public static void setMomNumber(Context context, String number) {
        if (context == null || number == null) return;
        getPrefs(context).edit().putString(KEY_MOM_NUMBER, number.trim()).apply();
    }

    // Father Contact Number
    public static String getDadNumber(Context context) {
        if (context == null) return DEFAULT_DAD_NUMBER;
        return getPrefs(context).getString(KEY_DAD_NUMBER, DEFAULT_DAD_NUMBER);
    }

    public static void setDadNumber(Context context, String number) {
        if (context == null || number == null) return;
        getPrefs(context).edit().putString(KEY_DAD_NUMBER, number.trim()).apply();
    }

    // Recent Context Topic
    public static String getRecentTopic(Context context) {
        if (context == null) return "";
        return getPrefs(context).getString(KEY_RECENT_TOPIC, "");
    }

    public static void saveRecentTopic(Context context, String topic) {
        if (context == null || topic == null) return;
        getPrefs(context).edit().putString(KEY_RECENT_TOPIC, topic.trim()).apply();
    }

    // User Name
    public static String getUserName(Context context) {
        if (context == null) return "";
        return getPrefs(context).getString(KEY_USER_NAME, "");
    }

    public static void setUserName(Context context, String name) {
        if (context == null || name == null) return;
        getPrefs(context).edit().putString(KEY_USER_NAME, name.trim()).apply();
    }

    // Voice Profile (1: Male English, 2: Male Hindi, 3: Female English, 4: Female Hindi)
    public static int getVoiceProfile(Context context) {
        if (context == null) return DEFAULT_VOICE_PROFILE;
        return getPrefs(context).getInt(KEY_VOICE_PROFILE, DEFAULT_VOICE_PROFILE);
    }

    public static void setVoiceProfile(Context context, int profile) {
        if (context == null) return;
        getPrefs(context).edit().putInt(KEY_VOICE_PROFILE, profile).apply();
    }

    // Active Time Window (Start and End Hours for wake word listening)
    public static String getActiveStartTime(Context context) {
        if (context == null) return DEFAULT_ACTIVE_START_TIME;
        return getPrefs(context).getString(KEY_ACTIVE_START_TIME, DEFAULT_ACTIVE_START_TIME);
    }

    public static void setActiveStartTime(Context context, String time) {
        if (context == null || time == null) return;
        getPrefs(context).edit().putString(KEY_ACTIVE_START_TIME, time.trim()).apply();
    }

    public static String getActiveEndTime(Context context) {
        if (context == null) return DEFAULT_ACTIVE_END_TIME;
        return getPrefs(context).getString(KEY_ACTIVE_END_TIME, DEFAULT_ACTIVE_END_TIME);
    }

    public static void setActiveEndTime(Context context, String time) {
        if (context == null || time == null) return;
        getPrefs(context).edit().putString(KEY_ACTIVE_END_TIME, time.trim()).apply();
    }

    /**
     * Step 13.5: Evaluates if current system time is within user-configured active time window.
     */
    public static boolean isWithinActiveWindow(Context context) {
        if (context == null) return true;
        try {
            String startStr = getActiveStartTime(context);
            String endStr = getActiveEndTime(context);

            int startMinutes = parseMinutes(startStr, 6 * 60);
            int endMinutes = parseMinutes(endStr, 23 * 60);

            Calendar cal = Calendar.getInstance();
            int currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);

            if (startMinutes <= endMinutes) {
                return currentMinutes >= startMinutes && currentMinutes <= endMinutes;
            } else {
                // Spans midnight (e.g. 22:00 to 06:00)
                return currentMinutes >= startMinutes || currentMinutes <= endMinutes;
            }
        } catch (Exception e) {
            return true;
        }
    }

    private static int parseMinutes(String timeStr, int defaultMinutes) {
        if (timeStr == null || !timeStr.contains(":")) return defaultMinutes;
        try {
            String[] parts = timeStr.trim().split(":");
            int hour = Integer.parseInt(parts[0].trim());
            int min = Integer.parseInt(parts[1].trim());
            return hour * 60 + min;
        } catch (Exception e) {
            return defaultMinutes;
        }
    }

    // ===== Step 15: Custom Q&A Knowledge Base ("Teach AI") =====

    // ===== Step 21: Clean Native Storage Architecture =====

    public static File getModelsDir(Context context) {
        if (context == null) return null;
        File documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File dir = new File(documents, "Marvo_Models");
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            return null;
        }

        // Move models downloaded by older builds into the uninstall-resistant public location.
        File legacyDir = new File(context.getFilesDir(), "models");
        if (legacyDir.isDirectory()) {
            File[] legacyFiles = legacyDir.listFiles();
            if (legacyFiles != null) {
                for (File legacyFile : legacyFiles) {
                    if (!legacyFile.isFile()) continue;
                    File migratedFile = new File(dir, legacyFile.getName());
                    if (migratedFile.exists()) continue;
                    try {
                        if (!legacyFile.renameTo(migratedFile)) {
                            copyFile(legacyFile, migratedFile);
                            if (!legacyFile.delete()) {
                                android.util.Log.w("MarvoStorage", "Could not remove migrated file: " + legacyFile.getName());
                            }
                        }
                    } catch (Exception e) {
                        android.util.Log.w("MarvoStorage", "Model migration failed: " + legacyFile.getName(), e);
                    }
                }
            }
        }
        return dir;
    }

    private static void copyFile(File source, File target) throws Exception {
        FileInputStream input = new FileInputStream(source);
        FileOutputStream output = new FileOutputStream(target);
        try {
            byte[] buffer = new byte[1024 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            output.getFD().sync();
        } finally {
            try { input.close(); } finally { output.close(); }
        }
    }

    public static File getMemoryDir(Context context) {
        if (context == null) return null;
        File dir = new File(context.getFilesDir(), "memory");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File getCacheDir(Context context) {
        if (context == null) return null;
        File dir = new File(context.getFilesDir(), "cache");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static void writeStringToFile(File file, String data) {
        if (file == null || data == null) return;
        try {
            FileOutputStream fos = new FileOutputStream(file);
            OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
            osw.write(data);
            osw.flush();
            osw.close();
            fos.close();
        } catch (Exception ignored) {}
    }

    private static String readStringFromFile(File file) {
        if (file == null || !file.exists()) return null;
        try {
            FileInputStream fis = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            fis.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    // ===== Step 15 & 21: Custom Q&A Knowledge Base ("Teach AI") =====

    /**
     * Saves a custom question-answer pair to the local knowledge base.
     * Synchronized across SharedPreferences, CapacitorStorage, and /memory/custom_qa.json.
     */
    public static void saveCustomQA(Context context, String question, String answer) {
        if (context == null || question == null || answer == null) return;
        try {
            String existing = getAllCustomQA(context);
            JSONArray qaArray = new JSONArray(existing);

            JSONObject entry = new JSONObject();
            entry.put("q", question.trim().toLowerCase());
            entry.put("a", answer.trim());
            qaArray.put(entry);

            String jsonStr = qaArray.toString();
            getPrefs(context).edit().putString(KEY_CUSTOM_QA, jsonStr).apply();
            try {
                context.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE)
                    .edit().putString(KEY_CUSTOM_QA, jsonStr).apply();
            } catch (Exception ignored) {}
            File memFile = new File(getMemoryDir(context), "custom_qa.json");
            writeStringToFile(memFile, jsonStr);
        } catch (Exception e) {
            // Fallback: reset and save fresh
            try {
                JSONArray fresh = new JSONArray();
                JSONObject entry = new JSONObject();
                entry.put("q", question.trim().toLowerCase());
                entry.put("a", answer.trim());
                fresh.put(entry);
                String jsonStr = fresh.toString();
                getPrefs(context).edit().putString(KEY_CUSTOM_QA, jsonStr).apply();
                try {
                    context.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE)
                        .edit().putString(KEY_CUSTOM_QA, jsonStr).apply();
                } catch (Exception ignored) {}
                File memFile = new File(getMemoryDir(context), "custom_qa.json");
                writeStringToFile(memFile, jsonStr);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Searches custom Q&A database for a fuzzy match against the user's spoken query.
     * Uses String.contains() matching — if any taught question is contained in the query
     * or the query is contained in the taught question, returns the taught answer.
     * Returns null if no match found.
     */
    public static String getCustomQAAnswer(Context context, String query) {
        if (context == null || query == null || query.trim().isEmpty()) return null;
        try {
            String existing = getAllCustomQA(context);
            JSONArray qaArray = new JSONArray(existing);
            String lowerQuery = query.trim().toLowerCase();

            for (int i = 0; i < qaArray.length(); i++) {
                JSONObject entry = qaArray.getJSONObject(i);
                String taughtQuestion = entry.getString("q").toLowerCase();
                String taughtAnswer = entry.getString("a");

                // Fuzzy match: query contains taught question OR taught question contains query
                if (lowerQuery.contains(taughtQuestion) || taughtQuestion.contains(lowerQuery)) {
                    return taughtAnswer;
                }
            }
        } catch (Exception e) {
            // Silently fail — no match
        }
        return null;
    }

    /**
     * Returns all stored custom Q&A pairs as a JSON array string.
     * Checked across SharedPreferences, CapacitorStorage, and /memory/custom_qa.json.
     */
    public static String getAllCustomQA(Context context) {
        if (context == null) return "[]";
        String val = getPrefs(context).getString(KEY_CUSTOM_QA, null);
        if (val != null && !val.trim().isEmpty() && !val.equals("[]")) {
            return val;
        }
        try {
            String capVal = context.getSharedPreferences("CapacitorStorage", Context.MODE_PRIVATE)
                .getString(KEY_CUSTOM_QA, null);
            if (capVal != null && !capVal.trim().isEmpty() && !capVal.equals("[]")) {
                getPrefs(context).edit().putString(KEY_CUSTOM_QA, capVal).apply();
                return capVal;
            }
        } catch (Exception ignored) {}
        File memFile = new File(getMemoryDir(context), "custom_qa.json");
        String fileVal = readStringFromFile(memFile);
        if (fileVal != null && !fileVal.trim().isEmpty()) {
            getPrefs(context).edit().putString(KEY_CUSTOM_QA, fileVal).apply();
            return fileVal;
        }
        return "[]";
    }

    // ===== Step 19 & 21: Persistent Multi-Turn Conversation Memory (Context Window) =====

    /**
     * Saves a conversation turn (user or model) in a persistent JSON array in SharedPreferences and /memory/.
     * Retains the last 5 conversation turns to append to Gemini API prompt context.
     */
    public static synchronized void saveConversationTurn(Context context, String role, String text) {
        if (context == null || text == null || text.trim().isEmpty()) return;
        try {
            String existing = getPrefs(context).getString(KEY_CONVERSATION_HISTORY, "[]");
            JSONArray array = new JSONArray(existing);
            JSONObject turn = new JSONObject();
            turn.put("role", role);
            turn.put("text", text.trim());
            turn.put("timestamp", System.currentTimeMillis());
            array.put(turn);
            while (array.length() > MAX_SAVED_TURNS) {
                array.remove(0);
            }
            String arrayStr = array.toString();
            getPrefs(context).edit().putString(KEY_CONVERSATION_HISTORY, arrayStr).apply();
            File convFile = new File(getMemoryDir(context), "conversation_history.json");
            writeStringToFile(convFile, arrayStr);
        } catch (Exception e) {
            // Silently ignore or reset on JSON parse failure
        }
    }

    /**
     * Retrieves the stored conversation turns as a JSONArray.
     */
    public static synchronized JSONArray getConversationTurns(Context context) {
        if (context == null) return new JSONArray();
        try {
            String existing = getPrefs(context).getString(KEY_CONVERSATION_HISTORY, "[]");
            return new JSONArray(existing);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    /**
     * Formats the last 5 conversation turns into a prompt context string for Gemini.
     */
    public static synchronized String getConversationContextPrompt(Context context) {
        if (context == null) return "";
        try {
            JSONArray array = getConversationTurns(context);
            if (array.length() == 0) return "";
            StringBuilder sb = new StringBuilder("\n[Recent Conversation Memory]:\n");
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String role = obj.optString("role", "user");
                String text = obj.optString("text", "");
                sb.append("- ").append(role.toUpperCase()).append(": ").append(text).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Clears persistent conversation turns.
     */
    public static synchronized void clearConversationHistory(Context context) {
        if (context == null) return;
        getPrefs(context).edit().remove(KEY_CONVERSATION_HISTORY).apply();
        File convFile = new File(getMemoryDir(context), "conversation_history.json");
        try { convFile.delete(); } catch (Exception ignored) {}
    }

    // Reset Vault
    public static void clearAll(Context context) {
        if (context == null) return;
        getPrefs(context).edit().clear().apply();
        try {
            new File(getMemoryDir(context), "custom_qa.json").delete();
            new File(getMemoryDir(context), "conversation_history.json").delete();
        } catch (Exception ignored) {}
    }

    // Orb Eye Color
    public static String getEyeColor(Context context) {
        if (context == null) return "neon-blue";
        return getPrefs(context).getString("orb_eye_color", "neon-blue");
    }

    public static void setEyeColor(Context context, String color) {
        if (context == null || color == null) return;
        getPrefs(context).edit().putString("orb_eye_color", color).apply();
    }
}

