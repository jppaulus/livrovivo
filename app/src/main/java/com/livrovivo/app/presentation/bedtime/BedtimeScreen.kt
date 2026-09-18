package com.livrovivo.app.presentation.bedtime

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livrovivo.app.core.ai.ChapterSanitizer
import com.livrovivo.app.core.audio.AudioPlayerController
import com.livrovivo.app.core.audio.NarrationStatus
import com.livrovivo.app.core.bedtime.Bedtime
import com.livrovivo.app.core.settings.SettingsManager
import com.livrovivo.app.core.theme.FairyGold
import com.livrovivo.app.core.theme.FairyNight
import com.livrovivo.app.core.theme.FairyNightSurface
import com.livrovivo.app.core.theme.FairyPurple
import com.livrovivo.app.core.ui.CompanionAvatar
import com.livrovivo.app.core.ui.MagicalSparklesEffect
import com.livrovivo.app.domain.model.AgeGroup
import com.livrovivo.app.domain.model.MagicalCompanion
import com.livrovivo.app.domain.usecase.GetActiveChildUseCase
import com.livrovivo.app.domain.usecase.GetChildProfilesUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId

enum class RitualPhase { YAWN, BREATHE, GOODNIGHT, ASLEEP }

data class BedtimeUiState(
    val phase: RitualPhase = RitualPhase.YAWN,
    val childName: String = "",
    val companion: MagicalCompanion = MagicalCompanion.ALL.first(),
    /** Respiração atual (1 a [BedtimeScript.BREATHS]). */
    val breath: Int = 0,
    val inhaling: Boolean = true
)

/**
 * O ritual de dormir: o companheiro boceja, a criança respira três vezes com ele, vem o
 * boa-noite e o app "dorme" até as 6h — só um adulto, pelo portão, acorda antes.
 */
class BedtimeViewModel(
    private val childId: String,
    private val getChildProfilesUseCase: GetChildProfilesUseCase,
    private val getActiveChildUseCase: GetActiveChildUseCase,
    private val settingsManager: SettingsManager,
    private val audio: AudioPlayerController
) : ViewModel() {

    companion object {
        /** Tempo para o leitor terminar de sair de cena antes da primeira fala. */
        private const val SETTLE_MS = 1_200L

        /** Se a voz não responder, o ritual segue mesmo assim. */
        private const val MAX_LINE_MS = 25_000L
        private const val AFTER_GOODNIGHT_MS = 2_500L

        /** Depois do boa-noite, a caixinha toca mais um pouco e some devagar. */
        private const val MUSIC_HOLD_MS = 3 * 60_000L
        private const val MUSIC_FADE_MS = 60_000L

        private val LINE_DONE = setOf(NarrationStatus.ENDED, NarrationStatus.ERROR)
    }

    private val _uiState = MutableStateFlow(BedtimeUiState())
    val uiState: StateFlow<BedtimeUiState> = _uiState.asStateFlow()

    private var asleep = false

    init {
        viewModelScope.launch { runRitual() }
    }

    private suspend fun runRitual() {
        val child = getChildProfilesUseCase.byId(childId) ?: getActiveChildUseCase.getDirect()
        val name = child?.name ?: "Pequeno Leitor"
        val companion = MagicalCompanion.findById(child?.companionId)
        val listenerAge = AgeGroup.fromCode(child?.ageGroup).illustrationAge + " child"
        _uiState.update { it.copy(childName = name, companion = companion) }

        audio.startBedtimeMusic()
        delay(SETTLE_MS)
        speak("intro", BedtimeScript.introScript(companion, name), listenerAge)

        _uiState.update { it.copy(phase = RitualPhase.BREATHE) }
        repeat(BedtimeScript.BREATHS) { i ->
            _uiState.update { it.copy(breath = i + 1, inhaling = true) }
            delay(BedtimeScript.INHALE_MS)
            _uiState.update { it.copy(inhaling = false) }
            delay(BedtimeScript.EXHALE_MS)
        }

        _uiState.update { it.copy(phase = RitualPhase.GOODNIGHT) }
        speak("goodnight", BedtimeScript.goodnightScript(name), listenerAge)
        delay(AFTER_GOODNIGHT_MS)

        settingsManager.setSleepUntil(Bedtime.wakeUpAt(Instant.now(), ZoneId.systemDefault()).toEpochMilli())
        audio.fadeOutBedtimeMusic(holdMs = MUSIC_HOLD_MS, fadeMs = MUSIC_FADE_MS)
        asleep = true
        _uiState.update { it.copy(phase = RitualPhase.ASLEEP) }
    }

    /** Narra uma fala com o narrador escolhido e espera ela acabar. */
    private suspend fun speak(step: String, script: String, listenerAge: String) {
        val key = "${AudioPlayerController.BEDTIME_KEY_PREFIX}$step#${System.nanoTime()}"
        audio.load(
            key = key,
            text = ChapterSanitizer.stripAudioTags(script),
            script = script,
            mood = "sonolento",
            listenerAge = listenerAge,
            autoPlay = true
        )
        withTimeoutOrNull(MAX_LINE_MS) {
            audio.playbackState.first { it.chapterKey == key && it.status in LINE_DONE }
        }
    }

    override fun onCleared() {
        // A criança saiu antes do boa-noite: fala e música param junto.
        if (!asleep) {
            audio.stop()
            audio.stopBedtimeMusic()
        }
    }
}

