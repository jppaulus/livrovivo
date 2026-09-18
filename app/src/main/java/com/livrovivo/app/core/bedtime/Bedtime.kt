package com.livrovivo.app.core.bedtime

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Emite agora e depois a cada minuto: o modo da noite muda com o relógio, não só com dados. */
fun minuteTicks(periodMs: Long = 60_000L): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(periodMs)
    }
}

/**
 * O que os pais escolheram para a hora de dormir.
 *
 * [startMinutes] é o minuto do dia em que ela começa (null = ritual desligado).
 * [storiesPerNight] é quantas histórias a criança termina antes de o boa-noite virar o único
 * caminho (null = sem limite: o boa-noite só aparece em destaque).
 */
data class BedtimeSettings(
    val startMinutes: Int? = Bedtime.DEFAULT_START_MINUTES,
    val storiesPerNight: Int? = null
) {
    val isEnabled: Boolean get() = startMinutes != null
}

/** Em que ponto da noite a criança está. */
enum class BedtimeMode {
    /** Fora da hora de dormir (ou ritual desligado): tudo como sempre. */
    OFF,

    /** Hora de dormir: o boa-noite aparece em destaque, mas ainda dá para ler outra. */
    OFFER,

    /** Limite da noite atingido: o boa-noite é o único caminho. */
    REQUIRED
}

/**
 * Regras de horário da hora de dormir. A noite começa no horário dos pais e vai até as 6h
 * do dia seguinte, quando o app "acorda" sozinho.
 */
object Bedtime {
    const val DEFAULT_START_MINUTES = 19 * 60 + 30
    const val WAKE_UP_MINUTES = 6 * 60

    /** Quantos fins de história guardar por criança: o bastante para contar uma noite. */
    const val ENDINGS_TO_KEEP = 10

    val START_OPTIONS: List<Int> = listOf(
        18 * 60 + 30, 19 * 60, 19 * 60 + 30, 20 * 60, 20 * 60 + 30, 21 * 60, 21 * 60 + 30
    )
    val STORIES_OPTIONS: List<Int?> = listOf(null, 1, 2, 3)

    /** 19 * 60 + 30 → "19h30"; 20 * 60 → "20h". */
    fun label(minutes: Int): String {
        val hours = minutes / 60
        val rest = minutes % 60
        return if (rest == 0) "${hours}h" else "${hours}h${rest.toString().padStart(2, '0')}"
    }

    fun isBedtime(settings: BedtimeSettings, now: Instant, zone: ZoneId): Boolean =
        nightStartedAt(settings, now, zone) != null

    /**
     * Começo da noite que contém [now], ou null se agora não é hora de dormir.
     * Às 2h da madrugada, a noite começou no dia anterior.
     */
    fun nightStartedAt(settings: BedtimeSettings, now: Instant, zone: ZoneId): Instant? {
        val start = settings.startMinutes ?: return null
        val local = now.atZone(zone)
        val minutes = local.hour * 60 + local.minute
        val date = when {
            minutes >= start -> local.toLocalDate()
            minutes < WAKE_UP_MINUTES -> local.toLocalDate().minusDays(1)
            else -> return null
        }
        return date.atTime(LocalTime.of(start / 60, start % 60)).atZone(zone).toInstant()
    }

    /** Quando o app acorda depois do boa-noite: as 6h seguintes. */
    fun wakeUpAt(now: Instant, zone: ZoneId): Instant {
        val local = now.atZone(zone)
        val minutes = local.hour * 60 + local.minute
        val date = if (minutes < WAKE_UP_MINUTES) local.toLocalDate() else local.toLocalDate().plusDays(1)
        return date.atTime(LocalTime.of(WAKE_UP_MINUTES / 60, WAKE_UP_MINUTES % 60)).atZone(zone).toInstant()
    }

    /** Quantas histórias a criança terminou nesta noite. */
    fun storiesTonight(endings: List<Long>, settings: BedtimeSettings, now: Instant, zone: ZoneId): Int {
        val start = nightStartedAt(settings, now, zone)?.toEpochMilli() ?: return 0
        val nowMs = now.toEpochMilli()
        return endings.count { it in start..nowMs }
    }

    fun mode(settings: BedtimeSettings, endings: List<Long>, now: Instant, zone: ZoneId): BedtimeMode {
        if (!isBedtime(settings, now, zone)) return BedtimeMode.OFF
        val limit = settings.storiesPerNight ?: return BedtimeMode.OFFER
        return if (storiesTonight(endings, settings, now, zone) >= limit) BedtimeMode.REQUIRED else BedtimeMode.OFFER
    }

    /** Registra o fim de uma história, guardando só os mais recentes de cada criança. */
    fun withEnding(endings: Map<String, List<Long>>, childId: String, at: Long): Map<String, List<Long>> =
        endings + (childId to (endings[childId].orEmpty() + at).sorted().takeLast(ENDINGS_TO_KEEP))
}
