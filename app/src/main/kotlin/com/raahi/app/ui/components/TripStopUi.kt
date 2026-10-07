package com.raahi.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.raahi.app.R
import com.raahi.app.domain.model.Place
import com.raahi.app.domain.model.TripStop

@Composable
fun TripStop.displayName(): String = when {
    useCurrentLocation -> stringResource(R.string.trip_stop_my_location)
    else -> place.displayName()
}

@Composable
fun Place?.displayName(): String = this?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.trip_stop_unnamed)
