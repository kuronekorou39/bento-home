package com.rou39.bentohome.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rou39.bentohome.layout.ItemContent
import com.rou39.bentohome.layout.WorkspaceItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** はみ出しの倍率を変える刻み。 */
private const val SCALE_STEP = 0.25f

/** 組み込みの具材。絵はコードで描く（画像素材を持たない）。 */
enum class GarnishKind(val label: String, val draw: DrawScope.() -> Unit) {
    ONIGIRI("おにぎり", { drawOnigiri() }),
    TAMAGOYAKI("卵焼き", { drawTamagoyaki() }),
    WIENER("タコさん", { drawWiener() }),
    SHRIMP("エビフライ", { drawShrimp() }),
    BROCCOLI("ブロッコリー", { drawBroccoli() }),
    TOMATO("ミニトマト", { drawTomato() }),
    BARAN("バラン", { drawBaran() }),
    ;

    companion object {
        fun of(name: String): GarnishKind? = entries.firstOrNull { it.name == name }
    }
}

fun ItemContent.Garnish.label(): String = GarnishKind.of(kind)?.label ?: "画像の具材"

/**
 * 具材の絵。占有範囲いっぱいに描き、scale は呼び出し側が graphicsLayer で掛ける（範囲の外へはみ出す）。
 * 見つからない画像は薄い枠だけ出す。
 */
@Composable
fun GarnishArt(garnish: ItemContent.Garnish, modifier: Modifier = Modifier) {
    val kind = GarnishKind.of(garnish.kind)
    if (kind != null) {
        Canvas(modifier.padding(2.dp)) { kind.draw(this) }
        return
    }
    val path = garnish.imagePath
    val image by produceState<ImageBitmap?>(null, path) {
        value = path?.let { withContext(Dispatchers.IO) { BitmapFactory.decodeFile(it)?.asImageBitmap() } }
    }
    val current = image
    if (current != null) {
        Image(current, contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier)
    } else {
        Box(modifier.padding(4.dp).border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp)))
    }
}

/**
 * ホームに置いた具材。長押しでメニュー、長押しのまま動かすと移動。タップでは何もしない。
 * 絵は占有範囲より大きく描くが、操作を受けるのは占有範囲だけ。
 */
@Composable
fun HomeGarnishCell(controller: HomeController, item: WorkspaceItem, garnish: ItemContent.Garnish) {
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(controller.resetKey) { menuOpen = false }
    val vm = controller.vm
    Box(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = "具材 ${garnish.label()}" }
            .launcherItemGestures(
                item.id,
                controller.itemCallbacks(
                    source = DragSource.Item(item.id),
                    visual = DragVisual.Garnish(garnish),
                    spanX = item.rect.width,
                    spanY = item.rect.height,
                    entry = null,
                    canDrag = controller.resizingItemId != item.id,
                    onLongPress = { menuOpen = true },
                    onDragStarted = { menuOpen = false },
                    onTap = { },
                ),
            ),
    ) {
        GarnishArt(
            garnish,
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = garnish.scale
                scaleY = garnish.scale
            },
        )
        if (controller.isHeld(DragSource.Item(item.id), menuOpen)) HoldHighlight()
        BentoMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (controller.locked) {
                BentoMenuItem("配置はロック中です", MenuSymbol.LOCK, onClick = { menuOpen = false })
                return@BentoMenu
            }
            BentoMenuNote("はみ出し ${"%.2f".format(garnish.scale).trimEnd('0').trimEnd('.')} 倍")
            BentoMenuItem(
                "はみ出しを大きく", MenuSymbol.GROW,
                onClick = { vm.setGarnishScale(item.id, garnish.scale + SCALE_STEP) },
                enabled = garnish.scale < ItemContent.Garnish.MAX_SCALE,
            )
            BentoMenuItem(
                "はみ出しを小さく", MenuSymbol.SHRINK,
                onClick = { vm.setGarnishScale(item.id, garnish.scale - SCALE_STEP) },
                enabled = garnish.scale > ItemContent.Garnish.MIN_SCALE,
            )
            BentoMenuItem("サイズ変更", MenuSymbol.RESIZE, onClick = {
                menuOpen = false
                controller.resizingItemId = item.id
            })
            SelectMenuItem(controller, item.id, close = { menuOpen = false })
            BentoMenuItem("ホームから削除", MenuSymbol.REMOVE, onClick = {
                menuOpen = false
                vm.removeItem(item.id)
            })
        }
    }
}

