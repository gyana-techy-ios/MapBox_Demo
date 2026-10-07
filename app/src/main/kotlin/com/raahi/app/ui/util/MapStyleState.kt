package com.raahi.app.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raahi.app.core.di.LocalAppContainer
import com.raahi.app.domain.model.MapStyleMode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun rememberMapStyle(): MapStyleMode {
    val container = LocalAppContainer.current
    val styleFlow = remember(container) {
        container.preferencesRepository.preferences.map { it.mapStyle }.distinctUntilChanged()
    }
    val style by styleFlow.collectAsStateWithLifecycle(initialValue = MapStyleMode.STANDARD)
    return style
}
