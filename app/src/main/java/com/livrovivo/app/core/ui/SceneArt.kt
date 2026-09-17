package com.livrovivo.app.core.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livrovivo.app.domain.model.SceneKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ilustração desenhada pelo próprio app (sem internet), usada como capa, como cena offline
 * e como fundo enquanto a IA pinta a ilustração da página.
 */
@Composable
fun ProceduralScene(
    scene: SceneKind,
    modifier: Modifier = Modifier,
    companionEmoji: String? = null,
    animate: Boolean = true
) {
    val phase: Float
    val bob: Float
    if (animate) {
        val transition = rememberInfiniteTransition(label = "scene")
        val p by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
            label = "phase"
        )
        val b by transition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
            label = "bob"
        )
        phase = p
        bob = b
    } else {
        phase = 0.3f
        bob = 0f
    }

    val stars = remember { List(60) { Random(it * 31 + 7).let { r -> Triple(r.nextFloat(), r.nextFloat() * 0.62f, r.nextFloat()) } } }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (scene) {
                SceneKind.NIGHT -> drawNight(phase, stars)
                SceneKind.SPACE -> drawSpace(phase, stars)
                SceneKind.SCHOOL -> drawSchool(phase)
                SceneKind.PARTY -> drawParty(phase)
                SceneKind.HOME -> drawHome(phase)
                SceneKind.OCEAN -> drawOcean(phase)
                SceneKind.DINOSAURS -> drawDinosaurs(phase)
                SceneKind.FOREST -> drawForest(phase)
            }
        }
        if (companionEmoji != null) {
            Text(
                text = companionEmoji,
                fontSize = 46.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 10.dp)
                    .graphicsLayer { translationY = bob * 6.dp.toPx() }
            )
        }
    }
}

private fun twinkle(phase: Float, seed: Float): Float = 0.35f + 0.65f * abs(sin((phase * 2f * PI.toFloat() * 3f) + seed * 10f))

private fun DrawScope.skyGradient(vararg colors: Long) {
    drawRect(Brush.verticalGradient(colors.map { Color(it) }))
}

private fun DrawScope.drawStars(phase: Float, stars: List<Triple<Float, Float, Float>>, count: Int) {
    stars.take(count).forEach { (x, y, s) ->
        drawCircle(
            color = Color.White.copy(alpha = twinkle(phase, s)),
            radius = (1.2f + s * 2.2f) * density,
            center = Offset(x * size.width, y * size.height)
        )
    }
}

