package com.rou39.bentohome.settings

import android.content.Intent
import android.view.MenuItem
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import com.rou39.bentohome.home.AppIconView
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
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.rou39.bentohome.backup.BackupService
import com.rou39.bentohome.backup.RestorePlan
import com.rou39.bentohome.badge.BadgeListenerService
import com.rou39.bentohome.catalog.AppEntry
import com.rou39.bentohome.container
import com.rou39.bentohome.data.BadgeMode
import com.rou39.bentohome.data.IconDisguise
import com.rou39.bentohome.data.IconShape
import com.rou39.bentohome.data.LabelSize
import com.rou39.bentohome.data.LauncherSettings
import com.rou39.bentohome.home.BadgeDisplay
import com.rou39.bentohome.home.iconStyle
import com.rou39.bentohome.layout.AppRef
import com.rou39.bentohome.layout.GridSpec
import com.rou39.bentohome.layout.LayoutEngine
import com.rou39.bentohome.layout.Workspace
import com.rou39.bentohome.monitor.MonitorActivity
import kotlinx.coroutines.launch

/** 設定の分野。最初の画面に一覧で並べ、押すとその分野のページを開く。 */
private enum class SettingsPage(val title: String, val symbol: String) {
    HOME("ホーム画面", "▦"),
    ICONS("アイコン", "◐"),
    BADGES("通知バッジ", "●"),
    BACKUP("バックアップ", "⇩"),
    MONITOR("システムモニター", "∿"),
}

/** プレビューに並べるアプリの数。 */
private const val PREVIEW_APP_COUNT = 4

/** 一覧の大きな行の、左の絵の場所と、その中のアイコンの大きさ。 */
private val LARGE_NAV_LEADING = 56.dp
private val NAV_ICON_SIZE = 44.dp
private val LARGE_NAV_INDENT = 70.dp

/** プレビューの横のページ送りボタンの高さ（プレビューの高さに対する割合）と、押せないときの濃さ。 */
private const val PAGE_BUTTON_HEIGHT_RATIO = 0.5f
private const val DISABLED_ALPHA = 0.3f

/** 通知バッジのプレビューで使う件数（並びの順。null はバッジなし）。 */
private val SAMPLE_BADGE_COUNTS = listOf(3, null, 12, 1)

/** 頻度反映のプレビューで使う灰色の段階（並びの順）。使っていないアプリほど色が抜ける様子を見せる。 */
private val SAMPLE_GRAY_LEVELS = listOf(0, 0, 1, 3)

/**
 * 設定画面。最初は分野の一覧（今の値の要約つき）で、押すとその分野のページを開く。
 * 見た目の設定は、説明文ではなく実際のアイコン・ホームの縮小表示で見せる。
 * 戻る操作で一覧へ戻る。既定のホームの切り替えは一覧から OS の設定を直接開く。
 */
class SettingsActivity : ComponentActivity() {
    private var notificationAccess by mutableStateOf(false)
    private val settingsRepo by lazy { container.settingsRepository }
    private val workspaceRepo by lazy { container.workspaceRepository }
    private val usageRepo by lazy { container.usageRepository }
    private val catalog by lazy { container.appCatalog }
    private val iconCache by lazy { container.iconCache }
    private val backup by lazy { BackupService(this, container) }

    private var includeUsage by mutableStateOf(false)

