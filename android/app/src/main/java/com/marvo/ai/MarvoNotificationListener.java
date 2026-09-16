package com.marvo.ai;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Traffic Police V3/V4: Smart Notification Summarizer Service.
 * Implements passive, zero-battery-drain notification monitoring.
 * Stores recent messages in a memory ring buffer and sleeps until invoked.
 */
public class MarvoNotificationListener extends NotificationListenerService {
    private static final String TAG = "MarvoNotifListener";
    private static final int MAX_BUFFER_SIZE = 25;

    public static class NotificationRecord {
        public final String packageName;
        public final String appName;
        public final String title;
        public final String text;
        public final long timestamp;

        public NotificationRecord(String packageName, String appName, String title, String text, long timestamp) {
            this.packageName = packageName;
            this.appName = appName;
            this.title = title;
            this.text = text;
            this.timestamp = timestamp;
        }
    }

    private static final ConcurrentLinkedDeque<NotificationRecord> notificationBuffer = new ConcurrentLinkedDeque<>();
    private static volatile boolean isServiceConnected = false;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        isServiceConnected = true;
        Log.d(TAG, "NotificationListener connected - entering passive deep sleep buffer mode");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        isServiceConnected = false;
        Log.d(TAG, "NotificationListener disconnected");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        Notification notification = sbn.getNotification();
        if (notification == null) return;

        // Ignore ongoing foreground services, media playback, or non-cancelable persistent alerts
        if ((notification.flags & Notification.FLAG_ONGOING_EVENT) != 0 ||
            (notification.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0) {
            return;
        }

        Bundle extras = notification.extras;
        if (extras == null) return;

        CharSequence titleCs = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence textCs = extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence subTextCs = extras.getCharSequence(Notification.EXTRA_SUB_TEXT);

        String title = titleCs != null ? titleCs.toString().trim() : "";
        String text = textCs != null ? textCs.toString().trim() : "";

        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(text)) {
            return;
        }

        String pkg = sbn.getPackageName();
        if ("android".equals(pkg) || "com.android.systemui".equals(pkg)) {
            return;
        }

        String appName = getFriendlyAppName(pkg);

        NotificationRecord record = new NotificationRecord(pkg, appName, title, text, System.currentTimeMillis());

        notificationBuffer.addFirst(record);
        while (notificationBuffer.size() > MAX_BUFFER_SIZE) {
            notificationBuffer.pollLast();
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Passive; no-op to conserve battery
    }

    private String getFriendlyAppName(String pkg) {
        if (pkg == null) return "Unknown";
        if (pkg.contains("whatsapp")) return "WhatsApp";
        if (pkg.contains("messaging") || pkg.contains("mms")) return "Messages (SMS)";
        if (pkg.contains("telegram")) return "Telegram";
        if (pkg.contains("gm") || pkg.contains("gmail")) return "Gmail";
        if (pkg.contains("instagram")) return "Instagram";
        if (pkg.contains("twitter") || pkg.contains("x.android")) return "X";
        if (pkg.contains("facebook")) return "Facebook";
        if (pkg.contains("slack")) return "Slack";
        if (pkg.contains("linkedin")) return "LinkedIn";
        return pkg;
    }

    /**
     * Checks whether the user has granted Notification Access in Android Settings.
     */
    public static boolean isNotificationAccessGranted(Context context) {
        if (context == null) return false;
        String pkgName = context.getPackageName();
        String flat = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            String[] names = flat.split(":");
            for (String name : names) {
                ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null && TextUtils.equals(pkgName, cn.getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Retrieves the most recent notifications captured in the passive buffer.
     */
    public static List<NotificationRecord> getRecentNotifications(int limit) {
        List<NotificationRecord> list = new ArrayList<>();
        int count = 0;
        for (NotificationRecord nr : notificationBuffer) {
            list.add(nr);
            count++;
            if (count >= limit) break;
        }
        return list;
    }

    /**
     * Returns a formatted text digest of recent notifications for AI or local summarization.
     */
    public static String getFormattedRecentSummary(int limit) {
        List<NotificationRecord> list = getRecentNotifications(limit);
        if (list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            NotificationRecord nr = list.get(i);
            sb.append(i + 1).append(". [").append(nr.appName).append("] ");
            if (!TextUtils.isEmpty(nr.title)) {
                sb.append("From: ").append(nr.title).append(" - ");
            }
            sb.append(nr.text).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * Clears notification buffer to free memory.
     */
    public static void clearBuffer() {
        notificationBuffer.clear();
    }
}
