package com.raahi.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Traffic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.raahi.app.R
import com.raahi.app.domain.model.RouteProfile
import com.raahi.app.ui.theme.Sizes

fun RouteProfile.icon(): ImageVector = when (this) {
    RouteProfile.DRIVING_TRAFFIC -> Icons.Outlined.Traffic
    RouteProfile.DRIVING -> Icons.Outlined.DirectionsCar
    RouteProfile.WALKING -> Icons.Outlined.DirectionsWalk
    RouteProfile.CYCLING -> Icons.Outlined.DirectionsBike
}

@StringRes
fun RouteProfile.labelRes(): Int = when (this) {
    RouteProfile.DRIVING_TRAFFIC -> R.string.route_profile_driving_traffic
    RouteProfile.DRIVING -> R.string.route_profile_driving
    RouteProfile.WALKING -> R.string.route_profile_walking
    RouteProfile.CYCLING -> R.string.route_profile_cycling
}

@StringRes
private fun RouteProfile.shortLabelRes(): Int = when (this) {
    RouteProfile.DRIVING_TRAFFIC -> R.string.route_profile_short_driving_traffic
    RouteProfile.DRIVING -> R.string.route_profile_short_driving
    RouteProfile.WALKING -> R.string.route_profile_short_walking
    RouteProfile.CYCLING -> R.string.route_profile_short_cycling
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteProfileSelector(
    selected: RouteProfile,
    onSelect: (RouteProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val profiles = RouteProfile.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        profiles.forEachIndexed { index, profile ->
            val description = stringResource(profile.labelRes())
            SegmentedButton(
                selected = profile == selected,
                onClick = { onSelect(profile) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = profiles.size),
                icon = {},
                modifier = Modifier
                    .heightIn(min = Sizes.minTouchTarget)
                    .semantics { contentDescription = description }
                    .testTag("profile_option_${profile.name}"),
            ) {
                Text(stringResource(profile.shortLabelRes()))
            }
        }
    }
}