private fun DrawScope.hill(baseY: Float, peakY: Float, color: Color, shift: Float = 0f) {
    val path = Path().apply {
        moveTo(0f, size.height)
        lineTo(0f, baseY)
        quadraticTo(size.width * (0.3f + shift), peakY, size.width * 0.6f, baseY - (baseY - peakY) * 0.3f)
        quadraticTo(size.width * (0.85f + shift), peakY + (baseY - peakY) * 0.5f, size.width, baseY)
        lineTo(size.width, size.height)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.cloud(center: Offset, scale: Float, alpha: Float = 0.95f) {
    val c = Color.White.copy(alpha = alpha)
    drawCircle(c, 22f * scale, center)
    drawCircle(c, 16f * scale, center + Offset(-22f * scale, 6f * scale))
    drawCircle(c, 17f * scale, center + Offset(22f * scale, 5f * scale))
    drawRoundRect(c, center + Offset(-36f * scale, 2f * scale), Size(72f * scale, 18f * scale), CornerRadius(9f * scale))
}

private fun DrawScope.heart(center: Offset, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y + size * 0.35f)
        cubicTo(center.x - size, center.y - size * 0.35f, center.x - size * 0.45f, center.y - size, center.x, center.y - size * 0.4f)
        cubicTo(center.x + size * 0.45f, center.y - size, center.x + size, center.y - size * 0.35f, center.x, center.y + size * 0.35f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawNight(phase: Float, stars: List<Triple<Float, Float, Float>>) {
    skyGradient(0xFF1B1446, 0xFF33287A, 0xFF5A4A9C)
    drawStars(phase, stars, 45)
    val moonCenter = Offset(size.width * 0.78f, size.height * 0.24f)
    val r = size.minDimension * 0.11f
    drawCircle(Brush.radialGradient(listOf(Color(0x55FFF4C2), Color.Transparent), moonCenter, r * 2.6f), r * 2.6f, moonCenter)
    drawCircle(Color(0xFFFFF4C2), r, moonCenter)
    drawCircle(Color(0x22B8A76A), r * 0.22f, moonCenter + Offset(-r * 0.3f, -r * 0.2f))
    drawCircle(Color(0x22B8A76A), r * 0.14f, moonCenter + Offset(r * 0.35f, r * 0.3f))
    hill(size.height * 0.78f, size.height * 0.6f, Color(0xFF2B2266))
    hill(size.height * 0.9f, size.height * 0.72f, Color(0xFF1F1850), shift = -0.15f)
    // Casinha com janela acesa
    val houseX = size.width * 0.16f
    val houseY = size.height * 0.62f
    val w = size.width * 0.16f
    drawRect(Color(0xFF16113A), Offset(houseX, houseY), Size(w, w * 0.75f))
    val roof = Path().apply {
        moveTo(houseX - w * 0.1f, houseY)
        lineTo(houseX + w / 2, houseY - w * 0.5f)
        lineTo(houseX + w * 1.1f, houseY)
        close()
    }
    drawPath(roof, Color(0xFF16113A))
    val window = Offset(houseX + w * 0.32f, houseY + w * 0.2f)
    drawCircle(Brush.radialGradient(listOf(Color(0x66FFD36B), Color.Transparent), window + Offset(w * 0.18f, w * 0.15f), w * 0.6f), w * 0.6f, window + Offset(w * 0.18f, w * 0.15f))
    drawRect(Color(0xFFFFD36B), window, Size(w * 0.36f, w * 0.3f))
}

private fun DrawScope.drawSpace(phase: Float, stars: List<Triple<Float, Float, Float>>) {
    skyGradient(0xFF0B1026, 0xFF1E1650, 0xFF3A1F5C)
    drawStars(phase, stars.map { Triple(it.first, it.second / 0.62f, it.third) }, 60)
    val planet = Offset(size.width * 0.24f, size.height * 0.72f)
    val pr = size.minDimension * 0.22f
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFB3D9), Color(0xFFB15BD6)), planet - Offset(pr * 0.3f, pr * 0.3f), pr * 1.4f), pr, planet)
    rotate(-18f, planet) {
        drawOval(Color(0xCCFFE0A3), planet - Offset(pr * 1.6f, pr * 0.28f), Size(pr * 3.2f, pr * 0.56f), style = Stroke(width = pr * 0.1f))
    }
    val small = Offset(size.width * 0.84f, size.height * 0.2f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFB3ECFF), Color(0xFF2D9CDB)), small, size.minDimension * 0.08f), size.minDimension * 0.07f, small)
    // Foguete flutuando
    val cx = size.width * 0.62f + sin(phase * 2 * PI.toFloat()) * size.width * 0.03f
    val cy = size.height * 0.46f + cos(phase * 2 * PI.toFloat()) * size.height * 0.03f
    val s = size.minDimension * 0.1f
    rotate(28f, Offset(cx, cy)) {
        val flame = 0.6f + 0.4f * abs(sin(phase * 40f))
        val flamePath = Path().apply {
            moveTo(cx - s * 0.3f, cy + s * 0.9f)
            lineTo(cx, cy + s * (0.9f + 0.9f * flame))
            lineTo(cx + s * 0.3f, cy + s * 0.9f)
            close()
        }
        drawPath(flamePath, Color(0xFFFFB300))
        drawRoundRect(Color(0xFFF5F3FF), Offset(cx - s * 0.38f, cy - s * 0.8f), Size(s * 0.76f, s * 1.75f), CornerRadius(s * 0.38f))
        drawCircle(Color(0xFF5E49E2), s * 0.2f, Offset(cx, cy - s * 0.2f))
        drawCircle(Color(0xFFB3ECFF), s * 0.12f, Offset(cx, cy - s * 0.2f))
        val fin = Path().apply {
            moveTo(cx - s * 0.38f, cy + s * 0.3f)
            lineTo(cx - s * 0.75f, cy + s * 0.95f)
            lineTo(cx - s * 0.38f, cy + s * 0.8f)
            close()
            moveTo(cx + s * 0.38f, cy + s * 0.3f)
            lineTo(cx + s * 0.75f, cy + s * 0.95f)
            lineTo(cx + s * 0.38f, cy + s * 0.8f)
            close()
        }
        drawPath(fin, Color(0xFFFF6584))
    }
}

