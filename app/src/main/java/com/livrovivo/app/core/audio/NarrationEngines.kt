package com.livrovivo.app.core.audio

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.livrovivo.app.core.ai.AiException
import com.livrovivo.app.core.ai.AzureSpeechService
import com.livrovivo.app.core.ai.ChapterSanitizer
import com.livrovivo.app.core.ai.ElevenLabsService
import com.livrovivo.app.core.ai.ElevenLabsVoice
import com.livrovivo.app.core.ai.GeminiService
import com.livrovivo.app.core.ai.SpeechChunk
import com.livrovivo.app.core.settings.AppSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.Locale
import java.util.UUID

private const val LOG_TAG = "LivroVivoVoz"

enum class EngineKind(val label: String) {
    AZURE("Microsoft"),
    ELEVENLABS("ElevenLabs"),
    GEMINI("Gemini"),
    DEVICE("Voz do aparelho")
}

data class NarrationRequest(
    /** Texto exibido na tela (da parte que será narrada). */
    val text: String,
    /** Texto com marcações de emoção ([whispers]...), quando disponível. */
    val script: String?,
    val persona: VoicePersona,
    val mood: String? = null,
    val listenerAge: String? = null,
    /** Posição desta parte na página (1..partCount): a página é narrada em partes para começar rápido. */
    val partIndex: Int = 1,
    val partCount: Int = 1,
    /** Trechos vizinhos, usados apenas como contexto de entonação (não são falados). */
    val previousText: String? = null,
    val nextText: String? = null
) {
    val isSinglePart: Boolean get() = partCount <= 1
}

interface NarrationEngine {
    val kind: EngineKind

    suspend fun isAvailable(settings: AppSettings): Boolean

    /** Identificador que muda quando voz/modelo mudam, para invalidar o cache de áudio. */
    fun cacheSignature(request: NarrationRequest, settings: AppSettings): String

    /** Gera o áudio e grava em [outputBase] + extensão; devolve o arquivo final. */
    suspend fun synthesize(request: NarrationRequest, settings: AppSettings, outputBase: File): File
}

/** Motor que narra a página inteira num pedido só e toca enquanto a voz chega. */
interface StreamingNarrationEngine : NarrationEngine {
    /** Dá para narrar em partes (tocando enquanto gera)? */
    suspend fun canStream(): Boolean

    fun stream(request: NarrationRequest): Flow<SpeechChunk>
}

/** Grava a fala em AAC (.m4a) ou, se o aparelho não codificar, em WAV; devolve o arquivo final. */
suspend fun savePcm(pcm: ByteArray, sampleRate: Int, outputBase: File): File = withContext(Dispatchers.IO) {
    val m4a = File(outputBase.path + ".m4a")
    try {
        AacEncoder.encode(pcm, sampleRate, m4a)
        if (m4a.length() < 1_000) throw IllegalStateException("AAC vazio")
        m4a
    } catch (_: Exception) {
        m4a.delete()
        File(outputBase.path + ".wav").also { WavWriter.write(it, pcm, sampleRate) }
    }
}

// -------------------------------------------------------------------------------------------------

/**
 * Vozes da Microsoft Azure: as que podem ir para a loja (HANDOFF §4, item 12). Cada narrador tem a sua
 * ([VoicePersona.azureVoice]); a página inteira vai num pedido e toca desde o primeiro pedaço.
 */
class AzureNarrationEngine(private val azure: AzureSpeechService) : StreamingNarrationEngine {
    override val kind = EngineKind.AZURE

    override suspend fun isAvailable(settings: AppSettings): Boolean = azure.isAvailable

    override fun cacheSignature(request: NarrationRequest, settings: AppSettings): String =
        "azure|${request.persona.azureVoice}|$RATE|v1"

    override suspend fun canStream(): Boolean = azure.isAvailable

    override fun stream(request: NarrationRequest): Flow<SpeechChunk> = azure.stream(buildSsml(request))

    override suspend fun synthesize(request: NarrationRequest, settings: AppSettings, outputBase: File): File {
        val speech = azure.synthesize(buildSsml(request))
        return savePcm(speech.pcm, speech.sampleRate, outputBase)
    }

