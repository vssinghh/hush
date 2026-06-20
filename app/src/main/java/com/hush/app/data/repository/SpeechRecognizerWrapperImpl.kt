package com.hush.app.data.repository

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.hush.app.domain.repository.SpeechRecognizerWrapper
import com.hush.app.domain.repository.SpeechState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeechRecognizerWrapperImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SpeechRecognizerWrapper {

    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    override val state: Flow<SpeechState> = _state

    private val mainScope = CoroutineScope(Dispatchers.Main)
    private var speechRecognizer: SpeechRecognizer? = null

    private fun getOrCreateRecognizer(): SpeechRecognizer? {
        if (speechRecognizer == null) {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createListener())
                }
            }
        }
        return speechRecognizer
    }

    override fun startListening() {
        mainScope.launch {
            try {
                val recognizer = getOrCreateRecognizer()
                if (recognizer == null) {
                    _state.value = SpeechState.Error(SpeechRecognizer.ERROR_CLIENT)
                    return@launch
                }
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
                recognizer.startListening(intent)
                _state.value = SpeechState.Listening
            } catch (e: Exception) {
                _state.value = SpeechState.Error(SpeechRecognizer.ERROR_CLIENT)
            }
        }
    }

    override fun stopListening() {
        mainScope.launch {
            speechRecognizer?.stopListening()
            _state.value = SpeechState.Idle
        }
    }

    fun destroy() {
        mainScope.launch {
            speechRecognizer?.destroy()
            speechRecognizer = null
            _state.value = SpeechState.Idle
            mainScope.cancel()
        }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = SpeechState.Listening
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                _state.value = SpeechState.WaveformUpdate(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                _state.value = SpeechState.Error(error)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                _state.value = SpeechState.FinalResult(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { text ->
                    _state.value = SpeechState.PartialResult(text)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }
}

