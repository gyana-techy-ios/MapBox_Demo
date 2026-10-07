package com.raahi.app.feature.offline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.raahi.app.core.result.toMessageRes
import com.raahi.app.domain.model.OfflineRegionStatus
import com.raahi.app.ui.components.ConfirmDialog
import com.raahi.app.ui.components.ConnectivityBanner
import com.raahi.app.ui.components.ErrorState
import com.raahi.app.ui.components.LabeledFab
import com.raahi.app.ui.components.RaahiTopBar
import com.raahi.app.ui.components.SectionHeader
import com.raahi.app.ui.components.SkeletonList
import com.raahi.app.ui.format.formatFileSize
import com.raahi.app.ui.format.formatShortDate
import com.raahi.app.ui.theme.LocalRaahiColors
import com.raahi.app.ui.theme.LocalSpacing
import com.raahi.app.ui.theme.RaahiShapes
import com.raahi.app.ui.theme.Sizes

@Composable
fun OfflineRoute(
    onPickRegion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = appViewModel {
        OfflineViewModel(
            offlineMaps = it.offlineMapRepository,
            coordinator = it.downloadCoordinator,
            connectivity = it.connectivityMonitor,
            config = it.mapboxConfig,
            meteredGuard = MeteredDownloadGuard(it.preferencesRepository, it.connectivityMonitor),
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.offline_deleted)
    var pendingDelete by remember { mutableStateOf<OfflineRegionItem?>(null) }
    var meteredRegionId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is OfflineEvent.ConfirmMetered -> meteredRegionId = event.regionId
                OfflineEvent.Deleted -> snackbarHost.showSnackbar(deletedMessage)
            }
        }
    }

    Scaffold(
        modifier = modifier.testTag("screen_offline"),
        topBar = { RaahiTopBar(title = stringResource(R.string.offline_title)) },
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            LabeledFab(
                text = stringResource(R.string.offline_action_pick),
                icon = Icons.Outlined.Add,
                onClick = onPickRegion,
                modifier = Modifier.testTag("offline_pick"),
            )
        },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when {
            state.isLoading -> SkeletonList(modifier = contentModifier.padding(top = LocalSpacing.current.lg))
            state.loadError != null -> ErrorState(error = state.loadError!!, modifier = contentModifier)
            else -> OfflineList(
                state = state,
                onPickRegion = onPickRegion,
                onAction = { item, action ->
                    when (action) {
                        RegionAction.Cancel -> viewModel.cancel(item.region.id)
                        RegionAction.Resume, RegionAction.Update -> viewModel.resume(item.region.id)
                        RegionAction.Delete -> pendingDelete = item
                    }
                },
                modifier = contentModifier,
            )
        }
    }

    pendingDelete?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.offline_delete_title),
            message = stringResource(R.string.offline_delete_message, item.region.name),
            confirmLabel = stringResource(R.string.offline_action_delete),
            onConfirm = {
                pendingDelete = null
                viewModel.delete(item.region.id)
            },
            onDismiss = { pendingDelete = null },
        )
    }
    meteredRegionId?.let { regionId ->
        ConfirmDialog(
            title = stringResource(R.string.offline_metered_title),
            message = stringResource(R.string.offline_metered_message),
            confirmLabel = stringResource(R.string.offline_metered_confirm),
            onConfirm = {
                meteredRegionId = null
                viewModel.resume(regionId, allowMetered = true)
            },
            onDismiss = { meteredRegionId = null },
        )
    }
}

