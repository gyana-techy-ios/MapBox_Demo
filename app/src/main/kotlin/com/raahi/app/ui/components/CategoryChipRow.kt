package com.raahi.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.raahi.app.domain.model.PlaceCategory
import com.raahi.app.ui.theme.LocalRaahiColors
import com.raahi.app.ui.theme.LocalSpacing
import com.raahi.app.ui.theme.Sizes

@Composable
fun CategoryChipRow(
    categories: List<PlaceCategory>,
    selected: PlaceCategory?,
    onCategoryClick: (PlaceCategory) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val spacing = LocalSpacing.current
    val raahiColors = LocalRaahiColors.current
    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        items(categories, key = { it.name }) { category ->
            val isSelected = category == selected
            FilterChip(
                selected = isSelected,
                onClick = { onCategoryClick(category) },
                modifier = Modifier
                    .heightIn(min = Sizes.minTouchTarget)
                    .testTag("chip_${category.name}"),
                label = { Text(stringResource(category.labelRes())) },
                leadingIcon = {
                    Icon(
                        imageVector = category.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                        tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else raahiColors.categoryColor(category),
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
        }
    }
}
