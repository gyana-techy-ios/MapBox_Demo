package com.raahi.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class Spacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp,
)

val LocalSpacing = compositionLocalOf { Spacing() }

val LocalIsDarkTheme = compositionLocalOf { false }

object Elevation {
    val none: Dp = 0.dp
    val low: Dp = 2.dp
    val medium: Dp = 6.dp
    val high: Dp = 12.dp
}

object Sizes {
    val minTouchTarget: Dp = 48.dp
    val iconSmall: Dp = 20.dp
    val icon: Dp = 24.dp
    val iconLarge: Dp = 48.dp
    val iconHero: Dp = 96.dp
    val searchBarHeight: Dp = 56.dp
    val fab: Dp = 56.dp
}

val RaahiShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
