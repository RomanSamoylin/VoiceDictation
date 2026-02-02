package com.voicedictation

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.voicedictation.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    
    // Константы для запросов
    private companion object {
        const val REQUEST_CODE_SPEECH_INPUT = 100
        const val REQUEST_PERMISSION_RECORD_AUDIO = 101
    }
    
    // Переменные состояния
    private var isRecording = false
    private var lastRecognizedText = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        checkMicrophonePermission()
    }
    
    private fun setupUI() {
        // Настройка основной кнопки записи
        binding.recordButton.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startVoiceRecognition()
            }
        }
        
        // Кнопка копирования
        binding.copyButton.setOnClickListener {
            copyTextToClipboard()
        }
        
        // Кнопка очистки
        binding.clearButton.setOnClickListener {
            clearText()
        }
        
        // Дополнительная кнопка "Поделиться" (можно добавить позже)
        // binding.shareButton.setOnClickListener { shareText() }
        
        // Настройка анимаций (пульсация кнопки при записи)
        setupAnimations()
    }
    
    private fun setupAnimations() {
        // Простая пульсация для индикатора записи
        val scaleAnimation = android.view.animation.ScaleAnimation(
            1.0f, 1.2f, 1.0f, 1.2f,
            android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
            android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 500
            repeatCount = android.view.animation.Animation.INFINITE
            repeatMode = android.view.animation.Animation.REVERSE
        }
        
        // Применяем анимацию к индикатору
        binding.recordingIndicator.tag = scaleAnimation
    }
    
    private fun startRecordingAnimation() {
        val animation = binding.recordingIndicator.tag as? android.view.animation.ScaleAnimation
        animation?.let {
            binding.recordingIndicator.startAnimation(it)
        }
    }
    
    private fun stopRecordingAnimation() {
        binding.recordingIndicator.clearAnimation()
    }
    
    private fun checkMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Запрашиваем разрешение
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_PERMISSION_RECORD_AUDIO
            )
        }
    }
    
    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun startVoiceRecognition() {
        if (!hasMicrophonePermission()) {
            showPermissionSnackbar()
            return
        }
        
        if (!isSpeechRecognitionAvailable()) {
            showSpeechNotSupportedDialog()
            return
        }
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите сейчас...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            
            // Дополнительные настройки для лучшего распознавания
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
            
            // Для русского языка
            if (Locale.getDefault().language == "ru") {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            }
        }
        
        try {
            startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT)
            startRecordingUI()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            stopRecordingUI()
        }
    }
    
    private fun startRecordingUI() {
        isRecording = true
        binding.recordButton.text = getString(R.string.stop_recording)
        binding.recordButton.icon = ContextCompat.getDrawable(this, R.drawable.ic_stop)
        binding.recordingIndicator.visibility = android.view.View.VISIBLE
        binding.recordingStatusTextView.visibility = android.view.View.VISIBLE
        binding.recordButton.setBackgroundColor(ContextCompat.getColor(this, R.color.recording_red))
        startRecordingAnimation()
    }
    
    private fun stopRecording() {
        // Останавливаем запись (Google Speech API сам останавливается)
        stopRecordingUI()
    }
    
    private fun stopRecordingUI() {
        isRecording = false
        binding.recordButton.text = getString(R.string.start_recording)
        binding.recordButton.icon = ContextCompat.getDrawable(this, R.drawable.ic_mic)
        binding.recordingIndicator.visibility = android.view.View.GONE
        binding.recordingStatusTextView.visibility = android.view.View.GONE
        binding.recordButton.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
        stopRecordingAnimation()
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_SPEECH_INPUT) {
            stopRecordingUI()
            
            if (resultCode == RESULT_OK && data != null) {
                val result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val recognizedText = result?.firstOrNull()
                
                if (!recognizedText.isNullOrEmpty()) {
                    lastRecognizedText = recognizedText
                    binding.resultTextView.text = recognizedText
                    showSnackbar(getString(R.string.recognition_complete))
                    
                    // Автоматически копируем в буфер обмена
                    copyTextToClipboard(showMessage = false)
                    
                    // Показываем кнопки действий
                    binding.copyButton.visibility = android.view.View.VISIBLE
                    binding.clearButton.visibility = android.view.View.VISIBLE
                } else {
                    showSnackbar(getString(R.string.no_speech_detected))
                }
            } else {
                showSnackbar(getString(R.string.recognition_failed))
            }
        }
    }
    
    private fun copyTextToClipboard(showMessage: Boolean = true) {
        val text = binding.resultTextView.text.toString()
        if (text.isNotEmpty() && text != getString(R.string.placeholder_text)) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Распознанный текст", text)
            clipboard.setPrimaryClip(clip)
            
            if (showMessage) {
                showSnackbar(getString(R.string.text_copied))
            }
        } else if (showMessage) {
            showSnackbar("Нет текста для копирования")
        }
    }
    
    private fun clearText() {
        binding.resultTextView.text = getString(R.string.placeholder_text)
        showSnackbar(getString(R.string.text_cleared))
        lastRecognizedText = ""
    }
    
    private fun shareText() {
        val text = binding.resultTextView.text.toString()
        if (text.isNotEmpty() && text != getString(R.string.placeholder_text)) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, "Распознанный текст")
            }
            startActivity(Intent.createChooser(shareIntent, "Поделиться текстом"))
        } else {
            showSnackbar("Нет текста для отправки")
        }
    }
    
    private fun isSpeechRecognitionAvailable(): Boolean {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        val activities = packageManager.queryIntentActivities(intent, 0)
        return activities.isNotEmpty()
    }
    
    private fun showPermissionSnackbar() {
        Snackbar.make(
            binding.root,
            getString(R.string.mic_permission_required),
            Snackbar.LENGTH_LONG
        ).setAction(getString(R.string.grant_permission)) {
            checkMicrophonePermission()
        }.show()
    }
    
    private fun showSpeechNotSupportedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Распознавание речи не поддерживается")
            .setMessage("Ваше устройство не поддерживает распознавание речи. Пожалуйста, установите Google приложение или обновите систему.")
            .setPositiveButton("OK", null)
            .setNegativeButton("Установить Google") { _, _ ->
                openGooglePlayStore()
            }
            .show()
    }
    
    private fun openGooglePlayStore() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=com.google.android.googlequicksearchbox")
            }
            startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.googlequicksearchbox")
            }
            startActivity(intent)
        }
    }
    
    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            REQUEST_PERMISSION_RECORD_AUDIO -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    showSnackbar("Доступ к микрофону предоставлен")
                } else {
                    showSnackbar("Доступ к микрофону необходим для работы приложения")
                }
            }
        }
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("LAST_TEXT", lastRecognizedText)
        outState.putBoolean("IS_RECORDING", isRecording)
    }
    
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        lastRecognizedText = savedInstanceState.getString("LAST_TEXT", "")
        isRecording = savedInstanceState.getBoolean("IS_RECORDING", false)
        
        if (lastRecognizedText.isNotEmpty()) {
            binding.resultTextView.text = lastRecognizedText
        }
        
        if (isRecording) {
            startRecordingUI()
        }
    }
}