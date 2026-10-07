package com.raahi.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raahi.app.domain.repository.LocationRepository
import com.raahi.app.domain.repository.PreferencesRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep { INTRO, LOCATION, NOTIFICATIONS }

enum class LocationOutcome { NOT_ASKED, PRECISE, APPROXIMATE, DENIED, PERMANENTLY_DENIED }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.INTRO,
    val locationOutcome: LocationOutcome = LocationOutcome.NOT_ASKED,
    val isCompleting: Boolean = false,
)

fun classifyLocationResult(
    fineGranted: Boolean,
    coarseGranted: Boolean,
    canShowRationale: Boolean,
): LocationOutcome = when {
    fineGranted -> LocationOutcome.PRECISE
    coarseGranted -> LocationOutcome.APPROXIMATE
    canShowRationale -> LocationOutcome.DENIED
    else -> LocationOutcome.PERMANENTLY_DENIED
}

class OnboardingViewModel(
    private val preferences: PreferencesRepository,
    private val locationRepository: LocationRepository,
    private val requiresNotificationStep: Boolean,
) : ViewModel() {

    private val mutableState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = mutableState.asStateFlow()

    private val finishedChannel = Channel<Unit>(Channel.BUFFERED)
    val finished = finishedChannel.receiveAsFlow()

    fun onIntroFinished() {
        mutableState.update { it.copy(step = OnboardingStep.LOCATION) }
    }

    fun onLocationOutcome(outcome: LocationOutcome) {
        locationRepository.refreshPermissionState()
        mutableState.update { it.copy(locationOutcome = outcome) }
    }

    fun onLocationStepContinue() {
        if (requiresNotificationStep) {
            mutableState.update { it.copy(step = OnboardingStep.NOTIFICATIONS) }
        } else {
            complete()
        }
    }

    fun onNotificationsHandled() = complete()

    private fun complete() {
        if (mutableState.value.isCompleting) return
        mutableState.update { it.copy(isCompleting = true) }
        viewModelScope.launch {
            preferences.setOnboardingDone(true)
            finishedChannel.send(Unit)
        }
    }
}
