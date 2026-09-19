package com.livrovivo.app.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.livrovivo.app.BuildConfig
import com.livrovivo.app.core.ai.AiException
import com.livrovivo.app.core.audio.BackgroundSound
import com.livrovivo.app.core.bedtime.Bedtime
import com.livrovivo.app.core.bedtime.BedtimeMode
import com.livrovivo.app.core.bedtime.BedtimeSettings
import com.livrovivo.app.core.illustration.IllustrationPause
import com.livrovivo.app.data.model.appJson
import com.livrovivo.app.domain.model.IllustrationStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import java.time.Instant
import java.time.ZoneId

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "livro_vivo_settings")

/** Narrador padrão do app (Capitão Aventura): voz animada e clara, boa em qualquer aparelho. */
const val DEFAULT_PERSONA_ID = "aventureiro"

/** Versão dos padrões do app; aumentar aplica os novos padrões uma vez em quem já usa o app. */
private const val CURRENT_DEFAULTS_VERSION = 2

/**
 * Quem narra. A voz do aparelho é a padrão: é a do Capitão Aventura aprovada para o app, começa
 * rápido e não depende de IA. As vozes de IA ficam como opção dos pais.
 */
enum class VoiceEngineChoice(val id: String, val title: String, val description: String) {
    DEVICE("device", "Voz do aparelho", "Padrão. O Capitão e os outros narradores começam rápido, sem IA e até sem internet."),
    AUTO("auto", "Automático", "Usa a voz de IA mais natural disponível. Cada página leva alguns segundos para começar."),
    GEMINI("gemini", "Google Gemini", "Vozes de IA com a chave do Gemini. Cada página leva alguns segundos para começar."),
    ELEVENLABS("elevenlabs", "ElevenLabs", "A voz de IA mais expressiva (chave ElevenLabs). Também leva alguns segundos.");

