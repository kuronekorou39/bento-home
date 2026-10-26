package com.rou39.bentohome.monitor

import android.os.IBinder
import android.util.Log
import com.rou39.bentohome.monitor.priv.IPrivilegedService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * シェル権限で動く計測用プロセス（[com.rou39.bentohome.monitor.priv.PrivilegedService]）との接続（docs/monitor.md）。
 *
 * 起動と Binder の受け渡しはここでは行わない。外部から [connect] で窓口を渡す。
 * 渡されていない間は [service] が null のままで、各機能はアプリの権限で読める範囲に縮む。
 */
class PrivilegedBridge {
    enum class Status { NOT_CONNECTED, READY }

    private val _service = MutableStateFlow<IPrivilegedService?>(null)
    val service: StateFlow<IPrivilegedService?> = _service.asStateFlow()

    private val _status = MutableStateFlow(Status.NOT_CONNECTED)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** 計測用プロセスを使いたい画面があるか。起動する側は、false の間はプロセスを止めてよい。 */
    private val _inUse = MutableStateFlow(false)
    val inUse: StateFlow<Boolean> = _inUse.asStateFlow()

    private var users = 0
    private var deathRecipient: IBinder.DeathRecipient? = null

    /** 差し込み口: 計測用プロセスの窓口を渡す。プロセスが終わると自動で切り離す。 */
    fun connect(binder: IBinder) {
        disconnect()
        if (!binder.pingBinder()) return
        val recipient = IBinder.DeathRecipient { clear() }
        runCatching { binder.linkToDeath(recipient, 0) }.onFailure {
            Log.w(TAG, "linkToDeath failed", it)
            return
        }
        deathRecipient = recipient
        _service.value = IPrivilegedService.Stub.asInterface(binder)
        _status.value = Status.READY
    }

    fun disconnect() {
        val binder = _service.value?.asBinder()
        deathRecipient?.let { r -> binder?.let { runCatching { it.unlinkToDeath(r, 0) } } }
        clear()
    }

    fun acquire() {
        users++
        _inUse.value = true
    }

    fun release() {
        users = (users - 1).coerceAtLeast(0)
        _inUse.value = users > 0
    }

    /** シェル権限でコマンドを実行する。接続していなければ null。 */
    fun exec(vararg command: String): String? = runCatching { _service.value?.exec(arrayOf(*command)) }
        .onFailure { Log.w(TAG, "exec failed", it) }
        .getOrNull()

    private fun clear() {
        deathRecipient = null
        _service.value = null
        _status.value = Status.NOT_CONNECTED
    }

    private companion object {
        const val TAG = "PrivilegedBridge"
    }
}
