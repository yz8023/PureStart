package com.yz8023.purestart.ui

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * 应用背景图片来源（对齐 yz8023/UI 的「极光 / 系统壁纸 / 自定义图片」三档）。
 *
 * 所有来源都被统一成一张限制尺寸的位图，并缓存到应用私有目录：
 * 系统壁纸或相册图片被删除、授权被撤销，都不会让背景变空白。
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
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        file.absolutePath
    } catch (_: Throwable) {
        null
    }

    fun deleteCustom(context: Context) {
        customFile(context).delete()
    }

    /** 系统桌面壁纸（只读取，不修改系统设置，不需要任何权限）。 */
    fun loadSystemBitmap(context: Context): Bitmap? = try {
        val manager = WallpaperManager.getInstance(context)
        val drawable = manager.drawable
        when (drawable) {
            is BitmapDrawable -> drawable.bitmap?.let { scaleDown(it) }
            null -> null
            else -> {
                val width = max(1, drawable.intrinsicWidth)
                val height = max(1, drawable.intrinsicHeight)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
                scaleDown(bitmap)
            }
        }
    } catch (_: Throwable) {
        null
    }

    fun loadCustomBitmap(path: String?): Bitmap? = try {
        if (path == null) null else {
            val file = File(path)
            if (!file.isFile) null else scaleDown(BitmapFactory.decodeFile(path) ?: return null)
        }
    } catch (_: Throwable) {
        null
    }

    /** 供 Compose 背景层取用；失败返回 null，界面自动回落极光背景。 */
    fun loadPainter(context: Context, mode: Int, path: String?): Painter? {
        val bitmap = when (mode) {
            MODE_SYSTEM -> loadSystemBitmap(context)
            MODE_CUSTOM -> loadCustomBitmap(path) ?: loadCustomBitmap(customFile(context).absolutePath)
            else -> null
        } ?: return null
        return BitmapPainterSafe(bitmap.asImageBitmap())
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

/** 位图 painter：整层铺满（背景层用 Crop 效果由调用方控制）。 */
private class BitmapPainterSafe(private val image: ImageBitmap) : Painter() {
    override val intrinsicSize: androidx.compose.ui.geometry.Size
        get() = androidx.compose.ui.geometry.Size(image.width.toFloat(), image.height.toFloat())

    override fun DrawScope.onDraw() {
        drawImage(image, dstSize = androidx.compose.ui.unit.IntSize(
            size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)))
    }
}
