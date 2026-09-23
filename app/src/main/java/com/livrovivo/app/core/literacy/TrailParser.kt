package com.livrovivo.app.core.literacy

import com.livrovivo.app.domain.model.LiteracyModule
import com.livrovivo.app.domain.model.LiteracyPhase
import com.livrovivo.app.domain.model.LiteracyQuestion
import com.livrovivo.app.domain.model.LiteracyTrail
import com.livrovivo.app.domain.model.QuestionType
import com.livrovivo.app.domain.model.VocabularyWord
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Conteúdo da trilha com algum problema; a mensagem lista tudo o que está errado. */
class InvalidTrailException(val problems: List<String>) :
    IllegalStateException("trilha.json inválido:\n" + problems.joinToString("\n"))

/**
 * Lê o `trilha.json` gerado por `ferramentas/gerar_conteudo.py`.
 *
 * Confere as mesmas regras do script: um JSON editado à mão com erro falha aqui, com uma mensagem
 * clara, em vez de travar a criança no meio de uma atividade.
 */
object TrailParser {
    const val ASSET_PATH = "alfabetizacao/trilha.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): LiteracyTrail {
        val dto = try {
            json.decodeFromString<TrailDto>(raw)
        } catch (e: Exception) {
            throw InvalidTrailException(listOf("JSON ilegível: ${e.message}"))
        }
        val problems = mutableListOf<String>()
        val trail = dto.toDomain(problems)
        problems += validate(trail)
        if (problems.isNotEmpty()) throw InvalidTrailException(problems)
        return trail
    }

    private fun TrailDto.toDomain(problems: MutableList<String>) = LiteracyTrail(
        version = versao,
        supportWords = palavrasDeApoio,
        vocabulary = vocabulario.map { VocabularyWord(it.palavra, it.silabas, it.imagem, it.artigo) },
        modules = modulos.sortedBy { it.ordem }.map { module ->
            LiteracyModule(
                id = module.id,
                title = module.titulo,
                order = module.ordem,
                phases = module.fases.map { phase ->
                    LiteracyPhase(
                        id = phase.id,
                        title = phase.titulo,
                        isFree = phase.gratis,
                        teaches = phase.ensina,
                        questions = phase.perguntas.mapIndexedNotNull { index, question ->
                            val type = QuestionType.fromCode(question.tipo)
                            if (type == null) {
                                problems += "${phase.id} pergunta ${index + 1}: tipo desconhecido '${question.tipo}'"
                                null
                            } else {
                                LiteracyQuestion(
                                    type = type,
                                    prompt = question.fala,
                                    answer = question.resposta,
                                    options = question.opcoes,
                                    pieces = question.pecas,
                                    image = question.imagem
                                )
                            }
                        }
                    )
                }
            )
        }
    )

    private fun validate(trail: LiteracyTrail): List<String> {
        val problems = mutableListOf<String>()
        if (trail.modules.isEmpty()) problems += "nenhum módulo"
        trail.modules.groupBy { it.id }.filterValues { it.size > 1 }.keys
            .forEach { problems += "módulo repetido: $it" }
        trail.phases.groupBy { it.id }.filterValues { it.size > 1 }.keys
            .forEach { problems += "fase repetida: $it" }
        trail.vocabulary.forEach { word ->
            if (word.syllables.joinToString("") != word.word) problems += "vocabulário ${word.word}: sílabas não formam a palavra"
            if (word.article != "O" && word.article != "A") problems += "vocabulário ${word.word}: artigo '${word.article}'"
        }
        trail.modules.forEach { module ->
            if (module.phases.isEmpty()) problems += "módulo ${module.id} sem fases"
            module.phases.forEach { phase ->
                if (phase.questions.isEmpty()) problems += "fase ${phase.id} sem perguntas"
                phase.questions.forEachIndexed { index, question ->
                    val where = "${phase.id} pergunta ${index + 1}"
                    if (question.prompt.isBlank()) problems += "$where: fala vazia"
                    if (question.type.usesOptions) {
                        if (question.answer !in question.options) problems += "$where: resposta não está nas opções"
                        if (question.options.size < 2) problems += "$where: precisa de pelo menos 2 opções"
                        if (question.options.toSet().size != question.options.size) problems += "$where: opções repetidas"
                    } else if (!canBuild(question.answer, question.pieces)) {
                        problems += "$where: não dá para formar a resposta com as peças"
                    }
                    if (question.type == QuestionType.PICTURE_WORD && question.image.isNullOrBlank()) {
                        problems += "$where: figura_palavra precisa de imagem"
                    }
                }
            }
        }
        return problems
    }

    /** true se [answer] pode ser montada juntando algumas das [pieces], cada uma usada no máximo uma vez. */
    internal fun canBuild(answer: String, pieces: List<String>): Boolean =
        answer.isNotEmpty() && LiteracyRules.solvePieces(answer, pieces) != null
}

// --- Formato do JSON (nomes em português, como o script gera) ---

@Serializable
private data class TrailDto(
    val versao: Int,
    @SerialName("palavras_de_apoio") val palavrasDeApoio: List<String>,
    val vocabulario: List<VocabularyDto>,
    val modulos: List<ModuleDto>
)

@Serializable
private data class VocabularyDto(
    val palavra: String,
    val silabas: List<String>,
    val imagem: String,
    val artigo: String
)

@Serializable
private data class ModuleDto(
    val id: String,
    val titulo: String,
    val ordem: Int,
    val fases: List<PhaseDto>
)

@Serializable
private data class PhaseDto(
    val id: String,
    val titulo: String,
    val gratis: Boolean,
    val ensina: List<String>,
    val perguntas: List<QuestionDto>
)

@Serializable
private data class QuestionDto(
    val tipo: String,
    val fala: String,
    val resposta: String,
    val opcoes: List<String> = emptyList(),
    val pecas: List<String> = emptyList(),
    val imagem: String? = null
)
