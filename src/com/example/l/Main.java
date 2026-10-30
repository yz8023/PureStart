package com.example.l;

import android.app.Activity;
import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/**
 * PureStart (ModKit Edition) — 现代化架构 Xposed 模块入口
 * 全面整合 XiaoCan 净化、通用广告过滤与 DexFinder 自动特征识别
 */
public class Main extends XposedModule {

    private static final String TAG = "ModKit";

    private static final String[][] TARGETS = {
            { "com.realtech.xiaocan", "小蚕惠生活 · 开屏及信息流去广告、弹窗拦截" },
            { "com.netease.cloudmusic", "网易云音乐 · 开屏广告/横幅与信息流净化" },
            { "com.luna.music", "汽水音乐 · 冷启动开屏广告拦截" },
            { "com.xiaomi.shop", "小米商城 · 开屏与促销广告净化" },
            { "com.yek.android.kfc.activitys", "肯德基 · 启动页与横幅广告净化" },
            { "com.mcdonalds.gma.cn", "麦当劳 · 启动开屏与横幅广告净化" },
            { "com.tencent.qqmusic", "QQ音乐 · 开屏/Feed/插屏及精简优化" },
            { "com.coolapk.market", "酷安 · 头条流/评论区/Sponsor广告净化" },
            { "com.baidu.BaiduMap", "百度地图 · 极速启动/开屏及营销广告去除" },
            { "com.xiaomi.market", "小米应用商店 · 启动开屏广告跳过" },
            { "com.glodon.drawingexplorer", "CAD快速看图 · 启动与插屏广告拦截" },
            { "com.xbxxhz.box", "小白学习打印 · 启动与通用SDK广告拦截" },
            { "tv.danmaku.bili", "哔哩哔哩 · 开屏与Landing页广告净化" },
            { "com.autonavi.minimap", "高德地图 · 开屏与推广弹窗拦截" },
            { "com.zhihu.android", "知乎 · 开屏与信息流回答卡片广告拦截" },
            { "com.dragon.read", "红果免费短剧 · 开屏与激励广告跳过" },
            { "com.baidu.netdisk", "百度网盘 · 启动页与营销弹窗净化" },
    };

    private static final Uri STATE_URI = Uri.parse("content://com.example.l.hookstate");
    private ContentResolver resolver;
    private long lastToggleCheck;
    private boolean cachedEnabled = true;
    private boolean toggleLogged;
    private String currentPkg;

    public Main() {
        super();
    }

    private ContentResolver resolver() {
        if (resolver != null) return resolver;
        try {
            Object app = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            if (app instanceof Context) {
                resolver = ((Context) app).getContentResolver();
            }
        } catch (Throwable ignored) {}
        return resolver;
    }

    private boolean hookEnabled() {
        long now = SystemClock.uptimeMillis();
        if (now - lastToggleCheck < 2000) return cachedEnabled;
        lastToggleCheck = now;
        try {
            ContentResolver r = resolver();
            if (r != null) {
                Bundle b = r.call(STATE_URI, "enabled", currentPkg, null);
                if (b != null && b.containsKey("v")) {
                    boolean e = b.getBoolean("v", true);
                    if (e != cachedEnabled || !toggleLogged) {
                        log(Log.INFO, TAG, "toggle(provider) = " + e);
                        toggleLogged = true;
                    }
                    cachedEnabled = e;
                    return e;
                }
            }
        } catch (Throwable ignored) {}
        try {
            SharedPreferences sp = getRemotePreferences("settings");
            if (sp != null && sp.contains("hook_" + currentPkg)) {
                cachedEnabled = sp.getBoolean("hook_" + currentPkg, true);
                return cachedEnabled;
            }
        } catch (Throwable ignored) {}
        cachedEnabled = true;
        return true;
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        String pkg = param.getPackageName();
        ClassLoader cl = param.getDefaultClassLoader();

        for (String[] t : TARGETS) {
            if (!t[0].equals(pkg)) continue;
            currentPkg = pkg;
            log(Log.INFO, TAG, "Loaded into " + pkg);

            if (!hookEnabled()) {
                log(Log.INFO, TAG, "Hook disabled by user for " + pkg);
                return;
            }

            if ("com.realtech.xiaocan".equals(pkg)) {
                try {
                    XiaoCanHook.install(this, cl);
                    hookLifecycleForShell(this, cl, pkg);
                } catch (Throwable e) {
                    log(Log.ERROR, TAG, "XiaoCan hook failed", e);
                }
            } else {
                try {
                    UniversalAdHook.install(this, cl, pkg);
                } catch (Throwable e) {
                    log(Log.ERROR, TAG, "UniversalAdHook failed for " + pkg, e);
                }
            }
            return;
        }
    }

    private void hookLifecycleForShell(final XposedInterface xposed, final ClassLoader initialLoader, final String pkg) {
        try {
            Method attach = Application.class.getDeclaredMethod("attach", Context.class);
            xposed.hook(attach).intercept(new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Object r = chain.proceed();
                    Object app = chain.getThisObject();
                    if (app instanceof Application) {
                        ClassLoader cl = ((Application) app).getClassLoader();
                        if (cl != null && cl != initialLoader) {
                            XiaoCanHook.install(xposed, cl);
                        }
                    }
                    return r;
                }
            });
        } catch (Throwable ignored) {}
    }
}
