package com.raahi.app.feature.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raahi.app.R
import com.raahi.app.core.di.appViewModel
import com.raahi.app.ui.components.PermissionRationale
import com.raahi.app.ui.components.PrimaryButton
import com.raahi.app.ui.components.SecondaryButton
import com.raahi.app.ui.theme.LocalSpacing
import com.raahi.app.ui.util.findActivity
import com.raahi.app.ui.util.openAppSettings
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private data class IntroPage(
    val icon: ImageVector,
    @StringRes val title: Int,
    @StringRes val body: Int,
)

private val introPages = listOf(
    IntroPage(Icons.Outlined.Explore, R.string.onboarding_page1_title, R.string.onboarding_page1_body),
    IntroPage(Icons.Outlined.Route, R.string.onboarding_page2_title, R.string.onboarding_page2_body),
    IntroPage(Icons.Outlined.CloudDownload, R.string.onboarding_page3_title, R.string.onboarding_page3_body),
)

@Composable
fun OnboardingRoute(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = appViewModel {
        OnboardingViewModel(
            preferences = it.preferencesRepository,
            locationRepository = it.locationRepository,
            requiresNotificationStep = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val latestOnFinished by rememberUpdatedState(onFinished)
    LaunchedEffect(viewModel) {
        viewModel.finished.collect { latestOnFinished() }
    }

    Surface(modifier = modifier.fillMaxSize().testTag("screen_onboarding"), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
            },
            label = "onboarding_step",
        ) { step ->
            when (step) {
                OnboardingStep.INTRO -> IntroPager(onFinished = viewModel::onIntroFinished)
                OnboardingStep.LOCATION -> LocationStep(
                    outcome = state.locationOutcome,
                    onOutcome = viewModel::onLocationOutcome,
                    onContinue = viewModel::onLocationStepContinue,
                )
                OnboardingStep.NOTIFICATIONS -> NotificationStep(onHandled = viewModel::onNotificationsHandled)
            }
        }
    }
}

@Composable
private fun IntroPager(onFinished: () -> Unit) {
    val spacing = LocalSpacing.current
    val pagerState = rememberPagerState(pageCount = { introPages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == introPages.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(spacing.xl),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!isLastPage) {
                TextButton(onClick = onFinished, modifier = Modifier.testTag("onboarding_skip")) {
                    Text(stringResource(R.string.onboarding_skip))
                }
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
            IntroPageContent(page = introPages[index], pagerState = pagerState, index = index)
        }
        PageIndicator(count = introPages.size, current = pagerState.currentPage)
        Spacer(Modifier.height(spacing.xl))
        PrimaryButton(
            text = stringResource(if (isLastPage) R.string.onboarding_get_started else R.string.onboarding_next),
            onClick = {
                if (isLastPage) onFinished() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            },
            modifier = Modifier.fillMaxWidth().testTag("onboarding_next"),
        )
    }
}

