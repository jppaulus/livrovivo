package com.livrovivo.app.core.literacy

import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.model.DecodableBook
import com.livrovivo.app.domain.model.LiteracyKnowledge
import com.livrovivo.app.domain.model.LiteracyTrail
import com.livrovivo.app.domain.model.MagicalCompanion

/** Livro escrito e de onde ele veio. */
data class WrittenBook(val book: DecodableBook, val isOffline: Boolean)

/**
 * Escreve os livros "Eu leio". Por enquanto só com o [OfflineDecodableEngine]; a etapa 6 põe a IA na
 * frente e deixa o motor offline como garantia (a mesma ideia do StoryWriter das aventuras).
 */
class LiteracyBookWriter {

    suspend fun write(
        trail: LiteracyTrail,
        knowledge: LiteracyKnowledge,
        child: ChildProfile,
        seed: Int,
        focusSyllables: Set<String>
    ): WrittenBook? {
        val companion = MagicalCompanion.findById(child.companionId).name
        return OfflineDecodableEngine.write(trail, knowledge, child.name, companion, seed, focusSyllables)
            ?.let { WrittenBook(it, isOffline = true) }
    }
}
