package com.raahi.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface DirectionsApi {

    @GET("{profile}/{coordinates}")
    suspend fun getRoutes(
        @Path("profile") profile: String,
        @Path("coordinates", encoded = true) coordinates: String,
        @Query("alternatives") alternatives: Boolean,
        @Query("overview") overview: String = "full",
        @Query("geometries") geometries: String = "polyline6",
        @Query("steps") steps: Boolean = true,
        @Query("annotations") annotations: String,
        @Query("exclude") exclude: String?,
        @Query("language") language: String,
    ): Response<DirectionsResponseDto>
}

@Serializable
data class DirectionsResponseDto(
    val code: String = "",
    val message: String? = null,
    val routes: List<RouteDto> = emptyList(),
)

@Serializable
data class RouteDto(
    val geometry: String = "",
    val distance: Double = 0.0,
    val duration: Double = 0.0,
    val legs: List<LegDto> = emptyList(),
)

@Serializable
data class LegDto(
    val summary: String = "",
    val steps: List<StepDto> = emptyList(),
    val annotation: AnnotationDto? = null,
)

@Serializable
data class AnnotationDto(val congestion: List<String>? = null)

@Serializable
data class StepDto(
    val distance: Double = 0.0,
    val duration: Double = 0.0,
    val name: String = "",
    val maneuver: ManeuverDto = ManeuverDto(),
)

@Serializable
data class ManeuverDto(
    val instruction: String = "",
    val type: String = "",
    val modifier: String? = null,
    @SerialName("location") val location: List<Double> = emptyList(),
)
