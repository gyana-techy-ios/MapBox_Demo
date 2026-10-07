package com.raahi.app.data.remote

import android.util.Log
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor

class TokenInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenProvider() ?: return chain.proceed(request)
        val url = request.url.newBuilder().addQueryParameter(TOKEN_PARAM, token).build()
        return chain.proceed(request.newBuilder().url(url).build())
    }

    companion object {
        const val TOKEN_PARAM = "access_token"
    }
}

object TokenRedactor {
    private val tokenPattern = Regex("(access_token=)[^&\\s]+")

    fun redact(message: String): String = tokenPattern.replace(message, "$1REDACTED")
}

object HttpClientFactory {

    fun create(
        tokenProvider: () -> String?,
        enableLogging: Boolean,
        readTimeoutSeconds: Long = 15,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(TokenInterceptor(tokenProvider))
        if (enableLogging) {
            val logging = HttpLoggingInterceptor { message -> Log.d(LOG_TAG, TokenRedactor.redact(message)) }
            logging.level = HttpLoggingInterceptor.Level.BASIC
            builder.addNetworkInterceptor(logging)
        }
        return builder.build()
    }

    private const val LOG_TAG = "RaahiHttp"
}