    companion object {
        fun fromId(id: String?): VoiceEngineChoice = entries.find { it.id == id } ?: DEVICE
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
    val voiceEngine: VoiceEngineChoice = VoiceEngineChoice.DEVICE,
    val defaultPersonaId: String = DEFAULT_PERSONA_ID,
    val elevenLabsVoiceIds: Map<String, String> = emptyMap(),
    val elevenLabsModel: String = AiModelDefaults.ELEVENLABS,
    val textModel: String = AiModelDefaults.TEXT,
    val imageModel: String = AiModelDefaults.IMAGE,
    val ttsModel: String = AiModelDefaults.TTS,
    val illustrationsEnabled: Boolean = true,
    /** Ilustrações com IA pausadas por um erro da conta (ex.: sem faturamento); null = liberadas. */
    val illustrationPause: IllustrationPause? = null,
    val illustrationStyle: IllustrationStyle = IllustrationStyle.AQUARELA,
    val autoPlayNarration: Boolean = true,
    val narrationSpeed: Float = 1.0f,
    /** O que toca por baixo da narração: sons da página (padrão), música de ninar ou nada. */
    val backgroundSound: BackgroundSound = BackgroundSound.AMBIENCE,
    val highlightReading: Boolean = true,
    val isPremium: Boolean = false,
    /** Total de histórias já criadas (não diminui ao apagar, para o limite gratuito ser justo). */
    val storiesCreated: Int = 0,
    /** Criança cuja estante está aberta. Vazio = usa o primeiro perfil cadastrado. */
    val activeChildId: String = "",
    val bedtime: BedtimeSettings = BedtimeSettings(),
    /** Depois do boa-noite o app "dorme" até este instante (epoch ms); 0 = acordado. */
    val sleepUntil: Long = 0L,
    /** Quando cada criança terminou suas últimas histórias, para contar as da noite. */
    val storyEndings: Map<String, List<Long>> = emptyMap(),
    /** Figurinhas já coladas no álbum de cada criança. Ausente = criança que ainda não tinha álbum. */
    val collectedStickers: Map<String, Set<String>> = emptyMap()
) {
    val hasGeminiKey: Boolean get() = geminiApiKey.isNotBlank()
    val hasElevenLabsKey: Boolean get() = elevenLabsApiKey.isNotBlank()

    /** Em que ponto da noite está a criança [childId] agora. */
    fun bedtimeModeFor(
        childId: String?,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): BedtimeMode = Bedtime.mode(bedtime, endingsOf(childId), now, zone)

    /** Quantas histórias a criança [childId] terminou nesta noite. */
    fun storiesTonightFor(
        childId: String?,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Int = Bedtime.storiesTonight(endingsOf(childId), bedtime, now, zone)

    private fun endingsOf(childId: String?): List<Long> = childId?.let { storyEndings[it] }.orEmpty()
}

class SettingsManager(private val context: Context) {

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
        val ILLUSTRATION_PAUSE = stringPreferencesKey("illustration_pause")
        val ILLUSTRATION_STYLE = stringPreferencesKey("illustration_style")
        val AUTO_PLAY = booleanPreferencesKey("auto_play_narration")
        val NARRATION_SPEED = floatPreferencesKey("narration_speed")
        /** Antiga liga/desliga da música de ninar; só é lida para migrar para [BACKGROUND_SOUND]. */
        val AMBIENT_MUSIC = booleanPreferencesKey("ambient_music")
        val BACKGROUND_SOUND = stringPreferencesKey("background_sound")
        val HIGHLIGHT_READING = booleanPreferencesKey("highlight_reading")
        val IS_PREMIUM = booleanPreferencesKey("is_premium")
        val STORIES_CREATED = intPreferencesKey("stories_created")
        val DEFAULTS_VERSION = intPreferencesKey("defaults_version")
        val ACTIVE_CHILD_ID = stringPreferencesKey("active_child_id")
        /** Minuto do dia; -1 = ritual desligado; ausente = padrão (19h30). */
        val BEDTIME_START = intPreferencesKey("bedtime_start")
        /** 0 = sem limite. */
        val STORIES_PER_NIGHT = intPreferencesKey("stories_per_night")
        val SLEEP_UNTIL = longPreferencesKey("sleep_until")
        val STORY_ENDINGS = stringPreferencesKey("story_endings")
        val COLLECTED_STICKERS = stringPreferencesKey("collected_stickers")
    }

    /**
     * Aplica os padrões novos do app uma única vez, inclusive para quem já tinha outra escolha
     * salva. Depois disso, a escolha dos pais manda.
     * - v1: narrador Capitão Aventura.
     * - v2: narração com a voz do aparelho (a do Capitão), em vez da voz de IA mais lenta.
     */
    suspend fun applyPendingDefaults() {
        context.dataStore.edit { prefs ->
            val version = prefs[DEFAULTS_VERSION] ?: 0
            if (version >= CURRENT_DEFAULTS_VERSION) return@edit
            if (version < 1) prefs[DEFAULT_PERSONA] = DEFAULT_PERSONA_ID
            if (version < 2) prefs[VOICE_ENGINE] = VoiceEngineChoice.DEVICE.id
            prefs[DEFAULTS_VERSION] = CURRENT_DEFAULTS_VERSION
        }
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

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

    /** Para de pedir ilustrações à IA por um tempo (ver [IllustrationPause]); vale para a chave atual. */
    suspend fun pauseIllustrations(reason: AiException.Kind) = edit { prefs ->
        prefs[ILLUSTRATION_PAUSE] = IllustrationPause.encode(
            reason,
            IllustrationPause.fingerprint(prefs.effectiveGeminiKey()),
            System.currentTimeMillis()
        )
    }

    /** Libera as ilustrações com IA de novo (ex.: a ilustração de teste funcionou). */
    suspend fun resumeIllustrations() = edit { it.remove(ILLUSTRATION_PAUSE) }

    suspend fun setIllustrationStyle(style: IllustrationStyle) = edit { it[ILLUSTRATION_STYLE] = style.id }

    suspend fun setAutoPlay(enabled: Boolean) = edit { it[AUTO_PLAY] = enabled }

    suspend fun setNarrationSpeed(speed: Float) = edit { it[NARRATION_SPEED] = speed.coerceIn(0.7f, 1.3f) }

    suspend fun setBackgroundSound(sound: BackgroundSound) = edit { it[BACKGROUND_SOUND] = sound.id }

    suspend fun setHighlightReading(enabled: Boolean) = edit { it[HIGHLIGHT_READING] = enabled }

    suspend fun setPremium(isPremium: Boolean) = edit { it[IS_PREMIUM] = isPremium }

    /** Troca a criança cuja estante está aberta. */
    suspend fun setActiveChildId(childId: String) = edit { it[ACTIVE_CHILD_ID] = childId }

    /** Horário em que começa a hora de dormir; null desliga o ritual. */
    suspend fun setBedtimeStart(minutes: Int?) = edit { it[BEDTIME_START] = minutes ?: -1 }

    /** Histórias por noite antes do boa-noite obrigatório; null = sem limite. */
    suspend fun setStoriesPerNight(count: Int?) = edit { it[STORIES_PER_NIGHT] = count ?: 0 }

    suspend fun setSleepUntil(epochMs: Long) = edit { it[SLEEP_UNTIL] = epochMs }

    /** Cola figurinhas no álbum da criança; o que já estava colado nunca sai. */
    suspend fun addCollectedStickers(childId: String, ids: Set<String>) = edit { prefs ->
        val all = prefs.collectedStickers()
        val merged = all[childId].orEmpty() + ids
        prefs[COLLECTED_STICKERS] = appJson.encodeToString(all + (childId to merged))
    }

    /** Esquece tudo o que é de uma criança (perfil apagado): álbum e finais de história. */
    suspend fun forgetChild(childId: String) = edit { prefs ->
        prefs[COLLECTED_STICKERS] = appJson.encodeToString(prefs.collectedStickers() - childId)
        prefs[STORY_ENDINGS] = appJson.encodeToString(prefs.storyEndings() - childId)
    }

    /** Registra que a criança terminou uma história (lido e gravado de uma vez só). */
    suspend fun recordStoryEnding(childId: String, at: Long) = edit { prefs ->
        prefs[STORY_ENDINGS] = appJson.encodeToString(Bedtime.withEnding(prefs.storyEndings(), childId, at))
    }

    /** Registra uma história criada; [existingStories] cobre quem já tinha histórias antes do contador existir. */
    suspend fun registerStoryCreated(existingStories: Int) = edit {
        val current = it[STORIES_CREATED] ?: 0
        it[STORIES_CREATED] = maxOf(current, existingStories - 1) + 1
    }

    private fun Preferences.collectedStickers(): Map<String, Set<String>> = try {
        this[COLLECTED_STICKERS]?.let { appJson.decodeFromString<Map<String, Set<String>>>(it) } ?: emptyMap()
    } catch (_: Exception) {
        emptyMap()
    }

    private fun Preferences.storyEndings(): Map<String, List<Long>> = try {
        this[STORY_ENDINGS]?.let { appJson.decodeFromString<Map<String, List<Long>>>(it) } ?: emptyMap()
    } catch (_: Exception) {
        emptyMap()
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    /** A chave do Gemini em uso: a salva pelos pais ou, no build de debug, a do local.properties. */
    private fun Preferences.effectiveGeminiKey(): String =
        this[GEMINI_API_KEY].orEmpty().ifBlank { BuildConfig.DEV_GEMINI_API_KEY }

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
            illustrationPause = IllustrationPause.decode(
                this[ILLUSTRATION_PAUSE],
                IllustrationPause.fingerprint(effectiveGeminiKey()),
                System.currentTimeMillis()
            ),
            illustrationStyle = IllustrationStyle.fromId(this[ILLUSTRATION_STYLE]),
            autoPlayNarration = this[AUTO_PLAY] ?: true,
            narrationSpeed = this[NARRATION_SPEED] ?: 1.0f,
            backgroundSound = BackgroundSound.fromStored(this[BACKGROUND_SOUND], this[AMBIENT_MUSIC]),
            highlightReading = this[HIGHLIGHT_READING] ?: true,
            isPremium = this[IS_PREMIUM] ?: false,
            storiesCreated = this[STORIES_CREATED] ?: 0,
            activeChildId = this[ACTIVE_CHILD_ID].orEmpty(),
            bedtime = BedtimeSettings(
                startMinutes = when (val start = this[BEDTIME_START]) {
                    null -> Bedtime.DEFAULT_START_MINUTES
                    -1 -> null
                    else -> start
                },
                storiesPerNight = this[STORIES_PER_NIGHT]?.takeIf { it > 0 }
            ),
            sleepUntil = this[SLEEP_UNTIL] ?: 0L,
            storyEndings = storyEndings(),
            collectedStickers = collectedStickers()
        )
    }
}
