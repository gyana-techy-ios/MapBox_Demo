package com.raahi.app.feature.tripeditor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.raahi.app.R
import com.raahi.app.core.network.ConnectivityState
import com.raahi.app.core.result.toMessageRes
import com.raahi.app.domain.model.SavedPlace
import com.raahi.app.domain.model.SearchSuggestion
import com.raahi.app.ui.components.SkeletonList
import com.raahi.app.ui.theme.LocalSpacing
import com.raahi.app.ui.theme.Sizes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopPickerSheet(
    title: String,
    state: StopPickerUiState,
    onQueryChange: (String) -> Unit,
    onPickSuggestion: (SearchSuggestion) -> Unit,
    onPickSaved: (SavedPlace) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val showSaved = state.query.trim().length < StopPickerViewModel.MIN_QUERY_LENGTH ||
        state.phase == StopSearchPhase.Error
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = Modifier.testTag("stop_picker")) {
        Column(modifier = Modifier.fillMaxHeight(PICKER_HEIGHT_FRACTION).padding(horizontal = spacing.lg).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.stoppicker_close))
                }
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = { Text(stringResource(R.string.stoppicker_search_label)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("stop_picker_field"),
            )
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                if (state.phase == StopSearchPhase.Error) {
                    item(key = "error") {
                        val message = if (state.connectivity == ConnectivityState.Offline || state.error == com.raahi.app.core.result.AppError.NoInternet) {
                            stringResource(R.string.stoppicker_offline)
                        } else {
                            stringResource(state.error?.toMessageRes() ?: R.string.error_unknown)
                        }
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = spacing.md).testTag("stop_picker_error"),
                        )
                    }
                }
                when (state.phase) {
                    StopSearchPhase.Loading -> item(key = "loading") {
                        SkeletonList(itemCount = 3, modifier = Modifier.padding(top = spacing.md))
                    }
                    StopSearchPhase.Results -> items(state.suggestions, key = { "s_${it.id}" }) { suggestion ->
                        PlaceRow(
                            title = suggestion.name,
                            subtitle = suggestion.address,
                            icon = Icons.Outlined.LocationOn,
                            onClick = { onPickSuggestion(suggestion) },
                            tag = "stop_suggestion",
                        )
                    }
                    StopSearchPhase.Empty -> item(key = "empty") {
                        Text(
                            text = stringResource(R.string.stoppicker_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = spacing.md).testTag("stop_picker_empty"),
                        )
                    }
                    else -> Unit
                }
                if (showSaved) {
                    item(key = "saved_header") {
                        Text(
                            text = stringResource(R.string.stoppicker_saved_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = spacing.sm).semantics { heading() },
                        )
                    }
                    if (state.saved.isEmpty()) {
                        item(key = "saved_empty") {
                            Text(
                                text = stringResource(R.string.stoppicker_saved_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(state.saved, key = { "saved_${it.id}" }) { saved ->
                        PlaceRow(
                            title = saved.place.name,
                            subtitle = saved.place.address,
                            icon = Icons.Outlined.Bookmark,
                            onClick = { onPickSaved(saved) },
                            tag = "stop_saved_${saved.id}",
                        )
                    }
                }
                if (state.isRetrieving) {
                    item(key = "retrieving") {
                        Row(modifier = Modifier.padding(vertical = spacing.md), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(strokeWidth = spacing.xxs)
                            Spacer(Modifier.width(spacing.md))
                            Text(stringResource(R.string.loading))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(
    title: String,
    subtitle: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    tag: String,
) {
    val spacing = LocalSpacing.current
    val description = stringResource(R.string.stoppicker_pick_description, title)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouchTarget)
            .clickable(onClick = onClick)
            .padding(vertical = spacing.sm)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(spacing.md))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val PICKER_HEIGHT_FRACTION = 0.8f
