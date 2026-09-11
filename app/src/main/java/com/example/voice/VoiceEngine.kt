package com.example.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
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
    val speechRate: Float = 1.04f,
    val speechPitch: Float = 1.03f,
    val voiceTone: String = "Natural Warm"
)

class VoiceEngine(
    private val context: Context,
    private val onTranscriptFinalized: (String) -> Unit,
    private val onErrorOccurred: (String) -> Unit
) : RecognitionListener, TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var currentActiveLocale: Locale? = null

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _transcription = MutableStateFlow("")
    val transcription: StateFlow<String> = _transcription.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _statusMessage = MutableStateFlow("Tap to talk")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    // Voice-to-text dictation hooks
    var isDictationMode = false
        private set
    var onDictationPartialResult: ((String) -> Unit)? = null
    var onDictationCompleted: ((String) -> Unit)? = null

    var settings = VoiceSettings()
        set(value) {
            field = value
            applyTtsSettings()
        }

    init {
        initSpeechRecognizer()
        initTts()
    }

    fun isRecognitionAvailable(): Boolean {
        return try {
            SpeechRecognizer.isRecognitionAvailable(context) ||
                (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                 SpeechRecognizer.isOnDeviceRecognitionAvailable(context))
        } catch (_: Exception) {
            false
        }
    }

    private fun initSpeechRecognizer() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = when {
                SpeechRecognizer.isRecognitionAvailable(context) -> {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context) -> {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                }
                else -> {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
            }?.apply {
                setRecognitionListener(this@VoiceEngine)
            }
        } catch (_: Exception) {
            speechRecognizer = null
        }
    }

    private fun initTts() {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true

            // Set speech-optimized AudioAttributes for natural, clear acoustics
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(audioAttributes)

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

        val targetLocale = when (settings.languageCode) {
            "bn-BD", "bn" -> Locale("bn", "BD")
            "bn-IN" -> Locale("bn", "IN")
            "en-US", "en" -> Locale.US
            "en-GB" -> Locale.UK
            else -> Locale.getDefault()
        }

        switchLocaleAndSelectNaturalVoice(targetLocale)
    }

    private fun switchLocaleAndSelectNaturalVoice(locale: Locale) {
        if (currentActiveLocale == locale) return

        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale.US)
            currentActiveLocale = Locale.US
        } else {
            currentActiveLocale = locale
        }

        // Score available voices and pick the most natural, neural voice
        try {
            val allVoices = tts?.voices
            if (!allVoices.isNullOrEmpty()) {
                val matchingVoices = allVoices.filter {
                    it.locale.language.equals(currentActiveLocale?.language, ignoreCase = true)
                }
                if (matchingVoices.isNotEmpty()) {
                    val bestNaturalVoice = matchingVoices.maxByOrNull { voice ->
                        var score = 0
                        if (voice.quality == Voice.QUALITY_VERY_HIGH) score += 60
                        if (voice.quality == Voice.QUALITY_HIGH) score += 40
                        if (voice.quality == Voice.QUALITY_NORMAL) score += 20
                        if (voice.quality == Voice.QUALITY_LOW) score -= 40

                        val name = voice.name.lowercase()
                        if (name.contains("neural") || name.contains("natural")) score += 50
                        if (name.contains("studio") || name.contains("premium")) score += 35
                        if (name.contains("network") || voice.isNetworkConnectionRequired) score += 30
                        if (name.contains("-x-")) score += 25 // Google WaveNet / Journey models
                        if (!name.contains("robot") && !name.contains("legacy")) score += 10
                        score
                    }
                    if (bestNaturalVoice != null) {
                        tts?.voice = bestNaturalVoice
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun startDictation(
        onPartial: (String) -> Unit,
        onComplete: (String) -> Unit
    ) {
        isDictationMode = true
        onDictationPartialResult = onPartial
        onDictationCompleted = onComplete
        startListening()
    }

    fun stopDictation() {
        if (isDictationMode) {
            isDictationMode = false
            stopListening()
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
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)

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

    fun previewVoice() {
        val sample = when (settings.languageCode) {
            "bn-BD", "bn", "bn-IN" -> "নমস্কার! আমি মায়াক্স। আমি স্বাভাবিক এবং বন্ধুভাবাপন্ন গলায় আপনার সাথে কথা বলতে পারি।"
            else -> "Hello! I am MayaX AI. My voice is now natural, expressive, and conversational. How can I help you today?"
        }
        speak(sample)
    }

    fun speak(text: String) {
        if (!isTtsInitialized || tts == null) {
            return
        }

        // Clean and format text for human-like conversational delivery
        val cleanText = NaturalSpeechFormatter.format(text)

        if (cleanText.isBlank()) {
            _voiceState.value = VoiceState.IDLE
            _statusMessage.value = "Tap to talk"
            return
        }

        // Detect language heuristic for TTS voice switching
        val isBengali = cleanText.any { it in '\u0980'..'\u09FF' }
        val targetLocale = if (isBengali) Locale("bn", "BD") else Locale.US
        switchLocaleAndSelectNaturalVoice(targetLocale)

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
        if (isDictationMode) {
            isDictationMode = false
            onErrorOccurred(msg)
        }
        _voiceState.value = VoiceState.IDLE
        _statusMessage.value = msg
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""
        if (isDictationMode) {
            isDictationMode = false
            _voiceState.value = VoiceState.IDLE
            _statusMessage.value = "Tap to talk"
            if (text.isNotBlank()) {
                _transcription.value = text
                onDictationCompleted?.invoke(text)
            }
            return
        }
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
            if (isDictationMode) {
                onDictationPartialResult?.invoke(text)
            }
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
