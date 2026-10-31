package com.yz8023.purestart

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.yz8023.purestart.ui.PureStartShell
import com.yz8023.purestart.ui.PureStartTheme
import com.yz8023.purestart.repository.SettingsRepository
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SettingsRepository(applicationContext)
        repository = store
        setContent { PureStartTheme(store) { PureStartShell(store) } }
    }

    override fun onDestroy() {
        repository?.close()
        repository = null
        super.onDestroy()
    }
}
