package com.raahi.app.feature.guidance

import android.Manifest
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.ForkLeft
import androidx.compose.material.icons.outlined.ForkRight
import androidx.compose.material.icons.outlined.GpsOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.RoundaboutLeft
import androidx.compose.material.icons.outlined.RoundaboutRight
import androidx.compose.material.icons.outlined.Straight
import androidx.compose.material.icons.outlined.TurnLeft
import androidx.compose.material.icons.outlined.TurnRight
import androidx.compose.material.icons.outlined.TurnSharpLeft
import androidx.compose.material.icons.outlined.TurnSharpRight
import androidx.compose.material.icons.outlined.TurnSlightLeft
import androidx.compose.material.icons.outlined.TurnSlightRight
import androidx.compose.material.icons.outlined.UTurnLeft
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.WrongLocation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.defaultMinSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raahi.app.R
import com.raahi.app.core.di.appViewModel
import com.raahi.app.domain.model.MapStyleMode
import com.raahi.app.domain.model.RouteStep
import com.raahi.app.map.LocalMapboxConfig
import com.raahi.app.map.MapMarker
import com.raahi.app.map.MapMarkerKind
import com.raahi.app.map.MapRoute
import com.raahi.app.map.RaahiMap
import com.raahi.app.map.rememberRaahiMapState
import com.raahi.app.ui.components.ConfirmDialog
import com.raahi.app.ui.components.EmptyState
import com.raahi.app.ui.components.ErrorState
import com.raahi.app.ui.components.PrimaryButton
import com.raahi.app.ui.components.SecondaryButton
import com.raahi.app.ui.components.displayName
import com.raahi.app.ui.format.formatClockTime
import com.raahi.app.ui.format.formatDistance
import com.raahi.app.ui.format.formatDistanceText
import com.raahi.app.ui.format.formatDuration
import com.raahi.app.ui.theme.LocalSpacing
import com.raahi.app.ui.util.rememberMapStyle
import com.raahi.app.ui.theme.RaahiShapes
import com.raahi.app.ui.theme.Sizes
import com.raahi.app.ui.util.findActivity
import kotlinx.coroutines.delay

@Composable
fun GuidanceRoute(
    tripId: Long,
    routeIndex: Int,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val viewModel = appViewModel(key = "guidance_${tripId}_$routeIndex") {
        GuidanceViewModel(
            tripId = tripId,
            routeIndex = routeIndex,
            trips = it.tripRepository,
            routeStore = it.activeRouteStore,
            planRoutes = it.planRoutesUseCase,
            locationRepository = it.locationRepository,
            preferences = it.preferencesRepository,
            connectivity = it.connectivityMonitor,
            voice = it.voiceGuide,
            clock = it.clock,
            phraser = AndroidGuidancePhraser(appContext),
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mapStyle = rememberMapStyle()
    val snackbarHost = remember { SnackbarHostState() }
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    val voiceUnavailableMessage = stringResource(R.string.guidance_voice_unavailable)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.onPermissionResult()
    }

    KeepScreenOn()
    BackHandler { confirmExit = true }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                GuidanceEvent.VoiceUnavailable -> snackbarHost.showSnackbar(voiceUnavailableMessage)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().testTag("screen_guidance")) {
        when (state.phase) {
            GuidancePhase.Loading -> CenteredStatus(onExit = onExit) {
                CircularProgressIndicator()
                Text(stringResource(R.string.guidance_loading), style = MaterialTheme.typography.bodyLarge)
            }
            GuidancePhase.Failed -> CenteredStatus(onExit = onExit) {
                ErrorState(error = state.error ?: com.raahi.app.core.result.AppError.Unknown(), onRetry = viewModel::load)
            }
            GuidancePhase.TripMissing -> CenteredStatus(onExit = onExit) {
                EmptyState(
                    icon = Icons.Outlined.Navigation,
                    title = stringResource(R.string.error_title),
                    body = stringResource(R.string.guidance_missing_trip),
                )
            }
            GuidancePhase.PermissionDenied -> CenteredStatus(onExit = onExit) {
                EmptyState(
                    icon = Icons.Outlined.LocationOff,
                    title = stringResource(R.string.guidance_permission_title),
                    body = stringResource(R.string.guidance_permission_body),
                    actionLabel = stringResource(R.string.action_allow_location),
                    onAction = {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        )
                    },
                )
            }
            GuidancePhase.Active -> ActiveGuidance(
                state = state,
                mapStyle = mapStyle,
                onRequestExit = { confirmExit = true },
                onToggleVoice = viewModel::toggleVoice,
                onDismissArrival = viewModel::dismissArrival,
                onDone = onExit,
            )
        }
        SnackbarHost(snackbarHost, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = SnackbarLift))
    }

    if (confirmExit) {
        ConfirmDialog(
            title = stringResource(R.string.guidance_exit_title),
            message = stringResource(R.string.guidance_exit_message),
            confirmLabel = stringResource(R.string.guidance_exit),
            dismissLabel = stringResource(R.string.guidance_exit_keep),
            onConfirm = {
                confirmExit = false
                onExit()
            },
            onDismiss = { confirmExit = false },
        )
    }
}

