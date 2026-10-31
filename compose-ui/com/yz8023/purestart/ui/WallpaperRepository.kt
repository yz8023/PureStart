package com.yz8023.purestart.ui

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.IntSize
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * 应用背景图片来源（对齐 yz8023/UI 的「极光 / 系统壁纸 / 自定义图片」三档）。
 *
 * 系统壁纸按参考仓库 `素材库/网络壁纸与离线缓存/代码/SystemWallpaper.kt` 的做法重写
 * （v15.1.6）。上一版我用了「透明窗口 + FLAG_SHOW_WALLPAPER」，那条路在多数 ROM 上
 * 根本透不出壁纸，等于没实现。参考仓库能读到壁纸的真正原因：
 *
 *  1. **不设运行时权限门**：直接同步读 `WallpaperManager.getInstance(context).drawable`
 *     （Drawable 懒解码，组合期读取代价可接受），任何异常一律降级，不弹权限框；
 *  2. **关键前提是「所有文件访问」（MANAGE_EXTERNAL_STORAGE）**：
 *     - Android 12 及以下：真实壁纸需要 `READ_EXTERNAL_STORAGE`；
 *     - Android 13：系统只返回默认壁纸（部分 ROM 抛异常）；
 *     - **Android 14+：`getDrawable()` 必定抛 SecurityException，
 *       除非应用持有「所有文件访问」**（官方文档明示，Google 标记 Won't Fix）。
 *     所以清单里必须声明该权限，并在用户选择「系统壁纸」时把用户带到
 *     `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` 系统设置页授权；
 *  3. `getWallpaperFile(FLAG_SYSTEM)` 作为第二条通道（同一个授权，个别 ROM 只认其中一条）；
 *  4. 拿不到就明确告诉用户「未授权/平台拒绝」，绝不假装成功。
 *
 * 只在 IO 线程调用位图读取。
 */
object WallpaperRepository {
    const val MODE_AURORA = 0
    const val MODE_SYSTEM = 1
    const val MODE_CUSTOM = 2

    private const val MAX_EDGE = 2048
    private const val FILE_NAME = "app_background.png"

    fun customFile(context: Context): File = File(context.filesDir, "wallpapers/$FILE_NAME")

    // ---------------------------------------------------------------- 系统壁纸

    /** 当前是否持有「所有文件访问」：Android 14+ 读取真实壁纸的必要条件。 */
    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

    /** 引导用户去系统设置开启「所有文件访问」（没有运行时权限弹窗可替代）。 */
    fun allFilesAccessSettingsIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) null
        else Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    /**
     * 读取系统桌面壁纸（对照参考仓库 loadSystemWallpaper）。
     *
     * 先走 `drawable`，失败再走 `getWallpaperFile(FLAG_SYSTEM)` 直接解码；
     * 两条都被拒时返回 null，由调用方提示「未授权」，绝不返回假数据。
     */
    fun loadSystemWallpaper(context: Context): Bitmap? {
        val manager = try {
            WallpaperManager.getInstance(context)
        } catch (_: Throwable) {
            return null
        }
        // 通道 ①：实时壁纸 Drawable（参考仓库主路径）
        try {
            val drawable: Drawable? = manager.drawable
            drawableToBitmap(drawable)?.let { return scaleDown(it) }
        } catch (_: Throwable) {
            // Android 14+ 未授权就在这里抛出，继续尝试文件通道
        }
        // 通道 ②：直接解码系统壁纸文件（同一个授权，个别 ROM 只认这条）
        return try {
            val descriptor = manager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM) ?: return null
            descriptor.use {
                BitmapFactory.decodeFileDescriptor(it.fileDescriptor)?.let { bitmap -> scaleDown(bitmap) }
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun isSystemWallpaperAvailable(context: Context): Boolean = loadSystemWallpaper(context) != null

    /** 给设置页展示的一句话状态；空字符串表示读取正常。 */
    fun systemWallpaperStatus(context: Context): String = when {
        isSystemWallpaperAvailable(context) -> ""
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !hasAllFilesAccess() ->
            "系统拒绝读取壁纸：请开启「所有文件访问」后重试（Android 14 起必须）"
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasAllFilesAccess() ->
            "未授权读取壁纸：可开启「所有文件访问」，或改用自定义图片"
        else -> "系统未返回壁纸位图（可能使用了动态壁纸），建议改用自定义图片"
    }

    // ---------------------------------------------------------------- 自定义图片

    fun saveCustom(context: Context, bitmap: Bitmap): String? = try {
        val file = customFile(context)
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out -> scaleDown(bitmap).compress(Bitmap.CompressFormat.JPEG, 92, out) }
        file.absolutePath
    } catch (_: Throwable) {
        null
    }

    fun deleteCustom(context: Context) {
        customFile(context).delete()
    }

    fun loadCustomBitmap(path: String?): Bitmap? = try {
        val file = if (path == null) null else File(path)
        if (file == null || !file.isFile) null
        else BitmapFactory.decodeFile(file.absolutePath)?.let { scaleDown(it) }
    } catch (_: Throwable) {
        null
    }

    // ---------------------------------------------------------------- 背景层

    /**
     * 背景层用的 painter。返回 null 表示这一层不画图（例如系统壁纸读不到、
     * 已退回窗口合成），此时界面保持透明并由调用方提示状态。
     */
    fun loadPainter(context: Context, mode: Int, path: String?): Painter? {
        val bitmap = when (mode) {
            MODE_SYSTEM -> loadSystemWallpaper(context)
            MODE_CUSTOM -> loadCustomBitmap(path) ?: loadCustomBitmap(customFile(context).absolutePath)
            else -> null
        } ?: return null
        return BitmapPainterSafe(bitmap.asImageBitmap())
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

/** 位图 painter：铺满目标区域。 */
private class BitmapPainterSafe(private val image: ImageBitmap) : Painter() {
    override val intrinsicSize: Size get() = Size(image.width.toFloat(), image.height.toFloat())

    override fun DrawScope.onDraw() {
        drawImage(image, dstSize = IntSize(
            size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1)))
    }
}
