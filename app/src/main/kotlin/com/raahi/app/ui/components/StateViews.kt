package com.raahi.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.raahi.app.R
import com.raahi.app.core.result.AppError
import com.raahi.app.core.result.toMessageRes
import com.raahi.app.ui.theme.LocalSpacing
import com.raahi.app.ui.theme.Sizes

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(Sizes.iconLarge),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(spacing.lg))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(spacing.sm))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(spacing.lg))
            PrimaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun ErrorState(
    error: AppError,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    EmptyState(
        icon = Icons.Outlined.ErrorOutline,
        title = stringResource(R.string.error_title),
        body = stringResource(error.toMessageRes()),
        modifier = modifier,
        actionLabel = onRetry?.let { stringResource(R.string.action_retry) },
        onAction = onRetry,
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    Text(
        text = title,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.sm)
            .semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun SkeletonList(modifier: Modifier = Modifier, itemCount: Int = 6) {
    val spacing = LocalSpacing.current
    val loadingDescription = stringResource(R.string.loading)
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha = transition.animateFloat(
        initialValue = SHIMMER_MIN_ALPHA,
        targetValue = SHIMMER_MAX_ALPHA,
        animationSpec = infiniteRepeatable(tween(SHIMMER_MILLIS, easing = LinearEasing), RepeatMode.Reverse),
        label = "skeleton_alpha",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = loadingDescription },
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        repeat(itemCount) {
            Row(
                modifier = Modifier.padding(horizontal = spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBlock(Modifier.size(40.dp).clip(CircleShape), alpha.value)
                Spacer(Modifier.width(spacing.md))
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(14.dp), alpha.value)
                    SkeletonBlock(Modifier.fillMaxWidth(0.9f).height(12.dp), alpha.value)
                }
            }
        }
    }
}

@Composable
private fun SkeletonBlock(modifier: Modifier, alphaValue: Float) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .alpha(alphaValue)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

private const val SHIMMER_MIN_ALPHA = 0.4f
private const val SHIMMER_MAX_ALPHA = 1f
private const val SHIMMER_MILLIS = 900
