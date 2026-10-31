package com.yz8023.purestart.ui

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntSize
import com.yz8023.purestart.Prefs
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * 应用背景图片来源（对齐 yz8023/UI 的「极光 / 系统壁纸 / 自定义图片」三档）。
 *
 * 系统壁纸的取法（v15.1.4 修正）：
 *  - Android 13 (API 33) 起，普通应用**无法**再读取壁纸位图：
 *    `WallpaperManager.getDrawable()` 在 13 上返回系统默认壁纸或抛 SecurityException，
 *    Android 14 起必定抛 SecurityException（Google 标记 Won't Fix）。
 *    所以高版本改用官方推荐方式：透明窗口 + `FLAG_SHOW_WALLPAPER`，
 *    由系统把桌面壁纸画在窗口下层，玻璃的 RenderEffect 依然能折射到它。
 *  - Android 12 及以下在授予读取权限时可以取到真实壁纸：先尽量取，取不到同样退回
 *    `FLAG_SHOW_WALLPAPER`，两条路径都不需要用户额外操作也能看到壁纸。
 *
 * 所有位图都会限制长边并缓存到应用私有目录：相册图片被删除或授权被撤销都不会让背景变空白。
 * 只在 IO 线程调用。
 */
object WallpaperRepository {
    const val MODE_AURORA = 0
    const val MODE_SYSTEM = 1
    const val MODE_CUSTOM = 2

    private const val MAX_EDGE = 2048
    private const val FILE_NAME = "app_background.png"

    fun customFile(context: Context): File = File(context.filesDir, "wallpapers/$FILE_NAME")

    /** 自定义图片落盘（相册选择后调用），返回私有路径。 */
    fun saveCustom(context: Context, bitmap: Bitmap): String? = try {
        val file = customFile(context)
        file.parentFile?.mkdirs()
        val scaled = scaleDown(bitmap)
        FileOutputStream(file).use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, 92, out) }
        file.absolutePath
    } catch (_: Throwable) {
        null
    }

    fun deleteCustom(context: Context) {
        customFile(context).delete()
    }

    /**
     * 系统壁纸位图。Android 13+ 不再尝试（系统已禁止，只会得到默认壁纸或异常），
     * 直接返回 null，交由窗口层的 FLAG_SHOW_WALLPAPER 显示。
     */
    fun loadSystemBitmap(context: Context): Bitmap? {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) return null
        return try {
            val drawable = WallpaperManager.getInstance(context).drawable
            drawableToBitmap(drawable)?.let { scaleDown(it) }
        } catch (_: Throwable) {
            null
        }
    }

    fun loadCustomBitmap(path: String?): Bitmap? = try {
        val file = if (path == null) null else File(path)
        if (file == null || !file.isFile) null
        else BitmapFactory.decodeFile(file.absolutePath)?.let { scaleDown(it) }
    } catch (_: Throwable) {
        null
    }

    /**
     * 背景层用的 painter。返回 null 表示「这一层不需要画图」：
     * 系统壁纸模式下窗口自己显示壁纸，背景层保持透明，玻璃才不会把壁纸叠两遍。
     */
    fun loadPainter(context: Context, mode: Int, path: String?): Painter? {
        val bitmap = when (mode) {
            MODE_SYSTEM -> loadSystemBitmap(context)
            MODE_CUSTOM -> loadCustomBitmap(path) ?: loadCustomBitmap(customFile(context).absolutePath)
            else -> null
        } ?: return null
        return BitmapPainterSafe(bitmap.asImageBitmap())
    }

    /**
     * 应用背景时顺便把「系统壁纸这次到底能不能取到」写进偏好，设置页直接展示，
     * 避免用户只看到「没反应」却不知道为什么。
     */
    fun probeSystemWallpaper(context: Context): String = try {
        val bitmap = loadSystemBitmap(context)
        when {
            bitmap != null -> ""              // 取到真实壁纸
            Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2 ->
                "Android 13+ 已禁止应用直接读取壁纸位图（Google Won't Fix），已改用系统合成显示"
            else -> "系统未返回壁纸位图（可能使用了动态壁纸），已改用系统合成显示"
        }
    } catch (t: Throwable) {
        "读取失败：${t.javaClass.simpleName}，已改用系统合成显示"
    }

    fun rememberProbe(context: Context) {
        Prefs.setWallpaperError(context, probeSystemWallpaper(context))
    }

    private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        if (drawable is BitmapDrawable) return drawable.bitmap
        val width = max(1, drawable.intrinsicWidth)
        val height = max(1, drawable.intrinsicHeight)
        return try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            bitmap
        } catch (_: Throwable) {
            null
        }
    }

    private fun scaleDown(source: Bitmap): Bitmap {
        val edge = max(source.width, source.height)
        if (edge <= MAX_EDGE) return source
        val ratio = MAX_EDGE.toFloat() / edge
        val scaled = Bitmap.createScaledBitmap(source, (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1), true)
        if (scaled !== source) source.recycle()
        return scaled
    }
}

/** 位图 painter：铺满目标区域（缩放方式由调用方的 ContentScale 决定）。 */
private class BitmapPainterSafe(private val image: ImageBitmap) : Painter() {
    override val intrinsicSize: Size get() = Size(image.width.toFloat(), image.height.toFloat())

    override fun DrawScope.onDraw() {
        drawImage(image, dstSize = IntSize(
            size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)))
    }
}
