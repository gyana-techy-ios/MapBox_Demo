# Raahi: Design System

**Date:** October 2026  
**Status:** MVP Specification  
**Version:** 1.0

---

## 1. Color System

### 1.1 Brand Palette

Raahi uses a deep teal primary (symbolizing travel, maps, and trust) with saffron accents (symbolizing India and spirituality). Emergency red is reserved for critical actions. All colors are defined as design tokens and **never hardcoded**.

**Primary Brand Colors:**

```
Deep Teal (Primary)
  Light: #1B9987
  Dark:  #0F5B51
  (Material 3 will interpolate primary, primaryContainer, etc.)

Saffron (Accent / Secondary)
  Light: #FF9E3C
  Dark:  #FF7A1A

Emergency Red
  Light: #E84C4C
  Dark:  #D32F2F

Offline Amber (Caution)
  Light: #FFC107
  Dark:  #FFA000

Success Green
  Light: #4CAF50
  Dark:  #2E7D32
```

### 1.2 Material 3 ColorScheme (Light and Dark)

All colors in the app come from `MaterialTheme.colorScheme.*` or custom `CompositionLocal` tokens. No literal hex values in code.

**Light Mode:**
```kotlin
// Light color scheme (Material 3 Default theme)
val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1B9987),           // Deep Teal
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB0EFE7),
    onPrimaryContainer = Color(0xFF002620),
    
    secondary = Color(0xFF4A6741),         // Green
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCEDC1),
    onSecondaryContainer = Color(0xFF0C2006),
    
    tertiary = Color(0xFF386666),          // Blue-teal
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBBEBED),
    onTertiaryContainer = Color(0xFF002122),
    
    error = Color(0xFFD32F2F),             // Emergency Red
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    
    background = Color.White,
    onBackground = Color(0xFF1C1B1F),
    
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFEAE2E4),
    onSurfaceVariant = Color(0xFF49454E),
    
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC7CF),
    scrim = Color.Black
)
```

**Dark Mode:**
```kotlin
val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7FD8CC),           // Lighter Teal for dark
    onPrimary = Color(0xFF003C35),
    primaryContainer = Color(0xFF00544B),
    onPrimaryContainer = Color(0xFFB0EFE7),
    
    secondary = Color(0xFFB0D4A5),
    onSecondary = Color(0xFF1D380F),
    secondaryContainer = Color(0xFF304F20),
    onSecondaryContainer = Color(0xFFCCEDC1),
    
    tertiary = Color(0xFFA0D0D1),
    onTertiary = Color(0xFF003738),
    tertiaryContainer = Color(0xFF1E4E4E),
    onTertiaryContainer = Color(0xFFBBEBED),
    
    error = Color(0xFFE84C4C),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E6),
    
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E6),
    surfaceVariant = Color(0xFF49454E),
    onSurfaceVariant = Color(0xFFCAC7CF),
    
    outline = Color(0xFF94909B),
    outlineVariant = Color(0xFF49454E),
    scrim = Color.Black
)
```

### 1.3 Custom Color Tokens (CompositionLocal)

For colors not in Material 3 default (e.g., Saffron accent, offline amber):

```kotlin
data class RaahiColors(
    val saffronLight: Color = Color(0xFF FF9E3C),
    val saffronDark: Color = Color(0xFFFF7A1A),
    val offlineAmber: Color = Color(0xFFFFC107),
    val successGreen: Color = Color(0xFF4CAF50),
    val warningOrange: Color = Color(0xFFFF6F00),
    val mapCluster1: Color = Color(0xFF1B9987),  // Primary (few items)
    val mapCluster2: Color = Color(0xFFFF9E3C), // Saffron (moderate)
    val mapCluster3: Color = Color(0xFFE84C4C)  // Red (many items)
)

val LocalRaahiColors = staticCompositionLocalOf { RaahiColors() }

@Composable
fun RaahiTheme(
    darkTheme: Boolean = isSystemInDarkMode(),
    content: @Composable () -> Unit
) {
    val raahiColors = if (darkTheme) {
        RaahiColors(
            saffronLight = Color(0xFFFF7A1A),
            saffronDark = Color(0xFFFF5722),
            offlineAmber = Color(0xFFFFA000),
            // ... etc
        )
    } else {
        RaahiColors(
            saffronLight = Color(0xFFFF9E3C),
            saffronDark = Color(0xFFFF7A1A),
            offlineAmber = Color(0xFFFFC107),
            // ... etc
        )
    }

    CompositionLocalProvider(LocalRaahiColors provides raahiColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = RaahiTypography,
            shapes = RaahiShapes,
            content = content
        )
    }
}

// Usage:
@Composable
fun CategoryChip(label: String, isSelected: Boolean) {
    val colors = LocalRaahiColors.current
    Button(
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primary 
            else 
                colors.saffronLight
        )
    ) {
        Text(label)
    }
}
```

