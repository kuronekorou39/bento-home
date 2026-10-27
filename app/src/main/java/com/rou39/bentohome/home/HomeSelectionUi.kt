package com.rou39.bentohome.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.rou39.bentohome.catalog.AppEntry
import com.rou39.bentohome.layout.AppRef
import com.rou39.bentohome.layout.Workspace
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** 光らせ始めるまでの待ち。一覧が閉じてページが切り替わるのを待つ。 */
private const val PULSE_DELAY_MS = 350L
private const val PULSE_IN_MS = 300
private const val PULSE_OUT_MS = 450
private const val PULSE_COUNT = 2

/** 光るときに広がる大きさ（元の大きさに対する増分）。 */
private const val PULSE_GROW = 0.06f

/** ページの縮小表示にこの時間指を乗せると、そのページへ切り替える。 */
private const val PAGE_JUMP_DELAY_MS = 300L

private val STRIP_GAP = 8.dp
private val STRIP_LABEL_HEIGHT = 16.dp

/**
 * 追加・移動した項目を2回ふわっと光らせる（メッセージの代わりに場所を知らせる）。
 * 操作は受けないので、下の項目のタップや長押しはそのまま届く。
 */
@Composable
fun PulseRing(token: Int) {
    val glow = remember { Animatable(0f) }
    LaunchedEffect(token) {
        glow.snapTo(0f)
        delay(PULSE_DELAY_MS)
        repeat(PULSE_COUNT) {
            glow.animateTo(1f, tween(PULSE_IN_MS))
            glow.animateTo(0f, tween(PULSE_OUT_MS))
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = glow.value
                val scale = 1f + PULSE_GROW * glow.value
                scaleX = scale
                scaleY = scale
            }
            .padding(2.dp)
            .border(2.dp, Color.White, RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
    )
}

/** ホームで選んでいる項目の印。枠とチェック。 */
@Composable
fun SelectedMark() {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxSize()
            .padding(2.dp)
            .border(2.dp, primary, RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(primary),
        ) { Text("✓", color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp) }
    }
}

/** 長押しメニューの「選択して移動」。ホームで選ぶ操作を始め、その項目を選んだ状態にする。 */
@Composable
fun SelectMenuItem(controller: HomeController, itemId: Long, close: () -> Unit) {
    if (controller.locked) return
    DropdownMenuItem(text = { Text("選択して移動") }, onClick = {
        close()
        controller.startItemSelection(itemId)
    })
}

/** ホームで選んでいる間の操作。ドックの位置に重ねて出す。 */
@Composable
fun HomeSelectionBar(controller: HomeController, modifier: Modifier = Modifier) {
    val count = controller.selectedItems.size
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xFF26262C))
            // 下のドックに操作を通さない
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${count}個を選択中", color = Color.White, maxLines = 1)
                Text("タップで選ぶ・長押しのまま動かすとまとめて移動", color = Color.LightGray, fontSize = 11.sp)
            }
            TextButton(onClick = controller::clearItemSelection) { Text("完了") }
        }
        Button(
            onClick = { controller.moveOverviewOpen = true },
            enabled = count > 0,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("ページを選んで移動") }
    }
}

/**
 * 運んでいる間、ドックの位置に並べるページの縮小表示（最後は新しいページ）。
 * 指を乗せて少し待つとそのページへ切り替わり（[PageJumpEffect]）、乗せたまま離すとそのページの空きへ置く。
 * @param hovered 指が乗っているページの番号
 */
@Composable
fun PageStrip(
    controller: HomeController,
    workspace: Workspace,
    byRef: Map<AppRef, AppEntry>,
    aspect: Float,
    hovered: Int?,
    modifier: Modifier = Modifier,
) {
    val pages = workspace.orderedPages
    val geometry = controller.geometry
    DisposableEffect(Unit) {
        onDispose {
            geometry.stripArea = Rect.Zero
            geometry.stripTiles.clear()
        }
    }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .background(Color(0xEE101014))
            .onGloballyPositioned { geometry.stripArea = it.boundsInRoot() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        val count = pages.size + 1
        val tileW = min((maxHeight - STRIP_LABEL_HEIGHT) * aspect, (maxWidth - STRIP_GAP * (count - 1)) / count)
        val tileH = tileW / aspect
        Row(
            horizontalArrangement = Arrangement.spacedBy(STRIP_GAP, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize(),
        ) {
            for (i in 0 until count) {
                val page = pages.getOrNull(i)
                val hot = hovered == i
                val current = controller.pagerState.currentPage == i
                val label = if (page != null) "${i + 1}" else "新規"
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .onGloballyPositioned { geometry.stripTiles[i] = it.boundsInRoot() }
                        .semantics { contentDescription = if (page != null) "${i + 1}ページ目" else "新しいページ" },
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(tileW, tileH)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = if (hot) 0.3f else 0.08f))
                            .border(
                                if (hot || current) 2.dp else 1.dp,
                                when {
                                    hot -> Color.White
                                    current -> Color.White.copy(alpha = 0.7f)
                                    else -> Color.White.copy(alpha = 0.3f)
                                },
                                RoundedCornerShape(6.dp),
                            ),
                    ) {
                        if (page != null) {
                            PageMiniature(controller, workspace, page, byRef)
                        } else {
                            Text("＋", color = Color.White, fontSize = 18.sp)
                        }
                    }
                    Text(label, color = Color.LightGray, fontSize = 10.sp, modifier = Modifier.height(STRIP_LABEL_HEIGHT))
                }
            }
        }
    }
}

/** ページの縮小表示に指を乗せて少し待つと、そのページへ一気に切り替える。 */
@Composable
fun PageJumpEffect(controller: HomeController, hovered: Int?) {
    val current by rememberUpdatedState(hovered)
    LaunchedEffect(controller) {
        snapshotFlow { current }.collectLatest { index ->
            if (index == null) return@collectLatest
            delay(PAGE_JUMP_DELAY_MS)
            val pager = controller.pagerState
            if (index in 0 until pager.pageCount && index != pager.currentPage) pager.scrollToPage(index)
        }
    }
}
