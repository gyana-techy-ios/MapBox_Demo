package com.raahi.app.feature.explore

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.raahi.app.R
import com.raahi.app.domain.model.Place
import com.raahi.app.ui.components.EmptyState
import com.raahi.app.ui.components.ErrorState
import com.raahi.app.ui.components.PlaceListItem
import com.raahi.app.ui.components.SkeletonList
import com.raahi.app.ui.components.labelRes
import com.raahi.app.ui.format.formatCachedAge
import com.raahi.app.ui.theme.LocalRaahiColors
import com.raahi.app.ui.theme.LocalSpacing

@Composable
internal fun ColumnScope.ExploreResultsSheet(
    state: ExploreUiState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onOpenPlace: (Place) -> Unit,
    nowMillis: () -> Long,
) {
    val category = state.selectedCategory ?: return
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = spacing.lg, end = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.explore_results_title, stringResource(category.labelRes()), state.results.size),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClose, modifier = Modifier.testTag("explore_results_close")) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.explore_close_results))
        }
    }
    state.cachedAtMillis?.let { cachedAt ->
        Row(
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.History, contentDescription = null, tint = LocalRaahiColors.current.offline)
            Text(
                text = stringResource(R.string.explore_cached_results, formatCachedAge(cachedAt, nowMillis())),
                modifier = Modifier.padding(start = spacing.sm),
                style = MaterialTheme.typography.bodySmall,
                color = LocalRaahiColors.current.offline,
            )
        }
    }
    HorizontalDivider()
    when {
        state.isLoading -> SkeletonList(modifier = Modifier.padding(vertical = spacing.md), itemCount = 4)
        state.error != null -> ErrorState(error = state.error, onRetry = onRetry, modifier = Modifier.heightIn(max = 280.dp))
        state.results.isEmpty() -> EmptyState(
            icon = Icons.Outlined.SearchOff,
            title = stringResource(R.string.explore_no_results_title),
            body = stringResource(R.string.explore_no_results_body, stringResource(category.labelRes())),
            modifier = Modifier.heightIn(max = 280.dp),
        )
        else -> LazyColumn(modifier = Modifier.heightIn(max = 420.dp).testTag("explore_results_list")) {
            items(state.results, key = { it.id }) { place ->
                PlaceListItem(place = place, onClick = { onOpenPlace(place) })
            }
        }
    }
}
