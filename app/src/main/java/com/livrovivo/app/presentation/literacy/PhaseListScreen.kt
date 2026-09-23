package com.livrovivo.app.presentation.literacy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livrovivo.app.core.theme.FairyGold
import com.livrovivo.app.core.ui.InfoPill

/** Fases de um módulo, com estrelas, cadeado e o selo das fases de assinantes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhaseListScreen(
    viewModel: LiteracyViewModel,
    moduleId: String,
    onNavigateBack: () -> Unit,
    onOpenPhase: (phaseId: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val moduleUi = uiState.module(moduleId)
    val moduleIndex = uiState.modules.indexOfFirst { it.module.id == moduleId }
    val color = moduleColor(moduleIndex.coerceAtLeast(0))
    DisposableEffect(viewModel) { onDispose { viewModel.stopNarration() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        moduleUi?.module?.title ?: "",
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar para a trilha")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (moduleUi == null) {
                if (uiState.isLoading) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                return@Box
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(moduleUi.phases, key = { it.phase.id }) { phase ->
                    PhaseCard(
                        phase = phase,
                        color = color,
                        onClick = {
                            if (phase.isLocked) viewModel.speak("Termine a fase de antes para abrir esta.")
                            else onOpenPhase(phase.phase.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PhaseCard(phase: PhaseUi, color: Color, onClick: () -> Unit) {
    val locked = phase.isLocked
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, if (phase.stars > 0) FairyGold else color.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = buildString {
                    append("Fase ${phase.number}, ${phase.phase.title}")
                    if (locked) append(", bloqueada") else append(", ${phase.stars} estrelas")
                    if (phase.isSubscriberOnly) append(", para assinantes")
                }
            }
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (locked) Color(0xFFBDB8CC) else color),
            contentAlignment = Alignment.Center
        ) {
            if (locked) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            } else {
                Text("${phase.number}", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
        Text(
            text = phase.phase.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        StarRow(stars = phase.stars, modifier = Modifier.padding(top = 4.dp))
        if (phase.isSubscriberOnly) {
            InfoPill(
                text = "Assinantes",
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
