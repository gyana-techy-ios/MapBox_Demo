package com.raahi.app

import com.raahi.app.core.di.AppContainer
import com.raahi.app.fakes.FakeAppContainer

class TestRaahiApplication : RaahiApplication() {

    override fun createContainer(): AppContainer = FakeAppContainer()

    override fun onContainerReady(container: AppContainer) = Unit
}
