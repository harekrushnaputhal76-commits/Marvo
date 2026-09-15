package com.marvo.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Step 30: Long-Term Personal Memory Manager.
 * Persistently stores permanent user facts in local marvo_memory.json.
 * Automatically extracts facts from conversation and invisibly injects them into system prompt.
 */
public class MemoryManager {
    private static final String TAG = "MemoryManager";
    private static final String PREF_NAME = "marvo_long_term_memory";
    private static final String KEY_MEMORY_ENABLED = "memory_enabled";
    private static final String MEMORY_FILE_NAME = "marvo_memory.json";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isMemoryEnabled(Context context) {
        if (context == null) return true;
        return getPrefs(context).getBoolean(KEY_MEMORY_ENABLED, true);
    }

    public static void setMemoryEnabled(Context context, boolean enabled) {
        if (context == null) return;
        getPrefs(context).edit().putBoolean(KEY_MEMORY_ENABLED, enabled).apply();
    }

    private static File getMemoryFile(Context context) {
        File dir = new File(context.getFilesDir(), "memory");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, MEMORY_FILE_NAME);
    }

    public static synchronized List<JSONObject> getAllFacts(Context context) {
        List<JSONObject> list = new ArrayList<>();
        if (context == null) return list;

        File file = getMemoryFile(context);
        if (!file.exists()) return list;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONObject root = new JSONObject(sb.toString());
            JSONArray facts = root.optJSONArray("facts");
            if (facts != null) {
                for (int i = 0; i < facts.length(); i++) {
                    list.add(facts.getJSONObject(i));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error reading marvo_memory.json: " + e.getMessage());
        }
        return list;
    }

    public static synchronized boolean saveFact(Context context, String fact, String category) {
        if (context == null || fact == null || fact.trim().isEmpty()) return false;
        String cleanFact = fact.trim();
        if (category == null || category.trim().isEmpty()) category = "general";

        try {
            File file = getMemoryFile(context);
            JSONObject root;
            JSONArray facts;
            if (file.exists()) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }
                root = new JSONObject(sb.length() > 0 ? sb.toString() : "{}");
                facts = root.optJSONArray("facts");
                if (facts == null) facts = new JSONArray();
            } else {
                root = new JSONObject();
                facts = new JSONArray();
                root.put("version", 1);
            }

            // Deduplicate facts
            String lowerFact = cleanFact.toLowerCase();
            for (int i = 0; i < facts.length(); i++) {
                JSONObject existing = facts.getJSONObject(i);
                if (existing.optString("fact", "").trim().toLowerCase().equals(lowerFact)) {
                    Log.d(TAG, "Fact already retained: " + cleanFact);
                    return true;
                }
            }

            JSONObject newEntry = new JSONObject();
            newEntry.put("id", "mem_" + System.currentTimeMillis() + "_" + (facts.length() + 1));
            newEntry.put("fact", cleanFact);
            newEntry.put("category", category);
            newEntry.put("timestamp", System.currentTimeMillis());

            facts.put(newEntry);
            root.put("facts", facts);
            root.put("updated_at", System.currentTimeMillis());

            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
                writer.write(root.toString(2));
            }
            Log.i(TAG, "Saved permanent memory fact: " + cleanFact);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed saving memory fact: " + e.getMessage(), e);
            return false;
        }
    }

    public static synchronized boolean deleteFact(Context context, String id) {
        if (context == null || id == null) return false;
        try {
            File file = getMemoryFile(context);
            if (!file.exists()) return false;

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }
            JSONObject root = new JSONObject(sb.toString());
            JSONArray facts = root.optJSONArray("facts");
            if (facts == null) return false;

            JSONArray updated = new JSONArray();
            boolean found = false;
            for (int i = 0; i < facts.length(); i++) {
                JSONObject obj = facts.getJSONObject(i);
                if (!obj.optString("id").equals(id)) {
                    updated.put(obj);
                } else {
                    found = true;
                }
            }
            root.put("facts", updated);
            root.put("updated_at", System.currentTimeMillis());

            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
                writer.write(root.toString(2));
            }
            return found;
        } catch (Exception e) {
            Log.e(TAG, "Failed deleting fact: " + e.getMessage());
            return false;
        }
    }

    public static synchronized void clearAllFacts(Context context) {
        if (context == null) return;
        try {
            File file = getMemoryFile(context);
            if (file.exists()) {
                file.delete();
            }
            Log.i(TAG, "Cleared all permanent memory facts");
        } catch (Exception e) {
            Log.e(TAG, "Error clearing memory facts: " + e.getMessage());
        }
    }

    /**
     * Extracts persistent personal facts from incoming user queries.
     * Heuristics for preferences, identity, location, relationships, and explicit statements.
     */
    public static void extractAndSaveFacts(Context context, String query) {
        if (context == null || query == null || !isMemoryEnabled(context)) return;
        String trimmed = query.trim();

        Pattern[] factPatterns = new Pattern[]{
            // Explicit remember directives
            Pattern.compile("^(?:please\\s+)?(?:always\\s+)?remember\\s+(?:that\\s+|this\\s*:?\\s*)?(.+)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(?:yaad\\s+rakhna|dhyaan\\s+rakhna)\\s+(?:ki\\s+)?(.+)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(?:note\\s+that|keep\\s+in\\s+mind\\s+that)\\s+(.+)$", Pattern.CASE_INSENSITIVE),
            // Identity & location statements
            Pattern.compile("^(?:my\\s+(?:favorite|favourite|best|dog's|cat's|brother's|sister's|father's|mother's)\\s+[^is]+is\\s+)(.+)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(?:i\\s+(?:live|reside|stay|work|study)\\s+in\\s+)(.+)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^(?:i\\s+(?:love|like|prefer|hate|dislike|am\\s+allergic\\s+to)\\s+)(.+)$", Pattern.CASE_INSENSITIVE),
            // Hindi / Hinglish patterns
            Pattern.compile("^(?:mera\\s+favourite|meri\\s+favourite|mujhe)\\s+(.+?)(?:\\s+pasand\\s+hai|\\s+accha\\s+lagta\\s+hai)$", Pattern.CASE_INSENSITIVE)
        };

        for (Pattern p : factPatterns) {
            Matcher m = p.matcher(trimmed);
            if (m.find()) {
                String fact = m.group(1);
                if (fact != null && fact.trim().length() > 3) {
                    saveFact(context, "User stated: " + trimmed, "personal");
                    break;
                }
            }
        }
    }

    /**
     * Formats stored facts into an invisible System Prompt injection block.
     */
    public static String getInjectedMemoryPrompt(Context context) {
        if (context == null || !isMemoryEnabled(context)) return "";
        List<JSONObject> facts = getAllFacts(context);
        if (facts.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("[LONG_TERM_USER_MEMORY:\n");
        sb.append("The following permanent facts were established by the user in previous conversations. Inherently remember and respect them without narrating source mechanisms:\n");
        for (JSONObject fact : facts) {
            String f = fact.optString("fact", "").trim();
            if (!f.isEmpty()) {
                sb.append("- ").append(f).append("\n");
            }
        }
        sb.append("]\n");
        return sb.toString();
    }
}

