package com.voiceoverlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class OverlayService extends Service {
    private static final String CHANNEL_ID = "VoiceOverlayChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String PREFS_NAME = "VoiceOverlayPrefs";
    private static final String KEY_HISTORY = "transcription_history";
    private static final String KEY_BUTTON_X = "button_position_x";
    private static final String KEY_BUTTON_Y = "button_position_y";
    private static final String KEY_AUDIO_QUALITY = "audio_quality";
    private static final String KEY_TRANSCRIPTION_MODE = "transcription_mode";

    private File currentAudioFile = null;
    private File historyFolder = null;

    private WindowManager windowManager;
    private ImageView mainButton;
    private View editorPanel;
    private AudioRecorder audioRecorder;
    private boolean isRecording = false;
    private boolean isPaused = false;
    private boolean isEditorOpen = false;
    private Handler mainHandler;
    private EditText transcriptionText;
    private ProgressBar processingIndicator;
    private TextView statusText;
    private Button recordBtn;
    private Button stopBtn;
    private Button cancelBtn;
    private Button pasteBtn;
    private Button clearBtn;
    private WindowManager.LayoutParams mainButtonParams;
    private long recordingStartTime = 0;
    private Runnable timerRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        android.util.Log.d("VoiceOverlay", "OverlayService onCreate called");

        createNotificationChannel();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification("Voice overlay is active"),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, createNotification("Voice overlay is active"));
        }

        audioRecorder = new AudioRecorder();

        // Load and apply audio quality settings
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String audioQuality = prefs.getString(KEY_AUDIO_QUALITY, "Low");
        audioRecorder.setQuality(audioQuality);

        // Create history folder
        historyFolder = new File(getFilesDir(), "recording_history");
        if (!historyFolder.exists()) {
            historyFolder.mkdirs();
        }

        mainHandler = new Handler(Looper.getMainLooper());
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Initialize timer runnable
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isRecording && !isPaused) {
                    long elapsedSeconds = (System.currentTimeMillis() - recordingStartTime) / 1000;
                    statusText.setText("🔴 Recording... " + formatTime(elapsedSeconds));
                    mainHandler.postDelayed(this, 1000);
                }
            }
        };

        createMainButton();
        createEditorPanel();
    }

    private String formatTime(long seconds) {
        long mins = seconds / 60;
        long secs = seconds % 60;
        return String.format("%d:%02d", mins, secs);
    }

    private void createMainButton() {
        mainButton = new ImageView(this);
        mainButton.setImageResource(android.R.drawable.ic_btn_speak_now);

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColors(new int[]{Color.parseColor("#667eea"), Color.parseColor("#764ba2")});
        mainButton.setBackground(shape);

        mainButton.setPadding(25, 25, 25, 25);
        mainButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mainButton.setColorFilter(Color.WHITE);
        mainButton.setElevation(10);

        mainButtonParams = new WindowManager.LayoutParams(
            130,
            130,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );

        mainButtonParams.gravity = Gravity.TOP | Gravity.START;

        // Load saved position
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        mainButtonParams.x = prefs.getInt(KEY_BUTTON_X, 50);
        mainButtonParams.y = prefs.getInt(KEY_BUTTON_Y, 200);

        mainButton.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean moved = false;
            private long pressStartTime = 0;
            private boolean isLongPressRecording = false;
            private Handler longPressHandler = new Handler(Looper.getMainLooper());
            private Runnable longPressRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!moved && !isEditorOpen) {
                        // Start long press recording
                        isLongPressRecording = true;
                        startQuickRecording();
                    }
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = mainButtonParams.x;
                        initialY = mainButtonParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        moved = false;
                        pressStartTime = System.currentTimeMillis();
                        isLongPressRecording = false;

                        // Start long press detection (500ms)
                        longPressHandler.postDelayed(longPressRunnable, 500);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);

                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            mainButtonParams.x = initialX + dx;
                            mainButtonParams.y = initialY + dy;
                            windowManager.updateViewLayout(mainButton, mainButtonParams);
                            moved = true;
                            // Cancel long press if moved
                            longPressHandler.removeCallbacks(longPressRunnable);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        // Save position if moved
                        if (moved) {
                            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putInt(KEY_BUTTON_X, mainButtonParams.x);
                            editor.putInt(KEY_BUTTON_Y, mainButtonParams.y);
                            editor.apply();
                        }

                        // Cancel long press detection
                        longPressHandler.removeCallbacks(longPressRunnable);

                        if (isLongPressRecording) {
                            // Stop quick recording and transcribe
                            stopQuickRecording();
                            isLongPressRecording = false;
                        } else if (!moved) {
                            // Regular tap - open editor
                            openEditor();
                        }
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                        longPressHandler.removeCallbacks(longPressRunnable);
                        if (isLongPressRecording) {
                            cancelQuickRecording();
                            isLongPressRecording = false;
                        }
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(mainButton, mainButtonParams);
    }

    private void createEditorPanel() {
        // Main container
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(25, 25, 25, 25);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(24);
        bg.setColor(Color.parseColor("#1c1c1e"));
        container.setBackground(bg);
        container.setElevation(16);

        // Top buttons row: Settings, History, Exit
        LinearLayout topButtonRow = new LinearLayout(this);
        topButtonRow.setOrientation(LinearLayout.HORIZONTAL);
        topButtonRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams topRowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        topRowParams.setMargins(0, 0, 0, 15);
        topButtonRow.setLayoutParams(topRowParams);

        Button topSettingsBtn = createButton("⚙ Settings", "#9C27B0");
        topSettingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettings();
            }
        });
        topButtonRow.addView(topSettingsBtn);

        Button topHistoryBtn = createButton("📜 History", "#00BCD4");
        topHistoryBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // If recording, stop it first
                if (isRecording) {
                    stopRecording();
                }
                showHistory();
            }
        });
        topButtonRow.addView(topHistoryBtn);

        Button topExitBtn = createButton("Exit", "#607D8B");
        topExitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeEditor();
            }
        });
        topButtonRow.addView(topExitBtn);

        container.addView(topButtonRow);

        // Status text
        statusText = new TextView(this);
        statusText.setText("⚫ Ready");
        statusText.setTextColor(Color.parseColor("#b4b4b4"));
        statusText.setTextSize(16);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 0, 0, 15);
        container.addView(statusText);

        // Processing indicator
        processingIndicator = new ProgressBar(this);
        processingIndicator.setIndeterminate(true);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        progressParams.setMargins(0, 0, 0, 15);
        processingIndicator.setLayoutParams(progressParams);
        processingIndicator.setVisibility(View.GONE);
        container.addView(processingIndicator);

        // Transcription text area
        transcriptionText = new EditText(this);
        transcriptionText.setHint("Transcriptions will appear here...");
        transcriptionText.setTextColor(Color.parseColor("#FFFFFF"));
        transcriptionText.setHintTextColor(Color.parseColor("#8E8E93"));
        transcriptionText.setTextSize(15);
        transcriptionText.setMinLines(8);
        transcriptionText.setMaxLines(12);
        transcriptionText.setGravity(Gravity.TOP | Gravity.START);
        transcriptionText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        transcriptionText.setPadding(18, 18, 18, 18);
        transcriptionText.setFocusable(true);
        transcriptionText.setFocusableInTouchMode(true);

        GradientDrawable textBg = new GradientDrawable();
        textBg.setCornerRadius(16);
        textBg.setColor(Color.parseColor("#2c2c2e"));
        textBg.setStroke(1, Color.parseColor("#38383a"));
        transcriptionText.setBackground(textBg);
        transcriptionText.setElevation(2);

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        textParams.setMargins(0, 0, 0, 20);
        transcriptionText.setLayoutParams(textParams);
        container.addView(transcriptionText);

        // Control buttons - Row 1: Abort, Pause/Resume, Append, Paste
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams row1Params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        row1Params.setMargins(0, 0, 0, 6);
        row1.setLayoutParams(row1Params);

        cancelBtn = createButton("⊗ Cancel", "#9E9E9E");
        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelRecording();
            }
        });
        cancelBtn.setVisibility(View.GONE);
        row1.addView(cancelBtn);

        stopBtn = createButton("✓ Append", "#2196F3");
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecording();
            }
        });
        stopBtn.setVisibility(View.GONE);
        row1.addView(stopBtn);

        recordBtn = createButton("🔴 Start Recording", "#f44336");
        recordBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecording();
            }
        });
        row1.addView(recordBtn);

        pasteBtn = createButton("📌 Paste", "#4CAF50");
        pasteBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handlePaste();
            }
        });
        row1.addView(pasteBtn);

        container.addView(row1);

        // Control buttons - Row 2: Clear, Improve, Voice Edit
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams row2Params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        row2Params.setMargins(0, 6, 0, 0);
        row2.setLayoutParams(row2Params);

        clearBtn = createButton("🗑 Clear", "#FF5722");
        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleClear();
            }
        });
        row2.addView(clearBtn);

        Button improveBtn = createButton("✨ Improve", "#FFC107");
        improveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                improveText();
            }
        });
        row2.addView(improveBtn);

        Button voiceEditBtn = createButton("🎙 Voice Edit", "#E91E63");
        voiceEditBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startVoiceEdit();
            }
        });
        row2.addView(voiceEditBtn);

        container.addView(row2);

        editorPanel = container;

        WindowManager.LayoutParams panelParams = new WindowManager.LayoutParams(
            900,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        );

        panelParams.gravity = Gravity.CENTER;

        windowManager.addView(editorPanel, panelParams);
        editorPanel.setVisibility(View.GONE);
    }

    private Button createButton(String text, String color) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setPadding(25, 22, 25, 22);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setAllCaps(false);
        button.setLetterSpacing(0.02f);
        button.setElevation(8);
        button.setStateListAnimator(null);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(50);
        bg.setColor(Color.parseColor(color));
        button.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(4, 0, 4, 0);
        button.setLayoutParams(params);

        return button;
    }

    private void openEditor() {
        isEditorOpen = true;
        editorPanel.setVisibility(View.VISIBLE);
        updateButtonVisibility();

        // Request focus on text box to show keyboard
        transcriptionText.requestFocus();

        // Auto-start recording when editor opens
        if (!isRecording) {
            startRecording();
        }
    }

    private void closeEditor() {
        // Save current text to history if not empty
        String currentText = transcriptionText.getText().toString().trim();
        if (!currentText.isEmpty()) {
            saveToHistory(currentText);
            transcriptionText.setText("");
        }

        // Cancel recording if active
        if (isRecording) {
            // Stop timer
            mainHandler.removeCallbacks(timerRunnable);
            audioRecorder.release();
            audioRecorder = new AudioRecorder();
            isRecording = false;
            isPaused = false;
        }

        isEditorOpen = false;
        editorPanel.setVisibility(View.GONE);
        updateMainButton();
        updateButtonVisibility();
        statusText.setText("⚫ Ready");
        processingIndicator.setVisibility(View.GONE);
    }

    private void updateButtonVisibility() {
        if (isRecording) {
            recordBtn.setVisibility(View.GONE);
            stopBtn.setVisibility(View.VISIBLE);
            cancelBtn.setVisibility(View.VISIBLE);
        } else {
            recordBtn.setVisibility(View.VISIBLE);
            stopBtn.setVisibility(View.GONE);
            cancelBtn.setVisibility(View.GONE);
        }
    }

    private void updateMainButton() {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);

        if (isRecording && !isPaused) {
            shape.setColors(new int[]{Color.parseColor("#f44336"), Color.parseColor("#d32f2f")});
        } else if (isPaused) {
            shape.setColors(new int[]{Color.parseColor("#FF9800"), Color.parseColor("#F57C00")});
        } else {
            shape.setColors(new int[]{Color.parseColor("#667eea"), Color.parseColor("#764ba2")});
        }

        mainButton.setBackground(shape);
    }

    private void handleCopy() {
        // If recording, stop and transcribe first
        if (isRecording) {
            showToast("Stopping recording to copy...");
            stopRecordingThen(new Runnable() {
                @Override
                public void run() {
                    performCopy();
                }
            });
        } else {
            performCopy();
        }
    }

    private void performCopy() {
        String text = transcriptionText.getText().toString();
        if (text.isEmpty()) {
            showToast("Nothing to copy");
            return;
        }
        copyToClipboard(text);
        showToast("Copied to clipboard");
    }

    private void handlePaste() {
        // If recording, stop and transcribe first
        if (isRecording) {
            showToast("Stopping recording to paste...");
            stopRecordingThen(new Runnable() {
                @Override
                public void run() {
                    performPaste();
                }
            });
        } else {
            performPaste();
        }
    }

    private void performPaste() {
        String text = transcriptionText.getText().toString();
        if (text.isEmpty()) {
            showToast("Nothing to paste");
            return;
        }
        copyToClipboard(text);
        showToast("Copied! Long-press in any app to paste");
        closeEditor();
    }

    private void handleClear() {
        String currentText = transcriptionText.getText().toString().trim();
        if (!currentText.isEmpty()) {
            saveToHistory(currentText);
        }
        transcriptionText.setText("");
        showToast("Text cleared and saved to history");
    }

    private void openSettings() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void improveText() {
        // If currently recording, stop and append first, then improve
        if (isRecording) {
            stopRecordingThen(new Runnable() {
                @Override
                public void run() {
                    // After recording is appended, improve the text
                    performImprovement();
                }
            });
        } else {
            performImprovement();
        }
    }

    private void performImprovement() {
        String currentText = transcriptionText.getText().toString().trim();

        if (currentText.isEmpty()) {
            showToast("No text to improve");
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String apiKey = prefs.getString("whisper_api_key", "");

        if (apiKey.isEmpty()) {
            showToast("Please set OpenAI API key in settings");
            openSettings();
            return;
        }

        // Show processing indicator
        statusText.setText("✨ Improving text...");
        processingIndicator.setVisibility(View.VISIBLE);

        ChatGPTAPI.improveText(this, apiKey, currentText, new ChatGPTAPI.ChatGPTCallback() {
            @Override
            public void onSuccess(final String improvedText) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        processingIndicator.setVisibility(View.GONE);
                        statusText.setText("✅ Text improved!");
                        transcriptionText.setText(improvedText);
                        showToast("Text improved successfully!");
                    }
                });
            }

            @Override
            public void onError(final String error) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        processingIndicator.setVisibility(View.GONE);
                        statusText.setText("❌ Improvement failed");
                        showToast("Error: " + error);
                    }
                });
            }
        });
    }

    private void startVoiceEdit() {
        // If currently recording, stop and append first, then start voice edit
        if (isRecording) {
            stopRecordingThen(new Runnable() {
                @Override
                public void run() {
                    // After recording is appended, start voice edit
                    performVoiceEdit();
                }
            });
        } else {
            performVoiceEdit();
        }
    }

    private void performVoiceEdit() {
        final String originalText = transcriptionText.getText().toString().trim();

        if (originalText.isEmpty()) {
            showToast("No text to edit");
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        final String apiKey = prefs.getString("whisper_api_key", "");

        if (apiKey.isEmpty()) {
            showToast("Please set OpenAI API key in settings");
            openSettings();
            return;
        }

        // Create voice edit dialog
        final LinearLayout voiceEditView = new LinearLayout(this);
        voiceEditView.setOrientation(LinearLayout.VERTICAL);
        voiceEditView.setPadding(25, 25, 25, 25);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(24);
        bg.setColor(Color.parseColor("#1c1c1e"));
        voiceEditView.setBackground(bg);
        voiceEditView.setElevation(16);

        // Title
        TextView title = new TextView(this);
        title.setText("🎙 Voice Edit Mode");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 15);
        voiceEditView.addView(title);

        // Original text (read-only)
        TextView originalLabel = new TextView(this);
        originalLabel.setText("Original Text:");
        originalLabel.setTextColor(Color.parseColor("#999999"));
        originalLabel.setTextSize(12);
        originalLabel.setPadding(0, 0, 0, 8);
        voiceEditView.addView(originalLabel);

        TextView originalTextView = new TextView(this);
        originalTextView.setText(originalText);
        originalTextView.setTextColor(Color.parseColor("#CCCCCC"));
        originalTextView.setTextSize(14);
        originalTextView.setPadding(18, 18, 18, 18);
        originalTextView.setMaxLines(5);

        GradientDrawable textBg = new GradientDrawable();
        textBg.setCornerRadius(16);
        textBg.setColor(Color.parseColor("#2c2c2e"));
        textBg.setStroke(1, Color.parseColor("#38383a"));
        originalTextView.setBackground(textBg);

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        textParams.setMargins(0, 0, 0, 20);
        originalTextView.setLayoutParams(textParams);
        voiceEditView.addView(originalTextView);

        // Instructions
        TextView instructions = new TextView(this);
        instructions.setText("🎤 Speak your edit instructions");
        instructions.setTextColor(Color.WHITE);
        instructions.setTextSize(16);
        instructions.setGravity(Gravity.CENTER);
        instructions.setPadding(0, 0, 0, 15);
        voiceEditView.addView(instructions);

        // Status text
        final TextView editStatus = new TextView(this);
        editStatus.setText("⚫ Ready to record");
        editStatus.setTextColor(Color.parseColor("#b4b4b4"));
        editStatus.setTextSize(14);
        editStatus.setGravity(Gravity.CENTER);
        editStatus.setPadding(0, 0, 0, 15);
        voiceEditView.addView(editStatus);

        // Buttons
        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER);

        final Button recordEditBtn = createButton("🔴 Record", "#f44336");
        final Button cancelEditBtn = createButton("Cancel", "#607D8B");

        final AudioRecorder editRecorder = new AudioRecorder();

        // Apply audio quality
        String audioQuality = prefs.getString(KEY_AUDIO_QUALITY, "Low");
        editRecorder.setQuality(audioQuality);

        final boolean[] isEditRecording = {false};

        recordEditBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isEditRecording[0]) {
                    // Start recording edit instructions
                    File cacheDir = getCacheDir();
                    editRecorder.startRecording(cacheDir, new AudioRecorder.RecordingCallback() {
                        @Override
                        public void onRecordingStarted() {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    isEditRecording[0] = true;
                                    recordEditBtn.setText("⏹ Stop");
                                    editStatus.setText("🔴 Recording edit instructions...");
                                }
                            });
                        }

                        @Override
                        public void onRecordingStopped(File audioFile) {}

                        @Override
                        public void onError(String error) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    showToast("Error: " + error);
                                }
                            });
                        }
                    });
                } else {
                    // Stop recording and process
                    editRecorder.stopRecording(new AudioRecorder.RecordingCallback() {
                        @Override
                        public void onRecordingStarted() {}

                        @Override
                        public void onRecordingStopped(final File audioFile) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    isEditRecording[0] = false;
                                    editStatus.setText("⏳ Transcribing edit instructions...");

                                    // Transcribe the edit instructions
                                    WhisperAPI.transcribeAudio(OverlayService.this, audioFile, new WhisperAPI.TranscriptionCallback() {
                                        @Override
                                        public void onSuccess(final String editInstructions) {
                                            mainHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    editStatus.setText("✨ Applying edits...");

                                                    // Apply voice edit using ChatGPT
                                                    ChatGPTAPI.applyVoiceEdit(OverlayService.this, apiKey, originalText, editInstructions, new ChatGPTAPI.ChatGPTCallback() {
                                                        @Override
                                                        public void onSuccess(final String editedText) {
                                                            mainHandler.post(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    // Close voice edit dialog
                                                                    windowManager.removeView(voiceEditView);

                                                                    // Update main text
                                                                    transcriptionText.setText(editedText);
                                                                    statusText.setText("✅ Voice edit applied!");
                                                                    showToast("Edits applied successfully!");
                                                                }
                                                            });
                                                        }

                                                        @Override
                                                        public void onError(final String error) {
                                                            mainHandler.post(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    editStatus.setText("❌ Edit failed");
                                                                    showToast("Error: " + error);
                                                                }
                                                            });
                                                        }
                                                    });
                                                }
                                            });
                                        }

                                        @Override
                                        public void onError(final String error) {
                                            mainHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    editStatus.setText("❌ Transcription failed");
                                                    showToast("Transcription error: " + error);
                                                }
                                            });
                                        }
                                    });
                                }
                            });
                        }

                        @Override
                        public void onError(String error) {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    showToast("Error stopping: " + error);
                                }
                            });
                        }
                    });
                }
            }
        });
        buttonRow.addView(recordEditBtn);

        cancelEditBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isEditRecording[0]) {
                    editRecorder.release();
                }
                windowManager.removeView(voiceEditView);
            }
        });
        buttonRow.addView(cancelEditBtn);

        voiceEditView.addView(buttonRow);

        // Show voice edit dialog
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            850,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.CENTER;

        windowManager.addView(voiceEditView, params);
    }

    private void saveToHistory(String text) {
        if (text.isEmpty()) return;

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String history = prefs.getString(KEY_HISTORY, "");

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(new Date());
        String displayTimestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        // Save audio file to history if available
        String audioFilePath = "";
        if (currentAudioFile != null && currentAudioFile.exists()) {
            try {
                File historyAudioFile = new File(historyFolder, "recording_" + timestamp + ".m4a");
                java.nio.file.Files.copy(currentAudioFile.toPath(), historyAudioFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                audioFilePath = historyAudioFile.getAbsolutePath();
            } catch (Exception e) {
                android.util.Log.e("VoiceOverlay", "Failed to save audio file to history", e);
            }
        }

        String entry = displayTimestamp + "|||" + text + "|||" + audioFilePath + "\n###ENTRY###\n";

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_HISTORY, entry + history);
        editor.apply();
    }

    private void showHistory() {
        final SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String history = prefs.getString(KEY_HISTORY, "");

        if (history.isEmpty()) {
            showToast("No history available");
            return;
        }

        // Parse history entries
        final String[] entries = history.split("\n###ENTRY###\n");

        // Create history dialog
        final ScrollView historyView = new ScrollView(this);
        final LinearLayout historyContainer = new LinearLayout(this);
        historyContainer.setOrientation(LinearLayout.VERTICAL);
        historyContainer.setPadding(25, 25, 25, 25);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(24);
        bg.setColor(Color.parseColor("#1c1c1e"));
        historyContainer.setBackground(bg);
        historyContainer.setElevation(16);

        // Title
        TextView historyTitle = new TextView(this);
        historyTitle.setText("📜 Transcription History");
        historyTitle.setTextColor(Color.WHITE);
        historyTitle.setTextSize(20);
        historyTitle.setGravity(Gravity.CENTER);
        historyTitle.setPadding(0, 0, 0, 15);
        historyContainer.addView(historyTitle);

        // Top buttons row: Clear All and Close
        LinearLayout historyTopRow = new LinearLayout(this);
        historyTopRow.setOrientation(LinearLayout.HORIZONTAL);
        historyTopRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams topRowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        topRowParams.setMargins(0, 0, 0, 15);
        historyTopRow.setLayoutParams(topRowParams);

        Button clearAllBtn = createButton("🗑 Clear All", "#f44336");
        clearAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Clear all history
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(KEY_HISTORY, "");
                editor.apply();
                windowManager.removeView(historyView);
                showToast("All history cleared");
            }
        });
        historyTopRow.addView(clearAllBtn);

        Button closeHistoryBtn = createButton("Close", "#607D8B");
        closeHistoryBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                windowManager.removeView(historyView);
            }
        });
        historyTopRow.addView(closeHistoryBtn);

        historyContainer.addView(historyTopRow);

        // Search box
        final EditText searchBox = new EditText(this);
        searchBox.setHint("🔍 Search transcriptions...");
        searchBox.setTextColor(Color.parseColor("#FFFFFF"));
        searchBox.setHintTextColor(Color.parseColor("#8E8E93"));
        searchBox.setTextSize(14);
        searchBox.setPadding(18, 15, 18, 15);
        searchBox.setSingleLine(true);

        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setCornerRadius(16);
        searchBg.setColor(Color.parseColor("#2c2c2e"));
        searchBg.setStroke(1, Color.parseColor("#38383a"));
        searchBox.setBackground(searchBg);

        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        searchParams.setMargins(0, 0, 0, 15);
        searchBox.setLayoutParams(searchParams);
        historyContainer.addView(searchBox);

        // Container for filtered entries
        final LinearLayout entriesContainer = new LinearLayout(this);
        entriesContainer.setOrientation(LinearLayout.VERTICAL);
        historyContainer.addView(entriesContainer);

        // Create a card for each entry
        for (int i = 0; i < entries.length; i++) {
            final String entry = entries[i].trim();
            if (entry.isEmpty()) continue;

            String[] parts = entry.split("\\|\\|\\|", 3);
            if (parts.length < 2) continue;

            final String timestamp = parts[0];
            final String text = parts[1];
            final String audioPath = parts.length > 2 ? parts[2] : "";
            final int index = i;

            // Entry container
            LinearLayout entryCard = new LinearLayout(this);
            entryCard.setOrientation(LinearLayout.VERTICAL);
            entryCard.setPadding(18, 18, 18, 18);
            entryCard.setElevation(4);

            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setCornerRadius(16);
            cardBg.setColor(Color.parseColor("#2c2c2e"));
            cardBg.setStroke(1, Color.parseColor("#38383a"));
            entryCard.setBackground(cardBg);

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardParams.setMargins(0, 0, 0, 12);
            entryCard.setLayoutParams(cardParams);

            // Timestamp
            TextView timestampView = new TextView(this);
            timestampView.setText("🕒 " + timestamp);
            timestampView.setTextColor(Color.parseColor("#999999"));
            timestampView.setTextSize(12);
            timestampView.setPadding(0, 0, 0, 10);
            entryCard.addView(timestampView);

            // Text content
            TextView textView = new TextView(this);
            textView.setText(text);
            textView.setTextColor(Color.parseColor("#e0e0e0"));
            textView.setTextSize(14);
            textView.setPadding(0, 0, 0, 15);
            entryCard.addView(textView);

            // Buttons row
            LinearLayout buttonRow = new LinearLayout(this);
            buttonRow.setOrientation(LinearLayout.HORIZONTAL);
            buttonRow.setGravity(Gravity.CENTER);

            Button copyEntryBtn = createButton("📋 Copy", "#2196F3");
            copyEntryBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    copyToClipboard(text);
                    showToast("Copied to clipboard");
                }
            });
            buttonRow.addView(copyEntryBtn);

            Button deleteEntryBtn = createButton("🗑 Delete", "#f44336");
            deleteEntryBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Delete this entry and audio file
                    String currentHistory = prefs.getString(KEY_HISTORY, "");
                    String entryToRemove = timestamp + "|||" + text + "|||" + audioPath + "\n###ENTRY###\n";
                    String newHistory = currentHistory.replace(entryToRemove, "");

                    // Delete audio file if exists
                    if (!audioPath.isEmpty()) {
                        File audioFile = new File(audioPath);
                        if (audioFile.exists()) {
                            audioFile.delete();
                        }
                    }

                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString(KEY_HISTORY, newHistory);
                    editor.apply();

                    // Refresh history view
                    windowManager.removeView(historyView);
                    showHistory();
                }
            });
            buttonRow.addView(deleteEntryBtn);

            entryCard.addView(buttonRow);
            entriesContainer.addView(entryCard);
        }

        // Add search functionality
        searchBox.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().toLowerCase().trim();

                // Filter entries
                for (int i = 0; i < entriesContainer.getChildCount(); i++) {
                    View child = entriesContainer.getChildAt(i);
                    if (child instanceof LinearLayout) {
                        LinearLayout card = (LinearLayout) child;
                        // Get the text view (it's the second child after timestamp)
                        if (card.getChildCount() > 1) {
                            View textViewChild = card.getChildAt(1);
                            if (textViewChild instanceof TextView) {
                                TextView textView = (TextView) textViewChild;
                                String cardText = textView.getText().toString().toLowerCase();

                                // Show/hide based on search
                                if (query.isEmpty() || cardText.contains(query)) {
                                    card.setVisibility(View.VISIBLE);
                                } else {
                                    card.setVisibility(View.GONE);
                                }
                            }
                        }
                    }
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        historyView.addView(historyContainer);

        WindowManager.LayoutParams historyParams = new WindowManager.LayoutParams(
            1000,
            1500,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        );
        historyParams.gravity = Gravity.CENTER;

        windowManager.addView(historyView, historyParams);
    }

    private void startRecording() {
        File cacheDir = getCacheDir();
        audioRecorder.startRecording(cacheDir, new AudioRecorder.RecordingCallback() {
            @Override
            public void onRecordingStarted() {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        isRecording = true;
                        isPaused = false;
                        recordingStartTime = System.currentTimeMillis();
                        currentAudioFile = audioRecorder.getOutputFile();
                        updateMainButton();
                        updateButtonVisibility();
                        statusText.setText("🔴 Recording... 0:00");
                        // Start timer
                        mainHandler.postDelayed(timerRunnable, 1000);
                        showToast("Recording started");
                    }
                });
            }

            @Override
            public void onRecordingStopped(File audioFile) {}

            @Override
            public void onError(String error) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showToast("Error: " + error);
                        resetState();
                    }
                });
            }
        });
    }

    private void pauseRecording() {
        audioRecorder.pauseRecording();
        isPaused = true;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                // Stop timer
                mainHandler.removeCallbacks(timerRunnable);
                long elapsedSeconds = (System.currentTimeMillis() - recordingStartTime) / 1000;
                updateMainButton();
                statusText.setText("⏸ Paused at " + formatTime(elapsedSeconds));
                showToast("Paused");
            }
        });
    }

    private void resumeRecording() {
        audioRecorder.resumeRecording();
        isPaused = false;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                updateMainButton();
                // Restart timer
                mainHandler.postDelayed(timerRunnable, 1000);
                showToast("Resumed");
            }
        });
    }

    private void stopRecording() {
        audioRecorder.stopRecording(new AudioRecorder.RecordingCallback() {
            @Override
            public void onRecordingStarted() {}

            @Override
            public void onRecordingStopped(final File audioFile) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        // Stop timer
                        mainHandler.removeCallbacks(timerRunnable);
                        isRecording = false;
                        isPaused = false;
                        updateMainButton();
                        updateButtonVisibility();
                        statusText.setText("⏳ Transcribing...");
                        processingIndicator.setVisibility(View.VISIBLE);
                        transcribeAudio(audioFile);
                    }
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showToast("Error stopping: " + error);
                        resetState();
                    }
                });
            }
        });
    }

    private void stopRecordingThen(final Runnable onComplete) {
        audioRecorder.stopRecording(new AudioRecorder.RecordingCallback() {
            @Override
            public void onRecordingStarted() {}

            @Override
            public void onRecordingStopped(final File audioFile) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        // Stop timer
                        mainHandler.removeCallbacks(timerRunnable);
                        isRecording = false;
                        isPaused = false;
                        updateMainButton();
                        updateButtonVisibility();
                        statusText.setText("⏳ Transcribing...");
                        processingIndicator.setVisibility(View.VISIBLE);
                        transcribeAudioThen(audioFile, onComplete);
                    }
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showToast("Error stopping: " + error);
                        resetState();
                    }
                });
            }
        });
    }

    private void cancelRecording() {
        // Stop timer
        mainHandler.removeCallbacks(timerRunnable);
        audioRecorder.release();
        audioRecorder = new AudioRecorder();
        isRecording = false;
        isPaused = false;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                updateMainButton();
                updateButtonVisibility();
                statusText.setText("⚫ Ready");
                showToast("Recording cancelled");
            }
        });
    }

    private void startQuickRecording() {
        File cacheDir = getCacheDir();
        audioRecorder.startRecording(cacheDir, new AudioRecorder.RecordingCallback() {
            @Override
            public void onRecordingStarted() {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        isRecording = true;
                        recordingStartTime = System.currentTimeMillis();
                        updateMainButton();
                        showToast("🎙️ Recording...");
                    }
                });
            }

            @Override
            public void onRecordingStopped(File audioFile) {}

            @Override
            public void onError(String error) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showToast("Error: " + error);
                        isRecording = false;
                        updateMainButton();
                    }
                });
            }
        });
    }

    private void stopQuickRecording() {
        audioRecorder.stopRecording(new AudioRecorder.RecordingCallback() {
            @Override
            public void onRecordingStarted() {}

            @Override
            public void onRecordingStopped(final File audioFile) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        isRecording = false;
                        updateMainButton();
                        showToast("⏳ Transcribing...");

                        // Transcribe and auto-copy
                        transcribeQuickRecording(audioFile);
                    }
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showToast("Error stopping: " + error);
                        isRecording = false;
                        updateMainButton();
                    }
                });
            }
        });
    }

    private void cancelQuickRecording() {
        audioRecorder.release();
        audioRecorder = new AudioRecorder();
        isRecording = false;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                updateMainButton();
                showToast("Recording cancelled");
            }
        });
    }

    private void transcribeQuickRecording(final File audioFile) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                WhisperAPI.transcribeAudio(OverlayService.this, audioFile, new WhisperAPI.TranscriptionCallback() {
                    @Override
                    public void onSuccess(final String transcription) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                // Auto-copy to clipboard
                                copyToClipboard(transcription);
                                showToast("✓ Copied to clipboard!");
                                audioFile.delete();
                            }
                        });
                    }

                    @Override
                    public void onError(final String error) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                showToast("Transcription failed: " + error);
                                audioFile.delete();
                            }
                        });
                    }
                });
            }
        }).start();
    }

    private void transcribeAudio(final File audioFile) {
        transcribeAudioThen(audioFile, null);
    }

    private void transcribeAudioThen(final File audioFile, final Runnable onComplete) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Check transcription mode
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                String mode = prefs.getString(KEY_TRANSCRIPTION_MODE, "api");

                // Create unified callback handler
                final Object callback;
                if (mode.equals("firebase")) {
                    // Use Firebase backend
                    callback = new FirebaseWhisperAPI.TranscriptionCallback() {
                        @Override
                        public void onSuccess(final String transcription) {
                            handleTranscriptionSuccess(transcription, audioFile, onComplete);
                        }

                        @Override
                        public void onError(final String error) {
                            handleTranscriptionError(error, audioFile, onComplete);
                        }
                    };
                    FirebaseWhisperAPI.transcribeAudio(OverlayService.this, audioFile, (FirebaseWhisperAPI.TranscriptionCallback) callback);
                } else {
                    // Use Direct API
                    callback = new WhisperAPI.TranscriptionCallback() {
                        @Override
                        public void onSuccess(final String transcription) {
                            handleTranscriptionSuccess(transcription, audioFile, onComplete);
                        }

                        @Override
                        public void onError(final String error) {
                            handleTranscriptionError(error, audioFile, onComplete);
                        }
                    };
                    WhisperAPI.transcribeAudio(OverlayService.this, audioFile, (WhisperAPI.TranscriptionCallback) callback);
                }
            }
        }).start();
    }

    private void handleTranscriptionSuccess(final String transcription, final File audioFile, final Runnable onComplete) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                processingIndicator.setVisibility(View.GONE);
                statusText.setText("✓ Transcribed");

                // Append to existing text
                String currentText = transcriptionText.getText().toString();
                if (!currentText.isEmpty() && !currentText.endsWith("\n")) {
                    currentText += "\n";
                }
                transcriptionText.setText(currentText + transcription);

                showToast("Transcription complete");
                audioFile.delete();

                // Execute callback if provided
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
    }

    private void handleTranscriptionError(final String error, final File audioFile, final Runnable onComplete) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                processingIndicator.setVisibility(View.GONE);
                statusText.setText("❌ Error");
                showToast("Transcription failed: " + error);
                audioFile.delete();

                // Execute callback even on error
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
    }

    private void resetState() {
        // Stop timer
        mainHandler.removeCallbacks(timerRunnable);
        isRecording = false;
        isPaused = false;
        updateMainButton();
        updateButtonVisibility();
        statusText.setText("⚫ Ready");
        processingIndicator.setVisibility(View.GONE);
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Transcription", text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
        }
    }

    private void showToast(final String message) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(OverlayService.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mainButton != null) {
            windowManager.removeView(mainButton);
        }
        if (editorPanel != null) {
            windowManager.removeView(editorPanel);
        }
        if (audioRecorder != null) {
            audioRecorder.release();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Voice Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps voice overlay active");
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification(String text) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, flags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
            .setContentTitle("Voice Overlay")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .build();
    }
}
