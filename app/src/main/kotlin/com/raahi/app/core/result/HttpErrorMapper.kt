package com.raahi.app.core.result

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

object HttpErrorMapper {

    private val noRouteCodes = setOf("NoRoute", "NoSegment")

    fun fromHttp(statusCode: Int, apiCode: String? = null): AppError = when {
        apiCode in noRouteCodes -> AppError.NoRoute
        statusCode == 401 || statusCode == 403 -> AppError.Unauthorized
        statusCode == 404 -> AppError.NotFound
        statusCode == 408 -> AppError.Timeout
        statusCode == 429 -> AppError.RateLimited
        else -> AppError.Server(statusCode)
    }

    fun fromApiCode(apiCode: String?): AppError? = when (apiCode) {
        null, "Ok" -> null
        in noRouteCodes -> AppError.NoRoute
        "ProfileNotFound" -> AppError.NotFound
        else -> AppError.Unknown(apiCode)
    }

    fun fromThrowable(throwable: Throwable): AppError = when (throwable) {
        is CancellationException -> throw throwable
        is SocketTimeoutException -> AppError.Timeout
        is UnknownHostException -> AppError.NoInternet
        is SerializationException -> AppError.Unknown(throwable.message)
        is IOException -> if (throwable.message?.contains("timeout", ignoreCase = true) == true) {
            AppError.Timeout
        } else {
            AppError.NoInternet
        }
        else -> AppError.Unknown(throwable.message)
    }
}
