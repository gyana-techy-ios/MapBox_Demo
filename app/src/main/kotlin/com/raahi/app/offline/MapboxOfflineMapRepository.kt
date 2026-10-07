package com.raahi.app.offline

import android.content.Context
import android.os.StatFs
import com.mapbox.bindgen.Value
import com.mapbox.common.Cancelable
import com.mapbox.common.NetworkRestriction
import com.mapbox.common.TileRegion
import com.mapbox.common.TileRegionErrorType
import com.mapbox.common.TileRegionLoadOptions
import com.mapbox.common.TileStore
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.GlyphsRasterizationMode
import com.mapbox.maps.OfflineManager
import com.mapbox.maps.Style
import com.mapbox.maps.StylePackLoadOptions
import com.mapbox.maps.TilesetDescriptorOptions
import com.raahi.app.core.config.MapboxConfig
import com.raahi.app.core.network.ConnectivityMonitor
import com.raahi.app.core.network.ConnectivityState
import com.raahi.app.core.result.AppError
import com.raahi.app.core.result.AppResult
import com.raahi.app.core.time.Clock
import com.raahi.app.data.local.OfflineRegionDao
import com.raahi.app.data.local.toDomain
import com.raahi.app.data.local.toEntity
import com.raahi.app.domain.model.DownloadProgress
import com.raahi.app.domain.model.GeoBounds
import com.raahi.app.domain.model.OfflineEstimate
import com.raahi.app.domain.model.OfflineRegion
import com.raahi.app.domain.model.OfflineRegionStatus
import com.raahi.app.domain.repository.OfflineMapRepository
import com.raahi.app.domain.repository.PreferencesRepository
import com.raahi.app.domain.usecase.TileEstimator
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class MapboxOfflineMapRepository(
    context: Context,
    private val config: MapboxConfig,
    private val regionDao: OfflineRegionDao,
    private val connectivity: ConnectivityMonitor,
    private val preferences: PreferencesRepository,
    private val clock: Clock,
    private val scope: CoroutineScope,
) : OfflineMapRepository {

    private val appContext = context.applicationContext
    private val storageDir: File = appContext.filesDir
    private val tileStore: TileStore by lazy { TileStore.create() }
    private val offlineManager: OfflineManager by lazy { OfflineManager() }
    private val activeLoads = ConcurrentHashMap<String, Cancelable>()

    override val regions: Flow<List<OfflineRegion>> =
        regionDao.observeAll().map { list -> list.map { it.toDomain(clock.nowMillis()) } }

    override fun estimate(bounds: GeoBounds, minZoom: Int, maxZoom: Int): OfflineEstimate =
        TileEstimator.estimate(bounds, minZoom, maxZoom)

    override suspend fun reconcile() {
        if (!config.isMapAvailable) return
        val stored = regionDao.getAll()
        if (stored.isEmpty()) return
        val installedIds = installedRegionIds() ?: return
        stored.forEach { entity ->
            val status = entity.status
            val isActive = activeLoads.containsKey(entity.id)
            when {
                status == OfflineRegionStatus.Completed.name && entity.id !in installedIds ->
                    regionDao.updateProgress(entity.id, OfflineRegionStatus.Failed.name, 0f, 0L, null)
                status == OfflineRegionStatus.Downloading.name && !isActive ->
                    regionDao.updateProgress(
                        entity.id,
                        OfflineRegionStatus.Partial.name,
                        entity.progress,
                        entity.sizeBytes,
                        entity.downloadedAt,
                    )
            }
        }
    }

    override fun download(region: OfflineRegion): Flow<DownloadProgress> = callbackFlow {
        val failure = preflightError(region)
        if (failure != null) {
            regionDao.upsert(region.copy(status = OfflineRegionStatus.Failed).toEntity())
            trySend(DownloadProgress(region.id, OfflineRegionStatus.Failed, 0f, 0L, failure))
            close()
            return@callbackFlow
        }
        regionDao.upsert(region.copy(status = OfflineRegionStatus.Downloading, progress = 0f).toEntity())
        trySend(DownloadProgress(region.id, OfflineRegionStatus.Downloading, 0f, 0L))
        startLoad(region) { progress ->
            trySend(progress)
            if (progress.status != OfflineRegionStatus.Downloading) close()
        }
        awaitClose { }
    }

    override fun resume(regionId: String): Flow<DownloadProgress> = callbackFlow {
        val entity = regionDao.get(regionId)
        if (entity == null) {
            trySend(DownloadProgress(regionId, OfflineRegionStatus.Failed, 0f, 0L, AppError.NotFound))
            close()
            return@callbackFlow
        }
        val region = entity.toDomain(clock.nowMillis())
        val failure = preflightError(region)
        if (failure != null) {
            trySend(DownloadProgress(regionId, OfflineRegionStatus.Failed, region.progress, region.sizeBytes, failure))
            close()
            return@callbackFlow
        }
        regionDao.updateProgress(regionId, OfflineRegionStatus.Downloading.name, region.progress, region.sizeBytes, null)
        startLoad(region) { progress ->
            trySend(progress)
            if (progress.status != OfflineRegionStatus.Downloading) close()
        }
        awaitClose { }
    }

    override suspend fun cancel(regionId: String) {
        activeLoads.remove(regionId)?.cancel()
        val entity = regionDao.get(regionId) ?: return
        val status = if (entity.progress > 0f) OfflineRegionStatus.Partial else OfflineRegionStatus.Cancelled
        regionDao.updateProgress(regionId, status.name, entity.progress, entity.sizeBytes, entity.downloadedAt)
    }

    override suspend fun delete(regionId: String): AppResult<Unit> {
        activeLoads.remove(regionId)?.cancel()
        if (config.isMapAvailable) tileStore.removeTileRegion(regionId)
        regionDao.delete(regionId)
        return AppResult.Success(Unit)
    }

    private suspend fun preflightError(region: OfflineRegion): AppError? {
        if (!config.isMapAvailable) return AppError.Configuration
        if (connectivity.state.value == ConnectivityState.Offline) return AppError.NoInternet
        if (preferences.preferences.first().wifiOnlyDownloads && connectivity.isActiveNetworkMetered()) {
            return AppError.WifiOnlyRestriction
        }
        val needed = estimate(region.bounds, region.minZoom, region.maxZoom).approximateBytes
        val available = StatFs(storageDir.path).availableBytes
        if (available < needed * STORAGE_SAFETY_FACTOR) return AppError.StorageFull
        return null
    }

    private fun startLoad(region: OfflineRegion, emit: (DownloadProgress) -> Unit) {
        val stylePackCancelable = offlineManager.loadStylePack(
            Style.STANDARD,
            StylePackLoadOptions.Builder()
                .glyphsRasterizationMode(GlyphsRasterizationMode.IDEOGRAPHS_RASTERIZED_LOCALLY)
                .acceptExpired(false)
                .build(),
            { },
        ) { result ->
            if (result.isError) {
                finishFailed(region, AppError.Unknown(result.error?.message), emit)
            } else {
                loadTiles(region, emit)
            }
        }
        activeLoads[region.id] = stylePackCancelable
    }

    private fun loadTiles(region: OfflineRegion, emit: (DownloadProgress) -> Unit) {
        val descriptor = offlineManager.createTilesetDescriptor(
            TilesetDescriptorOptions.Builder()
                .styleURI(Style.STANDARD)
                .minZoom(region.minZoom.toByte())
                .maxZoom(region.maxZoom.toByte())
                .pixelRatio(appContext.resources.displayMetrics.density)
                .build(),
        )
        val options = TileRegionLoadOptions.Builder()
            .geometry(region.bounds.toPolygon())
            .descriptors(listOf(descriptor))
            .metadata(Value.valueOf(region.name))
            .acceptExpired(false)
            .networkRestriction(NetworkRestriction.NONE)
            .build()

        var lastReportedPercent = -1
        val cancelable = tileStore.loadTileRegion(
            region.id,
            options,
            { progress ->
                val required = progress.requiredResourceCount.coerceAtLeast(1L)
                val fraction = (progress.completedResourceCount.toFloat() / required).coerceIn(0f, 1f)
                val percent = (fraction * 100).toInt()
                if (percent != lastReportedPercent) {
                    lastReportedPercent = percent
                    scope.launch {
                        regionDao.updateProgress(
                            region.id,
                            OfflineRegionStatus.Downloading.name,
                            fraction,
                            progress.loadedResourceSize,
                            null,
                        )
                    }
                    emit(DownloadProgress(region.id, OfflineRegionStatus.Downloading, fraction, progress.loadedResourceSize))
                }
            },
        ) { result ->
            activeLoads.remove(region.id)
            val tileRegion: TileRegion? = result.value
            if (tileRegion != null) {
                val now = clock.nowMillis()
                scope.launch {
                    regionDao.updateProgress(
                        region.id,
                        OfflineRegionStatus.Completed.name,
                        1f,
                        tileRegion.completedResourceSize,
                        now,
                    )
                }
                emit(DownloadProgress(region.id, OfflineRegionStatus.Completed, 1f, tileRegion.completedResourceSize))
            } else if (result.error?.type == TileRegionErrorType.CANCELED) {
                emit(DownloadProgress(region.id, OfflineRegionStatus.Cancelled, region.progress, region.sizeBytes))
            } else {
                finishFailed(region, AppError.Unknown(result.error?.message), emit)
            }
        }
        activeLoads[region.id] = cancelable
    }

    private fun finishFailed(region: OfflineRegion, error: AppError, emit: (DownloadProgress) -> Unit) {
        activeLoads.remove(region.id)
        scope.launch {
            regionDao.updateProgress(region.id, OfflineRegionStatus.Failed.name, region.progress, region.sizeBytes, null)
        }
        emit(DownloadProgress(region.id, OfflineRegionStatus.Failed, region.progress, region.sizeBytes, error))
    }

    private suspend fun installedRegionIds(): Set<String>? = suspendCancellableCoroutine { continuation ->
        tileStore.getAllTileRegions { result ->
            val regions = result.value
            if (continuation.isActive) continuation.resume(regions?.map { it.id }?.toSet())
        }
    }

    private fun GeoBounds.toPolygon(): Polygon = Polygon.fromLngLats(
        listOf(
            listOf(
                Point.fromLngLat(west, south),
                Point.fromLngLat(east, south),
                Point.fromLngLat(east, north),
                Point.fromLngLat(west, north),
                Point.fromLngLat(west, south),
            ),
        ),
    )

    private companion object {
        const val STORAGE_SAFETY_FACTOR = 1.25
    }
}
