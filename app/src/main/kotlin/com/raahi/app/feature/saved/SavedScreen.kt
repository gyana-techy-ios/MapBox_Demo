package com.raahi.app.feature.saved

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raahi.app.R
import com.raahi.app.core.di.LocalAppContainer
import com.raahi.app.core.di.appViewModel
import com.raahi.app.domain.model.Place
import com.raahi.app.domain.model.SavedPlace
import com.raahi.app.ui.components.EmptyState
import com.raahi.app.ui.components.RaahiTopBar
import com.raahi.app.ui.components.SectionHeader
import com.raahi.app.ui.components.SkeletonList
import com.raahi.app.ui.components.SwipeToDeleteBox
import com.raahi.app.ui.theme.LocalRaahiColors
import com.raahi.app.ui.theme.LocalSpacing
import com.raahi.app.ui.theme.Sizes
import java.util.concurrent.TimeUnit

@Composable
fun SavedRoute(
    onOpenPlace: (Place) -> Unit,
    onOpenContacts: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenExplore: () -> Unit = {},
) {
    val viewModel = appViewModel { SavedViewModel(repository = it.savedPlaceRepository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.saved_undo)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SavedEvent.ShowUndo -> {
                    val result = snackbarHost.showSnackbar(
                        message = event.message.resolve(context),
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.testTag("screen_saved"),
        topBar = { RaahiTopBar(title = stringResource(R.string.saved_title)) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        val spacing = LocalSpacing.current
        Column(modifier = Modifier.padding(padding)) {
            when {
                !state.isLoaded -> SkeletonList(modifier = Modifier.padding(top = spacing.md), itemCount = 5)
                state.isEmpty -> {
                    ContactsEntryRow(onClick = onOpenContacts)
                    HorizontalDivider()
                    EmptyState(
                        icon = Icons.Outlined.BookmarkBorder,
                        title = stringResource(R.string.saved_empty_title),
                        body = stringResource(R.string.saved_empty_body),
                        modifier = Modifier.testTag("saved_empty"),
                        actionLabel = stringResource(R.string.saved_action_explore),
                        onAction = onOpenExplore,
                    )
                }
                else -> SavedList(state, onOpenPlace, onOpenContacts, viewModel::delete)
            }
        }
    }
}

@Composable
private fun SavedList(
    state: SavedUiState,
    onOpenPlace: (Place) -> Unit,
    onOpenContacts: () -> Unit,
    onDelete: (SavedPlace) -> Unit,
) {
    val spacing = LocalSpacing.current
    val deleteLabel = stringResource(R.string.saved_delete)
    LazyColumn(
        modifier = Modifier.testTag("saved_list"),
        contentPadding = PaddingValues(bottom = spacing.xl),
    ) {
        item(key = "contacts") {
            ContactsEntryRow(onClick = onOpenContacts)
            HorizontalDivider()
        }
        item(key = "slots_header") { SectionHeader(stringResource(R.string.saved_section_home_work)) }
        item(key = "slot_home") {
            SlotRow(
                saved = state.home,
                icon = Icons.Outlined.Home,
                label = stringResource(R.string.saved_slot_home),
                emptyHint = stringResource(R.string.saved_slot_home_empty),
                tag = "saved_slot_home",
                deleteLabel = deleteLabel,
                onOpen = onOpenPlace,
                onDelete = onDelete,
            )
        }
        item(key = "slot_work") {
            SlotRow(
                saved = state.work,
                icon = Icons.Outlined.Work,
                label = stringResource(R.string.saved_slot_work),
                emptyHint = stringResource(R.string.saved_slot_work_empty),
                tag = "saved_slot_work",
                deleteLabel = deleteLabel,
                onOpen = onOpenPlace,
                onDelete = onDelete,
            )
        }
        item(key = "parking_header") { SectionHeader(stringResource(R.string.saved_section_parking)) }
        if (state.parking.isEmpty()) {
            item(key = "parking_empty") { SectionHint(stringResource(R.string.saved_parking_empty), "saved_parking_empty") }
        } else {
            items(state.parking, key = { "parking_${it.id}" }) { saved ->
                SwipeToDeleteBox(deleteLabel = deleteLabel, onDelete = { onDelete(saved) }) {
                    ParkingRow(saved, deleteLabel, onOpen = onOpenPlace, onDelete = onDelete)
                }
            }
        }
        item(key = "favorites_header") { SectionHeader(stringResource(R.string.saved_section_favorites)) }
        if (state.favorites.isEmpty()) {
            item(key = "favorites_empty") { SectionHint(stringResource(R.string.saved_favorites_empty), "saved_favorites_empty") }
        } else {
            items(state.favorites, key = { "favorite_${it.id}" }) { saved ->
                SwipeToDeleteBox(deleteLabel = deleteLabel, onDelete = { onDelete(saved) }) {
                    FavoriteRow(saved, deleteLabel, onOpen = onOpenPlace, onDelete = onDelete)
                }
            }
        }
    }
}

@Composable
private fun ContactsEntryRow(onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouchTarget + spacing.md)
            .semantics(mergeDescendants = true) {}
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.lg)
            .testTag("saved_contacts_row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Contacts, contentDescription = null, tint = LocalRaahiColors.current.emergency)
        Spacer(Modifier.width(spacing.md))
        Text(
            text = stringResource(R.string.saved_action_contacts),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
private fun SectionHint(text: String, tag: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = LocalSpacing.current.lg, vertical = LocalSpacing.current.sm).testTag(tag),
    )
}

@Composable
private fun SlotRow(
    saved: SavedPlace?,
    icon: ImageVector,
    label: String,
    emptyHint: String,
    tag: String,
    deleteLabel: String,
    onOpen: (Place) -> Unit,
    onDelete: (SavedPlace) -> Unit,
) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouchTarget + spacing.md)
            .semantics(mergeDescendants = true) {}
            .let { if (saved != null) it.clickable(role = Role.Button, onClick = { onOpen(saved.place) }) else it }
            .padding(start = spacing.lg)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(spacing.md))
        Column(modifier = Modifier.weight(1f).padding(vertical = spacing.sm)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                text = saved?.place?.let { it.address ?: it.name } ?: emptyHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (saved != null) {
            IconButton(onClick = { onDelete(saved) }, modifier = Modifier.heightIn(min = Sizes.minTouchTarget)) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "$deleteLabel: $label")
            }
        } else {
            Spacer(Modifier.width(spacing.lg))
        }
    }
}

