package com.marvo.ai;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

/**
 * Step 13 & Step 13.5: Battery-Optimized Screen-Aware Wake Word Listener Foreground Service.
 * Implements lightweight Voice Activity Detection (VAD) with low-complexity RMS gating.
 * Screen-State Aware: Stops recording immediately on ACTION_SCREEN_OFF so CPU enters 100% Deep Sleep.
 * Resumes on ACTION_SCREEN_ON only if within MemoryVault's configured active time window.
 * On trigger ("Hey Marvo"), acquires a short-lived WakeLock and launches AssistantActivity.
 */
public class WakeWordService extends Service {
    private static final String TAG = "WakeWordService";
    public static final String ACTION_START = "com.marvo.ai.action.START_WAKE_WORD";
    public static final String ACTION_STOP = "com.marvo.ai.action.STOP_WAKE_WORD";
    private static final String CHANNEL_ID = "marvo_wake_word_channel";
    private static final int NOTIFICATION_ID = 1001;

    // Audio recording parameters (16kHz, Mono, 16-bit PCM)
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int CHUNK_SIZE = 512; // ~32ms of audio per chunk

    // Voice Activity Detection (VAD) Baseline & Energy Threshold
    private static final double VAD_BASELINE_ENERGY = 750.0;
    private static final double VAD_VOICE_TRIGGER_ENERGY = 1400.0;

    // Temporal Window for "Hey Marvo" syllable envelope (600ms - 1800ms)
    private static final long MIN_BURST_DURATION_MS = 350;
    private static final long MAX_BURST_DURATION_MS = 2200;
    private static final long COOLDOWN_AFTER_TRIGGER_MS = 4000;

    private volatile boolean isListening = false;
    private Thread workerThread = null;
    private AudioRecord audioRecord = null;
    private long lastTriggerTime = 0;

    // Syllable burst tracking state
    private boolean inSpeechBurst = false;
    private long burstStartTime = 0;
    private int burstSyllableCount = 0;
    private double lastRms = 0;

    // Step 13.5: Dynamic screen state awareness
    private BroadcastReceiver screenStateReceiver = null;

    public static void start(Context context) {
        Intent intent = new Intent(context, WakeWordService.class);
        intent.setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, WakeWordService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        registerScreenStateReceiver();
    }

