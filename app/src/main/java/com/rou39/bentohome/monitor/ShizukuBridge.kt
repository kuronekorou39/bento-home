package com.rou39.bentohome.monitor

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.rou39.bentohome.BuildConfig
import com.rou39.bentohome.monitor.priv.IPrivilegedService
import com.rou39.bentohome.monitor.priv.PrivilegedService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/**
 * Shizuku 経由でシェル権限のプロセスを使う（docs/monitor.md）。
 * Shizuku が無い・止まっている・許可が無いときは [service] が null のままで、各機能は使える範囲に縮む。
 */
class ShizukuBridge(private val context: Context) {
    enum class Status { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, CONNECTING, READY }

    private val _status = MutableStateFlow(Status.NOT_RUNNING)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _service = MutableStateFlow<IPrivilegedService?>(null)
    val service: StateFlow<IPrivilegedService?> = _service.asStateFlow()

    /** 権限プロセスを使いたい画面の数。0 になったらプロセスを終わらせる。 */
    private var users = 0
    private var bound = false

    private val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, PrivilegedService::class.java.name))
        .daemon(false)
        .processNameSuffix("monitor")
        .debuggable(BuildConfig.DEBUG)
        // 版が同じだと Shizuku は古いプロセスを使い回す。更新のたびに作り直すようインストール時刻を使う
        .version((context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime / 1000).toInt())

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder == null || !binder.pingBinder()) return
            _service.value = IPrivilegedService.Stub.asInterface(binder)
            refresh()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _service.value = null
            bound = false
            refresh()
        }
    }

    init {
        Shizuku.addBinderReceivedListenerSticky { refresh(); bindIfWanted() }
        Shizuku.addBinderDeadListener {
            _service.value = null
            bound = false
            refresh()
        }
        Shizuku.addRequestPermissionResultListener { _, _ -> refresh(); bindIfWanted() }
        refresh()
    }

    fun refresh() {
        _status.value = when {
            !installed() -> Status.NOT_INSTALLED
            !Shizuku.pingBinder() -> Status.NOT_RUNNING
            !granted() -> Status.NO_PERMISSION
            _service.value == null -> Status.CONNECTING
            else -> Status.READY
        }
    }

    fun requestPermission() {
        if (Shizuku.pingBinder() && !granted()) Shizuku.requestPermission(REQUEST_CODE)
    }

    fun acquire() {
        users++
        bindIfWanted()
    }

    fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0 && bound) {
            // 使わない間は権限プロセスを残さない
            runCatching { Shizuku.unbindUserService(args, connection, true) }
            bound = false
            _service.value = null
            refresh()
        }
    }

    /** シェル権限でコマンドを実行する。使えなければ null。 */
    fun exec(vararg command: String): String? = runCatching { _service.value?.exec(arrayOf(*command)) }
        .onFailure { Log.w(TAG, "exec failed", it) }
        .getOrNull()

    private fun bindIfWanted() {
        if (users == 0 || bound || !Shizuku.pingBinder() || !granted()) return
        runCatching {
            Shizuku.bindUserService(args, connection)
            bound = true
        }.onFailure { Log.w(TAG, "bind failed", it) }
        refresh()
    }

    private fun granted(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    private fun installed(): Boolean = runCatching {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    }.getOrDefault(false)

    private companion object {
        const val TAG = "ShizukuBridge"
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val REQUEST_CODE = 39
    }
}
