package com.rou39.bentohome.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rou39.bentohome.layout.Partition

/**
 * 仕切りの線と塗り（plan.md §10）。見た目だけで、タップは受けない。
 * 白い線の下に薄い黒い線を重ね、明るい壁紙でも暗い壁紙でも見えるようにする。
 */
private val LIGHT_LINE = Color.White.copy(alpha = 0.5f)
private val DARK_LINE = Color.Black.copy(alpha = 0.2f)
private val FILL_COLOR = Color.White.copy(alpha = 0.06f)
private val LIGHT_WIDTH = 1.5.dp
private val DARK_WIDTH = 3.dp
private val FRAME_CORNER = 18.dp
private val FRAME_INSET = 4.dp

private val PREVIEW_WIDTH = 76.dp
private val PREVIEW_HEIGHT = 100.dp

/** ページに仕切りを描く。width・height はページの大きさ。 */
@Composable
fun PartitionLayer(partition: Partition, width: Dp, height: Dp) {
    val shape = RoundedCornerShape(FRAME_CORNER)
    for (a in partition.areas) {
        Box(
            Modifier
                .offset(x = width * a.left, y = height * a.top)
                .size(width * a.width, height * a.height)
                .padding(FRAME_INSET)
                .clip(shape)
                .background(FILL_COLOR)
                .border(DARK_WIDTH, DARK_LINE, shape)
                .border(LIGHT_WIDTH, LIGHT_LINE, shape),
        )
    }
}

/** 仕切りを選ぶ。選ぶとすぐ反映し、ダイアログを閉じる。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PartitionPicker(current: Partition, onPick: (Partition) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
        title = { Text("このページの仕切り") },
        text = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for (p in Partition.entries) {
                    val isCurrent = p == current
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(PREVIEW_WIDTH)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable(role = Role.RadioButton) { onPick(p) }
                            .semantics { selected = isCurrent }
                            .padding(6.dp),
                    ) {
                        PartitionPreview(p)
                        Text(p.label, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
    )
}

/** 縦長の画面に見立てた小さな見本。 */
@Composable
private fun PartitionPreview(partition: Partition) {
    BoxWithConstraints(
        Modifier
            .size(PREVIEW_WIDTH - 12.dp, PREVIEW_HEIGHT)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF3A3A40)),
    ) {
        for (a in partition.areas) {
            Box(
                Modifier
                    .offset(x = maxWidth * a.left, y = maxHeight * a.top)
                    .size(maxWidth * a.width, maxHeight * a.height)
                    .padding(2.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(5.dp)),
            )
        }
    }
}
