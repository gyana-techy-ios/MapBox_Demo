package com.raahi.app.feature.tripeditor

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.EditRoad
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raahi.app.R
import com.raahi.app.core.di.appStatefulViewModel
import com.raahi.app.core.di.appViewModel
import com.raahi.app.domain.model.RouteOptions
import com.raahi.app.domain.model.Trip
import com.raahi.app.domain.usecase.TripIssue
import com.raahi.app.ui.components.ConfirmDialog
import com.raahi.app.ui.components.EmptyState
import com.raahi.app.ui.components.PrimaryButton
import com.raahi.app.ui.components.RaahiTopBar
import com.raahi.app.ui.components.RouteProfileSelector
import com.raahi.app.ui.components.SecondaryButton
import com.raahi.app.ui.components.SectionHeader
import com.raahi.app.ui.components.SkeletonList
import com.raahi.app.ui.components.displayName
import com.raahi.app.ui.theme.LocalSpacing
import com.raahi.app.ui.theme.Sizes

private enum class PickerMode { Stop, Start }

private const val HEADER_ITEM_COUNT = 3

@Composable
fun TripEditorRoute(
    tripId: Long,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appContext = LocalContext.current.applicationContext
    val viewModel = appStatefulViewModel(key = "trip_editor_$tripId") { container, handle ->
        TripEditorViewModel(
            tripId = tripId,
            trips = container.tripRepository,
            preferences = container.preferencesRepository,
            validator = container.tripValidator,
            clock = container.clock,
            defaultName = { millis ->
                val date = DateUtils.formatDateTime(appContext, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR)
                appContext.getString(R.string.tripeditor_default_name, date)
            },
            savedState = handle,
        )
    }
    val pickerViewModel = appViewModel(key = "stop_picker") {
        StopPickerViewModel(
            placeSearch = it.placeSearchRepository,
            savedPlaces = it.savedPlaceRepository,
            locationRepository = it.locationRepository,
            connectivity = it.connectivityMonitor,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pickerState by pickerViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    var pickerMode by rememberSaveable { mutableStateOf<PickerMode?>(null) }
    val requestBack: () -> Unit = { if (state.hasUnsavedChanges) showDiscard = true else onBack() }

    BackHandler(enabled = state.hasUnsavedChanges) { showDiscard = true }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TripEditorEvent.Saved -> onSaved(event.tripId)
                is TripEditorEvent.StopMoved -> snackbarHost.showSnackbar(
                    context.getString(
                        R.string.tripeditor_moved,
                        event.name ?: context.getString(R.string.trip_stop_unnamed),
                        event.position,
                    ),
                )
                TripEditorEvent.SaveFailed -> snackbarHost.showSnackbar(context.getString(R.string.tripeditor_save_failed))
            }
        }
    }
    LaunchedEffect(pickerViewModel) {
        pickerViewModel.events.collect { event ->
            if (event is StopPickerEvent.Picked) {
                when (pickerMode) {
                    PickerMode.Start -> viewModel.setStart(event.place)
                    else -> viewModel.addStop(event.place)
                }
                pickerMode = null
            }
        }
    }

    Scaffold(
        modifier = modifier.testTag("screen_tripeditor"),
        topBar = {
            RaahiTopBar(
                title = stringResource(if (state.isEditing) R.string.tripeditor_title_edit else R.string.tripeditor_title),
                onBack = requestBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            if (state.draft != null) {
                Surface(tonalElevation = 3.dp) {
                    PrimaryButton(
                        text = stringResource(R.string.tripeditor_save),
                        onClick = viewModel::save,
                        enabled = state.canSave,
                        modifier = Modifier.fillMaxWidth().padding(LocalSpacing.current.lg).testTag("trip_save"),
                    )
                }
            }
        },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        val draft = state.draft
        when {
            state.isLoading -> SkeletonList(modifier = contentModifier.padding(top = LocalSpacing.current.lg))
            state.isNotFound || draft == null -> EmptyState(
                icon = Icons.Outlined.EditRoad,
                title = stringResource(R.string.tripeditor_not_found_title),
                body = stringResource(R.string.tripeditor_not_found_body),
                modifier = contentModifier,
                actionLabel = stringResource(R.string.tripeditor_back_to_trips),
                onAction = onBack,
            )
            else -> EditorForm(
                draft = draft,
                issues = state.issues,
                viewModel = viewModel,
                onPickStart = {
                    pickerViewModel.reset()
                    pickerMode = PickerMode.Start
                },
                onAddStop = {
                    pickerViewModel.reset()
                    pickerMode = PickerMode.Stop
                },
                modifier = contentModifier,
            )
        }
    }

    pickerMode?.let { mode ->
        StopPickerSheet(
            title = stringResource(if (mode == PickerMode.Start) R.string.stoppicker_title_start else R.string.stoppicker_title_stop),
            state = pickerState,
            onQueryChange = pickerViewModel::onQueryChange,
            onPickSuggestion = pickerViewModel::pickSuggestion,
            onPickSaved = pickerViewModel::pickSaved,
            onDismiss = { pickerMode = null },
        )
    }
    if (showDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.tripeditor_discard_title),
            message = stringResource(R.string.tripeditor_discard_message),
            confirmLabel = stringResource(R.string.tripeditor_discard_confirm),
            dismissLabel = stringResource(R.string.tripeditor_keep_editing),
            onConfirm = {
                showDiscard = false
                onBack()
            },
            onDismiss = { showDiscard = false },
        )
    }
}