private fun DrawScope.drawSchool(phase: Float) {
    skyGradient(0xFF8FD3FF, 0xFFD6F0FF, 0xFFFFF6DA)
    val sun = Offset(size.width * 0.86f, size.height * 0.17f)
    val sr = size.minDimension * 0.08f
    rotate(phase * 360f, sun) {
        repeat(10) { i ->
            val angle = i * 36f * PI.toFloat() / 180f
            drawLine(Color(0xFFFFC94D), sun + Offset(cos(angle) * sr * 1.3f, sin(angle) * sr * 1.3f), sun + Offset(cos(angle) * sr * 1.8f, sin(angle) * sr * 1.8f), strokeWidth = 4f * density)
        }
    }
    drawCircle(Color(0xFFFFD54F), sr, sun)
    cloud(Offset(((phase * 1.2f + 0.1f) % 1.3f - 0.15f) * size.width, size.height * 0.2f), size.minDimension / 260f)
    cloud(Offset(((phase * 1.2f + 0.7f) % 1.3f - 0.15f) * size.width, size.height * 0.33f), size.minDimension / 340f, 0.85f)
    drawRect(Color(0xFF8BCF86), Offset(0f, size.height * 0.76f), Size(size.width, size.height * 0.24f))
    drawRect(Color(0xFF7CC377), Offset(0f, size.height * 0.76f), Size(size.width, size.height * 0.03f))
    // Escola
    val left = size.width * 0.3f
    val top = size.height * 0.44f
    val w = size.width * 0.42f
    val h = size.height * 0.34f
    drawRect(Color(0xFFFF8A65), Offset(left, top), Size(w, h))
    val roof = Path().apply {
        moveTo(left - w * 0.06f, top)
        lineTo(left + w / 2, top - h * 0.45f)
        lineTo(left + w * 1.06f, top)
        close()
    }
    drawPath(roof, Color(0xFFD84315))
    drawCircle(Color(0xFFFFF59D), h * 0.1f, Offset(left + w / 2, top - h * 0.16f))
    drawRoundRect(Color(0xFF6D4C41), Offset(left + w * 0.42f, top + h * 0.5f), Size(w * 0.16f, h * 0.5f), CornerRadius(8f))
    listOf(0.12f, 0.7f).forEach { fx ->
        listOf(0.18f, 0.55f).forEach { fy ->
            drawRect(Color(0xFFFFF9C4), Offset(left + w * fx, top + h * fy), Size(w * 0.18f, h * 0.22f))
        }
    }
    // Bandeira ao vento
    val poleX = left + w * 0.5f
    drawLine(Color(0xFF5D4037), Offset(poleX, top - h * 0.45f), Offset(poleX, top - h * 0.95f), strokeWidth = 3f * density)
    val wave = sin(phase * 2 * PI.toFloat() * 4) * 4f * density
    val flag = Path().apply {
        moveTo(poleX, top - h * 0.95f)
        quadraticTo(poleX + w * 0.1f, top - h * 0.95f + wave, poleX + w * 0.2f, top - h * 0.9f)
        lineTo(poleX + w * 0.2f, top - h * 0.75f)
        quadraticTo(poleX + w * 0.1f, top - h * 0.8f - wave, poleX, top - h * 0.78f)
        close()
    }
    drawPath(flag, Color(0xFF5E49E2))
    // Árvore
    val tree = Offset(size.width * 0.12f, size.height * 0.62f)
    drawRect(Color(0xFF8D6E63), tree + Offset(-8f * density, 0f), Size(16f * density, size.height * 0.16f))
    drawCircle(Color(0xFF66BB6A), size.minDimension * 0.1f, tree)
    drawCircle(Color(0xFF81C784), size.minDimension * 0.07f, tree + Offset(-size.minDimension * 0.07f, size.minDimension * 0.03f))
}