---

## 2. Typography

### 2.1 Type Scale

Material 3 type scale, using `sp` units (scale-independent pixels; respects system font size setting).

```kotlin
val RaahiTypography = Typography(
    // Display: largest, for headers or emphasis
    displayLarge = TextStyle(
        fontSize = 57.sp,
        lineHeight = 64.sp,
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontSize = 45.sp,
        lineHeight = 52.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontSize = 36.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp
    ),

    // Headline: large text for important content
    headlineLarge = TextStyle(
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp
    ),

    // Title: medium-size text, common in lists and cards
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp
    ),

    // Body: regular text
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.4.sp
    ),

    // Label: buttons, chips, tags
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp
    )
)
```

---

## 3. Spacing and Layout

### 3.1 Spacing Scale

All spacing uses a consistent 4 dp base unit:

```kotlin
object Spacing {
    val xxs = 2.dp    // smallest gaps
    val xs = 4.dp
    val sm = 8.dp     // small margins
    val md = 12.dp    // medium margins
    val lg = 16.dp    // standard margin
    val xl = 24.dp    // large gap
    val xxl = 32.dp   // extra-large gap
    val xxxl = 48.dp  // full spacing for sections
}

// Usage:
@Composable
fun PlaceCard(place: Place) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.lg)  // 16 dp
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg)  // 16 dp inner padding
        ) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(Spacing.sm))  // 8 dp
            Text(
                text = place.address,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(Spacing.md))  // 12 dp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md),  // 12 dp
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)  // 8 dp between buttons
            ) {
                Button(onClick = { }) { Text("Navigate") }
                Button(onClick = { }) { Text("Save") }
            }
        }
    }
}
```

### 3.2 Responsive Layouts

**Edge-to-edge:** All screens use edge-to-edge (no system insets applied automatically).

```kotlin
@Composable
fun ExploreScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()  // Apply padding for system bars
    ) {
        // Content here
    }
}
```

---

## 4. Shapes and Elevation

### 4.1 Shape Tokens

```kotlin
val RaahiShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),    // Chips, small buttons
    small = RoundedCornerShape(8.dp),         // Standard buttons, text fields
    medium = RoundedCornerShape(12.dp),       // Cards, dialogs
    large = RoundedCornerShape(16.dp),        // Sheets, bottom nav
    extraLarge = RoundedCornerShape(28.dp)    // FAB, corner radiuses
)
```

### 4.2 Elevation (Material 3)

Elevation is used sparingly to convey hierarchy:

```kotlin
// Level 0 (no elevation): background, surface (same level)
val surface = Color.White

// Level 1 (+1 dp): cards, text fields
Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp))

// Level 2 (+3 dp): buttons, chips (hover state)
Button(elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 3.dp))

// Level 3 (+6 dp): FAB
FloatingActionButton(elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp))

// Level 4 (+12 dp): dialogs, modals
Dialog(properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 12.dp
    )
}
```

---

## 5. Component Library

### 5.1 RaahiSearchBar

A custom search bar with clear button and recent searches.

```kotlin
@Composable
fun RaahiSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    recentSearches: List<String> = emptyList(),
    onRecentSearchSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = Spacing.lg),
            placeholder = { Text("Search places...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query) })
        )

        if (recentSearches.isNotEmpty() && query.isEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(recentSearches) { search ->
                    Text(
                        text = search,
                        modifier = Modifier
                            .clickable { onRecentSearchSelected(search) }
                            .padding(Spacing.sm),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
```

### 5.2 CategoryChipRow

Horizontally scrollable row of category chips.

