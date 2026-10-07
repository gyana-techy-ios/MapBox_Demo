package com.raahi.app.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.raahi.app.R
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val METERS_PER_KM = 1000.0
private const val SHORT_RANGE_METERS = 1000.0

@Composable
fun formatDistance(meters: Double): String =
    if (meters < SHORT_RANGE_METERS) {
        stringResource(R.string.distance_meters, meters.toInt().coerceAtLeast(0))
    } else {
        stringResource(R.string.distance_kilometers, String.format(Locale.getDefault(), "%.1f", meters / METERS_PER_KM))
    }

@Composable
fun formatDuration(seconds: Double): String {
    val totalMinutes = TimeUnit.SECONDS.toMinutes(seconds.toLong()).coerceAtLeast(1)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours == 0L) {
        stringResource(R.string.duration_minutes, minutes.toInt())
    } else {
        stringResource(R.string.duration_hours_minutes, hours.toInt(), minutes.toInt())
    }
}

@Composable
fun formatCachedAge(cachedAtMillis: Long, nowMillis: Long): String {
    val ageMinutes = TimeUnit.MILLISECONDS.toMinutes((nowMillis - cachedAtMillis).coerceAtLeast(0)).toInt()
    val ageHours = TimeUnit.MILLISECONDS.toHours((nowMillis - cachedAtMillis).coerceAtLeast(0)).toInt()
    return when {
        ageMinutes < 60 -> pluralStringResource(R.plurals.cached_minutes_ago, ageMinutes.coerceAtLeast(1), ageMinutes.coerceAtLeast(1))
        ageHours < 48 -> pluralStringResource(R.plurals.cached_hours_ago, ageHours, ageHours)
        else -> stringResource(R.string.cached_days_ago, ageHours / 24)
    }
}