    /** 開いている分野。null なら一覧。タイトルバーの戻る矢印・戻る操作で一覧へ戻る。 */
    private var page by mutableStateOf<SettingsPage?>(null)
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
        page = savedInstanceState?.getString(KEY_PAGE)?.let { name -> SettingsPage.entries.firstOrNull { it.name == name } }
        actionBar?.setDisplayHomeAsUpEnabled(true)
        setContent {
            val settings by settingsRepo.settings.collectAsState(initial = null)
            val apps by catalog.apps.collectAsState()
            val byRef by catalog.byRef.collectAsState()
            val scope = rememberCoroutineScope()
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
                    // マス目・ドックを適用したら、ホームが並べ直した配置を読み直す
                    val workspace by produceState<Workspace?>(null, s.baseColumns, s.baseRows, s.dockSlots) {
                        value = workspaceRepo.load()
                    }
                    val sampleApps = remember(workspace, apps, byRef) { previewApps(workspace, apps, byRef) }
                    // ホーム画面のページは縮小表示を画面に収めるので、縦に流さない
                    val fixed = page == SettingsPage.HOME
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .then(if (fixed) Modifier.fillMaxSize() else Modifier.verticalScroll(rememberScrollState()))
                            .padding(16.dp),
                    ) {
                        when (page) {
                            null -> TopList(s, sampleApps, onOpen = { page = it })
                            SettingsPage.HOME -> HomePage(s, ::update, workspace, byRef, Modifier.weight(1f))
                            SettingsPage.ICONS -> IconsPage(s, ::update, sampleApps)
                            SettingsPage.BADGES -> BadgesPage(s, ::update, sampleApps)
                            SettingsPage.BACKUP -> BackupPage(onClear = { confirmClear = true })
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        page?.let { outState.putString(KEY_PAGE, it.name) }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId != android.R.id.home) return super.onOptionsItemSelected(item)
        if (page != null) page = null else finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        notificationAccess = BadgeListenerService.isAccessGranted(this)
    }

    /** プレビューに使うアプリ。ドックのアプリを先に、足りなければ名前順で埋める。 */
    private fun previewApps(workspace: Workspace?, apps: List<AppEntry>, byRef: Map<AppRef, AppEntry>): List<AppEntry> {
        val dock = workspace?.dock.orEmpty().sortedBy { it.slot }.mapNotNull { byRef[it.app] }
        return (dock + apps.sortedBy { it.label }).distinctBy { it.ref }.take(PREVIEW_APP_COUNT)
    }

    // ---- 一覧 ----

