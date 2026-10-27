package com.rou39.bentohome.monitor.priv

import android.content.AttributionSource
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.system.Os
import android.system.OsConstants
import java.io.File
import kotlin.system.exitProcess

/**
 * 計測用プロセスの入口。PC の adb から `app_process` で起動する（コマンドは [PrivilegedProtocol.startCommand]）。
 *
 * 1. [PrivilegedService] を作り、Bento の受け渡し口（ContentProvider）に窓口を渡す。
 * 2. 渡せたら標準入出力を閉じて adb を返し、そのまま待機する（読まれない間は CPU を使わない）。
 * 3. Bento のプロセスが終わったら、起動し直されるのを待って渡し直す。
 * 4. Bento が更新・削除されたら（APK が消えたら）、古いコードのまま動き続けないよう終了する。
 *
 * シェルのプロセスには Context が無いので、ContentProvider は隠し API（IActivityManager）を
 * リフレクションで呼ぶ。app_process で起動したプロセスには隠し API の制限がかからない。
 */
object Starter {
    private const val TAG = "BentoStarter"
    private const val SHELL_PACKAGE = "com.android.shell"
    private const val USER_ID = 0
    private const val RESEND_DELAY_MS = 2_000L
    /** Bento の再起動を待つ回数。約1分。 */
    private const val RESEND_ATTEMPTS = 30

    private lateinit var packageName: String
    private var apkPath: String? = null
    private lateinit var service: PrivilegedService
    private lateinit var handler: Handler
    private var token: IBinder? = null

    @JvmStatic
    fun main(args: Array<String>) {
        packageName = args.firstOrNull() ?: run {
            System.err.println("使い方: app_process /system/bin ${Starter::class.java.name} <パッケージ名>")
            exitProcess(2)
        }
        apkPath = System.getenv("CLASSPATH")
        // モニターの一覧で「Bento の計測用プロセス」と分かるよう、プロセス名を「パッケージ名:monitor」にする（AppRows）
        runCatching {
            android.os.Process::class.java.getMethod("setArgV0", String::class.java).invoke(null, "$packageName:monitor")
        }
        Looper.prepareMainLooper()
        handler = Handler(Looper.getMainLooper())
        service = PrivilegedService()

        val error = runCatching { send() }.exceptionOrNull()
        if (error != null) {
            System.err.println("$TAG: Bento に接続できませんでした: $error")
            exitProcess(1)
        }
        println("$TAG: Bento に接続しました（pid ${Os.getpid()}）。端末を再起動するまで動き続けます。")
        detachFromTerminal()
        Looper.loop()
    }

    /** Bento の uid を聞いてから窓口を渡す。失敗したら例外を投げる。 */
    private fun send() {
        val authority = PrivilegedProtocol.authority(packageName)
        val hello = callProvider(authority, PrivilegedProtocol.METHOD_HELLO, null)
            ?: error("$authority から応答がありません")
        service.allowedUid = hello.getInt(PrivilegedProtocol.KEY_UID, -1).also { check(it >= 0) { "uid が不明です" } }

        val extras = Bundle().apply { putBinder(PrivilegedProtocol.KEY_BINDER, service) }
        val reply = callProvider(authority, PrivilegedProtocol.METHOD_CONNECT, extras)
        val newToken = reply?.getBinder(PrivilegedProtocol.KEY_TOKEN) ?: error("窓口を受け取ってもらえませんでした")
        newToken.linkToDeath({ handler.post { resend(RESEND_ATTEMPTS) } }, 0)
        token = newToken
    }

    private fun resend(remaining: Int) {
        token = null
        val apk = apkPath
        if (apk != null && !File(apk).exists()) exitProcess(0)
        if (runCatching { send() }.isSuccess) return
        if (remaining <= 1) exitProcess(0)
        handler.postDelayed({ resend(remaining - 1) }, RESEND_DELAY_MS)
    }

    private fun callProvider(authority: String, method: String, extras: Bundle?): Bundle? {
        val am = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null)!!
        val amMethods = am.javaClass.methods
        val clientToken = Binder()
        // getContentProviderExternal(String name, int userId, IBinder token, String tag)
        val holder = amMethods.first { it.name == "getContentProviderExternal" && it.parameterTypes.size == 4 }
            .invoke(am, authority, USER_ID, clientToken, TAG) ?: return null
        try {
            val provider = holder.javaClass.getField("provider").get(holder)!!
            // call(AttributionSource, String authority, String method, String arg, Bundle extras)
            val call = provider.javaClass.methods.first {
                it.name == "call" && it.parameterTypes.size == 5 && it.parameterTypes[0] == AttributionSource::class.java
            }
            val source = AttributionSource.Builder(Os.getuid()).setPackageName(SHELL_PACKAGE).build()
            return call.invoke(provider, source, authority, method, null, extras) as Bundle?
        } finally {
            runCatching {
                amMethods.first { it.name == "removeContentProviderExternalAsUser" }
                    .invoke(am, authority, clientToken, USER_ID)
            }
        }
    }

    /** 標準入出力を /dev/null に付け替えて、adb shell が終了を待たずに戻れるようにする。 */
    private fun detachFromTerminal() {
        System.out.flush()
        System.err.flush()
        val devNull = Os.open("/dev/null", OsConstants.O_RDWR, 0)
        for (stdio in 0..2) Os.dup2(devNull, stdio)
        Os.close(devNull)
    }
}
