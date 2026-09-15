package com.marvo.ai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import org.json.JSONObject;

/**
 * Sticky Foreground Service for Offline AI Brain Download.
 * Ensures the Android OS does not kill or throttle the 2.2GB model download
 * when the app is minimized or the screen turns off.
 * Provides a live system notification with real-time percentage and MB progress,
 * and Pause/Resume/Cancel notification actions.
 */
public class ModelDownloadService extends Service {
    private static final String TAG = "MarvoDownload";

    public static final String ACTION_START_DOWNLOAD = "com.marvo.ai.action.START_DOWNLOAD";
    public static final String ACTION_PAUSE = "com.marvo.ai.action.PAUSE_DOWNLOAD";
    public static final String ACTION_CANCEL = "com.marvo.ai.action.CANCEL_DOWNLOAD";
    public static final String EXTRA_ALLOW_METERED = "extra_allow_metered";

    public static final String CHANNEL_ID = "marvo_download_channel";
    public static final int NOTIFICATION_ID = 1001;

    private NotificationManager notificationManager;
    private PowerManager.WakeLock wakeLock;
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private boolean isPolling = false;

    public static final String EXTRA_MODEL_TYPE = "extra_model_type";
    private String currentModelType = OfflineBrainDownloader.TYPE_LLM;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isPolling) return;
            try {
                JSONObject progress = OfflineBrainDownloader.getInstance().getDownloadProgress(ModelDownloadService.this, currentModelType);
                String status = progress.optString("status", "idle");
                int pct = progress.optInt("progress", 0);
                long downloaded = progress.optLong("downloadedBytes", 0);
                long total = progress.optLong("totalBytes", 2200L * 1024L * 1024L);
                boolean isReady = progress.optBoolean("isReady", false);

                if (isReady || "completed".equalsIgnoreCase(status)) {
                    showCompletedNotification();
                    stopPolling();
                    releaseWakeLock();
                    stopForeground(false);
                    stopSelf();
                    return;
                } else if ("downloading".equalsIgnoreCase(status)) {
                    updateProgressNotification(pct, downloaded, total);
                } else if ("paused".equalsIgnoreCase(status) || "paused_wifi".equalsIgnoreCase(status)) {
                    showPausedNotification();
                    stopPolling();
                    releaseWakeLock();
                    stopForeground(false);
                    return;
                } else if ("failed".equalsIgnoreCase(status)) {
                    showFailedNotification();
                    stopPolling();
                    releaseWakeLock();
                    stopForeground(false);
                    stopSelf();
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in notification poll loop: " + e.getMessage());
            }

            if (isPolling) {
                pollHandler.postDelayed(this, 1000); // 1-second update interval for real-time notification
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
        Log.d(TAG, "ModelDownloadService created.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            // Check if download was actively in progress before process kill
            JSONObject progress = OfflineBrainDownloader.getInstance().getDownloadProgress(this, currentModelType);
            String status = progress.optString("status", "idle");
            if ("downloading".equalsIgnoreCase(status)) {
                acquireWakeLock();
                startForegroundNotification();
                OfflineBrainDownloader.getInstance().startDownload(this, currentModelType, true);
                startPolling();
                return START_STICKY;
            }
            return START_STICKY;
        }

        String action = intent.getAction();
        Log.d(TAG, "ModelDownloadService received action: " + action);

        if (intent.hasExtra(EXTRA_MODEL_TYPE)) {
            currentModelType = intent.getStringExtra(EXTRA_MODEL_TYPE);
            if (currentModelType == null) currentModelType = OfflineBrainDownloader.TYPE_LLM;
        }

        if (ACTION_START_DOWNLOAD.equals(action)) {
            boolean allowMetered = intent.getBooleanExtra(EXTRA_ALLOW_METERED, true);
            acquireWakeLock();
            startForegroundNotification();
            OfflineBrainDownloader.getInstance().startDownload(this, currentModelType, allowMetered);
            startPolling();
            return START_STICKY;
        } else if (ACTION_PAUSE.equals(action)) {
            OfflineBrainDownloader.getInstance().pauseDownload(this, currentModelType);
            releaseWakeLock();
            showPausedNotification();
            stopPolling();
            stopForeground(false);
            return START_NOT_STICKY;
        } else if (ACTION_CANCEL.equals(action)) {
            OfflineBrainDownloader.getInstance().cancelDownload(this, currentModelType);
            releaseWakeLock();
            stopPolling();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        return START_STICKY;
    }

    private void acquireWakeLock() {
        try {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Marvo:OfflineBrainDownloadWakeLock");
                    wakeLock.setReferenceCounted(false);
                }
            }
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(2 * 60 * 60 * 1000L); // 2 hours max
                Log.d(TAG, "Acquired PARTIAL_WAKE_LOCK for background download.");
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not acquire wake lock: " + e.getMessage());
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                Log.d(TAG, "Released PARTIAL_WAKE_LOCK.");
            }
        } catch (Exception e) {
            Log.w(TAG, "Error releasing wake lock: " + e.getMessage());
        }
    }

    private void startForegroundNotification() {
        Notification notification = buildProgressNotification(0, 0, 2200L * 1024L * 1024L, "Connecting to mirror...");
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                } else {
                    startForeground(NOTIFICATION_ID, notification);
                }
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting foreground service: " + e.getMessage(), e);
        }
    }

    private void startPolling() {
        if (!isPolling) {
            isPolling = true;
            pollHandler.post(pollRunnable);
        }
    }

    private void stopPolling() {
        isPolling = false;
        pollHandler.removeCallbacks(pollRunnable);
    }

    private Notification buildProgressNotification(int progress, long downloaded, long total, String statusText) {
        Intent openIntent = new Intent(this, NativeSettingsActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0
        );

        // Pause Action Intent
        Intent pauseIntent = new Intent(this, ModelDownloadService.class);
        pauseIntent.setAction(ACTION_PAUSE);
        PendingIntent pendingPause = PendingIntent.getService(
            this, 1, pauseIntent,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT
        );

        // Cancel Action Intent
        Intent cancelIntent = new Intent(this, ModelDownloadService.class);
        cancelIntent.setAction(ACTION_CANCEL);
        PendingIntent pendingCancel = PendingIntent.getService(
            this, 2, cancelIntent,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT
        );

        long dlMb = downloaded / (1024 * 1024);
        long totMb = total > 0 ? (total / (1024 * 1024)) : 2200;
        String contentText = dlMb + " MB / " + totMb + " MB (" + progress + "%)";
        if (statusText != null && !statusText.isEmpty()) {
            contentText = statusText + " — " + contentText;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading Marvo Offline AI Brain")
            .setContentText(contentText)
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pendingPause)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", pendingCancel);

        return builder.build();
    }

    private void updateProgressNotification(int progress, long downloaded, long total) {
        if (notificationManager == null) return;
        Notification notification = buildProgressNotification(progress, downloaded, total, "");
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    private void showCompletedNotification() {
        if (notificationManager == null) return;
        Intent openIntent = new Intent(this, NativeSettingsActivity.class);
        PendingIntent pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Marvo Offline Brain Ready")
            .setContentText("Model Ready (Offline Active). 2.2GB offline LLM verified.")
            .setContentIntent(pendingOpen)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build();

        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    private void showPausedNotification() {
        if (notificationManager == null) return;
        Intent openIntent = new Intent(this, NativeSettingsActivity.class);
        PendingIntent pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0
        );

        Intent resumeIntent = new Intent(this, ModelDownloadService.class);
        resumeIntent.setAction(ACTION_START_DOWNLOAD);
        resumeIntent.putExtra(EXTRA_ALLOW_METERED, true);
        PendingIntent pendingResume = PendingIntent.getService(
            this, 3, resumeIntent,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT
        );

        JSONObject progress = OfflineBrainDownloader.getInstance().getDownloadProgress(this);
        long downloaded = progress.optLong("downloadedBytes", 0);
        long total = progress.optLong("totalBytes", 2200L * 1024L * 1024L);
        int pct = progress.optInt("progress", 0);
        long dlMb = downloaded / (1024 * 1024);
        long totMb = total > 0 ? (total / (1024 * 1024)) : 2200;

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Offline Brain Download Paused (" + pct + "%)")
            .setContentText(dlMb + " MB / " + totMb + " MB downloaded. Tap Resume to continue.")
            .setContentIntent(pendingOpen)
            .setOngoing(false)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_media_play, "Resume", pendingResume)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();

        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    private void showFailedNotification() {
        if (notificationManager == null) return;
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Offline Brain Download Interrupted")
            .setContentText("Network error occurred. Tap settings to retry.")
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build();

        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Marvo Offline AI Brain Downloader",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows live download percentage and progress of the heavy offline LLM model");
            channel.setShowBadge(false);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseWakeLock();
        stopPolling();
        Log.d(TAG, "ModelDownloadService destroyed.");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
