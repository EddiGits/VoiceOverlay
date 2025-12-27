package com.voiceoverlay;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "VoiceOverlayPrefs";
    private static final String KEY_API_URL = "whisper_api_url";
    private static final String KEY_API_KEY = "whisper_api_key";
    private static final String KEY_AUTO_START = "auto_start_enabled";
    private static final String KEY_TRANSCRIPTION_PROMPT = "transcription_prompt";
    private static final String KEY_AUDIO_QUALITY = "audio_quality";
    private static final String KEY_WHISPER_MODEL = "whisper_model";
    private static final String KEY_TRANSCRIPTION_MODE = "transcription_mode"; // "api" or "firebase"
    private static final int REQUEST_OVERLAY_PERMISSION = 1234;
    private static final int REQUEST_MICROPHONE_PERMISSION = 1235;

    private EditText urlInput;
    private EditText keyInput;
    private EditText transcriptionPromptInput;
    private Switch autoStartSwitch;
    private Spinner qualitySpinner;
    private Spinner modelSpinner;
    private Spinner modeSpinner;
    private boolean isServiceRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Auto-start overlay service if permissions are granted
        autoStartOverlay();

        // Create modern UI
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#1c1c1e"));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 50, 30, 30);

        // Title
        TextView title = new TextView(this);
        title.setText("Voice Overlay");
        title.setTextSize(32);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 40);
        title.setLetterSpacing(0.05f);
        layout.addView(title);

        // API Settings Card
        LinearLayout apiCard = createCard();

        TextView apiTitle = new TextView(this);
        apiTitle.setText("API Configuration");
        apiTitle.setTextSize(18);
        apiTitle.setTextColor(Color.WHITE);
        apiTitle.setPadding(0, 0, 0, 20);
        apiCard.addView(apiTitle);

        // Whisper API URL input
        urlInput = createInput("Whisper API URL", "https://api.openai.com/v1/audio/transcriptions");
        apiCard.addView(urlInput);

        // API Key input (used for both Whisper and ChatGPT)
        keyInput = createInput("OpenAI API Key", "sk-...");
        keyInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        apiCard.addView(keyInput);

        // Save API button
        Button saveButton = createButton("Save API Settings", "#4CAF50");
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });
        apiCard.addView(saveButton);

        layout.addView(apiCard);

        // Transcription Settings Card
        LinearLayout transcriptionCard = createCard();

        TextView transcriptionTitle = new TextView(this);
        transcriptionTitle.setText("Transcription Settings");
        transcriptionTitle.setTextSize(18);
        transcriptionTitle.setTextColor(Color.WHITE);
        transcriptionTitle.setPadding(0, 0, 0, 20);
        transcriptionCard.addView(transcriptionTitle);

        // Transcription Mode Selection
        TextView modeLabel = new TextView(this);
        modeLabel.setText("Transcription Mode");
        modeLabel.setTextSize(14);
        modeLabel.setTextColor(Color.parseColor("#CCCCCC"));
        modeLabel.setPadding(0, 0, 0, 8);
        transcriptionCard.addView(modeLabel);

        modeSpinner = createSpinner(new String[]{"Direct API (Use your own key)", "Firebase Backend (Secure & Subscription-ready)"});
        transcriptionCard.addView(modeSpinner);

        // Whisper Model Selection
        TextView modelLabel = new TextView(this);
        modelLabel.setText("Whisper Model");
        modelLabel.setTextSize(14);
        modelLabel.setTextColor(Color.parseColor("#CCCCCC"));
        modelLabel.setPadding(0, 0, 0, 8);
        transcriptionCard.addView(modelLabel);

        modelSpinner = createSpinner(new String[]{"whisper-1", "gpt-4o-audio-preview", "gpt-4o-mini-audio-preview"});
        transcriptionCard.addView(modelSpinner);

        // Audio Quality Selection
        TextView qualityLabel = new TextView(this);
        qualityLabel.setText("Audio Quality");
        qualityLabel.setTextSize(14);
        qualityLabel.setTextColor(Color.parseColor("#CCCCCC"));
        qualityLabel.setPadding(0, 15, 0, 8);
        transcriptionCard.addView(qualityLabel);

        qualitySpinner = createSpinner(new String[]{"Low (16kHz Mono - Fastest)", "Medium (22kHz Mono - Balanced)", "High (44kHz Stereo - Best)"});
        transcriptionCard.addView(qualitySpinner);

        // Transcription Prompt
        TextView promptLabel = new TextView(this);
        promptLabel.setText("Transcription Instructions");
        promptLabel.setTextSize(14);
        promptLabel.setTextColor(Color.parseColor("#CCCCCC"));
        promptLabel.setPadding(0, 15, 0, 8);
        transcriptionCard.addView(promptLabel);

        transcriptionPromptInput = createMultilineInput("Custom instructions for transcription...",
            "Punctuate and then grammatically correct and improve the given recorded audio");
        transcriptionCard.addView(transcriptionPromptInput);

        // Save Transcription Settings button
        Button saveTranscriptionButton = createButton("Save Transcription Settings", "#2196F3");
        saveTranscriptionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveTranscriptionSettings();
            }
        });
        transcriptionCard.addView(saveTranscriptionButton);

        layout.addView(transcriptionCard);

        // Permissions Card
        LinearLayout permCard = createCard();

        TextView permTitle = new TextView(this);
        permTitle.setText("Permissions");
        permTitle.setTextSize(18);
        permTitle.setTextColor(Color.WHITE);
        permTitle.setPadding(0, 0, 0, 20);
        permCard.addView(permTitle);

        // Microphone permission button
        Button micPermissionButton = createButton("Enable Microphone", "#2196F3");
        micPermissionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestMicrophonePermission();
            }
        });
        permCard.addView(micPermissionButton);

        // Overlay permission button
        Button permissionButton = createButton("Enable Overlay", "#9C27B0");
        permissionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestOverlayPermission();
            }
        });
        permCard.addView(permissionButton);

        layout.addView(permCard);

        // Service Control Card
        LinearLayout serviceCard = createCard();

        TextView serviceTitle = new TextView(this);
        serviceTitle.setText("Overlay Service");
        serviceTitle.setTextSize(18);
        serviceTitle.setTextColor(Color.WHITE);
        serviceTitle.setPadding(0, 0, 0, 20);
        serviceCard.addView(serviceTitle);

        // Auto-start toggle
        LinearLayout toggleRow = new LinearLayout(this);
        toggleRow.setOrientation(LinearLayout.HORIZONTAL);
        toggleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        toggleParams.setMargins(0, 0, 0, 20);
        toggleRow.setLayoutParams(toggleParams);

        TextView toggleLabel = new TextView(this);
        toggleLabel.setText("Auto-start on boot");
        toggleLabel.setTextSize(16);
        toggleLabel.setTextColor(Color.parseColor("#CCCCCC"));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        );
        toggleLabel.setLayoutParams(labelParams);
        toggleRow.addView(toggleLabel);

        autoStartSwitch = new Switch(this);
        autoStartSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                saveAutoStartSetting(isChecked);
            }
        });
        toggleRow.addView(autoStartSwitch);

        serviceCard.addView(toggleRow);

        // Start/Stop service button
        final Button serviceButton = createButton("Start Overlay", "#f44336");
        serviceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isServiceRunning) {
                    stopOverlayService();
                    serviceButton.setText("Start Overlay");
                    isServiceRunning = false;
                } else {
                    startOverlayService();
                    serviceButton.setText("Stop Overlay");
                    isServiceRunning = true;
                }
            }
        });
        serviceCard.addView(serviceButton);

        layout.addView(serviceCard);

        // Info Card
        LinearLayout infoCard = createCard();

        TextView infoTitle = new TextView(this);
        infoTitle.setText("Setup Instructions");
        infoTitle.setTextSize(18);
        infoTitle.setTextColor(Color.WHITE);
        infoTitle.setPadding(0, 0, 0, 15);
        infoCard.addView(infoTitle);

        TextView infoText = new TextView(this);
        infoText.setText("1. Configure API settings\n2. Set transcription preferences\n3. Enable microphone permission (set to \"Allow all the time\")\n4. Enable overlay permission\n5. Overlay starts automatically\n6. Use the floating button to record");
        infoText.setTextSize(14);
        infoText.setTextColor(Color.parseColor("#AAAAAA"));
        infoText.setLineSpacing(5, 1);
        infoCard.addView(infoText);

        layout.addView(infoCard);

        scrollView.addView(layout);
        setContentView(scrollView);

        // Load saved settings
        loadSettings();
    }

    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(25, 25, 25, 25);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(20);
        bg.setColor(Color.parseColor("#2c2c2e"));
        card.setBackground(bg);
        card.setElevation(8);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 25);
        card.setLayoutParams(params);

        return card;
    }

    private EditText createInput(String label, String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.parseColor("#666666"));
        input.setPadding(20, 20, 20, 20);
        input.setTextSize(16);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(12);
        bg.setColor(Color.parseColor("#3a3a3c"));
        input.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 15);
        input.setLayoutParams(params);

        return input;
    }

    private EditText createMultilineInput(String hint, String defaultText) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(defaultText);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.parseColor("#666666"));
        input.setPadding(20, 20, 20, 20);
        input.setTextSize(14);
        input.setMinLines(3);
        input.setMaxLines(5);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(12);
        bg.setColor(Color.parseColor("#3a3a3c"));
        input.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 15);
        input.setLayoutParams(params);

        return input;
    }

    private Spinner createSpinner(String[] items) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(12);
        bg.setColor(Color.parseColor("#3a3a3c"));
        spinner.setBackground(bg);
        spinner.setPadding(20, 20, 20, 20);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 15);
        spinner.setLayoutParams(params);

        return spinner;
    }

    private Button createButton(String text, String color) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16);
        button.setPadding(0, 20, 0, 20);
        button.setAllCaps(false);
        button.setElevation(6);
        button.setStateListAnimator(null);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(12);
        bg.setColor(Color.parseColor(color));
        button.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 12);
        button.setLayoutParams(params);

        return button;
    }

    private void autoStartOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                Intent serviceIntent = new Intent(this, OverlayService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                isServiceRunning = true;
            }
        } else {
            Intent serviceIntent = new Intent(this, OverlayService.class);
            startService(serviceIntent);
            isServiceRunning = true;
        }
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String url = prefs.getString(KEY_API_URL, "");
        String key = prefs.getString(KEY_API_KEY, "");
        String prompt = prefs.getString(KEY_TRANSCRIPTION_PROMPT, "Punctuate and then grammatically correct and improve the given recorded audio");
        String quality = prefs.getString(KEY_AUDIO_QUALITY, "Low");
        String model = prefs.getString(KEY_WHISPER_MODEL, "whisper-1");
        String mode = prefs.getString(KEY_TRANSCRIPTION_MODE, "api");
        boolean autoStart = prefs.getBoolean(KEY_AUTO_START, true);

        urlInput.setText(url);
        keyInput.setText(key);
        transcriptionPromptInput.setText(prompt);
        autoStartSwitch.setChecked(autoStart);

        // Set quality spinner
        if (quality.equals("Medium")) {
            qualitySpinner.setSelection(1);
        } else if (quality.equals("High")) {
            qualitySpinner.setSelection(2);
        } else {
            qualitySpinner.setSelection(0);
        }

        // Set model spinner
        if (model.equals("gpt-4o-audio-preview")) {
            modelSpinner.setSelection(1);
        } else if (model.equals("gpt-4o-mini-audio-preview")) {
            modelSpinner.setSelection(2);
        } else {
            modelSpinner.setSelection(0);
        }

        // Set mode spinner
        if (mode.equals("firebase")) {
            modeSpinner.setSelection(1);
        } else {
            modeSpinner.setSelection(0);
        }
    }

    private void saveSettings() {
        String url = urlInput.getText().toString().trim();
        String key = keyInput.getText().toString().trim();

        if (url.isEmpty() || key.isEmpty()) {
            Toast.makeText(this, "Please fill in API fields", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_API_URL, url);
        editor.putString(KEY_API_KEY, key);
        editor.apply();

        Toast.makeText(this, "API settings saved!", Toast.LENGTH_SHORT).show();
    }

    private void saveTranscriptionSettings() {
        String prompt = transcriptionPromptInput.getText().toString().trim();
        String quality = qualitySpinner.getSelectedItem().toString();
        String model = modelSpinner.getSelectedItem().toString();
        String modeSelection = modeSpinner.getSelectedItem().toString();

        // Parse quality
        String qualityKey = "Low";
        if (quality.startsWith("Medium")) {
            qualityKey = "Medium";
        } else if (quality.startsWith("High")) {
            qualityKey = "High";
        }

        // Parse mode
        String modeKey = modeSelection.startsWith("Direct") ? "api" : "firebase";

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_TRANSCRIPTION_PROMPT, prompt);
        editor.putString(KEY_AUDIO_QUALITY, qualityKey);
        editor.putString(KEY_WHISPER_MODEL, model);
        editor.putString(KEY_TRANSCRIPTION_MODE, modeKey);
        editor.apply();

        Toast.makeText(this, "Transcription settings saved!", Toast.LENGTH_SHORT).show();
    }

    private void saveAutoStartSetting(boolean enabled) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_AUTO_START, enabled);
        editor.apply();
    }

    private void requestMicrophonePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE_PERMISSION);
            } else {
                Toast.makeText(this, "Microphone permission already granted. Please go to Settings → Apps → Voice Overlay → Permissions → Microphone and set to \"Allow all the time\"", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
            } else {
                Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startOverlayService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please enable overlay permission first", Toast.LENGTH_LONG).show();
                return;
            }
        }

        Intent serviceIntent = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "Voice overlay started!", Toast.LENGTH_SHORT).show();
    }

    private void stopOverlayService() {
        Intent serviceIntent = new Intent(this, OverlayService.class);
        stopService(serviceIntent);
        Toast.makeText(this, "Voice overlay stopped!", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_MICROPHONE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Microphone permission granted! Now go to Settings → Apps → Voice Overlay → Permissions → Microphone and set to \"Allow all the time\"", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Microphone permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Overlay permission granted!", Toast.LENGTH_SHORT).show();
                    autoStartOverlay();
                } else {
                    Toast.makeText(this, "Overlay permission denied", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}
