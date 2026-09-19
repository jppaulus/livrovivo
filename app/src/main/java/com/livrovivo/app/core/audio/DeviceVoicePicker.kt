package com.livrovivo.app.core.audio

/**
 * Qual voz do aparelho cada narrador usa. Regra pura, separada do Android para ser testada.
 *
 * Cada narrador tem uma voz fixa do Google ([VoicePersona.deviceVoice]). Escolher pela posição na
 * lista não serve: a lista muda de um aparelho para outro e até no mesmo aparelho (logo depois de
 * ligar, o Google ainda não lista a voz "pt-BR-language", e todos os narradores trocam de voz).
 */
internal object DeviceVoicePicker {

    /** "pt-br-x-pte-network" e "pt-br-x-pte-local" são a mesma pessoa: "pt-br-x-pte". */
    fun speakerId(voiceName: String): String =
        voiceName.lowercase().removeSuffix("-local").removeSuffix("-network")

    /**
     * Posição do falante do narrador em [speakerIds]: a voz fixa dele, quando o aparelho a tem;
     * senão, uma por posição (aparelhos sem as vozes do Google). -1 quando não há nenhuma voz.
     */
    fun pick(speakerIds: List<String>, preferred: String, ordinal: Int): Int = when {
        speakerIds.isEmpty() -> -1
        preferred in speakerIds -> speakerIds.indexOf(preferred)
        else -> ordinal % speakerIds.size
    }
}
