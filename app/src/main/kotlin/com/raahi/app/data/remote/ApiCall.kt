package com.raahi.app.data.remote

import com.raahi.app.core.result.AppError
import com.raahi.app.core.result.AppResult
import com.raahi.app.core.result.HttpErrorMapper
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.Response

val RaahiJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
}

@Serializable
internal data class ApiErrorBody(
    val code: String? = null,
    val message: String? = null,
    @SerialName("error") val error: String? = null,
)

internal suspend fun <T> apiCall(block: suspend () -> Response<T>): AppResult<T> = try {
    val response = block()
    if (response.isSuccessful) {
        val body = response.body()
        if (body != null) AppResult.Success(body) else AppResult.Failure(AppError.Unknown("empty body"))
    } else {
        val apiCode = parseApiCode(response.errorBody()?.string())
        AppResult.Failure(HttpErrorMapper.fromHttp(response.code(), apiCode))
    }
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (throwable: Throwable) {
    AppResult.Failure(HttpErrorMapper.fromThrowable(throwable))
}

private fun parseApiCode(body: String?): String? {
    if (body.isNullOrBlank()) return null
    return runCatching { RaahiJson.decodeFromString(ApiErrorBody.serializer(), body).code }.getOrNull()
}
