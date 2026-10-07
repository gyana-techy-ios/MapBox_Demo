package com.raahi.app.core.config

enum class TokenStatus { Valid, Missing, SecretTokenRejected, Malformed }

class MapboxConfig(rawToken: String?) {

    private val token: String = rawToken.orEmpty().trim()

    val status: TokenStatus = parseStatus(token)

    val isMapAvailable: Boolean get() = status == TokenStatus.Valid

    val accessToken: String? get() = token.takeIf { isMapAvailable }

    companion object {
        const val PUBLIC_PREFIX = "pk."
        const val SECRET_PREFIX = "sk."

        fun parseStatus(rawToken: String?): TokenStatus {
            val token = rawToken.orEmpty().trim()
            return when {
                token.isEmpty() -> TokenStatus.Missing
                token.startsWith(SECRET_PREFIX) -> TokenStatus.SecretTokenRejected
                !token.startsWith(PUBLIC_PREFIX) || token.length <= PUBLIC_PREFIX.length -> TokenStatus.Malformed
                else -> TokenStatus.Valid
            }
        }
    }
}
