package com.rou39.bentohome.icon

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import com.rou39.bentohome.data.IconShape

/**
 * アイコンを指定の形で描く（plan.md §2）。adaptive icon は前景・背景の層を形で切り抜く。
 * 旧式アイコンは白い背景に余白付きで載せる。素材によって仕上がりは揃わない。
 */
object IconRenderer {
    /** adaptive icon の層は見える範囲の 1.5 倍。各辺に 1/4 ずつはみ出して描く。 */
    private const val ADAPTIVE_EXTRA_INSET = 0.25f

    /** 旧式アイコンを形の中に載せるときの余白の割合。 */
    private const val LEGACY_PADDING = 0.14f

    fun render(drawable: Drawable, sizePx: Int, shape: IconShape): Bitmap {
        if (shape == IconShape.SYSTEM) return drawable.toBitmap(sizePx, sizePx)
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        canvas.clipPath(shapePath(shape, sizePx.toFloat()))
        if (drawable is AdaptiveIconDrawable) {
            val inset = (sizePx * ADAPTIVE_EXTRA_INSET).toInt()
            val bounds = Rect(-inset, -inset, sizePx + inset, sizePx + inset)
            drawable.background?.let { it.bounds = bounds; it.draw(canvas) }
            drawable.foreground?.let { it.bounds = bounds; it.draw(canvas) }
        } else {
            canvas.drawColor(Color.WHITE)
            val pad = (sizePx * LEGACY_PADDING).toInt()
            drawable.setBounds(pad, pad, sizePx - pad, sizePx - pad)
            drawable.draw(canvas)
        }
        return bitmap
    }

    /**
     * 影絵用の形。テーマアイコン用の単色の層（monochrome）があればそれを、無ければ前景の層を使い、
     * 透明でない部分を白く塗る。旧式アイコンは全体の輪郭になる。
     */
    fun renderSilhouette(drawable: Drawable, sizePx: Int): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val layer = (drawable as? AdaptiveIconDrawable)?.let { it.monochrome ?: it.foreground }
        if (layer != null) {
            val inset = (sizePx * ADAPTIVE_EXTRA_INSET).toInt()
            layer.setBounds(-inset, -inset, sizePx + inset, sizePx + inset)
            layer.draw(canvas)
        } else {
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(canvas)
        }
        canvas.drawColor(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
        return bitmap
    }

    /** アイコンの代表色。透明な部分を除いた平均。 */
    fun averageColor(bitmap: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(bitmap, AVERAGE_SAMPLE, AVERAGE_SAMPLE, true)
        var r = 0L
        var g = 0L
        var b = 0L
        var weight = 0L
        for (x in 0 until AVERAGE_SAMPLE) for (y in 0 until AVERAGE_SAMPLE) {
            val p = small.getPixel(x, y)
            val a = Color.alpha(p).toLong()
            r += Color.red(p) * a
            g += Color.green(p) * a
            b += Color.blue(p) * a
            weight += a
        }
        if (weight == 0L) return Color.GRAY
        return Color.rgb((r / weight).toInt(), (g / weight).toInt(), (b / weight).toInt())
    }

    /** 代表色を求めるときに縮める大きさ（1辺の px）。 */
    private const val AVERAGE_SAMPLE = 16

    private fun shapePath(shape: IconShape, size: Float): Path = Path().apply {
        when (shape) {
            IconShape.CIRCLE -> addCircle(size / 2, size / 2, size / 2, Path.Direction.CW)
            IconShape.ROUNDED -> addRoundRect(0f, 0f, size, size, size * 0.22f, size * 0.22f, Path.Direction.CW)
            IconShape.SQUARE -> addRoundRect(0f, 0f, size, size, size * 0.06f, size * 0.06f, Path.Direction.CW)
            IconShape.SYSTEM -> addRect(0f, 0f, size, size, Path.Direction.CW)
        }
    }
}
