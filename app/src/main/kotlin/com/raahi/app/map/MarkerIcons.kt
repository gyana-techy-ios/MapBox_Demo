package com.raahi.app.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.DrawableCompat
import com.raahi.app.R
import com.raahi.app.ui.theme.RaahiColors

internal object MarkerIcons {

    private const val NORMAL_DP = 36
    private const val SELECTED_DP = 48

    fun render(context: Context, tint: Color, selected: Boolean): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = ((if (selected) SELECTED_DP else NORMAL_DP) * density).toInt()
        val drawable = checkNotNull(AppCompatResources.getDrawable(context, R.drawable.ic_map_pin)).mutate()
        val wrapped = DrawableCompat.wrap(drawable)
        DrawableCompat.setTint(wrapped, tint.toArgb())
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        wrapped.setBounds(0, 0, sizePx, sizePx)
        wrapped.draw(canvas)
        return bitmap
    }
}

internal fun MapMarkerKind.tint(colors: RaahiColors, primary: Color, secondary: Color): Color = when (this) {
    MapMarkerKind.Place -> primary
    MapMarkerKind.Parking -> colors.categoryParking
    MapMarkerKind.Stop -> secondary
    MapMarkerKind.Destination -> colors.saffron
    MapMarkerKind.Emergency -> colors.emergency
    is MapMarkerKind.Category -> colors.categoryColor(category)
}
