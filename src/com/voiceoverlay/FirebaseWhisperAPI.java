package com.voiceoverlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class FirebaseWhisperAPI {
    // Firebase Cloud Function URL - will be configured after deployment
    private static final String FIREBASE_FUNCTION_URL = "https://YOUR_REGION-YOUR_PROJECT_ID.cloudfunctions.net/transcribeAudio";

    private static final String PREFS_NAME = "VoiceOverlayPrefs";
    private static final String KEY_TRANSCRIPTION_PROMPT = "transcription_prompt";
    private static final String KEY_WHISPER_MODEL = "whisper_model";

    public interface TranscriptionCallback {
        void onSuccess(String transcription);
        void onError(String error);
    }

    public static void transcribeAudio(final Context context, final File audioFile, final TranscriptionCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    // Get transcription settings
                    SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    String transcriptionPrompt = prefs.getString(KEY_TRANSCRIPTION_PROMPT, "");
                    String whisperModel = prefs.getString(KEY_WHISPER_MODEL, "whisper-1");

                    URL url = new URL(FIREBASE_FUNCTION_URL);

                    // Use active network binding for DNS resolution
                    ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                    Network activeNetwork = null;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        activeNetwork = cm.getActiveNetwork();
                    }

                    if (activeNetwork != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        conn = (HttpURLConnection) activeNetwork.openConnection(url);
                    } else {
                        conn = (HttpURLConnection) url.openConnection();
                    }

                    // Create multipart form data
                    String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
                    String CRLF = "\r\n";

                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(30000);
                    conn.setReadTimeout(120000);

                    DataOutputStream request = new DataOutputStream(conn.getOutputStream());

                    // Add model parameter
                    request.writeBytes("--" + boundary + CRLF);
                    request.writeBytes("Content-Disposition: form-data; name=\"model\"" + CRLF);
                    request.writeBytes(CRLF);
                    request.writeBytes(whisperModel + CRLF);

                    // Add prompt parameter if provided
                    if (!transcriptionPrompt.isEmpty()) {
                        request.writeBytes("--" + boundary + CRLF);
                        request.writeBytes("Content-Disposition: form-data; name=\"prompt\"" + CRLF);
                        request.writeBytes(CRLF);
                        request.writeBytes(transcriptionPrompt + CRLF);
                    }

                    // Add audio file
                    request.writeBytes("--" + boundary + CRLF);
                    request.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + audioFile.getName() + "\"" + CRLF);
                    request.writeBytes("Content-Type: audio/m4a" + CRLF);
                    request.writeBytes(CRLF);

                    FileInputStream fileInputStream = new FileInputStream(audioFile);
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                        request.write(buffer, 0, bytesRead);
                    }
                    fileInputStream.close();

                    request.writeBytes(CRLF);
                    request.writeBytes("--" + boundary + "--" + CRLF);
                    request.flush();
                    request.close();

                    // Check response code
                    int responseCode = conn.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        // Read response
                        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            response.append(line);
                        }
                        br.close();

                        // Parse JSON response from Firebase
                        JSONObject jsonResponse = new JSONObject(response.toString());

                        if (jsonResponse.has("error")) {
                            String error = jsonResponse.getString("error");
                            if (callback != null) {
                                callback.onError("Firebase error: " + error);
                            }
                        } else if (jsonResponse.has("text")) {
                            String transcription = jsonResponse.getString("text");
                            if (callback != null) {
                                callback.onSuccess(transcription);
                            }
                        } else {
                            if (callback != null) {
                                callback.onError("Invalid response from Firebase");
                            }
                        }
                    } else {
                        // Read error response
                        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
                        StringBuilder error = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            error.append(line);
                        }
                        br.close();

                        if (callback != null) {
                            callback.onError("Firebase API error (" + responseCode + "): " + error.toString());
                        }
                    }
                } catch (Exception e) {
                    if (callback != null) {
                        callback.onError("Error: " + e.getMessage());
                    }
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }
        }).start();
    }
}
