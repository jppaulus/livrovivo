package com.livrovivo.app.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.livrovivo.app.BuildConfig
import com.livrovivo.app.data.model.appJson
import com.livrovivo.app.domain.model.IllustrationStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "livro_vivo_settings")

/** Narrador padrão do app (Capitão Aventura): voz animada e clara, boa em qualquer aparelho. */
const val DEFAULT_PERSONA_ID = "aventureiro"

/** Versão dos padrões do app; aumentar aplica os novos padrões uma vez em quem já usa o app. */
private const val CURRENT_DEFAULTS_VERSION = 1

enum class VoiceEngineChoice(val id: String, val title: String, val description: String) {
    AUTO("auto", "Automático", "Usa a voz mais natural disponível"),
    ELEVENLABS("elevenlabs", "ElevenLabs", "A mais expressiva (chave ElevenLabs)"),
    GEMINI("gemini", "Google Gemini", "Vozes naturais com a chave do Gemini"),
    DEVICE("device", "Voz do aparelho", "Funciona offline, menos natural");

    companion object {
        fun fromId(id: String?): VoiceEngineChoice = entries.find { it.id == id } ?: AUTO
    }
}

object AiModelDefaults {
    const val TEXT = "gemini-2.5-flash"
    const val IMAGE = "gemini-3.1-flash-image"
    const val TTS = "gemini-3.1-flash-tts-preview"
    const val ELEVENLABS = "eleven_v3"

    /**
     * Modelos tentados em sequência quando o configurado foi desativado ou não está liberado para a conta.
     * Todos os de texto e voz abaixo fazem parte do plano gratuito do Gemini.
     */
    val TEXT_FALLBACKS = listOf(
        "gemini-2.5-flash",
        "gemini-3.5-flash",
        "gemini-3.1-flash-lite",
        "gemini-2.5-flash-lite",
        "gemini-flash-latest"
    )
    val IMAGE_FALLBACKS = listOf("gemini-3.1-flash-image", "gemini-2.5-flash-image", "gemini-3.1-flash-lite-image")
    val TTS_FALLBACKS = listOf("gemini-3.1-flash-tts-preview", "gemini-2.5-flash-preview-tts", "gemini-2.5-pro-preview-tts")

    val ELEVENLABS_MODELS = listOf(
        "eleven_v3" to "v3 · mais expressivo",
        "eleven_multilingual_v2" to "Multilingual v2 · estável",
        "eleven_flash_v2_5" to "Flash v2.5 · mais rápido"
    )
    val IMAGE_MODELS = listOf(
        "gemini-3.1-flash-image" to "Equilibrado",
        "gemini-3.1-flash-lite-image" to "Rápido e econômico",
        "gemini-3-pro-image" to "Máxima qualidade"
    )
}

data class AppSettings(
    val geminiApiKey: String = "",
    val elevenLabsApiKey: String = "",
    val geminiKeyFromDevConfig: Boolean = false,
    val elevenLabsKeyFromDevConfig: Boolean = false,
    val voiceEngine: VoiceEngineChoice = VoiceEngineChoice.AUTO,
    val defaultPersonaId: String = DEFAULT_PERSONA_ID,
    val elevenLabsVoiceIds: Map<String, String> = emptyMap(),
    val elevenLabsModel: String = AiModelDefaults.ELEVENLABS,
    val textModel: String = AiModelDefaults.TEXT,
    val imageModel: String = AiModelDefaults.IMAGE,
    val ttsModel: String = AiModelDefaults.TTS,
    val illustrationsEnabled: Boolean = true,
    val illustrationStyle: IllustrationStyle = IllustrationStyle.AQUARELA,
    val autoPlayNarration: Boolean = true,
    val prepareNextChoices: Boolean = true,
    val narrationSpeed: Float = 1.0f,
    val ambientMusicEnabled: Boolean = false,
    val highlightReading: Boolean = true,
    val isPremium: Boolean = false,
    /** A trilha "Aprender a ler" aparece também para crianças de 9+ (escondida por padrão). */
    val showTrailForOlderKids: Boolean = false,
    /** Total de histórias já criadas (não diminui ao apagar, para o limite gratuito ser justo). */
    val storiesCreated: Int = 0
) {
    val hasGeminiKey: Boolean get() = geminiApiKey.isNotBlank()
    val hasElevenLabsKey: Boolean get() = elevenLabsApiKey.isNotBlank()
}