@Composable
private fun IntroPageContent(page: IntroPage, pagerState: PagerState, index: Int) {
    val spacing = LocalSpacing.current
    val offset = ((pagerState.currentPage - index) + pagerState.currentPageOffsetFraction).absoluteValue
    Column(
        modifier = Modifier
            .fillMaxSize()
            .alpha(1f - offset.coerceIn(0f, 1f) * 0.6f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(176.dp)
                .scale(1f - offset.coerceIn(0f, 1f) * 0.2f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                modifier = Modifier.size(88.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(spacing.xxl))
        Text(
            text = stringResource(page.title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(spacing.md))
        Text(
            text = stringResource(page.body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PageIndicator(count: Int, current: Int) {
    val spacing = LocalSpacing.current
    val description = stringResource(R.string.onboarding_page_indicator, current + 1, count)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { index ->
            val selected = index == current
            val width = animateDpAsState(if (selected) 28.dp else 8.dp, label = "dot_width")
            val color = animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "dot_color",
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = spacing.xs)
                    .height(8.dp)
                    .width(width.value)
                    .clip(CircleShape)
                    .background(color.value),
            )
        }
    }
}

@Composable
private fun LocationStep(
    outcome: LocationOutcome,
    onOutcome: (LocationOutcome) -> Unit,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val spacing = LocalSpacing.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val activity = context.findActivity()
        val canShowRationale = activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        onOutcome(classifyLocationResult(fine, coarse, canShowRationale))
    }
    val requestPermissions = {
        launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    StepScaffold(
        icon = Icons.Outlined.MyLocation,
        title = stringResource(R.string.onboarding_location_title),
        body = stringResource(R.string.onboarding_location_body),
        modifier = Modifier.testTag("onboarding_location_step"),
    ) {
        Text(
            text = stringResource(R.string.onboarding_location_precise_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(spacing.xl))
        when (outcome) {
            LocationOutcome.NOT_ASKED -> {
                PrimaryButton(
                    text = stringResource(R.string.onboarding_location_allow),
                    onClick = { requestPermissions() },
                    modifier = Modifier.fillMaxWidth().testTag("onboarding_location_allow"),
                )
                Spacer(Modifier.height(spacing.sm))
                SecondaryButton(
                    text = stringResource(R.string.onboarding_location_skip),
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().testTag("onboarding_location_skip"),
                )
            }
            LocationOutcome.PRECISE -> OutcomeCard(R.string.onboarding_location_precise_granted, onContinue)
            LocationOutcome.APPROXIMATE -> OutcomeCard(R.string.onboarding_location_approximate_granted, onContinue)
            LocationOutcome.DENIED -> {
                PermissionRationale(
                    title = stringResource(R.string.onboarding_location_denied_title),
                    body = stringResource(R.string.onboarding_location_denied_body),
                    primaryLabel = stringResource(R.string.onboarding_location_try_again),
                    onPrimary = { requestPermissions() },
                    secondaryLabel = stringResource(R.string.onboarding_location_skip),
                    onSecondary = onContinue,
                )
            }
            LocationOutcome.PERMANENTLY_DENIED -> {
                PermissionRationale(
                    title = stringResource(R.string.onboarding_location_denied_title),
                    body = stringResource(R.string.onboarding_location_blocked_body),
                    primaryLabel = stringResource(R.string.onboarding_open_settings),
                    onPrimary = { context.openAppSettings() },
                    secondaryLabel = stringResource(R.string.onboarding_location_skip),
                    onSecondary = onContinue,
                )
            }
        }
    }
}

@Composable
private fun OutcomeCard(@StringRes message: Int, onContinue: () -> Unit) {
    val spacing = LocalSpacing.current
    Text(
        text = stringResource(message),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(spacing.lg))
    PrimaryButton(
        text = stringResource(R.string.onboarding_continue),
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth().testTag("onboarding_location_continue"),
    )
}

@Composable
private fun NotificationStep(onHandled: () -> Unit) {
    val spacing = LocalSpacing.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onHandled() }
    StepScaffold(
        icon = Icons.Outlined.Notifications,
        title = stringResource(R.string.onboarding_notifications_title),
        body = stringResource(R.string.onboarding_notifications_body),
        modifier = Modifier.testTag("onboarding_notifications_step"),
    ) {
        PrimaryButton(
            text = stringResource(R.string.onboarding_notifications_allow),
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onHandled()
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("onboarding_notifications_allow"),
        )
        Spacer(Modifier.height(spacing.sm))
        SecondaryButton(
            text = stringResource(R.string.onboarding_notifications_skip),
            onClick = onHandled,
            modifier = Modifier.fillMaxWidth().testTag("onboarding_notifications_skip"),
        )
    }
}

@Composable
private fun StepScaffold(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(Modifier.height(spacing.xl))
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(spacing.md))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.lg))
        actions()
    }
}
