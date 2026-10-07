package com.raahi.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.SignalWifiStatusbarConnectedNoInternet4
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.raahi.app.R
import com.raahi.app.core.network.ConnectivityState
import com.raahi.app.ui.theme.LocalRaahiColors
import com.raahi.app.ui.theme.LocalSpacing

const val CONNECTIVITY_BANNER_TAG = "connectivity_banner"

@Composable
fun ConnectivityBanner(state: ConnectivityState, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = state != ConnectivityState.Online,
        modifier = modifier,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        val spacing = LocalSpacing.current
        val colors = LocalRaahiColors.current
        val isOffline = state == ConnectivityState.Offline
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(CONNECTIVITY_BANNER_TAG)
                .semantics { liveRegion = LiveRegionMode.Polite },
            color = colors.offlineContainer,
            contentColor = colors.onOfflineContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isOffline) Icons.Outlined.CloudOff else Icons.Outlined.SignalWifiStatusbarConnectedNoInternet4,
                    contentDescription = null,
                )
                Spacer(Modifier.width(spacing.md))
                Text(
                    text = stringResource(if (isOffline) R.string.banner_offline else R.string.banner_limited),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
