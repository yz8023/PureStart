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

import java.lang.reflect.Method;

/**
 * PureStart (ModKit Edition) — 现代化架构 Xposed 模块入口
 * 全面整合 XiaoCan 净化、通用广告过滤与 DexFinder 自动特征识别
 *
 * v15.0.2 关键修正：
 *  - 开关查询改为「拦截回调触发时懒查询」（此前在 onPackageLoaded 过早调用，
 *    目标进程尚未 attach，Provider 必然拿不到 → 现在回调时应用已就绪）；
 *  - 细分子项开关（开屏/信息流/弹窗/DexFinder）真正接入 Hook 判定；
 *  - 通用引擎升级：启动器安全判定 + 全自动「跳过」点击 + 广告弹窗拦截。
 */
public class Main extends XposedModule implements HookGate {

    private static final String TAG = "ModKit";

    static final String[][] TARGETS = {
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
            { "com.baidu.netdisk", "百度网盘 · 启动页与营销广告净化" },
    };

    private static final Uri STATE_URI = Uri.parse("content://com.example.l.hookstate");
    private ContentResolver resolver;
    private String currentPkg;

    /** 细分子项 key → 偏好名（设置页 AppDetailDialog 写入的同名键） */
    private static final String[][] SUB_KEYS = {
            { "enabled",   "hook_" },
            { "splash",    "sub_splash_" },
            { "feed",      "sub_feed_" },
            { "popup",     "sub_popup_" },
            { "dexfinder", "sub_dexfinder_" },
    };

    /** 闸门缓存（2s；回调触发时查询，此刻目标应用已 attach，Provider 可达） */
    private final Bundle cfgCache = new Bundle();
    private long cfgAt = -2000;
    private String cfgPkg;
    private boolean cfgLogged;

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

    @Override
    public boolean on(String key) {
        String pkg = currentPkg;
        long now = SystemClock.uptimeMillis();
        if (pkg == null || !pkg.equals(cfgPkg) || now - cfgAt > 2000) {
            cfgAt = now;
            cfgPkg = pkg;
            boolean got = false;
            // ① 首选：跨进程 Provider（设置页实时勾选状态）
            try {
                ContentResolver r = resolver();
                if (r != null && pkg != null) {
                    Bundle b = r.call(STATE_URI, "config", pkg, null);
                    if (b != null && b.containsKey("enabled")) {
                        cfgCache.clear();
                        cfgCache.putAll(b);
                        got = true;
                    }
                }
            } catch (Throwable ignored) {}
            // ② 兜底：框架远程偏好
            if (!got) {
                try {
                    SharedPreferences sp = getRemotePreferences("settings");
                    if (sp != null) {
                        cfgCache.clear();
                        for (String[] sk : SUB_KEYS) {
                            cfgCache.putBoolean(sk[0], sp.getBoolean(sk[1] + pkg, true));
                        }
                        got = true;
                    }
                } catch (Throwable ignored) {}
            }
            if (!got) cfgCache.clear();   // 双通道都失败 → 全默认 true（拦截）
            if (!cfgLogged) {
                log(Log.INFO, TAG, "gate(" + key + ") provider=" + got
                        + " enabled=" + cfgCache.getBoolean("enabled", true));
                cfgLogged = true;
            }
        }
        return cfgCache.getBoolean(key, true);
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        String pkg = param.getPackageName();
        ClassLoader cl = param.getDefaultClassLoader();

        for (String[] t : TARGETS) {
            if (!t[0].equals(pkg)) continue;
            currentPkg = pkg;
            log(Log.INFO, TAG, "Loaded into " + pkg + " (module v15.0.6, API 102)");

            if ("com.realtech.xiaocan".equals(pkg)) {
                // 小蚕为加固壳应用：先尝试直接安装；类未解密则挂三个生命周期点延迟重试
                try {
                    if (XiaoCanHook.canLoadTargetClasses(cl)) {
                        XiaoCanHook.install(this, cl, this);
                    } else {
                        log(Log.INFO, TAG, "XiaoCan shelled, deferring install to lifecycle");
                    }
                    XiaoCanHook.hookLifecycleForDeferredInstall(this);
                } catch (Throwable e) {
                    log(Log.ERROR, TAG, "XiaoCan hook failed", e);
                }
            } else {
                try {
                    UniversalAdHook.install(this, cl, pkg, this);
                } catch (Throwable e) {
                    log(Log.ERROR, TAG, "UniversalAdHook failed for " + pkg, e);
                }
            }
            return;
        }
    }

    /**
     * 原模块同款时机：包就绪后再尝试一次（壳往往在此时已解密出业务类）。
     * XiaoCanHook 内部 AtomicBoolean 保证幂等，重复调用安全。
     */
    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        String pkg = param.getPackageName();
        if (!"com.realtech.xiaocan".equals(pkg)) return;
        currentPkg = pkg;
        ClassLoader cl = param.getClassLoader();
        log(Log.INFO, TAG, "Package ready: " + pkg);
        try {
            if (XiaoCanHook.canLoadTargetClasses(cl)) {
                XiaoCanHook.install(this, cl, this);
            } else {
                XiaoCanHook.hookLifecycleForDeferredInstall(this);
            }
        } catch (Throwable e) {
            log(Log.ERROR, TAG, "XiaoCan ready-install failed", e);
        }
    }
}
