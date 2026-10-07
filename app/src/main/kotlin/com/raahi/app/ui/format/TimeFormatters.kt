package com.raahi.app.ui.format

import android.text.format.DateFormat
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun formatRelativeTime(timestampMillis: Long, nowMillis: Long): String =
    DateUtils.getRelativeTimeSpanString(timestampMillis, nowMillis, DateUtils.MINUTE_IN_MILLIS).toString()

@Composable
fun formatClockTime(timestampMillis: Long): String {
    val context = LocalContext.current
    return DateFormat.getTimeFormat(context).format(java.util.Date(timestampMillis))
}

@Composable
fun formatShortDate(timestampMillis: Long): String =
    DateUtils.formatDateTime(LocalContext.current, timestampMillis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)

@Composable
fun formatFileSize(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)