    companion object {
        /** Um pouco mais devagar que a fala normal: a velocidade das amostras que o usuário aprovou (25/09/2026). */
        const val RATE = "-8%"

        /** As marcações de emoção ([whispers]...) são do Gemini: a Azure as leria em voz alta. */
        fun buildSsml(request: NarrationRequest): String =
            AzureSpeechService.ssml(
                voice = request.persona.azureVoice,
                text = ChapterSanitizer.stripAudioTags(request.script ?: request.text),
                rate = RATE
            )
    }
}

// -------------------------------------------------------------------------------------------------

class GeminiNarrationEngine(private val gemini: GeminiService) : StreamingNarrationEngine {
    override val kind = EngineKind.GEMINI

    override suspend fun isAvailable(settings: AppSettings): Boolean = gemini.isAvailable()

    override fun cacheSignature(request: NarrationRequest, settings: AppSettings): String =
        "gemini|${settings.ttsModel}|${request.persona.geminiVoice}|${request.mood}|v2"

    override suspend fun canStream(): Boolean = gemini.canStreamSpeech()

    /** A página inteira num pedido só, em partes: o primeiro pedaço chega em cerca de 1 s. */
    override fun stream(request: NarrationRequest): Flow<SpeechChunk> =
        gemini.streamSpeech(buildPrompt(request), request.persona.geminiVoice)

    override suspend fun synthesize(request: NarrationRequest, settings: AppSettings, outputBase: File): File {
        val speech = gemini.generateSpeech(buildPrompt(request), request.persona.geminiVoice)
        return savePcm(speech.pcm, speech.sampleRate, outputBase)
    }

    companion object {
        /** Estrutura recomendada pelo guia de prompts do Gemini TTS: perfil, cena, direção e transcrição. */
        fun buildPrompt(request: NarrationRequest): String {
            val persona = request.persona
            val scene = when (request.mood) {
                "sonolento", "aconchegante" -> "A cozy, softly lit bedroom at bedtime. The child is snuggled under a blanket, getting sleepy."
                "misterioso" -> "A cozy reading nook with a small lamp. The child leans in, curious about a gentle mystery."
                "aventura", "emocionante" -> "A playroom turned into an imaginary adventure. The child listens with wide, excited eyes."
                else -> "A warm, cheerful living room. The storyteller sits with a picture book beside a delighted child."
            }
            val transcript = request.script?.takeIf { it.isNotBlank() } ?: request.text
            val continuity = if (request.isSinglePart) {
                ""
            } else {
                "\nContinuity: this is part ${request.partIndex} of ${request.partCount} of the same page, " +
                    "read in one sitting. Keep exactly the same voice, tone and energy as the other parts, " +
                    "without re-introducing anything." +
                    (request.previousText?.takeIf { it.isNotBlank() }?.let { "\nPrevious part ended with: \"${it.takeLast(160)}\"" } ?: "")
            }
            return """
# AUDIO PROFILE: ${persona.title}
## ${persona.character}

## THE SCENE
$scene The storyteller is reading aloud to ${request.listenerAge ?: "a young child"}.

### DIRECTOR'S NOTES
Style: ${persona.style} Give dialogue lines (after the dash) a slightly different, characterful voice. Make onomatopoeias playful.
Pace: ${persona.pace}
Accent: Native Brazilian Portuguese (pt-BR), natural and friendly.$continuity

### TRANSCRIPT
$transcript
""".trim()
        }
    }
}

// -------------------------------------------------------------------------------------------------

class ElevenLabsNarrationEngine(private val elevenLabs: ElevenLabsService) : NarrationEngine {
    override val kind = EngineKind.ELEVENLABS

    override suspend fun isAvailable(settings: AppSettings): Boolean = elevenLabs.isAvailable()

    override fun cacheSignature(request: NarrationRequest, settings: AppSettings): String =
        "eleven|${settings.elevenLabsModel}|${settings.elevenLabsVoiceIds[request.persona.id] ?: "auto-${request.persona.id}"}|v2"

