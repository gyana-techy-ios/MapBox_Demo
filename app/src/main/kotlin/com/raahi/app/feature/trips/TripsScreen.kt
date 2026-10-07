package com.raahi.app.feature.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raahi.app.R
import com.raahi.app.core.di.appViewModel
import com.raahi.app.domain.model.Trip
import com.raahi.app.ui.components.ConfirmDialog
import com.raahi.app.ui.components.EmptyState
import com.raahi.app.ui.components.ErrorState
import com.raahi.app.ui.components.LabeledFab
import com.raahi.app.ui.components.RaahiTopBar
import com.raahi.app.ui.components.SectionHeader
import com.raahi.app.ui.components.SkeletonList
import com.raahi.app.ui.components.displayName
import com.raahi.app.ui.components.icon
import com.raahi.app.ui.format.formatRelativeTime
import com.raahi.app.ui.theme.LocalSpacing
import com.raahi.app.ui.theme.Sizes

@Composable
fun TripsRoute(
    onOpenTrip: (Long) -> Unit,
    onNewTrip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = appViewModel { TripsViewModel(trips = it.tripRepository, clock = it.clock) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.trips_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    val clearedMessage = stringResource(R.string.trips_recent_cleared)
    var pendingDeletion by remember { mutableStateOf<Trip?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TripsEvent.TripDeleted -> {
                    val result = snackbarHost.showSnackbar(deletedMessage, actionLabel = undoLabel)
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
                }
                TripsEvent.RecentCleared -> snackbarHost.showSnackbar(clearedMessage)
            }
        }
    }

    TripsContent(
        state = state,
        snackbarHost = snackbarHost,
        onOpenTrip = onOpenTrip,
        onNewTrip = onNewTrip,
        onRequestDelete = { pendingDeletion = it },
        onClearRecent = viewModel::clearRecent,
        onRetry = viewModel::retry,
        modifier = modifier,
    )

    pendingDeletion?.let { trip ->
        ConfirmDialog(
            title = stringResource(R.string.trips_delete_title),
            message = stringResource(R.string.trips_delete_message, trip.name),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                pendingDeletion = null
                viewModel.deleteTrip(trip)
            },
            onDismiss = { pendingDeletion = null },
        )
    }
}

@Composable
private fun TripsContent(
    state: TripsUiState,
    snackbarHost: SnackbarHostState,
    onOpenTrip: (Long) -> Unit,
    onNewTrip: () -> Unit,
    onRequestDelete: (Trip) -> Unit,
    onClearRecent: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.testTag("screen_trips"),
        topBar = { RaahiTopBar(title = stringResource(R.string.trips_title)) },
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            LabeledFab(
                text = stringResource(R.string.trips_action_new),
                icon = Icons.Outlined.Add,
                onClick = onNewTrip,
                modifier = Modifier.testTag("trips_new"),
            )
        },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when {
            state.isLoading -> SkeletonList(modifier = contentModifier.padding(top = LocalSpacing.current.lg))
            state.error != null -> ErrorState(error = state.error, modifier = contentModifier, onRetry = onRetry)
            state.isEmpty -> EmptyState(
                icon = Icons.Outlined.Route,
                title = stringResource(R.string.trips_empty_title),
                body = stringResource(R.string.trips_empty_body),
                modifier = contentModifier.testTag("trips_empty"),
                actionLabel = stringResource(R.string.trips_empty_cta),
                onAction = onNewTrip,
            )
            else -> TripsList(
                state = state,
                onOpenTrip = onOpenTrip,
                onRequestDelete = onRequestDelete,
                onClearRecent = onClearRecent,
                onNewTrip = onNewTrip,
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun TripsList(
    state: TripsUiState,
    onOpenTrip: (Long) -> Unit,
    onRequestDelete: (Trip) -> Unit,
    onClearRecent: () -> Unit,
    onNewTrip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    LazyColumn(
        modifier = modifier.testTag("trips_list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = spacing.lg,
            end = spacing.lg,
            top = spacing.sm,
            bottom = spacing.xxxl + spacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        if (state.trips.isEmpty()) {
            item(key = "no_trips") {
                TextButton(onClick = onNewTrip, modifier = Modifier.heightIn(min = Sizes.minTouchTarget)) {
                    Text(stringResource(R.string.trips_empty_cta))
                }
            }
        }
        items(state.trips, key = { it.id }) { trip ->
            TripCard(
                trip = trip,
                nowMillis = state.nowMillis,
                onOpen = { onOpenTrip(trip.id) },
                onDelete = { onRequestDelete(trip) },
            )
        }
        if (state.recent.isNotEmpty()) {
            item(key = "recent_header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(title = stringResource(R.string.trips_recent_title), modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = onClearRecent,
                        modifier = Modifier.heightIn(min = Sizes.minTouchTarget).testTag("trips_clear_recent"),
                    ) { Text(stringResource(R.string.trips_recent_clear)) }
                }
            }
            items(state.recent, key = { "recent_${it.id}" }) { trip ->
                RecentTripRow(trip = trip, nowMillis = state.nowMillis, onOpen = { onOpenTrip(trip.id) })
            }
        }
    }
}

@Composable
private fun TripCard(trip: Trip, nowMillis: Long, onOpen: () -> Unit, onDelete: () -> Unit) {
    val spacing = LocalSpacing.current
    val stops = trip.stops.sortedBy { it.order }
    val stopCount = pluralStringResource(R.plurals.trip_stop_count, stops.size, stops.size)
    val first = stops.firstOrNull()?.displayName().orEmpty()
    val last = stops.lastOrNull()?.displayName().orEmpty()
    val updated = formatRelativeTime(trip.updatedAt, nowMillis)
    val description = stringResource(R.string.trips_card_description, trip.name, stopCount, first, last, updated)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Sizes.minTouchTarget)
                    .clickable(onClick = onOpen)
                    .padding(spacing.lg)
                    .semantics(mergeDescendants = true) { contentDescription = description }
                    .testTag("trip_card_${trip.id}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(trip.profile.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(spacing.md))
                Column {
                    Text(trip.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = stringResource(R.string.stats_separator, stopCount, stringResource(R.string.trip_first_to_last, first, last)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.trips_updated, updated),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.testTag("trip_delete_${trip.id}")) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.trips_delete_action, trip.name),
                )
            }
        }
    }
}

@Composable
private fun RecentTripRow(trip: Trip, nowMillis: Long, onOpen: () -> Unit) {
    val spacing = LocalSpacing.current
    val updated = formatRelativeTime(trip.updatedAt, nowMillis)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouchTarget)
            .clickable(onClick = onOpen)
            .padding(vertical = spacing.sm)
            .semantics(mergeDescendants = true) {}
            .testTag("recent_trip_${trip.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(trip.profile.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(spacing.md))
        Column {
            Text(trip.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(updated, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
