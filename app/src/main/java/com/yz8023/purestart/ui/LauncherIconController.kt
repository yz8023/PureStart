package com.yz8023.purestart.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.yz8023.purestart.Prefs

/** The launcher alias, not a separate preference, is the authoritative state. */
class LauncherIconController(context: Context) {
    private val app = context.applicationContext
    private val alias = ComponentName(app, "com.yz8023.purestart.IconAlias")

    fun isShown(): Boolean = when (app.packageManager.getComponentEnabledSetting(alias)) {
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> false
        else -> true // ENABLED or manifest DEFAULT (the alias defaults to enabled)
    }

    /** Keep the existing preference in sync after PackageManager has accepted the change. */
    fun setShown(show: Boolean) {
        app.packageManager.setComponentEnabledSetting(alias,
            if (show) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP)
        Prefs.setDesktopIconPref(app, show)
    }
}
