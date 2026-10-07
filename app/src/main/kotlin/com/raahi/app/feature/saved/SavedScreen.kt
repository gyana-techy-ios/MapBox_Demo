package com.raahi.app.feature.saved

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raahi.app.R
import com.raahi.app.domain.model.Place
import com.raahi.app.ui.components.EmptyState
import com.raahi.app.ui.components.RaahiTopBar
import com.raahi.app.ui.components.SecondaryButton
import com.raahi.app.ui.theme.LocalSpacing

@Composable
fun SavedRoute(
    onOpenPlace: (Place) -> Unit,
    onOpenContacts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.testTag("screen_saved"),
        topBar = {
            RaahiTopBar(
                title = stringResource(R.string.saved_title),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Box(modifier = Modifier.weight(1f)) {
                EmptyState(
                    icon = Icons.Outlined.BookmarkBorder,
                    title = stringResource(R.string.saved_empty_title),
                    body = stringResource(R.string.saved_empty_body),
                )
            }
            val spacing = LocalSpacing.current
            SecondaryButton(
                text = stringResource(R.string.saved_action_contacts),
                onClick = { onOpenContacts() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.xs),
            )
        }
    }
}