/** 置く具材を選ぶ。組み込みの具材か、端末の画像。 */
@Composable
fun GarnishPicker(onPick: (GarnishKind) -> Unit, onPickImage: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
        title = { Text("具材を置く") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 360.dp),
                ) {
                    items(GarnishKind.entries) { kind ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .clickable { onPick(kind) }
                                .padding(8.dp),
                        ) {
                            Canvas(Modifier.size(56.dp)) { kind.draw(this) }
                            Text(kind.label, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
                OutlinedButton(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) { Text("端末の画像を選ぶ") }
                Text(
                    "置いた後、長押しで「はみ出し」を変えると、マスより大きく描けます",
                    fontSize = 11.sp,
                    color = Color.Gray,
                )
            }
        },
    )
}

/** 具材の画像。端末の画像をアプリ内へ縮小して複製し、元の画像が消えても表示できるようにする。 */
object GarnishImages {
    private const val DIR = "garnish"

    /** 長い辺をこの大きさまで縮める（px）。 */
    private const val MAX_SIDE = 1024

    /** @return 複製した画像の場所。読めなければ null */
    fun import(context: Context, uri: Uri): String? = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(w, h))
            decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        file.absolutePath
    }.getOrNull()

    /** 配置から使われなくなった画像を消す。起動時（取り消しの猶予が無いとき）だけ呼ぶ。 */
    fun cleanup(context: Context, used: Set<String>) {
        File(context.filesDir, DIR).listFiles()?.filter { it.absolutePath !in used }?.forEach { it.delete() }
    }
}

// ---- 組み込みの具材の絵。座標は描く範囲の幅・高さに対する比率 ----

private val NORI = Color(0xFF1F2A22)
private val RICE = Color(0xFFF7F4EC)

private fun DrawScope.drawOnigiri() {
    val w = size.width
    val h = size.height
    val rice = Path().apply {
        moveTo(w * 0.5f, h * 0.1f)
        cubicTo(w * 0.62f, h * 0.1f, w * 0.95f, h * 0.68f, w * 0.9f, h * 0.82f)
        cubicTo(w * 0.86f, h * 0.94f, w * 0.14f, h * 0.94f, w * 0.1f, h * 0.82f)
        cubicTo(w * 0.05f, h * 0.68f, w * 0.38f, h * 0.1f, w * 0.5f, h * 0.1f)
        close()
    }
    drawPath(rice, RICE)
    drawRoundRect(NORI, Offset(w * 0.32f, h * 0.58f), Size(w * 0.36f, h * 0.34f), CornerRadius(w * 0.03f))
    drawCircle(Color(0xFFD94A5A), w * 0.07f, Offset(w * 0.5f, h * 0.42f))
}

private fun DrawScope.drawTamagoyaki() {
    val w = size.width
    val h = size.height
    val egg = Color(0xFFF6C945)
    val line = Color(0xFFE0A21B)
    drawRoundRect(egg, Offset(w * 0.1f, h * 0.22f), Size(w * 0.8f, h * 0.56f), CornerRadius(w * 0.12f))
    val stroke = Stroke(width = w * 0.035f)
    drawArc(line, 180f, 270f, false, Offset(w * 0.3f, h * 0.34f), Size(w * 0.4f, h * 0.32f), style = stroke)
    drawArc(line, 180f, 270f, false, Offset(w * 0.4f, h * 0.42f), Size(w * 0.2f, h * 0.16f), style = stroke)
}

