package com.livrovivo.app.presentation.album

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livrovivo.app.core.album.StickerBook
import com.livrovivo.app.core.ui.StickerView
import com.livrovivo.app.domain.model.AlbumView
import com.livrovivo.app.domain.model.StickerPage
import com.livrovivo.app.domain.model.StickerSlot
import com.livrovivo.app.domain.usecase.GetChildProfilesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlbumUiState(
    val childName: String = "",
    val album: AlbumView? = null
)

class AlbumViewModel(
    private val childId: String,
    private val stickerBook: StickerBook,
    getChildProfilesUseCase: GetChildProfilesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumUiState())
    val uiState: StateFlow<AlbumUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val name = getChildProfilesUseCase.byId(childId)?.name.orEmpty()
            _uiState.update { it.copy(childName = name) }
            // O que a leitura já garante fica colado de vez, mesmo se histórias forem apagadas depois.
            stickerBook.keep(childId)
        }
        viewModelScope.launch {
            stickerBook.albumFlow(childId).collect { album -> _uiState.update { it.copy(album = album) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    viewModel: AlbumViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selected by remember { mutableStateOf<StickerSlot?>(null) }

    selected?.let { slot -> StickerDetailDialog(slot = slot, onDismiss = { selected = null }) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.childName.isBlank()) "Meu álbum" else "Álbum de ${uiState.childName}",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val album = uiState.album ?: return@Scaffold
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                    Text(
                        text = "${album.collectedCount} de ${album.total} figurinhas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { album.collectedCount.toFloat() / album.total },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Figurinhas se ganham lendo. Toque em uma para ver como ganhar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                    )
                }
            }

            StickerPage.entries.forEach { page ->
                val slots = album.page(page)
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.padding(top = 14.dp)) {
                        Text(
                            text = "${page.title} · ${slots.count { it.collected }} de ${slots.size}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = page.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                        )
                    }
                }
                items(slots, key = { it.sticker.id }) { slot ->
                    Column(
                        modifier = Modifier.clickable { selected = slot },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        StickerView(sticker = slot.sticker, collected = slot.collected)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (slot.collected) slot.sticker.caption else progressLabel(slot),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (slot.collected) 0.9f else 0.5f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Legenda de um espaço vazio: quanto falta, quando dá para contar; num mundo, o nome dele —
 * o número já está no espaço, e o nome ajuda a escolher a próxima história.
 */
private fun progressLabel(slot: StickerSlot): String =
    slot.progress?.let { (have, need) -> "$have de $need" } ?: slot.sticker.caption

@Composable
private fun StickerDetailDialog(slot: StickerSlot, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
        title = {
            Text(
                text = if (slot.collected) slot.sticker.title else "Figurinha nº ${slot.sticker.number}",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                StickerView(
                    sticker = slot.sticker,
                    collected = slot.collected,
                    size = 180.dp,
                    modifier = Modifier.width(180.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = if (slot.collected) "Conquistada lendo! ✨"
                    else "Como ganhar: ${slot.sticker.howToEarn.replaceFirstChar { it.lowercase() }}.",
                    textAlign = TextAlign.Center
                )
                slot.progress?.let { (have, need) ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Até agora: $have de $need.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    )
}
