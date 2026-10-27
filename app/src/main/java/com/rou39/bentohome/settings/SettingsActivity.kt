package com.rou39.bentohome.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.rou39.bentohome.backup.BackupService
import com.rou39.bentohome.backup.RestorePlan
import com.rou39.bentohome.badge.BadgeListenerService
import com.rou39.bentohome.container
import com.rou39.bentohome.data.BadgeMode
import com.rou39.bentohome.data.GrayStages
import com.rou39.bentohome.data.IconDisguise
import com.rou39.bentohome.data.IconShape
import com.rou39.bentohome.data.LabelSize
import com.rou39.bentohome.data.LauncherSettings
import com.rou39.bentohome.layout.GridSpec
import com.rou39.bentohome.layout.LayoutEngine
import com.rou39.bentohome.monitor.MonitorActivity
import kotlinx.coroutines.launch

/** 設定の分野。最初の画面に一覧で並べ、押すとその分野のページを開く。 */
private enum class SettingsPage(val title: String, val symbol: String) {
    HOME("ホーム画面", "▦"),
    ICONS("アイコン", "◐"),
    BADGES("通知バッジ", "●"),
    USAGE("使用頻度とグレー表示", "◷"),
    BACKUP("バックアップ", "⇩"),
    MONITOR("システムモニター", "∿"),
}

