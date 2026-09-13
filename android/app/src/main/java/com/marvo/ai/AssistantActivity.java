package com.marvo.ai;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.provider.MediaStore;
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
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AssistantActivity extends AppCompatActivity {
    private static final String TAG = "MarvoAssistant";
    private static final int PERMISSION_REQUEST_RECORD_AUDIO = 101;
    private static final int PERMISSION_REQUEST_CONTACTS_CALL = 102;
    private static final int PERMISSION_REQUEST_SMS = 103;

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

                    // If not an offline direct action (which handle their own UI and finish lifecycle), wait 2.5s and finish
                    String lower = transcribed.trim().toLowerCase();
                    boolean isHandledDirectly = lower.startsWith("call") || lower.startsWith("sms") || lower.startsWith("message") ||
                            lower.startsWith("text") || lower.startsWith("whatsapp") || lower.contains("timer") ||
                            lower.contains("alarm") || lower.contains("remind me") || lower.contains("calendar") ||
                            lower.contains("save contact") || lower.contains("add contact") || lower.contains("selfie");
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
     * Resolves a contact's phone number by name from the device's Contacts Provider.
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

        String number = null;
        String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?";
        String[] selectionArgs = new String[]{"%" + contactName.trim() + "%"};
        Cursor cursor = null;

        try {
            cursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME},
                selection,
                selectionArgs,
                null
            );

            if (cursor != null && cursor.moveToFirst()) {
                int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                if (numberIndex != -1) {
                    number = cursor.getString(numberIndex);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying contacts: " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return number;
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
     * Local Intent Router:
     * Separates offline hardware / system commands (calls, sms, flashlight) from online AI queries.
     */
    private void routeCommand(String command) {
        if (statusTextView == null) return;
        String lower = (command == null ? "" : command.trim().toLowerCase());

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

        // Other offline & online intents
        if (lower.contains("flashlight")) {
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
                new String[]{
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.SEND_SMS
                },
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
