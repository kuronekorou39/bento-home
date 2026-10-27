package com.rou39.bentohome.monitor

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.rou39.bentohome.container
import com.rou39.bentohome.monitor.priv.PrivilegedProtocol
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 計測用プロセス（[com.rou39.bentohome.monitor.priv.Starter]）から窓口を受け取る口（docs/monitor.md）。
 * マニフェストで INTERACT_ACROSS_USERS_FULL を要求し、さらに呼び出し元がシェルか root であることを確かめる。
 */
class PrivilegedReceiver : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val caller = Binder.getCallingUid()
        if (caller != PrivilegedProtocol.SHELL_UID && caller != PrivilegedProtocol.ROOT_UID) {
            throw SecurityException("uid $caller からの接続は受け付けません")
        }
        return when (method) {
            PrivilegedProtocol.METHOD_HELLO -> Bundle().apply { putInt(PrivilegedProtocol.KEY_UID, Process.myUid()) }
            PrivilegedProtocol.METHOD_CONNECT -> {
                val binder = extras?.getBinder(PrivilegedProtocol.KEY_BINDER) ?: return null
                val ctx = context ?: return null
                // ContentProvider は Application.onCreate より先に呼ばれうるので、メインスレッドで渡す
                if (!onMainThread { ctx.container.privileged.connect(binder) }) return null
                Bundle().apply { putBinder(PrivilegedProtocol.KEY_TOKEN, token) }
            }
            else -> null
        }
    }

    private fun onMainThread(block: () -> Unit): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
            return true
        }
        val done = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            try { block() } finally { done.countDown() }
        }
        return done.await(MAIN_TIMEOUT_S, TimeUnit.SECONDS)
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0

    private companion object {
        const val MAIN_TIMEOUT_S = 5L
        /** 計測用プロセスが Bento の終了を知るための目印。プロセスが生きている間だけ存在する。 */
        val token = Binder()
    }
}
