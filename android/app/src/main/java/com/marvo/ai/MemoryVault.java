package com.marvo.ai;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Step 9 - Part 10: Local User Profile Vault (MemoryVault).
 * Securely caches personal parameters, family aliases, and contextual memory locally on-device.
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

    public static final String DEFAULT_MOM_NUMBER = "+919437000002";
    public static final String DEFAULT_DAD_NUMBER = "+919437000001";
    public static final int DEFAULT_VOICE_PROFILE = 2; // Profile 2: Male Hindi

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

    // Reset Vault
    public static void clearAll(Context context) {
        if (context == null) return;
        getPrefs(context).edit().clear().apply();
    }
}

