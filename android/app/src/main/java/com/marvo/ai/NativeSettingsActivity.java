package com.marvo.ai;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import org.json.JSONObject;

/**
 * Step 23: Production-Grade Categorized Native Settings & Live Download Manager.
 * 100% Programmatic Android Layout — Clean Tabbed Category Navigation:
 *   - 🎨 Appearance & 3D Orb (Eye color, animations, theme)
 *   - 🧠 Offline Brain & AI Models (Model status, live progress bar, pause/cancel controls)
 *   - 🔊 Voice & Audio (TTS voices, active listening hours)
 *   - ⚙️ System & Memory (Teach AI / Custom Q&A, cache & storage breakdown)
 */
public class NativeSettingsActivity extends Activity {
    private static final String TAG = "NativeSettingsActivity";

    // Category Tabs
    private static final int TAB_APPEARANCE = 0;
    private static final int TAB_BRAIN = 1;
    private static final int TAB_VOICE = 2;
    private static final int TAB_SYSTEM = 3;
    private int currentTab = TAB_BRAIN; // Default to Offline Brain so download manager is immediately accessible

    private Button[] tabButtons = new Button[4];
    private LinearLayout[] tabContainers = new LinearLayout[4];

    // 🎨 Appearance Components
    private TextView activeColorLabel;

    // 🧠 Offline Brain Components
    private TextView brainStatusLabel;
    private TextView brainProgressLabel;
    private ProgressBar brainProgressBar;
    private Button brainDownloadBtn;
    private Button brainPauseBtn;
    private Button brainCancelBtn;
    private TextView brainStorageInfoLabel;
    private CheckBox allowMeteredCheckBox;

    // 🔊 Voice Components
    private Spinner voiceProfileSpinner;
    private EditText activeStartTimeInput;
    private EditText activeEndTimeInput;

    // ⚙️ System & Memory Components
    private EditText teachQuestionInput;
    private EditText teachAnswerInput;
    private TextView qaCountLabel;
    private TextView storageBreakdownLabel;

    private final Handler progressPollHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressPollRunnable = new Runnable() {
        @Override
        public void run() {
            updateOfflineBrainUI();
            progressPollHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Immersive status bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(Color.parseColor("#12131C"));
        }

        ScrollView mainScrollView = new ScrollView(this);
        mainScrollView.setBackgroundColor(Color.parseColor("#12131C"));
        mainScrollView.setFillViewport(true);

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(dp(20), dp(36), dp(20), dp(36));

        // ===== TOP BAR: TITLE & CLOSE =====
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleColParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        titleCol.setLayoutParams(titleColParams);

        TextView titleText = new TextView(this);
        titleText.setText("Marvo Settings");
        titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        titleText.setTextColor(Color.WHITE);
        titleText.setTypeface(Typeface.DEFAULT_BOLD);
        titleCol.addView(titleText);

        TextView subTitle = new TextView(this);
        subTitle.setText("System & Intelligence Control");
        subTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        subTitle.setTextColor(Color.parseColor("#8E8EA8"));
        titleCol.addView(subTitle);

        topBar.addView(titleCol);

        Button closeBtn = createCloseButton("✕");
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        topBar.addView(closeBtn);

        rootLayout.addView(topBar, createFullWidthParams());
        addSpacer(rootLayout, 20);

        // ===== CATEGORY TAB BAR (Horizontal Scrolling) =====
        HorizontalScrollView tabScrollView = new HorizontalScrollView(this);
        tabScrollView.setHorizontalScrollBarEnabled(false);

        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);

        String[] tabTitles = {
            "🎨 Appearance",
            "🧠 Offline Brain",
            "🔊 Voice & Audio",
            "⚙️ System"
        };

