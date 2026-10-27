package com.rou39.bentohome.monitor.priv

/**
 * 計測用プロセス（[Starter]）と Bento（[com.rou39.bentohome.monitor.PrivilegedReceiver]）の間の取り決め（docs/monitor.md）。
 * 両方から参照するので、Android のアプリ用の API に依存しない値だけを置く。
 */
object PrivilegedProtocol {
    /** 受け取り口の ContentProvider の authority。マニフェストの `${applicationId}.privileged` と一致させる。 */
    fun authority(packageName: String) = "$packageName.privileged"

    /** Bento の uid を聞く。計測用プロセスは、窓口を渡す前にこの uid だけを受け付けるようにする。 */
    const val METHOD_HELLO = "hello"

    /** 窓口を渡す。返り値の [KEY_TOKEN] が死んだら Bento が終わったとみなす。 */
    const val METHOD_CONNECT = "connect"

    const val KEY_UID = "uid"
    const val KEY_BINDER = "binder"
    const val KEY_TOKEN = "token"

    const val ROOT_UID = 0
    const val SHELL_UID = 2000

    /** PC の adb から計測用プロセスを起動するコマンド。PowerShell でも bash でもそのまま貼れる。 */
    fun startCommand(packageName: String) =
        "adb shell 'p=\$(pm path $packageName | head -n1 | cut -d: -f2); " +
            "CLASSPATH=\$p setsid app_process /system/bin ${Starter::class.java.name} $packageName &'"
}
