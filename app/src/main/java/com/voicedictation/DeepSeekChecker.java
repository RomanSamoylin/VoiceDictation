package com.voicedictation;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class DeepSeekChecker {
    private static final String TAG = "DeepSeekChecker";
    private static final String API_URL = "https://api.deepseek.com/v1/chat/completions";
    private static final String API_KEY = "sk-876fad4900aa47e3810b9cbc42857b41"; // Замените на ваш ключ

    public interface CheckCallback {
        void onSuccess(String correctedText);
        void onError(String error);
    }

    public static void checkText(Context context, String text, CheckCallback callback) {
        new Thread(() -> {
            try {
                // Формируем промпт для DeepSeek
                String systemPrompt = "Ты - помощник для коррекции текста. " +
                        "Исправь орфографические, грамматические ошибки и пунктуацию. " +
                        "Улучши стиль текста, если это необходимо. " +
                        "Сохрани оригинальный смысл и стиль автора. " +
                        "Отвечай ТОЛЬКО исправленным текстом, без пояснений.";

                JSONObject requestBody = new JSONObject();
                requestBody.put("model", "deepseek-chat");

                JSONArray messages = new JSONArray();

                JSONObject systemMessage = new JSONObject();
                systemMessage.put("role", "system");
                systemMessage.put("content", systemPrompt);
                messages.put(systemMessage);

                JSONObject userMessage = new JSONObject();
                userMessage.put("role", "user");
                userMessage.put("content", "Исправь этот текст: " + text);
                messages.put(userMessage);

                requestBody.put("messages", messages);
                requestBody.put("max_tokens", 2000);
                requestBody.put("temperature", 0.3);

                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);

                // Отправка данных
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = requestBody.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                // Получение ответа
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    Scanner scanner = new Scanner(conn.getInputStream(), "UTF-8");
                    String response = scanner.useDelimiter("\\A").next();
                    scanner.close();

                    // Парсинг JSON
                    JSONObject jsonResponse = new JSONObject(response);
                    JSONArray choices = jsonResponse.getJSONArray("choices");
                    if (choices.length() > 0) {
                        JSONObject choice = choices.getJSONObject(0);
                        JSONObject message = choice.getJSONObject("message");
                        String correctedText = message.getString("content");
                        callback.onSuccess(correctedText.trim());
                    } else {
                        callback.onError("Нет ответа от AI");
                    }
                } else {
                    Scanner scanner = new Scanner(conn.getErrorStream(), "UTF-8");
                    String errorResponse = scanner.useDelimiter("\\A").hasNext() ?
                            scanner.next() : "Unknown error";
                    scanner.close();
                    callback.onError("Ошибка API: " + responseCode + " - " + errorResponse);
                }

                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "DeepSeek error: " + e.getMessage());
                callback.onError(e.getMessage());
            }
        }).start();
    }
}