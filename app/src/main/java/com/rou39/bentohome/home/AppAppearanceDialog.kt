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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rou39.bentohome.catalog.AppEntry
import com.rou39.bentohome.data.AppAppearance
import com.rou39.bentohome.data.BadgeMode

/** アプリごとの外観（plan.md §2・§10）。名前以外の変更はすぐ保存する。 */
@Composable
fun AppAppearanceDialog(
    entry: AppEntry,
    appearance: AppAppearance,
    globalBadgeMode: BadgeMode,
    onChange: (AppAppearance) -> Unit,
    onDismiss: () -> Unit,
) {
    // 名前は入力中に保存せず、閉じるときに保存する（入力のたびに書くとカーソルが飛ぶため）
    var name by remember(entry.ref) { mutableStateOf(appearance.label.orEmpty()) }
    val latest by rememberUpdatedState(appearance)
    fun close() {
        val label = name.trim().takeIf { it.isNotEmpty() && it != entry.originalLabel }
        if (label != latest.label) onChange(latest.copy(label = label))
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = ::close,
        confirmButton = { TextButton(onClick = ::close) { Text("閉じる") } },
        title = { Text(entry.label) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("表示名") },
                    placeholder = { Text(entry.originalLabel) },
                    supportingText = { Text("空にすると元の名前（${entry.originalLabel}）") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { close() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (name.isNotEmpty()) {
                    TextButton(onClick = { name = "" }) { Text("元の名前に戻す") }
                }
                SwitchLine(
                    "アプリ一覧に表示しない",
                    "ホーム・ドック・フォルダーに置いたものはそのまま。一覧の下の「非表示のアプリを表示」から戻せます",
                    appearance.hidden,
                ) {
                    onChange(appearance.copy(hidden = it))
                }
                SwitchLine(
                    "アイコンを隠さない",
                    "設定でホームのアイコンを隠しているときも、このアプリは元のアイコンで出す",
                    appearance.disguiseExempt,
                ) {
                    onChange(appearance.copy(disguiseExempt = it))
                }
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
