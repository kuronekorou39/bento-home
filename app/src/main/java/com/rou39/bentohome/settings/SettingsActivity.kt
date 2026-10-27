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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.rou39.bentohome.backup.BackupService
import com.rou39.bentohome.backup.RestorePlan
import com.rou39.bentohome.layout.ItemContent
import com.rou39.bentohome.badge.BadgeListenerService
import com.rou39.bentohome.container
import com.rou39.bentohome.data.BadgeMode
import com.rou39.bentohome.data.IconShape
import com.rou39.bentohome.data.LabelSize
import com.rou39.bentohome.data.GrayStages
import com.rou39.bentohome.data.LauncherSettings
import com.rou39.bentohome.layout.GridSpec
import com.rou39.bentohome.layout.LayoutEngine
import com.rou39.bentohome.monitor.MonitorActivity
import kotlinx.coroutines.launch

/** 設定画面。通知バッジ・ホーム画面・アイコンの外観・既定ホームの切り替え導線を持つ。 */
class SettingsActivity : ComponentActivity() {
    private var notificationAccess by mutableStateOf(false)
    private val settingsRepo by lazy { container.settingsRepository }
    private val workspaceRepo by lazy { container.workspaceRepository }
    private val usageRepo by lazy { container.usageRepository }
    private val backup by lazy { BackupService(this, container) }

    private var includeUsage by mutableStateOf(false)
    private var restorePlan by mutableStateOf<RestorePlan?>(null)

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            runCatching { backup.export(uri, includeUsage) }
                .onSuccess { toast("書き出しました") }
                .onFailure { toast("書き出せませんでした: ${it.message}") }
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            runCatching { backup.read(uri) }
                .onSuccess { restorePlan = it }
                .onFailure { toast("読み込めませんでした: ${it.message}") }
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun describe(plan: RestorePlan): String = buildString {
        append("ページ ${plan.workspace.pages.size}、")
        append("ホームとドックのアプリ ${plan.workspace.appCount}、")
        append("ウィジェット ${plan.widgetsToReAdd}（追加し直しが必要）を復元します。")
        if (plan.skippedApps > 0) append("この端末に無いアプリ ${plan.skippedApps} 個は復元しません。")
        if (plan.usage != null) append("使用履歴も置き換えます。")
        append("今の配置・設定・外観の個別設定は置き換わります。")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by settingsRepo.settings.collectAsState(initial = null)
            val scope = rememberCoroutineScope()
            var confirmClear by remember { mutableStateOf(false) }
            fun update(transform: (LauncherSettings) -> LauncherSettings) {
                scope.launch { settingsRepo.update(transform) }
            }
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                // 上下の領域は最初の描画で読む。設定の読み込みを待ってから読むと、
                // タイトルバーのある画面では領域の大きさが届かず 0 になる
                Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    val s = settings ?: return@Surface
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
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
                        SwitchRow("背景を下にスワイプして通知を開く", s.swipeDownNotifications) { v ->
                            update { it.copy(swipeDownNotifications = v) }
                        }

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
                        Section("使用頻度とグレー表示")
                        Text(
                            "このランチャーから起動した回数と最終日時だけを端末内に記録します。" +
                                "通知・履歴画面・他のアプリのリンクから開いた分は含みません。日別の件数は60日分だけ残します。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        SwitchRow("ホームで、使っていないアプリを薄く表示", s.grayOnHome) { v ->
                            update { it.copy(grayOnHome = v) }
                        }
                        SwitchRow("アプリ一覧で、使っていないアプリを薄く表示", s.grayInDrawer) { v ->
                            update { it.copy(grayInDrawer = v) }
                        }
                        Text("薄くする段階（使っていない日数）")
                        Chips(GrayStages.entries, s.grayStages, { it.label }) { v ->
                            update { it.copy(grayStages = v) }
                        }
                        Text(
                            "最初の日数で少し薄くし、次の日数でさらに薄く、最後の日数で灰色にします。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "起動できなくなることはありません。通知バッジのあるアプリ、通常の色を維持にしたアプリ、" +
                                "観測を始めてから日が浅いアプリは薄くしません。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("使用履歴を削除")
                        }

                        HorizontalDivider()
                        Section("バックアップ")
                        Text(
                            "配置・設定・外観の個別設定をファイルに書き出し、読み込んで戻せます。保存先は自分で選びます。" +
                                "ウィジェットは端末ごとに登録し直す必要があるため、読み込むと「追加し直す」表示になります。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        SwitchRow("使用履歴も含める", includeUsage) { includeUsage = it }
                        Button(
                            onClick = { exportLauncher.launch("bento-home-${java.time.LocalDate.now()}.json") },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("書き出す") }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("読み込む") }

                        HorizontalDivider()
                        Section("システムモニター")
                        Text(
                            "端末の CPU・メモリ・通信・温度のグラフ、アプリごとの負荷、ウィジェットの負荷、" +
                                "Bento 自身の権限・保存データ・負荷を確認できます。ホームの背景の長押しからも開けます。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = { startActivity(Intent(this@SettingsActivity, MonitorActivity::class.java)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("システムモニターを開く") }
                        OutlinedButton(
                            onClick = {
                                startActivity(
                                    Intent(this@SettingsActivity, MonitorActivity::class.java)
                                        .putExtra(MonitorActivity.EXTRA_TAB, MonitorActivity.TAB_BENTO),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Bento の権限・データ・負荷を見る") }

                        HorizontalDivider()
                        Section("ホームアプリ")
                        Button(
                            onClick = { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("既定のホームアプリを変更（元のホームへ戻す）")
                        }
                    }
                    restorePlan?.let { plan ->
                        AlertDialog(
                            onDismissRequest = { restorePlan = null },
                            title = { Text("バックアップから復元しますか") },
                            text = { Text(describe(plan)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    restorePlan = null
                                    scope.launch {
                                        runCatching { backup.apply(plan) }
                                            .onSuccess { toast("復元しました。ホームで取り消せます（配置のみ）") }
                                            .onFailure { toast("復元できませんでした: ${it.message}") }
                                    }
                                }) { Text("置き換える") }
                            },
                            dismissButton = { TextButton(onClick = { restorePlan = null }) { Text("やめる") } },
                        )
                    }
                    if (confirmClear) {
                        AlertDialog(
                            onDismissRequest = { confirmClear = false },
                            title = { Text("使用履歴を削除しますか") },
                            text = {
                                Text("起動回数と最終起動日時を消し、今日から記録をやり直します。並び順とグレー表示はしばらく判定しません。")
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    confirmClear = false
                                    scope.launch { usageRepo.clearHistory() }
                                }) { Text("削除") }
                            },
                            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("やめる") } },
                        )
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
