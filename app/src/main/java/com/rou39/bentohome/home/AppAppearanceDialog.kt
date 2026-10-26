package com.rou39.bentohome.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rou39.bentohome.catalog.AppEntry
import com.rou39.bentohome.data.AppAppearance
import com.rou39.bentohome.data.BadgeMode

/** アプリごとの外観（plan.md §2）。変更はすぐ保存する。 */
@Composable
fun AppAppearanceDialog(
    entry: AppEntry,
    appearance: AppAppearance,
    globalBadgeMode: BadgeMode,
    onChange: (AppAppearance) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
        title = { Text(entry.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SwitchLine("通常の色を維持", "使わなくなってもグレー表示にしない", appearance.keepColor) {
                    onChange(appearance.copy(keepColor = it))
                }
                SwitchLine("頻度集計から除外", "使用頻度順の並びに使わない", appearance.excludeFromRanking) {
                    onChange(appearance.copy(excludeFromRanking = it))
                }
                Text("通知バッジ", style = MaterialTheme.typography.titleSmall)
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = appearance.badgeMode == null,
                        onClick = { onChange(appearance.copy(badgeMode = null)) },
                        label = { Text("全体の設定（${globalBadgeMode.label}）") },
                    )
                    for (mode in BadgeMode.entries) {
                        FilterChip(
                            selected = appearance.badgeMode == mode,
                            onClick = { onChange(appearance.copy(badgeMode = mode)) },
                            label = { Text(mode.label) },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun SwitchLine(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
