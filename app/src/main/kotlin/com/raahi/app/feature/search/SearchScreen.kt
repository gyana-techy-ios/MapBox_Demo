package com.raahi.app.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raahi.app.R
import com.raahi.app.core.di.appViewModel
import com.raahi.app.domain.model.Place
import com.raahi.app.domain.model.PlaceCategory
import com.raahi.app.domain.model.RecentSearch
import com.raahi.app.domain.model.SearchSuggestion
import com.raahi.app.ui.components.CategoryChipRow
import com.raahi.app.ui.components.ConnectivityBanner
import com.raahi.app.ui.components.EmptyState
import com.raahi.app.ui.components.ErrorState
import com.raahi.app.ui.components.RaahiSearchField
import com.raahi.app.ui.components.SectionHeader
import com.raahi.app.ui.components.SkeletonList
import com.raahi.app.ui.components.SwipeToDeleteBox
import com.raahi.app.ui.components.icon
import com.raahi.app.ui.components.labelRes
import com.raahi.app.ui.format.formatDistance
import com.raahi.app.ui.theme.LocalRaahiColors
import com.raahi.app.ui.theme.LocalSpacing
import com.raahi.app.ui.theme.Sizes
import com.raahi.app.core.result.AppError

private val shortcutCategories = listOf(
    PlaceCategory.FUEL,
    PlaceCategory.HOSPITAL,
    PlaceCategory.PHARMACY,
    PlaceCategory.ATM,
    PlaceCategory.RESTAURANT,
    PlaceCategory.HOTEL,
    PlaceCategory.PARKING,
    PlaceCategory.EV_CHARGING,
    PlaceCategory.POLICE,
    PlaceCategory.TOILET,
)

@Composable
fun SearchRoute(
    onBack: () -> Unit,
    onOpenPlace: (Place) -> Unit,
    modifier: Modifier = Modifier,
    onOpenNearby: (PlaceCategory) -> Unit = {},
) {
    val viewModel = appViewModel {
        SearchViewModel(
            placeSearch = it.placeSearchRepository,
            recentSearches = it.recentSearchRepository,
            locationRepository = it.locationRepository,
            connectivity = it.connectivityMonitor,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SearchEvent.OpenPlace -> onOpenPlace(event.place)
                is SearchEvent.ShowMessage -> snackbarHost.showSnackbar(context.getString(event.messageRes))
            }
        }
    }

    Scaffold(
        modifier = modifier.testTag("screen_search"),
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .imePadding(),
        ) {
            val spacing = LocalSpacing.current
            RaahiSearchField(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                placeholder = stringResource(R.string.search_hint),
                onBack = onBack,
                onSearch = viewModel::onImeSearch,
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
                fieldModifier = Modifier.focusRequester(focusRequester).testTag("search_field"),
            )
            ConnectivityBanner(state = state.connectivity)
            if (state.isRetrieving) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            SearchBody(
                state = state,
                onSelectSuggestion = viewModel::select,
                onSelectRecent = viewModel::selectRecent,
                onDeleteRecent = viewModel::deleteRecent,
                onClearRecents = viewModel::clearRecents,
                onRetry = viewModel::retry,
                onOpenNearby = onOpenNearby,
            )
        }
    }
}

