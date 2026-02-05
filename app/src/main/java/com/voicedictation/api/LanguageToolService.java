package com.voicedictation.api;

import android.content.Context;
import android.util.Log;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class LanguageToolService {

    private static final String TAG = "LanguageToolService";
    private static final String BASE_URL = "https://api.languagetool.org/v2/check";

    private OkHttpClient client;
    private Context context;

    // Интерфейс Callback внутри класса
    public interface SpellCheckCallback {
        void onProgress(String message);
        void onSuccess(String correctedText, int fixesCount);
        void onError(String errorMessage);
    }

    public LanguageToolService(Context context) {
        this.context = context;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        Log.d(TAG, "LanguageToolService инициализирован");
    }

    public void checkText(String text, SpellCheckCallback callback) {
        Log.d(TAG, "checkText: Начало проверки текста");

        if (text == null || text.trim().isEmpty()) {
            callback.onError("Текст пуст");
            return;
        }

        callback.onProgress("Подключение к LanguageTool...");

        try {
            // Формируем тело запроса
            RequestBody formBody = new FormBody.Builder()
                    .add("text", text)
                    .add("language", "ru")
                    .add("enabledOnly", "false")
                    .add("level", "picky") // Более строгая проверка
                    .build();

            Request request = new Request.Builder()
                    .url(BASE_URL)
                    .post(formBody)
                    .addHeader("Accept", "application/json")
                    .addHeader("User-Agent", "VoiceDictationApp/1.0")
                    .build();

            Log.d(TAG, "checkText: Отправка запроса на " + BASE_URL);

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "checkText: Ошибка сети - " + e.getMessage(), e);
                    callback.onError("Ошибка сети: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        String errorMsg = "Ошибка сервера: " + response.code();
                        Log.e(TAG, "checkText: " + errorMsg);
                        callback.onError(errorMsg);
                        return;
                    }

                    try {
                        String responseBody = response.body().string();
                        Log.d(TAG, "checkText: Получен ответ от сервера");

                        // Парсим JSON ответ
                        JSONObject json = new JSONObject(responseBody);
                        JSONArray matches = json.optJSONArray("matches");

                        int fixesCount = 0;
                        String correctedText = text;

                        if (matches != null && matches.length() > 0) {
                            fixesCount = matches.length();
                            Log.d(TAG, "checkText: Найдено " + fixesCount + " ошибок");
                            callback.onProgress("Найдено " + fixesCount + " ошибок. Применяю исправления...");

                            // Применяем исправления
                            correctedText = applyCorrections(text, matches);
                        } else {
                            Log.d(TAG, "checkText: Ошибок не найдено");
                            callback.onProgress("Ошибок не найдено");
                        }

                        // Улучшаем пунктуацию
                        correctedText = enhancePunctuation(correctedText);

                        callback.onSuccess(correctedText, fixesCount);
                        Log.d(TAG, "checkText: Проверка завершена успешно");

                    } catch (JSONException e) {
                        Log.e(TAG, "checkText: Ошибка парсинга JSON - " + e.getMessage(), e);
                        callback.onError("Ошибка обработки ответа: " + e.getMessage());
                    } catch (Exception e) {
                        Log.e(TAG, "checkText: Неизвестная ошибка - " + e.getMessage(), e);
                        callback.onError("Неизвестная ошибка: " + e.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "checkText: Ошибка формирования запроса - " + e.getMessage(), e);
            callback.onError("Ошибка формирования запроса: " + e.getMessage());
        }
    }

    private String applyCorrections(String originalText, JSONArray matches) {
        try {
            StringBuilder correctedText = new StringBuilder(originalText);

            // Применяем исправления с конца, чтобы не сбивать индексы
            for (int i = matches.length() - 1; i >= 0; i--) {
                try {
                    JSONObject match = matches.getJSONObject(i);
                    int offset = match.getInt("offset");
                    int length = match.getInt("length");
                    JSONArray replacements = match.optJSONArray("replacements");

                    if (replacements != null && replacements.length() > 0) {
                        String replacement = replacements.getJSONObject(0).getString("value");

                        // Проверяем границы
                        if (offset >= 0 && offset + length <= correctedText.length()) {
                            correctedText.replace(offset, offset + length, replacement);
                        } else {
                            Log.w(TAG, "applyCorrections: Неверные границы исправления - offset=" +
                                    offset + ", length=" + length + ", text length=" + correctedText.length());
                        }
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "applyCorrections: Ошибка парсинга совпадения - " + e.getMessage());
                    continue;
                }
            }

            return correctedText.toString();

        } catch (Exception e) {
            Log.e(TAG, "applyCorrections: Критическая ошибка - " + e.getMessage(), e);
            return originalText;
        }
    }

    private String enhancePunctuation(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        try {
            String result = text.trim();

            // 1. Убираем лишние пробелы
            result = result.replaceAll("\\s+", " ");

            // 2. Исправляем пробелы вокруг знаков препинания
            result = result.replaceAll("\\s+([.,!?:;])", "$1");
            result = result.replaceAll("([.,!?:;])(?=[а-яА-Яa-zA-Z])", "$1 ");

            // 3. Заглавная буква в начале предложения
            if (!result.isEmpty()) {
                result = Character.toUpperCase(result.charAt(0)) + result.substring(1);
            }

            // 4. Точка в конце предложения, если нет знака препинания
            if (!result.matches(".*[.!?:;]$")) {
                result += ".";
            }

            // 5. Исправляем множественные знаки препинания
            result = result.replaceAll("([.!?:;])\\1+", "$1");

            // 6. Добавляем пробел после запятой, если нужно
            result = result.replaceAll(",([^\\s])", ", $1");

            Log.d(TAG, "enhancePunctuation: Пунктуация улучшена");
            return result;

        } catch (Exception e) {
            Log.e(TAG, "enhancePunctuation: Ошибка улучшения пунктуации - " + e.getMessage());
            return text;
        }
    }

    public String getServiceName() {
        return "LanguageTool";
    }

    public boolean isAvailable() {
        // LanguageTool всегда доступен, так как бесплатный
        return true;
    }

    // Вспомогательные методы для совместимости со старым кодом
    public static void checkText(Context context, String text, final CheckCallback callback) {
        LanguageToolService service = new LanguageToolService(context);

        // Создаем SpellCheckCallback
        SpellCheckCallback spellCheckCallback = new SpellCheckCallback() {
            @Override
            public void onProgress(String message) {
                // Игнорируем прогресс в старом API
            }

            @Override
            public void onSuccess(String correctedText, int fixesCount) {
                callback.onSuccess(correctedText);
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        };

        service.checkText(text, spellCheckCallback);
    }

    // Старый интерфейс для совместимости
    public interface CheckCallback {
        void onSuccess(String correctedText);
        void onError(String error);
    }
}