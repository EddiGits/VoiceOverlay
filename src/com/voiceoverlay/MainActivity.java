package com.voiceoverlay;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "VoiceOverlayPrefs";
    private static final String KEY_API_URL = "whisper_api_url";
    private static final String KEY_API_KEY = "whisper_api_key";
    private static final int REQUEST_OVERLAY_PERMISSION = 1234;

    private EditText urlInput;
    private EditText keyInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create UI
        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 40, 40, 40);

        // Title
        TextView title = new TextView(this);
        title.setText("Voice Overlay Settings");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 30);
        layout.addView(title);

        // API URL label
        TextView urlLabel = new TextView(this);
        urlLabel.setText("Whisper API URL:");
        urlLabel.setTextSize(16);
        urlLabel.setPadding(0, 20, 0, 10);
        layout.addView(urlLabel);

        // API URL input
        urlInput = new EditText(this);
        urlInput.setHint("https://api.openai.com/v1/audio/transcriptions");
        urlInput.setSingleLine(true);
        urlInput.setPadding(20, 20, 20, 20);
        layout.addView(urlInput);

        // API Key label
        TextView keyLabel = new TextView(this);
        keyLabel.setText("API Key:");
        keyLabel.setTextSize(16);
        keyLabel.setPadding(0, 20, 0, 10);
        layout.addView(keyLabel);

        // API Key input
        keyInput = new EditText(this);
        keyInput.setHint("sk-...");
        keyInput.setSingleLine(true);
        keyInput.setPadding(20, 20, 20, 20);
        layout.addView(keyInput);

        // Save button
        Button saveButton = new Button(this);
        saveButton.setText("Save Settings");
        saveButton.setPadding(0, 30, 0, 0);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });
        layout.addView(saveButton);

        // Request overlay permission button
        Button permissionButton = new Button(this);
        permissionButton.setText("Enable Overlay Permission");
        permissionButton.setPadding(0, 20, 0, 0);
        permissionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestOverlayPermission();
            }
        });
        layout.addView(permissionButton);

        // Start service button
        Button startButton = new Button(this);
        startButton.setText("Start Voice Overlay");
        startButton.setPadding(0, 20, 0, 0);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startOverlayService();
            }
        });
        layout.addView(startButton);

        // Info text
        TextView infoText = new TextView(this);
        infoText.setText("\n1. Configure API settings above\n2. Enable overlay permission\n3. Start the overlay service\n4. Use the floating button to record voice");
        infoText.setTextSize(14);
        infoText.setPadding(0, 30, 0, 0);
        layout.addView(infoText);

        scrollView.addView(layout);
        setContentView(scrollView);

        // Load saved settings
        loadSettings();
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String url = prefs.getString(KEY_API_URL, "");
        String key = prefs.getString(KEY_API_KEY, "");

        urlInput.setText(url);
        keyInput.setText(key);
    }

    private void saveSettings() {
        String url = urlInput.getText().toString().trim();
        String key = keyInput.getText().toString().trim();

        if (url.isEmpty() || key.isEmpty()) {
            Toast.makeText(this, "Please fill in both fields", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_API_URL, url);
        editor.putString(KEY_API_KEY, key);
        editor.apply();

        Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show();
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
        startService(serviceIntent);
        Toast.makeText(this, "Voice overlay started!", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Overlay permission granted!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Overlay permission denied", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}
