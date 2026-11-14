package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.i18n.AppLanguage
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioDataStore by preferencesDataStore(name = "studio_settings")

enum class Mp3Quality(val bitrateKbps: Int, val labelEn: String, val labelBn: String) {
    STANDARD_128(128, "128 kbps · Standard", "১২৮ kbps · সাধারণ"),
    HIGH_192(192, "192 kbps · High", "১৯২ kbps · উচ্চ"),
    STUDIO_320(320, "320 kbps · Studio", "৩২০ kbps · স্টুডিও")
}

enum class AudioExportFormat(val extension: String, val mimeType: String) {
    WAV("wav", "audio/wav"),
    MP3("mp3", "audio/mpeg")
}

enum class ConnectionMode {
    AUTO,
    ONLINE_ONLY,
    OFFLINE_ONLY
}

data class StudioSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val appLanguage: AppLanguage = AppLanguage.ENGLISH,
    val mp3Quality: Mp3Quality = Mp3Quality.HIGH_192,
    val defaultFormat: AudioExportFormat = AudioExportFormat.WAV,
    val connectionMode: ConnectionMode = ConnectionMode.AUTO,
    val requireExplicitConsent: Boolean = true,
    val allowAcousticTimbreAnalysis: Boolean = true,
    val replicationEndpointUrl: String = "",
    val geminiRequestsToday: Int = 0,
    val lastRequestEpochDay: Long = 0L
)

class SettingsDataStore(private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val MP3_QUALITY = stringPreferencesKey("mp3_quality")
        val DEFAULT_FORMAT = stringPreferencesKey("default_format")
        val CONNECTION_MODE = stringPreferencesKey("connection_mode")
        val REQUIRE_CONSENT = booleanPreferencesKey("require_explicit_consent")
        val ALLOW_TIMBRE_ANALYSIS = booleanPreferencesKey("allow_acoustic_timbre_analysis")
        val REPLICATION_ENDPOINT = stringPreferencesKey("replication_endpoint_url")
        val GEMINI_REQUESTS_TODAY = intPreferencesKey("gemini_requests_today")
        val LAST_REQUEST_DAY = longPreferencesKey("last_request_epoch_day")
    }

    val settingsFlow: Flow<StudioSettings> = context.studioDataStore.data.map { prefs ->
        val today = System.currentTimeMillis() / 86_400_000L
        val storedDay = prefs[Keys.LAST_REQUEST_DAY] ?: today
        val count = if (storedDay == today) (prefs[Keys.GEMINI_REQUESTS_TODAY] ?: 0) else 0

        StudioSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.DARK,
            appLanguage = prefs[Keys.APP_LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                ?: AppLanguage.ENGLISH,
            mp3Quality = prefs[Keys.MP3_QUALITY]?.let { runCatching { Mp3Quality.valueOf(it) }.getOrNull() }
                ?: Mp3Quality.HIGH_192,
            defaultFormat = prefs[Keys.DEFAULT_FORMAT]?.let { runCatching { AudioExportFormat.valueOf(it) }.getOrNull() }
                ?: AudioExportFormat.WAV,
            connectionMode = prefs[Keys.CONNECTION_MODE]?.let { runCatching { ConnectionMode.valueOf(it) }.getOrNull() }
                ?: ConnectionMode.AUTO,
            requireExplicitConsent = prefs[Keys.REQUIRE_CONSENT] ?: true,
            allowAcousticTimbreAnalysis = prefs[Keys.ALLOW_TIMBRE_ANALYSIS] ?: true,
            replicationEndpointUrl = prefs[Keys.REPLICATION_ENDPOINT] ?: "",
            geminiRequestsToday = count,
            lastRequestEpochDay = today
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.studioDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        context.studioDataStore.edit { it[Keys.APP_LANGUAGE] = language.name }
    }

    suspend fun setMp3Quality(quality: Mp3Quality) {
        context.studioDataStore.edit { it[Keys.MP3_QUALITY] = quality.name }
    }

    suspend fun setDefaultFormat(format: AudioExportFormat) {
        context.studioDataStore.edit { it[Keys.DEFAULT_FORMAT] = format.name }
    }

    suspend fun setConnectionMode(mode: ConnectionMode) {
        context.studioDataStore.edit { it[Keys.CONNECTION_MODE] = mode.name }
    }

    suspend fun setRequireExplicitConsent(required: Boolean) {
        context.studioDataStore.edit { it[Keys.REQUIRE_CONSENT] = required }
    }

    suspend fun setAllowAcousticTimbreAnalysis(allowed: Boolean) {
        context.studioDataStore.edit { it[Keys.ALLOW_TIMBRE_ANALYSIS] = allowed }
    }

    suspend fun setReplicationEndpointUrl(url: String) {
        context.studioDataStore.edit { it[Keys.REPLICATION_ENDPOINT] = url.trim() }
    }

    suspend fun incrementGeminiRequestCount() {
        val today = System.currentTimeMillis() / 86_400_000L
        context.studioDataStore.edit { prefs ->
            val storedDay = prefs[Keys.LAST_REQUEST_DAY] ?: today
            val current = if (storedDay == today) (prefs[Keys.GEMINI_REQUESTS_TODAY] ?: 0) else 0
            prefs[Keys.LAST_REQUEST_DAY] = today
            prefs[Keys.GEMINI_REQUESTS_TODAY] = current + 1
        }
    }
}
