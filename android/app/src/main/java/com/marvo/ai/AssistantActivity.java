package com.marvo.ai;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Locale;

public class AssistantActivity extends AppCompatActivity {
    private static final String TAG = "MarvoAssistant";
    private static final int PERMISSION_REQUEST_RECORD_AUDIO = 101;

    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private TextView statusTextView;
    private ImageView orbImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(R.anim.slide_up_assistant, 0);
        setContentView(R.layout.activity_assistant);
        Log.d(TAG, "Marvo Assistant Triggered via Hardware Button!");

        statusTextView = findViewById(R.id.statusTextView);
        orbImageView = findViewById(R.id.orbImageView);

        // Start pulsating Siri-style glowing orb animation
        if (orbImageView != null) {
            Animation pulse = AnimationUtils.loadAnimation(this, R.anim.pulse_orb);
            orbImageView.startAnimation(pulse);
        }

        // Tap outside bottom sheet to dismiss
        View rootLayout = findViewById(R.id.assistantRootLayout);
        if (rootLayout != null) {
            rootLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        View bottomSheet = findViewById(R.id.bottomSheetContainer);
        if (bottomSheet != null) {
            bottomSheet.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Consume click so tapping the sheet itself does not dismiss
                }
            });
        }

        initSpeechRecognizer();
        checkPermissionAndStart();
    }

    private void initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "Speech recognition is not available on this device");
            if (statusTextView != null) {
                statusTextView.setText("Speech service unavailable");
            }
            return;
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.d(TAG, "SpeechRecognizer onReadyForSpeech");
                if (statusTextView != null) {
                    statusTextView.setText("Listening...");
                }
                if (orbImageView != null) {
                    orbImageView.animate().alpha(1.0f).setDuration(200).start();
                    if (orbImageView.getAnimation() == null) {
                        Animation pulse = AnimationUtils.loadAnimation(AssistantActivity.this, R.anim.pulse_orb);
                        orbImageView.startAnimation(pulse);
                    }
                }
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.d(TAG, "SpeechRecognizer onBeginningOfSpeech");
            }

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                Log.d(TAG, "SpeechRecognizer onEndOfSpeech: Silence detected");
                if (statusTextView != null) {
                    statusTextView.setText("Processing...");
                }
                // Transition orb into thinking mode
                if (orbImageView != null) {
                    orbImageView.clearAnimation();
                    orbImageView.animate()
                        .alpha(0.65f)
                        .scaleX(1.15f)
                        .scaleY(1.15f)
                        .setDuration(250)
                        .start();
                }
            }

            @Override
            public void onError(int error) {
                Log.w(TAG, "SpeechRecognizer onError code: " + error);
                if (statusTextView != null) {
                    statusTextView.setText("Didn't catch that...");
                }
                if (orbImageView != null) {
                    orbImageView.clearAnimation();
                    orbImageView.animate().alpha(0.4f).scaleX(0.9f).scaleY(0.9f).setDuration(200).start();
                }
                // Auto dismiss on error after 2 seconds
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing()) {
                            finish();
                        }
                    }
                }, 2000);
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String transcribed = matches.get(0);
                    Log.d(TAG, "Speech transcribed: " + transcribed);

                    // Route through local intent router
                    routeCommand(transcribed);

                    // Wait 2.5s to display the action, then slide-down and finish
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!isFinishing()) {
                                finish();
                            }
                        }
                    }, 2500);
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> partialMatches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (partialMatches != null && !partialMatches.isEmpty()) {
                    if (statusTextView != null) {
                        statusTextView.setText(partialMatches.get(0));
                    }
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    /**
     * Local Intent Router:
     * Separates offline hardware / system commands from online AI queries.
     */
    private void routeCommand(String command) {
        if (statusTextView == null) return;
        String lower = (command == null ? "" : command.trim().toLowerCase());

        if (lower.startsWith("call")) {
            String target = command.length() > 4 ? command.substring(4).trim() : "";
            statusTextView.setText(target.isEmpty() ? "Action: Offline Call" : "Action: Offline Call " + target);
        } else if (lower.startsWith("sms") || lower.startsWith("message")) {
            statusTextView.setText("Action: Offline SMS");
        } else if (lower.contains("flashlight")) {
            statusTextView.setText("Action: Toggle Flashlight");
        } else {
            statusTextView.setText("Action: Online AI Query");
        }
    }

    private void checkPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else {
            ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                PERMISSION_REQUEST_RECORD_AUDIO
            );
        }
    }

    private void startListening() {
        if (speechRecognizer != null && speechRecognizerIntent != null) {
            try {
                speechRecognizer.startListening(speechRecognizerIntent);
            } catch (Exception e) {
                Log.e(TAG, "Error starting speech recognition: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening();
            } else {
                Log.w(TAG, "RECORD_AUDIO permission was denied by user");
                if (statusTextView != null) {
                    statusTextView.setText("Microphone permission needed");
                }
            }
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, R.anim.slide_down_assistant);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (orbImageView != null) {
            orbImageView.clearAnimation();
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
    }
}
