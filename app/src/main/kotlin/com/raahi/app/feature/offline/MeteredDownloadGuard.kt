package com.raahi.app.feature.offline

import com.raahi.app.core.network.ConnectivityMonitor
import com.raahi.app.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.first

class MeteredDownloadGuard(
    private val preferences: PreferencesRepository,
    private val connectivity: ConnectivityMonitor,
) {
    suspend fun requiresConfirmation(): Boolean =
        preferences.preferences.first().wifiOnlyDownloads && connectivity.isActiveNetworkMetered()
}
