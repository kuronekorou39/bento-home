package com.rou39.bentohome.home

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.rou39.bentohome.monitor.PrivilegedBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 通知パネルを開く（plan.md §10）。公開の API が無いので、他のランチャーと同じく
 * StatusBarManager.expandNotificationsPanel をリフレクションで呼ぶ（EXPAND_STATUS_BAR は通常の権限）。
 * 呼べなかったときは、計測用プロセスが動いていればシェルの `cmd statusbar` で開く。
 */
object NotificationPanel {
    private const val TAG = "NotificationPanel"

    @SuppressLint("WrongConstant")
    fun expand(context: Context, privileged: PrivilegedBridge, scope: CoroutineScope) {
        val opened = runCatching {
            val manager = context.getSystemService("statusbar") ?: error("no statusbar service")
            manager.javaClass.getMethod("expandNotificationsPanel").invoke(manager)
        }.onFailure { Log.w(TAG, "expandNotificationsPanel failed", it) }.isSuccess
        if (opened) return
        if (privileged.service.value == null) return
        scope.launch(Dispatchers.IO) { privileged.exec("cmd", "statusbar", "expand-notifications") }
    }
}