@Composable
private fun OfflineList(
    state: OfflineUiState,
    onPickRegion: () -> Unit,
    onAction: (OfflineRegionItem, RegionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    LazyColumn(
        modifier = modifier.testTag("offline_list"),
        contentPadding = PaddingValues(bottom = spacing.xxxl + spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        item(key = "banner") { ConnectivityBanner(state = state.connectivity) }
        item(key = "intro") { OfflineIntro() }
        if (!state.isConfigured) {
            item(key = "config") { ConfigurationNotice() }
        }
        item(key = "regions_header") { SectionHeader(title = stringResource(R.string.offline_regions_title)) }
        if (state.items.isEmpty()) {
            item(key = "empty") { EmptyRegions(onPickRegion) }
        } else {
            item(key = "storage") {
                Text(
                    text = pluralStringResource(R.plurals.offline_storage_summary, state.items.size, formatFileSize(state.totalBytes), state.items.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = spacing.lg).testTag("offline_storage"),
                )
            }
            items(state.items, key = { it.region.id }) { item ->
                RegionCard(item = item, onAction = { onAction(item, it) })
            }
        }
    }
}

@Composable
private fun OfflineIntro() {
    val spacing = LocalSpacing.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg).testTag("offline_intro"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(
                text = stringResource(R.string.offline_intro_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            CapabilityRow(Icons.Outlined.CheckCircle, stringResource(R.string.offline_matrix_map), works = true)
            CapabilityRow(Icons.Outlined.CloudOff, stringResource(R.string.offline_matrix_search), works = false)
            CapabilityRow(Icons.Outlined.CheckCircle, stringResource(R.string.offline_matrix_saved), works = true)
        }
    }
}

@Composable
private fun CapabilityRow(icon: ImageVector, text: String, works: Boolean) {
    val spacing = LocalSpacing.current
    val status = stringResource(if (works) R.string.offline_works else R.string.offline_needs_internet)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouchTarget)
            .semantics(mergeDescendants = true) { contentDescription = "$text. $status" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (works) LocalRaahiColors.current.success else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(spacing.md))
        Column {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConfigurationNotice() {
    val spacing = LocalSpacing.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg).testTag("offline_config_error"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(
                text = stringResource(R.string.offline_config_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.error_configuration),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun EmptyRegions(onPickRegion: () -> Unit) {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(spacing.xl).testTag("offline_empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Icon(Icons.Outlined.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.width(Sizes.iconLarge))
        Text(stringResource(R.string.offline_empty_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.offline_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onPickRegion, modifier = Modifier.heightIn(min = Sizes.minTouchTarget)) {
            Text(stringResource(R.string.offline_empty_cta))
        }
    }
}

@Composable
private fun RegionCard(item: OfflineRegionItem, onAction: (RegionAction) -> Unit) {
    val spacing = LocalSpacing.current
    val region = item.region
    val statusText = stringResource(item.status.labelRes())
    val size = formatFileSize(region.sizeBytes)
    val whenText = region.downloadedAt?.let { stringResource(R.string.offline_downloaded_on, formatShortDate(it)) }
        ?: stringResource(R.string.offline_not_downloaded)
    val percent = (item.progress * PERCENT).toInt()
    val progressDescription = stringResource(R.string.offline_progress_description, region.name, percent)
    val cardDescription = stringResource(R.string.offline_region_description, region.name, statusText, "$size, $whenText")
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg).testTag("offline_region_${region.id}")) {
        Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = cardDescription },
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(region.name, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(
                        text = statusText,
                        icon = item.status.icon(),
                        container = item.status.containerColor(),
                        content = item.status.contentColor(),
                        tag = "offline_status_${region.id}",
                    )
                    if (item.isOutdated) {
                        StatusPill(
                            text = stringResource(R.string.offline_outdated),
                            icon = Icons.Outlined.Schedule,
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                            tag = "offline_outdated_${region.id}",
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.stats_separator, size, whenText),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.status == OfflineRegionStatus.Downloading) {
                val progressModifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = progressDescription }
                    .testTag("offline_progress_${region.id}")
                if (item.progress > 0f) {
                    LinearProgressIndicator(progress = { item.progress }, modifier = progressModifier)
                } else {
                    LinearProgressIndicator(modifier = progressModifier)
                }
            }
            item.error?.let {
                Text(
                    text = stringResource(it.toMessageRes()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                item.actions.forEach { action ->
                    TextButton(
                        onClick = { onAction(action) },
                        modifier = Modifier.heightIn(min = Sizes.minTouchTarget).testTag("offline_${action.name.lowercase()}_${region.id}"),
                    ) { Text(stringResource(action.labelRes())) }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, icon: ImageVector, container: Color, content: Color, tag: String) {
    val spacing = LocalSpacing.current
    Surface(shape = RaahiShapes.small, color = container, contentColor = content, modifier = Modifier.semantics(mergeDescendants = true) {}.testTag(tag)) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.width(16.dp))
            Spacer(Modifier.width(spacing.xs))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun OfflineRegionStatus.labelRes(): Int = when (this) {
    OfflineRegionStatus.Downloading -> R.string.offline_status_downloading
    OfflineRegionStatus.Completed -> R.string.offline_status_ready
    OfflineRegionStatus.Failed -> R.string.offline_status_failed
    OfflineRegionStatus.Cancelled -> R.string.offline_status_cancelled
    OfflineRegionStatus.Partial -> R.string.offline_status_partial
}

private fun OfflineRegionStatus.icon(): ImageVector = when (this) {
    OfflineRegionStatus.Downloading -> Icons.Outlined.CloudDownload
    OfflineRegionStatus.Completed -> Icons.Outlined.CheckCircle
    OfflineRegionStatus.Failed -> Icons.Outlined.ErrorOutline
    OfflineRegionStatus.Cancelled -> Icons.Outlined.Cancel
    OfflineRegionStatus.Partial -> Icons.Outlined.PauseCircleOutline
}

@Composable
private fun OfflineRegionStatus.containerColor(): Color = when (this) {
    OfflineRegionStatus.Downloading -> MaterialTheme.colorScheme.primaryContainer
    OfflineRegionStatus.Completed -> LocalRaahiColors.current.successContainer
    OfflineRegionStatus.Failed -> MaterialTheme.colorScheme.errorContainer
    OfflineRegionStatus.Cancelled, OfflineRegionStatus.Partial -> MaterialTheme.colorScheme.secondaryContainer
}

@Composable
private fun OfflineRegionStatus.contentColor(): Color = when (this) {
    OfflineRegionStatus.Downloading -> MaterialTheme.colorScheme.onPrimaryContainer
    OfflineRegionStatus.Completed -> LocalRaahiColors.current.onSuccessContainer
    OfflineRegionStatus.Failed -> MaterialTheme.colorScheme.onErrorContainer
    OfflineRegionStatus.Cancelled, OfflineRegionStatus.Partial -> MaterialTheme.colorScheme.onSecondaryContainer
}

private fun RegionAction.labelRes(): Int = when (this) {
    RegionAction.Cancel -> R.string.offline_action_cancel
    RegionAction.Resume -> R.string.offline_action_resume
    RegionAction.Update -> R.string.offline_action_update
    RegionAction.Delete -> R.string.offline_action_delete
}

private const val PERCENT = 100
