package com.voiceoverlay;

import android.media.MediaRecorder;
import java.io.File;
import java.io.IOException;

public class AudioRecorder {
    private MediaRecorder mediaRecorder;
    private File outputFile;
    private boolean isRecording = false;

    public interface RecordingCallback {
        void onRecordingStarted();
        void onRecordingStopped(File audioFile);
        void onError(String error);
    }

    public void startRecording(File outputDir, RecordingCallback callback) {
        try {
            // Create output file - use .m4a format (better compatibility with Whisper)
            outputFile = new File(outputDir, "voice_" + System.currentTimeMillis() + ".m4a");

            // Initialize MediaRecorder
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioEncodingBitRate(128000);
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setOutputFile(outputFile.getAbsolutePath());

            mediaRecorder.prepare();
            mediaRecorder.start();

            isRecording = true;
            if (callback != null) {
                callback.onRecordingStarted();
            }
        } catch (IOException e) {
            isRecording = false;
            if (callback != null) {
                callback.onError("Failed to start recording: " + e.getMessage());
            }
        }
    }

    public void stopRecording(RecordingCallback callback) {
        if (!isRecording || mediaRecorder == null) {
            if (callback != null) {
                callback.onError("Not currently recording");
            }
            return;
        }

        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;

            if (callback != null) {
                callback.onRecordingStopped(outputFile);
            }
        } catch (Exception e) {
            isRecording = false;
            if (callback != null) {
                callback.onError("Failed to stop recording: " + e.getMessage());
            }
        }
    }

    public boolean isRecording() {
        return isRecording;
    }

    public void release() {
        if (mediaRecorder != null) {
            try {
                if (isRecording) {
                    mediaRecorder.stop();
                }
                mediaRecorder.release();
            } catch (Exception e) {
                // Ignore
            }
            mediaRecorder = null;
        }
        isRecording = false;
    }
}
