package com.raahi.app.feature.guidance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raahi.app.core.network.ConnectivityMonitor
import com.raahi.app.core.network.ConnectivityState
import com.raahi.app.core.result.AppError
import com.raahi.app.core.result.AppResult
import com.raahi.app.core.time.Clock
import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.LocationPermissionState
import com.raahi.app.domain.model.Route
import com.raahi.app.domain.model.RouteStep
import com.raahi.app.domain.model.Trip
import com.raahi.app.domain.model.TripStop
import com.raahi.app.domain.model.UserLocation
import com.raahi.app.domain.repository.LocationRepository
import com.raahi.app.domain.repository.PreferencesRepository
import com.raahi.app.domain.repository.TripRepository
import com.raahi.app.domain.usecase.ActiveRouteStore
import com.raahi.app.domain.usecase.GuidanceEngine
import com.raahi.app.domain.usecase.GuidanceProgress
import com.raahi.app.domain.usecase.PlanRoutesUseCase
import com.raahi.app.domain.voice.VoiceGuide
import com.raahi.app.domain.voice.VoiceStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GuidancePhase { Loading, Active, Failed, PermissionDenied, TripMissing }

enum class AnnouncementKind { Upcoming, Imminent, Arrived, ReachedStop, Rerouting }

fun interface GuidancePhraser {
    fun phrase(kind: AnnouncementKind, step: RouteStep?, distanceMeters: Double, stop: TripStop?): String
}

data class ArrivalState(val stop: TripStop?, val isFinal: Boolean)

data class GuidanceUiState(
    val phase: GuidancePhase = GuidancePhase.Loading,
    val error: AppError? = null,
    val trip: Trip? = null,
    val route: Route? = null,
    val progress: GuidanceProgress? = null,
    val userPoint: GeoPoint? = null,
    val etaMillis: Long? = null,
    val isRerouting: Boolean = false,
    val needsConnectionToReroute: Boolean = false,
    val waitingForGps: Boolean = false,
    val arrival: ArrivalState? = null,
    val voiceEnabled: Boolean = true,
)

sealed interface GuidanceEvent {
    data object VoiceUnavailable : GuidanceEvent
}