private fun DrawScope.drawParty(phase: Float) {
    skyGradient(0xFFFFE0EC, 0xFFFFF0DC, 0xFFFFF7E6)
    val palette = listOf(0xFFFF6584, 0xFFFFB300, 0xFF5E49E2, 0xFF10B981, 0xFF4FC3F7).map { Color(it) }
    // Bandeirinhas
    val count = 11
    repeat(count) { i ->
        val x0 = size.width * i / count
        val x1 = size.width * (i + 1) / count
        val sag = { x: Float -> size.height * 0.08f + sin(x / size.width * PI.toFloat()) * size.height * 0.08f }
        val tri = Path().apply {
            moveTo(x0, sag(x0))
            lineTo(x1, sag(x1))
            lineTo((x0 + x1) / 2, sag((x0 + x1) / 2) + size.height * 0.1f)
            close()
        }
        drawPath(tri, palette[i % palette.size])
    }
    // Balões
    listOf(0.15f to 0f, 0.8f to 0.4f, 0.62f to 0.75f).forEachIndexed { i, (fx, offset) ->
        val y = size.height * (0.42f + 0.05f * sin((phase + offset) * 2 * PI.toFloat()))
        val center = Offset(size.width * fx, y)
        val bw = size.minDimension * 0.1f
        drawLine(Color(0x99795548), center + Offset(0f, bw * 1.2f), center + Offset(bw * 0.2f, bw * 3.2f), strokeWidth = 2f * density)
        drawOval(palette[(i + 2) % palette.size], center - Offset(bw, bw * 1.2f), Size(bw * 2, bw * 2.4f))
        drawOval(Color.White.copy(alpha = 0.35f), center - Offset(bw * 0.6f, bw * 0.9f), Size(bw * 0.5f, bw * 0.7f))
    }
    // Confete
    val random = Random(3)
    repeat(28) { i ->
        val x = random.nextFloat() * size.width
        val y = ((random.nextFloat() + phase * (0.6f + random.nextFloat())) % 1f) * size.height
        rotate(phase * 720f + i * 40f, Offset(x, y)) {
            drawRect(palette[i % palette.size], Offset(x, y), Size(6f * density, 10f * density))
        }
    }
    // Blocos de brinquedo
    val block = size.minDimension * 0.13f
    val baseY = size.height - block
    listOf(0.3f, 0.3f + block / size.width).forEachIndexed { i, fx ->
        drawRoundRect(palette[i], Offset(size.width * fx, baseY), Size(block, block), CornerRadius(8f))
    }
    drawRoundRect(palette[3], Offset(size.width * 0.3f + block / 2, baseY - block), Size(block, block), CornerRadius(8f))
}

private fun DrawScope.drawHome(phase: Float) {
    skyGradient(0xFFFFD6E5, 0xFFFFE9DA, 0xFFFFF6EC)
    hill(size.height * 0.8f, size.height * 0.66f, Color(0xFFA8E6A1))
    val left = size.width * 0.34f
    val top = size.height * 0.46f
    val w = size.width * 0.32f
    val h = size.height * 0.3f
    drawRect(Color(0xFFFFF8E1), Offset(left, top), Size(w, h))
    val roof = Path().apply {
        moveTo(left - w * 0.1f, top)
        lineTo(left + w / 2, top - h * 0.55f)
        lineTo(left + w * 1.1f, top)
        close()
    }
    drawPath(roof, Color(0xFFE57373))
    drawRoundRect(Color(0xFF8D6E63), Offset(left + w * 0.62f, top + h * 0.45f), Size(w * 0.2f, h * 0.55f), CornerRadius(10f))
    heart(Offset(left + w * 0.3f, top + h * 0.42f), w * 0.14f, Color(0xFFFF6584))
    // Corações flutuando
    repeat(6) { i ->
        val t = (phase * 1.5f + i / 6f) % 1f
        val x = size.width * (0.2f + 0.12f * i) + sin((t + i) * 6f) * 10f * density
        val y = size.height * (0.75f - t * 0.7f)
        heart(Offset(x, y), size.minDimension * 0.03f, Color(0xFFFF6584).copy(alpha = (1f - t) * 0.8f))
    }
    cloud(Offset(size.width * 0.15f, size.height * 0.18f), size.minDimension / 300f)
}

