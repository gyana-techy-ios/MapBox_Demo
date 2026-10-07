package com.raahi.app.feature.guidance

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raahi.app.R
import com.raahi.app.ui.components.EmptyState
import com.raahi.app.ui.components.RaahiTopBar
import com.raahi.app.ui.theme.LocalSpacing

@Composable
fun GuidanceRoute(
    tripId: Long,
    routeIndex: Int,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.testTag("screen_guidance"),
        topBar = {
            RaahiTopBar(
                title = stringResource(R.string.guidance_title),
                onBack = onExit,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Box(modifier = Modifier.weight(1f)) {
                EmptyState(
                    icon = Icons.Outlined.Navigation,
                    title = stringResource(R.string.guidance_empty_title),
                    body = stringResource(R.string.guidance_empty_body),
                )
            }        }
    }
}
