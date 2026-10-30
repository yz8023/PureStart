package com.yz8023.purestart;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * PureStart (ModKit Edition) — 现代化架构 Xposed 模块入口
 * 全面整合 XiaoCan 净化、通用广告过滤与 DexFinder 自动特征识别
 *
 * 工程修正（按外部分析报告 P0/P1，编号见 git 历史）：
 *  - 开关闸门 fail-safe：双通道读取失败时保留上次已知值（last-known），
 *    完全无缓存时 fail-closed（返回 false）并输出 ERROR 日志；
 *    降级状态下缩短重试间隔（400ms），瞬时故障可自愈；
 *  - cfgCache 读写全程同步（Hook 可能运行在多线程）；
 *  - 通道状态变化即记录日志（不再只记一次）；
 *  - Hook 自检：安装结果经 Provider 回传设置页（selfcheck 通道）；
 *  - 版本号唯一来源 = AndroidManifest.xml；代码内版本文案运行时从 PackageManager 取。
 */
public class Main extends XposedModule implements HookGate {

    private static final String TAG = "ModKit";

    /** 目标清单唯一来源在 Targets.java（纯数据类，UI 进程也可安全加载） */
    static final String[][] TARGETS = Targets.LIST;

    /** 模块包名（module id / Provider authority 同源；改包名时此处与 Manifest 同改，check.py 把关） */
    static final String MODULE_PACKAGE = "com.yz8023.purestart";
    private static final String STATE_AUTHORITY = MODULE_PACKAGE + ".hookstate";
    private static final Uri STATE_URI = Uri.parse("content://" + STATE_AUTHORITY);
    private static final Uri SELFCHECK_URI = Uri.parse("content://" + STATE_AUTHORITY + "/selfcheck");
    private ContentResolver resolver;
    private String currentPkg;
    private static volatile String cachedModuleVersion;

    /** P0-2：版本文案运行时从 PackageManager 取（模块 forceQueryable，目标进程可查），不硬编码 */
    static String moduleVersion() {
        String v = cachedModuleVersion;
        if (v != null) return v;
        try {
            Object app = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            if (app instanceof Context) {
                v = ((Context) app).getPackageManager()
                        .getPackageInfo(MODULE_PACKAGE, 0).versionName;
            }
        } catch (Throwable t) {
            Log.w(TAG, "moduleVersion unavailable: " + t);
        }
        if (v == null) v = "unknown";
        cachedModuleVersion = v;
        return v;
    }

    /** 细分子项 key → 偏好名前缀（键名唯一出处 Prefs.java） */
    private static final String[][] SUB_KEYS = {
            { "enabled",   Prefs.PREFIX_HOOK },
            { "splash",    Prefs.PREFIX_SUB_SPLASH },
            { "feed",      Prefs.PREFIX_SUB_FEED },
            { "popup",     Prefs.PREFIX_SUB_POPUP },
            { "dexfinder", Prefs.PREFIX_SUB_DEXFINDER },
    };

