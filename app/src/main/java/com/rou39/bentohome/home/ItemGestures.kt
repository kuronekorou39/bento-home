package com.rou39.bentohome.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

private class CoordsHolder {
    var coords: LayoutCoordinates? = null

    fun windowRect(): android.graphics.Rect? = coords?.boundsInWindow()?.let {
        android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt())
    }
}

/**
 * アイコンの操作。タップで起動、長押しでメニュー、長押しのまま動かすとドラッグ。
 * 長押しが成立したら指を離すまでのイベントをすべて消費し、離したときにタップとして起動しない。
 * 座標は毎回ルート座標に変換するので、ドラッグ中にページが動いてもずれない。
 */
fun Modifier.launcherItemGestures(key: Any, callbacks: ItemGestureCallbacks): Modifier = composed {
    val holder = remember { CoordsHolder() }
    val haptic = LocalHapticFeedback.current
    val cb by rememberUpdatedState(callbacks)
    this
        .onGloballyPositioned { holder.coords = it }
        .pointerInput(key) {
            var dragging = false
            try {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var tapUp: PointerInputChange? = null
                    val beforeLongPress = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            // ページ送りなど他の操作に取られた、または指が動いたらタップ・長押しにしない
                            if (change.isConsumed) break
                            if (!change.pressed) {
                                tapUp = change
                                break
                            }
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                        }
                    }
                    if (beforeLongPress != null) {
                        tapUp?.let {
                            it.consume()
                            cb.onTap(holder.windowRect())
                        }
                        return@awaitEachGesture
                    }

                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    cb.onLongPress()
                    dragging = false
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        val change = event.changes.firstOrNull { it.id == down.id }
                        val coords = holder.coords
                        if (change != null && change.pressed && coords != null) {
                            val root = coords.localToRoot(change.position)
                            if (!dragging && cb.canDrag &&
                                (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                            ) {
                                dragging = true
                                cb.onDragStart(root, down.position, coords.size)
                            }
                            if (dragging) cb.onDragMove(root)
                        }
                    } while (event.changes.any { it.pressed })
                    if (dragging) cb.onDragEnd()
                    dragging = false
                }
            } finally {
                // 項目が画面から外れて処理が打ち切られても、ドラッグ状態を残さない
                if (dragging) cb.onDragCancel()
            }
        }
}

/** ウィジェット上の横ドラッグを、ページ送りとして受け取る先。 */
class PagerDragHandler(
    val onDrag: (dx: Float) -> Unit,
    val onDragEnd: (velocityX: Float) -> Unit,
)

/**
 * ウィジェットの操作（docs/design.md §6）。タップと縦スクロールはウィジェットに渡す。
 * 長押しと横ドラッグはホームが取り、以降のイベントを消費してウィジェット側の操作を取り消させる。
 * 消費するとページャーにも届かないので、横ドラッグは [PagerDragHandler] で直接ページを動かす。
 */
fun Modifier.widgetGestures(
    key: Any,
    callbacks: ItemGestureCallbacks,
    pagerDrag: PagerDragHandler,
): Modifier = composed {
    val holder = remember { CoordsHolder() }
    val haptic = LocalHapticFeedback.current
    val cb by rememberUpdatedState(callbacks)
    val pager by rememberUpdatedState(pagerDrag)
    this
        .onGloballyPositioned { holder.coords = it }
        .pointerInput(key) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var horizontal = false
                var slopRoot = Offset.Zero
                val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val delta = change.position - down.position
                        if (delta.getDistance() > viewConfiguration.touchSlop) {
                            horizontal = abs(delta.x) > abs(delta.y)
                            slopRoot = holder.coords?.localToRoot(change.position) ?: Offset.Zero
                            break
                        }
                    }
                }
                val coords = holder.coords ?: return@awaitEachGesture
                when {
                    ended == null -> {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        cb.onLongPress()
                        var dragging = false
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change != null && change.pressed) {
                                val root = coords.localToRoot(change.position)
                                if (!dragging && cb.canDrag &&
                                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                                ) {
                                    dragging = true
                                    cb.onDragStart(root, down.position, coords.size)
                                }
                                if (dragging) cb.onDragMove(root)
                            }
                        } while (event.changes.any { it.pressed })
                        if (dragging) cb.onDragEnd()
                    }
                    horizontal -> {
                        // ページが動くとウィジェット自身も動くので、移動量はルート座標で測る
                        val tracker = VelocityTracker()
                        val downRoot = coords.localToRoot(down.position)
                        pager.onDrag(slopRoot.x - downRoot.x)
                        var lastRoot = slopRoot
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change != null && change.pressed) {
                                val root = coords.localToRoot(change.position)
                                pager.onDrag(root.x - lastRoot.x)
                                tracker.addPosition(change.uptimeMillis, root)
                                lastRoot = root
                            }
                        } while (event.changes.any { it.pressed })
                        pager.onDragEnd(tracker.calculateVelocity().x)
                    }
                    else -> Unit
                }
            }
        }
}
