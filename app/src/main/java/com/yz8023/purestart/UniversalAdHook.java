package com.yz8023.purestart;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * UniversalAdHook: 通用去广告引擎（强化版）
 *
 * 三层拦截，全部经 HookGate 按细分子项开关判定：
 *  ① Activity.onCreate —— 广告 SDK 页面直接关闭（绝不误关启动器）；
 *     启动器页面 → 自动扫描并点击「跳过」按钮（通用开屏跳过），
 *     并用 DexFinder 特征在开屏容器上定位 skip/jump 方法直接触发。
 *  ② ViewGroup.addView —— 广告 SDK / 信息流广告视图加入即隐藏（feed 开关）。
 *  ③ Dialog.show —— 广告 SDK 弹窗不再弹出（popup 开关）。
 */
public final class UniversalAdHook {
    private static final String TAG = "ModKit-Universal";

    private static final String[] COMMON_AD_PREFIXES = {
            "com.anythink.", "com.smartdigimkt.", "com.beizi.", "com.meishu.", "com.baidu.mobads.",
            "com.kwad.", "com.qq.e.", "com.wangmai.", "com.sigmob.", "com.octopus.",
            "com.huawei.openalliance.ad.", "com.youth.", "com.adprof.", "com.alliance.ssp.",
            "com.opos.mobad.", "com.ubix.", "com.ubixnow.", "com.mbridge.", "com.google.android.gms.ads.",
            "com.tuia.", "cj.mobile.ad.", "com.bilibili.ad.", "tv.danmaku.bili.splash.ad."
    };

    private static final String[] SAFE_PREFIXES = {
            "com.alipay.sdk.", "com.tencent.tauth.", "com.tencent.connect.", "com.baidu.oauth."
    };

    private UniversalAdHook() {}