@Composable
private fun ParkingRow(saved: SavedPlace, deleteLabel: String, onOpen: (Place) -> Unit, onDelete: (SavedPlace) -> Unit) {
    val spacing = LocalSpacing.current
    val clock = LocalAppContainer.current.clock
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouchTarget + spacing.md)
            .semantics(mergeDescendants = true) {}
            .padding(start = spacing.lg)
            .testTag("saved_item_${saved.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.LocalParking, contentDescription = null, tint = LocalRaahiColors.current.categoryParking)
        Spacer(Modifier.width(spacing.md))
        Column(modifier = Modifier.weight(1f).padding(vertical = spacing.sm)) {
            Text(saved.place.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = stringResource(R.string.saved_parking_time, timeAgo(saved.savedAt, clock.nowMillis())),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            saved.note?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(
            onClick = { onOpen(saved.place) },
            modifier = Modifier.heightIn(min = Sizes.minTouchTarget).testTag("saved_open_${saved.id}"),
        ) { Text(stringResource(R.string.saved_open)) }
        IconButton(onClick = { onDelete(saved) }, modifier = Modifier.heightIn(min = Sizes.minTouchTarget)) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "$deleteLabel: ${saved.place.name}")
        }
    }
}

@Composable
private fun FavoriteRow(saved: SavedPlace, deleteLabel: String, onOpen: (Place) -> Unit, onDelete: (SavedPlace) -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouchTarget + spacing.md)
            .semantics(mergeDescendants = true) {}
            .clickable(role = Role.Button, onClick = { onOpen(saved.place) })
            .padding(start = spacing.lg)
            .testTag("saved_item_${saved.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Star, contentDescription = null, tint = LocalRaahiColors.current.saffron)
        Spacer(Modifier.width(spacing.md))
        Column(modifier = Modifier.weight(1f).padding(vertical = spacing.sm)) {
            Text(saved.place.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            saved.place.address?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = { onDelete(saved) }, modifier = Modifier.heightIn(min = Sizes.minTouchTarget)) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "$deleteLabel: ${saved.place.name}")
        }
    }
}

@Composable
private fun timeAgo(savedAtMillis: Long, nowMillis: Long): String {
    val elapsed = (nowMillis - savedAtMillis).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed).toInt()
    val hours = TimeUnit.MILLISECONDS.toHours(elapsed).toInt()
    val days = TimeUnit.MILLISECONDS.toDays(elapsed).toInt()
    return when {
        minutes < 1 -> stringResource(R.string.saved_time_just_now)
        minutes < 60 -> pluralStringResource(R.plurals.saved_time_minutes_ago, minutes, minutes)
        hours < 24 -> pluralStringResource(R.plurals.saved_time_hours_ago, hours, hours)
        else -> pluralStringResource(R.plurals.saved_time_days_ago, days, days)
    }
}
