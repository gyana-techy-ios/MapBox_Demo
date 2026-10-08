package com.raahi.app.navigation

import com.raahi.app.ui.util.findActivity
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.util.Consumer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.raahi.app.core.di.AppContainer
import com.raahi.app.core.di.LocalAppContainer
import com.raahi.app.domain.model.ThemeMode
import com.raahi.app.domain.model.UserPreferences
import com.raahi.app.map.LocalMapboxConfig
import com.raahi.app.ui.theme.RaahiAppTheme

@Composable
fun RaahiApp(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) {
    DeepLinkIntentEffect(navController)
    CompositionLocalProvider(
        LocalAppContainer provides container,
        LocalMapboxConfig provides container.mapboxConfig,
    ) {
        val preferences by container.preferencesRepository.preferences
            .collectAsStateWithLifecycle(initialValue = null as UserPreferences?)
        var startDestination by remember { mutableStateOf<Any?>(null) }
        val loaded = preferences
        if (startDestination == null && loaded != null) {
            startDestination = if (loaded.onboardingDone) Explore else Onboarding
        }
        RaahiAppTheme(themeMode = loaded?.themeMode ?: ThemeMode.SYSTEM) {
            val start = startDestination
            if (start == null) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("app_loading"))
            } else {
                RaahiScaffold(navController = navController, startDestination = start)
            }
        }
    }
}

@Composable
private fun DeepLinkIntentEffect(navController: NavHostController) {
    val activity = LocalContext.current.findActivity() as? ComponentActivity ?: return
    DisposableEffect(activity, navController) {
        val listener = Consumer<Intent> { intent -> navController.handleDeepLink(intent) }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }
}

@Composable
private fun RaahiScaffold(navController: NavHostController, startDestination: Any) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBottomBar = destination.showsBottomBar()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                RaahiBottomBar(
                    isSelected = { destination.isSelected(it) },
                    onSelect = { tab -> navController.navigateToTab(tab) },
                )
            }
        },
    ) { innerPadding ->
        val contentModifier = if (showBottomBar) {
            Modifier
                .padding(bottom = innerPadding.calculateBottomPadding())
                .consumeWindowInsets(WindowInsets.navigationBars)
        } else {
            Modifier
        }
        RaahiNavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = contentModifier,
        )
    }
}

@Composable
private fun RaahiBottomBar(isSelected: (TopLevelTab) -> Boolean, onSelect: (TopLevelTab) -> Unit) {
    NavigationBar(modifier = Modifier.testTag("bottom_bar")) {
        TopLevelTab.entries.forEach { tab ->
            val label = stringResource(tab.labelRes)
            NavigationBarItem(
                selected = isSelected(tab),
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(label) },
                modifier = Modifier.testTag(tab.testTag),
            )
        }
    }
}

fun NavHostController.navigateToTab(tab: TopLevelTab) {
    if (tab == TopLevelTab.EXPLORE) {
        popBackStack<Explore>(inclusive = false)
        return
    }
    navigate(tab.route) {
        popUpTo<Explore> { saveState = false }
        launchSingleTop = true
    }
}