    @Composable
    private fun TopList(s: LauncherSettings, apps: List<AppEntry>, onOpen: (SettingsPage) -> Unit) {
        // 見た目の分野は、記号ではなく今の設定で描いた小さな絵を付ける
        val sample = apps.firstOrNull()
        Card {
            LargeNavRow({ HomeGlyph(s) }, SettingsPage.HOME.title, homeSummary(s)) { onOpen(SettingsPage.HOME) }
            NavDivider(LARGE_NAV_INDENT)
            LargeNavRow(
                { if (sample != null) AppIconView(sample, iconCache, NAV_ICON_SIZE, s.iconShape, null, labelSizeSp = null) },
                SettingsPage.ICONS.title,
                iconsSummary(s),
            ) { onOpen(SettingsPage.ICONS) }
            NavDivider(LARGE_NAV_INDENT)
            LargeNavRow(
                {
                    if (sample != null) {
                        val badge = when (s.badgeMode) {
                            BadgeMode.NONE -> null
                            BadgeMode.DOT -> BadgeDisplay.Dot
                            BadgeMode.NUMBER -> BadgeDisplay.Count(3)
                        }
                        AppIconView(sample, iconCache, NAV_ICON_SIZE, s.iconShape, badge, labelSizeSp = null)
                    }
                },
                SettingsPage.BADGES.title,
                "${if (notificationAccess) "許可済み" else "未許可"}・${s.badgeMode.label}",
            ) { onOpen(SettingsPage.BADGES) }
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
        if (s.usageReflected) add("頻度反映")
    }.joinToString("・")

    // ---- 各ページ ----

    /**
     * ホーム画面。上に縮小表示、下に操作をまとめる。縮小表示が残りの高さに合わせて縮むので、縦に流れない。
     * マス目とドックの枠数は「適用」で確定する（plan.md §5）。適用前の縮小表示は、適用後の並びを計算して描く。
     */
    @Composable
    private fun HomePage(
        s: LauncherSettings,
        update: ((LauncherSettings) -> LauncherSettings) -> Unit,
        workspace: Workspace?,
        byRef: Map<AppRef, AppEntry>,
        modifier: Modifier,
    ) {
        var cols by remember { mutableIntStateOf(s.baseColumns) }
        var rows by remember { mutableIntStateOf(s.baseRows) }
        var dock by remember { mutableIntStateOf(s.dockSlots) }
        LaunchedEffect(s.baseColumns, s.baseRows, s.dockSlots) {
            cols = s.baseColumns
            rows = s.baseRows
            dock = s.dockSlots
        }
        val changed = cols != s.baseColumns || rows != s.baseRows || dock != s.dockSlots
        val grid = GridSpec(cols, rows, LauncherSettings.SUBDIVISIONS)
        val gridChange = remember(workspace, cols, rows) {
            workspace?.takeIf { cols != s.baseColumns || rows != s.baseRows }?.let { LayoutEngine.changeGrid(it, grid) }
        }
        val moved = (gridChange?.movedItemIds?.size ?: 0) + (workspace?.dock?.count { it.slot >= dock } ?: 0)

        val previewWs = gridChange?.workspace ?: workspace
        val pageCount by rememberUpdatedState(previewWs?.pages?.size?.coerceAtLeast(1) ?: 1)
        val pagerState = rememberPagerState { pageCount }
        val scope = rememberCoroutineScope()
        val current = pagerState.currentPage
        // 左右のボタンでもページを送る。ループするなら端から反対の端へ
        fun go(delta: Int) {
            scope.launch {
                val n = pagerState.pageCount
                val target = if (s.loopPages) (current + delta + n) % n else current + delta
                if (target in 0 until n) pagerState.animateScrollToPage(target)
            }
        }

        Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 鍵はプレビューの外、右上の角に置く（左右のボタンは縦の中ほどなので重ならない）
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    PageButton("‹", "前のページ", enabled = s.loopPages || current > 0) { go(-1) }
                    HomePreview(
                        workspace = previewWs,
                        pagerState = pagerState,
                        grid = grid,
                        dockSlots = dock,
                        dockVisible = s.dockVisible,
                        loopPages = s.loopPages,
                        shape = s.iconShape,
                        byRef = byRef,
                        cache = iconCache,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    PageButton("›", "次のページ", enabled = s.loopPages || current < pageCount - 1) { go(1) }
                }
                LockToggle(
                    locked = s.layoutLocked,
                    onToggle = { update { it.copy(layoutLocked = !it.layoutLocked) } },
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
            Card {
                // 左の数と右の切り替えの間に仕切りを入れ、＋ボタンとチップを離す
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(IntrinsicSize.Min),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StepperRow("列", cols, LauncherSettings.COLUMNS_RANGE) { cols = it }
                        StepperRow("行", rows, LauncherSettings.ROWS_RANGE) { rows = it }
                        StepperRow("ドック", dock, LauncherSettings.DOCK_SLOTS_RANGE, enabled = s.dockVisible) { dock = it }
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ToggleChips(
                        listOf(
                            Toggle("ドック", s.dockVisible) { v -> update { it.copy(dockVisible = v) } },
                            Toggle("ページをループ", s.loopPages) { v -> update { it.copy(loopPages = v) } },
                            Toggle("下スワイプで通知", s.swipeDownNotifications) { v -> update { it.copy(swipeDownNotifications = v) } },
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
                // 値を変えたときだけ出す行。場所は常に取っておき、出たときに全体がずれないようにする
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(if (changed) 1f else 0f)) {
                    Text(
                        when {
                            !changed -> ""
                            moved > 0 -> "${moved}個が移ります"
                            else -> "そのまま収まります"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(enabled = changed, onClick = {
                        cols = s.baseColumns
                        rows = s.baseRows
                        dock = s.dockSlots
                    }) { Text("戻す") }
                    Button(enabled = changed, onClick = { update { it.copy(baseColumns = cols, baseRows = rows, dockSlots = dock) } }) {
                        Text("適用")
                    }
                }
            }
        }
    }

    @Composable
    private fun IconsPage(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit, apps: List<AppEntry>) {
        IconRowPreview(
            apps, iconCache, s.iconStyle(),
            grayLevels = if (s.usageReflected) SAMPLE_GRAY_LEVELS else emptyList(),
        )
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("大きさ", modifier = Modifier.weight(1f))
                Text("${s.iconSizeDp}dp", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Slider(
                value = s.iconSizeDp.toFloat(),
                onValueChange = { v -> update { it.copy(iconSizeDp = v.toInt()) } },
                valueRange = LauncherSettings.ICON_SIZE_RANGE.first.toFloat()..LauncherSettings.ICON_SIZE_RANGE.last.toFloat(),
                steps = (LauncherSettings.ICON_SIZE_RANGE.last - LauncherSettings.ICON_SIZE_RANGE.first) / 4 - 1,
            )
            Chips(IconShape.entries, s.iconShape, { it.label }) { v -> update { it.copy(iconShape = v) } }
        }
        Card {
            SwitchRow("ラベル", s.labelVisible) { v -> update { it.copy(labelVisible = v) } }
            if (s.labelVisible) {
                Chips(LabelSize.entries, s.labelSize, { it.label }) { v -> update { it.copy(labelSize = v) } }
            }
        }
        Card {
            Text("アイコンを隠す")
            Chips(IconDisguise.entries, s.iconDisguise, { it.label }) { v -> update { it.copy(iconDisguise = v) } }
            if (s.iconDisguise != IconDisguise.NONE) {
                SwitchRow("長押しの間は元のアイコン", s.revealOnHold) { v -> update { it.copy(revealOnHold = v) } }
            }
        }
        Card {
            // 使っていないアプリを薄くする。段階の日数は既定のまま使う
            SwitchRow("頻度反映", s.usageReflected) { v -> update { it.copy(grayOnHome = v, grayInDrawer = v) } }
        }
    }

    @Composable
    private fun BadgesPage(s: LauncherSettings, update: ((LauncherSettings) -> LauncherSettings) -> Unit, apps: List<AppEntry>) {
        val badges = SAMPLE_BADGE_COUNTS.map { count ->
            when {
                count == null -> null
                s.badgeMode == BadgeMode.DOT -> BadgeDisplay.Dot
                s.badgeMode == BadgeMode.NUMBER -> BadgeDisplay.Count(count)
                else -> null
            }
        }
        IconRowPreview(apps, iconCache, s.iconStyle(), badges = badges)
        Card {
            Chips(BadgeMode.entries, s.badgeMode, { it.label }) { m -> update { it.copy(badgeMode = m) } }
        }
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("通知へのアクセス")
                    Note(if (notificationAccess) "許可済み" else "未許可（バッジは出ません）")
                }
                TextButton(onClick = ::openNotificationAccess) { Text("開く") }
            }
        }
    }

    @Composable
    private fun BackupPage(onClear: () -> Unit) {
        Card("書き出す") {
            Note("配置・設定・外観の個別設定をファイルに書き出します。保存先は自分で選びます。画像の具材は含みません。")
            SwitchRow("使用履歴も含める", includeUsage) { includeUsage = it }
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
        Card("使用履歴") {
            OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Text("使用履歴を削除") }
        }
    }

    @Composable
    private fun MonitorPage() {
        Note("ホームの背景の長押しからも開けます。")
        Card {
            NavRow("∿", "システムモニター", "CPU・メモリ・通信・温度・アプリごとの負荷") { openMonitor(null) }
            NavDivider()
            NavRow("▤", "アプリの棚卸し", "入っているアプリを使い方で見直す") { openMonitor(MonitorActivity.TAB_APPS) }
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

    private companion object {
        const val DEFAULT_TITLE = "Bento Home 設定"
        const val KEY_PAGE = "page"
    }
}

/** 使っていないアプリを薄く表示するか（頻度反映）。ホームと一覧をまとめて切り替える。 */
private val LauncherSettings.usageReflected: Boolean get() = grayOnHome || grayInDrawer

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
private fun NavDivider(indent: Dp = 50.dp) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(start = indent))
}

/** 一覧の大きな行。左の絵だけで何の設定か分かるようにする。 */
@Composable
private fun LargeNavRow(leading: @Composable () -> Unit, title: String, summary: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(LARGE_NAV_LEADING).clearAndSetSemantics { },
        ) { leading() }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics { })
    }
}

