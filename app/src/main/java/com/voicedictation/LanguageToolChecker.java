package com.voicedictation;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Scanner;

public class LanguageToolChecker {
    private static final String TAG = "LanguageToolChecker";
    private static final String API_URL = "https://api.languagetool.org/v2/check";

    // Настройки лимита для LanguageTool
    // LanguageTool имеет лимиты: бесплатно - 20 запросов/день по 20 000 символов
    private static final int DAILY_CHAR_LIMIT = 20000; // 20k символов в день
    private static final int DAILY_REQUEST_LIMIT = 20; // 20 запросов в день
    private static final int MAX_CHARS_PER_REQUEST = 10000; // максимум за один запрос

    public interface CheckCallback {
        void onSuccess(String correctedText, int charsUsed);
        void onError(String error);
        void onLimitExceeded(int remainingChars, int remainingRequests);
    }

    public static void checkText(Context context, String text, CheckCallback callback) {
        new Thread(() -> {
            try {
                // 1. Проверяем дневные лимиты
                SharedPreferences prefs = context.getSharedPreferences("languagetool_limits", Context.MODE_PRIVATE);
                String today = getTodayDate();

                // Получаем использованные символы и запросы за сегодня
                int charsUsedToday = prefs.getInt(today + "_chars", 0);
                int requestsUsedToday = prefs.getInt(today + "_requests", 0);

                // Проверяем длину текста
                int textLength = text.length();

                if (textLength > MAX_CHARS_PER_REQUEST) {
                    callback.onError("Текст слишком длинный. Максимум " + MAX_CHARS_PER_REQUEST + " символов за раз.");
                    return;
                }

                // Проверяем лимит символов
                if (charsUsedToday + textLength > DAILY_CHAR_LIMIT) {
                    int remainingChars = DAILY_CHAR_LIMIT - charsUsedToday;
                    int remainingRequests = DAILY_REQUEST_LIMIT - requestsUsedToday;
                    callback.onLimitExceeded(remainingChars, remainingRequests);
                    return;
                }

                // Проверяем лимит запросов
                if (requestsUsedToday >= DAILY_REQUEST_LIMIT) {
                    int remainingChars = DAILY_CHAR_LIMIT - charsUsedToday;
                    int remainingRequests = 0;
                    callback.onLimitExceeded(remainingChars, remainingRequests);
                    return;
                }

                // 2. Подготавливаем запрос к LanguageTool
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

                    // 3. Обновляем счетчики лимитов
                    int newCharsUsed = charsUsedToday + textLength;
                    int newRequestsUsed = requestsUsedToday + 1;

                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putInt(today + "_chars", newCharsUsed);
                    editor.putInt(today + "_requests", newRequestsUsed);
                    editor.apply();

                    callback.onSuccess(correctedText, textLength);
                } else {
                    callback.onError("Ошибка API: код " + responseCode);
                }

                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "LanguageTool error: " + e.getMessage());
                callback.onError(e.getMessage());
            }
        }).start();
    }

    private static String getTodayDate() {
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.YEAR) + "-" +
                (cal.get(Calendar.MONTH) + 1) + "-" +
                cal.get(Calendar.DAY_OF_MONTH);
    }

    // Методы для получения информации о лимитах
    public static int getRemainingChars(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("languagetool_limits", Context.MODE_PRIVATE);
        String today = getTodayDate();
        int charsUsedToday = prefs.getInt(today + "_chars", 0);
        return DAILY_CHAR_LIMIT - charsUsedToday;
    }

    public static int getRemainingRequests(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("languagetool_limits", Context.MODE_PRIVATE);
        String today = getTodayDate();
        int requestsUsedToday = prefs.getInt(today + "_requests", 0);
        return DAILY_REQUEST_LIMIT - requestsUsedToday;
    }

    public static int getCharsUsedToday(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("languagetool_limits", Context.MODE_PRIVATE);
        String today = getTodayDate();
        return prefs.getInt(today + "_chars", 0);
    }

    public static int getRequestsUsedToday(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("languagetool_limits", Context.MODE_PRIVATE);
        String today = getTodayDate();
        return prefs.getInt(today + "_requests", 0);
    }

    public static void resetLimits(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("languagetool_limits", Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    // Метод для применения исправлений (без изменений)
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