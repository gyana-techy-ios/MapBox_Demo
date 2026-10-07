# Raahi: Security and Privacy Policy

**Date:** October 2026  
**Status:** MVP Specification  
**Version:** 1.0

---

## 1. Threat Model

### 1.1 Assets

- **User data:** Trips, saved places, parked car locations, emergency contacts.
- **Location data:** Real-time GPS, recent locations (from cached routes).
- **Mapbox public token:** Used for API requests; extractable from APK by design.
- **User's app data:** Stored locally in Room and DataStore (device-bound).

### 1.2 Threat Scenarios

| Threat | Risk | Mitigation |
|---|---|---|
| **APK decompilation extracts Mapbox token** | Attacker uses stolen token to make API calls; Mapbox charges us; attacker floods our requests. | Dedicated public token with minimal scopes; usage monitoring and alerts; token rotation quarterly. |
| **Malicious app on device reads app data via side-channel** | Attacker accesses Room database or DataStore files. | Use Android file permissions (app is single-user); device encryption at rest (user's responsibility). |
| **Man-in-the-middle (MitM) on HTTP requests** | Attacker intercepts Mapbox API requests or search results. | Use HTTPS only; pin certificates for Mapbox API (optional, but recommended). |
| **Location spoofing / GPS jamming** | Attacker feeds false location data; user is misguided. | Validate location sanity checks (speed bounds, distance continuity); inform user if location is stale. |
| **Offline data access without app unlock** | Attacker reads offline maps or saved places from device backup. | Offline data is stored in app-specific directories (Android OS enforces access control); device backup encryption is user's responsibility. |
| **Social engineering** | Attacker tricks user into sharing sensitive location via phishing link or fake app. | Educate user in Help section; no automatic sharing of location. |

### 1.3 Out of Scope (MVP)

- **Network-level attacks (DDoS, route hijacking):** Mapbox infrastructure responsibility.
- **Mapbox account compromise:** User's responsibility (secure password, 2FA).
- **Device theft:** Assumes device passcode/biometric is used.
- **Backup / cloud sync attacks:** Device backup encryption is user's responsibility.

---

## 2. Mapbox Token Strategy

### 2.1 Public Token Configuration

**Token type:** Public (`pk.*`), not secret (`sk.*`).

**Scopes:** Limited to public styles reading and tile requests:
- `styles:read` (read map styles)
- `fonts:read` (read fonts)
- `tiles:read` (read raster/vector tiles)
- NOT `uploads:*`, NOT `datasets:*`, NOT geocoding writes, NOT directions token. 

**Storage:**
```
local.properties (gitignored):
MAPBOX_ACCESS_TOKEN=pk.your_live_token_here

BuildConfig.kt (generated at build):
const val MAPBOX_ACCESS_TOKEN = "pk.your_live_token_here"

Application.kt:
MapboxOptions.accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN
```

**Never:**
- Hardcode token in strings.xml.
- Store secret (`sk.`) token anywhere in the app.
- Log the token (OkHttp logging redacts it in debug builds).
- Commit token to Git.

### 2.2 Token Extraction and Mitigation

**APK decompilation risk:** Attacker decompiles APK → finds token in BuildConfig or strings → uses token to spam Mapbox API.

**Mitigations:**

1. **Dedicated app token:** Use a separate public token for Raahi only (not shared with other apps). Rotation is easier with dedicated token.
2. **Minimal scopes:** Restrict to `styles:read`, `tiles:read`. No upload, data write, or geocoding scopes.
3. **Usage monitoring:** Set up Mapbox usage alerts (triggers at 80%, 100% of quota). Respond to anomalies within hours.
4. **Token rotation:** Rotate public token every 3 months (or immediately if exposed).
5. **URL restrictions (partial):** Mapbox URL restrictions don't apply to mobile apps (only web origins). Mitigation is detection + rotation.

### 2.3 Invalid or Missing Token Handling

If token is missing or invalid at startup, show a **developer-facing panel** instead of crashing:

```kotlin
if (BuildConfig.MAPBOX_ACCESS_TOKEN.isEmpty() || 
    !BuildConfig.MAPBOX_ACCESS_TOKEN.startsWith("pk.")) {
    // Launch DeveloperPanel activity instead of crashing
    val intent = Intent(this, DeveloperPanelActivity::class.java)
        .putExtra("error", "Invalid or missing MAPBOX_ACCESS_TOKEN")
    startActivity(intent)
    finish()
    return
}
```

**DeveloperPanelActivity shows:**
- "Mapbox access token is missing or invalid."
- Instructions: "Set MAPBOX_ACCESS_TOKEN in local.properties and rebuild."
- Restart button.

This prevents cryptic "blank map" errors in production and helps with debugging.

---

## 3. Data Storage and Privacy

### 3.1 Stored Data Inventory

| Data | Storage | Retention | Sensitive? |
|---|---|---|---|
| Trips (name, stops, routes) | Room (trips, stops tables) | Until user deletes | No (not PII unless user names trip with personal details) |
| Saved places (home, work, car) | Room (saved_places table) | Until user deletes | **Yes** (exact locations) |
| Emergency contacts (name, phone) | Room (emergency_contacts table) | Until user deletes | **Yes** (phone numbers) |
| Recent searches | Room (recent_searches table) | Kept for UX; user can clear | No (general places, not PII) |
| Parked car photo | Local file in app-specific directory | Until user deletes | **Yes** (may show surroundings) |
| Offline region metadata | Room (offline_regions table) | Until user deletes region | No (just metadata) |
| App preferences (theme, language) | DataStore | Indefinite | No |

### 3.2 Data Privacy Model

**Zero location history in MVP:** App does not track or store user's continuous location. Only stores:
- Manually saved places (home, work, car).
- Trip endpoints (origin + stops, user-entered).
- Recent searches (user-entered place names).

**No cloud sync / backend:** All data remains on device. No server-side storage, no user accounts, no sync across devices.

**User control:**
- Clear history: Settings > Clear Recent Searches.
- Delete saved places: Any time from "My Places".
- Delete offline regions: Settings > Offline.
- Delete app data: Device Settings > Apps > Raahi > Storage > Clear.

### 3.3 Local File Permissions

All user data is stored in app-specific directories protected by Android OS:

```
/data/data/com.example.raahi/
├── databases/
│   └── raahi.db (Room database, encrypted if device uses encryption)
├── files/
│   └── (parked car photos)
└── shared_prefs/
    └── (DataStore files for preferences)
```

**Android permissions:** Only the app process can read/write these files (enforced by OS). Other apps cannot access without rooting the device.

### 3.4 Search Results and Mapbox ToS

**Important legal note:** Mapbox Search Box ToS restricts result caching to temporary use only. Persisting a search result (e.g., in a saved place) may require a different Mapbox product (Permanent Geocoding).

**Current MVP approach:**
- Save place names (user-entered) in Room, not search results.
- When user taps "Save" on a search result, store only the name + coordinates, not the full Mapbox result object.
- At app start, if token changes or license review requests it, audit Room for any cached Mapbox result objects and delete.

**Action item:** Legal review with Mapbox Sales on "saved places" contract before GA (milestone: week before launch). If needed, upgrade to Permanent Geocoding API.

---

## 4. Logging and Debugging

### 4.1 Debug Logging (Development Only)

In debug builds, log useful information (but not sensitive data):

```kotlin
if (BuildConfig.DEBUG) {
    val logger = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    // Interceptor will log request/response; see next section for token redaction
}
```

### 4.2 Token Redaction in Logs

OkHttp's HttpLoggingInterceptor can be configured to redact sensitive headers:

```kotlin
class RedactingLoggingInterceptor : Interceptor {
    private val httpLogging = HttpLoggingInterceptor { message ->
        // Redact access_token from logs
        val redacted = message.replace(
            Regex("""access_token=[^&\s]+"""),
            "access_token=***redacted***"
        )
        Log.d("HTTP", redacted)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        return httpLogging.intercept(chain)
    }
}
```

### 4.3 No Analytics Backend (MVP)

The app does NOT send logs, crash reports, or telemetry to a backend server. All logging is local (Logcat in debug builds only).

**Post-MVP:** If analytics are needed, use Google Firebase Crashlytics with explicit user consent and privacy policy disclosure.

---

## 5. Compliance and Disclosure

### 5.1 Play Store Data Safety Form

The app must comply with Google Play's Data Safety form:

**Permissions requested:**
- ACCESS_FINE_LOCATION (precise location)
- ACCESS_COARSE_LOCATION (approximate location)
- POST_NOTIFICATIONS (Android 13+)
- INTERNET
- ACCESS_NETWORK_STATE

**Data collected:**
- Location (precise, approximate)
- App activity (search history)

**Data sharing:**
- Not shared with third parties (except Mapbox as an API call; user's data does not go to Mapbox servers, only the API request params).

**Data retention:**
- Deleted when user removes app or clears app data.
- User can manually clear search history, offline regions, saved places anytime.

**Mapbox attribution and disclosure:**
- Mapbox logo + attribution visible on map (required by Mapbox ToS).
- Privacy Policy mentions use of Mapbox APIs and Mapbox's privacy practices (link to https://www.mapbox.com/legal/privacy/).

### 5.2 Privacy Policy Template (Legal Review Required)

```
PRIVACY POLICY – Raahi

1. Information We Collect
   - Location: Precise and approximate GPS location, only when you enable location permission.
   - Trips and Places: Trip names, stop locations, saved home/work/favorite places (stored locally).
   - Searches: Recent search queries (stored locally; you can clear anytime).
   - Device: Unique device identifier, OS version, app version (for crash analytics, if enabled in v1.1+).

2. How We Use Information
   - To provide trip planning, route computation, and navigation features.
   - To improve offline map downloads and app performance.
   - To provide emergency location sharing (only when you initiate).

3. Data Storage and Security
   - All data is stored locally on your device.
   - We do not store data on servers or sync across devices.
   - We do not sell or share your data with third parties.
   - Mapbox is used for maps and directions; see Mapbox Privacy Policy (link).

4. User Control
   - You can clear search history, delete saved places, or delete offline regions anytime.
   - Uninstalling the app deletes all data.
   - You control location permission via device Settings.

5. Third-Party Services
   - Mapbox (maps, directions, search): https://www.mapbox.com/legal/privacy/
   - Google Play Services (location): https://policies.google.com/privacy

6. Contact
   - Questions? Email [support@raahi.app] or visit [www.raahi.app]
```

---

## 6. Android Security Best Practices

### 6.1 Manifest Hardening

```xml
<application
    android:usesCleartextTraffic="false"  <!-- HTTPS only -->
    android:hardwareAccelerated="true"     <!-- Enable HW acceleration for perf -->
    android:supportsRtl="true"
    android:extractNativeLibs="false"      <!-- Reduce exploit surface -->
>
    <!-- Activities have hardcoded intent filters; use app links for deep links -->
    <activity
        android:name=".feature.explore.ExploreActivity"
        android:exported="false"  <!-- Not exported to other apps unless needed -->
    />
    
    <!-- FileProvider for sharing (if needed; e.g., trip PDFs) -->
    <provider
        android:name="androidx.core.content.FileProvider"
        android:authorities="com.example.raahi.fileprovider"
        android:exported="false"
    >
        <meta-data
            android:name="android.support.FILE_PROVIDER_PATHS"
            android:resource="@xml/file_paths"
        />
    </provider>
</application>

<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Declare used features -->
<uses-feature android:name="android.hardware.location" android:required="false" />
<uses-feature android:name="android.hardware.location.gps" android:required="false" />
```

### 6.2 Runtime Permissions

All location and notification permissions are requested at runtime:

```kotlin
class PermissionHelper(private val activity: Activity) {
    fun requestLocationPermission(onGranted: () -> Unit, onDenied: () -> Unit) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.Manifest.permission.ACCESS_FINE_LOCATION
        } else {
            android.Manifest.permission.ACCESS_FINE_LOCATION
        }

        when {
            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED -> {
                onGranted()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) -> {
                // Show rationale dialog
            }
            else -> {
                ActivityCompat.requestPermissions(activity, arrayOf(permission), REQUEST_LOCATION)
            }
        }
    }
}
```

### 6.3 Network Security (HTTPS, Certificate Pinning)

**HTTPS only:** All API calls use HTTPS. No unencrypted HTTP except for local network (if needed).

**Certificate pinning (optional, but recommended for Mapbox):**

```kotlin
val certificatePinner = CertificatePinner.Builder()
    .add("api.mapbox.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")  // Replace with actual pin
    .build()

val okHttpClient = OkHttpClient.Builder()
    .certificatePinner(certificatePinner)
    .build()
```

(Pins must be updated before certificate rotation; coordinate with Mapbox.)

### 6.4 Code Obfuscation (ProGuard/R8)

Release builds use R8 minification and obfuscation:

```gradle
buildTypes {
    release {
        isMinifyEnabled true
        isShrinkResources true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
}
```

### 6.5 No Debuggable Release Build

```gradle
buildTypes {
    release {
        debuggable = false  // Prevent debugger attachment
        // ...
    }
}
```

---

## 7. Pre-Release Security Checklist

Before shipping to Play Store:

- [ ] **Token:** Public token is set (not secret). Scopes limited. Rotation plan documented.
- [ ] **Logging:** No location, token, or personal data logged in release builds.
- [ ] **Manifest:** No exported components unless needed. usesCleartextTraffic = false.
- [ ] **Permissions:** All permissions are necessary and requested at runtime.
- [ ] **Storage:** All user data stored locally; no cloud sync, no backend.
- [ ] **Offline maps:** Downloaded from Mapbox at runtime; no bundled map tiles.
- [ ] **Privacy policy:** Written and linked in app + Play Store.
- [ ] **Data Safety:** Play Store Data Safety form filled out accurately.
- [ ] **Deep links:** Registered and validated (raahi://place, raahi://trip/{id}).
- [ ] **Secrets:** No hardcoded API keys, tokens, or endpoints (use BuildConfig).
- [ ] **Encryption:** Device encryption for app data is user's responsibility (documented in help).
- [ ] **Crash analytics:** Disabled in MVP; if enabled in v1.1, requires user consent and privacy policy update.
- [ ] **Third-party SDKs:** Mapbox is the only external SDK; ToS reviewed and attributed.
- [ ] **Play Protect:** App passes Play Protect scan (no malware).
- [ ] **Penetration test (optional):** Security audit by third party if budget allows (recommended for GA).

---

## 8. Incident Response

If a security issue is discovered post-launch:

1. **Severity assessment:** Determine criticality (critical = immediate user impact, high = potential exploit, medium = low-risk issue).
2. **Patch:** Fix in a hotfix release (e.g., 1.0.1).
3. **Notification:** If user data is at risk, notify users via in-app banner.
4. **Disclosure:** Follow responsible disclosure; coordinate with Play Store if needed.
5. **Post-mortem:** Document root cause and prevention for future releases.

---

**Document Owner:** Security Team  
**Last Updated:** October 2026  
**Next Review:** Before GA launch
