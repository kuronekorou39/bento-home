package com.rou39.bentohome.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.os.SystemClock
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rou39.bentohome.monitor.WidgetMeasurement
import java.util.Locale

/** 計測中は操作を受け付けない。指でページを動かすと結果が乱れるため。 */
@Composable
fun MeasuringOverlay(message: String, endsAt: Long) {
    // 回転する表示は毎フレーム描画させて計測を乱すので、1秒ごとに変わる残り秒数だけを出す
    var remaining by remember { mutableLongStateOf(0L) }
    LaunchedEffect(endsAt) {
        while (true) {
            remaining = ((endsAt - SystemClock.elapsedRealtime() + 999) / 1000).coerceAtLeast(0)
            delay(1000)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp)
                .background(Color(0xE6202020), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(message, color = Color.White)
            Text("残り約${remaining}秒。画面に触れずにお待ちください", color = Color(0xFFBDBDBD))
        }
    }
}

@Composable
fun MeasureResultDialog(result: MeasureResult, canMove: Boolean, onMove: () -> Unit, onDismiss: () -> Unit) {
    val m = result.measurement
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("「${result.label}」の負荷") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(describe(m))
                if (m.animations.isNotEmpty()) Text("動き続ける部品: ${m.animations.joinToString("、")}")
                Text(
                    if (m.heavy) {
                        "表示している間、負荷がかかり続けています。後ろのページへ移すと、そのページを開いているときだけ動きます。"
                    } else {
                        "負荷はほとんどありません。CPU の数%の差は、他のウィジェットや端末の状態による誤差の範囲です。"
                    },
                )
            }
        },
        confirmButton = {
            if (m.heavy && canMove) {
                TextButton(onClick = onMove) { Text("後ろのページへ移す") }
            } else {
                TextButton(onClick = onDismiss) { Text("閉じる") }
            }
        },
        dismissButton = if (m.heavy && canMove) {
            { TextButton(onClick = onDismiss) { Text("このままにする") } }
        } else {
            null
        },
    )
}

/** 計測結果の1行説明。モニター画面でも使う。 */
fun describe(m: WidgetMeasurement): String =
    String.format(Locale.JAPAN, "CPU 約%.1f%%（1コアあたり）、描画 毎秒%.0f回", m.cpuPercent, m.framesPerSecond)
