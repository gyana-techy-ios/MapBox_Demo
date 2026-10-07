package com.raahi.app.offline

import com.raahi.app.core.result.AppResult
import com.raahi.app.domain.model.DownloadProgress
import com.raahi.app.domain.model.OfflineRegion
import com.raahi.app.domain.model.OfflineRegionStatus
import com.raahi.app.domain.repository.OfflineMapRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DownloadCoordinator(
    private val repository: OfflineMapRepository,
    private val scope: CoroutineScope,
) {
    private val jobs = ConcurrentHashMap<String, Job>()
    private val mutableProgress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())

    val progress: StateFlow<Map<String, DownloadProgress>> = mutableProgress.asStateFlow()

    fun isDownloading(regionId: String): Boolean = jobs[regionId]?.isActive == true

    fun start(region: OfflineRegion, allowMetered: Boolean = false) {
        launchCollection(region.id) { repository.download(region, allowMetered) }
    }

    fun resume(regionId: String, allowMetered: Boolean = false) {
        launchCollection(regionId) { repository.resume(regionId, allowMetered) }
    }

    suspend fun cancel(regionId: String) {
        jobs.remove(regionId)?.cancel()
        repository.cancel(regionId)
        mutableProgress.update { it - regionId }
    }

    suspend fun delete(regionId: String): AppResult<Unit> {
        jobs.remove(regionId)?.cancel()
        mutableProgress.update { it - regionId }
        return repository.delete(regionId)
    }

    private fun launchCollection(regionId: String, source: () -> Flow<DownloadProgress>) {
        if (isDownloading(regionId)) return
        mutableProgress.update { it - regionId }
        val job = scope.launch {
            source().collect { update -> record(update) }
        }
        jobs[regionId] = job
        job.invokeOnCompletion { jobs.remove(regionId, job) }
    }

    private fun record(update: DownloadProgress) {
        mutableProgress.update { current ->
            when (update.status) {
                OfflineRegionStatus.Downloading, OfflineRegionStatus.Failed -> current + (update.regionId to update)
                else -> current - update.regionId
            }
        }
    }
}
