package com.voicedictation;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // Константы
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final String TAG = "VoiceDictation";

    // UI элементы
    private EditText editText;
    private ImageButton micButton;
    private ImageButton clearButton;
    private ImageButton copyButton;
    private ImageButton shareButton;
    private Button btnCheckBasic;
    private Button btnCheckPremium;
    private ProgressBar progressBarBasic;
    private ProgressBar progressBarPremium;
    private TextView charCountText;
    private ImageView voiceAnimationView;

    // Распознавание речи
    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private boolean isListening = false;

    // Анимации
    private Animation pulseAnimation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate: Начало инициализации");

        // Инициализация UI элементов
        initViews();

        // Настройка анимаций
        setupAnimations();

        // Настройка слушателей текста
        setupTextListeners();

        // Проверка и запрос разрешений
        checkPermissions();
    }

    private void initViews() {
        editText = findViewById(R.id.editText);
        micButton = findViewById(R.id.micButton);
        clearButton = findViewById(R.id.clearButton);
        copyButton = findViewById(R.id.copyButton);
        shareButton = findViewById(R.id.shareButton);
        btnCheckBasic = findViewById(R.id.btn_check_basic);
        btnCheckPremium = findViewById(R.id.btn_check_premium);
        progressBarBasic = findViewById(R.id.progressBarBasic);
        progressBarPremium = findViewById(R.id.progressBarPremium);
        charCountText = findViewById(R.id.charCountText);
        voiceAnimationView = findViewById(R.id.voiceAnimationView);

        // Скрываем прогресс-бары и анимацию по умолчанию
        progressBarBasic.setVisibility(View.GONE);
        progressBarPremium.setVisibility(View.GONE);
        voiceAnimationView.setVisibility(View.GONE);

        Log.d(TAG, "initViews: UI элементы инициализированы");
    }

    private void setupAnimations() {
        pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.pulse_animation);
        Log.d(TAG, "setupAnimations: Анимации загружены");
    }

    private void setupTextListeners() {
        // Слушатель для подсчета символов
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                int charCount = s.length();
                int wordCount = s.toString().trim().isEmpty() ? 0 :
                        s.toString().trim().split("\\s+").length;
                charCountText.setText(String.format("Символов: %d | Слов: %d", charCount, wordCount));

                // Показываем/скрываем кнопки в зависимости от наличия текста
                boolean hasText = charCount > 0;
                clearButton.setVisibility(hasText ? View.VISIBLE : View.GONE);
                copyButton.setVisibility(hasText ? View.VISIBLE : View.GONE);
                shareButton.setVisibility(hasText ? View.VISIBLE : View.GONE);
                btnCheckBasic.setEnabled(hasText);
                btnCheckPremium.setEnabled(hasText);
            }
        });

        Log.d(TAG, "setupTextListeners: Слушатели текста настроены");
    }

    private void checkPermissions() {
        String[] permissions = {
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE
        };

        boolean allGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                Log.d(TAG, "checkPermissions: Разрешение " + permission + " не предоставлено");
                break;
            }
        }

        if (allGranted) {
            Log.d(TAG, "checkPermissions: Все разрешения уже предоставлены");
            setupSpeechRecognition();
        } else {
            Log.d(TAG, "checkPermissions: Запрашиваем разрешения");
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    Log.d(TAG, "onRequestPermissionsResult: Отказано в разрешении " + permissions[i]);
                }
            }

            if (allGranted) {
                Log.d(TAG, "onRequestPermissionsResult: Все разрешения предоставлены");
                showToast("Разрешения получены!");
                setupSpeechRecognition();
            } else {
                Log.w(TAG, "onRequestPermissionsResult: Не все разрешения предоставлены");
                showToast("Для работы приложения нужны все разрешения");

                // Показать диалог с объяснением
                new AlertDialog.Builder(this)
                        .setTitle("Требуются разрешения")
                        .setMessage("Приложению нужны разрешения для:\n" +
                                "• Записи аудио (для распознавания речи)\n" +
                                "• Доступа к интернету (для проверки текста)")
                        .setPositiveButton("OK", null)
                        .show();
            }
        }
    }

    private void setupSpeechRecognition() {
        Log.d(TAG, "setupSpeechRecognition: Настройка распознавания речи");

        // Проверка доступности распознавания речи
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "setupSpeechRecognition: Распознавание речи недоступно");
            showToast("Распознавание речи не поддерживается на этом устройстве");
            micButton.setEnabled(false);
            return;
        }

        Log.d(TAG, "setupSpeechRecognition: SpeechRecognizer доступен");

        try {
            // Создание SpeechRecognizer
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            Log.d(TAG, "setupSpeechRecognition: SpeechRecognizer создан");

            // Настройка Intent для распознавания
            speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            speechRecognizerIntent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            );
            speechRecognizerIntent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    Locale.getDefault()
            );
            speechRecognizerIntent.putExtra(
                    RecognizerIntent.EXTRA_PROMPT,
                    "Говорите сейчас..."
            );
            speechRecognizerIntent.putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    true
            );
            speechRecognizerIntent.putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    5
            );

            Log.d(TAG, "setupSpeechRecognition: Intent настроен с языком: " + Locale.getDefault());

            // Настройка слушателя
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    Log.d(TAG, "RecognitionListener: onReadyForSpeech");
                    runOnUiThread(() -> {
                        showToast("Говорите...");
                        micButton.setImageResource(R.drawable.ic_mic_24);
                        voiceAnimationView.setVisibility(View.VISIBLE);
                        voiceAnimationView.startAnimation(pulseAnimation);
                    });
                }

                @Override
                public void onBeginningOfSpeech() {
                    Log.d(TAG, "RecognitionListener: onBeginningOfSpeech");
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    // Можно использовать для визуализации громкости
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                    // Не используется обычно
                }

                @Override
                public void onEndOfSpeech() {
                    Log.d(TAG, "RecognitionListener: onEndOfSpeech");
                    runOnUiThread(() -> {
                        voiceAnimationView.clearAnimation();
                        voiceAnimationView.setVisibility(View.GONE);
                        micButton.setImageResource(R.drawable.ic_mic_white);
                        showToast("Речь закончилась");
                    });
                }

                @Override
                public void onError(int error) {
                    String errorMessage = getErrorText(error);
                    Log.e(TAG, "RecognitionListener: onError - " + errorMessage + " (код: " + error + ")");
                    runOnUiThread(() -> {
                        isListening = false;
                        micButton.setImageResource(R.drawable.ic_mic_white);
                        voiceAnimationView.clearAnimation();
                        voiceAnimationView.setVisibility(View.GONE);

                        // Не показываем ошибку "нет совпадений" - это нормально
                        if (error != SpeechRecognizer.ERROR_NO_MATCH) {
                            showToast("Ошибка: " + errorMessage);
                        }
                    });
                }

                @Override
                public void onResults(Bundle results) {
                    Log.d(TAG, "RecognitionListener: onResults");
                    ArrayList<String> matches = results.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                    );

                    if (matches != null && !matches.isEmpty()) {
                        String spokenText = matches.get(0);
                        Log.d(TAG, "RecognitionListener: Распознанный текст: " + spokenText);

                        runOnUiThread(() -> {
                            // Добавляем текст в EditText
                            String currentText = editText.getText().toString();
                            if (!currentText.isEmpty() && !currentText.endsWith(" ") &&
                                    !currentText.endsWith("\n")) {
                                currentText += " ";
                            }
                            editText.setText(currentText + spokenText);
                            editText.setSelection(editText.getText().length());

                            showToast("Текст добавлен");
                        });
                    } else {
                        Log.w(TAG, "RecognitionListener: Нет результатов распознавания");
                    }

                    runOnUiThread(() -> {
                        isListening = false;
                        micButton.setImageResource(R.drawable.ic_mic_white);
                        voiceAnimationView.clearAnimation();
                        voiceAnimationView.setVisibility(View.GONE);
                    });
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    // Частичные результаты можно использовать для live-отображения
                    ArrayList<String> matches = partialResults.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                    );
                    if (matches != null && !matches.isEmpty()) {
                        Log.d(TAG, "RecognitionListener: onPartialResults - " + matches.get(0));
                    }
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                    Log.d(TAG, "RecognitionListener: onEvent - " + eventType);
                }
            });

            Log.d(TAG, "setupSpeechRecognition: Слушатель настроен");

            // Настройка кнопок
            setupButtons();

            showToast("Микрофон готов к использованию");
            Log.d(TAG, "setupSpeechRecognition: Распознавание речи настроено успешно");

        } catch (Exception e) {
            Log.e(TAG, "setupSpeechRecognition: Ошибка настройки - " + e.getMessage(), e);
            showToast("Ошибка настройки распознавания речи: " + e.getMessage());
            micButton.setEnabled(false);
        }
    }

    private void setupButtons() {
        Log.d(TAG, "setupButtons: Настройка кнопок");

        // Кнопка микрофона
        micButton.setOnClickListener(v -> {
            Log.d(TAG, "micButton: onClick");
            if (!isListening) {
                startListening();
            } else {
                stopListening();
            }
        });

        // Кнопка очистки
        clearButton.setOnClickListener(v -> {
            Log.d(TAG, "clearButton: onClick");
            new AlertDialog.Builder(this)
                    .setTitle("Очистить текст")
                    .setMessage("Вы уверены, что хотите очистить весь текст?")
                    .setPositiveButton("Очистить", (dialog, which) -> {
                        editText.setText("");
                        showToast("Текст очищен");
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        });

        // Кнопка копирования
        copyButton.setOnClickListener(v -> {
            Log.d(TAG, "copyButton: onClick");
            String text = editText.getText().toString();
            if (!text.isEmpty()) {
                ClipboardManager clipboard =
                        (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Распознанный текст", text);
                clipboard.setPrimaryClip(clip);
                showToast("Текст скопирован в буфер обмена");
            }
        });

        // Кнопка поделиться
        shareButton.setOnClickListener(v -> {
            Log.d(TAG, "shareButton: onClick");
            String text = editText.getText().toString();
            if (!text.isEmpty()) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, text);
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Текст из VoiceDictation");
                startActivity(Intent.createChooser(shareIntent, "Поделиться текстом"));
            }
        });

        // Кнопка базовой проверки
        btnCheckBasic.setOnClickListener(v -> {
            Log.d(TAG, "btnCheckBasic: onClick");
            String text = editText.getText().toString().trim();
            if (text.isEmpty()) {
                showToast("Введите текст для проверки");
                return;
            }

            progressBarBasic.setVisibility(View.VISIBLE);
            btnCheckBasic.setEnabled(false);
            showToast("Проверяем базовой проверкой…");

            LanguageToolChecker.checkText(MainActivity.this, text,
                    new LanguageToolChecker.CheckCallback() {
                        @Override
                        public void onSuccess(String correctedText) {
                            runOnUiThread(() -> {
                                progressBarBasic.setVisibility(View.GONE);
                                btnCheckBasic.setEnabled(true);

                                if (!text.equals(correctedText)) {
                                    showCorrectionDialog("Результат базовой проверки",
                                            text, correctedText);
                                } else {
                                    showToast("Текст проверен, ошибок не найдено");
                                }
                            });
                        }

                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> {
                                progressBarBasic.setVisibility(View.GONE);
                                btnCheckBasic.setEnabled(true);
                                showToast("Ошибка базовой проверки: " + error);
                                Log.e(TAG, "LanguageTool error: " + error);
                            });
                        }
                    });
        });

        // Кнопка премиум-проверки
        btnCheckPremium.setOnClickListener(v -> {
            Log.d(TAG, "btnCheckPremium: onClick");
            String text = editText.getText().toString().trim();
            if (text.isEmpty()) {
                showToast("Введите текст для проверки");
                return;
            }

            if (text.length() > 4000) {
                showToast("Текст слишком длинный для премиум-проверки (макс. 4000 символов)");
                return;
            }

            progressBarPremium.setVisibility(View.VISIBLE);
            btnCheckPremium.setEnabled(false);
            showToast("Проверяем премиум-проверкой…");

            DeepSeekChecker.checkText(MainActivity.this, text,
                    new DeepSeekChecker.CheckCallback() {
                        @Override
                        public void onSuccess(String correctedText) {
                            runOnUiThread(() -> {
                                progressBarPremium.setVisibility(View.GONE);
                                btnCheckPremium.setEnabled(true);

                                if (!text.equals(correctedText)) {
                                    showCorrectionDialog("Результат премиум-проверки",
                                            text, correctedText);
                                } else {
                                    showToast("Текст проверен, ошибок не найдено");
                                }
                            });
                        }

                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> {
                                progressBarPremium.setVisibility(View.GONE);
                                btnCheckPremium.setEnabled(true);
                                showToast("Ошибка премиум-проверки: " + error);
                                Log.e(TAG, "DeepSeek error: " + error);
                            });
                        }
                    });
        });

        Log.d(TAG, "setupButtons: Все кнопки настроены");
    }

    private void startListening() {
        Log.d(TAG, "startListening: Попытка начать запись");

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "startListening: Нет разрешения на запись аудио");
            showToast("Нет разрешения на запись аудио");
            checkPermissions();
            return;
        }

        if (speechRecognizer == null) {
            Log.e(TAG, "startListening: SpeechRecognizer не инициализирован");
            showToast("Распознавание речи не настроено");
            return;
        }

        try {
            Log.d(TAG, "startListening: Запуск speechRecognizer.startListening()");
            speechRecognizer.startListening(speechRecognizerIntent);
            isListening = true;
            showToast("Слушаю...");
        } catch (Exception e) {
            Log.e(TAG, "startListening: Ошибка запуска - " + e.getMessage(), e);
            showToast("Ошибка запуска распознавания: " + e.getMessage());
            isListening = false;
            micButton.setImageResource(R.drawable.ic_mic_white);
            voiceAnimationView.clearAnimation();
            voiceAnimationView.setVisibility(View.GONE);
        }
    }

    private void stopListening() {
        Log.d(TAG, "stopListening: Остановка записи");
        if (speechRecognizer != null && isListening) {
            try {
                speechRecognizer.stopListening();
            } catch (Exception e) {
                Log.e(TAG, "stopListening: Ошибка остановки - " + e.getMessage());
            }
        }
        isListening = false;
        micButton.setImageResource(R.drawable.ic_mic_white);
        voiceAnimationView.clearAnimation();
        voiceAnimationView.setVisibility(View.GONE);
    }

    private void showCorrectionDialog(String title, String originalText, String correctedText) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);

        // Простой диалог с кнопками
        builder.setMessage("Применить исправления к тексту?")
                .setPositiveButton("Применить", (dialog, which) -> {
                    editText.setText(correctedText);
                    showToast("Исправления применены");
                })
                .setNegativeButton("Отмена", (dialog, which) -> {
                    // Оставляем оригинальный текст
                })
                .show();
    }

    private String getErrorText(int errorCode) {
        String message;
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                message = "Ошибка аудиозаписи";
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                message = "Ошибка клиента";
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                message = "Недостаточно разрешений";
                break;
            case SpeechRecognizer.ERROR_NETWORK:
                message = "Ошибка сети";
                break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                message = "Таймаут сети";
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                message = "Речь не распознана";
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                message = "Распознаватель занят";
                break;
            case SpeechRecognizer.ERROR_SERVER:
                message = "Ошибка сервера";
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                message = "Таймаут речи";
                break;
            default:
                message = "Неизвестная ошибка (" + errorCode + ")";
        }
        return message;
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: Приложение на паузе");
        stopListening();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: Уничтожение активности");
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            Log.d(TAG, "onDestroy: SpeechRecognizer уничтожен");
        }
    }
}