package com.raahi.app.domain.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class VoiceStatus { Idle, Ready, Unavailable }

interface VoiceGuide {
    val status: StateFlow<VoiceStatus>
    fun prepare()
    fun speak(text: String)
    fun stop()
    fun release()
}

object NoOpVoiceGuide : VoiceGuide {
    override val status: StateFlow<VoiceStatus> = MutableStateFlow(VoiceStatus.Ready)
    override fun prepare() = Unit
    override fun speak(text: String) = Unit
    override fun stop() = Unit
    override fun release() = Unit
}
