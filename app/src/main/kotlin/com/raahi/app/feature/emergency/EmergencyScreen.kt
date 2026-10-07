package com.raahi.app.feature.emergency

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raahi.app.R
import com.raahi.app.core.di.LocalAppContainer
import com.raahi.app.core.di.appViewModel
import com.raahi.app.core.result.toMessageRes
import com.raahi.app.domain.model.EmergencyContact
import com.raahi.app.domain.model.LocationPermissionState
import com.raahi.app.domain.model.Place
import com.raahi.app.ui.components.ConnectivityBanner
import com.raahi.app.ui.components.RaahiTopBar
import com.raahi.app.ui.components.SecondaryButton
import com.raahi.app.ui.components.SectionHeader
import com.raahi.app.ui.components.SkeletonList
import com.raahi.app.ui.components.icon
import com.raahi.app.ui.components.labelRes
import com.raahi.app.ui.format.formatCachedAge
import com.raahi.app.ui.format.formatDistance
import com.raahi.app.ui.theme.LocalRaahiColors
import com.raahi.app.ui.theme.LocalSpacing
import com.raahi.app.ui.theme.Sizes
import com.raahi.app.ui.util.PlaceLinks
import com.raahi.app.ui.util.dialNumber
import com.raahi.app.ui.util.openAppSettings
import com.raahi.app.ui.util.openLocationSettings
import com.raahi.app.ui.util.shareText
import kotlinx.coroutines.launch

private const val NATIONAL_EMERGENCY_NUMBER = "112"
private const val AMBULANCE_NUMBER = "108"

@Composable
fun EmergencyRoute(
    onBack: () -> Unit,
    onOpenPlace: (Place) -> Unit,
    onOpenContacts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = appViewModel {
        EmergencyViewModel(
            findNearest = it.findNearestEmergencyUseCase,
            contacts = it.emergencyContactRepository,
            locationRepository = it.locationRepository,
            connectivity = it.connectivityMonitor,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showLocationHelp by remember { mutableStateOf(false) }
    val shareHeader = stringResource(R.string.emergency_share_header)
    val shareChooser = stringResource(R.string.emergency_share_chooser)
    val dialFailed = stringResource(R.string.emergency_dial_failed)

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        viewModel.onPermissionResult(grants.values.any { it })
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is EmergencyEvent.ShareLocation -> context.shareText(PlaceLinks.locationShareText(shareHeader, event.point), shareChooser)
                EmergencyEvent.ShowLocationHelp -> showLocationHelp = true
            }
        }
    }

    val dial: (String) -> Unit = { number ->
        if (!context.dialNumber(number)) {
            scope.launch { snackbarHost.showSnackbar(dialFailed) }
        }
    }

    EmergencyContent(
        state = state,
        snackbarHost = snackbarHost,
        onBack = onBack,
        onDial = dial,
        onShareLocation = {
            if (state.permission == LocationPermissionState.DENIED) {
                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            } else {
                viewModel.shareLocation()
            }
        },
        onSelectCategory = viewModel::selectCategory,
        onReloadServices = viewModel::reloadServices,
        onOpenPlace = onOpenPlace,
        onOpenContacts = onOpenContacts,
        modifier = modifier,
    )

    if (showLocationHelp) {
        LocationHelpDialog(
            permissionDenied = state.permission == LocationPermissionState.DENIED,
            onRetry = {
                showLocationHelp = false
                viewModel.shareLocation()
            },
            onOpenSettings = {
                showLocationHelp = false
                if (state.permission == LocationPermissionState.DENIED) context.openAppSettings() else context.openLocationSettings()
            },
            onDismiss = { showLocationHelp = false },
        )
    }
}