private fun DrawScope.drawOcean(phase: Float) {
    skyGradient(0xFF8BE0FF, 0xFF2C9AD8, 0xFF0D4F8C)
    repeat(4) { i ->
        val x = size.width * (0.1f + 0.25f * i)
        val ray = Path().apply {
            moveTo(x, 0f)
            lineTo(x + size.width * 0.08f, 0f)
            lineTo(x + size.width * 0.2f, size.height * 0.8f)
            lineTo(x + size.width * 0.05f, size.height * 0.8f)
            close()
        }
        drawPath(ray, Color.White.copy(alpha = 0.07f))
    }
    val sand = Path().apply {
        moveTo(0f, size.height)
        lineTo(0f, size.height * 0.84f)
        quadraticTo(size.width * 0.5f, size.height * 0.76f, size.width, size.height * 0.86f)
        lineTo(size.width, size.height)
        close()
    }
    drawPath(sand, Color(0xFFF3D9A4))
    // Algas balançando
    repeat(5) { i ->
        val baseX = size.width * (0.06f + 0.22f * i)
        val sway = sin((phase * 2 * PI.toFloat() * 2) + i) * 12f * density
        val weed = Path().apply {
            moveTo(baseX, size.height * 0.9f)
            quadraticTo(baseX + sway, size.height * 0.7f, baseX - sway * 0.5f, size.height * 0.55f)
        }
        drawPath(weed, Color(0xFF2E9E5B), style = Stroke(width = 7f * density))
    }
    drawCircle(Color(0xFFFF8A80), size.minDimension * 0.05f, Offset(size.width * 0.78f, size.height * 0.84f))
    drawCircle(Color(0xFFFFAB91), size.minDimension * 0.035f, Offset(size.width * 0.83f, size.height * 0.8f))
    // Peixinhos
    listOf(Triple(0.35f, 0.4f, Color(0xFFFFB300)), Triple(0.55f, 0.58f, Color(0xFFFF6584)), Triple(0.15f, 0.3f, Color(0xFFB2FF59))).forEachIndexed { i, (offset, fy, color) ->
        val x = ((phase * (0.8f + i * 0.2f) + offset) % 1.2f - 0.1f) * size.width
        val y = size.height * fy + sin(phase * 20f + i) * 4f * density
        val fs = size.minDimension * 0.05f
        drawOval(color, Offset(x - fs, y - fs * 0.6f), Size(fs * 2, fs * 1.2f))
        val tail = Path().apply {
            moveTo(x - fs * 0.9f, y)
            lineTo(x - fs * 1.7f, y - fs * 0.6f)
            lineTo(x - fs * 1.7f, y + fs * 0.6f)
            close()
        }
        drawPath(tail, color)
        drawCircle(Color(0xFF1B1446), fs * 0.15f, Offset(x + fs * 0.5f, y - fs * 0.15f))
    }
    // Bolhas
    repeat(12) { i ->
        val t = (phase * 1.3f + i / 12f) % 1f
        val x = size.width * ((i * 37 % 100) / 100f)
        val y = size.height * (0.95f - t)
        drawCircle(Color.White.copy(alpha = 0.5f * (1f - t)), (3f + i % 4) * density, Offset(x, y), style = Stroke(width = 1.5f * density))
    }
}

private fun DrawScope.drawDinosaurs(phase: Float) {
    skyGradient(0xFFFFE3A3, 0xFFFFF1C9, 0xFFD9F2C4)
    // Vulcão com fumacinha
    val vx = size.width * 0.78f
    val vTop = size.height * 0.38f
    val volcano = Path().apply {
        moveTo(vx - size.width * 0.22f, size.height * 0.8f)
        lineTo(vx - size.width * 0.05f, vTop)
        lineTo(vx + size.width * 0.05f, vTop)
        lineTo(vx + size.width * 0.22f, size.height * 0.8f)
        close()
    }
    drawPath(volcano, Color(0xFF8D6E63))
    repeat(4) { i ->
        val t = (phase * 2f + i / 4f) % 1f
        drawCircle(Color(0xFF9E9E9E).copy(alpha = 0.45f * (1f - t)), size.minDimension * (0.03f + 0.05f * t), Offset(vx + sin(t * 6f) * 10f, vTop - t * size.height * 0.3f))
    }
    hill(size.height * 0.84f, size.height * 0.7f, Color(0xFF7CB342))
    // Palmeira
    val trunkBase = Offset(size.width * 0.14f, size.height * 0.86f)
    val trunkTop = Offset(size.width * 0.19f, size.height * 0.42f)
    drawLine(Color(0xFF8D6E63), trunkBase, trunkTop, strokeWidth = 10f * density)
    repeat(5) { i ->
        rotate(-70f + i * 35f + sin(phase * 10f) * 3f, trunkTop) {
            drawOval(Color(0xFF43A047), trunkTop - Offset(0f, size.minDimension * 0.03f), Size(size.minDimension * 0.18f, size.minDimension * 0.06f))
        }
    }
    // Dinossauro pescoçudo
    val body = Offset(size.width * 0.46f, size.height * 0.7f)
    val bw = size.minDimension * 0.2f
    drawOval(Color(0xFF26A69A), body - Offset(bw, bw * 0.45f), Size(bw * 2, bw * 0.9f))
    val neck = Path().apply {
        moveTo(body.x + bw * 0.6f, body.y - bw * 0.2f)
        quadraticTo(body.x + bw * 1.1f, body.y - bw * 0.9f, body.x + bw * 1.0f, body.y - bw * 1.5f)
    }
    drawPath(neck, Color(0xFF26A69A), style = Stroke(width = bw * 0.28f))
    drawCircle(Color(0xFF26A69A), bw * 0.22f, Offset(body.x + bw * 1.05f, body.y - bw * 1.55f))
    drawCircle(Color(0xFF1B1446), bw * 0.04f, Offset(body.x + bw * 1.12f, body.y - bw * 1.6f))
    val tail = Path().apply {
        moveTo(body.x - bw * 0.9f, body.y)
        quadraticTo(body.x - bw * 1.5f, body.y + bw * 0.1f, body.x - bw * 1.9f, body.y - bw * 0.3f)
    }
    drawPath(tail, Color(0xFF26A69A), style = Stroke(width = bw * 0.18f))
    listOf(-0.5f, -0.1f, 0.3f, 0.6f).forEach { fx ->
        drawRoundRect(Color(0xFF00897B), Offset(body.x + bw * fx, body.y + bw * 0.25f), Size(bw * 0.18f, bw * 0.45f), CornerRadius(6f))
    }
    listOf(0.3f to 0.95f, 0.36f to 0.9f).forEach { (fx, fy) ->
        drawCircle(Color(0x33000000), size.minDimension * 0.02f, Offset(size.width * fx, size.height * fy))
    }
}

