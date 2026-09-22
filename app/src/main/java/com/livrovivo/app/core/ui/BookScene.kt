package com.livrovivo.app.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.livrovivo.app.R
import com.livrovivo.app.domain.model.SceneKind

/** Bundled editorial setting; available immediately, without network or generation. */
private val bookArtCache = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookScene(scene: SceneKind, modifier: Modifier = Modifier) {
    val resource = when (scene) {
        SceneKind.NIGHT -> R.drawable.book_night
        SceneKind.SCHOOL -> R.drawable.book_school
        SceneKind.SPACE -> R.drawable.book_space
        SceneKind.PARTY -> R.drawable.book_party
        SceneKind.HOME -> R.drawable.book_home
        SceneKind.OCEAN -> R.drawable.book_ocean
        SceneKind.DINOSAURS -> R.drawable.book_dinosaurs
        SceneKind.FOREST -> R.drawable.book_forest
    }
    val resources = LocalContext.current.resources
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val target = with(density) { maxWidth.roundToPx() }.coerceIn(320, 1448)
        val sample = if (target <= 724) 2 else 1
        val key = "$resource:$sample"
        val art by produceState(bookArtCache.get(key), key) {
            value = withContext(Dispatchers.IO) {
                bookArtCache.get(key) ?: BitmapFactory.decodeResource(resources, resource,
                    BitmapFactory.Options().apply { inSampleSize = sample; inScaled = false })
                    ?.asImageBitmap()?.also { bookArtCache.put(key, it) }
            }
        }
        art?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}
