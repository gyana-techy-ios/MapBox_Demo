package com.raahi.app.core.result

import androidx.annotation.StringRes
import com.raahi.app.R

@StringRes
fun AppError.toMessageRes(): Int = when (this) {
    AppError.NoInternet -> R.string.error_no_internet
    AppError.Timeout -> R.string.error_timeout
    AppError.Unauthorized -> R.string.error_unauthorized
    AppError.RateLimited -> R.string.error_rate_limited
    AppError.NotFound -> R.string.error_not_found
    AppError.NoRoute -> R.string.error_no_route
    is AppError.Server -> R.string.error_server
    AppError.Configuration -> R.string.error_configuration
    AppError.LocationUnavailable -> R.string.error_location_unavailable
    AppError.PermissionDenied -> R.string.error_permission_denied
    AppError.StorageFull -> R.string.error_storage_full
    AppError.WifiOnlyRestriction -> R.string.error_wifi_only
    is AppError.Unknown -> R.string.error_unknown
}

@StringRes
fun AppError.toSearchMessageRes(): Int =
    if (this == AppError.NoInternet) R.string.error_search_needs_internet else toMessageRes()
