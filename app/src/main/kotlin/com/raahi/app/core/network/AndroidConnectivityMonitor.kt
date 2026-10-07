package com.raahi.app.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidConnectivityMonitor(context: Context) : ConnectivityMonitor {

    private val connectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private val mutableState = MutableStateFlow(readCurrentState())
    override val state: StateFlow<ConnectivityState> = mutableState.asStateFlow()

    init {
        connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                mutableState.value = networkCapabilities.toState()
            }

            override fun onLost(network: Network) {
                mutableState.value = ConnectivityState.Offline
            }
        })
    }

    override fun isActiveNetworkMetered(): Boolean = connectivityManager.isActiveNetworkMetered

    private fun readCurrentState(): ConnectivityState {
        val network = connectivityManager.activeNetwork ?: return ConnectivityState.Offline
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return ConnectivityState.Offline
        return capabilities.toState()
    }

    private fun NetworkCapabilities.toState(): ConnectivityState = when {
        !hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> ConnectivityState.Offline
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> ConnectivityState.Online
        else -> ConnectivityState.Limited
    }
}