@Composable
private fun EmergencyContent(
    state: EmergencyUiState,
    snackbarHost: SnackbarHostState,
    onBack: () -> Unit,
    onDial: (String) -> Unit,
    onShareLocation: () -> Unit,
    onSelectCategory: (com.raahi.app.domain.model.PlaceCategory) -> Unit,
    onReloadServices: () -> Unit,
    onOpenPlace: (Place) -> Unit,
    onOpenContacts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Scaffold(
        modifier = modifier.testTag("screen_emergency"),
        topBar = { RaahiTopBar(title = stringResource(R.string.emergency_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).testTag("emergency_list"),
            contentPadding = PaddingValues(bottom = spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item(key = "banner") { ConnectivityBanner(state = state.connectivity) }
            item(key = "intro") {
                Text(
                    text = stringResource(R.string.emergency_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = spacing.lg),
                )
            }
            item(key = "dial") {
                Row(
                    modifier = Modifier.padding(horizontal = spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    DialButton(
                        number = NATIONAL_EMERGENCY_NUMBER,
                        label = stringResource(R.string.emergency_dial_112),
                        onDial = onDial,
                        modifier = Modifier.weight(1f).testTag("emergency_dial_112"),
                    )
                    DialButton(
                        number = AMBULANCE_NUMBER,
                        label = stringResource(R.string.emergency_dial_108),
                        onDial = onDial,
                        modifier = Modifier.weight(1f).testTag("emergency_dial_108"),
                    )
                }
            }
            item(key = "share") {
                SecondaryButton(
                    text = stringResource(R.string.emergency_share_location),
                    onClick = onShareLocation,
                    icon = Icons.Outlined.Share,
                    enabled = !state.isResolvingLocation,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg).testTag("emergency_share_location"),
                )
            }
            item(key = "contacts_header") { SectionHeader(stringResource(R.string.emergency_contacts_header)) }
            contactsSection(state.contacts, onDial, onOpenContacts)
            item(key = "services_header") { SectionHeader(stringResource(R.string.emergency_nearby_header)) }
            item(key = "services") {
                NearestServices(state = state, onSelectCategory = onSelectCategory, onReload = onReloadServices, onOpenPlace = onOpenPlace, onDial = onDial)
            }
        }
    }
}

@Composable
private fun DialButton(number: String, label: String, onDial: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalRaahiColors.current
    val spacing = LocalSpacing.current
    val description = stringResource(R.string.emergency_dial_description, label, number)
    Button(
        onClick = { onDial(number) },
        modifier = modifier
            .heightIn(min = 112.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        colors = ButtonDefaults.buttonColors(containerColor = colors.emergency, contentColor = colors.onEmergency),
        contentPadding = PaddingValues(spacing.md),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Call, contentDescription = null, modifier = Modifier.size(Sizes.icon))
            Text(text = number, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
            Text(text = label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.contactsSection(
    contacts: List<EmergencyContact>,
    onDial: (String) -> Unit,
    onOpenContacts: () -> Unit,
) {
    if (contacts.isEmpty()) {
        item(key = "contacts_empty") {
            Text(
                text = stringResource(R.string.emergency_contacts_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp).testTag("emergency_contacts_empty"),
            )
        }
    } else {
        contacts.forEach { contact ->
            item(key = "contact_${contact.id}") { ContactRow(contact, onDial) }
        }
    }
    item(key = "contacts_manage") {
        SecondaryButton(
            text = stringResource(R.string.emergency_action_contacts),
            onClick = onOpenContacts,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("emergency_manage_contacts"),
        )
    }
}

@Composable
private fun ContactRow(contact: EmergencyContact, onDial: (String) -> Unit) {
    val spacing = LocalSpacing.current
    val callDescription = stringResource(R.string.emergency_call_person, contact.name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouchTarget + spacing.sm)
            .semantics(mergeDescendants = true) {}
            .padding(start = spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(contact.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(contact.phone, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(
            onClick = { onDial(contact.phone) },
            modifier = Modifier.heightIn(min = Sizes.minTouchTarget).testTag("emergency_call_contact_${contact.id}"),
        ) {
            Icon(Icons.Outlined.Call, contentDescription = callDescription)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NearestServices(
    state: EmergencyUiState,
    onSelectCategory: (com.raahi.app.domain.model.PlaceCategory) -> Unit,
    onReload: () -> Unit,
    onOpenPlace: (Place) -> Unit,
    onDial: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    val categories = EmergencyServiceCategories
    val selectedIndex = categories.indexOf(state.selectedCategory).coerceAtLeast(0)
    Column(modifier = Modifier.testTag("emergency_services")) {
        PrimaryTabRow(selectedTabIndex = selectedIndex) {
            categories.forEach { category ->
                Tab(
                    selected = category == state.selectedCategory,
                    onClick = { onSelectCategory(category) },
                    modifier = Modifier.heightIn(min = Sizes.minTouchTarget).testTag("emergency_tab_${category.name}"),
                    text = { Text(stringResource(category.labelRes())) },
                    icon = { Icon(category.icon(), contentDescription = null) },
                )
            }
        }
        when (val list = state.services[state.selectedCategory] ?: ServiceListState.Loading) {
            ServiceListState.Loading -> SkeletonList(modifier = Modifier.padding(top = spacing.md), itemCount = 3)
            ServiceListState.NeedsLocation -> ServicesMessage(
                icon = Icons.Outlined.LocationOff,
                text = stringResource(R.string.emergency_services_need_location),
                actionLabel = stringResource(R.string.action_retry),
                onAction = onReload,
                tag = "emergency_services_need_location",
            )
            is ServiceListState.Failed -> ServicesMessage(
                icon = Icons.Outlined.LocationOff,
                text = stringResource(list.error.toMessageRes()),
                actionLabel = stringResource(R.string.action_retry),
                onAction = onReload,
                tag = "emergency_services_error",
            )
            is ServiceListState.Loaded -> {
                list.cachedAtMillis?.let { CachedNote(it) }
                if (list.places.isEmpty()) {
                    ServicesMessage(
                        icon = Icons.Outlined.LocationOff,
                        text = stringResource(R.string.emergency_services_none, stringResource(state.selectedCategory.labelRes())),
                        actionLabel = null,
                        onAction = null,
                        tag = "emergency_services_none",
                    )
                } else {
                    list.places.forEach { place -> ServiceRow(place, onOpenPlace, onDial) }
                }
            }
        }
    }
}

@Composable
private fun CachedNote(cachedAtMillis: Long) {
    val spacing = LocalSpacing.current
    val clock = LocalAppContainer.current.clock
    Row(
        modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm).testTag("emergency_cached_label"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.History, contentDescription = null, tint = LocalRaahiColors.current.offline)
        Text(
            text = stringResource(R.string.emergency_cached_results, formatCachedAge(cachedAtMillis, clock.nowMillis())),
            modifier = Modifier.padding(start = spacing.sm),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalRaahiColors.current.offline,
        )
    }
}

@Composable
private fun ServicesMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    tag: String,
) {
    val spacing = LocalSpacing.current
    Column(modifier = Modifier.fillMaxWidth().padding(spacing.lg).testTag(tag), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(spacing.sm))
        Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, modifier = Modifier.heightIn(min = Sizes.minTouchTarget)) { Text(actionLabel) }
        }
    }
}

@Composable
private fun ServiceRow(place: Place, onOpenPlace: (Place) -> Unit, onDial: (String) -> Unit) {
    val spacing = LocalSpacing.current
    val callDescription = stringResource(R.string.emergency_call_person, place.name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouchTarget + spacing.sm)
            .semantics(mergeDescendants = true) {}
            .clickable(role = Role.Button, onClick = { onOpenPlace(place) })
            .padding(start = spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = spacing.sm)) {
            Text(place.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            place.address?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        place.distanceMeters?.let {
            Spacer(Modifier.width(spacing.sm))
            Text(formatDistance(it), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (place.phone != null) {
            IconButton(onClick = { onDial(place.phone) }, modifier = Modifier.heightIn(min = Sizes.minTouchTarget)) {
                Icon(Icons.Outlined.Call, contentDescription = callDescription)
            }
        } else {
            Spacer(Modifier.width(spacing.lg))
        }
    }
}

@Composable
private fun LocationHelpDialog(
    permissionDenied: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.emergency_location_help_title)) },
        text = {
            Text(
                stringResource(
                    if (permissionDenied) R.string.emergency_location_help_permission else R.string.emergency_location_help_unavailable,
                ),
            )
        },
        confirmButton = {
            Row {
                TextButton(onClick = onRetry, modifier = Modifier.testTag("emergency_location_retry")) {
                    Text(stringResource(R.string.action_retry))
                }
                TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.action_settings)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
