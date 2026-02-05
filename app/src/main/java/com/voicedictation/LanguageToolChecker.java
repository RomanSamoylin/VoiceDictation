package com.voicedictation;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class LanguageToolChecker {
    private static final String TAG = "LanguageToolChecker";
    private static final String API_URL = "https://api.languagetool.org/v2/check";

    public interface CheckCallback {
        void onSuccess(String correctedText);
        void onError(String error);
    }

    public static void checkText(Context context, String text, CheckCallback callback) {
        new Thread(() -> {
            try {
                // LanguageTool API ожидает данные в формате x-www-form-urlencoded
                String postData = "text=" + java.net.URLEncoder.encode(text, "UTF-8") +
                        "&language=ru-RU" +
                        "&enabledOnly=false";

                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("Accept", "application/json");
                conn.setDoOutput(true);

                // Отправка данных
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = postData.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                // Получение ответа
                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    Scanner scanner = new Scanner(conn.getInputStream(), "UTF-8");
                    String response = scanner.useDelimiter("\\A").next();
                    scanner.close();

                    // Парсинг JSON и применение исправлений
                    String correctedText = applyCorrections(text, response);
                    callback.onSuccess(correctedText);
                } else {
                    callback.onError("Ошибка API: " + responseCode);
                }

                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "LanguageTool error: " + e.getMessage());
                callback.onError(e.getMessage());
            }
        }).start();
    }

    private static String applyCorrections(String originalText, String jsonResponse) {
        try {
            JSONObject response = new JSONObject(jsonResponse);
            JSONArray matches = response.getJSONArray("matches");

            if (matches.length() == 0) {
                return originalText; // Нет ошибок
            }

            // Создаем StringBuilder для результата
            StringBuilder result = new StringBuilder(originalText);

            // Применяем исправления с конца текста, чтобы индексы не сдвигались
            for (int i = matches.length() - 1; i >= 0; i--) {
                JSONObject match = matches.getJSONObject(i);
                JSONObject context = match.getJSONObject("context");

                int offset = match.getInt("offset");
                int length = match.getInt("length");
                String errorText = context.getString("text");

                // Получаем лучшее исправление
                JSONArray replacements = match.getJSONArray("replacements");
                if (replacements.length() > 0) {
                    JSONObject replacement = replacements.getJSONObject(0);
                    String correctedText = replacement.getString("value");

                    // Заменяем текст
                    result.replace(offset, offset + length, correctedText);
                }
            }

            return result.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error applying corrections: " + e.getMessage());
            return originalText;
        }
    }
}