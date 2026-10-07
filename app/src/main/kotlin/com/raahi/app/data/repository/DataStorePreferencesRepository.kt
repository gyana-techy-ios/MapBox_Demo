package com.raahi.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.raahi.app.domain.model.MapStyleMode
import com.raahi.app.domain.model.RouteOptions
import com.raahi.app.domain.model.RouteProfile
import com.raahi.app.domain.model.ThemeMode
import com.raahi.app.domain.model.UserPreferences
import com.raahi.app.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.raahiDataStore: DataStore<Preferences> by preferencesDataStore(name = "raahi_preferences")

class DataStorePreferencesRepository(private val dataStore: DataStore<Preferences>) : PreferencesRepository {

    override val preferences: Flow<UserPreferences> = dataStore.data.map { prefs ->
        val defaults = UserPreferences()
        UserPreferences(
            onboardingDone = prefs[ONBOARDING_DONE] ?: defaults.onboardingDone,
            themeMode = enumOrDefault(prefs[THEME], defaults.themeMode),
            mapStyle = enumOrDefault(prefs[MAP_STYLE], defaults.mapStyle),
            defaultProfile = enumOrDefault(prefs[PROFILE], defaults.defaultProfile),
            defaultRouteOptions = RouteOptions(
                avoidTolls = prefs[AVOID_TOLLS] ?: false,
                avoidMotorways = prefs[AVOID_MOTORWAYS] ?: false,
                avoidFerries = prefs[AVOID_FERRIES] ?: false,
            ),
            wifiOnlyDownloads = prefs[WIFI_ONLY] ?: defaults.wifiOnlyDownloads,
            voiceGuidance = prefs[VOICE] ?: defaults.voiceGuidance,
        )
    }

    override suspend fun setOnboardingDone(done: Boolean) {
        dataStore.edit { it[ONBOARDING_DONE] = done }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME] = mode.name }
    }

    override suspend fun setMapStyle(style: MapStyleMode) {
        dataStore.edit { it[MAP_STYLE] = style.name }
    }

    override suspend fun setRouteDefaults(profile: RouteProfile, options: RouteOptions) {
        dataStore.edit {
            it[PROFILE] = profile.name
            it[AVOID_TOLLS] = options.avoidTolls
            it[AVOID_MOTORWAYS] = options.avoidMotorways
            it[AVOID_FERRIES] = options.avoidFerries
        }
    }

    override suspend fun setWifiOnlyDownloads(enabled: Boolean) {
        dataStore.edit { it[WIFI_ONLY] = enabled }
    }

    override suspend fun setVoiceGuidance(enabled: Boolean) {
        dataStore.edit { it[VOICE] = enabled }
    }

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private companion object {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val THEME = stringPreferencesKey("theme_mode")
        val MAP_STYLE = stringPreferencesKey("map_style")
        val PROFILE = stringPreferencesKey("default_profile")
        val AVOID_TOLLS = booleanPreferencesKey("avoid_tolls")
        val AVOID_MOTORWAYS = booleanPreferencesKey("avoid_motorways")
        val AVOID_FERRIES = booleanPreferencesKey("avoid_ferries")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only_downloads")
        val VOICE = booleanPreferencesKey("voice_guidance")
    }
}
