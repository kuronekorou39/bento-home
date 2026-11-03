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
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

private class CoordsHolder {
    var coords: LayoutCoordinates? = null

    fun windowRect(): android.graphics.Rect? = coords?.boundsInWindow()?.let {
        android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt())
    }
}

/**
 * アイコンの操作。タップで起動。長押しが成立したら振動して「つまめる状態」にし、
 * そのまま動かせばドラッグ、動かさずに離したらメニューを出す（メニューとドラッグを同時に出さない）。
 * 長押しが成立したら指を離すまでのイベントをすべて消費し、離したときにタップとして起動しない。
 * 座標は毎回ルート座標に変換するので、ドラッグ中にページが動いてもずれない。
 */
fun Modifier.launcherItemGestures(key: Any, callbacks: ItemGestureCallbacks): Modifier = composed {
    val holder = remember { CoordsHolder() }
    val haptic = LocalHapticFeedback.current
    val cb by rememberUpdatedState(callbacks)
    this
        .onGloballyPositioned { holder.coords = it }
        // 自前のジェスチャー処理は読み上げから操作できないので、開く・メニューの操作を明示する
        .semantics(mergeDescendants = true) {
            onClick(label = "開く") {
                cb.onTap(holder.windowRect())
                true
            }
            onLongClick(label = "メニューを開く") {
                cb.onLongPress()
                true
            }
        }
        .pointerInput(key) {
            var dragging = false
            var holding = false
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
                    holding = true
                    cb.onHoldStart()
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
                    holding = false
                    cb.onHoldEnd()
                    if (dragging) cb.onDragEnd() else cb.onLongPress()
                    dragging = false
                }
            } finally {
                // 項目が画面から外れて処理が打ち切られても、ドラッグ状態・ハイライトを残さない
                if (holding) cb.onHoldEnd()
                if (dragging) cb.onDragCancel()
            }
        }
}

/**
 * 指の位置を、前のイベントからの移動量で進めて速さの記録に入れる。まとめて届いた途中の位置（historical）も入れる。
 * 位置はどれも同じイベントの座標系なので、差を取ればページが動いたぶんは消える。
 */
private fun trackFinger(tracker: VelocityTracker, change: PointerInputChange, finger: Offset): Offset {
    val base = finger - change.previousPosition
    change.historical.forEach { tracker.addPosition(it.uptimeMillis, base + it.position) }
    val next = base + change.position
    // 指を離したイベントは入れない（標準のドラッグと同じ）。離すまでに間が空くと、指が止まったとみなされて速さが 0 になる
    if (change.pressed) tracker.addPosition(change.uptimeMillis, next)
    return next
}

/** ウィジェット上の横ドラッグを、ページ送りとして受け取る先。 */
class PagerDragHandler(
    /** 前のスワイプで寄せている途中なら止める（止めないと指の動きと引っ張り合う）。 */
    val onDragStart: () -> Unit,
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
        // ウィジェットの中身は提供元の読み上げに任せ、ホーム側はメニューの操作だけ足す
        .semantics {
            onLongClick(label = "ウィジェットのメニューを開く") {
                cb.onLongPress()
                true
            }
        }
        .pointerInput(key) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var horizontal = false
                // 指の位置は「前のイベントからの移動量」を積み上げて持つ。移動量はページが動いても変わらないが、
                // 項目の座標から換算すると、ページと一緒に動くぶんがずれて払う速さが 0 になる。
                // 速さは触れた瞬間から指を離す直前までの全部の動きで測る（途中が抜けると、短く払っても送られない）
                var finger = down.position
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, finger)
                val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        finger = trackFinger(tracker, change, finger)
                        val delta = finger - down.position
                        if (delta.getDistance() > viewConfiguration.touchSlop) {
                            horizontal = abs(delta.x) > abs(delta.y)
                            // ページャーも同じイベントで横ドラッグを始めてしまうので、ここで取る。
                            // 取らないと、ページャーがすぐ打ち切られて元のページへ戻すアニメーションを始め、指と引っ張り合う
                            if (horizontal) change.consume()
                            break
                        }
                    }
                }
                val coords = holder.coords ?: return@awaitEachGesture
                when {
                    ended == null -> {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        cb.onHoldStart()
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
                        cb.onHoldEnd()
                        if (dragging) cb.onDragEnd() else cb.onLongPress()
                    }
                    horizontal -> {
                        pager.onDragStart()
                        pager.onDrag(finger.x - down.position.x)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change != null) {
                                val before = finger
                                finger = trackFinger(tracker, change, finger)
                                if (change.pressed) pager.onDrag(finger.x - before.x)
                            }
                        } while (event.changes.any { it.pressed })
                        pager.onDragEnd(tracker.calculateVelocity().x)
                    }
                    else -> Unit
                }
            }
        }
}
