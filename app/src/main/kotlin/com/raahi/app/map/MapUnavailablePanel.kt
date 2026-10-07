package com.raahi.app.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MapsHomeWork
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.raahi.app.R
import com.raahi.app.core.config.TokenStatus
import com.raahi.app.ui.theme.LocalSpacing
import com.raahi.app.ui.theme.Sizes

const val MAP_UNAVAILABLE_TAG = "map_unavailable_panel"

@Composable
fun MapUnavailablePanel(status: TokenStatus, modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    val message = when (status) {
        TokenStatus.SecretTokenRejected -> R.string.map_unavailable_secret_token
        TokenStatus.Malformed -> R.string.map_unavailable_malformed_token
        else -> R.string.map_unavailable_missing_token
    }
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(MAP_UNAVAILABLE_TAG)
            .semantics(mergeDescendants = true) {},
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.MapsHomeWork,
                contentDescription = null,
                modifier = Modifier.size(Sizes.iconLarge),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(spacing.lg))
            Text(
                text = stringResource(R.string.map_unavailable_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(spacing.sm))
            Text(
                text = stringResource(message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
