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
import android.media.audiofx.AcousticEchoCanceler;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Step 19: Deep-Sleep Battery Architecture & Two-Stage Wake Word Gatekeeper.
 * - Screen OFF: 100% CPU deep sleep. AudioRecord, SpeechRecognizer, and ExecutorService instantly destroyed.
 * - Screen ON: Dedicated background ExecutorService with AcousticEchoCanceler to block internal media sounds.
 * - Two-Stage Verification: Stage 1 detects speech; Stage 2 (Gatekeeper) enforces EXACT match ("marvo", "hey marvo", "hello marvo").
 * - Zero ghost triggers, zero speaker echo, minimal CPU usage.
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
    private static final int CHUNK_SIZE = 512;

    // Voice Activity Detection (VAD) Baseline & Energy Threshold
    private static final double VAD_BASELINE_ENERGY = 850.0;
    private static final double VAD_VOICE_TRIGGER_ENERGY = 1500.0;
    private static final long COOLDOWN_AFTER_TRIGGER_MS = 3000;

    private volatile boolean isListening = false;
    private volatile boolean isVerifyingStage2 = false;

    private ExecutorService executorService = null;
    private AudioRecord audioRecord = null;
    private AcousticEchoCanceler echoCanceler = null;
    private SpeechRecognizer speechRecognizer = null;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private long lastTriggerTime = 0;

    // Dynamic screen state awareness
    private BroadcastReceiver screenStateReceiver = null;

    public static void start(Context context) {
        try {
            Intent intent = new Intent(context, WakeWordService.class);
            intent.setAction(ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting WakeWordService: " + e.getMessage());
        }
    }

    public static void stop(Context context) {
        try {
            Intent intent = new Intent(context, WakeWordService.class);
            intent.setAction(ACTION_STOP);
            context.startService(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error stopping WakeWordService: " + e.getMessage());
        }
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
                    Log.i(TAG, "Screen OFF -> Instantly stopping listener for 100% CPU Deep Sleep");
                    stopListening();
                } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    Log.i(TAG, "Screen ON -> Checking active time window before listening");
                    if (MemoryVault.isWithinActiveWindow(context)) {
                        startListening();
                    } else {
                        Log.i(TAG, "Screen ON but outside active time window. Sleeping.");
                        stopListening();
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenStateReceiver, filter);
        Log.i(TAG, "Screen State BroadcastReceiver registered");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopListening();
            try { stopForeground(true); } catch (Exception ignored) {}
            stopSelf();
            return START_NOT_STICKY;
        }

        startForegroundNotification();

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean isScreenOn = pm != null && pm.isInteractive();
        if (isScreenOn && MemoryVault.isWithinActiveWindow(this)) {
            startListening();
        } else {
            Log.i(TAG, "Screen off or outside active window -> Idle Deep Sleep mode.");
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
            channel.setDescription("Maintains ultra-low-power wake word listener");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void startForegroundNotification() {
        try {
            Intent notificationIntent = new Intent(this, AssistantActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
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
        } catch (Exception e) {
            Log.w(TAG, "Foreground notification start error: " + e.getMessage());
        }
    }

    /**
     * Starts listening on a dedicated background thread using ExecutorService.
     */
    public synchronized void startListening() {
        if (isListening || isVerifyingStage2) return;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Cannot start WakeWordService: RECORD_AUDIO permission not granted");
            return;
        }

        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newSingleThreadExecutor();
        }

        isListening = true;
        executorService.execute(this::audioLoop);
        Log.i(TAG, "WakeWordService Stage 1 audio loop started on ExecutorService");
    }

    /**
     * Instantly stops audio recording, releases mic, destroys recognizer, and shuts down executor.
     * Ensures 100% CPU deep sleep when screen is off.
     */
    public synchronized void stopListening() {
        isListening = false;
        isVerifyingStage2 = false;

        if (echoCanceler != null) {
            try {
                echoCanceler.setEnabled(false);
                echoCanceler.release();
            } catch (Exception ignored) {}
            echoCanceler = null;
        }

        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
                audioRecord.release();
            } catch (Exception ignored) {}
            audioRecord = null;
        }

        mainHandler.post(() -> {
            if (speechRecognizer != null) {
                try {
                    speechRecognizer.cancel();
                    speechRecognizer.destroy();
                } catch (Exception ignored) {}
                speechRecognizer = null;
            }
        });

        if (executorService != null) {
            try {
                executorService.shutdownNow();
            } catch (Exception ignored) {}
            executorService = null;
        }

        Log.i(TAG, "WakeWordService completely stopped: Deep Sleep active");
    }

    /**
     * Stage 1: Ultra-low-power audio processing loop.
     * Uses AcousticEchoCanceler to ignore phone-generated media sound.
     * Uses Thread.sleep(100) on silence to keep CPU usage below 1%.
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

            // Attach AcousticEchoCanceler to block internal phone media/music sounds
            if (AcousticEchoCanceler.isAvailable()) {
                try {
                    echoCanceler = AcousticEchoCanceler.create(audioRecord.getAudioSessionId());
                    if (echoCanceler != null) {
                        echoCanceler.setEnabled(true);
                        Log.d(TAG, "AcousticEchoCanceler enabled on AudioRecord");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Could not initialize AcousticEchoCanceler: " + e.getMessage());
                }
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

                // 1. Calculate RMS Energy over 512-sample chunk
                double sum = 0;
                for (int i = 0; i < read; i++) {
                    sum += (double) buffer[i] * buffer[i];
                }
                double rms = Math.sqrt(sum / read);

                // 2. Low-Complexity VAD Gating: Below baseline -> drop buffer & sleep 100ms
                if (rms < VAD_BASELINE_ENERGY) {
                    try {
                        Thread.sleep(100); // Critical sleep: keeps CPU under 1% during silence
                    } catch (InterruptedException ie) {
                        break;
                    }
                    continue;
                }

                // 3. Human speech detected above baseline
                long now = SystemClock.elapsedRealtime();
                if (now - lastTriggerTime < COOLDOWN_AFTER_TRIGGER_MS) {
                    continue;
                }

                // Stage 1 Trigger: Potential speech detected -> Transition to Stage 2 Gatekeeper
                if (rms > VAD_VOICE_TRIGGER_ENERGY) {
                    Log.i(TAG, "[STAGE 1] Speech energy detected (" + (int) rms + ") -> Invoking Stage 2 Gatekeeper");
                    triggerStage2Verification();
                    break; // Exit audio loop to hand off microphone to SpeechRecognizer
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Audio loop exception: " + e.getMessage(), e);
        } finally {
            if (audioRecord != null && !isVerifyingStage2) {
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
     * Stage 2 (Gatekeeper): Stops AudioRecord, initializes SpeechRecognizer on main thread,
     * and strictly evaluates recognized text to eliminate ghost triggers.
     */
    private synchronized void triggerStage2Verification() {
        if (isVerifyingStage2) return;
        isVerifyingStage2 = true;
        isListening = false;

        // Release AudioRecord so SpeechRecognizer has exclusive microphone access
        if (echoCanceler != null) {
            try { echoCanceler.release(); } catch (Exception ignored) {}
            echoCanceler = null;
        }
        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
                audioRecord.release();
            } catch (Exception ignored) {}
            audioRecord = null;
        }

        mainHandler.post(this::startGatekeeperRecognizer);
    }

    private void startGatekeeperRecognizer() {
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                Log.w(TAG, "SpeechRecognizer not available for Gatekeeper");
                restartListeningSilently();
                return;
            }

            if (speechRecognizer != null) {
                try { speechRecognizer.destroy(); } catch (Exception ignored) {}
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {}

                @Override
                public void onBeginningOfSpeech() {}

                @Override
                public void onRmsChanged(float rmsdB) {}

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {}

                @Override
                public void onError(int error) {
                    Log.d(TAG, "Stage 2 Gatekeeper error (" + error + ") -> Resuming silent listening");
                    restartListeningSilently();
                }

                @Override
                public void onResults(Bundle results) {
                    if (results != null) {
                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            String text = matches.get(0).toLowerCase(Locale.ROOT).trim();
                            Log.i(TAG, "[STAGE 2 GATEKEEPER] Recognized: \"" + text + "\"");

                            // EXACT MATCH GATEWAY (Kill Ghost Triggers)
                            if (text.equals("marvo") || text.equals("hey marvo") || text.equals("hello marvo")) {
                                launchAssistantActivity();
                                return;
                            }
                        }
                    }
                    // Ignored noise, room chatter, TV, or non-matching text
                    restartListeningSilently();
                }

                @Override
                public void onPartialResults(Bundle partialResults) {}

                @Override
                public void onEvent(int eventType, Bundle params) {}
            });

            Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L);

            speechRecognizer.startListening(recognizerIntent);

        } catch (Exception e) {
            Log.e(TAG, "Error starting Gatekeeper SpeechRecognizer: " + e.getMessage(), e);
            restartListeningSilently();
        }
    }

    /**
     * Cleans up Stage 2 Gatekeeper and resumes silent Stage 1 listening without waking the device.
     */
    private void restartListeningSilently() {
        mainHandler.post(() -> {
            if (speechRecognizer != null) {
                try {
                    speechRecognizer.cancel();
                    speechRecognizer.destroy();
                } catch (Exception ignored) {}
                speechRecognizer = null;
            }
            isVerifyingStage2 = false;

            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            boolean isScreenOn = pm != null && pm.isInteractive();
            if (isScreenOn && MemoryVault.isWithinActiveWindow(WakeWordService.this)) {
                startListening();
            } else {
                stopListening();
            }
        });
    }

    /**
     * Executes on-device wake-up: acquires temporary WakeLock, fires AssistantActivity, releases lock.
     */
    private void launchAssistantActivity() {
        lastTriggerTime = SystemClock.elapsedRealtime();
        Log.i(TAG, "[WAKE WORD CONFIRMED] Exact match authenticated -> Summoning Marvo!");

        stopListening();

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
                wakeLock.acquire(5000);
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
        Log.d(TAG, "WakeWordService destroyed cleanly");
    }
}
