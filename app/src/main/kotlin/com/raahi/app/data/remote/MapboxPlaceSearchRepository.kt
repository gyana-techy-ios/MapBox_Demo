package com.raahi.app.data.remote

import com.raahi.app.core.result.AppError
import com.raahi.app.core.result.AppResult
import com.raahi.app.core.result.map
import com.raahi.app.domain.model.GeoPoint
import com.raahi.app.domain.model.Place
import com.raahi.app.domain.model.PlaceCategory
import com.raahi.app.domain.model.PlaceSource
import com.raahi.app.domain.model.SearchSuggestion
import com.raahi.app.domain.repository.PlaceSearchRepository
import java.util.Locale

fun apiLanguageFor(locale: Locale): String = if (locale.language == "hi") "hi" else "en"

class MapboxPlaceSearchRepository(
    private val api: SearchBoxApi,
    private val isConfigured: () -> Boolean,
    private val language: () -> String = { apiLanguageFor(Locale.getDefault()) },
) : PlaceSearchRepository {

    override suspend fun suggest(
        query: String,
        proximity: GeoPoint?,
        sessionToken: String,
    ): AppResult<List<SearchSuggestion>> {
        if (!isConfigured()) return notConfigured()
        if (query.isBlank()) return AppResult.Success(emptyList())
        val bias = proximity ?: GeoPoint.INDIA_CENTER
        return apiCall { api.suggest(query.trim(), sessionToken, bias.toQueryValue(), language()) }
            .map { response -> response.suggestions.map { it.toDomain() } }
    }

    override suspend fun retrieve(suggestion: SearchSuggestion, sessionToken: String): AppResult<Place> {
        if (!isConfigured()) return notConfigured()
        return apiCall { api.retrieve(suggestion.id, sessionToken) }.firstPlace(PlaceSource.SEARCH, null)
    }

    override suspend fun categorySearch(category: PlaceCategory, near: GeoPoint, limit: Int): AppResult<List<Place>> {
        if (!isConfigured()) return notConfigured()
        return apiCall { api.category(category.canonicalId, near.toQueryValue(), language(), limit.coerceIn(1, MAX_CATEGORY_LIMIT)) }
            .map { response ->
                response.features.mapNotNull { it.toPlace(PlaceSource.CATEGORY, category, near) }
            }
    }

    override suspend fun reverseGeocode(point: GeoPoint): AppResult<Place> {
        if (!isConfigured()) return notConfigured()
        return apiCall { api.reverse(point.lng, point.lat, language()) }.firstPlace(PlaceSource.REVERSE_GEOCODE, point)
    }

    private fun AppResult<FeatureCollectionDto>.firstPlace(source: PlaceSource, origin: GeoPoint?): AppResult<Place> =
        when (this) {
            is AppResult.Failure -> this
            is AppResult.Success -> value.features.firstNotNullOfOrNull { it.toPlace(source, null, origin) }
                ?.let { AppResult.Success(it) }
                ?: AppResult.Failure(AppError.NotFound)
        }

    private fun <T> notConfigured(): AppResult<T> = AppResult.Failure(AppError.Configuration)

    private fun GeoPoint.toQueryValue(): String = "$lng,$lat"

    private fun SuggestionDto.toDomain(): SearchSuggestion = SearchSuggestion(
        id = mapboxId,
        name = name,
        address = fullAddress ?: placeFormatted ?: address,
        category = poiCategoryIds?.let { PlaceCategory.fromCanonicalIds(it) },
        distanceMeters = distance,
    )

    private fun FeatureDto.toPlace(source: PlaceSource, requested: PlaceCategory?, origin: GeoPoint?): Place? {
        val lng = geometry?.coordinates?.getOrNull(0) ?: properties.coordinates?.longitude ?: return null
        val lat = geometry?.coordinates?.getOrNull(1) ?: properties.coordinates?.latitude ?: return null
        val point = GeoPoint(lat, lng)
        if (SearchResultSanitizer.isPlaceholder(point, origin)) return null
        val name = properties.name?.takeIf { it.isNotBlank() } ?: properties.fullAddress ?: properties.placeFormatted ?: return null
        return Place(
            id = properties.mapboxId ?: "${lat},${lng}",
            name = name,
            address = properties.fullAddress ?: properties.placeFormatted ?: properties.address,
            point = point,
            category = properties.poiCategoryIds?.let { PlaceCategory.fromCanonicalIds(it) } ?: requested,
            distanceMeters = properties.distance ?: origin?.distanceTo(point),
            source = source,
            phone = properties.metadata?.phone?.takeIf { it.isNotBlank() },
        )
    }

    private companion object {
        const val MAX_CATEGORY_LIMIT = 25
    }
}
