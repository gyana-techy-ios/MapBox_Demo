package com.raahi.app.feature.offlineregionpicker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raahi.app.R
import com.raahi.app.core.di.appViewModel
import com.raahi.app.domain.model.MapStyleMode
import com.raahi.app.feature.offline.MeteredDownloadGuard
import com.raahi.app.map.LocalMapboxConfig
import com.raahi.app.map.RaahiMap
import com.raahi.app.map.rememberRaahiMapState
import com.raahi.app.ui.components.ConfirmDialog
import com.raahi.app.ui.components.ConnectivityBanner
import com.raahi.app.ui.components.PrimaryButton
import com.raahi.app.ui.components.RaahiTopBar
import com.raahi.app.ui.format.formatFileSize
import com.raahi.app.ui.theme.LocalSpacing
import com.raahi.app.ui.util.rememberMapStyle
import com.raahi.app.ui.theme.RaahiShapes
import com.raahi.app.ui.theme.Sizes

private const val FRAME_FILL_ALPHA = 0.12f
private val PanelMaxHeight = 360.dp

@Composable
fun OfflineRegionPickerRoute(
    onBack: () -> Unit,
    onDownloadStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val viewModel = appViewModel {
        OfflineRegionPickerViewModel(
            offlineMaps = it.offlineMapRepository,
            coordinator = it.downloadCoordinator,
            placeSearch = it.placeSearchRepository,
            locationRepository = it.locationRepository,
            connectivity = it.connectivityMonitor,
            storage = it.storageProbe,
            meteredGuard = MeteredDownloadGuard(it.preferencesRepository, it.connectivityMonitor),
            defaultName = { number -> context.getString(R.string.offlineregionpicker_default_name, number) },
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mapStyle = rememberMapStyle()
    var confirmMetered by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                RegionPickerEvent.DownloadStarted -> onDownloadStarted()
                RegionPickerEvent.ConfirmMetered -> confirmMetered = true
            }
        }
    }

    Scaffold(
        modifier = modifier.testTag("screen_offlineregionpicker"),
        topBar = { RaahiTopBar(title = stringResource(R.string.offlineregionpicker_title), onBack = onBack) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            ConnectivityBanner(state = state.connectivity)
            Box(modifier = Modifier.weight(1f)) {
                PickerMap(state = state, mapStyle = mapStyle, viewModel = viewModel)
            }
            PickerPanel(state = state, viewModel = viewModel)
        }
    }

    if (confirmMetered) {
        ConfirmDialog(
            title = stringResource(R.string.offline_metered_title),
            message = stringResource(R.string.offline_metered_message),
            confirmLabel = stringResource(R.string.offline_metered_confirm),
            onConfirm = {
                confirmMetered = false
                viewModel.download(allowMetered = true)
            },
            onDismiss = { confirmMetered = false },
        )
    }
}

@Composable
private fun PickerMap(state: RegionPickerUiState, mapStyle: MapStyleMode, viewModel: OfflineRegionPickerViewModel) {
    val mapState = rememberRaahiMapState()
    val mapAvailable = LocalMapboxConfig.current.isMapAvailable
    LaunchedEffect(state.initialCenter, mapAvailable) {
        val center = state.initialCenter
        if (mapAvailable && center != null) mapState.flyTo(center, zoom = START_ZOOM, durationMillis = 0L)
    }
    val mapDescription = stringResource(R.string.offlineregionpicker_map_description)
    Box(modifier = Modifier.fillMaxSize().semantics { contentDescription = mapDescription }.testTag("picker_map")) {
        RaahiMap(
            state = mapState,
            style = mapStyle,
            showUserLocation = state.hasLocationPermission,
            onCameraIdle = viewModel::onCameraIdle,
        )
        SelectionFrame()
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = HINT_ALPHA),
            shape = RaahiShapes.small,
            modifier = Modifier.align(Alignment.TopCenter).padding(LocalSpacing.current.sm),
        ) {
            Text(
                text = stringResource(R.string.offlineregionpicker_hint),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = LocalSpacing.current.md, vertical = LocalSpacing.current.xs),
            )
        }
    }
}

@Composable
private fun SelectionFrame() {
    val frameDescription = stringResource(R.string.offlineregionpicker_frame_description)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .padding(
                    horizontal = maxWidth * RegionFrame.HORIZONTAL_INSET,
                    vertical = maxHeight * RegionFrame.VERTICAL_INSET,
                )
                .fillMaxSize()
                .semantics { contentDescription = frameDescription }
                .testTag("picker_frame"),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RaahiShapes.small,
                color = MaterialTheme.colorScheme.primary.copy(alpha = FRAME_FILL_ALPHA),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                content = {},
            )
        }
    }
}

@Composable
private fun PickerPanel(state: RegionPickerUiState, viewModel: OfflineRegionPickerViewModel) {
    val spacing = LocalSpacing.current
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .heightIn(max = PanelMaxHeight)
                .verticalScroll(rememberScrollState())
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.offlineregionpicker_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("picker_name"),
            )
            Text(
                text = stringResource(R.string.offlineregionpicker_detail_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            Column(modifier = Modifier.selectableGroup()) {
                DetailPreset.entries.forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Sizes.minTouchTarget)
                            .selectable(
                                selected = state.preset == preset,
                                role = Role.RadioButton,
                                onClick = { viewModel.onPresetChange(preset) },
                            )
                            .testTag("picker_preset_${preset.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = state.preset == preset, onClick = null)
                        Text(
                            text = stringResource(preset.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = spacing.md),
                        )
                    }
                }
            }
            state.estimate?.let { estimate ->
                Text(
                    text = pluralStringResource(
                        R.plurals.offlineregionpicker_estimate,
                        estimate.tileCount.toInt().coerceAtLeast(1),
                        estimate.tileCount.toInt(),
                        formatFileSize(estimate.approximateBytes),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("picker_estimate"),
                )
            }
            state.issue?.let { issue ->
                Text(
                    text = stringResource(issue.messageRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("picker_issue"),
                )
            }
            PrimaryButton(
                text = stringResource(R.string.offlineregionpicker_action_download),
                onClick = { viewModel.download() },
                icon = Icons.Outlined.FileDownload,
                enabled = state.canDownload,
                modifier = Modifier.fillMaxWidth().testTag("picker_download"),
            )
        }
    }
}

private const val START_ZOOM = 13.0
private const val HINT_ALPHA = 0.85f
