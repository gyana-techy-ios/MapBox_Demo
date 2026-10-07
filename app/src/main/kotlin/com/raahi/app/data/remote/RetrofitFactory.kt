package com.raahi.app.data.remote

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object RetrofitFactory {

    const val SEARCH_BOX_BASE_URL = "https://api.mapbox.com/search/searchbox/v1/"
    const val DIRECTIONS_BASE_URL = "https://api.mapbox.com/directions/v5/mapbox/"

    fun <T> create(baseUrl: String, client: OkHttpClient, service: Class<T>): T =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(RaahiJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(service)
}
