package com.raahi.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface SearchBoxApi {

    @GET("suggest")
    suspend fun suggest(
        @Query("q") query: String,
        @Query("session_token") sessionToken: String,
        @Query("proximity") proximity: String?,
        @Query("language") language: String,
        @Query("limit") limit: Int = 8,
    ): Response<SuggestResponseDto>

    @GET("retrieve/{id}")
    suspend fun retrieve(
        @Path("id", encoded = true) id: String,
        @Query("session_token") sessionToken: String,
    ): Response<FeatureCollectionDto>

    @GET("category/{categoryId}")
    suspend fun category(
        @Path("categoryId") categoryId: String,
        @Query("proximity") proximity: String,
        @Query("language") language: String,
        @Query("limit") limit: Int,
    ): Response<FeatureCollectionDto>

    @GET("reverse")
    suspend fun reverse(
        @Query("longitude") longitude: Double,
        @Query("latitude") latitude: Double,
        @Query("language") language: String,
        @Query("limit") limit: Int = 1,
    ): Response<FeatureCollectionDto>
}

@Serializable
data class SuggestResponseDto(val suggestions: List<SuggestionDto> = emptyList())

@Serializable
data class SuggestionDto(
    @SerialName("mapbox_id") val mapboxId: String,
    val name: String = "",
    val address: String? = null,
    @SerialName("full_address") val fullAddress: String? = null,
    @SerialName("place_formatted") val placeFormatted: String? = null,
    @SerialName("poi_category_ids") val poiCategoryIds: List<String>? = null,
    val distance: Double? = null,
)

@Serializable
data class FeatureCollectionDto(val features: List<FeatureDto> = emptyList())

@Serializable
data class FeatureDto(
    val geometry: GeometryDto? = null,
    val properties: FeaturePropertiesDto = FeaturePropertiesDto(),
)

@Serializable
data class GeometryDto(val coordinates: List<Double> = emptyList())

@Serializable
data class CoordinatesDto(val latitude: Double? = null, val longitude: Double? = null)

@Serializable
data class FeaturePropertiesDto(
    @SerialName("mapbox_id") val mapboxId: String? = null,
    val name: String? = null,
    val address: String? = null,
    @SerialName("full_address") val fullAddress: String? = null,
    @SerialName("place_formatted") val placeFormatted: String? = null,
    @SerialName("poi_category_ids") val poiCategoryIds: List<String>? = null,
    val coordinates: CoordinatesDto? = null,
    val distance: Double? = null,
    val metadata: MetadataDto? = null,
)

@Serializable
data class MetadataDto(val phone: String? = null)