/**
 * 設定画面。最初は分野の一覧（今の値の要約つき）で、押すとその分野のページを開く。
 * 戻る操作で一覧へ戻る。既定のホームの切り替えは一覧から OS の設定を直接開く。
 */
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
            var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
            var confirmClear by remember { mutableStateOf(false) }
            fun update(transform: (LauncherSettings) -> LauncherSettings) {
                scope.launch { settingsRepo.update(transform) }
            }
            // タイトルバーに今のページの名前を出す
            LaunchedEffect(page) { title = page?.title ?: DEFAULT_TITLE }
            BackHandler(enabled = page != null) { page = null }
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                // 上下の領域は最初の描画で読む。設定の読み込みを待ってから読むと、
                // タイトルバーのある画面では領域の大きさが届かず 0 になる
                Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    val s = settings ?: return@Surface
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        when (page) {
                            null -> TopList(s, onOpen = { page = it })
                            SettingsPage.HOME -> HomePage(s, ::update)
                            SettingsPage.ICONS -> IconsPage(s, ::update)
                            SettingsPage.BADGES -> BadgesPage(s, ::update)
                            SettingsPage.USAGE -> UsagePage(s, ::update, onClear = { confirmClear = true })
                            SettingsPage.BACKUP -> BackupPage()
                            SettingsPage.MONITOR -> MonitorPage()
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

    // ---- 一覧 ----

    @Composable
    private fun TopList(s: LauncherSettings, onOpen: (SettingsPage) -> Unit) {
        Card {
            NavRow(SettingsPage.HOME, homeSummary(s)) { onOpen(SettingsPage.HOME) }
            NavDivider()
            NavRow(SettingsPage.ICONS, iconsSummary(s)) { onOpen(SettingsPage.ICONS) }
            NavDivider()
            NavRow(SettingsPage.BADGES, "${if (notificationAccess) "許可済み" else "未許可"}・${s.badgeMode.label}") {
                onOpen(SettingsPage.BADGES)
            }
            NavDivider()
            NavRow(SettingsPage.USAGE, usageSummary(s)) { onOpen(SettingsPage.USAGE) }
        }
        Card {
            NavRow(SettingsPage.BACKUP, "配置・設定を書き出す／読み込む") { onOpen(SettingsPage.BACKUP) }
            NavDivider()
            NavRow(SettingsPage.MONITOR, "CPU・メモリ・アプリの負荷・棚卸し") { onOpen(SettingsPage.MONITOR) }
        }
        Card {
            NavRow("⌂", "ホームアプリ", "既定のホームを変える（元のホームへ戻す）") {
                startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            }
        }
    }

    private fun homeSummary(s: LauncherSettings) = buildList {
        add("マス目 ${s.baseColumns}×${s.baseRows}")
        add(if (s.dockVisible) "ドック ${s.dockSlots}枠" else "ドックなし")
        if (s.layoutLocked) add("ロック中")
    }.joinToString("・")

    private fun iconsSummary(s: LauncherSettings) = buildList {
        add("${s.iconSizeDp}dp")
        add(s.iconShape.label)
        if (!s.labelVisible) add("ラベルなし")
        if (s.iconDisguise != IconDisguise.NONE) add("隠す: ${s.iconDisguise.label}")
    }.joinToString("・")

    private fun usageSummary(s: LauncherSettings) =
        if (s.grayOnHome || s.grayInDrawer) "${s.grayStages.label}で薄く表示" else "薄く表示しない"

    // ---- 各ページ ----

    @Composable
    private fun HomePage(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
        Card("マス目とドック") {
            GridEditor(s) { cols, rows, dock ->
                update { it.copy(baseColumns = cols, baseRows = rows, dockSlots = dock) }
            }
        }
        Card("操作") {
            SwitchRow("配置をロック", "移動・削除・追加をしない", s.layoutLocked) { v -> update { it.copy(layoutLocked = v) } }
            SwitchRow("ドックを表示", null, s.dockVisible) { v -> update { it.copy(dockVisible = v) } }
            SwitchRow("下にスワイプで通知を開く", "ホームの背景で", s.swipeDownNotifications) { v ->
                update { it.copy(swipeDownNotifications = v) }
            }
            SwitchRow("ページをループ", "最後のページの次を最初のページにする", s.loopPages) { v ->
                update { it.copy(loopPages = v) }
            }
        }
    }

    @Composable
    private fun IconsPage(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
        Card("大きさと形") {
            Text("大きさ  ${s.iconSizeDp}dp")
            Slider(
                value = s.iconSizeDp.toFloat(),
                onValueChange = { v -> update { it.copy(iconSizeDp = v.toInt()) } },
                valueRange = LauncherSettings.ICON_SIZE_RANGE.first.toFloat()..LauncherSettings.ICON_SIZE_RANGE.last.toFloat(),
                steps = (LauncherSettings.ICON_SIZE_RANGE.last - LauncherSettings.ICON_SIZE_RANGE.first) / 4 - 1,
            )
            Note("マス目に収まらない分は自動で縮めます。")
            Chips(IconShape.entries, s.iconShape, { it.label }) { v -> update { it.copy(iconShape = v) } }
            Note("形は adaptive icon のアプリで最もきれいに効きます。古い形式のアイコンは白い背景に載せます。")
        }
        Card("ラベル") {
            SwitchRow("ラベルを表示", "アイコンの下のアプリ名", s.labelVisible) { v -> update { it.copy(labelVisible = v) } }
            if (s.labelVisible) {
                Chips(LabelSize.entries, s.labelSize, { it.label }) { v -> update { it.copy(labelSize = v) } }
            }
        }
        Card("アイコンを隠す（遊び）") {
            Chips(IconDisguise.entries, s.iconDisguise, { it.label }) { v -> update { it.copy(iconDisguise = v) } }
            if (s.iconDisguise != IconDisguise.NONE) {
                SwitchRow("つまんでいる間は見せる", "長押ししている間だけ元のアイコンにする", s.revealOnHold) { v ->
                    update { it.copy(revealOnHold = v) }
                }
            }
            Note(
                "ホーム・ドック・フォルダーのアイコンを、何のアプリか分からない見た目にします。アプリ一覧はそのままです。" +
                    "名前も隠すなら「ラベルを表示」をオフに。アプリごとに対象外にするには、" +
                    "アイコンを長押し →「外観の個別設定」→「アイコンを隠さない」。",
            )
        }
    }

    @Composable
    private fun BadgesPage(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit) {
        Card("通知へのアクセス") {
            Text(if (notificationAccess) "許可済み" else "未許可", style = MaterialTheme.typography.titleSmall)
            Note(
                "通知の内容を読める強い権限です。このアプリは件数の集計だけに使い、タイトル・本文・送信者を保存・記録しません。" +
                    "バッジの数は今出ている通知の件数で、アプリ内の未読数とは一致しないことがあります。",
            )
            OutlinedButton(onClick = ::openNotificationAccess, modifier = Modifier.fillMaxWidth()) {
                Text("通知へのアクセス設定を開く")
            }
        }
        Card("表示方式") {
            Chips(BadgeMode.entries, s.badgeMode, { it.label }) { m -> update { it.copy(badgeMode = m) } }
            Note("アプリごとの表示方式は、アイコンの長押し →「外観の個別設定」で変えられます。")
        }
    }

    @Composable
    private fun UsagePage(
        s: LauncherSettings,
        update: ((LauncherSettings) -> LauncherSettings) -> Unit,
        onClear: () -> Unit,
    ) {
        Card("薄く表示する場所") {
            SwitchRow("ホーム", null, s.grayOnHome) { v -> update { it.copy(grayOnHome = v) } }
            SwitchRow("アプリ一覧", null, s.grayInDrawer) { v -> update { it.copy(grayInDrawer = v) } }
        }
        Card("薄くする段階（使っていない日数）") {
            Chips(GrayStages.entries, s.grayStages, { it.label }) { v -> update { it.copy(grayStages = v) } }
            Note("最初の日数で少し薄く、次の日数でさらに薄く、最後の日数で灰色にします。")
            Note(
                "起動できなくなることはありません。通知バッジのあるアプリ、「通常の色を維持」にしたアプリ、" +
                    "観測を始めてから日が浅いアプリは薄くしません。",
            )
        }
        Card("記録") {
            Note(
                "このランチャーから起動した回数と最終日時だけを端末内に記録します。通知・履歴画面・" +
                    "他のアプリのリンクから開いた分は含みません。日別の件数は60日分だけ残します。",
            )
            OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Text("使用履歴を削除") }
        }
    }

    @Composable
    private fun BackupPage() {
        Card("書き出す") {
            Note("配置・設定・外観の個別設定をファイルに書き出します。保存先は自分で選びます。画像の具材は含みません。")
            SwitchRow("使用履歴も含める", null, includeUsage) { includeUsage = it }
            Button(
                onClick = { exportLauncher.launch("bento-home-${java.time.LocalDate.now()}.json") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("書き出す") }
        }
        Card("読み込む") {
            Note("ウィジェットは端末ごとに登録し直す必要があるため、読み込むと「追加し直す」表示になります。")
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("読み込む") }
        }
    }

    @Composable
    private fun MonitorPage() {
        Note("ホームの背景の長押しからも開けます。")
        Card {
            NavRow("∿", "システムモニター", "CPU・メモリ・通信・温度・アプリごとの負荷") { openMonitor(null) }
            NavDivider()
            NavRow("▤", "アプリの棚卸し", "入っているアプリを使い方で見直す") { openMonitor(MonitorActivity.TAB_INVENTORY) }
            NavDivider()
            NavRow("⊙", "Bento について", "Bento 自身の権限・保存データ・負荷") { openMonitor(MonitorActivity.TAB_BENTO) }
        }
    }

    private fun openMonitor(tab: Int?) {
        val intent = Intent(this, MonitorActivity::class.java)
        if (tab != null) intent.putExtra(MonitorActivity.EXTRA_TAB, tab)
        startActivity(intent)
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
        preview?.let { Note(it) }
        Button(onClick = { onApply(cols, rows, dock) }, enabled = changed, modifier = Modifier.fillMaxWidth()) {
            Text("マス目とドックを適用")
        }
    }

    private companion object {
        const val DEFAULT_TITLE = "Bento Home 設定"
    }
}

// ---- 部品 ----

/** 関連する設定をまとめる面。title があれば上に小さな見出しを付ける。 */
@Composable
private fun Card(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(16.dp),
            content = content,
        )
    }
}

/** 一覧の1行。記号・名前・今の値の要約・右向きの印。 */
@Composable
private fun NavRow(page: SettingsPage, summary: String, onClick: () -> Unit) = NavRow(page.symbol, page.title, summary, onClick)

@Composable
private fun NavRow(symbol: String, title: String, summary: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clearAndSetSemantics { },
        ) { Text(symbol, color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 16.sp) }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics { })
    }
}

@Composable
private fun NavDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(start = 50.dp))
}

/** 補足の説明。小さく、色を抑える。 */
@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SwitchRow(label: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    // 文字を押しても切り替わるよう、行全体を押せるようにする（スイッチ自体は押しても行に任せる）
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 2.dp),
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label)
            if (description != null) Note(description)
        }
        Switch(checked = checked, onCheckedChange = null)
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
