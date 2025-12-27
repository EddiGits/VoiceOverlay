package com.voiceoverlay;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ChatGPTAPI {
    public interface ChatGPTCallback {
        void onSuccess(String response);
        void onError(String error);
    }

    public static void improveText(final Context context, final String apiKey, final String text, final ChatGPTCallback callback) {
        String prompt = "Please improve this text by fixing any grammar issues and making it more professional. Return only the improved text without any additional words or explanations:\n\n" + text;
        callAPI(context, apiKey, prompt, callback);
    }

    public static void applyVoiceEdit(final Context context, final String apiKey, final String originalText, final String editInstructions, final ChatGPTCallback callback) {
        String prompt = "Original text:\n" + originalText + "\n\nEdit instructions:\n" + editInstructions + "\n\nPlease edit the original text according to these edit instructions. Return only the edited text without any explanations.";
        callAPI(context, apiKey, prompt, callback);
    }

    private static void callAPI(final Context context, final String apiKey, final String prompt, final ChatGPTCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL("https://api.openai.com/v1/chat/completions");

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

                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(30000);
                    conn.setReadTimeout(60000);

                    // Create JSON request body
                    JSONObject jsonBody = new JSONObject();
                    jsonBody.put("model", "gpt-4o-mini");

                    JSONArray messages = new JSONArray();
                    JSONObject message = new JSONObject();
                    message.put("role", "user");
                    message.put("content", prompt);
                    messages.put(message);

                    jsonBody.put("messages", messages);
                    jsonBody.put("temperature", 0.3);

                    // Write request body
                    OutputStream os = conn.getOutputStream();
                    os.write(jsonBody.toString().getBytes("UTF-8"));
                    os.flush();
                    os.close();

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

                        // Parse JSON response
                        JSONObject jsonResponse = new JSONObject(response.toString());
                        JSONArray choices = jsonResponse.getJSONArray("choices");
                        if (choices.length() > 0) {
                            JSONObject choice = choices.getJSONObject(0);
                            JSONObject messageObj = choice.getJSONObject("message");
                            String content = messageObj.getString("content");

                            if (callback != null) {
                                callback.onSuccess(content.trim());
                            }
                        } else {
                            if (callback != null) {
                                callback.onError("No response from API");
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
                            callback.onError("API error (" + responseCode + "): " + error.toString());
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
