package com.livrovivo.app.core.literacy

import android.content.res.AssetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.text.Normalizer

/**
 * Falas da trilha gravadas antes com a voz do Gemini (`assets/voz`, gravadas por `ferramentas/gerar_audios.py`).
 * Tocam na hora, sem internet e sem chave no aparelho. O índice `voz/indice.json` diz em que arquivo está
 * cada fala; fala que ainda não foi gravada não está no índice, e o app usa a voz de narração normal.
 */
class VoiceClips(private val assets: AssetManager) {

    /** Tipos de fala. O prefixo é o mesmo das chaves do índice ("silaba:BA"). */
    enum class Kind(val prefix: String) {
        LETTER("letra"),
        SYLLABLE("silaba"),
        WORD("palavra"),
        PROMPT("fala"),
        PHRASE("frase")
    }

    private val mutex = Mutex()
    private var index: Map<String, String>? = null

    /** Caminho do áudio em assets ("voz/silabas/ba.ogg"), ou null quando a fala ainda não foi gravada. */
    suspend fun find(kind: Kind, text: String): String? {
        val clips = index ?: mutex.withLock {
            index ?: withContext(Dispatchers.IO) { load() }.also { index = it }
        }
        return clips[key(kind, text)]?.let { "$FOLDER/$it" }
    }

    private fun load(): Map<String, String> = try {
        assets.open(INDEX_PATH).bufferedReader(Charsets.UTF_8).use { parseIndex(it.readText()) }
    } catch (_: IOException) {
        emptyMap() // nenhuma fala gravada ainda
    }

    companion object {
        const val FOLDER = "voz"
        const val INDEX_PATH = "$FOLDER/indice.json"

        /** Letra sozinha ("B") ou sílaba ("BA"). */
        fun unitKind(unit: String): Kind = if (unit.length == 1) Kind.LETTER else Kind.SYLLABLE

        /** Mesma regra do script: acentos compostos (NFC), sem espaços sobrando; letras, sílabas e palavras em maiúsculas. */
        fun key(kind: Kind, text: String): String {
            val normalized = normalize(text)
            val value = if (kind == Kind.PROMPT || kind == Kind.PHRASE) normalized else normalized.uppercase()
            return "${kind.prefix}:$value"
        }

        fun normalize(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFC).replace(Regex("\\s+"), " ").trim()

        /** Lê o índice: `{"voz": "Puck", "clips": {"silaba:BA": "silabas/ba.ogg", ...}}`. */
        fun parseIndex(raw: String): Map<String, String> {
            val clips = Json.parseToJsonElement(raw).jsonObject["clips"]?.jsonObject ?: return emptyMap()
            return clips.mapNotNull { (key, value) ->
                (value as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }?.let { normalize(key) to it }
            }.toMap()
        }
    }
}
