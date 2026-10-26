package com.rou39.bentohome.backup

import android.content.Context
import android.net.Uri
import com.rou39.bentohome.AppContainer
import com.rou39.bentohome.layout.AppRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 手動バックアップの書き出しと読み込み（plan.md §5・§6）。
 * 保存先はユーザーがシステムのファイル選択で選ぶ。このアプリ自身は通信しない。
 */
class BackupService(private val context: Context, private val c: AppContainer) {

    suspend fun export(uri: Uri, includeUsage: Boolean) = withContext(Dispatchers.IO) {
        val file = BackupCodec.fromState(
            workspace = c.workspaceRepository.load(),
            settings = c.settingsRepository.settings.first(),
            appearances = c.appearanceRepository.appearances.first(),
            usage = if (includeUsage) c.usageRepository.usage.first() else null,
            exportedAt = System.currentTimeMillis(),
            appVersion = appVersion(),
        )
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("書き出し先を開けませんでした")
        stream.bufferedWriter(Charsets.UTF_8).use { it.write(BackupCodec.encode(file)) }
    }

    suspend fun read(uri: Uri): RestorePlan = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri) ?: error("ファイルを開けませんでした")
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val catalog = c.appCatalog
        val known = catalog.byRef.value
        fun installed(ref: AppRef) = ref in known ||
            (ref.profileSerial == catalog.mainProfileSerial && catalog.isInstalled(ref.packageName))
        BackupCodec.toRestorePlan(BackupCodec.decode(text), ::installed, c.settingsRepository.settings.first())
    }

    /**
     * 復元する。ホームが動いていればホームに渡し、配置の変更として取り消せるようにする。
     * 動いていなければ直接保存し、次にホームが開いたときに読み込まれる。
     */
    suspend fun apply(plan: RestorePlan) {
        if (c.restoreRequests.subscriptionCount.value > 0) {
            c.restoreRequests.emit(plan)
            return
        }
        c.workspaceRepository.save(plan.workspace)
        applyNonLayout(c, plan)
    }

    private fun appVersion(): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

    companion object {
        /** 配置以外（設定・外観の個別設定・使用履歴）を置き換える。 */
        suspend fun applyNonLayout(c: AppContainer, plan: RestorePlan) {
            c.settingsRepository.update { plan.settings }
            c.appearanceRepository.replaceAll(plan.appearances)
            plan.usage?.let {
                c.usageRepository.replaceAll(it)
                // バックアップに無いアプリも、今から観測を始める
                c.usageRepository.observe(c.appCatalog.apps.value.map { e -> e.ref })
            }
        }
    }
}