private val SnackbarLift = 96.dp

@Composable
private fun KeepScreenOn() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

@Composable
private fun CenteredStatus(onExit: () -> Unit, content: @Composable () -> Unit) {
    val spacing = LocalSpacing.current
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.md, Alignment.CenterVertically),
            ) { content() }
            SecondaryButton(
                text = stringResource(R.string.guidance_exit),
                onClick = onExit,
                modifier = Modifier.fillMaxWidth().testTag("guidance_exit_direct"),
            )
        }
    }
}

@Composable
private fun ActiveGuidance(
    state: GuidanceUiState,
    mapStyle: MapStyleMode,
    onRequestExit: () -> Unit,
    onToggleVoice: () -> Unit,
    onDismissArrival: () -> Unit,
    onDone: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val route = state.route
    val mapState = rememberRaahiMapState()
    val mapAvailable = LocalMapboxConfig.current.isMapAvailable
    val hasFix = state.userPoint != null
    LaunchedEffect(mapAvailable, hasFix) {
        if (mapAvailable) {
            delay(FOLLOW_SETTLE_MILLIS)
            mapState.followUser()
        }
    }
    val mapRoutes = remember(route) { route?.let { listOf(MapRoute(points = it.geometry, isPrimary = true)) }.orEmpty() }
    val markers = remember(state.trip) { destinationMarkers(state) }
    val mapDescription = stringResource(R.string.guidance_map_description)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().semantics { contentDescription = mapDescription }.testTag("guidance_map")) {
            RaahiMap(state = mapState, style = mapStyle, markers = markers, routes = mapRoutes, showUserLocation = true)
        }
        Column(
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            ManeuverBanner(state)
            StatusBanners(state)
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(horizontal = spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                SmallFloatingActionButton(
                    onClick = onToggleVoice,
                    modifier = Modifier.size(Sizes.minTouchTarget + spacing.xs).testTag("guidance_voice_toggle"),
                ) {
                    Icon(
                        imageVector = if (state.voiceEnabled) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff,
                        contentDescription = stringResource(if (state.voiceEnabled) R.string.guidance_mute else R.string.guidance_unmute),
                    )
                }
                SmallFloatingActionButton(
                    onClick = { if (mapAvailable) mapState.followUser() },
                    modifier = Modifier.size(Sizes.minTouchTarget + spacing.xs).testTag("guidance_recenter"),
                ) {
                    Icon(Icons.Outlined.MyLocation, contentDescription = stringResource(R.string.guidance_recenter))
                }
            }
            state.arrival?.let { arrival ->
                ArrivalCard(arrival = arrival, onNext = onDismissArrival, onDone = onDone)
            }
            BottomBar(state = state, onExit = onRequestExit)
        }
    }
}

private fun destinationMarkers(state: GuidanceUiState): List<MapMarker> {
    val stops = state.trip?.stops?.sortedBy { it.order }.orEmpty()
    return stops.mapIndexedNotNull { index, stop ->
        if (index == 0) return@mapIndexedNotNull null
        stop.place?.let {
            MapMarker(
                id = "stop_$index",
                point = it.point,
                kind = if (index == stops.lastIndex) MapMarkerKind.Destination else MapMarkerKind.Stop,
                label = "${index + 1}",
            )
        }
    }
}

