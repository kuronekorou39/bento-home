package com.rou39.bentohome.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.rou39.bentohome.catalog.AppEntry
import com.rou39.bentohome.data.IconShape
import com.rou39.bentohome.home.AppIconView
import com.rou39.bentohome.home.BadgeDisplay
import com.rou39.bentohome.home.IconStyle
import com.rou39.bentohome.home.rememberAppIcon
import com.rou39.bentohome.icon.IconCache
import com.rou39.bentohome.layout.AppRef
import com.rou39.bentohome.layout.CellRect
import com.rou39.bentohome.layout.GridSpec
import com.rou39.bentohome.layout.ItemContent
import com.rou39.bentohome.layout.Workspace

/** プレビューの背景。壁紙は読まず、暗い色の面で代わりにする（壁紙の読み取りには強い権限が要る）。 */
private val PREVIEW_BACKGROUND = Brush.verticalGradient(listOf(Color(0xFF3A3D4A), Color(0xFF23252E)))
private val SLOT_COLOR = Color.White.copy(alpha = 0.07f)
private val TILE_COLOR = Color.White.copy(alpha = 0.22f)
private val DOCK_COLOR = Color.White.copy(alpha = 0.12f)

/** 占有範囲に対する、縮小表示のアイコンの大きさ。 */
private const val MINI_ICON_RATIO = 0.62f

/**
 * ホーム画面の縮小表示。端末の画面の縦横比で、ページの配置・マス目・ページの点・ドックを描く。
 * ページは [pagerState] で横にめくれる（ドックは動かない）。
 * 与えられた場所に収まる最大の大きさにする（設定画面を縦に伸ばさないため）。
 */
@Composable
fun HomePreview(
    workspace: Workspace?,
    pagerState: PagerState,
    grid: GridSpec,
    dockSlots: Int,
    dockVisible: Boolean,
    loopPages: Boolean,
    shape: IconShape,
    byRef: Map<AppRef, AppEntry>,
    cache: IconCache,
    modifier: Modifier = Modifier,
) {
    val config = LocalConfiguration.current
    val aspect = config.screenWidthDp.toFloat() / config.screenHeightDp.coerceAtLeast(1)
    val pages = workspace?.orderedPages.orEmpty()
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        var h = maxHeight
        var w = h * aspect
        if (w > maxWidth) {
            w = maxWidth
            h = w / aspect
        }
        Box(
            Modifier
                .size(w, h)
                .clip(RoundedCornerShape(w * 0.08f))
                .background(PREVIEW_BACKGROUND),
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = w * 0.03f, vertical = h * 0.03f)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = h * 0.04f)
                        .semantics { contentDescription = "ホーム画面のプレビュー、${pagerState.currentPage + 1}ページ目" },
                ) { index ->
                    PreviewPage(workspace, pages.getOrNull(index)?.id, grid, shape, byRef, cache)
                }
                PageDots(
                    count = pages.size.coerceAtLeast(1),
                    current = pagerState.currentPage,
                    loop = loopPages,
                    modifier = Modifier.fillMaxWidth().height(h * 0.04f),
                )
                if (dockVisible) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(h * 0.09f)
                            .clip(RoundedCornerShape(h * 0.03f))
                            .background(DOCK_COLOR),
                    ) {
                        val side = min(h * 0.06f, w / dockSlots * 0.7f)
                        for (slot in 0 until dockSlots) {
                            val entry = workspace?.dockAt(slot)?.app?.let { byRef[it] }
                            Box(Modifier.size(side), contentAlignment = Alignment.Center) {
                                if (entry != null) MiniIcon(entry, side, shape, cache) else EmptySlot(side)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 縮小表示の1ページ。空いているマスも分かるよう、アイコン1個分の枠を薄く描く（項目のあるマスには描かない）。 */
@Composable
private fun PreviewPage(
    workspace: Workspace?,
    pageId: Long?,
    grid: GridSpec,
    shape: IconShape,
    byRef: Map<AppRef, AppEntry>,
    cache: IconCache,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cellW = maxWidth / grid.columns
        val cellH = maxHeight / grid.rows
        val items = if (workspace != null && pageId != null) workspace.itemsOn(pageId) else emptyList()
        Canvas(Modifier.fillMaxSize()) {
            val bw = size.width / grid.baseColumns
            val bh = size.height / grid.baseRows
            val side = minOf(bw, bh) * MINI_ICON_RATIO
            for (c in 0 until grid.baseColumns) for (r in 0 until grid.baseRows) {
                val cell = CellRect(c * grid.iconSpan, r * grid.iconSpan, grid.iconSpan, grid.iconSpan)
                if (items.any { it.rect.overlaps(cell) }) continue
                drawRoundRect(
                    SLOT_COLOR,
                    topLeft = Offset(c * bw + (bw - side) / 2, r * bh + (bh - side) / 2),
                    size = Size(side, side),
                    cornerRadius = CornerRadius(side * 0.25f),
                )
            }
        }
        for (item in items) {
            val r = item.rect
            if (r.right > grid.columns || r.bottom > grid.rows) continue
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset(x = cellW * r.x, y = cellH * r.y)
                    .size(cellW * r.width, cellH * r.height),
            ) {
                val side = min(cellW * grid.iconSpan, cellH * grid.iconSpan) * MINI_ICON_RATIO
                MiniContent(item.content, side, shape, byRef, cache)
            }
        }
    }
}

/** 配置のロックの切り替え。閉じた鍵・開いた鍵を描く。ロック中は色を付けて目立たせる。 */
@Composable
fun LockToggle(locked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (locked) colors.primary else colors.surfaceContainer)
            .toggleable(value = locked, role = Role.Switch, onValueChange = { onToggle() })
            .semantics { contentDescription = "配置のロック" },
    ) {
        LockGlyph(locked, if (locked) colors.onPrimary else colors.onSurfaceVariant, Modifier.size(20.dp))
    }
}