/** ホーム画面の小さな絵。今のマス目の数とドックの有無で描く。 */
@Composable
private fun HomeGlyph(s: LauncherSettings) {
    val frame = MaterialTheme.colorScheme.secondaryContainer
    val mark = MaterialTheme.colorScheme.onSecondaryContainer
    Canvas(Modifier.size(width = 36.dp, height = 56.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(frame, cornerRadius = CornerRadius(w * 0.2f))
        val pad = w * 0.14f
        val dockH = if (s.dockVisible) h * 0.12f else 0f
        val gridH = h - pad * 2 - dockH - (if (s.dockVisible) pad * 0.6f else 0f)
        val cw = (w - pad * 2) / s.baseColumns
        val ch = gridH / s.baseRows
        val dot = minOf(cw, ch) * 0.5f
        for (c in 0 until s.baseColumns) for (r in 0 until s.baseRows) {
            drawRoundRect(
                mark.copy(alpha = 0.55f),
                topLeft = Offset(pad + c * cw + (cw - dot) / 2, pad + r * ch + (ch - dot) / 2),
                size = Size(dot, dot),
                cornerRadius = CornerRadius(dot * 0.3f),
            )
        }
        if (s.dockVisible) {
            drawRoundRect(
                mark.copy(alpha = 0.35f),
                topLeft = Offset(pad, h - pad - dockH),
                size = Size(w - pad * 2, dockH),
                cornerRadius = CornerRadius(dockH * 0.4f),
            )
        }
    }
}

/** 補足の説明。小さく、色を抑える。 */
@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    // 文字を押しても切り替わるよう、行全体を押せるようにする（スイッチ自体は押しても行に任せる）
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 2.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** 増減の操作の1行。名前・−・値・＋。 */
@Composable
private fun StepperRow(label: String, value: Int, range: IntRange, enabled: Boolean = true, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(54.dp),
        )
        FilledTonalIconButton(
            onClick = { onChange(value - 1) },
            enabled = enabled && value > range.first,
            modifier = Modifier.size(34.dp).semantics { contentDescription = "${label}を減らす" },
        ) { Text("−") }
        Text(
            "$value",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(30.dp),
        )
        FilledTonalIconButton(
            onClick = { onChange(value + 1) },
            enabled = enabled && value < range.last,
            modifier = Modifier.size(34.dp).semantics { contentDescription = "${label}を増やす" },
        ) { Text("＋") }
    }
}

/** プレビューの横の、縦長のページ送りボタン。 */
@Composable
private fun PageButton(symbol: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(36.dp)
            .fillMaxHeight(PAGE_BUTTON_HEIGHT_RATIO)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Text(
            symbol,
            fontSize = 26.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else DISABLED_ALPHA),
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** オンオフの切り替え。プレビューで結果が見えるものは、スイッチの行ではなく小さなチップで縦に並べる。 */
private class Toggle(val label: String, val checked: Boolean, val onChange: (Boolean) -> Unit)

@Composable
private fun ToggleChips(toggles: List<Toggle>, modifier: Modifier = Modifier) {
    Column(modifier) {
        for (t in toggles) {
            FilterChip(
                selected = t.checked,
                onClick = { t.onChange(!t.checked) },
                label = { Text(t.label, maxLines = 1) },
                leadingIcon = if (t.checked) {
                    { Text("✓") }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