    /**
     * 闸门缓存 = 上次成功读到的配置（P0-1 语义：lastGood）。
     * 双通道失败时不清空、直接沿用；从未成功读过任何配置 → 一律 false（fail-closed）。
     * 正常 2s TTL；降级态 400ms 加速自愈。读写均持 cfgCache 锁（多线程 Hook 安全）。
     */
    private final Bundle cfgCache = new Bundle();
    private long cfgAt = -2000;
    private String cfgPkg;
    private boolean cfgEverLoaded;      // 是否成功加载过（决定降级语义）
    private String cfgChannel = "none"; // provider | remote | last-known | unknown

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
        } catch (Throwable t) {
            Log.w(TAG, "resolver unavailable: " + t);
        }
        return resolver;
    }

    @Override
    public boolean on(String key) {
        String pkg = currentPkg;
        long ttl = cfgEverLoaded ? 2000 : 400;   // 降级态短 TTL：瞬时 Provider 故障自愈
        long now = SystemClock.uptimeMillis();
        if (pkg == null || !pkg.equals(cfgPkg) || now - cfgAt > ttl) {
            cfgAt = now;
            cfgPkg = pkg;
            boolean got = false;
            String channel = "unknown";
            // ① 首选：跨进程 Provider（设置页实时勾选状态）
            try {
                ContentResolver r = resolver();
                if (r != null && pkg != null) {
                    Bundle b = r.call(STATE_URI, "config", pkg, null);
                    if (b != null && b.containsKey("enabled")) {
                        synchronized (cfgCache) {
                            cfgCache.clear();
                            cfgCache.putAll(b);
                        }
                        got = true;
                        channel = "provider";
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "gate provider channel failed: " + t);
            }
            // ② 兜底：框架远程偏好
            if (!got) {
                try {
                    SharedPreferences sp = getRemotePreferences(Prefs.FILE);
                    if (sp != null) {
                        synchronized (cfgCache) {
                            cfgCache.clear();
                            for (String[] sk : SUB_KEYS) {
                                // P0-1：远程兜底同样默认关（未设置 = 不动作）
                                cfgCache.putBoolean(sk[0], sp.getBoolean(sk[1] + pkg, false));
                            }
                        }
                        got = true;
                        channel = "remote";
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "gate remote-prefs channel failed: " + t);
                }
            }
            // ③ 双通道失败：保留上次已知值（last-known），绝不静默回退全开
            if (!got) {
                synchronized (cfgCache) {
                    if (!cfgCache.isEmpty()) {
                        channel = "last-known";
                    }
                }
                if ("unknown".equals(channel)) {
                    log(Log.ERROR, TAG, "gate: no config source and no last-known value for "
                            + pkg + " — failing CLOSED (hooks disabled) for safety");
                }
            }
            cfgEverLoaded |= got;
            if (!channel.equals(cfgChannel)) {
                log(Log.INFO, TAG, "gate(" + key + ") channel=" + channel
                        + " enabled=" + readGate("enabled", false));
                cfgChannel = channel;
            }
        }
        // P0-1 fail-closed：从未成功读到过配置（无 lastGood）→ 一律 false（含全部子项）
        if (!cfgEverLoaded) return false;
        return readGate(key, false);
    }

    private boolean readGate(String key, boolean def) {
        synchronized (cfgCache) {
            return cfgCache.getBoolean(key, def);
        }
    }

    // ==================== Hook 自检（安装结果回传设置页） ====================

    /** 目标进程内的安装结果收集器（每 App 一份，经 Provider 回传） */
    public interface Reporter {
        void ok(String what);
        void miss(String what);                       // 可选目标不存在（预期内）
        void fail(String what, Throwable t);          // 安装失败（必须留痕）
        void republish();                             // 延迟安装完成后重报
    }

    private static class InstallReport implements Reporter {
        private final String pkg;
        private final ContentResolver resolver;
        private int ok, miss;
        private final StringBuilder fails = new StringBuilder();

        InstallReport(String pkg, ContentResolver resolver) {
            this.pkg = pkg;
            this.resolver = resolver;
        }

        @Override public void ok(String what) { ok++; }

        @Override public void miss(String what) {
            miss++;
            Log.i(TAG, "[" + pkg + "] optional target absent: " + what);
        }

        @Override public void fail(String what, Throwable t) {
            fails.append(what).append(" (").append(t.getClass().getSimpleName()).append("); ");
            Log.e(TAG, "[" + pkg + "] hook install FAILED: " + what + " — " + t);
        }

        @Override public void republish() {
            try {
                ContentValues cv = new ContentValues();
                cv.put("pkg", pkg);
                cv.put("ok", ok);
                cv.put("miss", miss);
                cv.put("fails", fails.length() > 300 ? fails.substring(0, 300) : fails.toString());
                cv.put("version", moduleVersion());
                cv.put("at", System.currentTimeMillis());
                if (resolver != null) {
                    resolver.insert(SELFCHECK_URI, cv);
                }
            } catch (Throwable t) {
                Log.w(TAG, "selfcheck report dropped: " + t);
            }
        }
    }

    private InstallReport report;

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        String pkg = param.getPackageName();
        ClassLoader cl = param.getDefaultClassLoader();

        for (String[] t : TARGETS) {
            if (!t[0].equals(pkg)) continue;
            currentPkg = pkg;
            report = new InstallReport(pkg, resolver());
            log(Log.INFO, TAG, "Loaded into " + pkg + " (module v" + moduleVersion()
                    + ", API 102)");

            if ("com.realtech.xiaocan".equals(pkg)) {
                // 小蚕为加固壳应用：先挂三个生命周期重试点（内部设 reporter，主类未命中有留痕），
                // 再尝试直接安装；类未解密则由生命周期点在壳解密后补装
                try {
                    XiaoCanHook.hookLifecycleForDeferredInstall(this, report);
                    if (XiaoCanHook.canLoadTargetClasses(cl)) {
                        XiaoCanHook.install(this, cl, this, report);
                    } else {
                        log(Log.INFO, TAG, "XiaoCan shelled, deferring install to lifecycle");
                    }
                } catch (Throwable e) {
                    report.fail("XiaoCanHook.install", e);
                    log(Log.ERROR, TAG, "XiaoCan hook failed", e);
                }
            } else {
                try {
                    UniversalAdHook.install(this, cl, pkg, this, report);
                } catch (Throwable e) {
                    report.fail("UniversalAdHook.install", e);
                    log(Log.ERROR, TAG, "UniversalAdHook failed for " + pkg, e);
                }
            }
            report.republish();
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
        if (report == null) report = new InstallReport(pkg, resolver());
        try {
            if (XiaoCanHook.canLoadTargetClasses(cl)) {
                XiaoCanHook.install(this, cl, this, report);
            } else {
                XiaoCanHook.hookLifecycleForDeferredInstall(this, report);
            }
        } catch (Throwable e) {
            report.fail("XiaoCanHook.ready-install", e);
            log(Log.ERROR, TAG, "XiaoCan ready-install failed", e);
        }
    }
}
