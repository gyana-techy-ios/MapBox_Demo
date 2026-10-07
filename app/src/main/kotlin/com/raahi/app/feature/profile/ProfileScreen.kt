package com.raahi.app.feature.profile

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ContactPhone
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raahi.app.R
import com.raahi.app.core.di.appViewModel
import com.raahi.app.ui.components.RaahiTopBar
import com.raahi.app.ui.components.SectionHeader
import com.raahi.app.ui.theme.LocalSpacing
import com.raahi.app.ui.theme.RaahiShapes
import com.raahi.app.ui.theme.Sizes

@Composable
fun ProfileRoute(
    onOpenSettings: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenOffline: () -> Unit = {},
) {
    val viewModel = appViewModel {
        ProfileViewModel(savedPlaces = it.savedPlaceRepository, trips = it.tripRepository, offlineMaps = it.offlineMapRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    val languageLabel = stringResource(currentLanguageLabelRes())

    Scaffold(
        modifier = modifier.testTag("screen_profile"),
        topBar = { RaahiTopBar(title = stringResource(R.string.profile_title)) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(bottom = spacing.lg),
        ) {
            IdentityHeader()
            StatsSection(state)
            Column(modifier = Modifier.padding(top = spacing.sm)) {
                ProfileRow(Icons.Outlined.Settings, stringResource(R.string.profile_action_settings), stringResource(R.string.profile_settings_subtitle), onOpenSettings, "profile_settings")
                ProfileRow(Icons.Outlined.Language, stringResource(R.string.profile_action_language), languageLabel, onOpenLanguage, "profile_language")
                ProfileRow(Icons.Outlined.ContactPhone, stringResource(R.string.profile_action_contacts), stringResource(R.string.profile_contacts_subtitle), onOpenContacts, "profile_contacts")
                ProfileRow(Icons.Outlined.CloudDownload, stringResource(R.string.profile_action_offline), stringResource(R.string.profile_offline_subtitle), onOpenOffline, "profile_offline")
                ProfileRow(Icons.Outlined.Info, stringResource(R.string.profile_action_about), stringResource(R.string.profile_about_subtitle), onOpenAbout, "profile_about")
            }
        }
    }
}

private fun currentLanguageLabelRes(): Int = when (AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(',')) {
    "en" -> R.string.language_english
    "hi" -> R.string.language_hindi
    "or" -> R.string.language_odia
    else -> R.string.language_system
}

@Composable
private fun IdentityHeader() {
    val spacing = LocalSpacing.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.sm).testTag("profile_header"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Route, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.width(Sizes.iconLarge))
                Spacer(Modifier.width(spacing.md))
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(spacing.sm))
                Text(
                    text = stringResource(R.string.profile_privacy_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun StatsSection(state: ProfileUiState) {
    val spacing = LocalSpacing.current
    SectionHeader(title = stringResource(R.string.profile_stats_title))
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        StatCard(
            count = state.savedPlaceCount,
            label = stringResource(R.string.profile_stat_places),
            description = pluralStringResource(R.plurals.profile_stat_places_description, state.savedPlaceCount, state.savedPlaceCount),
            tag = "profile_stat_places",
            modifier = Modifier.weight(1f),
        )
        StatCard(
            count = state.tripCount,
            label = stringResource(R.string.profile_stat_trips),
            description = pluralStringResource(R.plurals.profile_stat_trips_description, state.tripCount, state.tripCount),
            tag = "profile_stat_trips",
            modifier = Modifier.weight(1f),
        )
        StatCard(
            count = state.offlineRegionCount,
            label = stringResource(R.string.profile_stat_regions),
            description = pluralStringResource(R.plurals.profile_stat_regions_description, state.offlineRegionCount, state.offlineRegionCount),
            tag = "profile_stat_regions",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(count: Int, label: String, description: String, tag: String, modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    Surface(
        shape = RaahiShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description }.testTag(tag),
    ) {
        Column(modifier = Modifier.padding(spacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$count", style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProfileRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, tag: String) {
    val spacing = LocalSpacing.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = spacing.lg, vertical = spacing.sm)
                .semantics(mergeDescendants = true) {}
                .testTag(tag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(spacing.lg))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()
    }
}
