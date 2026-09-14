package com.marvo.ai;

import android.util.Log;

/**
 * Step 9 - Part 10: Conversational Context Stack.
 * Tracks multi-turn conversational memory and pending unfulfilled intents.
 * Enables seamless resolution when missing data (location, duration, contact name) is provided in follow-up turns.
 */
public class ConversationContext {
    private static final String TAG = "ConversationContext";
    private static final long TTL_MILLIS = 60000; // 60 seconds time-to-live

    private String pendingIntent = null;
    private String pendingData = null;
    private long timestamp = 0;

    public synchronized void setPendingIntent(String intent, String data) {
        this.pendingIntent = intent;
        this.pendingData = data;
        this.timestamp = System.currentTimeMillis();
        Log.d(TAG, "Context updated: pendingIntent=" + intent + ", data=" + data);
    }

    public synchronized boolean hasPendingIntent() {
        if (pendingIntent == null) return false;
        if (System.currentTimeMillis() - timestamp > TTL_MILLIS) {
            Log.d(TAG, "Pending intent expired: " + pendingIntent);
            clear();
            return false;
        }
        return true;
    }

    public synchronized String getPendingIntent() {
        if (!hasPendingIntent()) return null;
        return pendingIntent;
    }

    public synchronized String getPendingData() {
        if (!hasPendingIntent()) return null;
        return pendingData;
    }

    public synchronized void clear() {
        this.pendingIntent = null;
        this.pendingData = null;
        this.timestamp = 0;
        Log.d(TAG, "Context cleared");
    }
}
