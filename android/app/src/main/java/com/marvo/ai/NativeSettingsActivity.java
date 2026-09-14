package com.marvo.ai;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import org.json.JSONObject;

/**
 * Step 15 - Part 2: Native Android Settings & "Teach AI" Knowledge Base.
 * 100% programmatic Java layout — zero XML or Capacitor dependency.
 * Provides:
 *   - Voice Profile Selector (Male/Female, Hindi/English)
 *   - Active Time Window (Start/End)
 *   - "Teach AI" Custom Q&A Section
 *   - Offline AI Brain Downloader & Live Progress Monitor
 */
public class NativeSettingsActivity extends Activity {
    private static final String TAG = "NativeSettingsActivity";

    // Voice Profile Spinner
    private Spinner voiceProfileSpinner;

    // Active Time Inputs
    private EditText activeStartTimeInput;
    private EditText activeEndTimeInput;

    // Teach AI Inputs
    private EditText teachQuestionInput;
    private EditText teachAnswerInput;

    // Offline AI Brain Downloader UI Components
    private TextView brainStatusLabel;
    private TextView brainProgressLabel;
    private Button brainActionBtn;
    private final Handler progressPollHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressPollRunnable = new Runnable() {
        @Override
        public void run() {
            updateOfflineBrainUI();
            progressPollHandler.postDelayed(this, 1500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Translucent status bar
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(Color.parseColor("#1A1A2E"));
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#1A1A2E"));
        scrollView.setFillViewport(true);

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(dp(24), dp(40), dp(24), dp(40));
        rootLayout.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);

        // ===== TITLE =====
        TextView titleText = createHeaderText("Marvo Settings", 28);
        rootLayout.addView(titleText);

        addSpacer(rootLayout, 24);

        // ===== SECTION 1: VOICE PROFILE =====
        rootLayout.addView(createSectionHeader("Voice Profile"));
        addSpacer(rootLayout, 8);
        rootLayout.addView(createSectionDescription("Select Marvo's voice personality."));
        addSpacer(rootLayout, 12);

        String[] voiceProfiles = {
            "Profile 1: Male English",
            "Profile 2: Male Hindi",
            "Profile 3: Female English",
            "Profile 4: Female Hindi"
        };

        voiceProfileSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, voiceProfiles);
        voiceProfileSpinner.setAdapter(adapter);

        // Pre-select current profile
        int currentProfile = MemoryVault.getVoiceProfile(this);
        if (currentProfile >= 1 && currentProfile <= 4) {
            voiceProfileSpinner.setSelection(currentProfile - 1);
        }

        GradientDrawable spinnerBg = createRoundedRectBg("#2D2D44", 12);
        voiceProfileSpinner.setBackground(spinnerBg);
        voiceProfileSpinner.setPadding(dp(12), dp(8), dp(12), dp(8));
        rootLayout.addView(voiceProfileSpinner, createFullWidthParams());

        addSpacer(rootLayout, 12);

        Button saveVoiceBtn = createButton("Save Voice Profile");
        saveVoiceBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int selectedProfile = voiceProfileSpinner.getSelectedItemPosition() + 1;
                MemoryVault.setVoiceProfile(NativeSettingsActivity.this, selectedProfile);
                Toast.makeText(NativeSettingsActivity.this, "Voice Profile " + selectedProfile + " saved!", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Voice profile saved: " + selectedProfile);
            }
        });
        rootLayout.addView(saveVoiceBtn, createFullWidthParams());

        addSpacer(rootLayout, 32);
        rootLayout.addView(createDivider());
        addSpacer(rootLayout, 24);

        // ===== SECTION 2: ACTIVE TIME WINDOW =====
        rootLayout.addView(createSectionHeader("Active Time Window"));
        addSpacer(rootLayout, 8);
        rootLayout.addView(createSectionDescription("Set when Marvo should actively listen. Format: HH:MM (24-hour)."));
        addSpacer(rootLayout, 12);

        rootLayout.addView(createLabel("Start Time"));
        addSpacer(rootLayout, 4);
        activeStartTimeInput = createTextInput("06:00");
        activeStartTimeInput.setText(MemoryVault.getActiveStartTime(this));
        rootLayout.addView(activeStartTimeInput, createFullWidthParams());

        addSpacer(rootLayout, 12);

        rootLayout.addView(createLabel("End Time"));
        addSpacer(rootLayout, 4);
        activeEndTimeInput = createTextInput("23:00");
        activeEndTimeInput.setText(MemoryVault.getActiveEndTime(this));
        rootLayout.addView(activeEndTimeInput, createFullWidthParams());

        addSpacer(rootLayout, 12);

        Button saveTimeBtn = createButton("Save Active Time");
        saveTimeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String startTime = activeStartTimeInput.getText().toString().trim();
                String endTime = activeEndTimeInput.getText().toString().trim();

                if (!isValidTimeFormat(startTime) || !isValidTimeFormat(endTime)) {
                    Toast.makeText(NativeSettingsActivity.this, "Invalid time format! Use HH:MM (e.g., 06:00)", Toast.LENGTH_SHORT).show();
                    return;
                }

                MemoryVault.setActiveStartTime(NativeSettingsActivity.this, startTime);
                MemoryVault.setActiveEndTime(NativeSettingsActivity.this, endTime);
                Toast.makeText(NativeSettingsActivity.this, "Active Time saved: " + startTime + " - " + endTime, Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Active time saved: " + startTime + " - " + endTime);
            }
        });
        rootLayout.addView(saveTimeBtn, createFullWidthParams());

        addSpacer(rootLayout, 32);
        rootLayout.addView(createDivider());
        addSpacer(rootLayout, 24);

        // ===== SECTION 3: TEACH AI (Custom Q&A) =====
        rootLayout.addView(createSectionHeader("Teach AI \uD83E\uDDE0"));
        addSpacer(rootLayout, 8);
        rootLayout.addView(createSectionDescription("Teach Marvo custom answers. These will be used BEFORE online AI."));
        addSpacer(rootLayout, 12);

        rootLayout.addView(createLabel("Question (What you'll ask)"));
        addSpacer(rootLayout, 4);
        teachQuestionInput = createTextInput("e.g., Mera birthday kab hai?");
        rootLayout.addView(teachQuestionInput, createFullWidthParams());

        addSpacer(rootLayout, 12);

        rootLayout.addView(createLabel("Answer (What Marvo should say)"));
        addSpacer(rootLayout, 4);
        teachAnswerInput = createTextInput("e.g., Aapka birthday 15 March ko hai!");
        teachAnswerInput.setMinLines(2);
        rootLayout.addView(teachAnswerInput, createFullWidthParams());

        addSpacer(rootLayout, 12);

        Button saveQABtn = createButton("Save Custom Answer");
        saveQABtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String question = teachQuestionInput.getText().toString().trim();
                String answer = teachAnswerInput.getText().toString().trim();

                if (question.isEmpty() || answer.isEmpty()) {
                    Toast.makeText(NativeSettingsActivity.this, "Please fill both Question and Answer!", Toast.LENGTH_SHORT).show();
                    return;
                }

                MemoryVault.saveCustomQA(NativeSettingsActivity.this, question, answer);
                Toast.makeText(NativeSettingsActivity.this, "Custom Q&A saved! Marvo will remember this.", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Custom QA saved: Q=" + question + " A=" + answer);

                teachQuestionInput.setText("");
                teachAnswerInput.setText("");
            }
        });
        rootLayout.addView(saveQABtn, createFullWidthParams());

        addSpacer(rootLayout, 32);
        rootLayout.addView(createDivider());
        addSpacer(rootLayout, 24);

        // ===== SECTION 4: OFFLINE AI BRAIN =====
        rootLayout.addView(createSectionHeader("Offline AI Brain \uD83E\uDDE0"));
        addSpacer(rootLayout, 8);
        rootLayout.addView(createSectionDescription("High-intelligence offline LLM model (~1.8GB). Enables Marvo to reason, answer queries, and execute commands offline without internet."));
        addSpacer(rootLayout, 12);

        LinearLayout brainCard = new LinearLayout(this);
        brainCard.setOrientation(LinearLayout.VERTICAL);
        brainCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        brainCard.setBackground(createRoundedRectBg("#2D2D44", 12));

        brainStatusLabel = new TextView(this);
        brainStatusLabel.setText("Status: Checking...");
        brainStatusLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        brainStatusLabel.setTextColor(Color.parseColor("#00E5FF"));
        brainStatusLabel.setTypeface(Typeface.DEFAULT_BOLD);
        brainCard.addView(brainStatusLabel);

        View brainCardSpacer = new View(this);
        brainCardSpacer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)));
        brainCard.addView(brainCardSpacer);

        brainProgressLabel = new TextView(this);
        brainProgressLabel.setText("Progress: 0% (0 MB / ~1800 MB)");
        brainProgressLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        brainProgressLabel.setTextColor(Color.parseColor("#CCCCCC"));
        brainCard.addView(brainProgressLabel);

        rootLayout.addView(brainCard, createFullWidthParams());

        addSpacer(rootLayout, 12);

        brainActionBtn = createButton("Download Offline Brain (1.8GB)");
        brainActionBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (OfflineBrainDownloader.getInstance().isModelDownloaded(NativeSettingsActivity.this)) {
                    Toast.makeText(NativeSettingsActivity.this, "Offline Brain is already installed & ready!", Toast.LENGTH_SHORT).show();
                    return;
                }

                JSONObject progress = OfflineBrainDownloader.getInstance().getDownloadProgress(NativeSettingsActivity.this);
                String status = progress.optString("status", "idle");
                if ("downloading".equalsIgnoreCase(status)) {
                    Toast.makeText(NativeSettingsActivity.this, "Download is already running in background...", Toast.LENGTH_SHORT).show();
                    return;
                }

                Toast.makeText(NativeSettingsActivity.this, "Starting Offline Brain download (1.8GB)...", Toast.LENGTH_LONG).show();
                OfflineBrainDownloader.getInstance().startDownload(NativeSettingsActivity.this, true);
                updateOfflineBrainUI();
            }
        });
        rootLayout.addView(brainActionBtn, createFullWidthParams());

        addSpacer(rootLayout, 32);
        rootLayout.addView(createDivider());
        addSpacer(rootLayout, 24);

        // ===== BACK BUTTON =====
        Button backBtn = createButton("Close Settings");
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        rootLayout.addView(backBtn, createFullWidthParams());

        addSpacer(rootLayout, 32);

        scrollView.addView(rootLayout);
        setContentView(scrollView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateOfflineBrainUI();
        progressPollHandler.postDelayed(progressPollRunnable, 1500);
    }

    @Override
    protected void onPause() {
        super.onPause();
        progressPollHandler.removeCallbacks(progressPollRunnable);
    }

    private void updateOfflineBrainUI() {
        if (brainStatusLabel == null || brainProgressLabel == null || brainActionBtn == null) return;

        JSONObject progressJson = OfflineBrainDownloader.getInstance().getDownloadProgress(this);
        String status = progressJson.optString("status", "idle");
        int progress = progressJson.optInt("progress", 0);
        boolean isReady = progressJson.optBoolean("isReady", false);
        long downloaded = progressJson.optLong("downloadedBytes", 0);
        long total = progressJson.optLong("totalBytes", 0);

        long dlMb = downloaded / (1024 * 1024);
        long totMb = total > 0 ? (total / (1024 * 1024)) : 1800;

        if (isReady || "completed".equalsIgnoreCase(status) || OfflineBrainDownloader.getInstance().isModelDownloaded(this)) {
            brainStatusLabel.setText("Status: ✓ Downloaded & Ready");
            brainStatusLabel.setTextColor(Color.parseColor("#2ECC71"));
            brainProgressLabel.setText("Model: " + OfflineBrainDownloader.DEFAULT_MODEL_NAME + " (~1.8GB installed)");
            brainActionBtn.setText("Offline Brain Installed");
        } else if ("downloading".equalsIgnoreCase(status)) {
            brainStatusLabel.setText("Status: Downloading... (" + progress + "%)");
            brainStatusLabel.setTextColor(Color.parseColor("#F39C12"));
            brainProgressLabel.setText("Downloaded: " + dlMb + " MB / " + totMb + " MB (" + progress + "%)");
            brainActionBtn.setText("Downloading in Background...");
        } else if ("paused_wifi".equalsIgnoreCase(status) || "paused".equalsIgnoreCase(status)) {
            brainStatusLabel.setText("Status: Paused (Waiting for Wi-Fi)");
            brainStatusLabel.setTextColor(Color.parseColor("#E67E22"));
            brainProgressLabel.setText("Wi-Fi required. Tap below to download now on mobile data.");
            brainActionBtn.setText("Download Now on Mobile Data");
        } else if ("failed".equalsIgnoreCase(status)) {
            brainStatusLabel.setText("Status: Download Failed / Interrupted");
            brainStatusLabel.setTextColor(Color.parseColor("#E74C3C"));
            brainProgressLabel.setText("An error occurred during download. Tap below to retry.");
            brainActionBtn.setText("Retry Download (1.8GB)");
        } else {
            brainStatusLabel.setText("Status: Not Downloaded");
            brainStatusLabel.setTextColor(Color.parseColor("#CCCCCC"));
            brainProgressLabel.setText("Requires ~1.8GB free storage. Fast Wi-Fi recommended.");
            brainActionBtn.setText("Download Offline Brain (1.8GB)");
        }
    }

    // ===== UI Helper Methods =====

    private int dp(int dpValue) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dpValue,
            getResources().getDisplayMetrics()
        );
    }

    private TextView createHeaderText(String text, int sizeSp) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        tv.setTextColor(Color.WHITE);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    private TextView createSectionHeader(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        tv.setTextColor(Color.parseColor("#9B59B6"));
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private TextView createSectionDescription(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTextColor(Color.parseColor("#AAAAAA"));
        return tv;
    }

    private TextView createLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setTextColor(Color.parseColor("#CCCCCC"));
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private EditText createTextInput(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setHintTextColor(Color.parseColor("#666666"));
        et.setTextColor(Color.WHITE);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setPadding(dp(14), dp(12), dp(14), dp(12));
        et.setBackground(createRoundedRectBg("#2D2D44", 10));
        et.setSingleLine(true);
        return et;
    }

    private Button createButton(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setAllCaps(false);
        btn.setPadding(dp(16), dp(14), dp(16), dp(14));
        btn.setBackground(createRoundedRectBg("#6C3483", 12));
        return btn;
    }

    private View createDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#3D3D5C"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
        );
        divider.setLayoutParams(lp);
        return divider;
    }

    private void addSpacer(LinearLayout parent, int heightDp) {
        View spacer = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp)
        );
        spacer.setLayoutParams(lp);
        parent.addView(spacer);
    }

    private GradientDrawable createRoundedRectBg(String colorHex, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(colorHex));
        gd.setCornerRadius(dp(radiusDp));
        return gd;
    }

    private LinearLayout.LayoutParams createFullWidthParams() {
        return new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private boolean isValidTimeFormat(String time) {
        if (time == null || !time.contains(":")) return false;
        try {
            String[] parts = time.split(":");
            int hour = Integer.parseInt(parts[0].trim());
            int min = Integer.parseInt(parts[1].trim());
            return hour >= 0 && hour <= 23 && min >= 0 && min <= 59;
        } catch (Exception e) {
            return false;
        }
    }
}

