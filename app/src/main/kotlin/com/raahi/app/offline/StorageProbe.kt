package com.raahi.app.offline

import android.content.Context
import android.os.StatFs

fun interface StorageProbe {
    fun availableBytes(): Long
}

class DeviceStorageProbe(context: Context) : StorageProbe {
    private val directory = context.applicationContext.filesDir
    override fun availableBytes(): Long = StatFs(directory.path).availableBytes
}