    public static void install(XposedInterface xposed, ClassLoader cl, final String pkg,
                               final HookGate gate, final Main.Reporter rep) {
        if (xposed == null) return;
        final Main.Reporter reporter = rep != null ? rep : new Main.Reporter() {
            @Override public void ok(String what) {}
            @Override public void miss(String what) {}
            @Override public void fail(String what, Throwable t) {}
            @Override public void republish() {}
        };
        try {
            Method actCreate = Activity.class.getDeclaredMethod("onCreate", Bundle.class);
            xposed.hook(actCreate).intercept(new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Object obj = chain.getThisObject();
                    if (obj instanceof Activity) {
                        Activity act = (Activity) obj;
                        if (gate.on("enabled")) {
                            String name = act.getClass().getName();
                            boolean launcher = isLauncherActivity(act);
                            // 广告 SDK 页面：非启动器才关（启动器绝不 finish，防止“点开就退出”）
                            if (isAdSdkActivity(name) && !launcher) {
                                Log.i(TAG, "Ad activity finished: " + name + " in " + pkg);
                                act.finish();
                                return null;
                            }
                            // 非启动器的启发式 splash/ad 页同样关闭
                            if (!launcher && gate.on("splash") && isHeuristicAdActivity(name)) {
                                Log.i(TAG, "Heuristic ad activity finished: " + name + " in " + pkg);
                                act.finish();
                                return null;
                            }
                            // 启动器：通用开屏跳过（自动点「跳过」+ DexFinder 方法触发）
                            if (launcher && gate.on("splash")) {
                                scheduleSkipScan(act, gate);
                            }
                        }
                    }
                    return chain.proceed();
                }
            });
            reporter.ok("Activity.onCreate@" + pkg);
        } catch (Throwable t) {
            reporter.fail("Activity.onCreate@" + pkg, t);
        }

        try {
            Method addViewMethod = ViewGroup.class.getDeclaredMethod("addView", View.class, int.class);
            xposed.hook(addViewMethod).intercept(new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    // addView 是所有界面都会走的热路径：开关判定按 500ms 节流缓存，
                    // 避免每个 View 都触发一次跨进程配置查询（原实现每 View 查两次）。
                    if (adViewBlocking(gate)) {
                        java.util.List<Object> args = chain.getArgs();
                        if (args != null && !args.isEmpty() && args.get(0) instanceof View) {
                            View child = (View) args.get(0);
                            if (isAdView(child.getClass().getName())) {
                                Log.i(TAG, "Ad view hidden: " + child.getClass().getName() + " in " + pkg);
                                child.setVisibility(View.GONE);
                                return null;
                            }
                        }
                    }
                    return chain.proceed();
                }
            });
            reporter.ok("addView@" + pkg);
        } catch (Throwable t) {
            reporter.fail("addView@" + pkg, t);
        }

        try {
            Method showMethod = Dialog.class.getDeclaredMethod("show");
            xposed.hook(showMethod).intercept(new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    if (gate.on("enabled") && gate.on("popup")) {
                        Object obj = chain.getThisObject();
                        if (obj instanceof Dialog) {
                            Dialog d = (Dialog) obj;
                            String name = obj.getClass().getName();
                            if (isAdSdkActivity(name) || isHeuristicAdDialog(name)) {
                                Log.i(TAG, "Ad dialog blocked: " + name + " in " + pkg);
                                return null;   // 不 show，也不 dismiss（避免崩溃）
                            }
                        }
                    }
                    return chain.proceed();
                }
            });
            reporter.ok("Dialog.show@" + pkg);
        } catch (Throwable t) {
            reporter.fail("Dialog.show@" + pkg, t);
        }
    }

    /** addView 热路径闸门：开关结果缓存 500ms（配置查询本身已有 2s 缓存）。 */
    private static long lastGateCheck;
    private static boolean lastGateOpen;

    private static boolean adViewBlocking(HookGate gate) {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastGateCheck >= 500L) {
            lastGateCheck = now;
            lastGateOpen = gate.on("enabled") && gate.on("feed");
        }
        return lastGateOpen;
    }

    // ------------------------------------------------------------------
    // 启动器判定（绝不 finish 启动器 —— 修复“点开就退出”）
    // ------------------------------------------------------------------

    private static boolean isLauncherActivity(Activity act) {
        try {
            Intent i = act.getIntent();
            if (i != null && Intent.ACTION_MAIN.equals(i.getAction())
                    && i.hasCategory(Intent.CATEGORY_LAUNCHER)) {
                return true;
            }
        } catch (Throwable ignored) {}   // 纯防御：取 Intent 元数据失败按非启动器处理（宁可漏关广告页，不误关启动器）
        return false;
    }

    // ------------------------------------------------------------------
    // 通用开屏跳过：多轮扫描视图树，自动点击「跳过」
    // ------------------------------------------------------------------

    private static void scheduleSkipScan(final Activity act, final HookGate gate) {
        int[] delays = {350, 800, 1300, 1900};
        for (final int delay : delays) {
            act.getWindow().getDecorView().postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (act.isFinishing() || act.isDestroyed() || !gate.on("enabled") || !gate.on("splash")) return;
                        View root = act.getWindow().getDecorView();
                        View hit = findSkipView(root);
                        if (hit != null) {
                            Log.i(TAG, "Splash skip clicked in " + act.getClass().getName());
                            hit.performClick();
                            return;   // 点到即止
                        }
                        // ② 按钮没点到（可能倒计时未结束/不可点）：DexFinder 特征兜底
                        if (gate.on("dexfinder")) tryDexFinderSkip(root);
                    } catch (Throwable ignored) {}   // 纯防御：跳过扫描尽力而为，失败仅多显示一帧开屏
                }
            }, delay);
        }
    }

    /** BFS 扫描（限量 900 个视图防大树卡顿）：可点击 + 文本/描述含「跳过」或 id 含 skip/jump */
    private static View findSkipView(View root) {
        if (root == null) return null;
        ArrayDeque<View> queue = new ArrayDeque<>();
        Set<View> seen = Collections.newSetFromMap(new IdentityHashMap<View, Boolean>());
        queue.add(root);
        int visited = 0;
        while (!queue.isEmpty() && visited < 900) {
            View v = queue.poll();
            if (v == null || !seen.add(v)) continue;
            visited++;
            if (v.isClickable() && matchesSkipToken(v)) return v;
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = g.getChildCount() - 1; i >= 0; i--) queue.add(g.getChildAt(i));
            }
        }
        return null;
    }

    private static boolean matchesSkipToken(View v) {
        CharSequence cs = null;
        if (v instanceof TextView) cs = ((TextView) v).getText();
        if (cs == null) cs = v.getContentDescription();
        if (cs != null) {
            String t = cs.toString().trim();
            if (t.contains("跳过") || t.equalsIgnoreCase("skip")) return true;
        }
        try {
            if (v.getId() != View.NO_ID) {
                String id = v.getResources().getResourceEntryName(v.getId());
                if (id != null) {
                    String l = id.toLowerCase();
                    if (l.contains("skip") || l.contains("jump")) return true;
                }
            }
        } catch (Throwable ignored) {}   // 纯防御：资源名解析失败按非跳过按钮处理（跳过判定宁缺勿滥）
        return false;
    }

    /** DexFinder 引擎：在开屏容器/根视图类上按特征（public void 无参）定位 skip/jump/close 方法并直接调用 */
    private static void tryDexFinderSkip(View root) {
        try {
            Class<?> c = root.getClass();
            // 从视图树根向上找到开屏容器（含 Splash 命名或广告 SDK 前缀的类）
            Object target = null;
            ArrayDeque<View> queue = new ArrayDeque<>();
            queue.add(root);
            int visited = 0;
            while (!queue.isEmpty() && visited < 200) {
                View v = queue.poll();
                if (v == null) continue;
                visited++;
                String n = v.getClass().getName();
                if (n.contains("Splash") || isAdSdkActivity(n)) { target = v; break; }
                if (v instanceof ViewGroup) {
                    ViewGroup g = (ViewGroup) v;
                    for (int i = 0; i < g.getChildCount(); i++) queue.add(g.getChildAt(i));
                }
            }
            if (target == null) return;
            Class<?> tc = target.getClass();
            for (Method m : tc.getDeclaredMethods()) {
                String mn = m.getName().toLowerCase();
                if ((mn.contains("skip") || mn.contains("jump"))
                        && m.getParameterTypes().length == 0
                        && (m.getModifiers() & Modifier.PUBLIC) != 0) {
                    m.setAccessible(true);
                    m.invoke(target);
                    Log.i(TAG, "DexFinder skip invoked: " + tc.getName() + "." + m.getName());
                    return;
                }
            }
        } catch (Throwable ignored) {}   // 纯防御：反射触发 skip 尽力而为，失败退回普通点击路径
    }

    // ------------------------------------------------------------------
    // 名称判定
    // ------------------------------------------------------------------

    private static boolean isSafe(String name) {
        for (String safe : SAFE_PREFIXES) {
            if (name.startsWith(safe)) return true;
        }
        return false;
    }

    /** 广告 SDK 包前缀（确定是第三方广告页，与业务启发式分开） */
    private static boolean isAdSdkActivity(String name) {
        if (isSafe(name)) return false;
        for (String prefix : COMMON_AD_PREFIXES) {
            if (name.startsWith(prefix)) return true;
        }
        return false;
    }

    /** 业务启发式：splash/ad 命名（仅用于非启动器页面与弹窗） */
    private static boolean isHeuristicAdActivity(String name) {
        if (isSafe(name)) return false;
        String lower = name.toLowerCase();
        return lower.contains("splashactivity") || lower.contains("adactivity")
                || lower.contains("rewardvideo") || lower.contains("landingpage");
    }

    private static boolean isHeuristicAdDialog(String name) {
        if (isSafe(name)) return false;
        String lower = name.toLowerCase();
        return lower.contains("addialog") || lower.contains("adpopup")
                || lower.contains("promotiondialog") || lower.contains("popupad");
    }

    private static boolean isAdView(String name) {
        if (isSafe(name)) return false;
        for (String prefix : COMMON_AD_PREFIXES) {
            if (name.startsWith(prefix)) return true;
        }
        String lower = name.toLowerCase();
        return lower.contains("adview") || lower.contains("splashad")
                || lower.contains("nativead") || lower.contains("feedad")
                || lower.contains("bannerad");
    }
}
