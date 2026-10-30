package com.example.l;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;

/**
 * 小蚕惠生活 (com.realtech.xiaocan) 专属净化 Hook
 * 完整整合自 XiaoCanPurify 核心业务逻辑
 */
public final class XiaoCanHook {
    private static final String TAG = "ModKit-XiaoCan";

    private XiaoCanHook() {}

    public static void install(XposedInterface xposed, ClassLoader cl) {
        if (xposed == null || cl == null) return;
        hookSplashAdFactory(xposed, cl);
        hookFlutterAdPlugins(xposed, cl);
        hookPopupDialogs(xposed, cl);
        hookAdActivities(xposed);
    }

    private static void hookSplashAdFactory(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> factoryClass = DexFinder.safeLoad(cl, "com.realtech.xiaocan.ad.splash.SplashAdSourceFactory");
            if (factoryClass == null) return;
            for (Method m : factoryClass.getDeclaredMethods()) {
                if ("create".equals(m.getName())) {
                    xposed.hook(m).intercept(new XposedInterface.Hooker() {
                        @Override
                        public Object intercept(XposedInterface.Chain chain) throws Throwable {
                            List<Object> args = chain.getArgs();
                            if (args != null && args.size() > 2) {
                                Object listener = args.get(2);
                                if (listener != null) {
                                    try {
                                        Method onError = listener.getClass().getMethod("onAdError", int.class, String.class);
                                        onError.invoke(listener, 1001, "Blocked by ModKit");
                                    } catch (Throwable ignored) {}
                                }
                            }
                            return null;
                        }
                    });
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "hookSplashAdFactory error: " + t);
        }
    }

    private static void hookFlutterAdPlugins(XposedInterface xposed, ClassLoader cl) {
        String[] handlers = {
                "com.windmill.windmill_ad_plugin.WindmillAdPluginDelegate",
                "com.windmill.windmill_ad_plugin.interstitial.InterstitialAd",
                "com.windmill.windmill_ad_plugin.reward.RewardVideoAd",
                "com.windmill.windmill_ad_plugin.splashAd.SplashAd",
                "com.windmill.windmill_ad_plugin.banner.BannerAd",
                "com.windmill.windmill_ad_plugin.feedAd.NativeAd",
                "com.realtech.xiaocan.flutter.NativeAdBridge"
        };
        for (String name : handlers) {
            try {
                Class<?> handler = DexFinder.safeLoad(cl, name);
                Class<?> callType = DexFinder.safeLoad(cl, "io.flutter.plugin.common.MethodCall");
                Class<?> resultType = DexFinder.safeLoad(cl, "io.flutter.plugin.common.MethodChannel$Result");
                if (handler == null || callType == null || resultType == null) continue;

                final Field methodField = callType.getField("method");
                final Method success = resultType.getMethod("success", Object.class);
                final Method error = resultType.getMethod("error", String.class, String.class, Object.class);
                String entry = name.endsWith("NativeAdBridge") ? "createBridge$lambda$3" : "onMethodCall";

                Method target = null;
                try {
                    target = handler.getDeclaredMethod(entry, callType, resultType);
                } catch (Throwable e) {
                    target = DexFinder.findMethodByFeature(handler, void.class, new Class<?>[]{callType, resultType}, 0);
                }
                if (target == null) continue;

                xposed.hook(target).intercept(new XposedInterface.Hooker() {
                    @Override
                    public Object intercept(XposedInterface.Chain chain) throws Throwable {
                        List<Object> args = chain.getArgs();
                        if (args == null || args.size() < 2) return chain.proceed();
                        String method = (String) methodField.get(args.get(0));
                        Object reply = args.get(1);
                        if (method != null && (method.contains("ad") || method.contains("Ad") || method.contains("dyds"))) {
                            if ("isReady".equals(method) || "show_dyds".equals(method)) {
                                success.invoke(reply, Boolean.FALSE);
                            } else if ("getCacheAdInfoList".equals(method)) {
                                success.invoke(reply, Collections.emptyList());
                            } else if ("load".equals(method) || "showAd".equals(method)) {
                                error.invoke(reply, "AD_BLOCKED", "Ad unavailable", null);
                            } else {
                                success.invoke(reply, (Object) null);
                            }
                            return null;
                        }
                        return chain.proceed();
                    }
                });
            } catch (Throwable t) {
                Log.w(TAG, "hookFlutterAdPlugins: " + name + " -> " + t);
            }
        }
    }

    private static void hookPopupDialogs(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> basePopup = DexFinder.safeLoad(cl, "com.realtech.promotion.dialog.BasePopup");
            if (basePopup != null) {
                Method showMethod = DexFinder.findMethodByFeature(basePopup, void.class, new Class<?>[]{}, 0);
                if (showMethod != null) {
                    xposed.hook(showMethod).intercept(new XposedInterface.Hooker() {
                        @Override
                        public Object intercept(XposedInterface.Chain chain) throws Throwable {
                            return null;
                        }
                    });
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void hookAdActivities(XposedInterface xposed) {
        try {
            Method m = Activity.class.getDeclaredMethod("onCreate", Bundle.class);
            xposed.hook(m).intercept(new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    Object thisObj = chain.getThisObject();
                    if (thisObj instanceof Activity) {
                        String name = thisObj.getClass().getName();
                        if (name.contains("windmill") || name.contains(".ad.") || name.contains("RewardVideo") || name.contains("SplashAd")) {
                            ((Activity) thisObj).finish();
                            return null;
                        }
                    }
                    return chain.proceed();
                }
            });
        } catch (Throwable ignored) {}
    }
}
