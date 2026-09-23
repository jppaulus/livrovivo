package com.livrovivo.app.presentation.literacy.activity

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.livrovivo.app.presentation.literacy.ActivityState

/** "Ouvir e tocar": a criança escuta uma letra ou sílaba e toca no botão certo. */
@Composable
fun ListenAndTapActivity(state: ActivityState, onChoose: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        OptionButtons(state = state, onChoose = onChoose)
    }
}
