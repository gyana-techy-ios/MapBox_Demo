package com.raahi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.raahi.app.domain.model.Place
import com.raahi.app.ui.format.formatDistance
import com.raahi.app.ui.theme.LocalRaahiColors
import com.raahi.app.ui.theme.LocalSpacing
import com.raahi.app.ui.theme.Sizes

@Composable
fun PlaceListItem(
    place: Place,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    val categoryColor = LocalRaahiColors.current.categoryColor(place.category)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouchTarget + spacing.sm)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(Sizes.minTouchTarget - spacing.sm)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = place.category.icon(),
                contentDescription = stringResource(place.category.labelRes()),
                tint = categoryColor,
            )
        }
        Spacer(Modifier.width(spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            place.address?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        place.distanceMeters?.let {
            Spacer(Modifier.width(spacing.sm))
            Text(
                text = formatDistance(it),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        trailing?.invoke()
    }
}

@Composable
fun PlaceCard(
    place: Place,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    val cardModifier = modifier.fillMaxWidth()
    val content: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(spacing.lg)) {
            PlaceHeader(place)
            actions?.let {
                Spacer(Modifier.size(spacing.md))
                it()
            }
        }
    }
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = cardModifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) { content() }
    } else {
        Card(
            modifier = cardModifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) { content() }
    }
}

@Composable
private fun PlaceHeader(place: Place) {
    val spacing = LocalSpacing.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = place.category.icon(),
            contentDescription = null,
            tint = LocalRaahiColors.current.categoryColor(place.category),
        )
        Spacer(Modifier.width(spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = place.name, style = MaterialTheme.typography.titleMedium)
            place.address?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        place.distanceMeters?.let {
            Text(
                text = formatDistance(it),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