@Composable
private fun ManeuverBanner(state: GuidanceUiState) {
    val spacing = LocalSpacing.current
    val progress = state.progress
    val step: RouteStep? = progress?.nextStep ?: progress?.currentStep ?: state.route?.steps?.firstOrNull()
    val instruction = step?.instruction?.takeIf { it.isNotBlank() } ?: stringResource(R.string.guidance_follow_route)
    val distanceText = progress?.let { stringResource(R.string.guidance_in_distance, formatDistance(it.distanceToNextManeuverMeters)) }
    val description = stringResource(R.string.guidance_banner_description, instruction, distanceText.orEmpty())
    Surface(
        shape = RaahiShapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            }
            .testTag("guidance_banner"),
    ) {
        Row(modifier = Modifier.padding(spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = (step?.let { ManeuverGlyphs.glyphFor(it.maneuverType, it.modifier) } ?: ManeuverGlyph.Straight).icon(),
                contentDescription = null,
                modifier = Modifier.size(Sizes.iconLarge),
            )
            Spacer(Modifier.width(spacing.md))
            Column {
                if (distanceText != null) Text(distanceText, style = MaterialTheme.typography.headlineSmall)
                Text(instruction, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun StatusBanners(state: GuidanceUiState) {
    if (state.isRerouting) {
        StatusBanner(Icons.Outlined.Navigation, stringResource(R.string.guidance_rerouting), "guidance_rerouting")
    } else if (state.needsConnectionToReroute) {
        StatusBanner(Icons.Outlined.WrongLocation, stringResource(R.string.guidance_off_route_offline), "guidance_off_route_offline")
    }
    if (state.waitingForGps) {
        StatusBanner(Icons.Outlined.GpsOff, stringResource(R.string.guidance_waiting_gps), "guidance_waiting_gps")
    }
}

@Composable
private fun StatusBanner(icon: ImageVector, text: String, tag: String) {
    val spacing = LocalSpacing.current
    Surface(
        shape = RaahiShapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }.testTag(tag),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(spacing.sm))
            Text(text, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun ArrivalCard(arrival: ArrivalState, onNext: () -> Unit, onDone: () -> Unit) {
    val spacing = LocalSpacing.current
    val title = if (arrival.isFinal) {
        stringResource(R.string.guidance_arrived_title)
    } else {
        stringResource(R.string.guidance_reached_stop, arrival.stop?.displayName().orEmpty())
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md)
            .semantics { liveRegion = LiveRegionMode.Assertive }
            .testTag("guidance_arrival"),
    ) {
        Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(spacing.sm))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (arrival.isFinal) {
                PrimaryButton(text = stringResource(R.string.action_done), onClick = onDone, modifier = Modifier.fillMaxWidth().testTag("guidance_done"))
            } else {
                PrimaryButton(text = stringResource(R.string.guidance_next_stop), onClick = onNext, modifier = Modifier.fillMaxWidth().testTag("guidance_next_stop"))
            }
        }
    }
}

@Composable
private fun BottomBar(state: GuidanceUiState, onExit: () -> Unit) {
    val spacing = LocalSpacing.current
    val progress = state.progress
    val remainingDistance = progress?.remainingDistanceMeters ?: state.route?.distanceMeters
    val remainingDuration = progress?.remainingDurationSeconds ?: state.route?.durationSeconds
    Surface(
        shape = RaahiShapes.large,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth().testTag("guidance_bottom_bar"),
    ) {
        Column(
            modifier = Modifier.navigationBarsPadding().padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat(stringResource(R.string.guidance_label_distance), remainingDistance?.let { formatDistance(it) }.orEmpty(), "guidance_stat_distance")
                    Stat(stringResource(R.string.guidance_label_arrival), state.etaMillis?.let { formatClockTime(it) } ?: "–", "guidance_stat_eta")
                    Stat(stringResource(R.string.guidance_label_time), remainingDuration?.let { formatDuration(it) }.orEmpty(), "guidance_stat_time")
                }
                Spacer(Modifier.width(spacing.md))
                OutlinedButton(
                    onClick = onExit,
                    modifier = Modifier.defaultMinSize(minHeight = Sizes.minTouchTarget).testTag("guidance_exit"),
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(spacing.xs))
                    Text(stringResource(R.string.guidance_exit))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(spacing.xs))
                Text(
                    text = stringResource(R.string.guidance_foreground_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, tag: String) {
    Column(modifier = Modifier.semantics(mergeDescendants = true) {}.testTag(tag).padding(end = LocalSpacing.current.sm)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

private fun ManeuverGlyph.icon(): ImageVector = when (this) {
    ManeuverGlyph.Depart -> Icons.Outlined.Navigation
    ManeuverGlyph.Arrive -> Icons.Outlined.Flag
    ManeuverGlyph.Straight -> Icons.Outlined.Straight
    ManeuverGlyph.Left -> Icons.Outlined.TurnLeft
    ManeuverGlyph.Right -> Icons.Outlined.TurnRight
    ManeuverGlyph.SlightLeft -> Icons.Outlined.TurnSlightLeft
    ManeuverGlyph.SlightRight -> Icons.Outlined.TurnSlightRight
    ManeuverGlyph.SharpLeft -> Icons.Outlined.TurnSharpLeft
    ManeuverGlyph.SharpRight -> Icons.Outlined.TurnSharpRight
    ManeuverGlyph.UTurn -> Icons.Outlined.UTurnLeft
    ManeuverGlyph.RoundaboutLeft -> Icons.Outlined.RoundaboutLeft
    ManeuverGlyph.RoundaboutRight -> Icons.Outlined.RoundaboutRight
    ManeuverGlyph.ForkLeft -> Icons.Outlined.ForkLeft
    ManeuverGlyph.ForkRight -> Icons.Outlined.ForkRight
    ManeuverGlyph.Merge -> Icons.Outlined.Merge
}

class AndroidGuidancePhraser(private val context: android.content.Context) : GuidancePhraser {
    override fun phrase(kind: AnnouncementKind, step: RouteStep?, distanceMeters: Double, stop: com.raahi.app.domain.model.TripStop?): String {
        val instruction = step?.instruction.orEmpty()
        return when (kind) {
            AnnouncementKind.Upcoming ->
                context.getString(R.string.guidance_voice_in_distance, context.formatDistanceText(distanceMeters), instruction)
            AnnouncementKind.Imminent -> instruction
            AnnouncementKind.Arrived -> context.getString(R.string.guidance_voice_arrived)
            AnnouncementKind.ReachedStop ->
                context.getString(R.string.guidance_voice_reached_stop, stop?.place?.name ?: context.getString(R.string.trip_stop_unnamed))
            AnnouncementKind.Rerouting -> context.getString(R.string.guidance_voice_rerouting)
        }
    }
}

private const val FOLLOW_SETTLE_MILLIS = 500L