@Composable
private fun SearchBody(
    state: SearchUiState,
    onSelectSuggestion: (SearchSuggestion) -> Unit,
    onSelectRecent: (RecentSearch) -> Unit,
    onDeleteRecent: (Long) -> Unit,
    onClearRecents: () -> Unit,
    onRetry: () -> Unit,
    onOpenNearby: (PlaceCategory) -> Unit,
) {
    val spacing = LocalSpacing.current
    if (!state.isQueryLongEnough) {
        RecentsAndShortcuts(
            recents = state.recents,
            offlineMessage = false,
            onSelectRecent = onSelectRecent,
            onDeleteRecent = onDeleteRecent,
            onClearRecents = onClearRecents,
            onOpenNearby = onOpenNearby,
        )
        return
    }
    when (state.phase) {
        SearchPhase.Idle, SearchPhase.Loading ->
            SkeletonList(modifier = Modifier.padding(top = spacing.md).testTag("search_loading"), itemCount = 5)
        SearchPhase.Empty -> EmptyState(
            icon = Icons.Outlined.SearchOff,
            title = stringResource(R.string.search_no_results_title),
            body = stringResource(R.string.search_no_results_body, state.query.trim()),
            modifier = Modifier.testTag("search_no_results"),
        )
        SearchPhase.Error -> when (state.error) {
            AppError.NoInternet -> RecentsAndShortcuts(
                recents = state.recents,
                offlineMessage = true,
                onSelectRecent = onSelectRecent,
                onDeleteRecent = onDeleteRecent,
                onClearRecents = onClearRecents,
                onOpenNearby = onOpenNearby,
            )
            else -> ErrorState(
                error = state.error ?: AppError.Unknown(),
                onRetry = onRetry,
                modifier = Modifier.testTag("search_error"),
            )
        }
        SearchPhase.Results -> LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("search_suggestions"),
            contentPadding = PaddingValues(bottom = spacing.xl),
        ) {
            itemsIndexed(state.suggestions) { _, suggestion ->
                SuggestionRow(suggestion = suggestion, onClick = { onSelectSuggestion(suggestion) })
            }
        }
    }
}

@Composable
private fun RecentsAndShortcuts(
    recents: List<RecentSearch>,
    offlineMessage: Boolean,
    onSelectRecent: (RecentSearch) -> Unit,
    onDeleteRecent: (Long) -> Unit,
    onClearRecents: () -> Unit,
    onOpenNearby: (PlaceCategory) -> Unit,
) {
    val spacing = LocalSpacing.current
    val deleteLabel = stringResource(R.string.search_delete_recent)
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("search_recents_list"),
        contentPadding = PaddingValues(bottom = spacing.xl),
    ) {
        if (offlineMessage) {
            item(key = "offline_message") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.lg)
                        .testTag("search_offline_message"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.SearchOff, contentDescription = null, tint = LocalRaahiColors.current.offline)
                    Spacer(Modifier.width(spacing.md))
                    Text(
                        text = stringResource(R.string.search_needs_internet),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        item(key = "shortcuts_header") { SectionHeader(stringResource(R.string.search_shortcuts_header)) }
        item(key = "shortcuts") {
            CategoryChipRow(
                categories = shortcutCategories,
                selected = null,
                onCategoryClick = onOpenNearby,
                contentPadding = PaddingValues(horizontal = spacing.lg),
            )
        }
        if (recents.isEmpty()) {
            item(key = "recents_empty") {
                Column(modifier = Modifier.padding(spacing.lg).testTag("search_recents_empty")) {
                    Text(stringResource(R.string.search_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = stringResource(R.string.search_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            item(key = "recents_header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionHeader(stringResource(R.string.search_recent_header)) }
                    TextButton(
                        onClick = onClearRecents,
                        modifier = Modifier.heightIn(min = Sizes.minTouchTarget).testTag("search_clear_recents"),
                    ) { Text(stringResource(R.string.search_clear_all)) }
                }
            }
            items(recents, key = { "recent_${it.id}" }) { recent ->
                SwipeToDeleteBox(deleteLabel = deleteLabel, onDelete = { onDeleteRecent(recent.id) }) {
                    RecentRow(
                        recent = recent,
                        deleteLabel = deleteLabel,
                        onClick = { onSelectRecent(recent) },
                        onDelete = { onDeleteRecent(recent.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentRow(recent: RecentSearch, deleteLabel: String, onClick: () -> Unit, onDelete: () -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouchTarget + spacing.sm)
            .semantics(mergeDescendants = true) {}
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = recent.place?.name ?: recent.query,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            recent.place?.address?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.heightIn(min = Sizes.minTouchTarget)) {
            Icon(Icons.Outlined.Close, contentDescription = deleteLabel)
        }
    }
}

@Composable
private fun SuggestionRow(suggestion: SearchSuggestion, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouchTarget + spacing.sm)
            .semantics(mergeDescendants = true) {}
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = suggestion.category.icon(),
            contentDescription = stringResource(suggestion.category.labelRes()),
            tint = LocalRaahiColors.current.categoryColor(suggestion.category),
        )
        Spacer(Modifier.width(spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = suggestion.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            suggestion.address?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        suggestion.distanceMeters?.let {
            Spacer(Modifier.width(spacing.sm))
            Text(
                text = formatDistance(it),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
