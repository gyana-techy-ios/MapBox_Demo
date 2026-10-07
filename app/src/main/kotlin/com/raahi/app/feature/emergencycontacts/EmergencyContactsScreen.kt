package com.raahi.app.feature.emergencycontacts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contacts
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
fun EmergencyContactsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.testTag("screen_emergencycontacts"),
        topBar = {
            RaahiTopBar(
                title = stringResource(R.string.emergencycontacts_title),
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Box(modifier = Modifier.weight(1f)) {
                EmptyState(
                    icon = Icons.Outlined.Contacts,
                    title = stringResource(R.string.emergencycontacts_empty_title),
                    body = stringResource(R.string.emergencycontacts_empty_body),
                )
            }        }
    }
}
