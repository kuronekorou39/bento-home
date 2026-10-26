package com.rou39.bentohome.icon

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import com.rou39.bentohome.data.IconShape

/**
 * アイコン画像のキャッシュ（docs/design.md §11 の最小版）。
 * 上限はアプリのメモリクラスの 1/8。パッケージ更新時に該当分を捨てる。
 */
class IconCache(context: Context) {
    private val maxBytes = context.getSystemService(ActivityManager::class.java).memoryClass * 1024 * 1024 / 8

    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    fun invalidatePackage(packageName: String) {
        val prefix = "$packageName/"
        cache.snapshot().keys.filter { it.startsWith(prefix) }.forEach { cache.remove(it) }
    }

    companion object {
        fun key(component: String, profileSerial: Long, sizePx: Int, shape: IconShape) =
            "$component#$profileSerial@$sizePx:${shape.name}"
    }
}