class GuidanceViewModel(
    private val tripId: Long,
    private val routeIndex: Int,
    private val trips: TripRepository,
    private val routeStore: ActiveRouteStore,
    private val planRoutes: PlanRoutesUseCase,
    private val locationRepository: LocationRepository,
    private val preferences: PreferencesRepository,
    private val connectivity: ConnectivityMonitor,
    private val voice: VoiceGuide,
    private val clock: Clock,
    private val phraser: GuidancePhraser,
) : ViewModel() {

    private val mutableState = MutableStateFlow(GuidanceUiState())
    val uiState: StateFlow<GuidanceUiState> = mutableState.asStateFlow()

    private val eventChannel = Channel<GuidanceEvent>(Channel.BUFFERED)
    val events: Flow<GuidanceEvent> = eventChannel.receiveAsFlow()

    private var engine: GuidanceEngine? = null
    private var stopsInOrder: List<TripStop> = emptyList()
    private var stepLegIndices: List<Int> = emptyList()
    private var legBase = 0
    private var currentLeg = 0
    private var lastFixAt: Long? = null
    private var startedAt = 0L
    private var lastRerouteAt: Long? = null
    private var lastAnnouncedStep = NONE
    private var lastImminentStep = NONE
    private var finalArrivalShown = false
    private var voiceNotified = false
    private var loadJob: Job? = null
    private var trackingJob: Job? = null
    private var gpsWatchJob: Job? = null
    private var rerouteJob: Job? = null

    init {
        preferences.preferences
            .onEach { prefs ->
                mutableState.update { it.copy(voiceEnabled = prefs.voiceGuidance) }
                if (!prefs.voiceGuidance) voice.stop()
            }
            .launchIn(viewModelScope)
        voice.status
            .onEach { status ->
                if (status == VoiceStatus.Unavailable && !voiceNotified) {
                    voiceNotified = true
                    eventChannel.send(GuidanceEvent.VoiceUnavailable)
                }
            }
            .launchIn(viewModelScope)
        load()
    }

    fun load() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch { prepare() }
    }

    fun onPermissionResult() {
        locationRepository.refreshPermissionState()
    }

    fun toggleVoice() {
        val enabled = !mutableState.value.voiceEnabled
        viewModelScope.launch { preferences.setVoiceGuidance(enabled) }
    }

    fun dismissArrival() {
        mutableState.update { it.copy(arrival = null) }
    }

    override fun onCleared() {
        voice.release()
        super.onCleared()
    }

    private suspend fun prepare() {
        mutableState.update { it.copy(phase = GuidancePhase.Loading, error = null) }
        val trip = trips.getTrip(tripId)
        if (trip == null) {
            mutableState.update { it.copy(phase = GuidancePhase.TripMissing) }
            return
        }
        stopsInOrder = trip.stops.sortedBy { it.order }
        mutableState.update { it.copy(trip = trip) }
        if (locationRepository.permissionState.value == LocationPermissionState.DENIED) {
            mutableState.update { it.copy(phase = GuidancePhase.PermissionDenied) }
            locationRepository.permissionState.first { it != LocationPermissionState.DENIED }
            mutableState.update { it.copy(phase = GuidancePhase.Loading) }
        }
        val route = routeStore.get(tripId)?.getOrNull(routeIndex) ?: when (val result = planRoutes(trip)) {
            is AppResult.Success -> result.value.firstOrNull()
            is AppResult.Failure -> {
                mutableState.update { it.copy(phase = GuidancePhase.Failed, error = result.error) }
                return
            }
        }
        if (route == null) {
            mutableState.update { it.copy(phase = GuidancePhase.Failed, error = AppError.NoRoute) }
            return
        }
        beginGuidance(route)
    }

    private fun beginGuidance(route: Route) {
        installRoute(route, legBaseIndex = 0)
        currentLeg = 0
        startedAt = clock.nowMillis()
        mutableState.update { it.copy(phase = GuidancePhase.Active, route = route, waitingForGps = false) }
        voice.prepare()
        trackingJob?.cancel()
        trackingJob = locationRepository.locationUpdates(LOCATION_INTERVAL_MILLIS)
            .catch { }
            .onEach(::onLocation)
            .launchIn(viewModelScope)
        gpsWatchJob?.cancel()
        gpsWatchJob = viewModelScope.launch {
            while (true) {
                delay(GPS_CHECK_MILLIS)
                val lastSeen = lastFixAt ?: startedAt
                val waiting = clock.nowMillis() - lastSeen > GPS_LOSS_MILLIS
                mutableState.update { if (it.waitingForGps == waiting) it else it.copy(waitingForGps = waiting) }
            }
        }
    }

    private fun installRoute(route: Route, legBaseIndex: Int) {
        engine = GuidanceEngine(route)
        legBase = legBaseIndex
        stepLegIndices = route.legs.flatMapIndexed { legIndex, leg -> leg.steps.map { legIndex } }
        lastAnnouncedStep = NONE
        lastImminentStep = NONE
    }

    private fun onLocation(location: UserLocation) {
        val activeEngine = engine ?: return
        val now = clock.nowMillis()
        lastFixAt = now
        val progress = activeEngine.update(location.point)
        mutableState.update {
            it.copy(
                progress = progress,
                userPoint = location.point,
                waitingForGps = false,
                etaMillis = now + (progress.remainingDurationSeconds * MILLIS_PER_SECOND).toLong(),
                needsConnectionToReroute = if (progress.isOffRoute) it.needsConnectionToReroute else false,
            )
        }
        if (finalArrivalShown) return
        if (progress.hasArrived) {
            finalArrivalShown = true
            mutableState.update { it.copy(arrival = ArrivalState(stopsInOrder.lastOrNull(), isFinal = true)) }
            speak(AnnouncementKind.Arrived, null, 0.0, null)
            return
        }
        trackLegs(progress)
        announce(progress)
        if (progress.isOffRoute) maybeReroute()
    }

    private fun trackLegs(progress: GuidanceProgress) {
        val legInRoute = stepLegIndices.getOrNull(progress.currentStepIndex) ?: return
        val global = legBase + legInRoute
        if (global > currentLeg) {
            currentLeg = global
            val reached = stopsInOrder.getOrNull(global)
            mutableState.update { it.copy(arrival = ArrivalState(reached, isFinal = false)) }
            speak(AnnouncementKind.ReachedStop, null, 0.0, reached)
        }
    }

    private fun announce(progress: GuidanceProgress) {
        val next = progress.nextStep ?: return
        val index = progress.currentStepIndex
        val distance = progress.distanceToNextManeuverMeters
        val imminent = distance <= IMMINENT_METERS
        if (index != lastAnnouncedStep) {
            lastAnnouncedStep = index
            if (imminent) {
                lastImminentStep = index
                speak(AnnouncementKind.Imminent, next, distance, null)
            } else {
                speak(AnnouncementKind.Upcoming, next, distance, null)
            }
        } else if (imminent && lastImminentStep != index) {
            lastImminentStep = index
            speak(AnnouncementKind.Imminent, next, distance, null)
        }
    }

    private fun speak(kind: AnnouncementKind, step: RouteStep?, distance: Double, stop: TripStop?) {
        if (!mutableState.value.voiceEnabled) return
        voice.speak(phraser.phrase(kind, step, distance, stop))
    }

    private fun maybeReroute() {
        if (rerouteJob?.isActive == true) return
        val now = clock.nowMillis()
        val previous = lastRerouteAt
        if (previous != null && now - previous < REROUTE_THROTTLE_MILLIS) return
        if (connectivity.state.value == ConnectivityState.Offline) {
            mutableState.update { it.copy(needsConnectionToReroute = true) }
            return
        }
        lastRerouteAt = now
        mutableState.update { it.copy(isRerouting = true, needsConnectionToReroute = false) }
        speak(AnnouncementKind.Rerouting, null, 0.0, null)
        rerouteJob = viewModelScope.launch { reroute() }
    }

    private suspend fun reroute() {
        val trip = mutableState.value.trip ?: return
        val remaining = stopsInOrder.drop(currentLeg + 1)
        if (remaining.isEmpty()) {
            mutableState.update { it.copy(isRerouting = false) }
            return
        }
        val rerouteTrip = trip.copy(
            stops = listOf(TripStop(id = 0, place = null, useCurrentLocation = true, order = 0)) +
                remaining.mapIndexed { index, stop -> stop.copy(order = index + 1) },
        )
        when (val result = planRoutes(rerouteTrip)) {
            is AppResult.Success -> {
                val newRoute = result.value.firstOrNull()
                if (newRoute == null) {
                    mutableState.update { it.copy(isRerouting = false) }
                } else {
                    installRoute(newRoute, legBaseIndex = currentLeg)
                    mutableState.update { it.copy(isRerouting = false, route = newRoute, progress = null) }
                }
            }
            is AppResult.Failure -> mutableState.update {
                it.copy(isRerouting = false, needsConnectionToReroute = result.error == AppError.NoInternet)
            }
        }
    }

    companion object {
        const val LOCATION_INTERVAL_MILLIS = 1_000L
        const val GPS_LOSS_MILLIS = 10_000L
        const val GPS_CHECK_MILLIS = 1_000L
        const val REROUTE_THROTTLE_MILLIS = 15_000L
        const val IMMINENT_METERS = 200.0
        private const val MILLIS_PER_SECOND = 1_000
        private const val NONE = -1
    }
}
