package com.rou39.bentohome.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rou39.bentohome.badge.BadgeListenerService
import com.rou39.bentohome.container
import com.rou39.bentohome.data.BadgeMode
import com.rou39.bentohome.data.IconShape
import com.rou39.bentohome.data.LabelSize
import com.rou39.bentohome.data.LauncherSettings
import com.rou39.bentohome.layout.GridSpec
import com.rou39.bentohome.layout.LayoutEngine
import kotlinx.coroutines.launch

/** 設定画面。通知バッジ・ホーム画面・アイコンの外観・既定ホームの切り替え導線を持つ。 */
class SettingsActivity : ComponentActivity() {
    private var notificationAccess by mutableStateOf(false)
    private val settingsRepo by lazy { container.settingsRepository }
    private val workspaceRepo by lazy { container.workspaceRepository }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by settingsRepo.settings.collectAsState(initial = null)
            val scope = rememberCoroutineScope()
            fun update(transform: (LauncherSettings) -> LauncherSettings) {
                scope.launch { settingsRepo.update(transform) }
            }
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    val s = settings ?: return@Surface
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        Section("通知バッジ")
                        Text(if (notificationAccess) "通知へのアクセス: 許可済み" else "通知へのアクセス: 未許可")
                        Text(
                            "通知へのアクセスは通知の内容を読める強い権限です。このアプリは件数の集計だけに使い、" +
                                "タイトル・本文・送信者を保存・記録しません。バッジの数は現在の通知の件数で、" +
                                "アプリ内の未読数とは一致しないことがあります。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(onClick = ::openNotificationAccess, modifier = Modifier.fillMaxWidth()) {
                            Text("通知へのアクセス設定を開く")
                        }
                        Text("表示方式")
                        Chips(BadgeMode.entries, s.badgeMode, { it.label }) { m -> update { it.copy(badgeMode = m) } }

                        HorizontalDivider()
                        Section("ホーム画面")
                        GridEditor(s) { cols, rows, dock ->
                            update { it.copy(baseColumns = cols, baseRows = rows, dockSlots = dock) }
                        }
                        SwitchRow("配置をロック（移動・削除・追加をしない）", s.layoutLocked) { v ->
                            update { it.copy(layoutLocked = v) }
                        }
                        SwitchRow("ドックを表示", s.dockVisible) { v -> update { it.copy(dockVisible = v) } }

                        HorizontalDivider()
                        Section("アイコン")
                        Text("大きさ: ${s.iconSizeDp}dp（マス目に収まらない分は自動で縮めます）")
                        Slider(
                            value = s.iconSizeDp.toFloat(),
                            onValueChange = { v -> update { it.copy(iconSizeDp = v.toInt()) } },
                            valueRange = LauncherSettings.ICON_SIZE_RANGE.first.toFloat()..LauncherSettings.ICON_SIZE_RANGE.last.toFloat(),
                            steps = (LauncherSettings.ICON_SIZE_RANGE.last - LauncherSettings.ICON_SIZE_RANGE.first) / 4 - 1,
                        )
                        Text("形")
                        Chips(IconShape.entries, s.iconShape, { it.label }) { v -> update { it.copy(iconShape = v) } }
                        Text(
                            "形の変更は adaptive icon に対応したアプリで最もきれいに効きます。古い形式のアイコンは白い背景に載せます。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        SwitchRow("ラベルを表示", s.labelVisible) { v -> update { it.copy(labelVisible = v) } }
                        Text("ラベルの文字サイズ")
                        Chips(LabelSize.entries, s.labelSize, { it.label }) { v -> update { it.copy(labelSize = v) } }

                        HorizontalDivider()
                        Section("ホームアプリ")
                        Button(
                            onClick = { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("既定のホームアプリを変更（元のホームへ戻す）")
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        notificationAccess = BadgeListenerService.isAccessGranted(this)
    }

    private fun openNotificationAccess() {
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                BadgeListenerService.component(this).flattenToString(),
            )
        runCatching { startActivity(detail) }
            .onFailure { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }

    /**
     * マス目の数とドックの枠数。変更は「適用」で確定する（plan.md §5）。
     * 適用前に、収まらずに移る項目の数を現在の配置から計算して見せる。項目は削除されず、ホームで取り消せる。
     */
    @Composable
    private fun GridEditor(s: LauncherSettings, onApply: (cols: Int, rows: Int, dock: Int) -> Unit) {
        var cols by remember { mutableIntStateOf(s.baseColumns) }
        var rows by remember { mutableIntStateOf(s.baseRows) }
        var dock by remember { mutableIntStateOf(s.dockSlots) }
        LaunchedEffect(s.baseColumns, s.baseRows, s.dockSlots) {
            cols = s.baseColumns
            rows = s.baseRows
            dock = s.dockSlots
        }
        val changed = cols != s.baseColumns || rows != s.baseRows || dock != s.dockSlots
        val preview by produceState<String?>(null, cols, rows, dock, changed) {
            if (!changed) {
                value = null
                return@produceState
            }
            val ws = workspaceRepo.load()
            val grid = GridSpec(cols, rows, LauncherSettings.SUBDIVISIONS)
            val change = LayoutEngine.changeGrid(ws, grid)
            val dockOverflow = ws.dock.count { it.slot >= dock }
            val parts = buildList {
                if (change.movedItemIds.isNotEmpty()) add("${change.movedItemIds.size}個の項目が別の位置へ移ります")
                if (change.resizedItemIds.isNotEmpty()) add("${change.resizedItemIds.size}個のウィジェットが小さくなります")
                if (dockOverflow > 0) add("ドックから外れる${dockOverflow}個のアプリはホームへ移ります")
            }
            value = if (parts.isEmpty()) "今の配置はそのまま収まります。" else parts.joinToString("。") + "。ホームで取り消せます。"
        }
        Stepper("列", cols, LauncherSettings.COLUMNS_RANGE) { cols = it }
        Stepper("行", rows, LauncherSettings.ROWS_RANGE) { rows = it }
        Stepper("ドックの枠", dock, LauncherSettings.DOCK_SLOTS_RANGE) { dock = it }
        preview?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Button(onClick = { onApply(cols, rows, dock) }, enabled = changed, modifier = Modifier.fillMaxWidth()) {
            Text("マス目とドックを適用")
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onChange(value - 1) }, enabled = value > range.first) { Text("−") }
        Text("$value", modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
        OutlinedButton(onClick = { onChange(value + 1) }, enabled = value < range.last) { Text("＋") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (option in options) {
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}
