package com.voiceoverlay;

import android.media.MediaRecorder;
import java.io.File;
import java.io.IOException;

public class AudioRecorder {
    private MediaRecorder mediaRecorder;
    private File outputFile;
    private boolean isRecording = false;
    private int sampleRate = 16000;
    private int channels = 1;  // 1 = mono, 2 = stereo
    private int bitRate = 128000;

    public interface RecordingCallback {
        void onRecordingStarted();
        void onRecordingStopped(File audioFile);
        void onError(String error);
    }

    public void setQuality(String quality) {
        if (quality.equals("High")) {
            sampleRate = 44100;
            channels = 2;  // Stereo
            bitRate = 256000;
        } else if (quality.equals("Medium")) {
            sampleRate = 22050;
            channels = 1;  // Mono
            bitRate = 192000;
        } else {  // Low (default)
            sampleRate = 16000;
            channels = 1;  // Mono
            bitRate = 128000;
        }
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
            mediaRecorder.setAudioEncodingBitRate(bitRate);
            mediaRecorder.setAudioSamplingRate(sampleRate);
            mediaRecorder.setAudioChannels(channels);
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

    public void pauseRecording() {
        if (isRecording && mediaRecorder != null) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    mediaRecorder.pause();
                }
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    public void resumeRecording() {
        if (isRecording && mediaRecorder != null) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    mediaRecorder.resume();
                }
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    public boolean isRecording() {
        return isRecording;
    }

    public File getOutputFile() {
        return outputFile;
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
