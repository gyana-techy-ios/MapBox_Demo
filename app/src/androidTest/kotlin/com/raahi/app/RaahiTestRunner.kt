package com.raahi.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

class RaahiTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, TestRaahiApplication::class.java.name, context)
}
