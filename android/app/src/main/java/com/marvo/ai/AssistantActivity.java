package com.marvo.ai;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.telephony.SmsManager;
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
import java.io.File;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

public class AssistantActivity extends AppCompatActivity {
    private static final String TAG = "MarvoAssistant";
    private static final int PERMISSION_REQUEST_RECORD_AUDIO = 101;
    private static final int PERMISSION_REQUEST_CONTACTS_CALL = 102;
    private static final int PERMISSION_REQUEST_SMS = 103;
    private static final String PREFS_NAME = "MarvoBusinessPrefs";

    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private TextView statusTextView;
    private TextView subtitleTextView;
    private ImageView orbImageView;

    // State Management for Confirmation Protocol (Step 6 Part 5)
    private String pendingActionType = null;
    private Intent pendingIntent = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(R.anim.slide_up_assistant, 0);
        setContentView(R.layout.activity_assistant);
        Log.d(TAG, "Marvo Assistant Triggered via Hardware Button!");

        statusTextView = findViewById(R.id.statusTextView);
        subtitleTextView = findViewById(R.id.subtitleTextView);
        orbImageView = findViewById(R.id.orbImageView);

        // Start pulsating Siri-style glowing orb animation
        if (orbImageView != null) {
            Animation pulse = AnimationUtils.loadAnimation(this, R.anim.pulse_orb);
            orbImageView.startAnimation(pulse);
            orbImageView.setColorFilter(android.graphics.Color.parseColor("#00E5FF"), android.graphics.PorterDuff.Mode.MULTIPLY);
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
                setVisualState("LISTENING");
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
                setVisualState("PROCESSING");
            }

