package com.raahi.app.core.result

sealed interface AppError {
    data object NoInternet : AppError
    data object Timeout : AppError
    data object Unauthorized : AppError
    data object RateLimited : AppError
    data object NotFound : AppError
    data object NoRoute : AppError
    data class Server(val code: Int) : AppError
    data object Configuration : AppError
    data object LocationUnavailable : AppError
    data object PermissionDenied : AppError
    data object StorageFull : AppError
    data object WifiOnlyRestriction : AppError
    data class Unknown(val detail: String? = null) : AppError
}
