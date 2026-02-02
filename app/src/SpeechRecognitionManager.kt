package com.voicedictation

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import java.util.Locale

class SpeechRecognitionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SpeechRecognitionManager"
    }
    
    fun createSpeechIntent(
        prompt: String = "Говорите сейчас...",
        language: String = Locale.getDefault().toString(),
        maxResults: Int = 1
    ): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, maxResults)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
        }
    }
    
    fun getSupportedLanguages(): List<Pair<String, String>> {
        return listOf(
            "ru-RU" to "Русский",
            "en-US" to "Английский (США)",
            "en-GB" to "Английский (Великобритания)",
            "de-DE" to "Немецкий",
            "fr-FR" to "Французский",
            "es-ES" to "Испанский",
            "it-IT" to "Итальянский",
            "zh-CN" to "Китайский",
            "ja-JP" to "Японский",
            "ko-KR" to "Корейский"
        )
    }
    
    fun isSpeechRecognitionAvailable(): Boolean {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        val activities = context.packageManager.queryIntentActivities(intent, 0)
        return activities.isNotEmpty()
    }
}