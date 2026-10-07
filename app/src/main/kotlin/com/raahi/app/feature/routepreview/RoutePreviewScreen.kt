package com.raahi.app.feature.routepreview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raahi.app.R
import com.raahi.app.ui.components.EmptyState
import com.raahi.app.ui.components.RaahiTopBar
import com.raahi.app.ui.components.SecondaryButton
import com.raahi.app.ui.theme.LocalSpacing

@Composable
fun RoutePreviewRoute(
    tripId: Long,
    onBack: () -> Unit,
    onStartGuidance: (Long, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.testTag("screen_routepreview"),
        topBar = {
            RaahiTopBar(
                title = stringResource(R.string.routepreview_title),
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Box(modifier = Modifier.weight(1f)) {
                EmptyState(
                    icon = Icons.Outlined.Directions,
                    title = stringResource(R.string.routepreview_empty_title),
                    body = stringResource(R.string.routepreview_empty_body),
                )
            }
            val spacing = LocalSpacing.current
            SecondaryButton(
                text = stringResource(R.string.routepreview_action_start),
                onClick = { onStartGuidance(tripId, 0) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.xs),
            )
        }
    }
}