```kotlin
@Composable
fun CategoryChipRow(
    categories: List<String> = listOf(
        "Fuel", "EV", "Hospital", "Pharmacy", "Police", "ATM", "Food", "Hotel"
    ),
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        items(categories) { category ->
            FilterChip(
                selected = false,  // Track selected state in parent
                onClick = { onCategorySelected(category) },
                label = { Text(category) },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.height(36.dp)
            )
        }
    }
}
```

### 5.3 PlaceCard

Card displaying a place with name, address, distance, and action buttons.

```kotlin
@Composable
fun PlaceCard(
    place: Place,
    onNavigate: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.lg),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg)
        ) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = place.address ?: "Address not available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            if (place.distance != null) {
                Text(
                    text = "${String.format("%.1f", place.distance / 1000)} km away",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(modifier = Modifier.height(Spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Button(
                    onClick = onNavigate,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                ) {
                    Text("Navigate")
                }
                IconButton(onClick = onSave, modifier = Modifier.size(40.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bookmark),
                        contentDescription = "Save place",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onShare, modifier = Modifier.size(40.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_share),
                        contentDescription = "Share place",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
```

### 5.4 EmptyState and ErrorState

```kotlin
@Composable
fun EmptyState(
    icon: Painter,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(Spacing.lg))
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun ErrorState(
    title: String = "Something went wrong",
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_error),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(Spacing.lg))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}
```

### 5.5 ConnectivityBanner

Banner shown when offline.

```kotlin
@Composable
fun ConnectivityBanner(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isOnline) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = LocalRaahiColors.current.offlineAmber,
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(Spacing.lg)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_signal_off),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.Black
                )
                Text(
                    text = "You are offline. Limited features available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
```

### 5.6 RaahiTopBar

Custom top app bar with theme-aware styling.

```kotlin
@Composable
fun RaahiTopBar(
    title: String,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = if (onBackClick != null) {
            { IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
        } else {
            null
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier
    )
}
```

### 5.7 PermissionRationale

Dialog requesting a permission with explanation.

```kotlin
@Composable
fun PermissionRationale(
    permission: String,
    title: String,
    description: String,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDeny,
        title = { Text(title) },
        text = { Text(description) },
        confirmButton = {
            Button(onClick = onAllow) {
                Text("Allow")
            }
        },
        dismissButton = {
            TextButton(onClick = onDeny) {
                Text("Deny")
            }
        }
    )
}
```

---

## 6. Map Styling

### 6.1 Standard Style with Light Presets

Map style follows the app's theme (light/dark mode).

```kotlin
@Composable
fun RaahiMap(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isSystemInDarkMode(),
    userLocation: LatLng? = null,
    onMapReady: () -> Unit = {}
) {
    val lightPreset = if (darkTheme) LightPresetValue.NIGHT else LightPresetValue.DAY
    val viewport = rememberMapViewportState {
        setCameraOptions {
            center(userLocation?.let { Point.fromLngLat(it.lng, it.lat) } ?: Point.fromLngLat(77.2090, 28.6139))
            zoom(12.0)
            pitch(0.0)
        }
    }

    MapboxMap(
        modifier = modifier.fillMaxSize(),
        mapViewportState = viewport,
        style = {
            MapboxStandardStyle(
                standardStyleState = rememberStandardStyleState {
                    configurationsState.apply {
                        this.lightPreset = lightPreset
                        theme = ThemeValue.DEFAULT
                        showPointOfInterestLabels = BooleanValue(true)
                        showPlaceLabels = BooleanValue(true)
                        showRoadLabels = BooleanValue(true)
                        showTransitLabels = BooleanValue(true)
                        show3dObjects = BooleanValue(false)  // For performance
                    }
                }
            )
        }
    ) {
        MapEffect(Unit) { mapView ->
            onMapReady()
        }

        // Add markers, clusters, polylines as child composables
    }
}
```

### 6.2 Satellite Style (Optional)

Satellite style is available in Settings; uses same light preset logic.

