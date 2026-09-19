package com.livrovivo.app.core.audio

/** O que toca por baixo da narração. O botão do leitor passa por eles nesta ordem. */
enum class BackgroundSound(val id: String, val label: String, val emoji: String) {
    /** Os sons do lugar e do clima de cada página (grilos, vento, ondas...). */
    AMBIENCE("ambiente", "Sons da página", "🍃"),

    /** A caixinha de música ("Brilha, Brilha, Estrelinha"). */
    LULLABY("ninar", "Música de ninar", "🎵"),

    OFF("desligado", "Sem som de fundo", "🔇");

    fun next(): BackgroundSound = entries[(ordinal + 1) % entries.size]

    companion object {
        /**
         * Quem tinha ligado a música de ninar antes desta opção existir continua com ela;
         * os demais passam a ouvir os sons da página.
         */
        fun fromStored(id: String?, legacyLullaby: Boolean?): BackgroundSound =
            entries.find { it.id == id } ?: if (legacyLullaby == true) LULLABY else AMBIENCE
    }
}
