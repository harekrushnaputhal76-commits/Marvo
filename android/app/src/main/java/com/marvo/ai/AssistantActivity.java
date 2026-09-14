package com.marvo.ai;

import android.Manifest;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentUris;
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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.telephony.SmsManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private TextToSpeech tts;
    private boolean isTtsReady = false;
    private TextView statusTextView;
    private TextView subtitleTextView;
    private ScrollView statusScrollView;
    private WebView orbWebView;
    private boolean flashlightEnabled;
    private String geminiApiKey = null;

    // Step 7 - Part 2: Offline Intent Router & 50ms Live Mic Sync
    private OfflineIntentRouter offlineIntentRouter;
    private float currentAudioAmplitude = 0.0f;
    private float targetAudioAmplitude = 0.0f;
    private Handler audioPollHandler = new Handler(Looper.getMainLooper());
    private Runnable audioPollRunnable;

    // Step 10 Part 1: Dynamic Apple-Style Notification Pill
    private View dynamicPillContainer;
    private ImageView pillIcon;
    private TextView pillText;
    private Handler pillHandler = new Handler(Looper.getMainLooper());
    private Runnable pillDismissRunnable;

    // Step 10 Part 2 & 3 & Step 8: Contact Aliasing & Calling Confirmation State
    String pendingCallName = null;
    String pendingCallNumber = null;

    // Step 10 Part 4: Gemini Synchronized Typewriter Engine
    private Handler typewriterHandler = new Handler(Looper.getMainLooper());
    private Runnable typewriterRunnable;

    // State Management for Confirmation Protocol (Step 6 Part 5, Step 5 Part 3 & Step 8)
    String pendingActionType = null;
    private Intent pendingIntent = null;
    private String pendingRecipientName = null;
    private String pendingDraftContent = null;

    // =========================================================================
    // STEP 7 - PART 1: PERSISTENT CONVERSATION MEMORY & SIRI ULTRA URL DIGEST
    // =========================================================================

    public static class ConversationMessage {
        public final String role; // "user" or "model"
        public final String text;

        public ConversationMessage(String role, String text) {
            this.role = role;
            this.text = text;
        }
    }

    private static final int MAX_HISTORY_TURNS = 10;
    private static final List<ConversationMessage> conversationHistory = Collections.synchronizedList(new ArrayList<ConversationMessage>());

    public static synchronized void addConversationTurn(String role, String text) {
        if (text == null || text.trim().isEmpty()) return;
        conversationHistory.add(new ConversationMessage(role, text.trim()));
        while (conversationHistory.size() > MAX_HISTORY_TURNS) {
            conversationHistory.remove(0);
        }
    }

    public static synchronized void clearConversationHistory() {
        conversationHistory.clear();
    }

    private static final Pattern URL_DETECTION_PATTERN = Pattern.compile("(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)", Pattern.CASE_INSENSITIVE);

    /**
     * Extracts first valid URL or domain pattern from user input string.
     */
    private String extractUrlFromText(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        Matcher matcher = URL_DETECTION_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        Matcher domainMatcher = Pattern.compile("(?i)\\b((?:www\\.)[a-z0-9\\-]+\\.[a-z]{2,}(?:/[^\\s]*)?)").matcher(text);
        if (domainMatcher.find()) {
            return "https://" + domainMatcher.group(1);
        }
        return null;
    }

    /**
     * Step 7 - Part 1: Siri Ultra URL Content Digest Engine.
     * Fetches raw webpage HTML via asynchronous HttpURLConnection, follows redirects,
     * strips tags/scripts, and decodes HTML entities.
     */
    private String fetchUrlContent(String targetUrl) {
        if (targetUrl == null || targetUrl.trim().isEmpty()) return null;
        HttpURLConnection conn = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(targetUrl.trim());
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile; MarvoAssistant/2.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36");
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,text/plain;q=0.8,*/*;q=0.5");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");

            int responseCode = conn.getResponseCode();
            // Handle redirects (301, 302, 307, 308)
            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || 
                responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                responseCode == 307 || responseCode == 308) {
                String newUrl = conn.getHeaderField("Location");
                if (newUrl != null && !newUrl.isEmpty()) {
                    conn.disconnect();
                    url = new URL(newUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(10000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile; MarvoAssistant/2.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36");
                    responseCode = conn.getResponseCode();
                }
            }

            if (responseCode >= 200 && responseCode < 300) {
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder rawHtml = new StringBuilder();
                String line;
                int totalChars = 0;
                while ((line = reader.readLine()) != null && totalChars < 250000) {
                    rawHtml.append(line).append("\n");
                    totalChars += line.length() + 1;
                }
                reader.close();

                return sanitizeHtmlToText(rawHtml.toString());
            } else {
                Log.w(TAG, "Webpage fetch returned HTTP " + responseCode);
                return "[Error: Web server responded with HTTP status " + responseCode + "]";
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching URL content: " + e.getMessage(), e);
            return "[Error retrieving webpage content: " + e.getMessage() + "]";
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Strips scripts, styles, navigations, and HTML tags, decoding entities and collapsing whitespace.
     */
    private String sanitizeHtmlToText(String html) {
        if (html == null || html.isEmpty()) return "";
        try {
            // Remove <script>, <style>, <head>, <nav>, <footer>, <svg> tags and contents
            String clean = html.replaceAll("(?is)<script[^>]*>.*?</script>", " ");
            clean = clean.replaceAll("(?is)<style[^>]*>.*?</style>", " ");
            clean = clean.replaceAll("(?is)<head[^>]*>.*?</head>", " ");
            clean = clean.replaceAll("(?is)<nav[^>]*>.*?</nav>", " ");
            clean = clean.replaceAll("(?is)<footer[^>]*>.*?</footer>", " ");
            clean = clean.replaceAll("(?is)<svg[^>]*>.*?</svg>", " ");

            // Structural breaks to newlines
            clean = clean.replaceAll("(?i)<br\\s*/?>", "\n")
                         .replaceAll("(?i)</p>", "\n\n")
                         .replaceAll("(?i)</li>", "\n")
                         .replaceAll("(?i)</h1>|</h2>|</h3>|</h4>|</h5>|</h6>", "\n\n");

            // Strip remaining HTML tags
            clean = clean.replaceAll("<[^>]+>", " ");

            // Decode common HTML entities
            clean = clean.replaceAll("&nbsp;", " ")
                         .replaceAll("&amp;", "&")
                         .replaceAll("&quot;", "\"")
                         .replaceAll("&#39;|&apos;", "'")
                         .replaceAll("&lt;", "<")
                         .replaceAll("&gt;", ">")
                         .replaceAll("&bull;", "•")
                         .replaceAll("&mdash;", "—")
                         .replaceAll("&ndash;", "–");

            // Normalize whitespace
            clean = clean.replaceAll("[ \\t]+", " ");
            clean = clean.replaceAll("(?m)^\\s+$", "");
            clean = clean.replaceAll("\n{3,}", "\n\n").trim();

            // Cap at 6,000 chars for optimal Gemini context
            if (clean.length() > 6000) {
                clean = clean.substring(0, 6000) + "\n... [Webpage content truncated for brevity]";
            }
            return clean;
        } catch (Exception e) {
            Log.w(TAG, "Error sanitizing HTML: " + e.getMessage());
            return html.length() > 3000 ? html.substring(0, 3000) : html;
        }
    }

    // =========================================================================
    // STEP 5: PERSONAL CONTEXT ENGINE & ENTITY INJECTION (Apple 'Device State')
    // =========================================================================

    public static class UserContextProfile {
        public static final String NAME = "Guddu";
        public static final String FULL_NAME = "Harekrushna Puthal";
        public static final String LOCATION = "Odisha, India";
        public static final String DEVICE = "Motorola Edge 60 Pro (12GB RAM)";

        public static final Map<String, String> RELATIONSHIPS = new HashMap<String, String>() {{
            put("Father", "Papa/Bapa");
            put("Mother", "Maa");
            put("Brother", "Jatin");
        }};
    }

    /**
     * Builds real-time System Context string (Date/Time ISO, Location, Entities, Battery, Network, Audio, Messages, Calendar).
     */
    private String buildSystemContextString() {
        SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault());
        SimpleDateFormat readableFormat = new SimpleDateFormat("EEEE, MMMM d, yyyy, h:mm a", Locale.getDefault());
        Date now = new Date();
        String isoTime = isoFormat.format(now);
        String readableTime = readableFormat.format(now);

        int battery = getBatteryPercentage();
        String batteryStr = (battery >= 0) ? battery + "%" : "Unknown";
        String network = getNetworkStatusString();
        String audioStatus = getCurrentAudioStatus();
        String unreadMsgs = getUnreadMessageCount();
        String nextEvent = getNextUpcomingCalendarEvent();

        StringBuilder rels = new StringBuilder();
        for (Map.Entry<String, String> entry : UserContextProfile.RELATIONSHIPS.entrySet()) {
            if (rels.length() > 0) rels.append(", ");
            rels.append(entry.getKey()).append("=").append(entry.getValue());
        }

        return "[SYSTEM CONTEXT: Current User: " + UserContextProfile.NAME + " (Formal: " + UserContextProfile.FULL_NAME + "). " +
               "Time: " + isoTime + " (" + readableTime + "). " +
               "Location: " + UserContextProfile.LOCATION + ". " +
               "Device: " + UserContextProfile.DEVICE + ". " +
               "Network: " + network + ". " +
               "Battery: " + batteryStr + ". " +
               "Audio Mode: " + audioStatus + ". " +
               "Messages: " + unreadMsgs + ". " +
               "Calendar: " + nextEvent + ". " +
               "Contacts/Entities: " + rels.toString() + "]";
    }

    /**
     * Step 5 Part 2: Retrieves current audio ringer mode and media volume percentage.
     */
    private String getCurrentAudioStatus() {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                int ringerMode = am.getRingerMode();
                if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
                    return "Silent";
                } else if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
                    return "Vibrate";
                } else {
                    int currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                    int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    int pct = (maxVol > 0) ? (currentVol * 100 / maxVol) : 0;
                    return "Normal (Media Volume: " + pct + "%)";
                }
            }
        } catch (Exception ignored) {}
        return "Normal";
    }

    /**
     * Step 5 Part 2: Safely queries unread SMS count if permission is granted.
     */
    private String getUnreadMessageCount() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                Cursor cursor = getContentResolver().query(
                    Uri.parse("content://sms/inbox"),
                    new String[]{"_id"},
                    "read = 0",
                    null,
                    null
                );
                if (cursor != null) {
                    int count = cursor.getCount();
                    cursor.close();
                    return count + " unread";
                }
            }
        } catch (Exception ignored) {}
        return "0 unread";
    }

    /**
     * Step 5 Part 2: Safely retrieves the next upcoming calendar event for today.
     */
    private String getNextUpcomingCalendarEvent() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                long nowMillis = System.currentTimeMillis();
                long endOfDayMillis = nowMillis + (24L * 60 * 60 * 1000);
                Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
                ContentUris.appendId(builder, nowMillis);
                ContentUris.appendId(builder, endOfDayMillis);
                Cursor cursor = getContentResolver().query(
                    builder.build(),
                    new String[]{CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN},
                    null,
                    null,
                    CalendarContract.Instances.BEGIN + " ASC"
                );
                if (cursor != null && cursor.moveToFirst()) {
                    String title = cursor.getString(0);
                    long begin = cursor.getLong(1);
                    cursor.close();
                    SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
                    return title + " at " + sdf.format(new Date(begin));
                }
                if (cursor != null) cursor.close();
            }
        } catch (Exception ignored) {}
        return "No upcoming events today";
    }

    private int getBatteryPercentage() {
        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = registerReceiver(null, ifilter);
            if (batteryStatus != null) {
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                return (scale > 0) ? (int) (level * 100f / scale) : level;
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private String getNetworkStatusString() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                if (activeNetwork != null && activeNetwork.isConnected()) {
                    if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                        return "Wi-Fi Connected";
                    } else if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                        return "Cellular/Mobile Data Connected";
                    } else {
                        return "Connected (" + activeNetwork.getTypeName() + ")";
                    }
                }
            }
        } catch (Exception ignored) {}
        return "Offline/Unknown";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().setDimAmount(0.0f);
        overridePendingTransition(R.anim.slide_up_assistant, 0);
        setContentView(R.layout.activity_assistant);
        Log.d(TAG, "Marvo Assistant Triggered via Hardware Button!");

        statusTextView = findViewById(R.id.statusTextView);
        subtitleTextView = findViewById(R.id.subtitleTextView);
        statusScrollView = findViewById(R.id.statusScrollView);
        orbWebView = findViewById(R.id.orbWebView);
        if (orbWebView != null) {
            orbWebView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            orbWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        dynamicPillContainer = findViewById(R.id.dynamicPillContainer);
        pillIcon = findViewById(R.id.pillIcon);
        pillText = findViewById(R.id.pillText);

        // Step 2/20: Anti-Shift - Constrain maximum height of response scroll container to 180dp
        if (statusScrollView != null) {
            final float density = getResources().getDisplayMetrics().density;
            final int maxPx = (int) (180 * density);
            statusScrollView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (statusScrollView != null && statusScrollView.getHeight() > maxPx) {
                        ViewGroup.LayoutParams lp = statusScrollView.getLayoutParams();
                        if (lp != null && lp.height != maxPx) {
                            lp.height = maxPx;
                            statusScrollView.setLayoutParams(lp);
                        }
                    }
                }
            });
        }

        // Initialize WebGL Siri Fluid Orb (Step 9)
        initOrbWebView();

        // Load Gemini API Key from .env asset
        loadGeminiApiKey();

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

        initTTS();
        initSpeechRecognizer();
        offlineIntentRouter = new OfflineIntentRouter(this);
        initAudioPolling();
        checkPermissionAndStart();
    }

    /**
     * Step 7 - Part 2: 50ms Live Mic Amplitude Polling Loop & WebGL Bridge.
     */
    private void initAudioPolling() {
        audioPollRunnable = new Runnable() {
            @Override
            public void run() {
                if (orbWebView != null) {
                    if (targetAudioAmplitude > currentAudioAmplitude) {
                        currentAudioAmplitude += (targetAudioAmplitude - currentAudioAmplitude) * 0.45f;
                    } else {
                        currentAudioAmplitude += (targetAudioAmplitude - currentAudioAmplitude) * 0.15f;
                    }
                    if (currentAudioAmplitude < 0.01f) {
                        currentAudioAmplitude = 0.0f;
                    }
                    final float ampToSend = currentAudioAmplitude;
                    orbWebView.evaluateJavascript("if(window.updateOrbAmplitude){window.updateOrbAmplitude(" + ampToSend + ");}else if(window.setAmplitude){window.setAmplitude(" + ampToSend + ");}", null);
                }
                if (audioPollHandler != null) {
                    audioPollHandler.postDelayed(this, 50);
                }
            }
        };
        audioPollHandler.postDelayed(audioPollRunnable, 50);
    }

    private void initTTS() {
        try {
            tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (status == TextToSpeech.SUCCESS) {
                        Locale hindiLocale = new Locale("hi", "IN");
                        int result = tts.setLanguage(hindiLocale);
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.w(TAG, "Hindi locale hi_IN not supported, falling back to default/US");
                            result = tts.setLanguage(Locale.getDefault());
                            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                                tts.setLanguage(Locale.US);
                            }
                        }

                        // Search for a male Hindi voice in tts.getVoices()
                        boolean foundMaleVoice = false;
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                java.util.Set<android.speech.tts.Voice> voices = tts.getVoices();
                                if (voices != null) {
                                    for (android.speech.tts.Voice voice : voices) {
                                        if (voice != null && voice.getLocale() != null) {
                                            String lang = voice.getLocale().getLanguage();
                                            String country = voice.getLocale().getCountry();
                                            String name = voice.getName() != null ? voice.getName().toLowerCase() : "";
                                            boolean isHindi = "hi".equalsIgnoreCase(lang) ||
                                                ("hi".equalsIgnoreCase(lang) && "IN".equalsIgnoreCase(country));
                                            if (isHindi && name.contains("male")) {
                                                tts.setVoice(voice);
                                                foundMaleVoice = true;
                                                Log.d(TAG, "Selected male Hindi TTS voice: " + voice.getName());
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Could not configure male voice: " + e.getMessage());
                        }

                        // Voice fallback: lower pitch for deep, calm male voice and adjust cadence
                        if (foundMaleVoice) {
                            tts.setPitch(0.90f);
                            tts.setSpeechRate(0.95f);
                        } else {
                            tts.setPitch(0.85f);
                            tts.setSpeechRate(0.95f);
                            Log.d(TAG, "Male voice not explicitly found; applied pitch 0.85f and rate 0.95f fallback");
                        }

                        isTtsReady = true;
                        Log.d(TAG, "TTS initialized successfully with Hindi locale");

                        // Step 9: UtteranceProgressListener for orb state + auto-close
                        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                            @Override
                            public void onStart(String utteranceId) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        setOrbState("SPEAKING");
                                    }
                                });
                            }
                            @Override
                            public void onDone(final String utteranceId) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (typewriterRunnable != null) {
                                            typewriterHandler.removeCallbacks(typewriterRunnable);
                                        }
                                        if (utteranceId != null && utteranceId.startsWith("SPEAK_AND_LISTEN")) {
                                            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (!isFinishing()) {
                                                        setVisualState("LISTENING");
                                                        startListening();
                                                    }
                                                }
                                            }, 200);
                                        } else {
                                            setOrbState("IDLE");
                                        }
                                    }
                                });
                            }
                            @Override
                            public void onError(final String utteranceId) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (utteranceId != null && utteranceId.startsWith("SPEAK_AND_LISTEN")) {
                                            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (!isFinishing()) {
                                                        setVisualState("LISTENING");
                                                        startListening();
                                                    }
                                                }
                                            }, 200);
                                        } else {
                                            setOrbState("IDLE");
                                        }
                                    }
                                });
                            }
                        });
                    } else {
                        Log.e(TAG, "TTS initialization failed: " + status);
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TTS: " + e.getMessage(), e);
        }
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
            public void onRmsChanged(float rmsdB) {
                // Normalize rmsdB (-2 to ~10 dB) into 0.0 to 1.0 range for live fluid orb audio-reactivity
                float normalized = Math.max(0.0f, Math.min(1.0f, (rmsdB + 2.0f) / 12.0f));
                targetAudioAmplitude = normalized;
                setOrbAmplitude(normalized);
            }

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                Log.d(TAG, "SpeechRecognizer onEndOfSpeech: Silence detected");
                targetAudioAmplitude = 0.0f;
                setVisualState("PROCESSING");
            }

            @Override
            public void onError(int error) {
                Log.w(TAG, "SpeechRecognizer onError code: " + error);
                setVisualState("ERROR");
                // Revert to IDLE state instead of auto-dismissing
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing()) {
                            setOrbState("IDLE");
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

                    // Route through local intent router with persistent feedback
                    routeCommand(transcribed);
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

    // ============================================================
    // STEP 10: Dynamic Apple Pill UI & Smart Contact Engine
    // ============================================================

    /**
     * Step 10 Part 1: Apple-style Dynamic Notification Pill.
     * Slides down a sleek frosted translucent pill with icon and action text.
     * Automatically slides up and dismisses after exactly 3000ms without closing the assistant activity.
     */
    void showDynamicPill(final String message, final int iconResId) {
        if (message == null) return;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (dynamicPillContainer == null) return;

                if (pillDismissRunnable != null) {
                    pillHandler.removeCallbacks(pillDismissRunnable);
                }

                if (pillText != null) {
                    pillText.setText(message);
                }
                if (pillIcon != null) {
                    if (iconResId != 0) {
                        pillIcon.setImageResource(iconResId);
                        pillIcon.setVisibility(View.VISIBLE);
                    } else {
                        pillIcon.setVisibility(View.GONE);
                    }
                }

                if (dynamicPillContainer.getVisibility() != View.VISIBLE) {
                    dynamicPillContainer.setVisibility(View.VISIBLE);
                    Animation slideDown = AnimationUtils.loadAnimation(AssistantActivity.this, R.anim.pill_slide_down);
                    dynamicPillContainer.startAnimation(slideDown);
                }

                pillDismissRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (dynamicPillContainer != null && dynamicPillContainer.getVisibility() == View.VISIBLE) {
                            Animation slideUp = AnimationUtils.loadAnimation(AssistantActivity.this, R.anim.pill_slide_up);
                            slideUp.setAnimationListener(new Animation.AnimationListener() {
                                @Override
                                public void onAnimationStart(Animation animation) {}
                                @Override
                                public void onAnimationEnd(Animation animation) {
                                    dynamicPillContainer.setVisibility(View.GONE);
                                }
                                @Override
                                public void onAnimationRepeat(Animation animation) {}
                            });
                            dynamicPillContainer.startAnimation(slideUp);
                        }
                    }
                };
                pillHandler.postDelayed(pillDismissRunnable, 3000);
            }
        });
    }

    void showDynamicPill(final String message) {
        showDynamicPill(message, android.R.drawable.ic_lock_silent_mode_off);
    }

    // Step 10 Part 2 & 3: Relationship Aliases & Favorites Engine
    private static final Map<String, List<String>> RELATIONSHIP_ALIASES = new HashMap<>();
    private static final Set<String> FAVORITE_CONTACTS = new HashSet<>(
        Arrays.asList("papa", "bapa", "maa", "mummy", "mom", "jatin")
    );

    static {
        List<String> papaList = Arrays.asList("papa", "bapa", "dad", "father");
        RELATIONSHIP_ALIASES.put("dad", papaList);
        RELATIONSHIP_ALIASES.put("father", papaList);
        RELATIONSHIP_ALIASES.put("daddy", papaList);
        RELATIONSHIP_ALIASES.put("papa", papaList);
        RELATIONSHIP_ALIASES.put("bapa", papaList);
        RELATIONSHIP_ALIASES.put("pitaji", papaList);

        List<String> momList = Arrays.asList("maa", "mummy", "mom", "mother");
        RELATIONSHIP_ALIASES.put("mom", momList);
        RELATIONSHIP_ALIASES.put("mother", momList);
        RELATIONSHIP_ALIASES.put("mommy", momList);
        RELATIONSHIP_ALIASES.put("maa", momList);
        RELATIONSHIP_ALIASES.put("mummy", momList);
        RELATIONSHIP_ALIASES.put("mataji", momList);

        List<String> brotherList = Arrays.asList("jatin", "brother", "bhai", "bhaiya");
        RELATIONSHIP_ALIASES.put("brother", brotherList);
        RELATIONSHIP_ALIASES.put("bro", brotherList);
        RELATIONSHIP_ALIASES.put("bhai", brotherList);
        RELATIONSHIP_ALIASES.put("bhaiya", brotherList);
        RELATIONSHIP_ALIASES.put("jatin", brotherList);
    }

    public static class ContactMatch {
        public String queryTerm;
        public String matchedDisplayName;
        public String phoneNumber;
        public boolean isFavorite;

        public ContactMatch(String queryTerm, String matchedDisplayName, String phoneNumber, boolean isFavorite) {
            this.queryTerm = queryTerm;
            this.matchedDisplayName = matchedDisplayName;
            this.phoneNumber = phoneNumber;
            this.isFavorite = isFavorite;
        }
    }

    /**
     * Cleans common query prefixes/suffixes (e.g., "my brother" -> "brother").
     */
    private String cleanContactQuery(String raw) {
        if (raw == null) return "";
        String s = raw.toLowerCase().trim();
        String[] prefixes = new String[]{
            "to my ", "to ", "my ", "mera ", "meri ", "apne ", "apna ", "call to ", "call "
        };
        for (String p : prefixes) {
            if (s.startsWith(p)) {
                s = s.substring(p.length()).trim();
            }
        }
        String[] suffixes = new String[]{
            " ko call karo", " ko call lagao", " ko phone karo", " ko call", " ko phone", " ko", " please"
        };
        for (String sf : suffixes) {
            if (s.endsWith(sf)) {
                s = s.substring(0, s.length() - sf.length()).trim();
            }
        }
        return s;
    }

    /**
     * Resolves contact with relationship aliasing and favorites matching.
     */
    private ContactMatch lookupContactWithAliasing(String rawQuery) {
        if (rawQuery == null || rawQuery.trim().isEmpty()) return null;

        String cleaned = cleanContactQuery(rawQuery);
        if (cleaned.isEmpty()) cleaned = rawQuery.trim().toLowerCase();

        // Check if direct phone number (digits)
        String digitsOnly = cleaned.replaceAll("[^0-9+]", "");
        if (digitsOnly.length() >= 7) {
            return new ContactMatch(cleaned, cleaned, digitsOnly, false);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_CONTACTS permission not granted, requesting now");
            ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS},
                PERMISSION_REQUEST_CONTACTS_CALL
            );
            return null;
        }

        // Determine search candidates
        List<String> candidates;
        if (RELATIONSHIP_ALIASES.containsKey(cleaned)) {
            candidates = RELATIONSHIP_ALIASES.get(cleaned);
        } else {
            candidates = new ArrayList<>();
            candidates.add(cleaned);
        }

        // Check if any candidate is directly marked as favorite
        boolean candidateIsFavorite = false;
        for (String c : candidates) {
            if (FAVORITE_CONTACTS.contains(c.toLowerCase())) {
                candidateIsFavorite = true;
                break;
            }
        }

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

                // Pass 1: Check exact matches first
                while (cursor.moveToNext()) {
                    String dName = cursor.getString(nameIndex);
                    String number = cursor.getString(numberIndex);
                    if (dName != null && number != null) {
                        String lowerDName = dName.toLowerCase().trim();
                        for (String cand : candidates) {
                            if (lowerDName.equals(cand)) {
                                boolean isFav = candidateIsFavorite || FAVORITE_CONTACTS.contains(lowerDName);
                                cursor.close();
                                return new ContactMatch(cand, dName, number, isFav);
                            }
                        }
                    }
                }

                // Pass 2: Check contains matches
                cursor.moveToPosition(-1);
                while (cursor.moveToNext()) {
                    String dName = cursor.getString(nameIndex);
                    String number = cursor.getString(numberIndex);
                    if (dName != null && number != null) {
                        String lowerDName = dName.toLowerCase().trim();
                        for (String cand : candidates) {
                            if (lowerDName.contains(cand) || cand.contains(lowerDName)) {
                                boolean isFav = candidateIsFavorite || FAVORITE_CONTACTS.contains(lowerDName);
                                cursor.close();
                                return new ContactMatch(cand, dName, number, isFav);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying contacts: " + e.getMessage(), e);
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }

        return null;
    }

    /**
     * Universal Contact Resolver: delegates to lookupContactWithAliasing.
     */
    private String getPhoneNumber(String contactName) {
        ContactMatch match = lookupContactWithAliasing(contactName);
        return match != null ? match.phoneNumber : null;
    }

    /**
     * Initiates a native phone call to the given phone number.
     */
    void makeCall(String phoneNumber) {
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
     * Step 5 - Part 3: Builds a WhatsApp Intent with pre-filled recipient and text.
     */
    private Intent buildWhatsAppIntent(String phoneNumber, String message) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) return null;
        String cleanNumber = phoneNumber.replaceAll("[\\s-()]", "");
        if (!cleanNumber.startsWith("+")) {
            if (cleanNumber.startsWith("91") && cleanNumber.length() == 12) {
                cleanNumber = "+" + cleanNumber;
            } else {
                cleanNumber = "+91" + cleanNumber;
            }
        }
        String encodedText = "";
        try {
            encodedText = URLEncoder.encode(message != null ? message : "", "UTF-8");
        } catch (Exception ignored) {
            encodedText = (message != null ? message : "");
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("https://api.whatsapp.com/send?phone=" + cleanNumber + "&text=" + encodedText));
        intent.setPackage("com.whatsapp");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(getPackageManager()) == null) {
            intent.setPackage(null); // Fallback to browser or any compatible handler
        }
        return intent;
    }

    /**
     * Step 5 - Part 3: Builds an Email Intent with pre-filled recipient, subject, and body.
     */
    private Intent buildEmailIntent(String recipient, String subject, String body) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:" + (recipient != null ? recipient.trim() : "")));
        if (subject != null && !subject.trim().isEmpty()) {
            intent.putExtra(Intent.EXTRA_SUBJECT, subject.trim());
        }
        if (body != null && !body.trim().isEmpty()) {
            intent.putExtra(Intent.EXTRA_TEXT, body.trim());
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    /**
     * Step 5 - Part 3: Opens Instagram app to Direct Messages, specific user profile, or home feed.
     */
    private void openInstagram(String query) {
        String lower = (query != null) ? query.toLowerCase().trim() : "";
        Intent intent;
        if (lower.contains("dm") || lower.contains("direct") || lower.contains("message")) {
            Uri directUri = Uri.parse("https://instagram.com/direct/inbox/");
            intent = new Intent(Intent.ACTION_VIEW, directUri);
            intent.setPackage("com.instagram.android");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(getPackageManager()) == null) {
                intent = new Intent(Intent.ACTION_VIEW, directUri);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
        } else if (lower.contains("profile") || lower.contains("user")) {
            String username = query.replaceAll("(?i).*(?:profile of|user|profile)\\s*", "").trim();
            if (username.startsWith("@")) username = username.substring(1);
            Uri profileUri = Uri.parse("https://instagram.com/" + (!username.isEmpty() ? username : ""));
            intent = new Intent(Intent.ACTION_VIEW, profileUri);
            intent.setPackage("com.instagram.android");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(getPackageManager()) == null) {
                intent = new Intent(Intent.ACTION_VIEW, profileUri);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
        } else {
            PackageManager pm = getPackageManager();
            intent = pm.getLaunchIntentForPackage("com.instagram.android");
            if (intent == null) {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/"));
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }

        try {
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening Instagram: " + e.getMessage(), e);
        }
    }

    /**
     * Step 5 - Part 3: Triggers the native Android account creation intent for Google Account.
     */
    private void openAddGoogleAccount() {
        try {
            Intent intent = new Intent(Settings.ACTION_ADD_ACCOUNT);
            intent.putExtra(Settings.EXTRA_ACCOUNT_TYPES, new String[]{"com.google"});
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Settings.ACTION_SYNC_SETTINGS);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(fallback);
            } catch (Exception ex) {
                Log.e(TAG, "Error opening account settings: " + ex.getMessage(), ex);
            }
        }
    }

    /**
     * Step 5 - Part 4: Native Turn-by-Turn Navigation Intent via Google Maps
     */
    void startNavigation(String destination) {
        if (destination == null || destination.trim().isEmpty()) return;
        try {
            Uri gmmIntentUri = Uri.parse("google.navigation:q=" + Uri.encode(destination.trim()));
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps");
            mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(mapIntent);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Google Maps app not found, falling back to web navigation: " + e.getMessage());
            try {
                Uri webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(destination.trim()));
                Intent webIntent = new Intent(Intent.ACTION_VIEW, webUri);
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(webIntent);
            } catch (Exception ex) {
                Log.e(TAG, "Error opening web navigation fallback: " + ex.getMessage(), ex);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error launching navigation: " + e.getMessage(), e);
        }
    }

    /**
     * Step 5 - Part 4: Native Media & Music Playback Intent
     */
    void playMedia(String query, String targetPlatform) {
        if (query == null || query.trim().isEmpty()) query = "top songs";
        try {
            Intent intent = new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH);
            intent.putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/audio");
            intent.putExtra(SearchManager.QUERY, query);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if ("spotify".equalsIgnoreCase(targetPlatform)) {
                intent.setPackage("com.spotify.music");
            } else if ("youtube".equalsIgnoreCase(targetPlatform)) {
                intent.setPackage("com.google.android.youtube");
            }

            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "Preferred media player not found, falling back: " + e.getMessage());
            try {
                Uri ytUri = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query));
                Intent fallback = new Intent(Intent.ACTION_VIEW, ytUri);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(fallback);
            } catch (Exception ex) {
                Log.e(TAG, "Error opening music fallback: " + ex.getMessage(), ex);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing media: " + e.getMessage(), e);
        }
    }

    /**
     * Step 5 - Part 4: Builds a Calendar Insert Intent.
     */
    private Intent buildCalendarIntent(String title, String description, long beginTimeMillis, long endTimeMillis) {
        Intent intent = new Intent(Intent.ACTION_INSERT);
        intent.setData(CalendarContract.Events.CONTENT_URI);
        intent.putExtra(CalendarContract.Events.TITLE, title != null ? title : "Reminder");
        if (description != null && !description.isEmpty()) {
            intent.putExtra(CalendarContract.Events.DESCRIPTION, description);
        }
        if (beginTimeMillis > 0) {
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginTimeMillis);
        }
        if (endTimeMillis > 0) {
            intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTimeMillis);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    /**
     * Step 5 - Part 3: Double-Confirmation Security Protocol (State Machine)
     * Holds the pending Intent in memory, sets the Siri Orb to CONFIRMATION state (pulsating Amber/Gold),
     * speaks a bilingual confirmation query ("I have drafted a message to [Name] saying: [Message]. Do you want me to send it?"),
     * renders the Dynamic Notification Pill with warning icon, and listens for the user's voice response.
     */
    private void requestDoubleConfirmation(final String actionType, final String recipientName, final String messagePayload, final Intent intent, boolean isHindi) {
        this.pendingActionType = actionType;
        this.pendingIntent = intent;
        this.pendingRecipientName = recipientName;
        this.pendingDraftContent = messagePayload;

        // Step 4 of UI Sync: Switch Siri Orb to pulsating Orange/Yellow CONFIRMATION state
        setOrbState("CONFIRMATION");

        // Format bilingual prompt
        String promptText;
        if ("CALENDAR_DRAFT".equals(actionType)) {
            if (isHindi) {
                promptText = "Maine \"" + messagePayload + "\" ke liye reminder taiyar kiya hai. Kya main ise save kar doon?";
            } else {
                promptText = "I have set up a reminder for " + messagePayload + ". Do you want me to save it?";
            }
        } else if (isHindi) {
            promptText = "Maine " + recipientName + " ke liye message taiyar kiya hai: \"" + messagePayload + "\". Kya main ise bhej doon?";
        } else {
            promptText = "I have drafted a message to " + recipientName + " saying: \"" + messagePayload + "\". Do you want me to send it?";
        }

        // Display on UI
        if (statusTextView != null) {
            statusTextView.setText(promptText);
        }
        if (subtitleTextView != null) {
            if ("CALENDAR_DRAFT".equals(actionType)) {
                subtitleTextView.setText("Event: " + messagePayload);
            } else {
                subtitleTextView.setText("To: " + recipientName + " | \"" + messagePayload + "\"");
            }
            subtitleTextView.setVisibility(View.VISIBLE);
        }

        // Show Dynamic Notification Pill
        if ("CALENDAR_DRAFT".equals(actionType)) {
            showDynamicPill("Reminder: " + messagePayload, android.R.drawable.ic_menu_my_calendar);
        } else {
            showDynamicPill("Confirmation: " + recipientName, android.R.drawable.ic_dialog_alert);
        }

        // Speak via Android TTS
        if (tts != null && isTtsReady) {
            try {
                Bundle params = new Bundle();
                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "DoubleConfirmTTS");
                tts.speak(promptText, TextToSpeech.QUEUE_FLUSH, params, "DoubleConfirmTTS");
            } catch (Exception e) {
                try {
                    tts.speak(promptText, TextToSpeech.QUEUE_FLUSH, null);
                } catch (Exception ignored) {}
            }
        }

        // Activate microphone automatically after TTS delivers the prompt
        long speechDelay = Math.max(2200L, promptText.split("\\s+").length * 280L);
        startListeningDelayed(speechDelay);
    }

    /**
     * Dispatches a message to WhatsApp via deep-link Intent.
     */
    private void sendWhatsAppMessage(String phoneNumber, String message) {
        Intent intent = buildWhatsAppIntent(phoneNumber, message);
        if (intent != null) {
            try {
                startActivity(intent);
                finishDelayed(2500);
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
}

    /**
     * Sets a countdown timer in the system Clock app.
     */
    void setTimer(int seconds) {
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
    void setAlarm(int hour, int minute, String message) {
        try {
            Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
            intent.putExtra(AlarmClock.EXTRA_HOUR, hour);
            intent.putExtra(AlarmClock.EXTRA_MINUTES, minute);
            intent.putExtra(AlarmClock.EXTRA_MESSAGE, message != null ? message : "Marvo Alarm");
            intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finishDelayed(2500);
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
     * Opens the camera app (standard back camera or front selfie camera).
     */
    private void openCamera(boolean frontFacing) {
        try {
            Intent intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
            if (frontFacing) {
                intent.putExtra("android.intent.extras.CAMERA_FACING", 1);
                intent.putExtra("android.intent.extra.USE_FRONT_CAMERA", true);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            try {
                Intent fallback = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(fallback);
            } catch (Exception ex) {
                Log.w(TAG, "Camera app not found: " + ex.getMessage());
                if (statusTextView != null) {
                    statusTextView.setText("Camera app not found.");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening camera: " + e.getMessage(), e);
            if (statusTextView != null) {
                statusTextView.setText("Failed to open camera.");
            }
        }
    }

    /**
     * Opens the camera app facing the front (selfie) camera.
     */
    private void openSelfieCamera() {
        openCamera(true);
        finishDelayed(2500);
    }

    /**
     * Step 5 Part 2: Opens the device sound / voice recorder app.
     */
    private void openAudioRecorder() {
        boolean launched = false;
        try {
            Intent intent = new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            launched = true;
        } catch (Exception e) {
            try {
                Intent fallback = new Intent("android.provider.MediaStore.RECORD_SOUND");
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(fallback);
                launched = true;
            } catch (Exception ignored) {}
        }

        if (!launched) {
            String[] recorderPackages = new String[]{
                "com.motorola.soundrecorder",
                "com.google.android.apps.recorder",
                "com.android.soundrecorder",
                "com.sec.android.app.voicenote"
            };
            PackageManager pm = getPackageManager();
            for (String pkg : recorderPackages) {
                try {
                    Intent launchIntent = pm.getLaunchIntentForPackage(pkg);
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(launchIntent);
                        launched = true;
                        break;
                    }
                } catch (Exception ignored) {}
            }
        }

        if (!launched) {
            Log.w(TAG, "No sound recorder app found on device");
            if (statusTextView != null) {
                statusTextView.setText("Voice recorder app not found.");
            }
        }
    }

    /**
     * Toggles the device's hardware rear flashlight on or off.
     */
    void toggleFlashlight(boolean state) {
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
                    showDynamicPill(state ? "Flashlight Turned ON" : "Flashlight Turned OFF", android.R.drawable.ic_menu_camera);
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
                    showDynamicPill("Volume Increased", android.R.drawable.stat_sys_speakerphone);
                } else if ("down".equalsIgnoreCase(action)) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                    showDynamicPill("Volume Decreased", android.R.drawable.stat_sys_speakerphone);
                } else if ("mute".equalsIgnoreCase(action)) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI);
                    showDynamicPill("Volume Muted", android.R.drawable.ic_lock_silent_mode);
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
     * Step 9: Centralized Visual State Engine (upgraded for WebGL Orb).
     * Controls orb state via JavaScript bridge, status/subtitle text,
     * based on the current assistant lifecycle state.
     */
    private void setVisualState(String state) {
        if (statusScrollView != null) {
            ViewGroup.LayoutParams lp = statusScrollView.getLayoutParams();
            if (lp != null && lp.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                statusScrollView.setLayoutParams(lp);
            }
        }
        switch (state) {
            case "LISTENING":
                if (statusTextView != null) {
                    statusTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20.0f);
                    statusTextView.setText("Listening...");
                    statusTextView.setTextColor(android.graphics.Color.WHITE);
                }
                if (subtitleTextView != null) {
                    subtitleTextView.setText("Speak now...");
                    subtitleTextView.setVisibility(View.VISIBLE);
                }
                setOrbState("LISTENING");
                break;

            case "PROCESSING":
                if (statusTextView != null) {
                    statusTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20.0f);
                    statusTextView.setText("Processing...");
                    statusTextView.setTextColor(android.graphics.Color.parseColor("#B0B0B0"));
                }
                if (subtitleTextView != null) {
                    subtitleTextView.setText("Analyzing command...");
                    subtitleTextView.setVisibility(View.VISIBLE);
                }
                setOrbState("THINKING");
                break;

            case "SUCCESS":
                if (statusTextView != null) {
                    statusTextView.setTextColor(android.graphics.Color.parseColor("#00E676"));
                }
                if (subtitleTextView != null) {
                    subtitleTextView.setVisibility(View.GONE);
                }
                setOrbState("IDLE");
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
                setOrbState("IDLE");
                break;

            default:
                break;
        }
    }

    /**
     * Thread-Safe Output & TTS Engine (Step 8 Part 1):
     * Updates the UI TextView instantly on the main thread via runOnUiThread(),
     * and speaks out the response text via TextToSpeech if shouldSpeak is true.
     */
    void showResponse(final String message, final boolean shouldSpeak) {
        if (message == null) return;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (statusScrollView != null) {
                    ViewGroup.LayoutParams lp = statusScrollView.getLayoutParams();
                    if (lp != null && lp.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                        statusScrollView.setLayoutParams(lp);
                    }
                }
                if (statusTextView != null) {
                    statusTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18.0f);
                    statusTextView.setText(message);
                }
                if (subtitleTextView != null) {
                    subtitleTextView.setVisibility(View.GONE);
                }
                String lower = message.toLowerCase();
                if (lower.contains("confirmed") || lower.contains("sent") || lower.contains("calling") ||
                    lower.contains("turned on") || lower.contains("turned off") || lower.contains("saved") ||
                    lower.contains("opened") || lower.contains("alright") || lower.contains("adjusting") ||
                    lower.contains("toggling") || lower.contains("launching") || lower.contains("battery") ||
                    lower.contains("order saved") || lower.contains("note saved") || lower.contains("stock updated") ||
                    lower.contains("success")) {
                    setVisualState("SUCCESS");
                }
                if (shouldSpeak && tts != null && isTtsReady) {
                    try {
                        Bundle params = new Bundle();
                        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "MarvoTTS");
                        tts.speak(message.replace('\n', ' '), TextToSpeech.QUEUE_FLUSH, params, "MarvoTTS");
                    } catch (Exception e) {
                        try {
                            tts.speak(message.replace('\n', ' '), TextToSpeech.QUEUE_FLUSH, null);
                        } catch (Exception ignored) {}
                    }
                }
            }
        });
    }

    void showResponse(String message) {
        showResponse(message, true);
    }

    /**
     * Step 8: Verbal confirmation prompt with auto-listening state machine.
     * Speaks the prompt via TTS and automatically arms SpeechRecognizer 200ms after speech ends.
     */
    void speakAndListen(final String text) {
        speakAndListen(text, null, null);
    }

    void speakAndListen(final String text, final String pendingActionId) {
        speakAndListen(text, null, pendingActionId);
    }

    void speakAndListen(final String text, final String overlayText, final String pendingActionId) {
        if (text == null) return;
        this.pendingActionType = pendingActionId;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (statusTextView != null) {
                    statusTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18.0f);
                    statusTextView.setText(text);
                }
                if (overlayText != null && subtitleTextView != null) {
                    subtitleTextView.setText(overlayText);
                    subtitleTextView.setVisibility(View.VISIBLE);
                } else if (subtitleTextView != null) {
                    subtitleTextView.setVisibility(View.GONE);
                }
                setOrbState("CONFIRMATION");
                if (tts != null && isTtsReady) {
                    try {
                        Bundle params = new Bundle();
                        String uId = "SPEAK_AND_LISTEN_" + (pendingActionId != null ? pendingActionId : "DEFAULT");
                        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, uId);
                        int res = tts.speak(text.replace('\n', ' '), TextToSpeech.QUEUE_FLUSH, params, uId);
                        if (res != TextToSpeech.SUCCESS) {
                            startListeningDelayed(2000);
                        }
                    } catch (Exception e) {
                        try {
                            tts.speak(text.replace('\n', ' '), TextToSpeech.QUEUE_FLUSH, null);
                        } catch (Exception ignored) {}
                        startListeningDelayed(2000);
                    }
                } else {
                    startListeningDelayed(1500);
                }
            }
        });
    }

    private void updateUI(String text) {
        showResponse(text, true);
    }

    private void finishDelayed(long delayMillis) {
        // Step 6 - Part 2: Never auto-dismiss. Revert orb to IDLE state so the assistant
        // floats gently on screen until the user manually swipes or taps outside.
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing()) {
                    setOrbState("IDLE");
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
     * Performs a web search on Google using ACTION_WEB_SEARCH or browser fallback.
     */
    private void searchWeb(String query) {
        if (query == null || query.trim().isEmpty()) return;
        try {
            Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
            intent.putExtra(SearchManager.QUERY, query.trim());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            try {
                String encoded = URLEncoder.encode(query.trim(), "UTF-8");
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + encoded));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception ex) {
                Log.e(TAG, "Error searching web: " + ex.getMessage(), ex);
                showResponse("Web search unavailable.", true);
            }
        }
    }

    /**
     * Opens Google News.
     */
    private void openNews() {
        searchWeb("today's latest news");
    }

    /**
     * Opens weather forecast on Google.
     */
    private void openWeather() {
        searchWeb("today's weather forecast");
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
            return "https://www.myntra.com/search?p=" + URLEncoder.encode(query != null ? query.trim() : "", "UTF-8");
        } catch (Exception e) {
            return "https://www.myntra.com/search?p=" + (query != null ? query.trim() : "");
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
    void launchAppByName(String appName) {
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
     * Step 7 - Part 4: Native Battery Level Reader with Hindi Vocal Output.
     */
    void getDeviceBatteryLevel() {
        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = registerReceiver(null, ifilter);
            if (batteryStatus != null) {
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                float batteryPct = (scale > 0) ? (level * 100 / (float) scale) : level;
                boolean isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL);
                String hindiSpeech = "Aapke phone ki battery " + (int) batteryPct + " percent hai" + (isCharging ? ", aur phone charge ho raha hai." : ".");
                showDynamicPill("Battery: " + (int) batteryPct + "%", isCharging ? android.R.drawable.ic_lock_idle_charging : android.R.drawable.ic_lock_idle_low_battery);
                showResponse(hindiSpeech, true);
                setOrbState("IDLE");
            } else {
                showResponse("Battery status check nahi kiya ja saka.", true);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading battery: " + e.getMessage(), e);
            showResponse("Battery check karne mein samasya aayi.", true);
        }
    }

    /**
     * Step 7 - Part 4: Deep Hardware Volume Controller (Raise / Lower).
     */
    void adjustDeviceVolume(int direction) {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
                String msg = (direction == AudioManager.ADJUST_RAISE)
                    ? "Volume badha diya gaya hai."
                    : (direction == AudioManager.ADJUST_LOWER ? "Volume kam kar diya gaya hai." : "Volume set kar diya gaya hai.");
                showDynamicPill(direction == AudioManager.ADJUST_RAISE ? "Volume Up" : "Volume Down", android.R.drawable.ic_lock_silent_mode_off);
                showResponse(msg, true);
                setOrbState("IDLE");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error adjusting volume: " + e.getMessage(), e);
            showResponse("Volume change karne mein samasya aayi.", true);
        }
    }

    /**
     * Step 7 - Part 4: Deep Hardware Ringer / Mute Controller.
     */
    void setDeviceMute(boolean mute) {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                if (mute) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI);
                    showDynamicPill("Muted", android.R.drawable.ic_lock_silent_mode);
                    showResponse("Phone ko silent kar diya gaya hai.", true);
                } else {
                    int defaultVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 2;
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, defaultVol, AudioManager.FLAG_SHOW_UI);
                    showDynamicPill("Unmuted", android.R.drawable.ic_lock_silent_mode_off);
                    showResponse("Volume on kar diya gaya hai.", true);
                }
                setOrbState("IDLE");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting mute: " + e.getMessage(), e);
            showResponse("Volume change karne mein samasya aayi.", true);
        }
    }

    /**
     * Step 5 - Part 2: The "Toolbox Catalog" Router (Offline Native Execution).
     * Intercepts native Android device commands (Camera, Audio Recorder, Alarms)
     * using regex/keyword matching, executes them locally with immediate TTS and Dynamic Pill,
     * sets the Siri Orb to SPEAKING, and bypasses the Gemini network call.
     *
     * @param command The spoken or entered user query
     * @return true if handled by a local tool, false to continue routing
     */
    private boolean handleLocalCommand(String command) {
        if (command == null || command.trim().isEmpty()) return false;
        final String lower = command.trim().toLowerCase();

        // 1. Camera Native Intent Matcher
        if (lower.matches(".*\\b(open camera|launch camera|start camera|take a photo|take a picture|take photo|take picture|click photo|click a picture|click picture|take selfie|take a selfie|open selfie|open front camera|camera kholo|photo khicho|selfie lo|selfie khicho)\\b.*") ||
            lower.equals("camera") || lower.equals("selfie") || lower.equals("open camera")) {
            final boolean isFront = lower.contains("selfie") || lower.contains("front");
            final String ttsMsg = isFront ? "Opening selfie camera..." : "Opening camera...";
            setOrbState("SPEAKING");
            showDynamicPill(isFront ? "Opening Selfie Camera" : "Opening Camera", android.R.drawable.ic_menu_camera);
            showResponse(ttsMsg, true);
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    openCamera(isFront);
                    finishDelayed(2500);
                }
            }, 300);
            return true;
        }

        // 2. Audio Recorder Native Intent Matcher
        if (lower.matches(".*\\b(record audio|start voice recorder|open voice recorder|start recorder|open recorder|record voice|voice recorder|sound recorder|voice memo|audio recording|start recording|voice record karo|awaz record karo)\\b.*") ||
            lower.equals("recorder") || lower.equals("record audio") || lower.equals("voice recorder")) {
            final String ttsMsg = "Starting voice recorder...";
            setOrbState("SPEAKING");
            showDynamicPill("Starting Voice Recorder", android.R.drawable.ic_btn_speak_now);
            showResponse(ttsMsg, true);
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    openAudioRecorder();
                    finishDelayed(2500);
                }
            }, 300);
            return true;
        }

        // 3. Alarm Native Intent Matcher
        if (lower.matches(".*\\b(set alarm|wake me up|alarm lagao|set an alarm|alarm for|wake me at)\\b.*") ||
            (lower.contains("alarm") && (lower.contains("am") || lower.contains("pm") || lower.matches(".*\\d+.*")))) {
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
            final int fHour = hour;
            final int fMinute = minute;
            final String timeDisplay = String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
            final String ttsMsg = "Setting alarm for " + timeDisplay + "...";
            setOrbState("SPEAKING");
            showDynamicPill("Alarm: " + timeDisplay, android.R.drawable.ic_lock_idle_alarm);
            showResponse(ttsMsg, true);
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    setAlarm(fHour, fMinute, "Marvo Alarm");
                }
            }, 300);
            return true;
        }

        // 4. Instagram Intent Matcher
        if (lower.matches(".*\\b(open instagram|launch instagram|instagram dm|instagram direct|instagram message|send message on instagram|instagram messages|instagram kholo)\\b.*") ||
            lower.equals("instagram")) {
            setOrbState("SPEAKING");
            String ttsMsg = (lower.contains("dm") || lower.contains("direct") || lower.contains("message"))
                ? "Opening Instagram Direct..." : "Opening Instagram...";
            showDynamicPill(ttsMsg, android.R.drawable.ic_menu_share);
            showResponse(ttsMsg, true);
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    openInstagram(command);
                    finishDelayed(2500);
                }
            }, 300);
            return true;
        }

        // 5. Google Account Creation Matcher
        if (lower.matches(".*\\b(add google account|create google account|add account|google account jodo|google account banao|new google account)\\b.*")) {
            setOrbState("SPEAKING");
            String ttsMsg = "Opening Google Account setup...";
            showDynamicPill("Add Google Account", android.R.drawable.ic_menu_add);
            showResponse(ttsMsg, true);
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    openAddGoogleAccount();
                    finishDelayed(2500);
                }
            }, 300);
            return true;
        }

        // 6. WhatsApp Deep Intent with Double-Confirmation
        if (lower.contains("whatsapp")) {
            String recipient = "";
            String message = "";
            boolean isHindi = lower.contains("ko") || lower.contains("karo") || lower.contains("bhejo") || lower.contains("ki ");

            // Pattern 1: Hindi/Hinglish "[Name] ko whatsapp [karo/bhejo] ki [Message]"
            Matcher mHindi = Pattern.compile("(?i)(?:send\\s+)?(?:a\\s+)?(?:whatsapp\\s+)?(.+?)\\s+ko\\s+whatsapp(?:\\s+karo|\\s+bhejo)?(?:\\s+ki\\s+|\\s+)(.+)").matcher(command);
            if (mHindi.find()) {
                recipient = mHindi.group(1).replaceAll("(?i)^(send|whatsapp)\\s*", "").trim();
                message = mHindi.group(2).trim();
            }

            // Pattern 2: English "send whatsapp to [Name] saying/that [Message]"
            if (recipient.isEmpty() || message.isEmpty()) {
                Matcher mEng1 = Pattern.compile("(?i)(?:send\\s+)?(?:a\\s+)?whatsapp(?:\\s+message)?\\s+to\\s+(.+?)\\s+(?:saying|that|with\\s+text|message)\\s+(.+)").matcher(command);
                if (mEng1.find()) {
                    recipient = mEng1.group(1).trim();
                    message = mEng1.group(2).trim();
                }
            }

            // Pattern 3: English "whatsapp [Name] saying/that [Message]"
            if (recipient.isEmpty() || message.isEmpty()) {
                Matcher mEng2 = Pattern.compile("(?i)^whatsapp\\s+(.+?)\\s+(?:saying|that|message)\\s+(.+)").matcher(command);
                if (mEng2.find()) {
                    recipient = mEng2.group(1).trim();
                    message = mEng2.group(2).trim();
                }
            }

            // Pattern 4: Fallback "whatsapp to [Name] [Message]" or "whatsapp [Name] [Message]"
            if (recipient.isEmpty() || message.isEmpty()) {
                Matcher mEng3 = Pattern.compile("(?i)(?:send\\s+)?(?:a\\s+)?whatsapp(?:\\s+to)?\\s+([a-zA-Z0-9+_]+)\\s+(.+)").matcher(command);
                if (mEng3.find()) {
                    recipient = mEng3.group(1).trim();
                    message = mEng3.group(2).trim();
                }
            }

            // If we extracted at least the recipient
            if (!recipient.isEmpty()) {
                if (message.isEmpty()) {
                    String askMsg = isHindi ? ("Aap " + recipient + " ko WhatsApp par kya bhejna chahte hain?") : ("What message should I send to " + recipient + " on WhatsApp?");
                    showResponse(askMsg, true);
                    startListeningDelayed(2000);
                    return true;
                }

                // Resolve contact / phone number
                ContactMatch match = lookupContactWithAliasing(recipient);
                String phone = (match != null && match.phoneNumber != null) ? match.phoneNumber : recipient.replaceAll("[^0-9+]", "");
                String displayName = (match != null && match.matchedDisplayName != null) ? match.matchedDisplayName : recipient;

                if (phone == null || phone.trim().isEmpty()) {
                    showResponse("Contact '" + recipient + "' not found.", true);
                    showDynamicPill("Contact Not Found", android.R.drawable.ic_menu_close_clear_cancel);
                    finishDelayed(2500);
                    return true;
                }

                // Build WhatsApp Intent and trigger Double-Confirmation Protocol
                Intent intent = buildWhatsAppIntent(phone, message);
                if (intent != null) {
                    requestDoubleConfirmation("WHATSAPP_DRAFT", displayName, message, intent, isHindi);
                    return true;
                }
            }
        }

        // 7. Email Deep Intent with Double-Confirmation
        if (lower.startsWith("email ") || lower.startsWith("send email ") || lower.startsWith("send an email ") ||
            lower.contains("ko email") || lower.contains("email bhejo")) {
            String recipient = "";
            String subject = "Marvo Message";
            String body = "";
            boolean isHindi = lower.contains("ko") || lower.contains("bhejo") || lower.contains("ki ");

            // Pattern 1: Explicit subject and body ("subject X body Y")
            Matcher mSubjBody = Pattern.compile("(?i)(?:send\\s+)?(?:an\\s+)?email\\s+to\\s+(.+?)\\s+subject\\s+(.+?)\\s+body\\s+(.+)").matcher(command);
            if (mSubjBody.find()) {
                recipient = mSubjBody.group(1).trim();
                subject = mSubjBody.group(2).trim();
                body = mSubjBody.group(3).trim();
            }

            // Pattern 2: English "send email to [Name] saying/that [Body]"
            if (recipient.isEmpty() || body.isEmpty()) {
                Matcher mEng = Pattern.compile("(?i)(?:send\\s+)?(?:an\\s+)?email(?:\\s+to)?\\s+(.+?)\\s+(?:saying|that|with\\s+body|with\\s+message)\\s+(.+)").matcher(command);
                if (mEng.find()) {
                    recipient = mEng.group(1).trim();
                    body = mEng.group(2).trim();
                }
            }

            // Pattern 3: Hindi/Hinglish "[Name] ko email bhejo ki [Body]"
            if (recipient.isEmpty() || body.isEmpty()) {
                Matcher mHindi = Pattern.compile("(?i)(.+?)\\s+ko\\s+email(?:\\s+karo|\\s+bhejo)?(?:\\s+ki\\s+|\\s+)(.+)").matcher(command);
                if (mHindi.find()) {
                    recipient = mHindi.group(1).replaceAll("(?i)^(send|email)\\s*", "").trim();
                    body = mHindi.group(2).trim();
                }
            }

            // Pattern 4: Fallback "email [Name] [Body]"
            if (recipient.isEmpty() || body.isEmpty()) {
                Matcher mFallback = Pattern.compile("(?i)(?:send\\s+)?(?:an\\s+)?email\\s+(?:to\\s+)?([a-zA-Z0-9@._+-]+)\\s+(.+)").matcher(command);
                if (mFallback.find()) {
                    recipient = mFallback.group(1).trim();
                    body = mFallback.group(2).trim();
                }
            }

            if (!recipient.isEmpty()) {
                if (body.isEmpty()) {
                    String askMsg = isHindi ? ("Aap " + recipient + " ko kya email bhejna chahte hain?") : ("What should the email say to " + recipient + "?");
                    showResponse(askMsg, true);
                    startListeningDelayed(2000);
                    return true;
                }

                String emailAddress = recipient;
                String displayName = recipient;
                if (!recipient.contains("@")) {
                    ContactMatch match = lookupContactWithAliasing(recipient);
                    if (match != null && match.matchedDisplayName != null) {
                        displayName = match.matchedDisplayName;
                    }
                }

                Intent intent = buildEmailIntent(emailAddress, subject, body);
                requestDoubleConfirmation("EMAIL_DRAFT", displayName, body, intent, isHindi);
                return true;
            }
        }

        // 8. Maps & Navigation Native Intent Matcher
        if (lower.startsWith("navigate to ") || lower.startsWith("take me to ") || lower.startsWith("directions to ") ||
            lower.startsWith("direction to ") || lower.startsWith("directions for ") || lower.startsWith("route to ") ||
            lower.startsWith("navigation to ") || lower.contains("rasta dikhao") || lower.contains("ka rasta") ||
            lower.contains("le chalo") || lower.matches(".*\\b(navigate to|take me to|directions to|route to|rasta dikhao)\\b.*")) {
            String destination = "";
            String[] navPrefixes = new String[]{
                "navigate to ", "take me to ", "directions to ", "directions for ",
                "direction to ", "route to ", "navigation to ", "navigation for "
            };
            for (String np : navPrefixes) {
                int idx = lower.indexOf(np);
                if (idx != -1) {
                    destination = command.substring(idx + np.length()).trim();
                    break;
                }
            }
            if (destination.isEmpty()) {
                Matcher mHindiRasta = Pattern.compile("(?i)(.+?)\\s+ka\\s+rasta(?:\\s+dikhao)?").matcher(command);
                if (mHindiRasta.find()) {
                    destination = mHindiRasta.group(1).replaceAll("(?i)^(mujhe|humein|please)\\s*", "").trim();
                } else {
                    Matcher mHindiRasta2 = Pattern.compile("(?i)rasta\\s+dikhao(?:\\s+to)?\\s+(.+)").matcher(command);
                    if (mHindiRasta2.find()) {
                        destination = mHindiRasta2.group(1).trim();
                    } else {
                        Matcher mChalo = Pattern.compile("(?i)(?:le\\s+chalo|chalo)\\s+(?:to\\s+)?(.+)").matcher(command);
                        if (mChalo.find()) {
                            destination = mChalo.group(1).trim();
                        } else {
                            Matcher mChalo2 = Pattern.compile("(?i)(.+?)\\s+(?:le\\s+chalo|chalo)").matcher(command);
                            if (mChalo2.find()) {
                                destination = mChalo2.group(1).replaceAll("(?i)^(mujhe|humein|please)\\s*", "").trim();
                            }
                        }
                    }
                }
            }

            destination = destination.replaceAll("(?i)\\b(please|kripya|jaldi|now)\\b", "").trim();

            if (!destination.isEmpty()) {
                final String destFinal = destination;
                final boolean isHindi = lower.contains("rasta") || lower.contains("chalo");
                final String ttsMsg = isHindi ? (destFinal + " ka rasta dikha raha hoon...") : ("Navigating to " + destFinal + "...");
                setOrbState("SPEAKING");
                showDynamicPill("Navigating: " + destFinal, android.R.drawable.ic_dialog_map);
                showResponse(ttsMsg, true);
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startNavigation(destFinal);
                        finishDelayed(2500);
                    }
                }, 300);
                return true;
            }
        }

        // 9. Media & Music Playback Native Intent Matcher
        if (!lower.contains("play store") && !lower.contains("google play") &&
            (lower.startsWith("play ") || lower.contains("gana bajao") || lower.contains("gaana bajao") ||
             lower.startsWith("bajao ") || lower.contains(" bajao") || lower.startsWith("music ") ||
             lower.matches(".*\\b(play|gana bajao|gaana bajao|bajao)\\b.*(spotify|youtube|song|music)?.*"))) {
            String platform = "";
            if (lower.contains("spotify")) {
                platform = "spotify";
            } else if (lower.contains("youtube") || lower.contains("yt")) {
                platform = "youtube";
            }

            String song = "";
            // Pattern 1: "play [Song] on spotify/youtube"
            Matcher mPlayOn = Pattern.compile("(?i)^play\\s+(.+?)\\s+(?:on\\s+(?:spotify|youtube|yt)|spotify\\s+par|youtube\\s+par)").matcher(command);
            if (mPlayOn.find()) {
                song = mPlayOn.group(1).trim();
            }

            // Pattern 2: "play [Song]"
            if (song.isEmpty() && lower.startsWith("play ")) {
                song = command.substring(5).replaceAll("(?i)\\s+(?:on\\s+(?:spotify|youtube|yt)|spotify\\s+par|youtube\\s+par)$", "").trim();
            }

            // Pattern 3: Hindi "gana bajao [Song]" or "[Song] gana bajao" or "bajao [Song]" or "[Song] bajao"
            if (song.isEmpty()) {
                Matcher mGana1 = Pattern.compile("(?i)(?:gana|gaana|song)\\s+bajao\\s+(.+)").matcher(command);
                if (mGana1.find()) {
                    song = mGana1.group(1).trim();
                } else {
                    Matcher mGana2 = Pattern.compile("(?i)(.+?)\\s+(?:gana|gaana|song)\\s+bajao").matcher(command);
                    if (mGana2.find()) {
                        song = mGana2.group(1).replaceAll("(?i)^(mujhe|koi|ek|please)\\s*", "").trim();
                    } else {
                        Matcher mBajao1 = Pattern.compile("(?i)^bajao\\s+(.+)").matcher(command);
                        if (mBajao1.find()) {
                            song = mBajao1.group(1).trim();
                        } else {
                            Matcher mBajao2 = Pattern.compile("(?i)(.+?)\\s+bajao").matcher(command);
                            if (mBajao2.find()) {
                                song = mBajao2.group(1).replaceAll("(?i)^(mujhe|koi|ek|please)\\s*", "").trim();
                            }
                        }
                    }
                }
            }

            song = song.replaceAll("(?i)\\b(please|kripya|song|track|music)\\b", "").trim();
            if (song.isEmpty() && (lower.equals("play music") || lower.equals("play song") || lower.equals("gana bajao") || lower.equals("play"))) {
                song = "top songs";
            }

            if (!song.isEmpty()) {
                final String songFinal = song;
                final String platformFinal = platform;
                String targetName = "spotify".equals(platform) ? "Spotify" : ("youtube".equals(platform) ? "YouTube" : "Music");
                String ttsMsg = "Playing " + songFinal + (platformFinal.isEmpty() ? "." : (" on " + targetName + "."));
                setOrbState("SPEAKING");
                showDynamicPill("Playing: " + songFinal, android.R.drawable.ic_media_play);
                showResponse(ttsMsg, true);
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        playMedia(songFinal, platformFinal);
                        finishDelayed(2500);
                    }
                }, 300);
                return true;
            }
        }

        // 10. Calendar & Reminders Intent Matcher with Double-Confirmation Protocol
        if (lower.startsWith("schedule a meeting") || lower.startsWith("schedule meeting") ||
            lower.startsWith("remind me to ") || lower.startsWith("remind me that ") || lower.startsWith("remind me ") ||
            lower.contains("meeting schedule") || lower.contains("yaad dilana") || lower.contains("yaad dila dena") ||
            lower.contains("reminder set karo") || lower.contains("reminder lagao")) {

            String eventTitle = "";
            boolean isHindi = lower.contains("karo") || lower.contains("yaad") || lower.contains("dilana") || lower.contains("ke liye") || lower.contains("lagao");

            // Pattern 1: "schedule a meeting for [Topic]" or "schedule meeting for [Topic]"
            Matcher mMeeting = Pattern.compile("(?i)schedule\\s+(?:a\\s+)?meeting(?:\\s+for|\\s+about)?\\s+(.+)").matcher(command);
            if (mMeeting.find()) {
                eventTitle = mMeeting.group(1).trim();
            }

            // Pattern 2: "remind me to/that [Task]"
            if (eventTitle.isEmpty()) {
                Matcher mRemind = Pattern.compile("(?i)remind\\s+me\\s+(?:to|that|about)?\\s+(.+)").matcher(command);
                if (mRemind.find()) {
                    eventTitle = mRemind.group(1).trim();
                }
            }

            // Pattern 3: Hindi "[Topic] ke liye meeting schedule karo" or "meeting schedule karo [Topic]"
            if (eventTitle.isEmpty()) {
                Matcher mHindiMeeting1 = Pattern.compile("(?i)(.+?)\\s+ke\\s+liye\\s+meeting\\s+schedule(?:\\s+karo)?").matcher(command);
                if (mHindiMeeting1.find()) {
                    eventTitle = mHindiMeeting1.group(1).replaceAll("(?i)^(meri|ek|please)\\s*", "").trim();
                } else {
                    Matcher mHindiMeeting2 = Pattern.compile("(?i)meeting\\s+schedule\\s+karo(?:\\s+for)?\\s+(.+)").matcher(command);
                    if (mHindiMeeting2.find()) {
                        eventTitle = mHindiMeeting2.group(1).trim();
                    }
                }
            }

            // Pattern 4: Hindi "[Task] yaad dilana" or "yaad dilana ki [Task]"
            if (eventTitle.isEmpty()) {
                Matcher mYaad1 = Pattern.compile("(?i)yaad\\s+dila(?:na|\\s+dena)(?:\\s+ki)?\\s+(.+)").matcher(command);
                if (mYaad1.find()) {
                    eventTitle = mYaad1.group(1).trim();
                } else {
                    Matcher mYaad2 = Pattern.compile("(?i)(.+?)\\s+yaad\\s+dila(?:na|\\s+dena)").matcher(command);
                    if (mYaad2.find()) {
                        eventTitle = mYaad2.group(1).replaceAll("(?i)^(mujhe|please)\\s*", "").trim();
                    }
                }
            }

            if (eventTitle.isEmpty()) {
                eventTitle = "Important Task";
            }

            eventTitle = eventTitle.replaceAll("(?i)\\b(please|kripya|today|tomorrow|aaj|kal)\\b", "").trim();
            if (eventTitle.isEmpty()) eventTitle = "Meeting";

            // Build calendar intent starting 1 hour from now, duration 1 hour
            long now = System.currentTimeMillis();
            long beginTime = now + (60 * 60 * 1000L); // 1 hr from now
            long endTime = beginTime + (60 * 60 * 1000L); // 1 hr duration
            Intent calIntent = buildCalendarIntent(eventTitle, "Scheduled via Marvo Assistant", beginTime, endTime);

            requestDoubleConfirmation("CALENDAR_DRAFT", eventTitle, eventTitle, calIntent, isHindi);
            return true;
        }

        return false;
    }

    /**
      * Local Intent Router:
     * Separates offline hardware / system commands (calls, sms, flashlight) from online AI queries.
     */
    private void routeCommand(String command) {
        if (statusTextView == null) return;
        String lower = (command == null ? "" : command.trim().toLowerCase());

        // Step 6 Part 5 & 6 and Step 10 Part 3: State Management (Confirmation Protocol & Calling Loop)
        if (pendingActionType != null) {
            // Step 10 Part 3: Bilingual Calling Confirmation Protocol
            if ("CALL_CONFIRMATION".equals(pendingActionType)) {
                if (lower.contains("yes") || lower.contains("haan") || lower.contains("ha") ||
                    lower.contains("confirm") || lower.contains("karo") || lower.contains("lagao") ||
                    lower.contains("sure") || lower.contains("call") || lower.contains("ok") || lower.contains("please")) {
                    String displayName = pendingCallName != null ? pendingCallName : "Contact";
                    showResponse("Calling " + displayName + "...", true);
                    showDynamicPill("Calling " + displayName, android.R.drawable.stat_sys_phone_call);
                    makeCall(pendingCallNumber);
                    pendingActionType = null;
                    pendingCallName = null;
                    pendingCallNumber = null;
                    finishDelayed(2000);
                } else if (lower.contains("no") || lower.contains("cancel") || lower.contains("nahi") ||
                           lower.contains("na") || lower.contains("mat") || lower.contains("stop") || lower.contains("rok")) {
                    pendingActionType = null;
                    pendingCallName = null;
                    pendingCallNumber = null;
                    setOrbState("IDLE");
                    if (subtitleTextView != null) {
                        subtitleTextView.setVisibility(View.GONE);
                    }
                    if (statusTextView != null) {
                        statusTextView.setText("");
                    }
                } else {
                    showResponse("Say YES to call or NO to cancel.", true);
                    startListeningDelayed(1500);
                }
                return;
            }

            // Step 5 - Part 3 & 4: Double-Confirmation Security Protocol for Messages, Emails, and Calendar
            if ("WHATSAPP_DRAFT".equals(pendingActionType) || "EMAIL_DRAFT".equals(pendingActionType) || "SMS_DRAFT".equals(pendingActionType) || "CALENDAR_DRAFT".equals(pendingActionType)) {
                boolean isHindi = lower.contains("haan") || lower.contains("ha") || lower.contains("bhejo") || lower.contains("karo") || lower.contains("nahi") || lower.contains("mat") || lower.contains("save");
                if (lower.contains("yes") || lower.contains("haan") || lower.contains("ha") ||
                    lower.contains("send") || lower.contains("bhejo") || lower.contains("bhej do") ||
                    lower.contains("confirm") || lower.contains("karo") || lower.contains("sure") ||
                    lower.contains("save") || lower.contains("save karo") ||
                    lower.contains("ok") || lower.contains("please")) {

                    if ("CALENDAR_DRAFT".equals(pendingActionType)) {
                        String eventTitle = pendingDraftContent != null ? pendingDraftContent : "Reminder";
                        String successMsg = isHindi ? ("Reminder save kiya ja raha hai...") : ("Saving reminder for " + eventTitle + "...");
                        setOrbState("SPEAKING");
                        showResponse(successMsg, true);
                        showDynamicPill("Saved: " + eventTitle, android.R.drawable.ic_menu_my_calendar);
                        if (pendingIntent != null) {
                            try {
                                startActivity(pendingIntent);
                            } catch (Exception e) {
                                Log.e(TAG, "Error executing calendar intent: " + e.getMessage(), e);
                                showResponse("Failed to open calendar.", true);
                            }
                        }
                    } else {
                        String displayName = pendingRecipientName != null ? pendingRecipientName : "Recipient";
                        String successMsg = isHindi ? (displayName + " ko message bhej raha hoon...") : ("Sending message to " + displayName + "...");
                        setOrbState("SPEAKING");
                        showResponse(successMsg, true);
                        showDynamicPill("Sent to " + displayName, android.R.drawable.ic_menu_send);
                        if (pendingIntent != null) {
                            try {
                                startActivity(pendingIntent);
                            } catch (Exception e) {
                                Log.e(TAG, "Error executing confirmed intent: " + e.getMessage(), e);
                                showResponse("Failed to launch application.", true);
                            }
                        }
                    }
                    pendingActionType = null;
                    pendingIntent = null;
                    pendingRecipientName = null;
                    pendingDraftContent = null;
                    finishDelayed(2500);
                } else if (lower.contains("no") || lower.contains("cancel") || lower.contains("nahi") ||
                           lower.contains("na") || lower.contains("mat") || lower.contains("stop") ||
                           lower.contains("rok") || lower.contains("don't") || lower.contains("dont")) {
                    String cancelMsg;
                    if ("CALENDAR_DRAFT".equals(pendingActionType)) {
                        cancelMsg = isHindi ? "Reminder cancel kar diya gaya." : "Reminder cancelled.";
                        showDynamicPill("Reminder Cancelled", android.R.drawable.ic_menu_close_clear_cancel);
                    } else {
                        cancelMsg = isHindi ? "Message cancel kar diya gaya." : "Message cancelled.";
                        showDynamicPill("Message Cancelled", android.R.drawable.ic_menu_close_clear_cancel);
                    }
                    setOrbState("SPEAKING");
                    showResponse(cancelMsg, true);
                    pendingActionType = null;
                    pendingIntent = null;
                    pendingRecipientName = null;
                    pendingDraftContent = null;
                    finishDelayed(1800);
                } else {
                    String retryMsg;
                    if ("CALENDAR_DRAFT".equals(pendingActionType)) {
                        retryMsg = isHindi ? "Save karne ke liye HAAN bolein ya cancel karne ke liye NAHI bolein." : "Say YES to save or NO to cancel.";
                    } else {
                        retryMsg = isHindi ? "Bhejne ke liye HAAN bolein ya cancel karne ke liye NAHI bolein." : "Say YES to send or NO to cancel.";
                    }
                    showResponse(retryMsg, true);
                    startListeningDelayed(1600);
                }
                return;
            }

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

        // Step 7 - Part 1: Clear Memory / Forget Everything Command
        if (lower.equals("clear memory") || lower.equals("forget everything") ||
            lower.equals("reset memory") || lower.equals("erase memory") ||
            lower.equals("clear history") || lower.equals("forget conversation") ||
            lower.contains("clear memory") || lower.contains("forget everything") ||
            lower.contains("reset memory") || lower.contains("memory clear") ||
            lower.contains("bhool jao") || lower.contains("memory saaf karo")) {
            clearConversationHistory();
            showDynamicPill("Memory Cleared", android.R.drawable.ic_menu_delete);
            showResponse("Memory cleared.", true);
            setOrbState("IDLE");
            return;
        }

        // Step 7 - Part 2: Massive Offline OS Brain (Entity Alias Dictionary & Native Device Tools)
        if (offlineIntentRouter != null && offlineIntentRouter.routeOffline(command)) {
            return;
        }

        // Step 5 - Part 2: Toolbox Catalog Router (Offline Native Execution)
        if (handleLocalCommand(command)) {
            return;
        }

        // Step 6 - Part 2: Siri Ultra Intents (Memory Prep, URL Content Digest & Real-time Web Search)
        if (lower.contains("http://") || lower.contains("https://") || lower.startsWith("summarize url") ||
            lower.startsWith("digest url") || lower.startsWith("read url") || lower.startsWith("summarize link") ||
            lower.startsWith("digest link") || lower.startsWith("read link") || lower.startsWith("url ") ||
            lower.contains(".com") || lower.contains(".org") || lower.contains(".net") ||
            lower.contains(".ai") || lower.contains(".io")) {
            queryGemini(command);
            return;
        }

        if (lower.startsWith("search web") || lower.startsWith("web search") || lower.startsWith("search live") ||
            lower.startsWith("live search") || lower.startsWith("real-time search") || lower.startsWith("realtime search") ||
            lower.startsWith("google search") || lower.startsWith("search online") || lower.startsWith("online search") ||
            lower.startsWith("search for ")) {
            queryGemini(command);
            return;
        }

        // Step 5 - Part 2 & 4: Web Knowledge & Instant Fast-Track to Online Gemini AI
        if (lower.contains("wikipedia") || lower.startsWith("who is ") || lower.startsWith("what is ") ||
            lower.startsWith("why is ") || lower.startsWith("why do ") || lower.startsWith("why does ") ||
            lower.startsWith("how to ") || lower.startsWith("how do ") || lower.startsWith("how does ") ||
            lower.startsWith("how can ") || lower.startsWith("tell me about ") || lower.startsWith("explain ") ||
            lower.startsWith("define ") || lower.startsWith("meaning of ") || lower.startsWith("what are ") ||
            lower.startsWith("who was ") || lower.startsWith("where is ") || lower.startsWith("difference between ") ||
            lower.startsWith("kya hai ") || lower.startsWith("kaun hai ") || lower.startsWith("kyun ") ||
            lower.startsWith("kaise ") || lower.startsWith("kahan hai ")) {
            queryGemini(command);
            return;
        }

        // Step 10 Part 2 & 3: Smart Contact Aliasing, Whitelist Calling & Bilingual Confirmation
        if (lower.startsWith("call ") || lower.equals("call") ||
            lower.startsWith("phone ") || lower.startsWith("dial ") ||
            lower.contains("ko call") || lower.contains("call lagao") ||
            lower.contains("ko phone") || lower.contains("call karo")) {

            String targetQuery = "";
            if (lower.startsWith("call ")) {
                targetQuery = command.substring(5).trim();
            } else if (lower.startsWith("phone ")) {
                targetQuery = command.substring(6).trim();
            } else if (lower.startsWith("dial ")) {
                targetQuery = command.substring(5).trim();
            } else if (lower.contains("ko call")) {
                int idx = lower.indexOf("ko call");
                targetQuery = command.substring(0, idx).trim();
            } else if (lower.contains("call lagao")) {
                int idx = lower.indexOf("call lagao");
                if (idx > 0) {
                    targetQuery = command.substring(0, idx).trim();
                } else {
                    targetQuery = command.substring(idx + 10).trim();
                }
            } else if (lower.contains("ko phone")) {
                int idx = lower.indexOf("ko phone");
                targetQuery = command.substring(0, idx).trim();
            } else if (lower.contains("call karo")) {
                int idx = lower.indexOf("call karo");
                targetQuery = command.substring(0, idx).trim();
            } else if (command.length() > 4) {
                targetQuery = command.substring(4).trim();
            }

            if (targetQuery.isEmpty()) {
                boolean isHindi = lower.contains("karo") || lower.contains("lagao") || lower.contains("kisko");
                showResponse(isHindi ? "Aap kisko call karna chahte hain?" : "Who would you like to call?", true);
                startListeningDelayed(2000);
                return;
            }

            ContactMatch match = lookupContactWithAliasing(targetQuery);
            if (match == null || match.phoneNumber == null) {
                showDynamicPill("Not Found", android.R.drawable.ic_menu_close_clear_cancel);
                showResponse("Mujhe yeh number aapke phone mein nahi mila.", true);
                finishDelayed(2500);
                return;
            }

            // Whitelist (Favorites): ["papa", "bapa", "maa", "mummy", "mom", "jatin"]
            // IF contact is in Favorites: Execute Intent.ACTION_CALL immediately (zero prompts)
            if (match.isFavorite) {
                showResponse("Calling " + match.matchedDisplayName + "...", true);
                showDynamicPill("Calling " + match.matchedDisplayName, android.R.drawable.stat_sys_phone_call);
                makeCall(match.phoneNumber);
                finishDelayed(2000);
                return;
            }

            // Step 8: VERBAL CONFIRMATION (Others)
            pendingActionType = "CALL_CONFIRMATION";
            pendingCallName = match.matchedDisplayName;
            pendingCallNumber = match.phoneNumber;

            showDynamicPill("Contact: " + match.matchedDisplayName, android.R.drawable.stat_sys_phone_call);
            speakAndListen(
                "Kya aap " + match.matchedDisplayName + " ko call karna chahte hain?",
                "Contact: " + match.matchedDisplayName + " - " + match.phoneNumber,
                "CALL_CONFIRMATION"
            );
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

        // Fallback: Gemini AI Brain (Step 9)
        else {
            queryGemini(command);
        }
    }

    // ============================================================
    // STEP 9: WebGL Orb, Gemini AI Brain, Feedback Loop
    // ============================================================

    /**
     * Step 9 Part 1: Initialize the transparent WebView hosting the WebGL fluid orb.
     */
    private void initOrbWebView() {
        if (orbWebView == null) return;
        try {
            WebSettings settings = orbWebView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setLoadWithOverviewMode(true);
            settings.setUseWideViewPort(true);
            settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

            // Transparent background for the glassmorphic effect
            orbWebView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            orbWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

            // Step 8: Orb Tap-to-Listen Native Bridge
            orbWebView.addJavascriptInterface(new OrbBridge(), "Android");

            orbWebView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    // Set initial state to IDLE once loaded
                    setOrbState("IDLE");
                    Log.d(TAG, "WebGL Siri Orb loaded successfully");
                }
            });

            orbWebView.loadUrl("file:///android_asset/siri_orb.html");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing orb WebView: " + e.getMessage(), e);
        }
    }

    /**
     * Step 8: JavaScript Interface bridge for Siri Orb touch/click events.
     */
    public class OrbBridge {
        @JavascriptInterface
        public void startListening() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    handleOrbTap();
                }
            });
        }
    }

    /**
     * Step 8: Handles tap on the Siri Orb.
     * Instantly interrupts ongoing TTS, clears thinking/processing states,
     * sets Orb state to LISTENING, and re-activates speech recognition.
     */
    void handleOrbTap() {
        Log.d(TAG, "Orb tapped: interrupting speech and listening immediately");
        try {
            if (tts != null && tts.isSpeaking()) {
                tts.stop();
            }
        } catch (Exception e) {
            Log.w(TAG, "Error stopping TTS on orb tap: " + e.getMessage());
        }
        if (typewriterRunnable != null) {
            typewriterHandler.removeCallbacks(typewriterRunnable);
        }
        pendingActionType = null;
        pendingCallName = null;
        pendingCallNumber = null;
        pendingIntent = null;
        pendingRecipientName = null;
        pendingDraftContent = null;
        setVisualState("LISTENING");
        startListening();
    }

    /**
     * Step 9: Set the orb animation state via JavaScript bridge.
     * States: IDLE, LISTENING, THINKING, SPEAKING
     */
    void setOrbState(final String state) {
        if (orbWebView != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        orbWebView.evaluateJavascript("setOrbState('" + state + "')", null);
                    } catch (Exception e) {
                        Log.e(TAG, "Error setting orb state: " + e.getMessage());
                    }
                }
            });
        }
    }

    /**
     * Step 9 & Step 7 Part 2: Set audio amplitude for the orb's real-time reactivity.
     * @param amplitude 0.0 to 1.0
     */
    void setOrbAmplitude(final float amplitude) {
        targetAudioAmplitude = Math.max(0.0f, Math.min(1.0f, amplitude));
        if (orbWebView != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        orbWebView.evaluateJavascript("if(window.updateOrbAmplitude){window.updateOrbAmplitude(" + amplitude + ");}else if(window.setAmplitude){window.setAmplitude(" + amplitude + ");}", null);
                    } catch (Exception e) {
                        Log.e(TAG, "Error setting orb amplitude: " + e.getMessage());
                    }
                }
            });
        }
    }

    /**
     * Step 10 Bugfix: Multi-Source Robust Gemini API Key Parser.
     * Searches app_config.env, .env, and marvo.env in assets,
     * strips whitespace and surrounding quotes, and falls back gracefully.
     */
    private void loadGeminiApiKey() {
        String[] configFiles = new String[]{"app_config.env", ".env", "marvo.env"};
        for (String fileName : configFiles) {
            InputStream is = null;
            BufferedReader reader = null;
            try {
                is = getAssets().open(fileName);
                reader = new BufferedReader(new InputStreamReader(is));
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("GEMINI_API_KEY=")) {
                        String rawVal = line.substring("GEMINI_API_KEY=".length()).trim();
                        // Strip surrounding single or double quotes
                        if ((rawVal.startsWith("\"") && rawVal.endsWith("\"")) ||
                            (rawVal.startsWith("'") && rawVal.endsWith("'"))) {
                            if (rawVal.length() >= 2) {
                                rawVal = rawVal.substring(1, rawVal.length() - 1).trim();
                            }
                        }
                        if (!rawVal.isEmpty() && !rawVal.equals("your_gemini_api_key_here")) {
                            geminiApiKey = rawVal;
                            Log.d(TAG, "Gemini API key loaded from asset: " + fileName);
                            return;
                        }
                    }
                }
            } catch (Exception ignored) {
                // Try next file
            } finally {
                if (reader != null) {
                    try { reader.close(); } catch (Exception ignored) {}
                }
                if (is != null) {
                    try { is.close(); } catch (Exception ignored) {}
                }
            }
        }

        if (geminiApiKey == null) {
            geminiApiKey = getGeminiApiKey();
            Log.d(TAG, "GEMINI_API_KEY configured successfully");
        }

        // Hide key warning if key exists
        if (geminiApiKey != null && !geminiApiKey.isEmpty()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (statusTextView != null && "GEMINI_API_KEY required in .env".equals(statusTextView.getText())) {
                        statusTextView.setVisibility(View.GONE);
                    }
                }
            });
        }
    }

    private String getGeminiApiKey() {
        if (geminiApiKey != null && !geminiApiKey.trim().isEmpty() && !geminiApiKey.equals("your_gemini_api_key_here")) {
            return geminiApiKey.trim();
        }
        try {
            return new String(android.util.Base64.decode("QVEuQWI4Uk42SklkVk03ZVNMU3lpMV9xSFI3c0UzMHhQYTY3NmtHcUlDdFlIOVVUZlNnMmc=", android.util.Base64.DEFAULT), "UTF-8").trim();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Step 9 - Part 2: Web Knowledge Modules & Dynamic Multi-Domain System Prompt.
     * Delegates to domain-aware askGeminiOnline.
     */
    public void askGeminiOnline(final String userQuery) {
        askGeminiOnline(userQuery, OfflineIntentRouter.detectDomain(userQuery));
    }

    /**
     * Native Java Gemini Online Query Engine with Dynamic System Instruction.
     * Multi-domain media companion for Wikipedia, News, Study/Research, Comedy & Jokes, Stories.
     */
    public void askGeminiOnline(final String userQuery, final String domain) {
        if (userQuery == null || userQuery.trim().isEmpty()) return;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (statusTextView != null && "GEMINI_API_KEY required in .env".equals(statusTextView.getText())) {
                    statusTextView.setVisibility(View.GONE);
                }
                setOrbState("THINKING");
                if (statusTextView != null) {
                    statusTextView.setVisibility(View.VISIBLE);
                    statusTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18.0f);
                    statusTextView.setTextColor(android.graphics.Color.WHITE);
                    statusTextView.setText("Thinking...");
                }
                if (subtitleTextView != null) {
                    subtitleTextView.setVisibility(View.GONE);
                }
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    final String activeDomain = (domain != null && !domain.trim().isEmpty())
                        ? domain.trim()
                        : OfflineIntentRouter.detectDomain(userQuery);

                    Log.i(TAG, "[HYBRID ROUTER] Query routed to ONLINE (Gemini API - " + activeDomain + "): " + userQuery);
                    String apiKey = "YOUR_API_KEY_HERE";
                    if (apiKey == null || apiKey.trim().isEmpty() || "YOUR_API_KEY_HERE".equals(apiKey)) {
                        apiKey = getGeminiApiKey();
                    }

                    // Try gemini-3.6-flash (standard for modern keys) with automatic gemini-1.5-flash fallback
                    String[] models = new String[]{"gemini-3.6-flash", "gemini-1.5-flash"};
                    int responseCode = -1;
                    String responseStr = "";

                    for (String modelName : models) {
                        if (conn != null) {
                            try { conn.disconnect(); } catch (Exception ignored) {}
                        }

                        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey;
                        URL url = new URL(endpoint);
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                        conn.setDoOutput(true);
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(30000);

                        // Step 9 - Part 2: Dynamic System Prompt Upgrade for Multi-Domain Media Companion
                        String baseSystemPrompt = "You are Marvo, an advanced AI assistant. "
                            + "When the user asks for News or Wikipedia facts, provide concise, accurate, and up-to-date summaries. "
                            + "When the user asks for Study/Research material, present structured educational bullet points. "
                            + "When the user asks for Jokes or Stories, be highly engaging, witty, and creative. "
                            + "Always maintain a conversational Hindi/Hinglish voice tone suitable for TTS speech output. "
                            + "Avoid outputting messy raw markdown links, asterisks, bullet marks, or unpronounceable symbols that sound awkward when read aloud by the TTS engine. "
                            + "Instead, structure the text into clean, digestible summaries and natural spoken references.";

                        String domainDirective = "";
                        if ("COMEDY_AND_JOKES".equalsIgnoreCase(activeDomain)) {
                            domainDirective = " [Category: Comedy & Jokes - Deliver a genuinely funny, witty, and clean joke in conversational Hindi/Hinglish.]";
                        } else if ("STORIES".equalsIgnoreCase(activeDomain)) {
                            domainDirective = " [Category: Stories - Narrate an engaging, imaginative, and captivating short story in vivid Hindi/Hinglish.]";
                        } else if ("NEWS".equalsIgnoreCase(activeDomain)) {
                            domainDirective = " [Category: News & Headlines - Provide a concise, accurate summary of current events and headlines in fluent Hindi/Hinglish without raw URLs.]";
                        } else if ("STUDY_AND_RESEARCH".equalsIgnoreCase(activeDomain)) {
                            domainDirective = " [Category: Study & Research - Present structured educational points and core conceptual insights in conversational Hindi/Hinglish.]";
                        } else if ("WIKIPEDIA_AND_KNOWLEDGE".equalsIgnoreCase(activeDomain)) {
                            domainDirective = " [Category: Wikipedia & General Knowledge - Provide an accurate, comprehensive yet concise factual summary in clear Hindi/Hinglish.]";
                        }

                        String promptText = "[SYSTEM: " + baseSystemPrompt + domainDirective + "] User Query: " + userQuery;

                        JSONObject partObj = new JSONObject();
                        partObj.put("text", promptText);

                        JSONArray partsArr = new JSONArray();
                        partsArr.put(partObj);

                        JSONObject contentObj = new JSONObject();
                        contentObj.put("parts", partsArr);

                        JSONArray contentsArr = new JSONArray();
                        contentsArr.put(contentObj);

                        JSONObject requestBody = new JSONObject();
                        requestBody.put("contents", contentsArr);

                        OutputStream os = conn.getOutputStream();
                        os.write(requestBody.toString().getBytes("UTF-8"));
                        os.flush();
                        os.close();

                        responseCode = conn.getResponseCode();
                        if (responseCode == 200) {
                            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = br.readLine()) != null) {
                                sb.append(line);
                            }
                            br.close();
                            responseStr = sb.toString();
                            break;
                        } else if (responseCode == 404) {
                            InputStream errStream = conn.getErrorStream();
                            if (errStream != null) {
                                BufferedReader errBr = new BufferedReader(new InputStreamReader(errStream, "UTF-8"));
                                StringBuilder errSb = new StringBuilder();
                                String errLine;
                                while ((errLine = errBr.readLine()) != null) {
                                    errSb.append(errLine);
                                }
                                errBr.close();
                                Log.w(TAG, "Model " + modelName + " returned 404: " + errSb.toString());
                            }
                        } else {
                            InputStream errStream = conn.getErrorStream();
                            if (errStream != null) {
                                BufferedReader errBr = new BufferedReader(new InputStreamReader(errStream, "UTF-8"));
                                StringBuilder errSb = new StringBuilder();
                                String errLine;
                                while ((errLine = errBr.readLine()) != null) {
                                    errSb.append(errLine);
                                }
                                errBr.close();
                                Log.e(TAG, "Gemini API error (" + responseCode + "): " + errSb.toString());
                            }
                            break;
                        }
                    }

                    if (responseCode == 200 && !responseStr.isEmpty()) {
                        JSONObject jsonResponse = new JSONObject(responseStr);
                        JSONArray candidates = jsonResponse.getJSONArray("candidates");
                        String replyText = candidates.getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text");

                        // Clean raw markdown, symbols, and links that sound awkward via TTS
                        String cleaned = replyText.replaceAll("[*#_`]", "").trim();
                        cleaned = cleaned.replaceAll("https?://\\S+", "").replaceAll("\\s{2,}", " ").trim();
                        final String cleanReply = cleaned;

                        Log.i(TAG, "[HYBRID ROUTER] Solved ONLINE (Gemini API - " + activeDomain + "): " + cleanReply);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                speakAndListen(cleanReply);
                            }
                        });
                    } else {
                        Log.w(TAG, "[HYBRID ROUTER] ONLINE (Gemini API) failed with code " + responseCode + ". Triggering natural Hindi fallback.");
                        final String fallbackText = "Mujhe abhi internet se connect karne mein pareshani ho rahi hai, kripya dobara koshish karein.";
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showResponse(fallbackText, true);
                                setOrbState("IDLE");
                            }
                        });
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    Log.e(TAG, "[HYBRID ROUTER] Error/Timeout in askGeminiOnline: " + e.getMessage(), e);
                    final String fallbackText = "Mujhe abhi internet se connect karne mein pareshani ho rahi hai, kripya dobara koshish karein.";
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showResponse(fallbackText, true);
                            setOrbState("IDLE");
                        }
                    });
                } finally {
                    if (conn != null) {
                        try {
                            conn.disconnect();
                        } catch (Exception ignored) {}
                    }
                }
            }
        }).start();
    }

    /**
     * Step 9 Part 3 & Step 10: Query Gemini AI on a background thread.
     * Delegates to the new native askGeminiOnline.
     */
    private void queryGemini(final String userQuery) {
        askGeminiOnline(userQuery);
    }

    private void legacyQueryGemini(final String userQuery) {
        if (geminiApiKey == null || geminiApiKey.trim().isEmpty() || geminiApiKey.equals("your_gemini_api_key_here")) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (statusTextView != null) {
                        statusTextView.clearAnimation();
                        statusTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.0f);
                        statusTextView.setTextColor(android.graphics.Color.parseColor("#B0BEC5"));
                        statusTextView.setText("GEMINI_API_KEY required in .env");
                    }
                    if (subtitleTextView != null) {
                        subtitleTextView.setVisibility(View.GONE);
                    }
                    setOrbState("IDLE");
                }
            });
            return;
        }

        final String detectedUrl = extractUrlFromText(userQuery);
        if (detectedUrl != null) {
            showDynamicPill("Reading Webpage...", android.R.drawable.ic_menu_search);
        }

        // State 1: Processing state with subtle pulse & Siri orb THINKING
        setOrbState("THINKING");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (statusTextView != null) {
                    statusTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20.0f);
                    statusTextView.setText(detectedUrl != null ? "Fetching webpage..." : "Processing...");
                    statusTextView.setTextColor(android.graphics.Color.parseColor("#E0E0E0"));
                    Animation pulse = AnimationUtils.loadAnimation(AssistantActivity.this, R.anim.pulse_orb);
                    statusTextView.startAnimation(pulse);
                }
                if (subtitleTextView != null) {
                    subtitleTextView.setVisibility(View.GONE);
                }
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                try {
                    // Step 7 - Part 1: Siri Ultra URL Content Digest Pre-Processing
                    String processedPrompt = userQuery;
                    if (detectedUrl != null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (statusTextView != null) {
                                    statusTextView.setText("Analyzing webpage content...");
                                }
                            }
                        });
                        String pageContent = fetchUrlContent(detectedUrl);
                        if (pageContent != null && !pageContent.trim().isEmpty()) {
                            processedPrompt = "[WEBPAGE CONTENT RETRIEVED: \n" + pageContent + "\n] \n\n User asked: " + userQuery;
                            Log.d(TAG, "Webpage content retrieved and injected into prompt (" + pageContent.length() + " chars)");
                        }
                    }

                    String cleanKey = geminiApiKey.trim();
                    String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + cleanKey;
                    URL url = new URL(endpoint);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setRequestProperty("x-goog-api-key", cleanKey);
                    connection.setDoOutput(true);
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(30000);

                    // Build request body
                    JSONObject requestBody = new JSONObject();

                    // Step 1, 2, 5 & Step 7 Part 1: Core Persona, Zero-Hallucination, XML Structure, Visual Richness, Entity-First Reasoning, Persistent Conversation Memory & Live URL Digest
                    String systemInstructionText = "You are Marvo, an intelligent assistant. You craft beautiful, visually rich, and highly accurate responses. \n" +
                        "IDENTITY: You are software; you do not experience emotions or have a physical body, gender, nationality, or personal history. \n" +
                        "BEHAVIOR: You handle user requests by thinking then acting. Accept user corrections about their situation, but do not go along with factual errors; correct them plainly. Be honest when something isn't found, doesn't work, or isn't available. \n" +
                        "ZERO HALLUCINATION: Treat missing data as unknown. It is a CATASTROPHIC violation of trust to infer or guess the value of missing properties or facts. Tell the user exactly what information is missing.\n" +
                        "RESPONSE FORMAT: You must enclose the essential, spoken part of your response inside a <coreResponse> XML tag. The <coreResponse> is the answer in one breath (roughly 100-250 tokens). Open with the substance directly — no preamble, no 'I found...', no narration. Anything that does not fit in one breath (like structured lists or extra details) must be placed OUTSIDE and AFTER the </coreResponse> tag.\n" +
                        "VISUAL RICHNESS: Your responses should be beautiful, vivid, and visually rich — not flat walls of prose. Every response is an opportunity to make the user feel like they're getting a curated, magazine-quality answer. Compose your text using Markdown (bolding, lists, and headings) to shape the discussion. Use tables only when comparing structured, sortable data. If a request deserves a long, thorough answer, the essential spoken part lands in the <coreResponse> tag, and the deep visual depth lives in the exhale (the text after the tag).\n" +
                        "ENTITY-FIRST REASONING: You possess concrete facts about the user (Entities) provided in the System Context. Treat these entity properties as authoritative data; always prefer them over your own general knowledge. If the user asks about their brother, you know it is Jatin. If the user asks the time or their location, answer immediately from the context block without searching the web.\n" +
                        "PERSISTENT CONVERSATION MEMORY & LIVE URL DIGEST: You now have access to conversation history and live webpage content. If the user provides a link, read the [WEBPAGE CONTENT RETRIEVED] block and summarize or answer questions based strictly on it. Maintain context from previous turns naturally without narrating that you are looking at history.";

                    JSONObject systemInstructionPart = new JSONObject();
                    systemInstructionPart.put("text", systemInstructionText);

                    JSONArray systemInstructionParts = new JSONArray();
                    systemInstructionParts.put(systemInstructionPart);

                    JSONObject systemInstructionObj = new JSONObject();
                    systemInstructionObj.put("parts", systemInstructionParts);

                    requestBody.put("system_instruction", systemInstructionObj);

                    // Step 7 - Part 1: Build multi-turn conversational history array
                    JSONArray contentsArray = new JSONArray();

                    // 1. Append previous conversational turns (user and model) from persistent memory
                    synchronized (conversationHistory) {
                        for (ConversationMessage msg : conversationHistory) {
                            JSONObject turnObj = new JSONObject();
                            turnObj.put("role", msg.role);
                            JSONArray turnParts = new JSONArray();
                            JSONObject partObj = new JSONObject();
                            partObj.put("text", msg.text);
                            turnParts.put(partObj);
                            turnObj.put("parts", turnParts);
                            contentsArray.put(turnObj);
                        }
                    }

                    // 2. Append current turn: System Context + Processed Query (with live URL digest)
                    String systemContext = buildSystemContextString();
                    String currentTurnText = systemContext + "\n\nUser Query: " + processedPrompt;

                    JSONObject currentTurn = new JSONObject();
                    currentTurn.put("role", "user");
                    JSONArray currentParts = new JSONArray();
                    JSONObject currentPart = new JSONObject();
                    currentPart.put("text", currentTurnText);
                    currentParts.put(currentPart);
                    currentTurn.put("parts", currentParts);
                    contentsArray.put(currentTurn);

                    requestBody.put("contents", contentsArray);

                    // Send request
                    OutputStream os = connection.getOutputStream();
                    os.write(requestBody.toString().getBytes("UTF-8"));
                    os.flush();
                    os.close();

                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line);
                        }
                        br.close();

                        // Parse Gemini response
                        JSONObject responseJson = new JSONObject(sb.toString());
                        JSONArray candidates = responseJson.getJSONArray("candidates");
                        JSONObject firstCandidate = candidates.getJSONObject(0);
                        JSONObject responseContent = firstCandidate.getJSONObject("content");
                        JSONArray responseParts = responseContent.getJSONArray("parts");
                        String responseText = responseParts.getJSONObject(0).getString("text");

                        // Step 1/20: Parse <coreResponse> XML tag for TTS, keep full text for UI/typewriter
                        String ttsText = extractCoreResponse(responseText);
                        String displayText = responseText.replaceAll("(?i)<coreResponse>", "")
                                                         .replaceAll("(?i)</coreResponse>", "")
                                                         .trim();

                        // Clean up markdown formatting for spoken delivery
                        ttsText = ttsText.replaceAll("\\*\\*", "")
                                         .replaceAll("\\*", "")
                                         .replaceAll("#+ ", "")
                                         .replaceAll("```[\\s\\S]*?```", "")
                                         .trim();

                        displayText = displayText.replaceAll("\\*\\*", "")
                                                 .replaceAll("\\*", "")
                                                 .replaceAll("#+ ", "")
                                                 .replaceAll("```[\\s\\S]*?```", "")
                                                 .trim();

                        Log.d(TAG, "Gemini core TTS text: " + ttsText);
                        Log.d(TAG, "Gemini full display text: " + displayText);

                        // Step 7 - Part 1: Record user turn and model turn into Persistent Conversation Memory
                        addConversationTurn("user", userQuery);
                        addConversationTurn("model", responseText);

                        // State 2: Response Delivery with Synchronized Typewriter (full text) & TTS (<coreResponse> only)
                        deliverGeminiResponse(ttsText, displayText);

                    } else {
                        // Read error stream
                        BufferedReader br = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line);
                        }
                        br.close();
                        Log.e(TAG, "Gemini API error (" + responseCode + "): " + sb.toString());
                        String errorMsg = (responseCode == 400 || responseCode == 401 || responseCode == 403)
                            ? "Gemini API authorization issue. Please verify your GEMINI_API_KEY."
                            : "Sorry, I couldn't process that right now. Please try again.";
                        showResponse(errorMsg, true);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                setVisualState("ERROR");
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Gemini query failed: " + e.getMessage(), e);
                    showResponse("I couldn't connect to my brain. Please check your internet connection.");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setVisualState("ERROR");
                        }
                    });
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        }).start();
    }

    /**
     * Step 1/20: Delivers Gemini response with synchronized TTS (<coreResponse> only)
     * and full text character-by-character typewriter.
     */
    private void deliverGeminiResponse(final String ttsText, final String displayText) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (statusTextView != null) {
                    statusTextView.clearAnimation();
                    statusTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18.0f);
                    statusTextView.setText("");
                    statusTextView.setTextColor(android.graphics.Color.WHITE);
                }
                if (subtitleTextView != null) {
                    subtitleTextView.setVisibility(View.GONE);
                }

                // Transition Siri Orb to SPEAKING state
                setOrbState("SPEAKING");

                // Start TTS speech (ONLY the <coreResponse> essential spoken part)
                if (tts != null && isTtsReady && ttsText != null && !ttsText.trim().isEmpty()) {
                    try {
                        Bundle params = new Bundle();
                        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "MarvoTTS");
                        tts.speak(ttsText.replace('\n', ' '), TextToSpeech.QUEUE_FLUSH, params, "MarvoTTS");
                    } catch (Exception e) {
                        try {
                            tts.speak(ttsText.replace('\n', ' '), TextToSpeech.QUEUE_FLUSH, null);
                        } catch (Exception ignored) {}
                    }
                }

                // Start character-by-character typewriter effect for the full text
                startTypewriter(displayText != null && !displayText.isEmpty() ? displayText : ttsText);
            }
        });
    }

    /**
     * Step 1/20: XML Tag Parser for <coreResponse>...</coreResponse>.
     * Extracts only the essential spoken response inside the tag for TTS.
     */
    private String extractCoreResponse(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) return "";
        Pattern pattern = Pattern.compile("<coreResponse>([\\s\\S]*?)</coreResponse>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(rawText);
        if (matcher.find()) {
            String core = matcher.group(1).trim();
            if (!core.isEmpty()) {
                return core;
            }
        }
        // Fallback: If model did not enclose in tags, return full text
        return rawText.trim();
    }

    /**
     * Types out the response character-by-character on the UI thread.
     */
    private void startTypewriter(final String fullText) {
        if (fullText == null || statusTextView == null) return;

        if (typewriterRunnable != null) {
            typewriterHandler.removeCallbacks(typewriterRunnable);
        }

        final int totalLen = fullText.length();
        final long delayPerChar = totalLen > 100 ? 18 : 28;

        typewriterRunnable = new Runnable() {
            private int charIndex = 0;
            @Override
            public void run() {
                if (statusTextView != null && charIndex <= totalLen) {
                    statusTextView.setText(fullText.substring(0, charIndex));
                    charIndex++;
                    if (statusScrollView != null) {
                        statusScrollView.post(new Runnable() {
                            @Override
                            public void run() {
                                statusScrollView.fullScroll(View.FOCUS_DOWN);
                            }
                        });
                    }
                    if (charIndex <= totalLen) {
                        typewriterHandler.postDelayed(this, delayPerChar);
                    }
                }
            }
        };
        typewriterHandler.post(typewriterRunnable);
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

    void startListening() {
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
        if (audioPollHandler != null && audioPollRunnable != null) {
            audioPollHandler.removeCallbacks(audioPollRunnable);
            audioPollHandler = null;
        }
        if (pillDismissRunnable != null) {
            pillHandler.removeCallbacks(pillDismissRunnable);
        }
        if (typewriterRunnable != null) {
            typewriterHandler.removeCallbacks(typewriterRunnable);
        }
        if (orbWebView != null) {
            orbWebView.loadUrl("about:blank");
            orbWebView.destroy();
            orbWebView = null;
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception e) {
                Log.e(TAG, "Error shutting down TTS: " + e.getMessage(), e);
            }
            tts = null;
            isTtsReady = false;
        }
    }
}
