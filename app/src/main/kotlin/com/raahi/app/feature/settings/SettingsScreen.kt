package com.raahi.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raahi.app.R
import com.raahi.app.core.di.appViewModel
import com.raahi.app.domain.model.MapStyleMode
import com.raahi.app.domain.model.RouteOptions
import com.raahi.app.domain.model.ThemeMode
import com.raahi.app.ui.components.ConfirmDialog
import com.raahi.app.ui.components.RaahiTopBar
import com.raahi.app.ui.components.RouteProfileSelector
import com.raahi.app.ui.components.SectionHeader
import com.raahi.app.ui.components.SkeletonList
import com.raahi.app.ui.theme.LocalSpacing
import com.raahi.app.ui.theme.Sizes

private enum class ClearTarget { History, Cache }

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = appViewModel {
        SettingsViewModel(
            preferences = it.preferencesRepository,
            recentSearches = it.recentSearchRepository,
            nearbyCache = it.nearbyCacheRepository,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current
    var clearTarget by rememberSaveable { mutableStateOf<ClearTarget?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShowMessage -> snackbarHost.showSnackbar(context.getString(event.messageRes))
            }
        }
    }

    Scaffold(
        modifier = modifier.testTag("screen_settings"),
        topBar = { RaahiTopBar(title = stringResource(R.string.settings_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        if (state.isLoading) {
            SkeletonList(modifier = Modifier.padding(padding).padding(top = LocalSpacing.current.lg))
        } else {
            SettingsBody(state = state, viewModel = viewModel, onClear = { clearTarget = it }, modifier = Modifier.padding(padding))
        }
    }

    clearTarget?.let { target ->
        val isHistory = target == ClearTarget.History
        ConfirmDialog(
            title = stringResource(if (isHistory) R.string.settings_clear_history_title else R.string.settings_clear_cache_title),
            message = stringResource(if (isHistory) R.string.settings_clear_history_message else R.string.settings_clear_cache_message),
            confirmLabel = stringResource(R.string.settings_clear_confirm),
            onConfirm = {
                clearTarget = null
                if (isHistory) viewModel.clearSearchHistory() else viewModel.clearNearbyCache()
            },
            onDismiss = { clearTarget = null },
        )
    }
}

@Composable
private fun SettingsBody(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onClear: (ClearTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val prefs = state.preferences
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(bottom = spacing.xl),
    ) {
        SectionHeader(title = stringResource(R.string.settings_section_appearance))
        Column(modifier = Modifier.padding(horizontal = spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
            SegmentedChoice(
                options = listOf(
                    ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                    ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                    ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
                ),
                selected = prefs.themeMode,
                onSelect = viewModel::setTheme,
                tagPrefix = "settings_theme",
            )
            Text(stringResource(R.string.settings_map_style), style = MaterialTheme.typography.bodyLarge)
            SegmentedChoice(
                options = listOf(
                    MapStyleMode.STANDARD to stringResource(R.string.settings_map_standard),
                    MapStyleMode.SATELLITE to stringResource(R.string.settings_map_satellite),
                ),
                selected = prefs.mapStyle,
                onSelect = viewModel::setMapStyle,
                tagPrefix = "settings_map",
            )
        }

        SectionHeader(title = stringResource(R.string.settings_section_routing))
        Column(modifier = Modifier.padding(horizontal = spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(stringResource(R.string.settings_default_profile), style = MaterialTheme.typography.bodyLarge)
            RouteProfileSelector(selected = prefs.defaultProfile, onSelect = viewModel::setDefaultProfile)
            Text(stringResource(R.string.settings_avoid_title), style = MaterialTheme.typography.bodyLarge)
        }
        AvoidSwitches(options = prefs.defaultRouteOptions, onChange = viewModel::setDefaultOptions)

        SectionHeader(title = stringResource(R.string.settings_section_downloads))
        SwitchRow(
            title = stringResource(R.string.settings_wifi_only),
            hint = stringResource(R.string.settings_wifi_only_hint),
            checked = prefs.wifiOnlyDownloads,
            onChange = viewModel::setWifiOnly,
            tag = "settings_wifi_only",
        )

        SectionHeader(title = stringResource(R.string.settings_section_navigation))
        SwitchRow(
            title = stringResource(R.string.settings_voice),
            hint = stringResource(R.string.settings_voice_hint),
            checked = prefs.voiceGuidance,
            onChange = viewModel::setVoiceGuidance,
            tag = "settings_voice",
        )

        SectionHeader(title = stringResource(R.string.settings_section_privacy))
        ActionRow(
            title = stringResource(R.string.settings_clear_history),
            hint = stringResource(R.string.settings_clear_history_hint),
            onClick = { onClear(ClearTarget.History) },
            tag = "settings_clear_history",
        )
        ActionRow(
            title = stringResource(R.string.settings_clear_cache),
            hint = stringResource(R.string.settings_clear_cache_hint),
            onClick = { onClear(ClearTarget.Cache) },
            tag = "settings_clear_cache",
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SegmentedChoice(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    tagPrefix: String,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                modifier = Modifier.heightIn(min = Sizes.minTouchTarget).testTag("${tagPrefix}_$value"),
            ) { Text(label) }
        }
    }
}

@Composable
private fun AvoidSwitches(options: RouteOptions, onChange: (RouteOptions) -> Unit) {
    SwitchRow(
        title = stringResource(R.string.route_option_avoid_tolls),
        hint = null,
        checked = options.avoidTolls,
        onChange = { onChange(options.copy(avoidTolls = it)) },
        tag = "settings_avoid_tolls",
    )
    SwitchRow(
        title = stringResource(R.string.route_option_avoid_motorways),
        hint = null,
        checked = options.avoidMotorways,
        onChange = { onChange(options.copy(avoidMotorways = it)) },
        tag = "settings_avoid_motorways",
    )
    SwitchRow(
        title = stringResource(R.string.route_option_avoid_ferries),
        hint = null,
        checked = options.avoidFerries,
        onChange = { onChange(options.copy(avoidFerries = it)) },
        tag = "settings_avoid_ferries",
    )
}

@Composable
private fun SwitchRow(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouchTarget)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = spacing.lg, vertical = spacing.xs)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ActionRow(title: String, hint: String, onClick: () -> Unit, tag: String) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.sm)
            .semantics(mergeDescendants = true) {}
            .testTag(tag),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
