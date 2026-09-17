package com.livrovivo.app.core.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private object LocalImageCache {
    private val cache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun get(key: String): ImageBitmap? = cache.get(key)

    fun load(path: String, maxSide: Int): ImageBitmap? {
        val file = File(path)
        if (!file.exists()) return null
        // Os arquivos de ilustração têm nome único por geração, então o caminho basta como chave.
        val key = keyFor(path, maxSide)
        cache.get(key)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        return bitmap.asImageBitmap().also { cache.put(key, it) }
    }

    fun keyFor(path: String, maxSide: Int): String = "$path|$maxSide"
}

/** Mostra uma imagem salva no aparelho, carregada fora da thread principal, com fade-in suave. */
@Composable
fun LocalImage(
    path: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    maxSide: Int = 1280,
    contentScale: ContentScale = ContentScale.Crop
) {
    val bitmap by produceState(
        initialValue = path?.let { LocalImageCache.get(LocalImageCache.keyFor(it, maxSide)) },
        key1 = path,
        key2 = maxSide
    ) {
        value = if (path == null) null else withContext(Dispatchers.IO) {
            try {
                LocalImageCache.load(path, maxSide)
            } catch (_: Exception) {
                null
            }
        }
    }

    AnimatedVisibility(visible = bitmap != null, enter = fadeIn(tween(700)), modifier = modifier) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