@Composable
fun BedtimeScreen(viewModel: BedtimeViewModel) {
    val state by viewModel.uiState.collectAsState()

    // A tela não apaga no meio da respiração; depois do boa-noite o aparelho pode dormir.
    val view = LocalView.current
    DisposableEffect(state.phase) {
        view.keepScreenOn = state.phase != RitualPhase.ASLEEP
        onDispose { view.keepScreenOn = false }
    }

    val breathing = state.phase == RitualPhase.BREATHE
    val breathScale by animateFloatAsState(
        targetValue = if (breathing && state.inhaling) 1f else 0.62f,
        animationSpec = tween(
            durationMillis = (if (state.inhaling) BedtimeScript.INHALE_MS else BedtimeScript.EXHALE_MS).toInt(),
            easing = FastOutSlowInEasing
        ),
        label = "breath"
    )
    val dim by animateFloatAsState(
        targetValue = when (state.phase) {
            RitualPhase.GOODNIGHT -> 0.5f
            RitualPhase.ASLEEP -> 0.8f
            else -> 0f
        },
        animationSpec = tween(durationMillis = 6_000),
        label = "dim"
    )

    val (title, subtitle) = when (state.phase) {
        RitualPhase.YAWN -> "${state.companion.name} está com soninho… 🥱" to "Vamos respirar juntinhos?"
        RitualPhase.BREATHE -> {
            val caption = if (state.inhaling) BedtimeScript.INHALE_CAPTION else BedtimeScript.exhaleCaption(state.companion)
            caption to "Respiração ${state.breath} de ${BedtimeScript.BREATHS}"
        }
        RitualPhase.GOODNIGHT -> "Boa noite, ${state.childName} 🌙" to "Sonhe com as estrelas…"
        RitualPhase.ASLEEP -> "Zzz…" to "Até amanhã!"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FairyNight, FairyNightSurface, FairyNight)))
    ) {
        MagicalSparklesEffect(sparkleColor = FairyGold.copy(alpha = 0.3f))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                // O círculo cresce ao puxar o ar e encolhe ao soltar: é o guia de quem ainda não lê.
                Box(
                    modifier = Modifier
                        .size(250.dp)
                        .scale(breathScale)
                        .clip(CircleShape)
                        .background(FairyPurple.copy(alpha = if (breathing) 0.45f else 0.2f))
                )
                CompanionAvatar(
                    companion = state.companion,
                    size = 120.dp,
                    animated = state.phase == RitualPhase.YAWN
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )

            if (breathing) {
                Spacer(modifier = Modifier.height(18.dp))
                BreathDots(done = state.breath)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = dim))
        )
    }
}

@Composable
private fun BreathDots(done: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(BedtimeScript.BREATHS) { i ->
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (i < done) FairyGold else Color.White.copy(alpha = 0.25f))
            )
        }
    }
}
