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

public class DeepSeekChecker {
    private static final String TAG = "DeepSeekChecker";
    private static final String API_URL = "https://api.deepseek.com/v1/chat/completions";
    private static final String API_KEY = "sk-";

    // Настройки лимита
    private static final int DAILY_TOKEN_LIMIT = 5000; // 5000 токенов в день
    private static final int MAX_TOKENS_PER_REQUEST = 500;

    public interface CheckCallback {
        void onSuccess(String correctedText, int tokensUsed);
        void onError(String error);
        void onLimitExceeded(int remainingTokens);
    }

    public static void checkText(Context context, String text, CheckCallback callback) {
        new Thread(() -> {
            try {
                // 1. Проверяем дневной лимит
                SharedPreferences prefs = context.getSharedPreferences("deepseek_limits", Context.MODE_PRIVATE);
                String today = getTodayDate();
                int tokensUsedToday = prefs.getInt(today, 0);

                // Рассчитываем примерное количество токенов для этого запроса
                // (приблизительно: 1 токен ≈ 4 символа на русском)
                int estimatedTokens = text.length() / 4;

                if (tokensUsedToday + estimatedTokens > DAILY_TOKEN_LIMIT) {
                    int remainingTokens = DAILY_TOKEN_LIMIT - tokensUsedToday;
                    callback.onLimitExceeded(remainingTokens);
                    return;
                }

                // 2. Отправляем запрос к API
                JSONObject request = new JSONObject();
                request.put("model", "deepseek-chat");

                JSONArray messages = new JSONArray();

                JSONObject systemMsg = new JSONObject();
                systemMsg.put("role", "system");
                systemMsg.put("content", "Исправь ошибки в тексте. Отвечай только исправленным текстом.");
                messages.put(systemMsg);

                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", "Исправь: " + text);
                messages.put(userMsg);

                request.put("messages", messages);
                request.put("max_tokens", Math.min(MAX_TOKENS_PER_REQUEST, DAILY_TOKEN_LIMIT - tokensUsedToday));

                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
                conn.setDoOutput(true);

                OutputStream os = conn.getOutputStream();
                os.write(request.toString().getBytes());
                os.close();

                int code = conn.getResponseCode();
                if (code == 200) {
                    Scanner scanner = new Scanner(conn.getInputStream());
                    String response = scanner.useDelimiter("\\A").next();
                    scanner.close();

                    JSONObject json = new JSONObject(response);
                    JSONArray choices = json.getJSONArray("choices");
                    if (choices.length() > 0) {
                        String corrected = choices.getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content");

                        // Получаем реальное количество использованных токенов из ответа
                        int tokensUsed = json.getJSONObject("usage").getInt("total_tokens");

                        // Обновляем счетчик токенов
                        int newTotal = tokensUsedToday + tokensUsed;
                        prefs.edit().putInt(today, newTotal).apply();

                        callback.onSuccess(corrected, tokensUsed);
                    } else {
                        callback.onError("Нет ответа");
                    }
                } else {
                    callback.onError("Ошибка API: код " + code);
                }

                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Ошибка: " + e.getMessage());
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

    public static int getRemainingTokens(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("deepseek_limits", Context.MODE_PRIVATE);
        String today = getTodayDate();
        int tokensUsedToday = prefs.getInt(today, 0);
        return DAILY_TOKEN_LIMIT - tokensUsedToday;
    }

    public static int getTokensUsedToday(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("deepseek_limits", Context.MODE_PRIVATE);
        String today = getTodayDate();
        return prefs.getInt(today, 0);
    }

    public static void resetLimits(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("deepseek_limits", Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }
}