private fun DrawScope.drawForest(phase: Float) {
    skyGradient(0xFFE6F7D9, 0xFFC5E8B7, 0xFF97CF92)
    repeat(3) { i ->
        val x = size.width * (0.2f + 0.3f * i)
        val ray = Path().apply {
            moveTo(x, 0f)
            lineTo(x + size.width * 0.06f, 0f)
            lineTo(x + size.width * 0.18f, size.height)
            lineTo(x + size.width * 0.02f, size.height)
            close()
        }
        drawPath(ray, Color(0xFFFFFDE7).copy(alpha = 0.18f))
    }
    val trees = listOf(0.08f to 0.9f, 0.3f to 0.75f, 0.55f to 1.0f, 0.8f to 0.85f, 0.95f to 0.7f)
    trees.forEach { (fx, scale) ->
        val base = Offset(size.width * fx, size.height * 0.82f)
        val th = size.height * 0.34f * scale
        drawRect(Color(0xFF795548), base - Offset(7f * density * scale, th), Size(14f * density * scale, th))
        val cr = size.minDimension * 0.13f * scale
        drawCircle(Color(0xFF388E3C), cr, base - Offset(0f, th))
        drawCircle(Color(0xFF4CAF50), cr * 0.75f, base - Offset(cr * 0.55f, th - cr * 0.3f))
        drawCircle(Color(0xFF66BB6A), cr * 0.7f, base - Offset(-cr * 0.55f, th - cr * 0.25f))
    }
    drawRect(Color(0xFF7CB342), Offset(0f, size.height * 0.82f), Size(size.width, size.height * 0.18f))
    // Cogumelos
    listOf(0.42f, 0.68f).forEach { fx ->
        val base = Offset(size.width * fx, size.height * 0.9f)
        val m = size.minDimension * 0.045f
        drawRect(Color(0xFFFFF3E0), base - Offset(m * 0.3f, m), Size(m * 0.6f, m))
        drawArc(Color(0xFFE53935), 180f, 180f, true, base - Offset(m, m * 1.8f), Size(m * 2, m * 1.6f))
        drawCircle(Color.White, m * 0.18f, base - Offset(m * 0.35f, m * 1.45f))
        drawCircle(Color.White, m * 0.14f, base - Offset(-m * 0.4f, m * 1.35f))
    }
    // Vaga-lumes
    repeat(10) { i ->
        val t = phase * 2 * PI.toFloat() + i
        val x = size.width * ((i * 23 % 100) / 100f) + sin(t) * 14f * density
        val y = size.height * (0.35f + (i * 17 % 40) / 100f) + cos(t * 1.3f) * 10f * density
        val a = twinkle(phase, i / 10f)
        drawCircle(Color(0xFFFFF176).copy(alpha = a * 0.35f), 8f * density, Offset(x, y))
        drawCircle(Color(0xFFFFF59D).copy(alpha = a), 2.5f * density, Offset(x, y))
    }
}