private fun DrawScope.drawWiener() {
    val w = size.width
    val h = size.height
    val skin = Color(0xFFE5533D)
    // 足は先に描いて、胴で付け根を隠す
    for (i in 0 until 4) {
        val x = w * (0.2f + i * 0.17f)
        drawRoundRect(skin, Offset(x, h * 0.5f), Size(w * 0.11f, h * 0.36f), CornerRadius(w * 0.055f))
    }
    drawArc(skin, 180f, 180f, true, Offset(w * 0.16f, h * 0.18f), Size(w * 0.68f, h * 0.68f))
    drawRect(skin, Offset(w * 0.16f, h * 0.5f), Size(w * 0.68f, h * 0.08f))
    drawCircle(Color.Black, w * 0.035f, Offset(w * 0.4f, h * 0.4f))
    drawCircle(Color.Black, w * 0.035f, Offset(w * 0.6f, h * 0.4f))
}

private fun DrawScope.drawShrimp() {
    val w = size.width
    val h = size.height
    rotate(-35f) {
        drawPath(
            Path().apply {
                moveTo(w * 0.72f, h * 0.5f)
                lineTo(w * 0.92f, h * 0.36f)
                lineTo(w * 0.88f, h * 0.5f)
                lineTo(w * 0.92f, h * 0.64f)
                close()
            },
            Color(0xFFE2462F),
        )
        drawOval(Color(0xFFC98A3C), Offset(w * 0.08f, h * 0.36f), Size(w * 0.68f, h * 0.28f))
        val crumb = Color(0xFFE7B15F)
        for (i in 0 until 7) {
            drawCircle(crumb, w * 0.025f, Offset(w * (0.18f + i * 0.08f), h * (if (i % 2 == 0) 0.45f else 0.55f)))
        }
    }
}

private fun DrawScope.drawBroccoli() {
    val w = size.width
    val h = size.height
    drawRoundRect(Color(0xFF9CC46B), Offset(w * 0.42f, h * 0.5f), Size(w * 0.16f, h * 0.38f), CornerRadius(w * 0.05f))
    val green = Color(0xFF3E8E3A)
    val dark = Color(0xFF2F7230)
    drawCircle(dark, w * 0.2f, Offset(w * 0.32f, h * 0.42f))
    drawCircle(dark, w * 0.2f, Offset(w * 0.68f, h * 0.42f))
    drawCircle(green, w * 0.22f, Offset(w * 0.5f, h * 0.3f))
    drawCircle(green, w * 0.15f, Offset(w * 0.3f, h * 0.36f))
    drawCircle(green, w * 0.15f, Offset(w * 0.7f, h * 0.36f))
}

private fun DrawScope.drawTomato() {
    val w = size.width
    val h = size.height
    drawCircle(Color(0xFFE53935), w * 0.36f, Offset(w * 0.5f, h * 0.55f))
    drawCircle(Color.White.copy(alpha = 0.35f), w * 0.07f, Offset(w * 0.38f, h * 0.45f))
    val leaf = Color(0xFF2E7D32)
    val c = Offset(w * 0.5f, h * 0.22f)
    for (i in 0 until 5) {
        rotate(i * 72f, c) {
            drawOval(leaf, Offset(c.x - w * 0.04f, c.y - h * 0.02f), Size(w * 0.08f, h * 0.16f))
        }
    }
    drawRect(leaf, Offset(w * 0.48f, h * 0.06f), Size(w * 0.04f, h * 0.14f))
}

private fun DrawScope.drawBaran() {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.05f, h * 0.92f)
        val peaks = 5
        for (i in 0 until peaks) {
            val left = w * (0.05f + i * 0.18f)
            lineTo(left + w * 0.09f, h * (if (i % 2 == 0) 0.1f else 0.22f))
            lineTo(left + w * 0.18f, h * 0.55f)
        }
        lineTo(w * 0.95f, h * 0.92f)
        close()
    }
    drawPath(path, Color(0xFF2FA84F))
    drawPath(path, Color(0xFF1E7A38), style = Stroke(width = w * 0.02f))
}
