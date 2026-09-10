package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class VoiceState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

data class VoiceSettings(
    val languageCode: String = "bn-BD", // "bn-BD", "en-US", "mixed"
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f
)

class VoiceEngine(
    private val context: Context,
    private val onTranscriptFinalized: (String) -> Unit,
    private val onErrorOccurred: (String) -> Unit
) : RecognitionListener, TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _transcription = MutableStateFlow("")
    val transcription: StateFlow<String> = _transcription.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _statusMessage = MutableStateFlow("Tap to talk")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    var settings = VoiceSettings()
        set(value) {
            field = value
            applyTtsSettings()
        }

    init {
        initSpeechRecognizer()
        initTts()
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(this@VoiceEngine)
            }
        }
    }

    private fun initTts() {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _voiceState.value = VoiceState.SPEAKING
                    _statusMessage.value = "Speaking..."
                }

                override fun onDone(utteranceId: String?) {
                    _voiceState.value = VoiceState.IDLE
                    _statusMessage.value = "Tap to talk"
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _voiceState.value = VoiceState.IDLE
                    _statusMessage.value = "Speech ended"
                }
            })
            applyTtsSettings()
        }
    }

    private fun applyTtsSettings() {
        if (!isTtsInitialized || tts == null) return
        tts?.setSpeechRate(settings.speechRate)
        tts?.setPitch(settings.speechPitch)

        val locale = when (settings.languageCode) {
            "bn-BD", "bn" -> Locale("bn", "BD")
            "bn-IN" -> Locale("bn", "IN")
            "en-US", "en" -> Locale.US
            "en-GB" -> Locale.UK
            else -> Locale.getDefault()
        }

        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Fallback to English if Bengali TTS data is missing on the specific Android device
            tts?.setLanguage(Locale.US)
        }
    }

    fun startListening() {
        // Interrupt TTS if speaking
        interrupt()

        if (speechRecognizer == null) {
            initSpeechRecognizer()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

            val langTag = when (settings.languageCode) {
                "bn-BD", "bn" -> "bn-BD"
                "bn-IN" -> "bn-IN"
                "en-US" -> "en-US"
                "mixed" -> "bn-BD,en-US"
                else -> "en-US"
            }
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("bn-BD", "en-US"))
        }

        _voiceState.value = VoiceState.LISTENING
        _statusMessage.value = "Listening..."
        _transcription.value = ""

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            _voiceState.value = VoiceState.ERROR
            _statusMessage.value = "Microphone error: ${e.message}"
            onErrorOccurred("Could not start speech recognizer")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            if (_voiceState.value == VoiceState.LISTENING) {
                _voiceState.value = VoiceState.THINKING
                _statusMessage.value = "Thinking..."
            }
        } catch (_: Exception) {}
    }

    fun interrupt() {
        try {
            if (tts?.isSpeaking == true) {
                tts?.stop()
            }
        } catch (_: Exception) {}

        if (_voiceState.value == VoiceState.SPEAKING) {
            _voiceState.value = VoiceState.IDLE
            _statusMessage.value = "Tap to talk"
        }
    }

    fun speak(text: String) {
        if (!isTtsInitialized || tts == null) {
            return
        }

        // Clean out action tags or markdown symbols for audio output
        val cleanText = text
            .replace(Regex("""ACTION:\s*\{.+?\}""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""```[\s\S]*?```"""), "Code snippet provided in chat.")
            .replace(Regex("""[*_#`~]"""), "")
            .trim()

        if (cleanText.isBlank()) {
            _voiceState.value = VoiceState.IDLE
            _statusMessage.value = "Tap to talk"
            return
        }

        // Detect language heuristic for TTS voice switching
        val isBengali = cleanText.any { it in '\u0980'..'\u09FF' }
        if (isBengali) {
            val res = tts?.setLanguage(Locale("bn", "BD"))
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.US)
            }
        } else {
            tts?.setLanguage(Locale.US)
        }

        _voiceState.value = VoiceState.SPEAKING
        _statusMessage.value = "Speaking..."
        val utteranceId = "mayax_${System.currentTimeMillis()}"
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun setThinkingState() {
        _voiceState.value = VoiceState.THINKING
        _statusMessage.value = "Thinking..."
    }

    fun setIdleState() {
        _voiceState.value = VoiceState.IDLE
        _statusMessage.value = "Tap to talk"
    }

    fun setErrorState(message: String) {
        _voiceState.value = VoiceState.ERROR
        _statusMessage.value = message
    }

    // SpeechRecognizer Callbacks
    override fun onReadyForSpeech(params: Bundle?) {
        _voiceState.value = VoiceState.LISTENING
        _statusMessage.value = "Listening..."
    }

    override fun onBeginningOfSpeech() {
        _statusMessage.value = "Hearing voice..."
    }

    override fun onRmsChanged(rmsdB: Float) {
        _rmsLevel.value = ((rmsdB + 2f).coerceIn(0f, 10f)) / 10f
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        _voiceState.value = VoiceState.THINKING
        _statusMessage.value = "Processing speech..."
    }

    override fun onError(error: Int) {
        val msg = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error for recognition"
            else -> "Speech recognition paused"
        }
        _voiceState.value = VoiceState.IDLE
        _statusMessage.value = msg
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""
        if (text.isNotBlank()) {
            _transcription.value = text
            _voiceState.value = VoiceState.THINKING
            _statusMessage.value = "Thinking..."
            onTranscriptFinalized(text)
        } else {
            _voiceState.value = VoiceState.IDLE
            _statusMessage.value = "Tap to talk"
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""
        if (text.isNotBlank()) {
            _transcription.value = text
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    fun destroy() {
        try {
            speechRecognizer?.destroy()
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
    }
}
