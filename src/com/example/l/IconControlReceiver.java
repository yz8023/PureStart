package com.example.l;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * 桌面图标恢复入口（修复早期缺陷：隐藏图标后无法再进入模块）。
 *
 * 隐藏图标后 LSPosed 的「打开」与系统启动意图都会失效，此前没有任何恢复通道。
 * 现提供常驻 exported 广播：
 *   adb shell am broadcast -a com.example.l.action.SHOW_ICON
 * 效果：重新启用 IconAlias（图标回到桌面）并直接打开设置页。
 */
public class IconControlReceiver extends BroadcastReceiver {
    public static final String ACTION_SHOW_ICON = "com.example.l.action.SHOW_ICON";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_SHOW_ICON.equals(intent.getAction())) return;
        try {
            context.getPackageManager().setComponentEnabledSetting(
                    new ComponentName(context, "com.example.l.IconAlias"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
            Log.i("ModKit", "SHOW_ICON: launcher alias re-enabled");
        } catch (Throwable t) {
            Log.w("ModKit", "SHOW_ICON failed: " + t);
        }
        try {
            Intent open = new Intent(context, SettingsActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(open);
        } catch (Throwable t) {
            Log.w("ModKit", "open settings after SHOW_ICON failed: " + t);
        }
    }
}