    override suspend fun synthesize(request: NarrationRequest, settings: AppSettings, outputBase: File): File {
        val voiceId = settings.elevenLabsVoiceIds[request.persona.id]?.takeIf { it.isNotBlank() }
            ?: pickVoice(elevenLabs.listVoices(), request.persona)?.id
            ?: throw AiException(AiException.Kind.BAD_REQUEST, "Nenhuma voz encontrada na conta ElevenLabs.")

        val model = settings.elevenLabsModel
        val supportsTags = model.startsWith("eleven_v3")
        val text = if (supportsTags) {
            request.script?.takeIf { it.isNotBlank() } ?: request.text
        } else {
            ChapterSanitizer.stripAudioTags(request.script ?: request.text)
        }
        val voiceSettings = if (supportsTags) {
            buildJsonObject { put("stability", 0.5) }
        } else {
            buildJsonObject {
                put("stability", 0.45)
                put("similarity_boost", 0.8)
                put("style", 0.35)
                put("use_speaker_boost", true)
                put("speed", request.persona.deviceSpeechRate.coerceIn(0.85f, 1.05f).toDouble())
            }
        }
        // previous_text/next_text dão continuidade de entonação entre as partes da página.
        val mp3 = elevenLabs.synthesize(
            text = text.take(4_800),
            voiceId = voiceId,
            modelId = model,
            voiceSettings = voiceSettings,
            previousText = request.previousText?.takeLast(500),
            nextText = request.nextText?.take(500)
        )
        return withContext(Dispatchers.IO) {
            File(outputBase.path + ".mp3").also { it.writeBytes(mp3) }
        }
    }

