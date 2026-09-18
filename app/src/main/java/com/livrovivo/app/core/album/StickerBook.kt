package com.livrovivo.app.core.album

import com.livrovivo.app.core.settings.SettingsManager
import com.livrovivo.app.domain.model.AlbumView
import com.livrovivo.app.domain.model.Sticker
import com.livrovivo.app.domain.model.StickerAlbum
import com.livrovivo.app.domain.repository.StoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/**
 * O álbum de figurinhas de cada criança: junta as regras ([StickerAlbum]) com o que já foi
 * colado (DataStore) e com as histórias dela.
 */
class StickerBook(
    private val storyRepository: StoryRepository,
    private val settingsManager: SettingsManager
) {

    /** O álbum da criança, atualizado quando ela lê ou cola figurinhas. */
    fun albumFlow(childId: String): Flow<AlbumView> =
        combine(storyRepository.getStoriesFlow(childId), settingsManager.settingsFlow) { stories, settings ->
            StickerAlbum.view(settings.collectedStickers[childId], stories, ZoneId.systemDefault())
        }

    /**
     * Chamado quando a página final de uma história aparece: cola o que a leitura garante e
     * devolve só o que é novidade. Chamar de novo para o mesmo final não repete a festa.
     */
    suspend fun collectAfterEnding(childId: String, storyId: String): List<Sticker> = collect(childId, storyId)

    /**
     * Garante no álbum tudo o que a leitura atual já dá, sem festa (ao abrir o álbum). Assim,
     * apagar histórias depois não tira figurinhas de ninguém.
     */
    suspend fun keep(childId: String) {
        collect(childId, currentStoryId = null)
    }

    private suspend fun collect(childId: String, currentStoryId: String?): List<Sticker> {
        val stories = storyRepository.getStoriesFlow(childId).first()
        val result = StickerAlbum.collect(
            collected = settingsManager.current().collectedStickers[childId],
            stories = stories,
            currentStoryId = currentStoryId,
            zone = ZoneId.systemDefault()
        )
        settingsManager.addCollectedStickers(childId, result.collected)
        return result.newlyCollected
    }
}
