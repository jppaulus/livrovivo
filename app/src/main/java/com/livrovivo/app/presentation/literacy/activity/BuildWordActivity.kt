package com.livrovivo.app.presentation.literacy.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.livrovivo.app.presentation.literacy.ActivityState

/** "Montar a palavra": a criança vê a figura e junta as sílabas na ordem certa (BO + LA = BOLA). */
@Composable
fun BuildWordActivity(
    state: ActivityState,
    onPlace: (pieceIndex: Int, slot: Int?) -> Unit,
    onRemove: (slot: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        state.question.image?.let { LiteracyPicture(imageName = it, word = state.question.answer) }
        PieceBoard(state = state, onPlace = onPlace, onRemove = onRemove)
    }
}
