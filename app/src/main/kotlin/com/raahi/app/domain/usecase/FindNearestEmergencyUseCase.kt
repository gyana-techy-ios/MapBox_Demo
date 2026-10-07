package com.raahi.app.domain.usecase

import com.raahi.app.core.result.AppResult
import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.Place
import com.raahi.app.domain.model.PlaceCategory
import com.raahi.app.domain.repository.NearbyCacheRepository
import com.raahi.app.domain.repository.PlaceSearchRepository

data class NearbyResult(
    val places: List<Place>,
    val cachedAtMillis: Long?,
) {
    val isFromCache: Boolean get() = cachedAtMillis != null
}

class FindNearestEmergencyUseCase(
    private val placeSearchRepository: PlaceSearchRepository,
    private val cacheRepository: NearbyCacheRepository,
) {
    suspend operator fun invoke(
        category: PlaceCategory,
        near: GeoPoint,
        limit: Int = DEFAULT_LIMIT,
    ): AppResult<NearbyResult> {
        val online = placeSearchRepository.categorySearch(category, near, limit)
        if (online is AppResult.Success) {
            val sorted = online.value.sortedBy { it.distanceMeters ?: it.point.distanceTo(near) }
            if (sorted.isNotEmpty()) cacheRepository.put(cacheKey(category), sorted)
            return AppResult.Success(NearbyResult(sorted, cachedAtMillis = null))
        }
        val cached = cacheRepository.get(cacheKey(category))
        if (cached != null && cached.places.isNotEmpty()) {
            val sorted = cached.places.sortedBy { it.point.distanceTo(near) }
            return AppResult.Success(NearbyResult(sorted, cached.cachedAtMillis))
        }
        return online as AppResult.Failure
    }

    companion object {
        const val DEFAULT_LIMIT = 10
        fun cacheKey(category: PlaceCategory): String = "emergency_${category.name}"
    }
}
