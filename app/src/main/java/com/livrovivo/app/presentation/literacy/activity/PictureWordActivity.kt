package com.livrovivo.app.presentation.literacy.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.livrovivo.app.presentation.literacy.ActivityState

/** "Figura e palavra": a criança vê a figura e toca na palavra que dá nome a ela. */
@Composable
fun PictureWordActivity(state: ActivityState, onChoose: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        state.question.image?.let { LiteracyPicture(imageName = it, word = state.question.answer) }
        OptionButtons(state = state, onChoose = onChoose)
    }
}
