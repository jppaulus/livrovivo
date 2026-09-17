package com.livrovivo.app.core.audio

/**
 * Personagens narradores. Cada um tem uma voz neural do Gemini, uma "direção de atuação"
 * para o narrador soar como um contador de histórias de verdade e um perfil para escolher
 * a voz da ElevenLabs automaticamente.
 */
enum class VoicePersona(
    val id: String,
    val title: String,
    val description: String,
    val emoji: String,
    val geminiVoice: String,
    val elevenLabsGender: String,
    val elevenLabsPreferredAges: List<String>,
    val character: String,
    val style: String,
    val pace: String,
    val deviceSpeechRate: Float,
    val devicePitch: Float
) {
    FADA(
        id = "fada",
        title = "Fada Encantada",
        description = "Doce, calorosa e acolhedora",
        emoji = "🌸",
        geminiVoice = "Sulafat",
        elevenLabsGender = "female",
        elevenLabsPreferredAges = listOf("young", "middle_aged", "middle aged"),
        character = "a loving fairy godmother who tells magical bedtime stories",
        style = "Warm, sweet and enchanting. You can hear the smile in her voice. Gentle wonder in magical moments, tender reassurance in scary moments.",
        pace = "Calm and unhurried, with soft pauses between paragraphs and a little suspense before surprises.",
        deviceSpeechRate = 0.95f,
        devicePitch = 1.1f
    ),
    URSINHO(
        id = "ursinho",
        title = "Ursinho Gentil",
        description = "Grave, calma e aconchegante",
        emoji = "🐻",
        geminiVoice = "Algieba",
        elevenLabsGender = "male",
        elevenLabsPreferredAges = listOf("middle_aged", "middle aged", "old"),
        character = "a big, gentle teddy bear who speaks like a warm hug",
        style = "Deep, velvety and cozy. Slow and soothing like a lullaby, with a kind chuckle in funny moments.",
        pace = "Slow and relaxed, with long comforting pauses. Perfect for bedtime.",
        deviceSpeechRate = 0.85f,
        devicePitch = 0.78f
    ),
    VOVO(
        id = "vovo",
        title = "Vovó Contadora",
        description = "Carinhosa, expressiva e divertida",
        emoji = "👵",
        geminiVoice = "Gacrux",
        elevenLabsGender = "female",
        elevenLabsPreferredAges = listOf("old", "middle_aged", "middle aged"),
        character = "an affectionate Brazilian grandmother who has told stories to her grandchildren for decades",
        style = "Expressive and playful, full of affection. She gives each character a slightly different little voice and savors the onomatopoeias.",
        pace = "Natural storytelling rhythm, slowing down for tender moments and speeding up a bit in exciting parts.",
        deviceSpeechRate = 0.86f,
        devicePitch = 0.96f
    ),
    AVENTUREIRO(
        id = "aventureiro",
        title = "Capitão Aventura",
        description = "Animado, vibrante e cheio de energia",
        emoji = "🧭",
        geminiVoice = "Puck",
        elevenLabsGender = "male",
        elevenLabsPreferredAges = listOf("young", "middle_aged", "middle aged"),
        character = "an enthusiastic explorer captain narrating an epic but kid-friendly adventure",
        style = "Lively, upbeat and dramatic in a fun way. Big wonder in discoveries, playful suspense, never scary.",
        pace = "Energetic but clear, with dramatic pauses before big reveals.",
        deviceSpeechRate = 1.06f,
        devicePitch = 1.02f
    );

    companion object {
        fun fromId(id: String?): VoicePersona = entries.find { it.id == id } ?: DEFAULT

        /** Narrador padrão do app: voz animada, vibrante e clara, que funciona bem em qualquer aparelho. */
        val DEFAULT = AVENTUREIRO
    }
}
