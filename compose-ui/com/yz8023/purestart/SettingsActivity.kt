package com.yz8023.purestart

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.yz8023.purestart.ui.PureStartShell
import com.yz8023.purestart.ui.PureStartTheme
import com.yz8023.purestart.ui.SettingsRepository
import java.util.Locale

/** Settings entry used by the launcher alias, LSPosed, and icon recovery receiver. */
class SettingsActivity : ComponentActivity() {
    /** onCreate 早期抛异常时 onDestroy 也会执行，因此不能依赖 lateinit 已赋值。 */
    private var repository: SettingsRepository? = null

    override fun attachBaseContext(base: Context) {
        val mode = try { Prefs.localeMode(base) } catch (_: Throwable) { 0 }
        val localized = if (mode == 1) {
            val configuration = Configuration()
            configuration.setLocale(Locale.SIMPLIFIED_CHINESE)
            configuration.setLocales(LocaleList.forLanguageTags("zh-CN"))
            base.createConfigurationContext(configuration)
        } else base
        super.attachBaseContext(localized)
    }

    /**
     * 系统壁纸模式：透明窗口 + FLAG_SHOW_WALLPAPER，由系统把桌面壁纸画在窗口下层。
     *
     * 这是 Android 13+ 唯一可行的「使用系统壁纸」方式——普通应用已被禁止读取壁纸位图
     * （WallpaperManager.getDrawable() 在 API 34 必定 SecurityException，Google Won't Fix）。
     * 壁纸由系统合成在本窗口之下，Compose 的 RenderEffect 依然能折射到它。
     */
    private fun applyWallpaperWindow(enabled: Boolean) {
        if (enabled) {
            window.setBackgroundDrawable(null)
            window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    window.decorView.systemUiVisibility or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
            @Suppress("DEPRECATION")
            window.statusBarColor = AndroidColor.TRANSPARENT
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 主题必须在 super.onCreate 之前设定：它决定窗口 surface 是否透明，
        // 透明才能让系统把桌面壁纸合成到窗口下层（FLAG_SHOW_WALLPAPER）。
        val wallpaperMode = try { Prefs.wallpaperMode(this) == 1 } catch (_: Throwable) { false }
        if (wallpaperMode) setTheme(R.style.AppTheme_Wallpaper)
        super.onCreate(savedInstanceState)
        applyWallpaperWindow(wallpaperMode)
        val store = SettingsRepository(applicationContext)
        repository = store
        setContent { PureStartTheme { PureStartShell(store) } }
    }

    override fun onDestroy() {
        repository?.close()
        repository = null
        super.onDestroy()
    }
}
