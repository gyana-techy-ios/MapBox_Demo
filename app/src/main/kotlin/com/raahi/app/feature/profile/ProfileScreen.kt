package com.raahi.app.feature.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
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
fun ProfileRoute(
    onOpenSettings: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.testTag("screen_profile"),
        topBar = {
            RaahiTopBar(
                title = stringResource(R.string.profile_title),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Box(modifier = Modifier.weight(1f)) {
                EmptyState(
                    icon = Icons.Outlined.Person,
                    title = stringResource(R.string.profile_empty_title),
                    body = stringResource(R.string.profile_empty_body),
                )
            }
            val spacing = LocalSpacing.current
            SecondaryButton(
                text = stringResource(R.string.profile_action_settings),
                onClick = { onOpenSettings() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.xs),
            )
            SecondaryButton(
                text = stringResource(R.string.profile_action_language),
                onClick = { onOpenLanguage() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.xs),
            )
            SecondaryButton(
                text = stringResource(R.string.profile_action_contacts),
                onClick = { onOpenContacts() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.xs),
            )
            SecondaryButton(
                text = stringResource(R.string.profile_action_about),
                onClick = { onOpenAbout() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.xs),
            )
        }
    }
}
