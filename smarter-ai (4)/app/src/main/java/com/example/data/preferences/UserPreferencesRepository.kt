package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.models.AiModelCatalog
import com.example.domain.models.AppPreferences
import com.example.domain.models.ThemeMode
import com.example.domain.repositories.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nexus_ai_preferences")

/**
 * Persists user preferences with a dual-layer synchronous startup mirror (`SharedPreferences`)
 * and reactive `DataStore<Preferences>`.
 *
 * This guarantees:
 * 1. Zero startup theme flicker: [getInitialPreferences] synchronously reads the saved
 *    [ThemeMode] before Compose renders Frame 0 (never flashing Dark theme when Light is selected).
 * 2. Instantaneous in-app theme switching: [setThemeMode] updates the synchronous state flow
 *    and SharedPreferences immediately before committing to DataStore.
 */
class UserPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
    private val syncPrefs: SharedPreferences? = null
) : PreferencesRepository {

    constructor(context: Context) : this(
        dataStore = context.applicationContext.dataStore,
        syncPrefs = context.applicationContext.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
    )

    companion object {
        const val SYNC_PREFS_NAME = "smarter_ai_sync_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DEFAULT_MODEL_ID = "default_model_id"
        private const val KEY_FALLBACK_MODEL_ID = "fallback_model_id"
        private const val KEY_ALLOW_MODEL_FALLBACK = "allow_model_fallback"
        private const val KEY_ENTER_TO_SEND = "enter_to_send"
        private const val KEY_REDUCED_ANIMATIONS = "reduced_animations"
        private const val KEY_STREAM_RESPONSES = "stream_responses"
        private const val KEY_RENDER_MARKDOWN = "render_markdown"
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey(KEY_THEME_MODE)
        val DEFAULT_MODEL_ID = stringPreferencesKey(KEY_DEFAULT_MODEL_ID)
        val FALLBACK_MODEL_ID = stringPreferencesKey(KEY_FALLBACK_MODEL_ID)
        val ALLOW_MODEL_FALLBACK = booleanPreferencesKey(KEY_ALLOW_MODEL_FALLBACK)
        val ENTER_TO_SEND = booleanPreferencesKey(KEY_ENTER_TO_SEND)
        val REDUCED_ANIMATIONS = booleanPreferencesKey(KEY_REDUCED_ANIMATIONS)
        val STREAM_RESPONSES = booleanPreferencesKey(KEY_STREAM_RESPONSES)
        val RENDER_MARKDOWN = booleanPreferencesKey(KEY_RENDER_MARKDOWN)
    }

    private val immediateStateFlow = MutableStateFlow(readFromSyncPrefs())

    override fun getInitialPreferences(): AppPreferences {
        val latest = readFromSyncPrefs()
        immediateStateFlow.value = latest
        return latest
    }

    private fun readFromSyncPrefs(): AppPreferences {
        val sp = syncPrefs ?: return AppPreferences()
        val themeName = sp.getString(KEY_THEME_MODE, null) ?: ThemeMode.DARK.name
        val themeMode = ThemeMode.entries.firstOrNull { it.name == themeName } ?: ThemeMode.DARK
        val storedDefault = sp.getString(KEY_DEFAULT_MODEL_ID, null)?.takeIf {
            AiModelCatalog.isSupportedApinexModel(it)
        } ?: AiModelCatalog.DEFAULT_MODEL_ID
        val storedFallback = sp.getString(KEY_FALLBACK_MODEL_ID, null)?.takeIf {
            AiModelCatalog.isSupportedApinexModel(it)
        }
        return AppPreferences(
            themeMode = themeMode,
            defaultModelId = storedDefault,
            fallbackModelId = storedFallback,
            allowModelFallback = sp.getBoolean(KEY_ALLOW_MODEL_FALLBACK, true),
            enterToSend = sp.getBoolean(KEY_ENTER_TO_SEND, false),
            reducedAnimations = sp.getBoolean(KEY_REDUCED_ANIMATIONS, false),
            streamResponses = sp.getBoolean(KEY_STREAM_RESPONSES, true),
            renderMarkdown = sp.getBoolean(KEY_RENDER_MARKDOWN, true)
        )
    }

    private fun writeToSyncPrefs(prefs: AppPreferences) {
        immediateStateFlow.value = prefs
        syncPrefs?.edit()?.apply {
            putString(KEY_THEME_MODE, prefs.themeMode.name)
            putString(KEY_DEFAULT_MODEL_ID, prefs.defaultModelId)
            if (prefs.fallbackModelId.isNullOrBlank()) {
                remove(KEY_FALLBACK_MODEL_ID)
            } else {
                putString(KEY_FALLBACK_MODEL_ID, prefs.fallbackModelId)
            }
            putBoolean(KEY_ALLOW_MODEL_FALLBACK, prefs.allowModelFallback)
            putBoolean(KEY_ENTER_TO_SEND, prefs.enterToSend)
            putBoolean(KEY_REDUCED_ANIMATIONS, prefs.reducedAnimations)
            putBoolean(KEY_STREAM_RESPONSES, prefs.streamResponses)
            putBoolean(KEY_RENDER_MARKDOWN, prefs.renderMarkdown)
            commit()
        }
    }

    override val preferencesFlow: Flow<AppPreferences> = combine(
        immediateStateFlow,
        dataStore.data.catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
    ) { immediate, dsPrefs ->
        // If syncPrefs has an explicit value, prefer the immediate synchronous state
        // while keeping DataStore synchronized.
        val hasSyncTheme = syncPrefs?.contains(KEY_THEME_MODE) == true
        val themeName = if (hasSyncTheme) {
            immediate.themeMode.name
        } else {
            dsPrefs[Keys.THEME_MODE] ?: immediate.themeMode.name
        }
        val themeMode = ThemeMode.entries.firstOrNull { it.name == themeName } ?: ThemeMode.DARK
        val storedDefault = (if (syncPrefs?.contains(KEY_DEFAULT_MODEL_ID) == true) {
            immediate.defaultModelId
        } else {
            dsPrefs[Keys.DEFAULT_MODEL_ID]
        })?.takeIf { AiModelCatalog.isSupportedApinexModel(it) } ?: AiModelCatalog.DEFAULT_MODEL_ID

        val storedFallback = (if (syncPrefs?.contains(KEY_FALLBACK_MODEL_ID) == true) {
            immediate.fallbackModelId
        } else {
            dsPrefs[Keys.FALLBACK_MODEL_ID]
        })?.takeIf { AiModelCatalog.isSupportedApinexModel(it) }

        AppPreferences(
            themeMode = themeMode,
            defaultModelId = storedDefault,
            fallbackModelId = storedFallback,
            allowModelFallback = if (syncPrefs?.contains(KEY_ALLOW_MODEL_FALLBACK) == true) {
                immediate.allowModelFallback
            } else {
                dsPrefs[Keys.ALLOW_MODEL_FALLBACK] ?: true
            },
            enterToSend = if (syncPrefs?.contains(KEY_ENTER_TO_SEND) == true) {
                immediate.enterToSend
            } else {
                dsPrefs[Keys.ENTER_TO_SEND] ?: false
            },
            reducedAnimations = if (syncPrefs?.contains(KEY_REDUCED_ANIMATIONS) == true) {
                immediate.reducedAnimations
            } else {
                dsPrefs[Keys.REDUCED_ANIMATIONS] ?: false
            },
            streamResponses = if (syncPrefs?.contains(KEY_STREAM_RESPONSES) == true) {
                immediate.streamResponses
            } else {
                dsPrefs[Keys.STREAM_RESPONSES] ?: true
            },
            renderMarkdown = if (syncPrefs?.contains(KEY_RENDER_MARKDOWN) == true) {
                immediate.renderMarkdown
            } else {
                dsPrefs[Keys.RENDER_MARKDOWN] ?: true
            }
        )
    }

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        writeToSyncPrefs(immediateStateFlow.value.copy(themeMode = themeMode))
        dataStore.edit { it[Keys.THEME_MODE] = themeMode.name }
    }

    override suspend fun setDefaultModelId(modelId: String) {
        writeToSyncPrefs(immediateStateFlow.value.copy(defaultModelId = modelId))
        dataStore.edit { it[Keys.DEFAULT_MODEL_ID] = modelId }
    }

    override suspend fun setFallbackModel(modelId: String?, allowFallback: Boolean) {
        val cleaned = modelId?.takeIf { it.isNotBlank() }
        writeToSyncPrefs(
            immediateStateFlow.value.copy(
                fallbackModelId = cleaned,
                allowModelFallback = if (cleaned == null) false else allowFallback
            )
        )
        dataStore.edit { prefs ->
            if (cleaned == null) {
                prefs.remove(Keys.FALLBACK_MODEL_ID)
                prefs[Keys.ALLOW_MODEL_FALLBACK] = false
            } else {
                prefs[Keys.FALLBACK_MODEL_ID] = cleaned
                prefs[Keys.ALLOW_MODEL_FALLBACK] = allowFallback
            }
        }
    }

    override suspend fun setEnterToSend(enabled: Boolean) {
        writeToSyncPrefs(immediateStateFlow.value.copy(enterToSend = enabled))
        dataStore.edit { it[Keys.ENTER_TO_SEND] = enabled }
    }

    override suspend fun setReducedAnimations(enabled: Boolean) {
        writeToSyncPrefs(immediateStateFlow.value.copy(reducedAnimations = enabled))
        dataStore.edit { it[Keys.REDUCED_ANIMATIONS] = enabled }
    }

    override suspend fun setStreamResponses(enabled: Boolean) {
        writeToSyncPrefs(immediateStateFlow.value.copy(streamResponses = enabled))
        dataStore.edit { it[Keys.STREAM_RESPONSES] = enabled }
    }

    override suspend fun setRenderMarkdown(enabled: Boolean) {
        writeToSyncPrefs(immediateStateFlow.value.copy(renderMarkdown = enabled))
        dataStore.edit { it[Keys.RENDER_MARKDOWN] = enabled }
    }
}