    companion object {
        /** Escolhe na conta a voz que mais combina com a persona (gênero, idade, uso em narração, PT). */
        fun pickVoice(voices: List<ElevenLabsVoice>, persona: VoicePersona): ElevenLabsVoice? {
            if (voices.isEmpty()) return null
            return voices.maxByOrNull { voice ->
                var score = 0
                // Gênero pesa mais que todo o resto somado: o Ursinho nunca ganha voz feminina.
                if (voice.gender == persona.elevenLabsGender) score += 25
                if (persona.elevenLabsPreferredAges.firstOrNull()?.let { voice.age?.contains(it.replace(' ', '_')) } == true) score += 4
                else if (persona.elevenLabsPreferredAges.any { age -> voice.age?.replace('_', ' ')?.contains(age.replace('_', ' ')) == true }) score += 2
                val useCase = voice.useCase.orEmpty().lowercase()
                if ("narrat" in useCase || "story" in useCase || "audiobook" in useCase) score += 6
                if ("character" in useCase || "animation" in useCase) score += 3
                if (voice.speaksPortuguese) score += 8
                val description = voice.description.orEmpty().lowercase()
                if (listOf("warm", "calm", "soft", "gentle", "friendly").any { it in description }) score += 2
                score
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------

/**
 * Voz do próprio Android (sem chave de IA). É o mesmo motor do Google Maps, então soa mais robótica;
 * para amenizar, cada narrador recebe uma voz PT-BR diferente (quando o aparelho tem mais de uma),
 * a versão online da voz é preferida quando há internet e o ritmo/tom muda por personagem.
 */
class DeviceNarrationEngine(private val context: Context) : NarrationEngine {
    override val kind = EngineKind.DEVICE

    /** Uma "pessoa" que fala: a mesma voz pode existir em versão local e online. */
    private data class Speaker(val id: String, val local: Voice?, val network: Voice?)

    private val mutex = Mutex()
    private var tts: TextToSpeech? = null
    private var ready = false

    /** Nome da última voz usada (exibido no teste de voz das configurações). */
    @Volatile
    var lastVoiceName: String? = null
        private set

    override suspend fun isAvailable(settings: AppSettings): Boolean = true

    override fun cacheSignature(request: NarrationRequest, settings: AppSettings): String =
        "device|${request.persona.id}|v3"

    override suspend fun synthesize(request: NarrationRequest, settings: AppSettings, outputBase: File): File = mutex.withLock {
        val engine = ensureReady()
        val persona = request.persona
        val output = File(outputBase.path + ".wav")
        val text = ChapterSanitizer.stripAudioTags(request.script ?: request.text)
            .replace("—", ", ")
            .take(TextToSpeech.getMaxSpeechInputLength() - 1)

        engine.setSpeechRate(persona.deviceSpeechRate)
        engine.setPitch(persona.devicePitch)

        val speaker = speakerFor(engine, persona)
        val online = isOnline()
        if (!online && speaker?.local == null) {
            throw IllegalStateException("Instale a voz em português nas configurações de texto para fala do Android para ouvir sem internet.")
        }
        val attempts = buildList {
            speaker?.local?.let(::add)
            if (online) speaker?.network?.let(::add)
            add(null) // voz padrão do idioma como última tentativa
        }.distinct()

        for (voice in attempts) {
            try {
                if (voice != null) engine.voice = voice else engine.language = Locale("pt", "BR")
            } catch (_: Exception) {
                continue
            }
            if (synthesizeOnce(engine, text, output)) {
                lastVoiceName = voice?.name ?: engine.voice?.name
                Log.d(LOG_TAG, "Narrando '${persona.id}' com a voz do aparelho ${lastVoiceName}")
                return@withLock output
            }
            Log.w(LOG_TAG, "Voz ${voice?.name} falhou; tentando a próxima")
        }
        throw IllegalStateException("A voz do aparelho não conseguiu gerar o áudio")
    }

    private suspend fun synthesizeOnce(engine: TextToSpeech, text: String, output: File): Boolean {
        output.delete()
        val utteranceId = UUID.randomUUID().toString()
        val done = CompletableDeferred<Boolean>()
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onDone(id: String?) {
                if (id == utteranceId) done.complete(true)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId) done.complete(false)
            }

            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId) done.complete(false)
            }
        })
        val result = withContext(Dispatchers.Main) {
            engine.synthesizeToFile(text, Bundle(), output, utteranceId)
        }
        if (result != TextToSpeech.SUCCESS) return false
        try {
            val ok = withTimeoutOrNull(12_000) { done.await() } ?: false
            return ok && output.exists() && output.length() > 1_000
        } finally {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) { engine.stop() }
        }
    }

    /** Vozes PT-BR agrupadas por pessoa, em ordem estável; cada narrador fica com uma diferente. */
    private fun speakerFor(engine: TextToSpeech, persona: VoicePersona): Speaker? {
        val voices = try {
            engine.voices.orEmpty()
        } catch (_: Exception) {
            emptySet()
        }.filter { voice ->
            voice.locale.language == "pt" &&
                (voice.locale.country.isBlank() || voice.locale.country.equals("BR", ignoreCase = true))
        }
        if (voices.isEmpty()) return null
        Log.d(LOG_TAG, "Vozes PT-BR no aparelho: " + voices.joinToString { "${it.name}(q=${it.quality}, rede=${it.isNetworkConnectionRequired})" })

        val speakers = voices
            .groupBy { it.name.lowercase().removeSuffix("-local").removeSuffix("-network") }
            .map { (id, group) ->
                Speaker(
                    id = id,
                    local = group.filter { !it.isNetworkConnectionRequired && !it.isNotInstalled() }.maxByOrNull { it.quality },
                    network = group.filter { it.isNetworkConnectionRequired }.maxByOrNull { it.quality }
                )
            }
            .filter { it.local != null || it.network != null }
            .sortedWith(compareBy<Speaker> { it.local == null }.thenBy { it.id })
        if (speakers.isEmpty()) return null
        return speakers[persona.ordinal % speakers.size]
    }

    private fun Voice.isNotInstalled(): Boolean =
        features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)

    private fun isOnline(): Boolean = try {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        manager.getNetworkCapabilities(manager.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (_: Exception) {
        false
    }

    private suspend fun ensureReady(): TextToSpeech {
        tts?.takeIf { ready }?.let { return it }
        val initialized = CompletableDeferred<Boolean>()
        val engine = withContext(Dispatchers.Main) {
            TextToSpeech(context.applicationContext) { status -> initialized.complete(status == TextToSpeech.SUCCESS) }
        }
        val ok = withTimeout(10_000) { initialized.await() }
        if (!ok) {
            engine.shutdown()
            throw IllegalStateException("Mecanismo de voz do aparelho indisponível")
        }
        val ptBr = Locale("pt", "BR")
        val available = engine.isLanguageAvailable(ptBr)
        if (available < TextToSpeech.LANG_AVAILABLE) {
            engine.shutdown()
            throw IllegalStateException("Instale a voz em português nas configurações do aparelho")
        }
        engine.language = ptBr
        tts = engine
        ready = true
        return engine
    }

    /** Inicializa o mecanismo de voz antes do primeiro uso (evita ~1s de espera). */
    suspend fun prewarm() {
        try {
            mutex.withLock { ensureReady() }
        } catch (_: Exception) {
            // Sem voz do aparelho disponível: o erro real aparece quando a narração for pedida.
        }
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
    }
}
