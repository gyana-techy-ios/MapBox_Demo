package com.raahi.app

import android.app.Application
import com.raahi.app.core.config.MapboxTokenBootstrap
import com.raahi.app.core.di.AppContainer
import com.raahi.app.core.di.DefaultAppContainer

open class RaahiApplication : Application() {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
        onContainerReady(container)
    }

    protected open fun createContainer(): AppContainer = DefaultAppContainer(this)

    protected open fun onContainerReady(container: AppContainer) {
        MapboxTokenBootstrap.apply(container.mapboxConfig)
    }
}