class SettingsManager(context: Context, private val store: DataStore<Preferences> = context.dataStore) {

    companion object {
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val ELEVENLABS_API_KEY = stringPreferencesKey("elevenlabs_api_key")
        val VOICE_ENGINE = stringPreferencesKey("voice_engine")
        val DEFAULT_PERSONA = stringPreferencesKey("default_persona")
        val ELEVENLABS_VOICES = stringPreferencesKey("elevenlabs_voice_ids")
        val ELEVENLABS_MODEL = stringPreferencesKey("elevenlabs_model")
        val TEXT_MODEL = stringPreferencesKey("text_model")
        val IMAGE_MODEL = stringPreferencesKey("image_model")
        val TTS_MODEL = stringPreferencesKey("tts_model")
        val ILLUSTRATIONS_ENABLED = booleanPreferencesKey("illustrations_enabled")
        val ILLUSTRATION_STYLE = stringPreferencesKey("illustration_style")
        val PREPARE_CHOICES = booleanPreferencesKey("prepare_next_choices")
        val AUTO_PLAY = booleanPreferencesKey("auto_play_narration")
        val NARRATION_SPEED = floatPreferencesKey("narration_speed")
        val AMBIENT_MUSIC = booleanPreferencesKey("ambient_music")
        val HIGHLIGHT_READING = booleanPreferencesKey("highlight_reading")
        val IS_PREMIUM = booleanPreferencesKey("is_premium")
        val TRAIL_FOR_OLDER_KIDS = booleanPreferencesKey("trail_for_older_kids")
        val STORIES_CREATED = intPreferencesKey("stories_created")
        val DEFAULTS_VERSION = intPreferencesKey("defaults_version")
    }

    /**
     * Aplica os padrões novos do app uma única vez (hoje: narrador Capitão Aventura),
     * inclusive para quem já tinha outro narrador salvo. Depois disso, a escolha dos pais manda.
     */
    suspend fun applyPendingDefaults() {
        store.edit { prefs ->
            if ((prefs[DEFAULTS_VERSION] ?: 0) < CURRENT_DEFAULTS_VERSION) {
                prefs[DEFAULT_PERSONA] = DEFAULT_PERSONA_ID
                prefs[DEFAULTS_VERSION] = CURRENT_DEFAULTS_VERSION
            }
        }
    }

    val settingsFlow: Flow<AppSettings> = store.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settingsFlow.first()

    // Mantido por compatibilidade com o código existente.
    val geminiApiKeyFlow: Flow<String> = settingsFlow.map { it.geminiApiKey }

    suspend fun getGeminiApiKey(): String = current().geminiApiKey

    suspend fun saveGeminiApiKey(apiKey: String) = edit { it[GEMINI_API_KEY] = apiKey.trim() }

    suspend fun saveElevenLabsApiKey(apiKey: String) = edit { it[ELEVENLABS_API_KEY] = apiKey.trim() }

    suspend fun setVoiceEngine(choice: VoiceEngineChoice) = edit { it[VOICE_ENGINE] = choice.id }

    suspend fun setDefaultPersona(personaId: String) = edit { it[DEFAULT_PERSONA] = personaId }

    suspend fun setElevenLabsVoice(personaId: String, voiceId: String?) {
        val current = current().elevenLabsVoiceIds.toMutableMap()
        if (voiceId.isNullOrBlank()) current.remove(personaId) else current[personaId] = voiceId
        edit { it[ELEVENLABS_VOICES] = appJson.encodeToString(current) }
    }

    suspend fun setElevenLabsModel(model: String) = edit { it[ELEVENLABS_MODEL] = model.trim() }

    suspend fun setTextModel(model: String) = edit { it[TEXT_MODEL] = model.trim() }

    suspend fun setImageModel(model: String) = edit { it[IMAGE_MODEL] = model.trim() }

    suspend fun setTtsModel(model: String) = edit { it[TTS_MODEL] = model.trim() }

