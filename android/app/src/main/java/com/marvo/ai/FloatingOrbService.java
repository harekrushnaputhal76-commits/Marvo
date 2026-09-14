package com.marvo.ai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

/**
 * Step 15 - Part 1: Persistent Floating Overlay Service.
 * Draws a small draggable Marvo icon on top of all other apps using TYPE_APPLICATION_OVERLAY.
 * Tapping the icon brings AssistantActivity back to the foreground.
 * Started when an external app is launched via OfflineIntentRouter.
 */
public class FloatingOrbService extends Service {
    private static final String TAG = "FloatingOrbService";
    private static final String CHANNEL_ID = "marvo_floating_orb_channel";
    private static final int NOTIFICATION_ID = 2002;

    public static final String ACTION_STOP = "com.marvo.ai.FloatingOrbService.STOP";

    private WindowManager windowManager;
    private ImageView floatingIcon;
    private WindowManager.LayoutParams layoutParams;
    private boolean isViewAdded = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "FloatingOrbService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (intent != null && ACTION_STOP.equals(intent.getAction())) {
                removeFloatingView();
                try { stopForeground(true); } catch (Exception ignored) {}
                stopSelf();
                return START_NOT_STICKY;
            }

            // Check overlay permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Log.w(TAG, "SYSTEM_ALERT_WINDOW permission not granted. Cannot draw overlay.");
                stopSelf();
                return START_NOT_STICKY;
            }

            startForegroundNotification();
            createFloatingView();
        } catch (Exception e) {
            Log.e(TAG, "FloatingOrbService onStartCommand intercepted safely: " + e.getMessage());
        }

        return START_STICKY;
    }

    private void startForegroundNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Marvo Floating Overlay",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Persistent floating Marvo icon overlay");
            channel.setShowBadge(false);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }

        Intent stopIntent = new Intent(this, FloatingOrbService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        Notification notification = builder
            .setContentTitle("Marvo Active")
            .setContentText("Tap the floating icon to summon Marvo")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", stopPendingIntent)
            .build();

        try {
            startForeground(NOTIFICATION_ID, notification);
        } catch (Exception e) {
            Log.w(TAG, "startForeground caught safely: " + e.getMessage());
        }
    }

    private void createFloatingView() {
        if (isViewAdded) return;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            Log.e(TAG, "WindowManager is null, cannot create floating view");
            stopSelf();
            return;
        }

        floatingIcon = new ImageView(this);
        floatingIcon.setImageResource(R.mipmap.ic_launcher);
        floatingIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        int iconSizePx = (int) (56 * getResources().getDisplayMetrics().density);

        int overlayType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            overlayType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            overlayType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        layoutParams = new WindowManager.LayoutParams(
            iconSizePx,
            iconSizePx,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );

        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = 20;
        layoutParams.y = 200;

        // Draggable touch listener
        floatingIcon.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private long touchStartTime;
            private static final int CLICK_THRESHOLD_MS = 200;
            private static final int MOVE_THRESHOLD_PX = 10;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = layoutParams.x;
                        initialY = layoutParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        touchStartTime = System.currentTimeMillis();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        layoutParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        layoutParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        try {
                            windowManager.updateViewLayout(floatingIcon, layoutParams);
                        } catch (Exception ignored) {}
                        return true;

                    case MotionEvent.ACTION_UP:
                        long duration = System.currentTimeMillis() - touchStartTime;
                        float distX = Math.abs(event.getRawX() - initialTouchX);
                        float distY = Math.abs(event.getRawY() - initialTouchY);

                        if (duration < CLICK_THRESHOLD_MS && distX < MOVE_THRESHOLD_PX && distY < MOVE_THRESHOLD_PX) {
                            // This is a tap — bring AssistantActivity to foreground
                            onFloatingIconTapped();
                        }
                        return true;
                }
                return false;
            }
        });

        try {
            windowManager.addView(floatingIcon, layoutParams);
            isViewAdded = true;
            Log.d(TAG, "Floating Marvo icon added to screen");
        } catch (Exception e) {
            Log.e(TAG, "Failed to add floating view: " + e.getMessage(), e);
            stopSelf();
        }
    }

    /**
     * Called when the user taps the floating icon.
     * Brings AssistantActivity back to the foreground and dismisses the overlay.
     */
    private void onFloatingIconTapped() {
        Log.d(TAG, "Floating icon tapped — bringing AssistantActivity to foreground");
        try {
            Intent launchIntent = new Intent(this, AssistantActivity.class);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(launchIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error launching AssistantActivity from floating icon: " + e.getMessage(), e);
        }

        // Remove overlay after bringing assistant back
        removeFloatingView();
        try { stopForeground(true); } catch (Exception ignored) {}
        stopSelf();
    }

    private void removeFloatingView() {
        if (isViewAdded && floatingIcon != null && windowManager != null) {
            try {
                windowManager.removeView(floatingIcon);
            } catch (Exception e) {
                Log.w(TAG, "Error removing floating view: " + e.getMessage());
            }
            isViewAdded = false;
        }
    }

    @Override
    public void onDestroy() {
        removeFloatingView();
        super.onDestroy();
        Log.d(TAG, "FloatingOrbService destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

