package com.livrovivo.app.presentation.literacy.activity

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.livrovivo.app.presentation.literacy.ActivityState

/** "Ditado": a criança só escuta a palavra e escreve com as sílabas. Há 2 peças a mais, que não servem. */
@Composable
fun DictationActivity(
    state: ActivityState,
    onPlace: (pieceIndex: Int, slot: Int?) -> Unit,
    onRemove: (slot: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        PieceBoard(state = state, onPlace = onPlace, onRemove = onRemove)
    }
}