```kotlin
@Composable
fun SatelliteMap(modifier: Modifier = Modifier) {
    val darkTheme = isSystemInDarkMode()
    val lightPreset = if (darkTheme) LightPresetValue.NIGHT else LightPresetValue.DAY

    MapboxMap(
        modifier = modifier.fillMaxSize(),
        style = {
            MapboxStandardSatelliteStyle(
                standardSatelliteStyleState = rememberStandardSatelliteStyleState {
                    configurationsState.apply {
                        this.lightPreset = lightPreset
                        showRoadLabels = BooleanValue(true)
                        showTransitLabels = BooleanValue(true)
                    }
                }
            )
        }
    )
}
```

---

## 7. Dark Mode

The app follows the system theme setting (via `isSystemInDarkMode()`), with override in Settings.

**Settings options:**
- System (default)
- Light
- Dark

**Color scheme switching:**
```kotlin
@Composable
fun RaahiApp() {
    val preferences = LocalRaahiPreferences.current
    val theme by preferences.theme.collectAsState(initial = Theme.SYSTEM)

    val darkTheme = when (theme) {
        Theme.SYSTEM -> isSystemInDarkMode()
        Theme.LIGHT -> false
        Theme.DARK -> true
    }

    RaahiTheme(darkTheme = darkTheme) {
        // App content
    }
}
```

---

## 8. Motion and Animation

**Principles:**
- Subtle, purposeful animations only (no bouncy or excessive motion).
- Respect system animation settings (`reduceMotion`).
- Animations should be 150–300 ms (quick, not distracting).

**Examples:**
```kotlin
// Smooth fade-in for new content
AnimatedVisibility(
    visible = contentVisible,
    enter = fadeIn(animationSpec = tween(150))
) {
    // Content
}

// Slide transition between screens
AnimatedContentTransitionScope<NavBackStackEntry>.slideIntoContainer(
    towards = AnimatedContentTransitionScope.SlideDirection.Start,
    animationSpec = tween(300)
)

// Color transition for theme change
val backgroundColor = animateColorAsState(
    targetValue = MaterialTheme.colorScheme.background,
    animationSpec = tween(300)
)
```

---

## 9. Accessibility

### 9.1 Touch Targets

All interactive elements are at least 48 dp:

```kotlin
Button(
    onClick = { },
    modifier = Modifier.size(48.dp)  // Minimum
)

IconButton(
    onClick = { },
    modifier = Modifier.size(48.dp)
)

FilterChip(
    onClick = { },
    label = { Text("Label") },
    modifier = Modifier.height(48.dp)
)
```

### 9.2 Content Descriptions

All icons and images have `contentDescription`:

```kotlin
Icon(
    painter = painterResource(R.drawable.ic_fuel),
    contentDescription = "Fuel station",  // Never null
    tint = MaterialTheme.colorScheme.primary
)

Image(
    painter = painterResource(R.drawable.ic_location),
    contentDescription = "Current location on map",
    modifier = Modifier.size(48.dp)
)
```

### 9.3 Font Scaling

All text uses `sp` units (scale-independent); respect Material 3 typography scales which adapt to system font size:

```kotlin
Text(
    text = "Distance: 487 km",
    fontSize = 16.sp,  // Scalable; will increase if user has large text enabled
    style = MaterialTheme.typography.bodyMedium  // Use preset styles
)
```

### 9.4 Color Contrast

- Text contrast ≥ 4.5:1 for normal text.
- Icons + text, not color alone, convey meaning.

**Example (poor):**
```kotlin
// BAD: Red text for "error" only
Text("Required field", color = Color.Red)

// GOOD: Icon + label + color
Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    Icon(
        painter = painterResource(R.drawable.ic_error),
        contentDescription = "Error icon"
    )
    Text("Required field")  // text + icon make the meaning clear
}
```

---

## 10. Localization (i18n)

**Supported languages:** English, Hindi, Odia.

**String resources:**
```
res/values/strings.xml              (English)
res/values-hi/strings.xml           (Hindi)
res/values-or/strings.xml           (Odia)
```

**locales_config.xml (for Play Store language targeting):**
```xml
<?xml version="1.0" encoding="utf-8"?>
<locales>
    <locale android:name="en" />
    <locale android:name="hi" />
    <locale android:name="or" />
</locales>
```

**Usage:**
```kotlin
Text(text = stringResource(R.string.label_search))
Text(text = stringResource(R.string.error_no_results))
```

---

**Document Owner:** Design Team  
**Last Updated:** October 2026  
**Next Review:** Q1 2027