/** 鍵の形。開いているときは、つるを片側だけ持ち上げる。 */
@Composable
fun LockGlyph(locked: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.13f
        val bodyTop = h * 0.45f
        drawRoundRect(color, topLeft = Offset(w * 0.12f, bodyTop), size = Size(w * 0.76f, h * 0.53f), cornerRadius = CornerRadius(w * 0.12f))
        val arcLeft = w * 0.26f
        val arcWidth = w * 0.48f
        val lift = if (locked) 0f else h * 0.16f
        val shackle = Path().apply {
            moveTo(arcLeft, bodyTop)
            lineTo(arcLeft, h * 0.3f - lift)
            arcTo(Rect(arcLeft, h * 0.06f - lift, arcLeft + arcWidth, h * 0.54f - lift), 180f, 180f, false)
            lineTo(arcLeft + arcWidth, if (locked) bodyTop else h * 0.3f - lift)
        }
        drawPath(shackle, color, style = Stroke(width = stroke))
    }
}

@Composable
private fun MiniContent(content: ItemContent, side: Dp, shape: IconShape, byRef: Map<AppRef, AppEntry>, cache: IconCache) {
    when (content) {
        is ItemContent.App -> byRef[content.app]?.let { MiniIcon(it, side, shape, cache) } ?: EmptySlot(side)
        is ItemContent.Widget -> Box(
            Modifier.fillMaxSize().padding(2.dp).clip(RoundedCornerShape(side * 0.25f)).background(TILE_COLOR),
        )
        is ItemContent.Garnish -> Unit
        else -> Box(Modifier.size(side).clip(RoundedCornerShape(side * 0.3f)).background(TILE_COLOR))
    }
}

@Composable
private fun MiniIcon(entry: AppEntry, side: Dp, shape: IconShape, cache: IconCache) {
    val icon = rememberAppIcon(entry, side, shape, cache)
    if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(side)) else EmptySlot(side)
}

@Composable
private fun EmptySlot(side: Dp) {
    Box(Modifier.size(side * 0.8f).clip(CircleShape).background(SLOT_COLOR))
}

/** ページの点。ループするときは、端と端がつながる印を添える。 */
@Composable
private fun PageDots(count: Int, current: Int, loop: Boolean, modifier: Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        if (loop) Text("↻", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
        repeat(count) { i ->
            Box(
                Modifier
                    .size(if (i == current) 5.dp else 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (i == current) 1f else 0.45f)),
            )
        }
        if (loop) Text("↺", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
    }
}

/**
 * アイコンの並びのプレビュー。実際のアプリを、今の設定（大きさ・形・ラベル・隠し方）で描く。
 * badges と grayLevels は並びの順に対応させる（足りない分は無し）。
 */
@Composable
fun IconRowPreview(
    apps: List<AppEntry>,
    cache: IconCache,
    style: IconStyle,
    modifier: Modifier = Modifier,
    badges: List<BadgeDisplay?> = emptyList(),
    grayLevels: List<Int> = emptyList(),
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(PREVIEW_BACKGROUND)
            .padding(vertical = 16.dp)
            .clearAndSetSemantics { contentDescription = "アイコンのプレビュー" },
    ) {
        apps.forEachIndexed { i, entry ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                AppIconView(
                    entry, cache, style.iconSize, style.shape, badges.getOrNull(i),
                    labelSizeSp = style.labelSizeSp.takeIf { style.labelVisible },
                    grayLevel = grayLevels.getOrNull(i) ?: 0,
                    disguise = style.disguise,
                )
            }
        }
    }
}
