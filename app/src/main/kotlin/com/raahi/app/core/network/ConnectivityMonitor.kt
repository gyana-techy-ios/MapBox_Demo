package com.raahi.app.core.network

import kotlinx.coroutines.flow.StateFlow

enum class ConnectivityState { Online, Limited, Offline }

interface ConnectivityMonitor {
    val state: StateFlow<ConnectivityState>
    fun isActiveNetworkMetered(): Boolean
}
