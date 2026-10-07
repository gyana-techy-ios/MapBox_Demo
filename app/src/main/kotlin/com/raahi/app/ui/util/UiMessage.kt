package com.raahi.app.ui.util

import android.content.Context
import androidx.annotation.StringRes

data class UiMessage(@StringRes val resId: Int, val args: List<Any> = emptyList()) {
    fun resolve(context: Context): String = context.getString(resId, *args.toTypedArray())
}
