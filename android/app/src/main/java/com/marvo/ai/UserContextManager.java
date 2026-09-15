package com.marvo.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/**
 * Step 27: Central User Context, Real-Time Tools & Personalization Manager.
 * Manages user profile, Student Mode, dynamic real-time Weather and Time hooks,
 * and strict privacy clean-slate rules.
 */
public class UserContextManager {
    private static final String TAG = "UserContextManager";
    private static final String PREF_NAME = "marvo_user_context";

    public static final String KEY_STUDENT_MODE = "is_student_mode";
    public static final String KEY_WEATHER_CACHE = "cached_weather_str";
    public static final String KEY_WEATHER_TIMESTAMP = "cached_weather_timestamp";

    // User Profile Configuration
    public static final String USER_FULL_NAME = "Harekrushna Puthal";
    public static final String USER_CALL_NAMES = "Guddu, Gudu, Boss, Sir";
    public static final String USER_PREFERRED_TITLE = "Boss";
    public static final String USER_LOCATION = "Talakia, Oupada, Balasore, Odisha, India";
    public static final double USER_LATITUDE = 21.32;
    public static final double USER_LONGITUDE = 86.58;

    private static final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private static volatile String lastWeatherSummary = "28°C, Partly Cloudy, Calm in Talakia, Odisha";
    private static volatile long lastWeatherFetchTime = 0L;

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isStudentMode(Context context) {
        if (context == null) return false;
        return getPrefs(context).getBoolean(KEY_STUDENT_MODE, false);
    }

    public static void setStudentMode(Context context, boolean enabled) {
        if (context == null) return;
        getPrefs(context).edit().putBoolean(KEY_STUDENT_MODE, enabled).apply();
    }

    /**
     * Formats real-time local date and time in IST (Indian Standard Time).
     */
    public static String getRealtimeTimeContext() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, d MMMM yyyy, hh:mm a 'IST'", Locale.ENGLISH);
        return sdf.format(new Date());
    }

    /**
     * Returns real-time weather summary for Talakia, Oupada, Balasore, Odisha.
     * Uses in-memory cache, SharedPreferences, or fetches from Open-Meteo asynchronously.
     */
    public static String getRealtimeWeatherContext(Context context) {
        long now = System.currentTimeMillis();
        // Return cached weather if less than 60 minutes old
        if (now - lastWeatherFetchTime < 60 * 60 * 1000L && lastWeatherSummary != null) {
            return lastWeatherSummary;
        }

        if (context != null) {
            String saved = getPrefs(context).getString(KEY_WEATHER_CACHE, null);
            long savedTime = getPrefs(context).getLong(KEY_WEATHER_TIMESTAMP, 0L);
            if (saved != null && (now - savedTime < 60 * 60 * 1000L)) {
                lastWeatherSummary = saved;
                lastWeatherFetchTime = savedTime;
                return saved;
            }

            // Trigger background weather fetch if connected
            fetchWeatherAsync(context);
        }

        return lastWeatherSummary != null ? lastWeatherSummary : "28°C, Partly Cloudy in Talakia, Odisha";
    }

    private static void fetchWeatherAsync(final Context context) {
        backgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String urlStr = "https://api.open-meteo.com/v1/forecast?latitude=" + USER_LATITUDE
                            + "&longitude=" + USER_LONGITUDE + "&current_weather=true";
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);

                    if (conn.getResponseCode() == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        reader.close();

                        JSONObject json = new JSONObject(sb.toString());
                        if (json.has("current_weather")) {
                            JSONObject cw = json.getJSONObject("current_weather");
                            double temp = cw.optDouble("temperature", 28.0);
                            double wind = cw.optDouble("windspeed", 5.0);
                            int weatherCode = cw.optInt("weathercode", 1);
                            String cond = getWeatherConditionText(weatherCode);

                            String summary = temp + "°C, " + cond + ", Wind " + wind + " km/h in Talakia, Oupada, Balasore, Odisha";
                            lastWeatherSummary = summary;
                            lastWeatherFetchTime = System.currentTimeMillis();

                            if (context != null) {
                                getPrefs(context).edit()
                                    .putString(KEY_WEATHER_CACHE, summary)
                                    .putLong(KEY_WEATHER_TIMESTAMP, lastWeatherFetchTime)
                                    .apply();
                            }
                            Log.d(TAG, "Fetched real-time weather: " + summary);
                        }
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    Log.w(TAG, "Weather fetch exception: " + e.getMessage());
                }
            }
        });
    }

    private static String getWeatherConditionText(int code) {
        switch (code) {
            case 0: return "Clear Sky";
            case 1: return "Mainly Clear";
            case 2: return "Partly Cloudy";
            case 3: return "Overcast";
            case 45: case 48: return "Foggy";
            case 51: case 53: case 55: return "Drizzle";
            case 61: case 63: case 65: return "Rainy";
            case 80: case 81: case 82: return "Rain Showers";
            case 95: case 96: case 99: return "Thunderstorm";
            default: return "Partly Cloudy";
        }
    }

    /**
     * Step 27: Master System Prompt Generator.
     * Incorporates Student Mode, Strict Clean Slate, Real-Time Weather & Time Hooks.
     */
    public static String getMasterSystemPrompt(Context context, boolean isNewChat) {
        boolean studentMode = isStudentMode(context);

        if (studentMode) {
            return "You are an academic tutor. The user is a Class 12 Higher Secondary Science student (Physics, Chemistry, Mathematics, Biology) under the CHSE Odisha board. Only discuss studies, solve problems concisely, and refuse non-academic banter. Address the user respectfully as Sir. Local Time: " + getRealtimeTimeContext() + ".";
        }

        String time = getRealtimeTimeContext();
        String weather = getRealtimeWeatherContext(context);

        if (isNewChat) {
            // Strict Privacy Clean-Slate Rule: Zero unprompted personal assumptions
            return "You are Marvo, a highly intelligent, polite, and professional personal AI assistant. "
                 + "Provide highly precise, concise, and professional answers. No extra chatting, rambling, or nonsense. "
                 + "Address the user respectfully as 'Boss' or 'Sir'. "
                 + "CRITICAL PRIVACY RULE: Treat this session as a completely fresh, neutral conversation with zero assumptions about who the user is. Never bring up personal facts, user names, or personal locations unprompted. "
                 + "Only if the user explicitly asks about their identity or name, they are Harekrushna Puthal (Guddu, Boss). "
                 + "If asked about the current weather or time, answer directly using the known local context: Current Time: " + time + " | Local Weather: " + weather + ".";
        }

        return "You are Marvo, a highly intelligent, polite, and professional personal AI assistant. "
             + "Provide highly precise, concise, and professional answers. No extra chatting or nonsense. "
             + "Address the user respectfully as 'Boss' or 'Sir'. "
             + "User Profile: Master Harekrushna Puthal (Call names: Guddu, Gudu, Boss, Sir) located in Talakia, Oupada, Balasore, Odisha, India. "
             + "Real-time Environment Context: Local Time: " + time + " | Local Weather: " + weather + ". "
             + "PRIVACY & MISSING PROPERTY RULES: Never narrate source mechanisms or say 'Based on your location...'. State facts directly. If any fact or entity is missing from context, state that the info is missing; never fabricate or hallucinate.";
    }
}

