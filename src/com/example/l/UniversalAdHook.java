package com.example.l;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * UniversalAdHook: 融合 PureStart 原有 Generic 广告拦截架构，
 * 适用于哔哩哔哩、知乎、红果短剧、百度网盘、酷安等所有遵循标准广告 SDK 前缀的应用。
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

    public static void install(XposedInterface xposed, ClassLoader cl, final String pkg) {
        if (xposed == null) return;
        try {
            Method actCreate = Activity.class.getDeclaredMethod("onCreate", Bundle.class);
            xposed.hook(actCreate).intercept(new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Object obj = chain.getThisObject();
                    if (obj instanceof Activity) {
                        Activity act = (Activity) obj;
                        String name = act.getClass().getName();
                        if (isAdActivity(name)) {
                            Log.i(TAG, "Ad Activity finished: " + name + " in " + pkg);
                            act.finish();
                            return null;
                        }
                    }
                    return chain.proceed();
                }
            });

            Method addViewMethod = ViewGroup.class.getDeclaredMethod("addView", View.class, int.class);
            xposed.hook(addViewMethod).intercept(new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Object[] args = chain.getArgs().toArray();
                    if (args.length > 0 && args[0] instanceof View) {
                        View child = (View) args[0];
                        String name = child.getClass().getName();
                        if (isAdView(name)) {
                            child.setVisibility(View.GONE);
                            return null;
                        }
                    }
                    return chain.proceed();
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "UniversalAdHook install error in " + pkg + ": " + t);
        }
    }

    private static boolean isSafe(String name) {
        for (String safe : SAFE_PREFIXES) {
            if (name.startsWith(safe)) return true;
        }
        return false;
    }

    private static boolean isAdActivity(String name) {
        if (isSafe(name)) return false;
        for (String prefix : COMMON_AD_PREFIXES) {
            if (name.startsWith(prefix)) return true;
        }
        String lower = name.toLowerCase();
        return lower.contains("splashactivity") || lower.contains("adactivity") || lower.contains("rewardvideo");
    }

    private static boolean isAdView(String name) {
        if (isSafe(name)) return false;
        for (String prefix : COMMON_AD_PREFIXES) {
            if (name.startsWith(prefix)) return true;
        }
        String lower = name.toLowerCase();
        return lower.contains("adview") || lower.contains("splashad") || lower.contains("nativead") || lower.contains("feedad");
    }
}
