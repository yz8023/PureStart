package com.example.l;

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
 *   getContentResolver().call(Uri.parse("content://com.example.l.hookstate"),
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
        if ("enabled".equals(method) && arg != null) {
            boolean v = true;
            try {
                Context c = getContext();
                if (c != null) {
                    v = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
                            .getBoolean("hook_" + arg, true);
                }
            } catch (Throwable t) {
                v = true;
            }
            b.putBoolean("v", v);
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

    @Override
    public Uri insert(Uri uri, ContentValues values) {
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