@Composable
private fun EditorForm(
    draft: TripDraft,
    issues: List<TripIssue>,
    viewModel: TripEditorViewModel,
    onPickStart: () -> Unit,
    onAddStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val listState = rememberLazyListState()
    val reorder = rememberStopReorderState(listState, HEADER_ITEM_COUNT, viewModel::moveStop)

    LazyColumn(
        state = listState,
        modifier = modifier.testTag("trip_editor_list"),
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        item(key = "name") {
            OutlinedTextField(
                value = draft.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.tripeditor_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("trip_name"),
            )
        }
        item(key = "start") {
            StartSection(draft = draft, viewModel = viewModel, onPickStart = onPickStart)
        }
        item(key = "stops_header") {
            Column {
                SectionHeader(title = stringResource(R.string.tripeditor_stops_title), modifier = Modifier.padding(horizontal = 0.dp))
                Text(
                    text = stringResource(R.string.tripeditor_stops_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        itemsIndexed(draft.destinations, key = { _, stop -> stop.key }) { index, stop ->
            val dragging = reorder.draggingKey == stop.key
            StopRow(
                index = index,
                count = draft.destinations.size,
                stop = stop,
                reorder = reorder,
                onMoveUp = { viewModel.moveStopUp(index) },
                onMoveDown = { viewModel.moveStopDown(index) },
                onRemove = { viewModel.removeStop(index) },
                modifier = if (dragging) {
                    Modifier.zIndex(1f).graphicsLayer { translationY = reorder.dragOffset }
                } else {
                    Modifier.animateItem()
                },
            )
        }
        item(key = "add_stop") {
            SecondaryButton(
                text = stringResource(R.string.tripeditor_add_stop),
                onClick = onAddStop,
                icon = Icons.Outlined.Add,
                modifier = Modifier.fillMaxWidth().testTag("trip_add_stop"),
            )
        }
        if (issues.isNotEmpty()) {
            item(key = "issues") {
                Column(modifier = Modifier.testTag("trip_issues"), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    issues.forEach { issue ->
                        Text(
                            text = stringResource(issue.messageRes(), Trip.MAX_STOPS),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        item(key = "travel") {
            Column {
                SectionHeader(title = stringResource(R.string.tripeditor_travel_title), modifier = Modifier.padding(horizontal = 0.dp))
                RouteProfileSelector(selected = draft.profile, onSelect = viewModel::setProfile)
            }
        }
        item(key = "avoid") {
            Column {
                SectionHeader(title = stringResource(R.string.tripeditor_avoid_title), modifier = Modifier.padding(horizontal = 0.dp))
                AvoidSwitches(options = draft.options, onChange = viewModel::setOptions)
            }
        }
    }
}

private fun TripIssue.messageRes(): Int = when (this) {
    TripIssue.TOO_FEW_STOPS -> R.string.tripeditor_issue_few
    TripIssue.TOO_MANY_STOPS -> R.string.tripeditor_issue_many
    TripIssue.EMPTY_STOP -> R.string.tripeditor_issue_empty
    TripIssue.MULTIPLE_CURRENT_LOCATIONS -> R.string.tripeditor_issue_multi
    TripIssue.CONSECUTIVE_DUPLICATE_STOPS -> R.string.tripeditor_issue_duplicate
}

@Composable
private fun StartSection(draft: TripDraft, viewModel: TripEditorViewModel, onPickStart: () -> Unit) {
    val spacing = LocalSpacing.current
    val usesMyLocation = draft.start.useCurrentLocation
    Column {
        SectionHeader(title = stringResource(R.string.tripeditor_start_title), modifier = Modifier.padding(horizontal = 0.dp))
        SwitchRow(
            label = stringResource(R.string.tripeditor_start_my_location),
            checked = usesMyLocation,
            onCheckedChange = { checked ->
                if (checked) viewModel.useMyLocationAsStart() else onPickStart()
            },
            tag = "trip_start_my_location",
            icon = Icons.Outlined.MyLocation,
        )
        if (!usesMyLocation) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = draft.start.place?.displayName() ?: stringResource(R.string.tripeditor_start_choose),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).testTag("trip_start_place"),
                )
                Spacer(Modifier.width(spacing.sm))
                SecondaryButton(
                    text = stringResource(R.string.tripeditor_start_change),
                    onClick = onPickStart,
                    modifier = Modifier.testTag("trip_start_change"),
                )
            }
        }
    }
}

@Composable
private fun AvoidSwitches(options: RouteOptions, onChange: (RouteOptions) -> Unit) {
    SwitchRow(
        label = stringResource(R.string.route_option_avoid_tolls),
        checked = options.avoidTolls,
        onCheckedChange = { onChange(options.copy(avoidTolls = it)) },
        tag = "trip_avoid_tolls",
    )
    SwitchRow(
        label = stringResource(R.string.route_option_avoid_motorways),
        checked = options.avoidMotorways,
        onCheckedChange = { onChange(options.copy(avoidMotorways = it)) },
        tag = "trip_avoid_motorways",
    )
    SwitchRow(
        label = stringResource(R.string.route_option_avoid_ferries),
        checked = options.avoidFerries,
        onCheckedChange = { onChange(options.copy(avoidFerries = it)) },
        tag = "trip_avoid_ferries",
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouchTarget)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(spacing.md))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun StopRow(
    index: Int,
    count: Int,
    stop: EditorStop,
    reorder: StopReorderState,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val name = stop.place.displayName()
    val itemDescription = stringResource(R.string.tripeditor_stop_item_description, index + 1, name)
    val moveUpLabel = stringResource(R.string.tripeditor_move_up)
    val moveDownLabel = stringResource(R.string.tripeditor_move_down)
    val removeDescription = stringResource(R.string.tripeditor_remove_description, name)
    val dragging = reorder.draggingKey == stop.key
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (dragging) 8.dp else 0.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.DragHandle,
                contentDescription = stringResource(R.string.tripeditor_drag_handle, name),
                modifier = Modifier
                    .size(Sizes.minTouchTarget)
                    .padding(spacing.md)
                    .pointerInput(stop.key) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { reorder.start(stop.key) },
                            onDrag = { change, amount ->
                                change.consume()
                                reorder.drag(amount.y)
                            },
                            onDragEnd = reorder::end,
                            onDragCancel = reorder::end,
                        )
                    }
                    .testTag("stop_handle_$index"),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = spacing.sm)
                    .semantics(mergeDescendants = true) {
                        contentDescription = itemDescription
                        customActions = buildList {
                            if (index > 0) add(CustomAccessibilityAction(moveUpLabel) { onMoveUp(); true })
                            if (index < count - 1) add(CustomAccessibilityAction(moveDownLabel) { onMoveDown(); true })
                        }
                    }
                    .testTag("stop_item_$index"),
            ) {
                Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                stop.place?.address?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.testTag("stop_up_$index")) {
                Icon(Icons.Outlined.ArrowUpward, contentDescription = stringResource(R.string.tripeditor_move_up_description, name))
            }
            IconButton(onClick = onMoveDown, enabled = index < count - 1, modifier = Modifier.testTag("stop_down_$index")) {
                Icon(Icons.Outlined.ArrowDownward, contentDescription = stringResource(R.string.tripeditor_move_down_description, name))
            }
            IconButton(onClick = onRemove, modifier = Modifier.testTag("stop_remove_$index")) {
                Icon(Icons.Outlined.Close, contentDescription = removeDescription)
            }
        }
    }
}