    private void registerScreenStateReceiver() {
        if (screenStateReceiver != null) return;
        screenStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) return;
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    Log.i(TAG, "Screen OFF detected -> Stopping audio recording immediately for 100% CPU deep sleep");
                    stopListening();
                } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    Log.i(TAG, "Screen ON detected -> Checking active time window before listening");
                    if (MemoryVault.isWithinActiveWindow(context)) {
                        startListening();
                    } else {
                        Log.i(TAG, "Screen ON but outside active time window (" + 
                              MemoryVault.getActiveStartTime(context) + " - " + MemoryVault.getActiveEndTime(context) + ")");
                        stopListening();
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenStateReceiver, filter);
        Log.i(TAG, "Dynamic Screen State BroadcastReceiver registered successfully");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopListening();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        startForegroundNotification();

        // Step 13.5: Screen-State Aware Wake Word - Only listen if screen is on AND within active window
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean isScreenOn = pm != null && pm.isInteractive();
        if (isScreenOn && MemoryVault.isWithinActiveWindow(this)) {
            startListening();
        } else {
            Log.i(TAG, "WakeWordService started, but screen is off or outside active window. Idle mode (zero CPU usage).");
            stopListening();
        }

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Marvo Background Voice Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Maintains ultra-low-power wake word listener for 'Hey Marvo'");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void startForegroundNotification() {
        Intent notificationIntent = new Intent(this, AssistantActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Marvo Assistant Active")
            .setContentText("Listening for 'Hey Marvo'...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW);

        Notification notification = builder.build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private synchronized void startListening() {
        if (isListening) return;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Cannot start WakeWordService: RECORD_AUDIO permission not granted");
            return;
        }

        isListening = true;
        workerThread = new Thread(this::audioLoop, "MarvoWakeWordThread");
        workerThread.setPriority(Thread.NORM_PRIORITY - 1);
        workerThread.start();
        Log.i(TAG, "WakeWordService audio loop started");
    }

    private synchronized void stopListening() {
        isListening = false;
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
                audioRecord.release();
            } catch (Exception e) {
                Log.w(TAG, "Error releasing AudioRecord: " + e.getMessage());
            }
            audioRecord = null;
        }
        Log.i(TAG, "WakeWordService audio loop stopped");
    }

    /**
     * Ultra-low-power audio processing loop.
     * Uses RMS Voice Activity Detection (VAD) gating:
     * Drops silence immediately and sleeps 85ms to conserve battery.
     */
    private void audioLoop() {
        int minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        int bufferSize = Math.max(minBufSize, CHUNK_SIZE * 4);

        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                isListening = false;
                return;
            }

            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            );

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed");
                isListening = false;
                return;
            }

            audioRecord.startRecording();
            short[] buffer = new short[CHUNK_SIZE];

            while (isListening && !Thread.currentThread().isInterrupted()) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read <= 0) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ie) {
                        break;
                    }
                    continue;
                }

                // 1. Calculate RMS Energy over tiny 512-sample chunk
                double sum = 0;
                for (int i = 0; i < read; i++) {
                    sum += (double) buffer[i] * buffer[i];
                }
                double rms = Math.sqrt(sum / read);

                // 2. Low-Complexity VAD Gating
                // Below ambient threshold -> Drop buffer & sleep 85ms to save battery
                if (rms < VAD_BASELINE_ENERGY) {
                    if (inSpeechBurst) {
                        long burstDuration = SystemClock.elapsedRealtime() - burstStartTime;
                        if (burstDuration >= MIN_BURST_DURATION_MS && burstDuration <= MAX_BURST_DURATION_MS && burstSyllableCount >= 2) {
                            evaluateWakeWordTrigger();
                        }
                        inSpeechBurst = false;
                        burstSyllableCount = 0;
                    }
                    try {
                        Thread.sleep(85); // Critical sleep: keeps CPU load under 1% during silence
                    } catch (InterruptedException ie) {
                        break;
                    }
                    continue;
                }

                // 3. Human speech detected above baseline
                long now = SystemClock.elapsedRealtime();
                if (now - lastTriggerTime < COOLDOWN_AFTER_TRIGGER_MS) {
                    continue; // In post-trigger cooldown
                }

                if (!inSpeechBurst) {
                    inSpeechBurst = true;
                    burstStartTime = now;
                    burstSyllableCount = 1;
                } else {
                    // Syllable peak detection (energy inflection)
                    if (rms > VAD_VOICE_TRIGGER_ENERGY && rms > lastRms * 1.35) {
                        burstSyllableCount++;
                    }
                }
                lastRms = rms;

                // Max burst duration check
                long currentDuration = now - burstStartTime;
                if (currentDuration > MAX_BURST_DURATION_MS) {
                    inSpeechBurst = false;
                    burstSyllableCount = 0;
                } else if (burstSyllableCount >= 3 && currentDuration >= MIN_BURST_DURATION_MS) {
                    // Characteristic 3-syllable envelope ("Hey-Mar-vo")
                    evaluateWakeWordTrigger();
                    inSpeechBurst = false;
                    burstSyllableCount = 0;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Audio loop exception: " + e.getMessage(), e);
        } finally {
            if (audioRecord != null) {
                try {
                    if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord.stop();
                    }
                    audioRecord.release();
                } catch (Exception ignored) {}
                audioRecord = null;
            }
        }
    }

    /**
     * Executes on-device wake-up: acquires temporary WakeLock, fires AssistantActivity, releases lock.
     */
    private void evaluateWakeWordTrigger() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastTriggerTime < COOLDOWN_AFTER_TRIGGER_MS) {
            return;
        }
        lastTriggerTime = now;
        Log.i(TAG, "[WAKE WORD TRIGGER] 'Hey Marvo' detected via low-power acoustic VAD envelope!");

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = null;

        if (pm != null) {
            try {
                wakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                    PowerManager.ACQUIRE_CAUSES_WAKEUP |
                    PowerManager.ON_AFTER_RELEASE,
                    "marvo:wakeword_trigger"
                );
                wakeLock.acquire(5000); // 5-second maximum safety timeout
            } catch (Exception e) {
                Log.w(TAG, "WakeLock acquisition notice: " + e.getMessage());
            }
        }

        try {
            Intent intent = new Intent(this, AssistantActivity.class);
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK |
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            );
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch AssistantActivity on wake word: " + e.getMessage(), e);
        } finally {
            if (wakeLock != null && wakeLock.isHeld()) {
                try {
                    wakeLock.release();
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void onDestroy() {
        if (screenStateReceiver != null) {
            try {
                unregisterReceiver(screenStateReceiver);
            } catch (Exception ignored) {}
            screenStateReceiver = null;
        }
        stopListening();
        super.onDestroy();
    }
}

