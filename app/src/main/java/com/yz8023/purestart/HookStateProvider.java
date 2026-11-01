package com.yz8023.purestart;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * Hook 开关查询入口。
 *
 * Hook 侧（目标应用进程内）通过:
 *   getContentResolver().call(Uri.parse("content://com.yz8023.purestart.hookstate"),
 *                             "enabled", 目标包名, null)
 * 读取设置页的勾选状态（模块私有偏好）。
 *
 * 为什么用它：vivo 等国产 ROM 即便授予 WRITE_SETTINGS 也会拦 Settings.System 写入，
 * 且模块 App 未被注入时 prefs 不可被目标进程直接读；exported Provider +
 * Manifest 的 forceQueryable 是最通用的跨进程通道（FPA/LSPatch/LSPosed 全兼容）。
 */
public class HookStateProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle b = new Bundle();
        if (arg == null) return b;
        Context ctx = getContext();
        if ("enabled".equals(method)) {
            b.putBoolean("v", ctx != null && Prefs.hookEnabled(ctx, arg));
        } else if ("config".equals(method)) {
            // 细分子项开关一并返回（Hook 侧 2s 缓存，回调触发时查询；未设置 = 不动作）
            b.putBoolean("enabled",   ctx != null && Prefs.hookEnabled(ctx, arg));
            b.putBoolean("splash",    ctx != null && Prefs.subEnabled(ctx, Prefs.PREFIX_SUB_SPLASH, arg));
            b.putBoolean("feed",      ctx != null && Prefs.subEnabled(ctx, Prefs.PREFIX_SUB_FEED, arg));
            b.putBoolean("popup",     ctx != null && Prefs.subEnabled(ctx, Prefs.PREFIX_SUB_POPUP, arg));
            b.putBoolean("dexfinder", ctx != null && Prefs.subEnabled(ctx, Prefs.PREFIX_SUB_DEXFINDER, arg));
        }
        return b;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    /**
     * Hook 自检回传通道（目标应用进程 → 设置页）。
     * Main.InstallReport 在安装完成后 insert 一行 {pkg, ok, miss, fails, version, at}，
     * 持久化到 selfcheck 偏好，设置页「Hook 自检」分组读取展示。
     * 注意：exported Provider 无鉴权，理论上任意 App 可伪造上报（仅影响展示，不影响开关），已在 README 注明取舍。
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        try {
            if (values == null) return null;
            String pkg = values.getAsString("pkg");
            if (pkg == null) return null;
            Context c = getContext();
            if (c == null) return null;
            String row = values.getAsInteger("ok") + "|"
                    + values.getAsInteger("miss") + "|"
                    + values.getAsString("fails") + "|"
                    + values.getAsString("version") + "|"
                    + values.getAsLong("at");
            Prefs.writeSelfCheck(c, pkg, row);
        } catch (Throwable t) {
            android.util.Log.w("ModKit", "selfcheck insert failed: " + t);
        }
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        return 0;
    }
}
