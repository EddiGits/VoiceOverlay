package com.voiceoverlay;

import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;
import java.io.File;

public class OverlayService extends Service {
    private WindowManager windowManager;
    private ImageView floatingButton;
    private AudioRecorder audioRecorder;
    private boolean isRecording = false;
    private Handler mainHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        android.util.Log.d("VoiceOverlay", "OverlayService onCreate called");
        audioRecorder = new AudioRecorder();
        mainHandler = new Handler(Looper.getMainLooper());

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Create floating button - make it bigger and more visible
        floatingButton = new ImageView(this);
        floatingButton.setImageResource(android.R.drawable.ic_btn_speak_now);
        floatingButton.setBackgroundColor(Color.parseColor("#4CAF50"));
        floatingButton.setPadding(30, 30, 30, 30);
        floatingButton.setScaleType(ImageView.ScaleType.FIT_CENTER);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            200,
            200,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 100;

        windowManager.addView(floatingButton, params);

        // Set click listener
        floatingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                android.util.Log.d("VoiceOverlay", "onClick fired");
                handleButtonClick();
            }
        });

        // Set touch listener for dragging
        floatingButton.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private long touchStartTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        android.util.Log.d("VoiceOverlay", "ACTION_DOWN");
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        touchStartTime = System.currentTimeMillis();
                        return false; // Let click listener also handle it

                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);

                        // Only start dragging if moved more than 10 pixels
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            android.util.Log.d("VoiceOverlay", "ACTION_MOVE dragging");
                            params.x = initialX + dx;
                            params.y = initialY + dy;
                            windowManager.updateViewLayout(floatingButton, params);
                            return true; // Consume event to prevent click
                        }
                        return false;

                    case MotionEvent.ACTION_UP:
                        android.util.Log.d("VoiceOverlay", "ACTION_UP");
                        return false; // Let click listener handle it
                }
                return false;
            }
        });
    }

    private void handleButtonClick() {
        android.util.Log.d("VoiceOverlay", "Button clicked! isRecording=" + isRecording);
        if (!isRecording) {
            startRecording();
        } else {
            stopRecordingAndTranscribe();
        }
    }

    private void startRecording() {
        android.util.Log.d("VoiceOverlay", "startRecording called");
        File cacheDir = getCacheDir();
        audioRecorder.startRecording(cacheDir, new AudioRecorder.RecordingCallback() {
            @Override
            public void onRecordingStarted() {
                android.util.Log.d("VoiceOverlay", "Recording started successfully");
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        isRecording = true;
                        floatingButton.setBackgroundColor(Color.RED);
                        showToast("Recording...");
                    }
                });
            }

            @Override
            public void onRecordingStopped(File audioFile) {
                // Not used
            }

            @Override
            public void onError(String error) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        showToast("Error: " + error);
                        resetButton();
                    }
                });
            }
        });
    }

    private void stopRecordingAndTranscribe() {
        audioRecorder.stopRecording(new AudioRecorder.RecordingCallback() {
            @Override
            public void onRecordingStarted() {
                // Not used
            }

            @Override
            public void onRecordingStopped(final File audioFile) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        resetButton();
                        showToast("Transcribing...");

                        WhisperAPI.transcribeAudio(OverlayService.this, audioFile, new WhisperAPI.TranscriptionCallback() {
                            @Override
                            public void onSuccess(final String transcription) {
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        copyToClipboard(transcription);
                                        showToast("Copied: " + transcription);
                                        audioFile.delete();
                                    }
                                });
                            }

                            @Override
                            public void onError(final String error) {
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        showToast("Error: " + error);
                                        audioFile.delete();
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
                        showToast("Recording error: " + error);
                        resetButton();
                    }
                });
            }
        });
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Voice Transcription", text);
        clipboard.setPrimaryClip(clip);
    }

    private void resetButton() {
        isRecording = false;
        floatingButton.setBackgroundColor(Color.parseColor("#4CAF50"));
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingButton != null) {
            windowManager.removeView(floatingButton);
        }
        if (audioRecorder != null) {
            audioRecorder.release();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
