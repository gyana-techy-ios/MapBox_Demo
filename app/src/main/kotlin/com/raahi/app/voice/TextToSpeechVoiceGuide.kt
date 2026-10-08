package com.raahi.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.appcompat.app.AppCompatDelegate
import com.raahi.app.domain.voice.InstructionLanguage
import com.raahi.app.domain.voice.VoiceGuide
import com.raahi.app.domain.voice.VoiceStatus
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TextToSpeechVoiceGuide(context: Context) : VoiceGuide {

    private val appContext = context.applicationContext
    private val mutableStatus = MutableStateFlow(VoiceStatus.Idle)
    override val status: StateFlow<VoiceStatus> = mutableStatus.asStateFlow()
    private var engine: TextToSpeech? = null

    @Synchronized
    override fun prepare() {
        if (engine != null) return
        engine = TextToSpeech(appContext) { code -> onEngineInitialised(code) }
    }

    override fun speak(text: String) {
        if (mutableStatus.value != VoiceStatus.Ready) return
        engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    override fun stop() {
        engine?.stop()
    }

    @Synchronized
    override fun release() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        mutableStatus.value = VoiceStatus.Idle
    }

    @Synchronized
    private fun onEngineInitialised(code: Int) {
        val tts = engine
        if (code != TextToSpeech.SUCCESS || tts == null) {
            markUnavailable()
            return
        }
        val availability = tts.setLanguage(preferredLocale())
        if (availability == TextToSpeech.LANG_MISSING_DATA || availability == TextToSpeech.LANG_NOT_SUPPORTED) {
            markUnavailable()
        } else {
            mutableStatus.value = VoiceStatus.Ready
        }
    }

    private fun markUnavailable() {
        engine?.shutdown()
        engine = null
        mutableStatus.value = VoiceStatus.Unavailable
    }

    private fun preferredLocale(): Locale =
        InstructionLanguage.speechLocaleFor(AppCompatDelegate.getApplicationLocales().get(0) ?: Locale.getDefault())
}
