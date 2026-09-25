package com.livrovivo.app.presentation.literacy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livrovivo.app.core.literacy.TrailPhrases
import com.livrovivo.app.core.theme.FairyCoral
import com.livrovivo.app.core.theme.FairyEmerald
import com.livrovivo.app.core.theme.FairyGold
import com.livrovivo.app.core.theme.FairyPurple
import com.livrovivo.app.core.ui.MagicalSparklesEffect

/** Cor de cada parada do caminho, na ordem dos módulos. */
internal val ModuleColors = listOf(FairyPurple, FairyCoral, FairyEmerald, Color(0xFF3AA0D8), Color(0xFFE08A00))

internal fun moduleColor(index: Int): Color = ModuleColors[index.mod(ModuleColors.size)]

private val StopHeight = 190.dp
private val StopSize = 116.dp

/** Mapa da Trilha da Leitura: os módulos em um caminho, com cadeado e estrelas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrailScreen(
    viewModel: LiteracyViewModel,
    onNavigateBack: () -> Unit,
    onOpenModule: (moduleId: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    DisposableEffect(viewModel) { onDispose { viewModel.stopNarration() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Aprender a ler", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        if (uiState.totalPhases > 0) {
                            Text(
                                "${uiState.completedPhases} de ${uiState.totalPhases} fases",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            MagicalSparklesEffect()
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.error != null -> Text(
                    uiState.error!!,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    textAlign = TextAlign.Center
                )
                else -> TrailMap(
                    modules = uiState.modules,
                    onOpen = { module ->
                        if (module.isLocked) viewModel.speak(TrailPhrases.moduleLocked(module.module.title))
                        else onOpenModule(module.module.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun TrailMap(modules: List<ModuleUi>, onOpen: (ModuleUi) -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        val width = maxWidth
        val totalHeight = StopHeight * modules.size + 40.dp
        val pathColor = FairyGold.copy(alpha = 0.7f)
        Box(modifier = Modifier.fillMaxWidth().height(totalHeight)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centers = modules.indices.map { index -> stopCenter(index, width, StopHeight).let { Offset(it.first.toPx(), it.second.toPx()) } }
                if (centers.size > 1) {
                    val path = Path().apply {
                        moveTo(centers.first().x, centers.first().y)
                        centers.zipWithNext().forEach { (from, to) ->
                            val midY = (from.y + to.y) / 2
                            cubicTo(from.x, midY, to.x, midY, to.x, to.y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = pathColor,
                        style = Stroke(
                            width = 14.dp.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 26.dp.toPx()))
                        )
                    )
                }
            }
            modules.forEachIndexed { index, module ->
                val (x, y) = stopCenter(index, width, StopHeight)
                TrailStop(
                    module = module,
                    color = moduleColor(index),
                    onClick = { onOpen(module) },
                    modifier = Modifier.offset(x = x - 90.dp, y = y - StopSize / 2)
                )
            }
        }
    }
}

/** Paradas em zigue-zague: esquerda, direita, esquerda... */
private fun stopCenter(index: Int, width: Dp, height: Dp): Pair<Dp, Dp> {
    val x = if (index % 2 == 0) width * 0.32f else width * 0.68f
    val y = height * index + height / 2 + 10.dp
    return x to y
}

@Composable
private fun TrailStop(module: ModuleUi, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val locked = module.isLocked
    Column(
        modifier = modifier
            .width(180.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = buildString {
                    append(module.module.title)
                    if (locked) append(", bloqueado") else append(", ${module.completedPhases} de ${module.phases.size} fases")
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(StopSize)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    if (locked) Brush.radialGradient(listOf(Color(0xFFD9D6E3), Color(0xFFB9B4C9)))
                    else Brush.radialGradient(listOf(lerp(color, Color.White, 0.22f), color))
                )
                .border(5.dp, if (locked) Color.White.copy(alpha = 0.6f) else FairyGold, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (locked) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
            } else {
                Text(
                    text = module.symbol,
                    fontSize = if (module.symbol.length > 2) 26.sp else 44.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }
        Text(
            text = module.module.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            text = "⭐ ${module.stars} de ${module.maxStars}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
    }
}

/** Três estrelinhas, cheias até [stars]. */
@Composable
fun StarRow(stars: Int, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.semantics { contentDescription = "$stars de 3 estrelas" },
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(3) { index ->
            Text(
                text = "★",
                fontSize = (size.value).sp,
                color = if (index < stars) FairyGold else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f)
            )
        }
    }
}