            @Override
            public void onError(int error) {
                Log.w(TAG, "SpeechRecognizer onError code: " + error);
                setVisualState("ERROR");
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

                    // If not an offline direct action or pending confirmation (which handle their own UI and finish lifecycle), wait 2.5s and finish
                    String lower = transcribed.trim().toLowerCase();
                    boolean isHandledDirectly = (pendingActionType != null) || lower.startsWith("call") || lower.startsWith("sms") || lower.startsWith("message") ||
                            lower.startsWith("text") || lower.startsWith("whatsapp") || lower.contains("timer") ||
                            lower.contains("alarm") || lower.contains("remind me") || lower.contains("calendar") ||
                            lower.contains("save contact") || lower.contains("add contact") || lower.contains("selfie") ||
                            lower.contains("flashlight") || lower.contains("torch") || lower.contains("volume") ||
                            lower.contains("awaaz") || lower.contains("sound") || lower.contains("install") ||
                            lower.contains("download") || lower.contains("uninstall") || lower.contains("delete") ||
                            lower.contains("wifi") || lower.contains("bluetooth") || lower.contains("data") ||
                            lower.contains("internet") || lower.contains("airplane") || lower.contains("flight") ||
                            lower.contains("dark") || lower.contains("brightness") || lower.contains("display") ||
                            lower.contains("calculator") || lower.contains("calculate") || lower.contains("email") ||
                            lower.contains("mail") || lower.contains("search") || lower.contains("google") ||
                            lower.contains("news") || lower.contains("weather") || lower.contains("mausam") ||
                            lower.contains("amazon") || lower.contains("flipkart") || lower.contains("myntra") ||
                            lower.contains("buy") || lower.contains("shop") || lower.contains("kharid") ||
                            lower.contains("review") || lower.contains("rating") ||
                            lower.contains("order") || lower.contains("stock") || lower.contains("khata") ||
                            lower.contains("bill") || lower.contains("client") || lower.contains("bulk rate") ||
                            lower.contains("calculate rate") || lower.contains("hisab") ||
                            lower.contains("business reminder") || lower.contains("delivery reminder") ||
                            lower.contains("payment reminder") || lower.contains("pickup reminder") ||
                            lower.contains("find file") || lower.contains("search document") ||
                            lower.contains("search file") || lower.contains("note down") ||
                            lower.startsWith("remember this") || lower.contains("save note") ||
                            lower.contains("read notes") || lower.contains("show notes") || lower.contains("my notes") ||
                            lower.startsWith("copy ") || lower.contains("clipboard") || lower.contains("paste") ||
                            lower.contains("count words") || lower.contains("word count") ||
                            lower.contains("uppercase") || lower.contains("lowercase") ||
                            lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("kholo ") ||
                            lower.contains("battery") || lower.contains("charge") || lower.contains("phone status");
                    if (!isHandledDirectly) {
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
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> partialMatches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (partialMatches != null && !partialMatches.isEmpty()) {
                    String partial = partialMatches.get(0);
                    // Show live transcription in the secondary cyan subtitle
                    if (subtitleTextView != null) {
                        subtitleTextView.setText("\"" + partial + "\"");
                        subtitleTextView.setVisibility(View.VISIBLE);
                    }
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    /**
     * Universal Contact Resolver (Step 6 Part 8 Hotfix):
     * Dynamically searches ALL device contacts with case-insensitive matching.
     * Prioritises exact match, then falls back to contains match.
     */
    private String getPhoneNumber(String contactName) {
        if (contactName == null || contactName.trim().isEmpty()) return null;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_CONTACTS permission not granted, requesting now");
            ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS},
                PERMISSION_REQUEST_CONTACTS_CALL
            );
            return null;
        }

        String phoneNumber = null;
        String searchName = contactName.toLowerCase().trim();
        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] projection = new String[]{
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        };

        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);

                while (cursor.moveToNext()) {
                    String name = cursor.getString(nameIndex);
                    String number = cursor.getString(numberIndex);

                    if (name != null) {
                        String lowerName = name.toLowerCase().trim();
                        // Check for exact match or contains match
                        if (lowerName.equals(searchName) || lowerName.contains(searchName)) {
                            phoneNumber = number;
                            break; // Found the best match
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying contacts: " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return phoneNumber;
    }

    /**
     * Initiates a native phone call to the given phone number.
     */
    private void makeCall(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) return;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + phoneNumber.trim()));
            callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(callIntent);
        } else {
            Log.w(TAG, "CALL_PHONE permission not granted, opening dialer");
            Intent dialIntent = new Intent(Intent.ACTION_DIAL);
            dialIntent.setData(Uri.parse("tel:" + phoneNumber.trim()));
            dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(dialIntent);
        }
    }

    /**
     * Smart Command Parser:
     * Parses commands starting with "sms", "message", or "text".
     * Strips the trigger word and splits the recipient and body via keywords
     * like " that " or " saying ", or splits at the first space if no keyword exists.
     * e.g., "message john that i am late" -> ["john", "i am late"]
     */
    private String[] parseSmsCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return new String[]{"", ""};
        }

        String raw = command.trim();
        String lower = raw.toLowerCase();

        String[] prefixes = new String[]{
            "send a whatsapp message to ", "send a whatsapp to ",
            "send whatsapp message to ", "send whatsapp to ",
            "whatsapp message to ", "whatsapp to ",
            "whatsapp message ", "whatsapp ",
            "send an sms to ", "send a message to ", "send a text to ",
            "send sms to ", "send message to ", "send text to ",
            "send sms ", "send message ", "send text ",
            "sms to ", "message to ", "text to ",
            "sms ", "message ", "text "
        };

        String remainder = raw;
        for (String p : prefixes) {
            if (lower.startsWith(p)) {
                remainder = raw.substring(p.length()).trim();
                break;
            }
        }

        if (remainder.isEmpty() || remainder.equalsIgnoreCase("sms") || remainder.equalsIgnoreCase("message") || remainder.equalsIgnoreCase("text") || remainder.equalsIgnoreCase("whatsapp")) {
            return new String[]{"", ""};
        }

        String lowerRemainder = remainder.toLowerCase();
        int idxThat = lowerRemainder.indexOf(" that ");
        int idxSaying = lowerRemainder.indexOf(" saying ");

        int splitIdx = -1;
        int splitWordLen = 0;

        if (idxThat != -1 && idxSaying != -1) {
            if (idxThat < idxSaying) {
                splitIdx = idxThat;
                splitWordLen = " that ".length();
            } else {
                splitIdx = idxSaying;
                splitWordLen = " saying ".length();
            }
        } else if (idxThat != -1) {
            splitIdx = idxThat;
            splitWordLen = " that ".length();
        } else if (idxSaying != -1) {
            splitIdx = idxSaying;
            splitWordLen = " saying ".length();
        }

        if (splitIdx != -1) {
            String name = remainder.substring(0, splitIdx).trim();
            String message = remainder.substring(splitIdx + splitWordLen).trim();
            return new String[]{name, message};
        }

        // If no split keyword is found, assume the first word is the name and the rest is the message
        int firstSpace = remainder.indexOf(" ");
        if (firstSpace != -1) {
            String name = remainder.substring(0, firstSpace).trim();
            String message = remainder.substring(firstSpace + 1).trim();
            return new String[]{name, message};
        } else {
            return new String[]{remainder.trim(), ""};
        }
    }

    /**
     * Sends an SMS completely offline in the background without launching an external app.
     */
    private void sendSilentSms(String phoneNumber, String messageBody) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty() || messageBody == null || messageBody.trim().isEmpty()) {
            Log.w(TAG, "sendSilentSms: phone number or message body is empty");
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "SEND_SMS permission not granted, requesting now");
            ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.SEND_SMS},
                PERMISSION_REQUEST_SMS
            );
            return;
        }

        try {
            SmsManager smsManager;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                smsManager = getSystemService(SmsManager.class);
            } else {
                smsManager = SmsManager.getDefault();
            }

            if (smsManager == null) {
                smsManager = SmsManager.getDefault();
            }

            if (messageBody.length() > 160) {
                ArrayList<String> parts = smsManager.divideMessage(messageBody);
                smsManager.sendMultipartTextMessage(phoneNumber.trim(), null, parts, null, null);
            } else {
                smsManager.sendTextMessage(phoneNumber.trim(), null, messageBody, null, null);
            }
            Log.d(TAG, "Silent SMS successfully sent to " + phoneNumber);
        } catch (Exception e) {
            Log.e(TAG, "Error sending silent SMS: " + e.getMessage(), e);
        }
    }

    /**
     * Dispatches a message to WhatsApp via deep-link Intent.
     */
    private void sendWhatsAppMessage(String phoneNumber, String message) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            Log.w(TAG, "sendWhatsAppMessage: Empty phone number");
            return;
        }

        // Clean the phone number (remove spaces, dashes, parentheses)
        String cleanNumber = phoneNumber.replaceAll("[\\s-()]", "");

        // Prepend +91 if no country code (+) is present
        if (!cleanNumber.startsWith("+")) {
            if (cleanNumber.startsWith("91") && cleanNumber.length() == 12) {
                cleanNumber = "+" + cleanNumber;
            } else {
                cleanNumber = "+91" + cleanNumber;
            }
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            String encodedText = "";
            try {
                encodedText = URLEncoder.encode(message != null ? message : "", "UTF-8");
            } catch (Exception ignored) {
                encodedText = message != null ? message : "";
            }
            intent.setData(Uri.parse("https://api.whatsapp.com/send?phone=" + cleanNumber + "&text=" + encodedText));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            if (!isFinishing()) {
                finish();
            }
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "WhatsApp not installed: " + e.getMessage());
            if (statusTextView != null) {
                statusTextView.setText("WhatsApp not installed.");
            }
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishing()) {
                        finish();
                    }
                }
            }, 2000);
        } catch (Exception e) {
            Log.e(TAG, "Error opening WhatsApp: " + e.getMessage(), e);
            if (statusTextView != null) {
                statusTextView.setText("Failed to open WhatsApp.");
            }
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishing()) {
                        finish();
                    }
                }
            }, 2000);
        }
    }

    /**
     * Sets a countdown timer in the system Clock app.
     */
    private void setTimer(int seconds) {
        try {
            Intent intent = new Intent(AlarmClock.ACTION_SET_TIMER);
            intent.putExtra(AlarmClock.EXTRA_LENGTH, seconds);
            intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            if (!isFinishing()) {
                finish();
            }
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Clock/Timer app not found: " + e.getMessage());
            if (statusTextView != null) {
                statusTextView.setText("Timer app not found.");
            }
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishing()) finish();
                }
            }, 2000);
        } catch (Exception e) {
            Log.e(TAG, "Error setting timer: " + e.getMessage(), e);
            if (statusTextView != null) {
                statusTextView.setText("Failed to set timer.");
            }
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishing()) finish();
                }
            }, 2000);
        }
    }

    /**
     * Sets an alarm in the system Clock app.
     */
    private void setAlarm(int hour, int minute, String message) {
        try {
            Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
            intent.putExtra(AlarmClock.EXTRA_HOUR, hour);
            intent.putExtra(AlarmClock.EXTRA_MINUTES, minute);
            intent.putExtra(AlarmClock.EXTRA_MESSAGE, message != null ? message : "Marvo Alarm");
            intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            if (!isFinishing()) {
                finish();
            }
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Clock/Alarm app not found: " + e.getMessage());
            if (statusTextView != null) {
                statusTextView.setText("Alarm app not found.");
            }
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishing()) finish();
                }
            }, 2000);
        } catch (Exception e) {
            Log.e(TAG, "Error setting alarm: " + e.getMessage(), e);
            if (statusTextView != null) {
                statusTextView.setText("Failed to set alarm.");
            }
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishing()) finish();
                }
            }, 2000);
        }
    }

    /**
     * Opens the system Contacts app to insert a new contact.
     */
    private void addContact(String name, String phone) {
        try {
            Intent intent = new Intent(Intent.ACTION_INSERT);
            intent.setType(ContactsContract.Contacts.CONTENT_TYPE);
            intent.putExtra(ContactsContract.Intents.Insert.NAME, name != null ? name : "");
            if (phone != null && !phone.trim().isEmpty()) {
                intent.putExtra(ContactsContract.Intents.Insert.PHONE, phone.trim());
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            if (!isFinishing()) {
                finish();
            }
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Contacts app not found: " + e.getMessage());
            if (statusTextView != null) {
                statusTextView.setText("Contacts app not found.");
            }
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishing()) finish();
                }
            }, 2000);
        } catch (Exception e) {
            Log.e(TAG, "Error adding contact: " + e.getMessage(), e);
            if (statusTextView != null) {
                statusTextView.setText("Failed to open contacts.");
            }
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishing()) finish();
                }
            }, 2000);
        }
    }

    /**
     * Opens the system Calendar app to insert a new event or reminder.
     */
    private void addCalendarEvent(String title) {
        try {
            Intent intent = new Intent(Intent.ACTION_INSERT);
            intent.setData(CalendarContract.Events.CONTENT_URI);
            intent.putExtra(CalendarContract.Events.TITLE, title != null ? title : "Reminder");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            if (!isFinishing()) {
                finish();
            }
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Calendar app not found: " + e.getMessage());
            if (statusTextView != null) {
                statusTextView.setText("Calendar app not found.");
            }
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishing()) finish();
                }
            }, 2000);
        } catch (Exception e) {
            Log.e(TAG, "Error adding calendar event: " + e.getMessage(), e);
            if (statusTextView != null) {
                statusTextView.setText("Failed to open calendar.");
            }
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishing()) finish();
                }
            }, 2000);
        }
    }

    /**
     * Opens the camera app facing the front (selfie) camera.
     */
    private void openSelfieCamera() {
        try {
            Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            intent.putExtra("android.intent.extras.CAMERA_FACING", 1); // Front camera
            intent.putExtra("android.intent.extra.USE_FRONT_CAMERA", true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            if (!isFinishing()) {
                finish();
            }
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Camera app not found: " + e.getMessage());
            if (statusTextView != null) {
                statusTextView.setText("Camera app not found.");
            }
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishing()) finish();
                }
            }, 2000);
        } catch (Exception e) {
            Log.e(TAG, "Error opening selfie camera: " + e.getMessage(), e);
            if (statusTextView != null) {
                statusTextView.setText("Failed to open camera.");
            }
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isFinishing()) finish();
                }
            }, 2000);
        }
    }

    /**
     * Toggles the device's hardware rear flashlight on or off.
     */
    private void toggleFlashlight(boolean state) {
        try {
            CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
            if (cameraManager != null) {
                String cameraId = null;
                for (String id : cameraManager.getCameraIdList()) {
                    CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                    Boolean hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    if (hasFlash != null && hasFlash && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        cameraId = id;
                        break;
                    }
                }
                if (cameraId == null && cameraManager.getCameraIdList().length > 0) {
                    cameraId = cameraManager.getCameraIdList()[0];
                }
                if (cameraId != null) {
                    cameraManager.setTorchMode(cameraId, state);
                    Log.d(TAG, "Flashlight torch set to: " + state);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error toggling flashlight: " + e.getMessage(), e);
            if (statusTextView != null) {
                statusTextView.setText("Flashlight unavailable.");
            }
        }
    }

    /**
     * Adjusts the music stream volume or mutes it.
     */
    private void changeVolume(String action) {
        try {
            AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (audioManager != null) {
                if ("up".equalsIgnoreCase(action)) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                } else if ("down".equalsIgnoreCase(action)) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                } else if ("mute".equalsIgnoreCase(action)) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error adjusting volume: " + e.getMessage(), e);
            if (statusTextView != null) {
                statusTextView.setText("Volume control error.");
            }
        }
    }

    /**
     * Opens the app store search for a requested app name.
     */
    private void openAppStore(String appName) {
        if (appName == null || appName.trim().isEmpty()) return;
        try {
            String query = URLEncoder.encode(appName.trim(), "UTF-8");
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + query));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            if (!isFinishing()) {
                finish();
            }
        } catch (ActivityNotFoundException e) {
            try {
                String query = URLEncoder.encode(appName.trim(), "UTF-8");
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=" + query + "&c=apps"));
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(webIntent);
                if (!isFinishing()) finish();
            } catch (Exception ex) {
                Log.e(TAG, "Error opening web store: " + ex.getMessage(), ex);
                if (statusTextView != null) statusTextView.setText("Play Store not available.");
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() { if (!isFinishing()) finish(); }
                }, 2000);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening app store: " + e.getMessage(), e);
            if (statusTextView != null) statusTextView.setText("Store unavailable.");
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() { if (!isFinishing()) finish(); }
            }, 2000);
        }
    }

    /**
     * Opens the system Application Settings screen to manage/uninstall apps.
     */
    private void openUninstallSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            if (!isFinishing()) {
                finish();
            }
        } catch (ActivityNotFoundException e) {
            try {
                Intent manageIntent = new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
                manageIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(manageIntent);
                if (!isFinishing()) finish();
            } catch (Exception ex) {
                if (statusTextView != null) statusTextView.setText("App settings not found.");
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() { if (!isFinishing()) finish(); }
                }, 2000);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening app settings: " + e.getMessage(), e);
            if (statusTextView != null) statusTextView.setText("Settings unavailable.");
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() { if (!isFinishing()) finish(); }
            }, 2000);
        }
    }

    /**
     * Opens a specific system Settings screen (WiFi, Bluetooth, etc.).
     */
    private void openSetting(String settingAction) {
        try {
            Intent intent = new Intent(settingAction);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            if (!isFinishing()) {
                finish();
            }
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Setting action not supported: " + settingAction);
            try {
                Intent fallback = new Intent(Settings.ACTION_SETTINGS);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(fallback);
                if (!isFinishing()) finish();
            } catch (Exception ex) {
                if (statusTextView != null) statusTextView.setText("Settings not available.");
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() { if (!isFinishing()) finish(); }
                }, 2000);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening setting: " + e.getMessage(), e);
            if (statusTextView != null) statusTextView.setText("Failed to open setting.");
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() { if (!isFinishing()) finish(); }
            }, 2000);
        }
    }

    /**
     * Launches the system Calculator app.
     */
    private void openCalculator() {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_APP_CALCULATOR);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            if (!isFinishing()) finish();
        } catch (ActivityNotFoundException e) {
            String[] calcPackages = new String[]{
                "com.google.android.calculator",
                "com.android.calculator2",
                "com.sec.android.app.popupcalculator",
                "com.miui.calculator"
            };
            boolean launched = false;
            for (String pkg : calcPackages) {
                try {
                    Intent launchIntent = getPackageManager().getLaunchIntentForPackage(pkg);
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(launchIntent);
                        launched = true;
                        if (!isFinishing()) finish();
                        break;
                    }
                } catch (Exception ignored) {}
            }
            if (!launched) {
                if (statusTextView != null) statusTextView.setText("Calculator not found.");
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() { if (!isFinishing()) finish(); }
                }, 2000);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening calculator: " + e.getMessage(), e);
            if (statusTextView != null) statusTextView.setText("Calculator unavailable.");
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() { if (!isFinishing()) finish(); }
            }, 2000);
        }
    }

    /**
     * Step 6 Part 9: Centralized Visual State Engine.
     * Controls orb animation, color filter, status/subtitle text, and alpha
     * based on the current assistant lifecycle state.
     */
    private void setVisualState(String state) {
        switch (state) {
            case "LISTENING":
                if (statusTextView != null) {
                    statusTextView.setText("Listening...");
                    statusTextView.setTextColor(android.graphics.Color.WHITE);
                }
                if (subtitleTextView != null) {
                    subtitleTextView.setText("Speak now...");
                    subtitleTextView.setVisibility(View.VISIBLE);
                }
                if (orbImageView != null) {
                    orbImageView.clearAnimation();
                    orbImageView.setColorFilter(android.graphics.Color.parseColor("#00E5FF"), android.graphics.PorterDuff.Mode.MULTIPLY);
                    orbImageView.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
                    Animation pulse = AnimationUtils.loadAnimation(this, R.anim.pulse_orb);
                    orbImageView.startAnimation(pulse);
                }
                break;

            case "PROCESSING":
                if (statusTextView != null) {
                    statusTextView.setText("Processing...");
                    statusTextView.setTextColor(android.graphics.Color.parseColor("#B0B0B0"));
                }
                if (subtitleTextView != null) {
                    subtitleTextView.setText("Analyzing command...");
                    subtitleTextView.setVisibility(View.VISIBLE);
                }
                if (orbImageView != null) {
                    orbImageView.clearAnimation();
                    orbImageView.setColorFilter(android.graphics.Color.parseColor("#7C4DFF"), android.graphics.PorterDuff.Mode.MULTIPLY);
                    Animation fastPulse = AnimationUtils.loadAnimation(this, R.anim.pulse_orb_fast);
                    orbImageView.startAnimation(fastPulse);
                }
                break;

            case "SUCCESS":
                if (statusTextView != null) {
                    statusTextView.setTextColor(android.graphics.Color.parseColor("#00E676"));
                }
                if (subtitleTextView != null) {
                    subtitleTextView.setVisibility(View.GONE);
                }
                if (orbImageView != null) {
                    orbImageView.clearAnimation();
                    orbImageView.setColorFilter(android.graphics.Color.parseColor("#00E676"), android.graphics.PorterDuff.Mode.MULTIPLY);
                    Animation successBurst = AnimationUtils.loadAnimation(this, R.anim.pulse_orb_success);
                    orbImageView.startAnimation(successBurst);
                }
                break;

            case "ERROR":
                if (statusTextView != null) {
                    statusTextView.setText("Didn't catch that...");
                    statusTextView.setTextColor(android.graphics.Color.parseColor("#FF5252"));
                }
                if (subtitleTextView != null) {
                    subtitleTextView.setText("Tap to try again");
                    subtitleTextView.setVisibility(View.VISIBLE);
                }
                if (orbImageView != null) {
                    orbImageView.clearAnimation();
                    orbImageView.setColorFilter(android.graphics.Color.parseColor("#FF5252"), android.graphics.PorterDuff.Mode.MULTIPLY);
                    orbImageView.animate().alpha(0.4f).scaleX(0.85f).scaleY(0.85f).setDuration(250).start();
                }
                break;

            default:
                break;
        }
    }

    /**
     * Premium updateUI: Sets the main status text and triggers a SUCCESS visual
     * state for action-related messages, keeping PROCESSING for others.
     */
    private void updateUI(String text) {
        if (statusTextView != null) {
            statusTextView.setText(text);
        }
        // Detect action-execution messages and trigger success glow
        String lower = text.toLowerCase();
        if (lower.contains("confirmed") || lower.contains("sent") || lower.contains("calling") ||
            lower.contains("turned on") || lower.contains("turned off") || lower.contains("saved") ||
            lower.contains("opened") || lower.contains("alright")) {
            setVisualState("SUCCESS");
        }
    }

    private void finishDelayed(long delayMillis) {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing()) {
                    finish();
                }
            }
        }, delayMillis);
    }

    private void startListeningDelayed(long delayMillis) {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing()) {
                    startListening();
                }
            }
        }, delayMillis);
    }

    /**
     * Confirmation Protocol:
     * Prompts the user with a confirmation query, retains pending state,
     * and automatically re-arms the speech listener after a 1.5s delay.
     */
    private void askForConfirmation(String promptText, Intent intent, String actionType) {
        pendingIntent = intent;
        pendingActionType = actionType;
        updateUI(promptText + "\n(Say YES to confirm or NO to cancel)");
        startListeningDelayed(1500);
    }

    /**
     * Prepares an email draft Intent and asks user confirmation.
     */
    private void sendEmail(String recipient, String subject, String body) {
        try {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:" + (recipient != null ? recipient.trim() : "")));
            if (subject != null && !subject.trim().isEmpty()) {
                intent.putExtra(Intent.EXTRA_SUBJECT, subject.trim());
            }
            if (body != null && !body.trim().isEmpty()) {
                intent.putExtra(Intent.EXTRA_TEXT, body.trim());
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            String target = (recipient != null && !recipient.trim().isEmpty()) ? recipient.trim() : "recipient";
            askForConfirmation("Send email to " + target + "?", intent, "EMAIL");
        } catch (Exception e) {
            Log.e(TAG, "Error preparing email: " + e.getMessage(), e);
            updateUI("Failed to prepare email.");
            finishDelayed(2000);
        }
    }

    /**
     * Performs a web search on Google.
     */
    private void searchWeb(String query) {
        if (query == null || query.trim().isEmpty()) return;
        try {
            String encoded = URLEncoder.encode(query.trim(), "UTF-8");
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + encoded));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            if (!isFinishing()) finish();
        } catch (Exception e) {
            Log.e(TAG, "Error searching web: " + e.getMessage(), e);
            updateUI("Web search unavailable.");
            finishDelayed(2000);
        }
    }

    /**
     * Opens Google News.
     */
    private void openNews() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://news.google.com"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            if (!isFinishing()) finish();
        } catch (Exception e) {
            Log.e(TAG, "Error opening news: " + e.getMessage(), e);
            updateUI("News unavailable.");
            finishDelayed(2000);
        }
    }

    /**
     * Opens weather forecast on Google.
     */
    private void openWeather() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=weather"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            if (!isFinishing()) finish();
        } catch (Exception e) {
            Log.e(TAG, "Error opening weather: " + e.getMessage(), e);
            updateUI("Weather unavailable.");
            finishDelayed(2000);
        }
    }

    /**
     * Constructs an Amazon search URL.
     */
    private String getAmazonSearchUrl(String query) {
        try {
            return "https://www.amazon.in/s?k=" + URLEncoder.encode(query != null ? query.trim() : "", "UTF-8");
        } catch (Exception e) {
            return "https://www.amazon.in/s?k=" + (query != null ? query.trim() : "");
        }
    }

    /**
     * Constructs a Flipkart search URL.
     */
    private String getFlipkartSearchUrl(String query) {
        try {
            return "https://www.flipkart.com/search?q=" + URLEncoder.encode(query != null ? query.trim() : "", "UTF-8");
        } catch (Exception e) {
            return "https://www.flipkart.com/search?q=" + (query != null ? query.trim() : "");
        }
    }

    /**
     * Constructs a Myntra search URL.
     */
    private String getMyntraSearchUrl(String query) {
        try {
            return "https://www.myntra.com/" + URLEncoder.encode(query != null ? query.trim() : "", "UTF-8");
        } catch (Exception e) {
            return "https://www.myntra.com/" + (query != null ? query.trim() : "");
        }
    }

    // =====================================================================
    // Step 7: Offline Business Suite (Orders, Inventory, Bulk Rate, Client Dialer)
    // =====================================================================

    /**
     * Saves a business order to local SharedPreferences as a JSON array.
     */
    private void saveBusinessOrder(String clientName, String quantity) {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String ordersJson = prefs.getString("orders", "[]");
            JSONArray orders = new JSONArray(ordersJson);

            JSONObject order = new JSONObject();
            order.put("date", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date()));
            order.put("client", clientName);
            order.put("product", "Paper Plates");
            order.put("quantity", quantity);

            orders.put(order);
            prefs.edit().putString("orders", orders.toString()).apply();

            updateUI("Order saved for " + clientName + " - " + quantity + " pieces.");
            finishDelayed(2000);
        } catch (Exception e) {
            Log.e(TAG, "Error saving business order: " + e.getMessage(), e);
            updateUI("Failed to save order.");
            finishDelayed(2000);
        }
    }

    /**
     * Reads and displays current inventory stock from local SharedPreferences.
     */
    private void checkInventoryStock() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String stockJson = prefs.getString("inventory", "{}");
            JSONObject stock = new JSONObject(stockJson);

            if (stock.length() == 0) {
                updateUI("No stock data recorded yet.\nSay 'update stock' to add.");
            } else {
                StringBuilder sb = new StringBuilder("Current Stock:\n");
                java.util.Iterator<String> keys = stock.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    sb.append("• ").append(key).append(": ").append(stock.getString(key)).append("\n");
                }
                updateUI(sb.toString().trim());
            }
            finishDelayed(3000);
        } catch (Exception e) {
            Log.e(TAG, "Error reading inventory: " + e.getMessage(), e);
            updateUI("Failed to read stock.");
            finishDelayed(2000);
        }
    }

    /**
     * Updates inventory stock in local SharedPreferences.
     */
    private void updateInventoryStock(String itemName, String quantity) {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String stockJson = prefs.getString("inventory", "{}");
            JSONObject stock = new JSONObject(stockJson);
            stock.put(itemName, quantity);
            prefs.edit().putString("inventory", stock.toString()).apply();

            updateUI("Stock updated: " + itemName + " = " + quantity);
            finishDelayed(2000);
        } catch (Exception e) {
            Log.e(TAG, "Error updating inventory: " + e.getMessage(), e);
            updateUI("Failed to update stock.");
            finishDelayed(2000);
        }
    }

    /**
     * Calls a business client using the universal contact resolver.
     */
    private void callBusinessClient(String clientName) {
        if (clientName == null || clientName.trim().isEmpty()) {
            updateUI("Which client should I call?");
            finishDelayed(2000);
            return;
        }
        String number = getPhoneNumber(clientName);
        if (number != null && !number.trim().isEmpty()) {
            updateUI("Calling " + clientName + "...");
            makeCall(number);
            finishDelayed(1500);
        } else {
            updateUI("Client '" + clientName + "' not found in contacts.");
            finishDelayed(2000);
        }
    }

    /**
     * Offline bulk rate calculator: parses quantity and per-item rate from voice.
     */
    private void calculateBulkRate(String command) {
        try {
            // Extract all numbers from the command
            Matcher m = Pattern.compile("(\\d+\\.?\\d*)").matcher(command);
            ArrayList<Double> numbers = new ArrayList<>();
            while (m.find()) {
                numbers.add(Double.parseDouble(m.group(1)));
            }

            if (numbers.size() >= 2) {
                double quantity = numbers.get(0);
                double rate = numbers.get(1);

                // Check if rate is in paise (e.g., "45 paise")
                String lower = command.toLowerCase();
                if (lower.contains("paise") || lower.contains("paisa")) {
                    rate = rate / 100.0;
                }

                double total = quantity * rate;
                String formattedTotal = String.format(Locale.getDefault(), "%.2f", total);
                updateUI("Bulk Rate Estimate:\n" +
                    String.format(Locale.getDefault(), "%.0f", quantity) + " × ₹" +
                    String.format(Locale.getDefault(), "%.2f", rate) + " = ₹" + formattedTotal);
            } else if (numbers.size() == 1) {
                updateUI("Got quantity " + String.format(Locale.getDefault(), "%.0f", numbers.get(0)) +
                    ". What is the rate per item?");
            } else {
                updateUI("Please say quantity and rate. E.g., 'Calculate 5000 at 45 paise'");
            }
            finishDelayed(3000);
        } catch (Exception e) {
            Log.e(TAG, "Error calculating rate: " + e.getMessage(), e);
            updateUI("Calculation error.");
            finishDelayed(2000);
        }
    }

    /**
     * Sets a business reminder via the native alarm/calendar system.
     */
    private void setBusinessReminder(String reminderText) {
        try {
            Intent intent = new Intent(Intent.ACTION_INSERT);
            intent.setData(CalendarContract.Events.CONTENT_URI);
            intent.putExtra(CalendarContract.Events.TITLE, "Marvo Biz: " + reminderText);
            intent.putExtra(CalendarContract.Events.DESCRIPTION, "Business reminder set by Marvo AI");
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, System.currentTimeMillis() + 3600000L); // 1 hr from now
            intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, System.currentTimeMillis() + 7200000L);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            updateUI("Business reminder set: " + reminderText);
            finishDelayed(1500);
        } catch (Exception e) {
            Log.e(TAG, "Error setting business reminder: " + e.getMessage(), e);
            updateUI("Failed to set reminder.");
            finishDelayed(2000);
        }
    }

    // =====================================================================
    // Step 8: Offline File Search & Smart Local Note-Taking
    // =====================================================================

    /**
     * Launches a file picker/search intent for local document searching.
     */
    private void searchLocalFiles(String query) {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_TITLE, query);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(Intent.createChooser(intent, "Search: " + query));
            updateUI("Opening file browser for: " + query);
            finishDelayed(1500);
        } catch (Exception e) {
            Log.e(TAG, "Error opening file browser: " + e.getMessage(), e);
            updateUI("File browser unavailable.");
            finishDelayed(2000);
        }
    }

    /**
     * Saves a quick note to a local MarvoNotes.txt file in internal storage.
     */
    private void saveLocalNote(String noteContent) {
        try {
            File notesDir = new File(getFilesDir(), "MarvoNotes");
            if (!notesDir.exists()) {
                notesDir.mkdirs();
            }
            File notesFile = new File(notesDir, "MarvoNotes.txt");
            FileWriter writer = new FileWriter(notesFile, true); // append mode
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            writer.write("[" + timestamp + "] " + noteContent + "\n");
            writer.flush();
            writer.close();

            updateUI("Note saved successfully.");
            finishDelayed(1500);
        } catch (Exception e) {
            Log.e(TAG, "Error saving note: " + e.getMessage(), e);
            updateUI("Failed to save note.");
            finishDelayed(2000);
        }
    }

    /**
     * Reads and displays all saved notes from MarvoNotes.txt.
     */
    private void readLocalNotes() {
        try {
            File notesFile = new File(new File(getFilesDir(), "MarvoNotes"), "MarvoNotes.txt");
            if (!notesFile.exists()) {
                updateUI("No notes saved yet.\nSay 'note down...' to create one.");
                finishDelayed(2000);
                return;
            }

            BufferedReader reader = new BufferedReader(new FileReader(notesFile));
            StringBuilder sb = new StringBuilder("Your Notes:\n");
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count < 10) {
                sb.append(line).append("\n");
                count++;
            }
            reader.close();

            if (count == 0) {
                updateUI("No notes saved yet.");
            } else {
                updateUI(sb.toString().trim());
            }
            finishDelayed(4000);
        } catch (Exception e) {
            Log.e(TAG, "Error reading notes: " + e.getMessage(), e);
            updateUI("Failed to read notes.");
            finishDelayed(2000);
        }
    }

    // =====================================================================
    // Step 7 Part 3: Clipboard & Smart Text Utilities
    // =====================================================================

    /**
     * Copies text to the system clipboard.
     */
    private void copyTextToClipboard(String text) {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Marvo Copied Text", text);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
            }
            updateUI("Text copied to clipboard.");
            finishDelayed(1500);
        } catch (Exception e) {
            Log.e(TAG, "Error copying to clipboard: " + e.getMessage(), e);
            updateUI("Failed to copy text.");
            finishDelayed(2000);
        }
    }

    /**
     * Reads and displays current clipboard content.
     */
    private void readTextFromClipboard() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                CharSequence pasteData = item.getText();
                if (pasteData != null && pasteData.length() > 0) {
                    updateUI("Clipboard:\n" + pasteData.toString());
                } else {
                    updateUI("Clipboard is empty.");
                }
            } else {
                updateUI("Clipboard is empty.");
            }
            finishDelayed(3000);
        } catch (Exception e) {
            Log.e(TAG, "Error reading clipboard: " + e.getMessage(), e);
            updateUI("Failed to read clipboard.");
            finishDelayed(2000);
        }
    }

    /**
     * Counts words and characters in a spoken sentence.
     */
    private void countWordsInText(String text) {
        if (text == null || text.trim().isEmpty()) {
            updateUI("No text to count.");
            finishDelayed(1500);
            return;
        }
        String cleaned = text.trim();
        int wordCount = cleaned.split("\\s+").length;
        int charCount = cleaned.length();
        updateUI("Word Count: " + wordCount + "\nCharacter Count: " + charCount);
        finishDelayed(2500);
    }

    // =====================================================================
    // Step 7 Part 4: App Launcher & Device Status
    // =====================================================================

    /**
     * Launches an installed app by matching the spoken name to package labels.
     */
    private void launchAppByName(String appName) {
        if (appName == null || appName.trim().isEmpty()) {
            updateUI("Which app should I open?");
            finishDelayed(2000);
            return;
        }

        String searchName = appName.toLowerCase().trim();
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo app : apps) {
            String label = pm.getApplicationLabel(app).toString().toLowerCase().trim();
            if (label.equals(searchName) || label.contains(searchName)) {
                Intent launchIntent = pm.getLaunchIntentForPackage(app.packageName);
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    updateUI("Opening " + pm.getApplicationLabel(app) + "...");
                    startActivity(launchIntent);
                    finishDelayed(1000);
                    return;
                }
            }
        }

        updateUI("App '" + appName + "' not found.");
        finishDelayed(2000);
    }

    /**
     * Queries and displays the current battery level.
     */
    private void getDeviceBatteryLevel() {
        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = registerReceiver(null, ifilter);
            if (batteryStatus != null) {
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                float batteryPct = level * 100 / (float) scale;
                String chargingState = (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL) ? "Charging" : "Not Charging";
                updateUI("Battery: " + (int) batteryPct + "%\nStatus: " + chargingState);
            } else {
                updateUI("Unable to read battery status.");
            }
            finishDelayed(2500);
        } catch (Exception e) {
            Log.e(TAG, "Error reading battery: " + e.getMessage(), e);
            updateUI("Battery check failed.");
            finishDelayed(2000);
        }
    }

    /**
      * Local Intent Router:
     * Separates offline hardware / system commands (calls, sms, flashlight) from online AI queries.
     */
    private void routeCommand(String command) {
        if (statusTextView == null) return;
        String lower = (command == null ? "" : command.trim().toLowerCase());

        // Step 6 Part 5 & 6: State Management (Confirmation Protocol & Checkout Flow)
        if (pendingActionType != null) {
            if ("CHECKOUT_FLOW".equals(pendingActionType)) {
                if (lower.contains("cash") || lower.contains("cod") || lower.contains("delivery")) {
                    updateUI("Alright! Selected Cash on Delivery. Please complete the final order placement on your screen.");
                    pendingActionType = null;
                    pendingIntent = null;
                    finishDelayed(2000);
                } else if (lower.contains("online") || lower.contains("card") || lower.contains("upi") || lower.contains("net banking") || lower.contains("gpay") || lower.contains("paytm") || lower.contains("phonepe")) {
                    updateUI("Opening secure online payment gateway steps...");
                    pendingActionType = null;
                    pendingIntent = null;
                    finishDelayed(2000);
                } else if (lower.contains("cancel") || lower.contains("nahi") || lower.contains("no") || lower.contains("mat")) {
                    updateUI("Shopping cancelled.");
                    pendingActionType = null;
                    pendingIntent = null;
                    finishDelayed(1500);
                } else {
                    updateUI("Please say Cash on Delivery or Online Payment.");
                    startListeningDelayed(1500);
                }
                return;
            }

            // General Confirmation Loop (Yes/No)
            if (lower.contains("yes") || lower.contains("haan") || lower.contains("confirm") || lower.contains("ok") || lower.contains("karo") || lower.contains("sure")) {
                updateUI("Action Confirmed. Executing...");
                if (pendingIntent != null) {
                    try {
                        startActivity(pendingIntent);
                    } catch (Exception e) {
                        Log.e(TAG, "Error executing confirmed intent: " + e.getMessage(), e);
                        updateUI("Failed to execute action.");
                    }
                }
                pendingActionType = null;
                pendingIntent = null;
                finishDelayed(1500);
            } else if (lower.contains("no") || lower.contains("cancel") || lower.contains("nahi") || lower.contains("mat") || lower.contains("stop")) {
                updateUI("Action Cancelled.");
                pendingActionType = null;
                pendingIntent = null;
                finishDelayed(1500);
            } else {
                updateUI("Didn't catch that. Say YES or NO.");
                startListeningDelayed(1500);
            }
            return; // Exit the router since we handled the confirmation
        }

        // Step 5: Native Offline Contact Calling
        if (lower.startsWith("call")) {
            String name = "";
            if (lower.startsWith("call ")) {
                name = command.substring(5).trim();
            } else if (command.length() > 4) {
                name = command.substring(4).trim();
            }

            if (name.isEmpty()) {
                statusTextView.setText("Who would you like to call?");
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing()) finish();
                    }
                }, 2000);
                return;
            }

            statusTextView.setText("Looking for " + name + "...");
            String number = getPhoneNumber(name);

            if (number != null && !number.trim().isEmpty()) {
                statusTextView.setText("Calling " + name + "...");
                makeCall(number);
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing()) {
                            finish();
                        }
                    }
                }, 1000);
            } else {
                statusTextView.setText("Contact not found.");
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing()) {
                            finish();
                        }
                    }
                }, 2000);
            }
            return;
        }

        // Step 6: The Offline Brain (Smart Native SMS & Parsing)
        if (lower.startsWith("sms ") || lower.startsWith("message ") || lower.startsWith("text ") ||
            lower.equals("sms") || lower.equals("message") || lower.equals("text")) {
            String[] parsed = parseSmsCommand(command);
            final String contactName = parsed[0];
            final String messageBody = parsed[1];

            if (contactName.isEmpty()) {
                statusTextView.setText("Who would you like to message?");
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing()) finish();
                    }
                }, 2000);
                return;
            }

            if (messageBody.isEmpty()) {
                statusTextView.setText("What message for " + contactName + "?");
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing()) finish();
                    }
                }, 2000);
                return;
            }

            statusTextView.setText("Finding " + contactName + "...");
            String number = getPhoneNumber(contactName);

            if (number != null && !number.trim().isEmpty()) {
                statusTextView.setText("Sending message...");
                sendSilentSms(number, messageBody);
                statusTextView.setText("Message Sent!");
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing()) {
                            finish();
                        }
                    }
                }, 2000);
            } else {
                statusTextView.setText("Contact not found.");
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing()) {
                            finish();
                        }
                    }
                }, 2000);
            }
            return;
        }

        // Step 6 Part 2: Advanced Third-Party Messaging (WhatsApp Engine)
        else if (lower.startsWith("whatsapp ") || lower.equals("whatsapp")) {
            String[] parsed = parseSmsCommand(command);
            final String contactName = parsed[0];
            final String messageBody = parsed[1];

            if (contactName.isEmpty()) {
                statusTextView.setText("Who would you like to message on WhatsApp?");
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing()) finish();
                    }
                }, 2000);
                return;
            }

            if (messageBody.isEmpty()) {
                statusTextView.setText("What message for " + contactName + "?");
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing()) finish();
                    }
                }, 2000);
                return;
            }

            statusTextView.setText("Opening WhatsApp for " + contactName + "...");
            String number = getPhoneNumber(contactName);

            if (number != null && !number.trim().isEmpty()) {
                sendWhatsAppMessage(number, messageBody);
            } else {
                statusTextView.setText("Contact not found.");
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing()) {
                            finish();
                        }
                    }
                }, 2000);
            }
            return;
        }

        // Step 6 Part 3: Advanced Offline System Controls (Alarms, Timers, Calendar, Contacts, Camera)
        else if (lower.contains("timer")) {
            int seconds = 300; // default 5 minutes
            Matcher m = Pattern.compile("(\\d+)").matcher(lower);
            if (m.find()) {
                try {
                    int val = Integer.parseInt(m.group(1));
                    if (lower.contains("hour")) {
                        seconds = val * 3600;
                    } else if (lower.contains("second") || lower.contains("sec")) {
                        seconds = val;
                    } else {
                        seconds = val * 60;
                    }
                } catch (Exception ignored) {}
            }
            String displayTime = seconds >= 60 ? (seconds / 60) + " min" : seconds + " sec";
            statusTextView.setText("Setting timer for " + displayTime + "...");
            setTimer(seconds);
            return;
        } else if (lower.contains("alarm")) {
            int hour = 7;
            int minute = 0;
            Matcher m = Pattern.compile("(\\d{1,2})(?:[:.](\\d{2}))?").matcher(lower);
            if (m.find()) {
                try {
                    hour = Integer.parseInt(m.group(1));
                    if (m.group(2) != null) {
                        minute = Integer.parseInt(m.group(2));
                    }
                } catch (Exception ignored) {}
            }
            if (lower.contains("pm") && hour < 12) {
                hour += 12;
            } else if (lower.contains("am") && hour == 12) {
                hour = 0;
            }
            statusTextView.setText(String.format(Locale.getDefault(), "Setting alarm for %02d:%02d...", hour, minute));
            setAlarm(hour, minute, "Marvo Alarm");
            return;
        } else if (lower.contains("remind me") || lower.contains("calendar")) {
            String title = "";
            String[] prefixes = new String[]{
                "add to my calendar ", "add to calendar ", "add event to calendar ",
                "remind me to ", "remind me that ", "remind me ", "calendar "
            };
            for (String p : prefixes) {
                int idx = lower.indexOf(p);
                if (idx != -1) {
                    title = command.substring(idx + p.length()).trim();
                    break;
                }
            }
            if (title.isEmpty()) {
                title = "Reminder";
            }
            statusTextView.setText("Adding event: " + title + "...");
            addCalendarEvent(title);
            return;
        } else if (lower.contains("save contact") || lower.contains("add contact")) {
            String name = "";
            String phone = "";
            String[] prefixes = new String[]{"save contact ", "add contact ", "save contact", "add contact"};
            for (String p : prefixes) {
                int idx = lower.indexOf(p);
                if (idx != -1) {
                    name = command.substring(idx + p.length()).trim();
                    break;
                }
            }
            Matcher phoneMatcher = Pattern.compile("(\\+?\\d[\\d\\s-]{6,}\\d)").matcher(name);
            if (phoneMatcher.find()) {
                phone = phoneMatcher.group(1).trim();
                name = name.replace(phone, "").trim();
            }
            if (name.isEmpty()) {
                name = "New Contact";
            }
            statusTextView.setText("Saving contact: " + name + "...");
            addContact(name, phone);
            return;
        } else if (lower.contains("selfie")) {
            statusTextView.setText("Opening Selfie Camera...");
            openSelfieCamera();
            return;
        }

        // Step 6 Part 4: Bilingual Master System Controller (Flashlight, Volume, Settings, Apps)
        else if (lower.contains("flashlight") || lower.contains("torch")) {
            if (lower.contains("off") || lower.contains("band") || lower.contains("bujhao") || lower.contains("close")) {
                statusTextView.setText("Flashlight Turned OFF");
                toggleFlashlight(false);
            } else {
                statusTextView.setText("Flashlight Turned ON");
                toggleFlashlight(true);
            }
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() { if (!isFinishing()) finish(); }
            }, 1500);
            return;
        } else if (lower.contains("volume") || lower.contains("awaaz") || lower.contains("sound")) {
            if (lower.contains("down") || lower.contains("decrease") || lower.contains("kam") || lower.contains("ghata") || lower.contains("dheere")) {
                statusTextView.setText("Decreasing Volume...");
                changeVolume("down");
            } else if (lower.contains("mute") || lower.contains("silent") || lower.contains("off") || lower.contains("band") || lower.contains("chup")) {
                statusTextView.setText("Volume Muted");
                changeVolume("mute");
            } else {
                statusTextView.setText("Increasing Volume...");
                changeVolume("up");
            }
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() { if (!isFinishing()) finish(); }
            }, 1500);
            return;
        } else if (lower.contains("uninstall") || (lower.contains("delete") && lower.contains("app")) || (lower.contains("remove") && lower.contains("app"))) {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            askForConfirmation("Open settings to manage/uninstall apps?", intent, "UNINSTALL");
            return;
        } else if (lower.contains("email") || lower.contains("mail")) {
            String target = "";
            String body = "";
            String raw = command.trim();
            String cleaned = raw.replaceAll("(?i)^(send an email to|send email to|send a mail to|send mail to|email to|mail to|email|mail)\\s*", "").trim();
            int idxThat = cleaned.toLowerCase().indexOf(" that ");
            int idxSaying = cleaned.toLowerCase().indexOf(" saying ");
            int splitIdx = -1;
            int splitLen = 0;
            if (idxThat != -1 && idxSaying != -1) {
                if (idxThat < idxSaying) {
                    splitIdx = idxThat;
                    splitLen = 6;
                } else {
                    splitIdx = idxSaying;
                    splitLen = 8;
                }
            } else if (idxThat != -1) {
                splitIdx = idxThat;
                splitLen = 6;
            } else if (idxSaying != -1) {
                splitIdx = idxSaying;
                splitLen = 8;
            }

            if (splitIdx != -1) {
                target = cleaned.substring(0, splitIdx).trim();
                body = cleaned.substring(splitIdx + splitLen).trim();
            } else {
                int firstSpace = cleaned.indexOf(" ");
                if (firstSpace != -1) {
                    target = cleaned.substring(0, firstSpace).trim();
                    body = cleaned.substring(firstSpace + 1).trim();
                } else {
                    target = cleaned;
                }
            }

            if (target.isEmpty()) {
                updateUI("Who would you like to email?");
                finishDelayed(2000);
                return;
            }

            sendEmail(target, "Marvo Message", body);
            return;
        } else if (lower.contains("install") || lower.contains("download")) {
            String appName = command;
            String[] prefixes = new String[]{"install app ", "download app ", "install ", "download ", "app "};
            for (String p : prefixes) {
                int idx = appName.toLowerCase().indexOf(p);
                if (idx != -1) {
                    appName = appName.substring(idx + p.length()).trim();
                    break;
                }
            }
            appName = appName.replaceAll("(?i)\\b(karo|karna hai|please|chahiye)\\b", "").trim();
            if (appName.isEmpty()) {
                appName = "Apps";
            }
            statusTextView.setText("Searching " + appName + " on Play Store...");
            openAppStore(appName);
            return;
        } else if (lower.contains("wifi") || lower.contains("wi-fi")) {
            statusTextView.setText("Opening WiFi Settings...");
            openSetting(Settings.ACTION_WIFI_SETTINGS);
            return;
        } else if (lower.contains("bluetooth")) {
            statusTextView.setText("Opening Bluetooth Settings...");
            openSetting(Settings.ACTION_BLUETOOTH_SETTINGS);
            return;
        } else if (lower.contains("data") || lower.contains("internet") || lower.contains("roaming")) {
            statusTextView.setText("Opening Mobile Data Settings...");
            openSetting(Settings.ACTION_DATA_ROAMING_SETTINGS);
            return;
        } else if (lower.contains("airplane") || lower.contains("flight")) {
            statusTextView.setText("Opening Airplane Mode...");
            openSetting(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
            return;
        } else if (lower.contains("dark") || lower.contains("brightness") || lower.contains("display")) {
            statusTextView.setText("Opening Display Settings...");
            openSetting(Settings.ACTION_DISPLAY_SETTINGS);
            return;
        } else if (lower.contains("calculator") || lower.contains("calculate") || lower.contains("hisab")) {
            statusTextView.setText("Opening Calculator...");
            openCalculator();
            return;
        } else if (lower.contains("news") || lower.contains("samachar") || lower.contains("khabar")) {
            updateUI("Opening Latest News...");
            openNews();
            return;
        } else if (lower.contains("weather") || lower.contains("mausam") || lower.contains("temperature")) {
            updateUI("Checking Weather Forecast...");
            openWeather();
            return;
        } else if (lower.startsWith("search") || lower.startsWith("google") || lower.startsWith("khojo") || lower.startsWith("dhoondo")) {
            String query = command;
            String[] prefixes = new String[]{
                "search on google for ", "search google for ", "search for ",
                "search on google ", "search google ", "search ",
                "google ", "khojo ", "dhoondo "
            };
            for (String p : prefixes) {
                int idx = query.toLowerCase().indexOf(p);
                if (idx != -1) {
                    query = query.substring(idx + p.length()).trim();
                    break;
                }
            }
            if (query.isEmpty()) {
                query = "Latest Updates";
            }
            updateUI("Searching for \"" + query + "\"...");
            searchWeb(query);
            return;
        } else if (lower.contains("is this product safe") || lower.contains("is it safe") || lower.contains("how is the quality") ||
                 lower.contains("review") || lower.contains("rating") || lower.contains("kaisa hai") || lower.contains("quality kaisi hai")) {
            String product = command.replaceAll("(?i)^(is this product safe|is it safe to buy|is it safe|how is the quality of|how is the quality|review of|rating of|review|rating)\\s*", "").trim();
            if (product.isEmpty()) {
                product = "Product";
            }
            updateUI("Checking reviews and rating for " + product + "...");
            searchWeb(product + " review and rating");
            return;
        } else if (lower.contains("amazon") || lower.contains("flipkart") || lower.contains("myntra") || lower.contains("buy") || lower.contains("shop") || lower.contains("kharid")) {
            String platform = "Amazon";
            String searchUrl;
            if (lower.contains("flipkart")) {
                platform = "Flipkart";
            } else if (lower.contains("myntra")) {
                platform = "Myntra";
            }

            // Extract the product query
            String product = command;
            String[] prefixes = new String[]{
                "search on amazon for ", "search on flipkart for ", "search on myntra for ",
                "search amazon for ", "search flipkart for ", "search myntra for ",
                "buy on amazon ", "buy on flipkart ", "buy on myntra ",
                "buy from amazon ", "buy from flipkart ", "buy from myntra ",
                "shop on amazon for ", "shop on flipkart for ", "shop on myntra for ",
                "shop for ", "amazon pe ", "flipkart pe ", "myntra pe ",
                "amazon par ", "flipkart par ", "myntra par ",
                "buy ", "shop ", "amazon ", "flipkart ", "myntra "
            };
            for (String p : prefixes) {
                int idx = product.toLowerCase().indexOf(p);
                if (idx != -1) {
                    product = product.substring(idx + p.length()).trim();
                    break;
                }
            }
            product = product.replaceAll("(?i)\\b(karo|please|chahiye|dekhna hai|kharidna hai|kharidna|dikhao)\\b", "").trim();
            if (product.isEmpty()) {
                product = "trending deals";
            }

            if ("Flipkart".equals(platform)) {
                searchUrl = getFlipkartSearchUrl(product);
            } else if ("Myntra".equals(platform)) {
                searchUrl = getMyntraSearchUrl(product);
            } else {
                searchUrl = getAmazonSearchUrl(product);
            }

            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Error opening shopping app: " + e.getMessage(), e);
            }

            // Trigger the Checkout Protocol
            pendingActionType = "CHECKOUT_FLOW";
            updateUI("Opening " + platform + " for " + product + ".\nWould you like to proceed with Cash on Delivery or Online Payment?");
            startListeningDelayed(2000);
            return;
        }

        // =====================================================================
        // Step 7: Offline Business Suite
        // =====================================================================
        else if (lower.contains("order") || lower.contains("stock") || lower.contains("khata") || lower.contains("bill")) {
            if (lower.contains("check stock") || lower.contains("stock level") || lower.contains("inventory") || lower.contains("maal")) {
                statusTextView.setText("Checking inventory...");
                checkInventoryStock();
                return;
            } else if (lower.contains("update stock") || lower.contains("stock update")) {
                String cleaned = command.replaceAll("(?i)(update stock|stock update|for|of)\\s*", "").trim();
                String[] parts = cleaned.split("\\s+", 2);
                String item = parts.length > 0 ? parts[0] : "General";
                String qty = parts.length > 1 ? parts[1] : "0";
                Matcher qm = Pattern.compile("(\\d+)").matcher(cleaned);
                if (qm.find()) {
                    qty = qm.group(1);
                    item = cleaned.replaceAll("\\d+", "").trim();
                    if (item.isEmpty()) item = "Paper Plates";
                }
                updateInventoryStock(item, qty);
                return;
            } else if (lower.contains("order for") || lower.contains("save order") || lower.startsWith("order")) {
                String cleaned = command.replaceAll("(?i)(save order for|order for|save order|order)\\s*", "").trim();
                String clientName = "Customer";
                String quantity = "0";
                Matcher qm = Pattern.compile("(\\d+)").matcher(cleaned);
                if (qm.find()) {
                    quantity = qm.group(1);
                    clientName = cleaned.replaceAll("\\d+", "").replaceAll("(?i)(pieces|plates|dona|packet)\\s*", "").trim();
                    if (clientName.isEmpty()) clientName = "Customer";
                } else {
                    clientName = cleaned;
                }
                saveBusinessOrder(clientName, quantity);
                return;
            } else if (lower.contains("bill") || lower.contains("khata")) {
                statusTextView.setText("Checking ledger...");
                checkInventoryStock();
                return;
            }
        }

        // Step 7: Business Dialer
        else if (lower.startsWith("call client") || lower.startsWith("client call")) {
            String clientName = command.replaceAll("(?i)(call client|client call)\\s*", "").trim();
            callBusinessClient(clientName);
            return;
        }

        // Step 7: Bulk Rate Calculator
        else if (lower.contains("calculate rate") || lower.contains("bulk rate") || lower.contains("rate calculate") || lower.contains("hisab")) {
            calculateBulkRate(command);
            return;
        }

        // Step 7: Business Reminders
        else if (lower.contains("business reminder") || lower.contains("delivery reminder") || lower.contains("payment reminder") || lower.contains("pickup reminder")) {
            String reminderText = command.replaceAll("(?i)(set|add|create)?\\s*(business|delivery|payment|pickup)?\\s*reminder\\s*(for|about|of)?\\s*", "").trim();
            if (reminderText.isEmpty()) reminderText = "Business Task";
            setBusinessReminder(reminderText);
            return;
        }

        // =====================================================================
        // Step 8: File Search & Note-Taking
        // =====================================================================
        else if (lower.contains("find file") || lower.contains("search document") || lower.contains("search file") || lower.contains("open document") || lower.contains("file dhundho")) {
            String query = command.replaceAll("(?i)(find file|search document|search file|open document|file dhundho|for|named)\\s*", "").trim();
            if (query.isEmpty()) query = "document";
            searchLocalFiles(query);
            return;
        } else if (lower.startsWith("note down") || lower.startsWith("remember this") || lower.startsWith("save note") || lower.startsWith("yaad rakh") || lower.startsWith("likh le")) {
            String noteContent = command.replaceAll("(?i)(note down|remember this|save note|yaad rakh|likh le|that)?\\s*", "").trim();
            if (noteContent.isEmpty()) {
                updateUI("What should I note down?");
                finishDelayed(2000);
            } else {
                saveLocalNote(noteContent);
            }
            return;
        } else if (lower.contains("read notes") || lower.contains("show notes") || lower.contains("my notes") || lower.contains("mera note")) {
            readLocalNotes();
            return;
        }

        // =====================================================================
        // Step 7 Part 3: Clipboard & Text Utilities
        // =====================================================================
        else if (lower.startsWith("copy ") || lower.startsWith("clipboard ")) {
            String text = command.replaceAll("(?i)(copy|clipboard)\\s*", "").trim();
            if (text.isEmpty()) {
                updateUI("What should I copy?");
                finishDelayed(2000);
            } else {
                copyTextToClipboard(text);
            }
            return;
        } else if (lower.contains("read clipboard") || lower.contains("show clipboard") || lower.contains("paste") || lower.contains("clipboard dikhao")) {
            readTextFromClipboard();
            return;
        } else if (lower.contains("count words") || lower.contains("word count") || lower.contains("kitne shabd")) {
            String text = command.replaceAll("(?i)(count words|word count|kitne shabd|in|of|for)\\s*", "").trim();
            countWordsInText(text);
            return;
        } else if (lower.contains("uppercase") || lower.contains("capital") || lower.contains("bada karo")) {
            String text = command.replaceAll("(?i)(convert to uppercase|make uppercase|uppercase|capital letters|bada karo)\\s*", "").trim();
            if (!text.isEmpty()) {
                copyTextToClipboard(text.toUpperCase());
            } else {
                updateUI("What text should I convert?");
                finishDelayed(2000);
            }
            return;
        } else if (lower.contains("lowercase") || lower.contains("chhota karo") || lower.contains("small letters")) {
            String text = command.replaceAll("(?i)(convert to lowercase|make lowercase|lowercase|small letters|chhota karo)\\s*", "").trim();
            if (!text.isEmpty()) {
                copyTextToClipboard(text.toLowerCase());
            } else {
                updateUI("What text should I convert?");
                finishDelayed(2000);
            }
            return;
        }

        // =====================================================================
        // Step 7 Part 4: App Launcher & Device Status
        // =====================================================================
        else if (lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("start app ") || lower.startsWith("kholo ")) {
            String appName = command.replaceAll("(?i)(open app|launch app|start app|open|launch|kholo)\\s*", "").trim();
            if (appName.isEmpty()) {
                updateUI("Which app should I open?");
                finishDelayed(2000);
            } else {
                launchAppByName(appName);
            }
            return;
        } else if (lower.contains("battery") || lower.contains("charge") || lower.contains("phone status") || lower.contains("kitna charge")) {
            getDeviceBatteryLevel();
            return;
        }

        // Fallback: Online AI Query
        else {
            statusTextView.setText("Action: Online AI Query");
        }
    }

    private void checkPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else {
            ActivityCompat.requestPermissions(
                this,
                new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.CAMERA
                },
                PERMISSION_REQUEST_RECORD_AUDIO
            );
        }
    }

    private void startListening() {
        if (speechRecognizer != null && speechRecognizerIntent != null) {
            try {
                speechRecognizer.cancel();
                speechRecognizer.startListening(speechRecognizerIntent);
            } catch (Exception e) {
                Log.e(TAG, "Error starting speech recognition: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_RECORD_AUDIO || requestCode == PERMISSION_REQUEST_CONTACTS_CALL || requestCode == PERMISSION_REQUEST_SMS) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
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
