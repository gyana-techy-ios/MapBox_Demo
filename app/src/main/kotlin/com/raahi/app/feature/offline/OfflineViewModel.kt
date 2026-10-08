package com.raahi.app.feature.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raahi.app.core.config.MapboxConfig
import com.raahi.app.core.network.ConnectivityMonitor
import com.raahi.app.core.network.ConnectivityState
import com.raahi.app.core.result.AppError
import com.raahi.app.domain.model.DownloadProgress
import com.raahi.app.domain.model.OfflineRegion
import com.raahi.app.domain.model.OfflineRegionStatus
import com.raahi.app.domain.repository.OfflineMapRepository
import com.raahi.app.offline.DownloadCoordinator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RegionAction { Cancel, Resume, Update, Delete }

data class OfflineRegionItem(
    val region: OfflineRegion,
    val status: OfflineRegionStatus,
    val progress: Float,
    val error: AppError?,
    val actions: List<RegionAction>,
) {
    val isOutdated: Boolean get() = region.isOutdated && status == OfflineRegionStatus.Completed
}

data class OfflineUiState(
    val isLoading: Boolean = true,
    val items: List<OfflineRegionItem> = emptyList(),
    val totalBytes: Long = 0L,
    val isConfigured: Boolean = true,
    val connectivity: ConnectivityState = ConnectivityState.Online,
    val loadError: AppError? = null,
)

sealed interface OfflineEvent {
    data class ConfirmMetered(val regionId: String) : OfflineEvent
    data object Deleted : OfflineEvent
}

object OfflineRegionMapper {
    fun toItem(region: OfflineRegion, active: DownloadProgress?): OfflineRegionItem {
        val status = if (active?.status == OfflineRegionStatus.Downloading) OfflineRegionStatus.Downloading else region.status
        val progress = if (status == OfflineRegionStatus.Downloading) active?.progress ?: region.progress else region.progress
        val error = active?.takeIf { it.status == OfflineRegionStatus.Failed }?.error
        val actions = buildList {
            when {
                status == OfflineRegionStatus.Downloading -> add(RegionAction.Cancel)
                status == OfflineRegionStatus.Completed && region.isOutdated -> add(RegionAction.Update)
                status != OfflineRegionStatus.Completed -> add(RegionAction.Resume)
            }
            add(RegionAction.Delete)
        }
        val liveRegion = if (status == OfflineRegionStatus.Downloading && active != null) region.copy(sizeBytes = active.loadedBytes) else region
        return OfflineRegionItem(liveRegion, status, progress, error, actions)
    }
}

class OfflineViewModel(
    private val offlineMaps: OfflineMapRepository,
    private val coordinator: DownloadCoordinator,
    private val connectivity: ConnectivityMonitor,
    private val config: MapboxConfig,
    private val meteredGuard: MeteredDownloadGuard,
) : ViewModel() {

    private val mutableState = MutableStateFlow(OfflineUiState(isConfigured = config.isMapAvailable))
    val uiState: StateFlow<OfflineUiState> = mutableState.asStateFlow()

    private val eventChannel = Channel<OfflineEvent>(Channel.BUFFERED)
    val events: Flow<OfflineEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch { offlineMaps.reconcile() }
        combine(offlineMaps.regions, coordinator.progress, connectivity.state) { regions, progress, network ->
            val items = regions.sortedByDescending { it.downloadedAt ?: Long.MAX_VALUE }
                .map { OfflineRegionMapper.toItem(it, progress[it.id]) }
            OfflineUiState(
                isLoading = false,
                items = items,
                totalBytes = items.sumOf { it.region.sizeBytes },
                isConfigured = config.isMapAvailable,
                connectivity = network,
            )
        }
            .catch { error -> mutableState.update { it.copy(isLoading = false, loadError = AppError.Unknown(error.message)) } }
            .onEach { mutableState.value = it }
            .launchIn(viewModelScope)
    }

    fun resume(regionId: String, allowMetered: Boolean = false) {
        viewModelScope.launch {
            if (!allowMetered && meteredGuard.requiresConfirmation()) {
                eventChannel.send(OfflineEvent.ConfirmMetered(regionId))
            } else {
                coordinator.resume(regionId, allowMetered)
            }
        }
    }

    fun cancel(regionId: String) {
        viewModelScope.launch { coordinator.cancel(regionId) }
    }

    fun delete(regionId: String) {
        viewModelScope.launch {
            coordinator.delete(regionId)
            eventChannel.send(OfflineEvent.Deleted)
        }
    }
}