        for (int i = 0; i < 4; i++) {
            final int index = i;
            Button tabBtn = new Button(this);
            tabBtn.setText(tabTitles[i]);
            tabBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tabBtn.setTypeface(Typeface.DEFAULT_BOLD);
            tabBtn.setAllCaps(false);
            tabBtn.setPadding(dp(16), dp(10), dp(16), dp(10));
            LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            tabLp.setMargins(0, 0, dp(8), 0);
            tabBtn.setLayoutParams(tabLp);

            tabBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchTab(index);
                }
            });

            tabButtons[i] = tabBtn;
            tabRow.addView(tabBtn);
        }

        tabScrollView.addView(tabRow);
        rootLayout.addView(tabScrollView, createFullWidthParams());
        addSpacer(rootLayout, 20);

        // ===== TAB CONTAINERS =====
        tabContainers[TAB_APPEARANCE] = buildAppearanceSection();
        tabContainers[TAB_BRAIN] = buildOfflineBrainSection();
        tabContainers[TAB_VOICE] = buildVoiceSection();
        tabContainers[TAB_SYSTEM] = buildSystemSection();

        for (int i = 0; i < 4; i++) {
            rootLayout.addView(tabContainers[i], createFullWidthParams());
        }

        // Initialize active tab
        switchTab(TAB_BRAIN);

        mainScrollView.addView(rootLayout);
        setContentView(mainScrollView);
    }

    private void switchTab(int tabIndex) {
        currentTab = tabIndex;
        for (int i = 0; i < 4; i++) {
            if (i == tabIndex) {
                tabContainers[i].setVisibility(View.VISIBLE);
                tabButtons[i].setBackground(createRoundedRectBg("#7D3C98", 14));
                tabButtons[i].setTextColor(Color.WHITE);
            } else {
                tabContainers[i].setVisibility(View.GONE);
                tabButtons[i].setBackground(createRoundedRectBg("#1E1F2E", 14));
                tabButtons[i].setTextColor(Color.parseColor("#9E9EB8"));
            }
        }
    }

    // =========================================================================
    // SECTION 1: 🎨 APPEARANCE & 3D ORB (STEP 29: NESTED CATEGORIES)
    // =========================================================================
    private LinearLayout buildAppearanceSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);

        section.addView(createSectionHeader("Appearance & Customization 🎨"));
        addSpacer(section, 6);
        section.addView(createSectionDescription("Select AI persona characters, customize 3D eye visuals, and access interactive playground engines."));
        addSpacer(section, 16);

        // ── 1. [ 🎭 Characters ] (Folder for AI Personas) ──
        LinearLayout charCard = createCardContainer();
        charCard.addView(createCardTitle("[ 🎭 Characters ] (AI Personas)"));
        addSpacer(charCard, 4);
        charCard.addView(createSubText("10+ reactive character avatars that dynamically respond to AI Idle, Thinking, and Speaking states."));
        addSpacer(charCard, 10);

        TextView charListText = createSubText("Active Roster: Spider-Man, Zen Monk, Iron Man, Hulk, Batman, Wolverine, Deadpool, Naruto, Goku, Cyberpunk.");
        charListText.setTextColor(Color.parseColor("#00F0FF"));
        charCard.addView(charListText);
        addSpacer(charCard, 10);

        Button manageCharsBtn = createSecondaryButton("Switch Character Persona");
        manageCharsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(NativeSettingsActivity.this, "Open main app sidebar to choose from 10+ Character Avatars.", Toast.LENGTH_SHORT).show();
            }
        });
        charCard.addView(manageCharsBtn, createFullWidthParams());
        section.addView(charCard, createFullWidthParams());
        addSpacer(section, 16);

        // ── 2. [ 👁️ 3D Eye ] (Folder for Visual Orb/Eye Configurations) ──
        LinearLayout eyeCard = createCardContainer();
        eyeCard.addView(createCardTitle("[ 👁️ 3D Eye ] (Orb & Visuals)"));
        addSpacer(eyeCard, 4);
        activeColorLabel = createSubText("Current Glow: " + MemoryVault.getEyeColor(this).toUpperCase());
        eyeCard.addView(activeColorLabel);
        addSpacer(eyeCard, 12);

        LinearLayout colorSwatchesRow = new LinearLayout(this);
        colorSwatchesRow.setOrientation(LinearLayout.HORIZONTAL);
        colorSwatchesRow.setGravity(Gravity.CENTER_VERTICAL);
        colorSwatchesRow.setLayoutParams(createFullWidthParams());

        final String[][] colors = {
            {"neon-blue", "#00f0ff", "Neon Blue"},
            {"cyber-red", "#ff0055", "Cyber Red"},
            {"emerald", "#00ff88", "Emerald"},
            {"amethyst", "#b5179e", "Amethyst"},
            {"starlight", "#ffffff", "Starlight"}
        };

        for (final String[] c : colors) {
            Button swatch = new Button(this);
            swatch.setText("");
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(Color.parseColor(c[1]));
            gd.setCornerRadius(dp(18));
            swatch.setBackground(gd);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(36), dp(36));
            lp.setMargins(0, 0, dp(14), 0);
            swatch.setLayoutParams(lp);

            swatch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    MemoryVault.setEyeColor(NativeSettingsActivity.this, c[0]);
                    activeColorLabel.setText("Current Glow: " + c[2].toUpperCase());
                    Toast.makeText(NativeSettingsActivity.this, "Orb eye color set to " + c[2], Toast.LENGTH_SHORT).show();
                }
            });

            colorSwatchesRow.addView(swatch);
        }

        eyeCard.addView(colorSwatchesRow);
        addSpacer(eyeCard, 14);

        Button testOverlayBtn = createSecondaryButton("Launch Floating 3D Eye Overlay");
        testOverlayBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent intent = new Intent(NativeSettingsActivity.this, FloatingOrbService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent);
                    } else {
                        startService(intent);
                    }
                    Toast.makeText(NativeSettingsActivity.this, "Floating Orb Overlay Launched!", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(NativeSettingsActivity.this, "Overlay permission required in system settings.", Toast.LENGTH_LONG).show();
                }
            }
        });
        eyeCard.addView(testOverlayBtn, createFullWidthParams());
        section.addView(eyeCard, createFullWidthParams());
        addSpacer(section, 16);

        // ── 3. [ 🎮 Playground ] (Interactive Labs & [ ♟️ Chess ]) ──
        LinearLayout playCard = createCardContainer();
        playCard.addView(createCardTitle("[ 🎮 Playground ] (Interactive Labs)"));
        addSpacer(playCard, 4);
        playCard.addView(createSubText("Experiment with interactive expressions, tactical AI challenges, and future engine labs."));
        addSpacer(playCard, 12);

        // Sub-item: [ ♟️ Chess ] Placeholder
        LinearLayout chessSubItem = new LinearLayout(this);
        chessSubItem.setOrientation(LinearLayout.VERTICAL);
        chessSubItem.setBackground(createRoundedRectBg("#1A1B28", 12));
        chessSubItem.setPadding(dp(12), dp(12), dp(12), dp(12));

        TextView chessTitle = new TextView(this);
        chessTitle.setText("♟️ Chess Engine (Local Stockfish Lab)");
        chessTitle.setTextColor(Color.parseColor("#FFAA00"));
        chessTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        chessTitle.setTypeface(null, Typeface.BOLD);
        chessSubItem.addView(chessTitle);
        addSpacer(chessSubItem, 4);

        TextView chessDesc = createSubText("Offline neural chess engine placeholder. Prepare local board evaluations, move calculation, and on-device tactical bot.");
        chessSubItem.addView(chessDesc);
        addSpacer(chessSubItem, 10);

        Button previewChessBtn = createSecondaryButton("♟️ Preview Chess Engine (Coming in Step 30)");
        previewChessBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(NativeSettingsActivity.this, "♟️ Local Neural Chess Engine placeholder active! Full Stockfish engine arriving in Step 30.", Toast.LENGTH_LONG).show();
            }
        });
        chessSubItem.addView(previewChessBtn, createFullWidthParams());

        playCard.addView(chessSubItem, createFullWidthParams());
        section.addView(playCard, createFullWidthParams());

        return section;
    }

    // =========================================================================
    // SECTION 2: 🧠 OFFLINE BRAIN & AI MODELS (LIVE DOWNLOAD MANAGER)
    // =========================================================================
    private LinearLayout buildOfflineBrainSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);

        section.addView(createSectionHeader("Offline Brain & AI Models 🧠"));
        addSpacer(section, 6);
        section.addView(createSectionDescription("High-intelligence local GGUF reasoning engine (~1.8GB). Operates 100% offline with zero internet."));
        addSpacer(section, 16);

        // Card: Live Download Card
        LinearLayout downloadCard = createCardContainer();
        downloadCard.addView(createCardTitle("Offline Neural Model"));
        addSpacer(downloadCard, 4);

        brainStatusLabel = new TextView(this);
        brainStatusLabel.setText("Status: Checking...");
        brainStatusLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        brainStatusLabel.setTextColor(Color.parseColor("#00E5FF"));
        brainStatusLabel.setTypeface(Typeface.DEFAULT_BOLD);
        downloadCard.addView(brainStatusLabel);

        addSpacer(downloadCard, 8);

        brainProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        brainProgressBar.setMax(100);
        brainProgressBar.setProgress(0);
        downloadCard.addView(brainProgressBar, createFullWidthParams());

        addSpacer(downloadCard, 8);

        brainProgressLabel = new TextView(this);
        brainProgressLabel.setText("Progress: 0% (0 MB / ~1800 MB)");
        brainProgressLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        brainProgressLabel.setTextColor(Color.parseColor("#CCCCCC"));
        downloadCard.addView(brainProgressLabel);

        addSpacer(downloadCard, 14);

        // Buttons Bar: Download / Pause / Cancel
        LinearLayout btnBar = new LinearLayout(this);
        btnBar.setOrientation(LinearLayout.VERTICAL);

        brainDownloadBtn = createPrimaryButton("Download Offline Brain (2.2GB)");
        brainDownloadBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (OfflineBrainDownloader.getInstance().isModelDownloaded(NativeSettingsActivity.this)) {
                    Toast.makeText(NativeSettingsActivity.this, "Model verified: Already ready for offline use!", Toast.LENGTH_SHORT).show();
                    return;
                }

                boolean allowMetered = allowMeteredCheckBox != null && allowMeteredCheckBox.isChecked();
                Toast.makeText(NativeSettingsActivity.this, "Starting download with live notification...", Toast.LENGTH_SHORT).show();
                OfflineBrainDownloader.startForegroundDownloadService(NativeSettingsActivity.this, allowMetered);
                updateOfflineBrainUI();
            }
        });
        btnBar.addView(brainDownloadBtn, createFullWidthParams());

        addSpacer(btnBar, 8);

        LinearLayout secondaryBtns = new LinearLayout(this);
        secondaryBtns.setOrientation(LinearLayout.HORIZONTAL);

        brainPauseBtn = createSecondaryButton("Pause");
        brainPauseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent pauseIntent = new Intent(NativeSettingsActivity.this, ModelDownloadService.class);
                pauseIntent.setAction(ModelDownloadService.ACTION_PAUSE);
                startService(pauseIntent);
                Toast.makeText(NativeSettingsActivity.this, "Download paused.", Toast.LENGTH_SHORT).show();
                updateOfflineBrainUI();
            }
        });
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        p1.setMargins(0, 0, dp(6), 0);
        brainPauseBtn.setLayoutParams(p1);
        secondaryBtns.addView(brainPauseBtn);

        brainCancelBtn = createSecondaryButton("Cancel");
        brainCancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent cancelIntent = new Intent(NativeSettingsActivity.this, ModelDownloadService.class);
                cancelIntent.setAction(ModelDownloadService.ACTION_CANCEL);
                startService(cancelIntent);
                Toast.makeText(NativeSettingsActivity.this, "Download canceled.", Toast.LENGTH_SHORT).show();
                updateOfflineBrainUI();
            }
        });
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        p2.setMargins(dp(6), 0, 0, 0);
        brainCancelBtn.setLayoutParams(p2);
        secondaryBtns.addView(brainCancelBtn);

        btnBar.addView(secondaryBtns, createFullWidthParams());
        downloadCard.addView(btnBar, createFullWidthParams());

        addSpacer(downloadCard, 12);

        allowMeteredCheckBox = new CheckBox(this);
        allowMeteredCheckBox.setText("Allow download on Mobile Data (Unmetered Wi-Fi not required)");
        allowMeteredCheckBox.setTextColor(Color.parseColor("#CCCCCC"));
        allowMeteredCheckBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        downloadCard.addView(allowMeteredCheckBox, createFullWidthParams());

        section.addView(downloadCard, createFullWidthParams());
        addSpacer(section, 16);

        // Card: Storage Specs Info
        LinearLayout specsCard = createCardContainer();
        specsCard.addView(createCardTitle("Model Specifications & Target"));
        addSpacer(specsCard, 8);

        brainStorageInfoLabel = createSubText("Model Name: " + OfflineBrainDownloader.DEFAULT_MODEL_NAME + "\nTarget Folder: " + MemoryVault.getModelsDir(this).getAbsolutePath() + "\nStorage: " + getAvailableDiskSpaceMb() + " MB Available");
        specsCard.addView(brainStorageInfoLabel);

        section.addView(specsCard, createFullWidthParams());
        return section;
    }

    // =========================================================================
    // SECTION 3: 🔊 VOICE & AUDIO
    // =========================================================================
    private LinearLayout buildVoiceSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);

        section.addView(createSectionHeader("Voice & Audio 🔊"));
        addSpacer(section, 6);
        section.addView(createSectionDescription("Configure Marvo's vocal synthesis personality and active assistant hours."));
        addSpacer(section, 16);

        // Card: Voice Persona Selector
        LinearLayout voiceCard = createCardContainer();
        voiceCard.addView(createCardTitle("Vocal Synthesis Personality"));
        addSpacer(voiceCard, 10);

        String[] voiceProfiles = {
            "Profile 1: Male English (Madhur / Natural)",
            "Profile 2: Male Hindi (Rohan / Clear)",
            "Profile 3: Female English (Aria / Sweet)",
            "Profile 4: Female Hindi (Swara / Expressive)"
        };

        voiceProfileSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, voiceProfiles);
        voiceProfileSpinner.setAdapter(adapter);

        int currentProfile = MemoryVault.getVoiceProfile(this);
        if (currentProfile >= 1 && currentProfile <= 4) {
            voiceProfileSpinner.setSelection(currentProfile - 1);
        }

        voiceProfileSpinner.setBackground(createRoundedRectBg("#2D2D44", 10));
        voiceProfileSpinner.setPadding(dp(12), dp(10), dp(12), dp(10));
        voiceCard.addView(voiceProfileSpinner, createFullWidthParams());

        addSpacer(voiceCard, 12);

        Button saveVoiceBtn = createPrimaryButton("Save Voice Profile");
        saveVoiceBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int selectedProfile = voiceProfileSpinner.getSelectedItemPosition() + 1;
                MemoryVault.setVoiceProfile(NativeSettingsActivity.this, selectedProfile);
                Toast.makeText(NativeSettingsActivity.this, "Voice Profile " + selectedProfile + " saved!", Toast.LENGTH_SHORT).show();
            }
        });
        voiceCard.addView(saveVoiceBtn, createFullWidthParams());

        section.addView(voiceCard, createFullWidthParams());
        addSpacer(section, 16);

        // Card: Active Time Window
        LinearLayout timeCard = createCardContainer();
        timeCard.addView(createCardTitle("Active Listening Window"));
        addSpacer(timeCard, 4);
        timeCard.addView(createSubText("Sets permissible operating hours (24-hour HH:MM format)."));
        addSpacer(timeCard, 10);

        timeCard.addView(createLabel("Start Time"));
        addSpacer(timeCard, 4);
        activeStartTimeInput = createTextInput("06:00");
        activeStartTimeInput.setText(MemoryVault.getActiveStartTime(this));
        timeCard.addView(activeStartTimeInput, createFullWidthParams());

        addSpacer(timeCard, 10);

        timeCard.addView(createLabel("End Time"));
        addSpacer(timeCard, 4);
        activeEndTimeInput = createTextInput("23:00");
        activeEndTimeInput.setText(MemoryVault.getActiveEndTime(this));
        timeCard.addView(activeEndTimeInput, createFullWidthParams());

        addSpacer(timeCard, 12);

        Button saveTimeBtn = createPrimaryButton("Save Active Window");
        saveTimeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String startTime = activeStartTimeInput.getText().toString().trim();
                String endTime = activeEndTimeInput.getText().toString().trim();

                if (!isValidTimeFormat(startTime) || !isValidTimeFormat(endTime)) {
                    Toast.makeText(NativeSettingsActivity.this, "Invalid format! Please use HH:MM (e.g. 06:00)", Toast.LENGTH_SHORT).show();
                    return;
                }

                MemoryVault.setActiveStartTime(NativeSettingsActivity.this, startTime);
                MemoryVault.setActiveEndTime(NativeSettingsActivity.this, endTime);
                Toast.makeText(NativeSettingsActivity.this, "Active Window saved: " + startTime + " - " + endTime, Toast.LENGTH_SHORT).show();
            }
        });
        timeCard.addView(saveTimeBtn, createFullWidthParams());

        section.addView(timeCard, createFullWidthParams());
        return section;
    }

    // =========================================================================
    // SECTION 4: ⚙️ SYSTEM & MEMORY
    // =========================================================================
    private LinearLayout buildSystemSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);

        section.addView(createSectionHeader("System & Memory ⚙️"));
        addSpacer(section, 6);
        section.addView(createSectionDescription("Teach AI custom facts, monitor storage hierarchies, and clean caches."));
        addSpacer(section, 16);

        // Card: Teach AI
        LinearLayout teachCard = createCardContainer();
        teachCard.addView(createCardTitle("Teach AI (Custom Knowledge Base)"));
        addSpacer(teachCard, 4);
        teachCard.addView(createSubText("Answers defined here take immediate priority over offline and online brains."));
        addSpacer(teachCard, 12);

        teachCard.addView(createLabel("User Question (Trigger)"));
        addSpacer(teachCard, 4);
        teachQuestionInput = createTextInput("e.g. Mera birthday kab hai?");
        teachCard.addView(teachQuestionInput, createFullWidthParams());

        addSpacer(teachCard, 10);

        teachCard.addView(createLabel("Marvo Response (Answer)"));
        addSpacer(teachCard, 4);
        teachAnswerInput = createTextInput("e.g. Aapka birthday 15 March ko hai!");
        teachAnswerInput.setMinLines(2);
        teachCard.addView(teachAnswerInput, createFullWidthParams());

        addSpacer(teachCard, 12);

        Button saveQABtn = createPrimaryButton("Save Knowledge Pair");
        saveQABtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String question = teachQuestionInput.getText().toString().trim();
                String answer = teachAnswerInput.getText().toString().trim();

                if (question.isEmpty() || answer.isEmpty()) {
                    Toast.makeText(NativeSettingsActivity.this, "Please enter both Question and Answer!", Toast.LENGTH_SHORT).show();
                    return;
                }

                MemoryVault.saveCustomQA(NativeSettingsActivity.this, question, answer);
                Toast.makeText(NativeSettingsActivity.this, "Saved! Marvo learned this answer.", Toast.LENGTH_SHORT).show();
                teachQuestionInput.setText("");
                teachAnswerInput.setText("");
                updateStorageLabels();
            }
        });
        teachCard.addView(saveQABtn, createFullWidthParams());

        section.addView(teachCard, createFullWidthParams());
        addSpacer(section, 16);

        // Card: Storage Breakdown & Clean-Up
        LinearLayout storageCard = createCardContainer();
        storageCard.addView(createCardTitle("Storage Hierarchy & Cache"));
        addSpacer(storageCard, 6);

        storageBreakdownLabel = createSubText("Calculating storage breakdown...");
        storageCard.addView(storageBreakdownLabel);

        addSpacer(storageCard, 14);

        Button clearCacheBtn = createSecondaryButton("Clear Temporary Cache (/cache/)");
        clearCacheBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File cacheDir = MemoryVault.getCacheDir(NativeSettingsActivity.this);
                deleteDirectoryFiles(cacheDir);
                Toast.makeText(NativeSettingsActivity.this, "Temporary cache cleared!", Toast.LENGTH_SHORT).show();
                updateStorageLabels();
            }
        });
        storageCard.addView(clearCacheBtn, createFullWidthParams());

        addSpacer(storageCard, 8);

        Button clearHistoryBtn = createSecondaryButton("Reset Conversation Turns (/memory/)");
        clearHistoryBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MemoryVault.clearConversationHistory(NativeSettingsActivity.this);
                Toast.makeText(NativeSettingsActivity.this, "Conversation history reset!", Toast.LENGTH_SHORT).show();
                updateStorageLabels();
            }
        });
        storageCard.addView(clearHistoryBtn, createFullWidthParams());

        section.addView(storageCard, createFullWidthParams());
        updateStorageLabels();
        return section;
    }

    private void updateOfflineBrainUI() {
        if (brainStatusLabel == null || brainProgressLabel == null || brainProgressBar == null) return;

        JSONObject progressJson = OfflineBrainDownloader.getInstance().getDownloadProgress(this);
        String status = progressJson.optString("status", "idle");
        int progress = progressJson.optInt("progress", 0);
        boolean isReady = progressJson.optBoolean("isReady", false);
        long downloaded = progressJson.optLong("downloadedBytes", 0);
        long total = progressJson.optLong("totalBytes", 0);

        long dlMb = downloaded / (1024 * 1024);
        long totMb = total > 0 ? (total / (1024 * 1024)) : 2200;

        if (isReady || "completed".equalsIgnoreCase(status) || OfflineBrainDownloader.getInstance().isModelDownloaded(this)) {
            brainStatusLabel.setText("Status: Model Ready (Offline Active)");
            brainStatusLabel.setTextColor(Color.parseColor("#2ECC71"));
            brainProgressBar.setProgress(100);
            brainProgressLabel.setText("Model: " + OfflineBrainDownloader.DEFAULT_MODEL_NAME + " (~2.2GB verified)");
            brainDownloadBtn.setText("Model Installed & Ready");
            brainDownloadBtn.setEnabled(true);
            if (brainPauseBtn != null) brainPauseBtn.setVisibility(View.GONE);
            if (brainCancelBtn != null) brainCancelBtn.setVisibility(View.GONE);
        } else if ("downloading".equalsIgnoreCase(status)) {
            brainStatusLabel.setText("Status: Downloading... (" + progress + "%)");
            brainStatusLabel.setTextColor(Color.parseColor("#F39C12"));
            brainProgressBar.setProgress(progress);
            brainProgressLabel.setText("Downloaded: " + dlMb + " MB / " + totMb + " MB (" + progress + "%)");
            brainDownloadBtn.setText("Downloading in Background...");
            if (brainPauseBtn != null) brainPauseBtn.setVisibility(View.VISIBLE);
            if (brainCancelBtn != null) brainCancelBtn.setVisibility(View.VISIBLE);
        } else if ("paused_wifi".equalsIgnoreCase(status) || "paused".equalsIgnoreCase(status)) {
            brainStatusLabel.setText("Status: Paused");
            brainStatusLabel.setTextColor(Color.parseColor("#E67E22"));
            brainProgressBar.setProgress(progress);
            brainProgressLabel.setText("Downloaded: " + dlMb + " MB / " + totMb + " MB. Tap below to resume.");
            brainDownloadBtn.setText("Resume Download");
            if (brainPauseBtn != null) brainPauseBtn.setVisibility(View.VISIBLE);
            if (brainCancelBtn != null) brainCancelBtn.setVisibility(View.VISIBLE);
        } else if ("failed".equalsIgnoreCase(status)) {
            brainStatusLabel.setText("Status: Download Interrupted");
            brainStatusLabel.setTextColor(Color.parseColor("#E74C3C"));
            brainProgressLabel.setText("Download encountered a network glitch. Tap below to retry.");
            brainDownloadBtn.setText("Retry Download (1.8GB)");
            if (brainPauseBtn != null) brainPauseBtn.setVisibility(View.GONE);
            if (brainCancelBtn != null) brainCancelBtn.setVisibility(View.VISIBLE);
        } else {
            brainStatusLabel.setText("Status: Not Downloaded");
            brainStatusLabel.setTextColor(Color.parseColor("#8E8EA8"));
            brainProgressBar.setProgress(0);
            brainProgressLabel.setText("Requires ~1.8GB free internal storage. Wi-Fi recommended.");
            brainDownloadBtn.setText("Download Offline Brain (1.8GB)");
            if (brainPauseBtn != null) brainPauseBtn.setVisibility(View.GONE);
            if (brainCancelBtn != null) brainCancelBtn.setVisibility(View.GONE);
        }
    }

    private void updateStorageLabels() {
        if (storageBreakdownLabel == null) return;
        try {
            File modelsDir = MemoryVault.getModelsDir(this);
            File memoryDir = MemoryVault.getMemoryDir(this);
            File cacheDir = MemoryVault.getCacheDir(this);

            long modelsSize = getDirSizeMb(modelsDir);
            long memorySize = getDirSizeMb(memoryDir);
            long cacheSize = getDirSizeMb(cacheDir);

            storageBreakdownLabel.setText(
                "• /models/: " + modelsSize + " MB (Offline Model storage)\n" +
                "• /memory/: " + memorySize + " MB (Custom Q&A + Context Vault)\n" +
                "• /cache/: " + cacheSize + " MB (Temporary chunk buffers)"
            );
        } catch (Exception ignored) {}
    }

    private long getDirSizeMb(File dir) {
        if (dir == null || !dir.exists()) return 0;
        long bytes = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) bytes += f.length();
            }
        }
        return bytes / (1024 * 1024);
    }

    private void deleteDirectoryFiles(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) f.delete();
            }
        }
    }

    private long getAvailableDiskSpaceMb() {
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            long bytesAvailable = stat.getBlockSizeLong() * stat.getAvailableBlocksLong();
            return bytesAvailable / (1024 * 1024);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateOfflineBrainUI();
        progressPollHandler.postDelayed(progressPollRunnable, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        progressPollHandler.removeCallbacks(progressPollRunnable);
    }

    // ===== UI Component Helpers =====

    private int dp(int dpValue) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dpValue,
            getResources().getDisplayMetrics()
        );
    }

    private LinearLayout createCardContainer() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(createRoundedRectBg("#1A1B28", 16));
        return card;
    }

    private TextView createSectionHeader(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        tv.setTextColor(Color.WHITE);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private TextView createSectionDescription(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTextColor(Color.parseColor("#8E8EA8"));
        return tv;
    }

    private TextView createCardTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTextColor(Color.WHITE);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private TextView createSubText(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTextColor(Color.parseColor("#8E8EA8"));
        return tv;
    }

    private TextView createLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTextColor(Color.parseColor("#CCCCCC"));
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private EditText createTextInput(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setHintTextColor(Color.parseColor("#5A5B72"));
        et.setTextColor(Color.WHITE);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setPadding(dp(14), dp(12), dp(14), dp(12));
        et.setBackground(createRoundedRectBg("#252538", 10));
        return et;
    }

    private Button createPrimaryButton(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setAllCaps(false);
        btn.setPadding(dp(14), dp(12), dp(14), dp(12));
        btn.setBackground(createRoundedRectBg("#7D3C98", 12));
        return btn;
    }

    private Button createSecondaryButton(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.parseColor("#DCDCF0"));
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setAllCaps(false);
        btn.setPadding(dp(12), dp(10), dp(12), dp(10));
        btn.setBackground(createRoundedRectBg("#2E2F44", 12));
        return btn;
    }

    private Button createCloseButton(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setBackground(createRoundedRectBg("#252538", 18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(36), dp(36));
        btn.setLayoutParams(lp);
        return btn;
    }

    private void addSpacer(ViewGroup parent, int heightDp) {
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp)
        ));
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
