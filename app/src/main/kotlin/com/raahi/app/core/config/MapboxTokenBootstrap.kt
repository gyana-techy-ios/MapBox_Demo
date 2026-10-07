package com.raahi.app.core.config

import com.mapbox.common.MapboxOptions

object MapboxTokenBootstrap {
    fun apply(config: MapboxConfig) {
        config.accessToken?.let { MapboxOptions.accessToken = it }
    }
}