    suspend fun setIllustrationsEnabled(enabled: Boolean) = edit { it[ILLUSTRATIONS_ENABLED] = enabled }

    suspend fun setIllustrationStyle(style: IllustrationStyle) = edit { it[ILLUSTRATION_STYLE] = style.id }

    suspend fun setPrepareChoices(enabled: Boolean) = edit { it[PREPARE_CHOICES] = enabled }

    suspend fun setAutoPlay(enabled: Boolean) = edit { it[AUTO_PLAY] = enabled }

    suspend fun setNarrationSpeed(speed: Float) = edit { it[NARRATION_SPEED] = speed.coerceIn(0.7f, 1.3f) }

    suspend fun setAmbientMusic(enabled: Boolean) = edit { it[AMBIENT_MUSIC] = enabled }

    suspend fun setHighlightReading(enabled: Boolean) = edit { it[HIGHLIGHT_READING] = enabled }

    suspend fun setPremium(isPremium: Boolean) = edit { it[IS_PREMIUM] = isPremium }

    suspend fun setTrailForOlderKids(enabled: Boolean) = edit { it[TRAIL_FOR_OLDER_KIDS] = enabled }

    /** Registra uma história criada; [existingStories] cobre quem já tinha histórias antes do contador existir. */
    suspend fun registerStoryCreated(existingStories: Int) = edit {
        val current = it[STORIES_CREATED] ?: 0
        it[STORIES_CREATED] = maxOf(current, existingStories - 1) + 1
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        store.edit { block(it) }
    }

    private fun Preferences.toSettings(): AppSettings {
        val storedGemini = this[GEMINI_API_KEY].orEmpty()
        val storedEleven = this[ELEVENLABS_API_KEY].orEmpty()
        val voiceIds: Map<String, String> = try {
            this[ELEVENLABS_VOICES]?.let { appJson.decodeFromString<Map<String, String>>(it) } ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
        return AppSettings(
            geminiApiKey = storedGemini.ifBlank { BuildConfig.DEV_GEMINI_API_KEY },
            elevenLabsApiKey = storedEleven.ifBlank { BuildConfig.DEV_ELEVENLABS_API_KEY },
            geminiKeyFromDevConfig = storedGemini.isBlank() && BuildConfig.DEV_GEMINI_API_KEY.isNotBlank(),
            elevenLabsKeyFromDevConfig = storedEleven.isBlank() && BuildConfig.DEV_ELEVENLABS_API_KEY.isNotBlank(),
            voiceEngine = VoiceEngineChoice.fromId(this[VOICE_ENGINE]),
            defaultPersonaId = this[DEFAULT_PERSONA] ?: DEFAULT_PERSONA_ID,
            elevenLabsVoiceIds = voiceIds,
            elevenLabsModel = this[ELEVENLABS_MODEL]?.takeIf { it.isNotBlank() } ?: AiModelDefaults.ELEVENLABS,
            textModel = this[TEXT_MODEL]?.takeIf { it.isNotBlank() } ?: AiModelDefaults.TEXT,
            imageModel = this[IMAGE_MODEL]?.takeIf { it.isNotBlank() } ?: AiModelDefaults.IMAGE,
            ttsModel = this[TTS_MODEL]?.takeIf { it.isNotBlank() } ?: AiModelDefaults.TTS,
            illustrationsEnabled = this[ILLUSTRATIONS_ENABLED] ?: true,
            illustrationStyle = IllustrationStyle.fromId(this[ILLUSTRATION_STYLE]),
            autoPlayNarration = this[AUTO_PLAY] ?: true,
            prepareNextChoices = this[PREPARE_CHOICES] ?: true,
            narrationSpeed = this[NARRATION_SPEED] ?: 1.0f,
            ambientMusicEnabled = this[AMBIENT_MUSIC] ?: false,
            highlightReading = this[HIGHLIGHT_READING] ?: true,
            isPremium = this[IS_PREMIUM] ?: false,
            showTrailForOlderKids = this[TRAIL_FOR_OLDER_KIDS] ?: false,
            storiesCreated = this[STORIES_CREATED] ?: 0
        )
    }
}
