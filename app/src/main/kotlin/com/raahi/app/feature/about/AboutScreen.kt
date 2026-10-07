package com.raahi.app.feature.about

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.heightIn
import com.raahi.app.BuildConfig
import com.raahi.app.R
import com.raahi.app.ui.components.RaahiTopBar
import com.raahi.app.ui.theme.LocalSpacing
import com.raahi.app.ui.theme.Sizes

private const val MAPBOX_ABOUT_URL = "https://www.mapbox.com/about/maps/"
private const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"

@Composable
fun AboutRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    val noBrowser = stringResource(R.string.about_no_browser)
    val openLink: (String) -> Unit = { url ->
        val opened = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!opened) Toast.makeText(context, noBrowser, Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        modifier = modifier.testTag("screen_about"),
        topBar = { RaahiTopBar(title = stringResource(R.string.about_title), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("about_version"),
            )
            Text(stringResource(R.string.about_description), style = MaterialTheme.typography.bodyLarge)

            Section(R.string.about_section_data) {
                Text(stringResource(R.string.about_attribution), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("about_attribution"))
                LinkButton(stringResource(R.string.about_link_mapbox), "about_link_mapbox") { openLink(MAPBOX_ABOUT_URL) }
                LinkButton(stringResource(R.string.about_link_osm), "about_link_osm") { openLink(OSM_COPYRIGHT_URL) }
            }
            Section(R.string.about_section_privacy) {
                Text(stringResource(R.string.about_privacy_body), style = MaterialTheme.typography.bodyMedium)
            }
            Section(R.string.about_section_licenses) {
                Text(stringResource(R.string.about_licenses_body), style = MaterialTheme.typography.bodyMedium)
            }
            Section(R.string.about_section_limits) {
                Text(stringResource(R.string.about_limits_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Section(titleRes: Int, content: @Composable () -> Unit) {
    val spacing = LocalSpacing.current
    Column(modifier = Modifier.padding(top = spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun LinkButton(label: String, tag: String, onClick: () -> Unit) {
    val description = stringResource(R.string.about_link_description, label)
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = Sizes.minTouchTarget).semantics { contentDescription = description }.testTag(tag),
    ) {
        Text(label)
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.padding(start = LocalSpacing.current.xs))
    }
}
