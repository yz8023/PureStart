package com.yz8023.purestart;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;

/**
 * XiaoCanHook —— 忠实移植 GeniusQA/XiaoCan（小蚕净化）的 AdBlocker + PopupBlocker 核心。
 *
 * 移植修复（此前失效的根因，见 git 历史）：
 *  1. 小蚕是加固壳应用（V库/类聚仁盾），onPackageLoaded 时业务类尚未解密加载 ——
 *     现按原模块的三段式时机：onPackageReady 尝试 → Application.attachBaseContext /
 *     Application.onCreate / Activity.onCreate 三个生命周期点重试，canLoadTargetClasses
 *     通过后一次性安装（AtomicBoolean 防重复）；
 *  2. SplashAdSourceFactory.create 按原版返回动态代理（load → 立即回调 onAdClosed，
 *     isAdReady → false），而非此前错误的 onAdError(int,String) 签名 + 返回 null；
 *  3. 补齐原版全部广告点：SplashAdUtils.initAd / SplashAdPlacementManager.refreshAsync /
 *     SilentTaskEngine / 信息流与横幅 Holder / FloatingAdManager / 搜索右侧横幅 /
 *     Flutter 广告插件全套（AmSdk + Windmill） / WelComeActivity 秒进主页 /
 *     广告 Activity 秒关清单 / 营销弹窗（文案扫描 + 指定弹窗类 + DialogUtils /
 *     AppUpdateServiceImpl / MainViewModel / Kuikly 弹窗队列）。
 */
public final class XiaoCanHook {
    private static final String TAG = "ModKit-XiaoCan";
    private static final String MAIN_CLASS = "com.realtech.xiaocan.MainActivity";

    private static final AtomicBoolean HOOKED = new AtomicBoolean(false);
    private static final AtomicBoolean LIFECYCLE_HOOKED = new AtomicBoolean(false);
    private static volatile HookGate gate;
    private static Main.Reporter reporter;   // 安装结果回传（自检页）；无则仅日志

    /** 可选目标缺失（预期内，如目标 App 版本变化）——留痕不静默 */
    private static void repOptional(Throwable t) {
        if (reporter != null) reporter.miss(String.valueOf(t));
        Log.i(TAG, "optional target absent: " + t);
    }

    /** 安装失败——必须留痕（类名 + 异常，ERROR 级） */
    private static void repFail(String what, Throwable t) {
        if (reporter != null) reporter.fail(what, t);
        Log.e(TAG, "hook install FAILED: " + what + " — " + t);
    }

    private static void repOk(String what) {
        if (reporter != null) reporter.ok(what);
    }

    private XiaoCanHook() {}

    /** 主类未命中每进程只留痕一次（生命周期点会反复探测，避免刷屏） */
    private static final AtomicBoolean MAIN_MISS_NOTED = new AtomicBoolean(false);

    /** 主类未命中：P1-3 安装路径不许静默——ERROR 日志 + 自检「未命中」+ 立即回传 */
    private static void noteMainMiss(Throwable cause) {
        if (MAIN_MISS_NOTED.compareAndSet(false, true)) {
            Log.e(TAG, "主类未命中: " + MAIN_CLASS + "（壳未解密或版本变更/类名失配）— " + cause);
            if (reporter != null) {
                reporter.miss("主类:" + MAIN_CLASS);
                reporter.republish();
            }
        }
    }

    /** 壳解密检测：宿主业务类是否已可加载（原模块同款判据） */
    public static boolean canLoadTargetClasses(ClassLoader cl) {
        if (cl == null) return false;
        try {
            Class.forName(MAIN_CLASS, false, cl);
            return true;
        } catch (Throwable e) {
            noteMainMiss(e);
            return false;
        }
    }

    /** 延迟安装：三个生命周期点重试（壳解密后类可见时一次性安装） */
    public static void hookLifecycleForDeferredInstall(XposedInterface xposed, Main.Reporter rep) {
        if (rep != null) reporter = rep;
        if (!LIFECYCLE_HOOKED.compareAndSet(false, true)) return;
        try {
            Method attachBaseContext = Application.class.getDeclaredMethod("attachBaseContext", Context.class);
            xposed.hook(attachBaseContext).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    Context context = (Context) chain.getArgs().get(0);
                    if (context != null) tryInstall(xposed, context.getClassLoader());
                } catch (Throwable t) { repOptional(t); }
                return result;
            });
        } catch (Throwable t) {
            Log.e(TAG, "hook attachBaseContext failed: " + t);
        }
        try {
            Method onCreate = Application.class.getDeclaredMethod("onCreate");
            xposed.hook(onCreate).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    Application app = (Application) chain.getThisObject();
                    if (app != null) tryInstall(xposed, app.getClassLoader());
                } catch (Throwable t) { repOptional(t); }
                return result;
            });
        } catch (Throwable t) {
            Log.e(TAG, "hook Application.onCreate failed: " + t);
        }
        try {
            Method actOnCreate = Activity.class.getDeclaredMethod("onCreate", Bundle.class);
            xposed.hook(actOnCreate).intercept(chain -> {
                try {
                    Activity activity = (Activity) chain.getThisObject();
                    if (activity != null) tryInstall(xposed, activity.getClassLoader());
                } catch (Throwable t) { repOptional(t); }
                return chain.proceed();
            });
        } catch (Throwable t) {
            Log.e(TAG, "hook Activity.onCreate failed: " + t);
        }
    }

    private static void tryInstall(XposedInterface xposed, ClassLoader cl) {
        if (canLoadTargetClasses(cl)) install(xposed, cl, gate, reporter);
    }

    private static boolean on(String key) {
        HookGate g = gate;
        return g == null || g.on(key);
    }

    /** 一次性安装全部净化 Hook（AtomicBoolean 保证幂等） */
    public static synchronized void install(XposedInterface xposed, ClassLoader cl, HookGate g,
                                            Main.Reporter rep) {
        if (g != null) gate = g;
        if (rep != null) reporter = rep;
        if (!HOOKED.compareAndSet(false, true)) return;
        Log.i(TAG, "Target classes available, installing XiaoCan purifier hooks");
        hookSplashActivity(xposed, cl);
        hookSplashAdFactory(xposed, cl);
        hookSplashAdUtils(xposed, cl);
        hookSilentTaskEngine(xposed, cl);
        hookFeedAndBannerAds(xposed, cl);
        hookFloatingAds(xposed, cl);
        hookSearchRightBanner(xposed, cl);
        hookFlutterAdPlugins(xposed, cl);
        hookAdActivities(xposed);
        hookDialogShowByContent(xposed);
        hookSpecificDialogClasses(xposed, cl);
        hookDialogUtils(xposed, cl);
        hookAppUpdateService(xposed, cl);
        hookMainViewModelPopups(xposed, cl);
        hookKuiklyDialogQueue(xposed, cl);
        Log.i(TAG, "XiaoCan purifier hooks installed");
        if (reporter != null) reporter.republish();   // 延迟安装完成后同步自检页
    }

    // ------------------------------------------------------------------
    // 冷启动开屏：WelComeActivity onCreate 后立即跳 MainActivity 并 finish
    // ------------------------------------------------------------------
    private static void hookSplashActivity(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> splash = Class.forName("com.realtech.xiaocan.WelComeActivity", false, cl);
            Class<?> main = Class.forName(MAIN_CLASS, false, cl);
            for (Method m : splash.getDeclaredMethods()) {
                if ("onCreate".equals(m.getName()) && m.getParameterTypes().length == 1) {
                    xposed.hook(m).intercept(chain -> {
                        Object result = chain.proceed();
                        if (on("splash")) {
                            try {
                                Activity a = (Activity) chain.getThisObject();
                                if (a != null && !a.isFinishing()) {
                                    android.content.Intent it = new android.content.Intent(a, main);
                                    it.addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                                    a.startActivity(it);
                                    a.finish();
                                    Log.i(TAG, "WelComeActivity skipped, entering MainActivity");
                                }
                            } catch (Throwable t) {
                                Log.w(TAG, "Splash skip failed: " + t);
                            }
                        }
                        return result;
                    });
                    Log.i(TAG, "Hooked WelComeActivity to skip splash"); repOk("WelComeActivity to skip splash");
                    break;
                }
            }
        } catch (Throwable t) {
            repFail("Failed to hook WelComeActivity", t);
        }
    }

    // ------------------------------------------------------------------
    // 开屏广告源工厂：create 返回净化代理（load → 立即 onAdClosed）
    // ------------------------------------------------------------------
    private static void hookSplashAdFactory(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> factoryClass = Class.forName("com.realtech.xiaocan.ad.splash.SplashAdSourceFactory", false, cl);
            Class<?> sourceInterface = Class.forName("com.realtech.xiaocan.ad.splash.SplashAdSource", false, cl);
            Class<?> listenerInterface = Class.forName("com.realtech.xiaocan.ad.splash.SplashAdSourceListener", false, cl);

            for (Method m : factoryClass.getDeclaredMethods()) {
                if ("create".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        if (!on("splash")) return chain.proceed();
                        Object listener = chain.getArgs().get(2);
                        Object proxy = Proxy.newProxyInstance(cl, new Class<?>[]{sourceInterface},
                                (p, method, args) -> {
                                    String mName = method.getName();
                                    if ("load".equals(mName)) {
                                        new Handler(Looper.getMainLooper()).post(() -> {
                                            try {
                                                Method onClosed = listenerInterface.getMethod("onAdClosed");
                                                onClosed.invoke(listener);
                                            } catch (Throwable closedErr) {
                                                try {
                                                    Method onError = listenerInterface.getMethod("onAdError");
                                                    onError.invoke(listener);
                                                } catch (Throwable t) { repOptional(t); }
                                            }
                                        });
                                        return null;
                                    }
                                    if ("isAdReady".equals(mName)) return Boolean.FALSE;
                                    if ("destroy".equals(mName)) return null;
                                    if ("equals".equals(mName))
                                        return p == (args != null && args.length > 0 ? args[0] : null);
                                    if ("hashCode".equals(mName)) return System.identityHashCode(p);
                                    if ("toString".equals(mName)) return "PurifiedSplashAdSource";
                                    return null;
                                });
                        Log.i(TAG, "SplashAdSourceFactory.create proxied");
                        return proxy;
                    });
                    Log.i(TAG, "Hooked SplashAdSourceFactory.create");
                    break;
                }
            }
        } catch (Throwable t) {
            repFail("Failed to hook SplashAdSourceFactory", t);
        }
    }

    // ------------------------------------------------------------------
    // 热启动开屏：initAd / refreshAsync 直接拦截
    // ------------------------------------------------------------------
    private static void hookSplashAdUtils(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> splashUtilsClass = Class.forName("com.realtech.xiaocan.util.SplashAdUtils", false, cl);
            for (Method m : splashUtilsClass.getDeclaredMethods()) {
                if ("initAd".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> on("splash") ? null : chain.proceed());
                    break;
                }
            }
        } catch (Throwable t) { repOptional(t); }

        try {
            Class<?> placementManager = Class.forName("com.realtech.xiaocan.ad.splash.SplashAdPlacementManager", false, cl);
            Method refreshAsync = placementManager.getDeclaredMethod("refreshAsync");
            xposed.hook(refreshAsync).intercept(chain -> on("splash") ? null : chain.proceed());
            Log.i(TAG, "Hooked SplashAdPlacementManager.refreshAsync"); repOk("SplashAdPlacementManager.refreshAsync");
        } catch (Throwable t) { repOptional(t); }
    }

    // ------------------------------------------------------------------
    // 静默任务引擎
    // ------------------------------------------------------------------
    private static void hookSilentTaskEngine(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> engineClass = Class.forName("com.realtech.xiaocan.ad.silent.SilentTaskEngine", false, cl);
            for (Method m : engineClass.getDeclaredMethods()) {
                String n = m.getName();
                if ("start".equals(n) || "init".equals(n) || "scheduleNext".equals(n)) {
                    xposed.hook(m).intercept(chain -> on("enabled") ? null : chain.proceed());
                }
            }
            Log.i(TAG, "Hooked SilentTaskEngine"); repOk("SilentTaskEngine");
        } catch (Throwable t) { repOptional(t); }
    }

    // ------------------------------------------------------------------
    // 信息流 / 横幅 Holder：bind 时整卡隐藏并零高度
    // ------------------------------------------------------------------
    private static void hideBindingRoot(Object holder) {
        try {
            Method getBinding = holder.getClass().getMethod("getBinding");
            Object binding = getBinding.invoke(holder);
            Method getRoot = binding.getClass().getMethod("getRoot");
            View root = (View) getRoot.invoke(binding);
            if (root != null) {
                root.setVisibility(View.GONE);
                ViewGroup.LayoutParams lp = root.getLayoutParams();
                if (lp != null) {
                    lp.height = 0;
                    root.setLayoutParams(lp);
                }
            }
        } catch (Throwable t) { repOptional(t); }
    }

    private static void hookFeedAndBannerAds(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> holderClass = Class.forName("com.realtech.promotion.adapters.holder.HomeFeedAdItemHolder", false, cl);
            for (Method m : holderClass.getDeclaredMethods()) {
                if ("bind".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        if (on("feed") && chain.getThisObject() != null) {
                            hideBindingRoot(chain.getThisObject());
                        }
                        return null;
                    });
                    Log.i(TAG, "Hooked HomeFeedAdItemHolder.bind"); repOk("HomeFeedAdItemHolder.bind");
                    break;
                }
            }
        } catch (Throwable t) { repOptional(t); }

        try {
            Class<?> bannerHolderClass = Class.forName("com.realtech.promotion.adapters.holder.HomeHeadBannerHolder", false, cl);
            for (Method m : bannerHolderClass.getDeclaredMethods()) {
                String n = m.getName();
                if ("bind".equals(n) || "renderBanner".equals(n)) {
                    xposed.hook(m).intercept(chain -> {
                        if (on("feed") && chain.getThisObject() != null) {
                            hideBindingRoot(chain.getThisObject());
                        }
                        return null;
                    });
                }
            }
            Log.i(TAG, "Hooked HomeHeadBannerHolder"); repOk("HomeHeadBannerHolder");
        } catch (Throwable t) { repOptional(t); }
    }

    // ------------------------------------------------------------------
    // 浮动广告管理器
    // ------------------------------------------------------------------
    private static void hookFloatingAds(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> floatingClass = Class.forName("com.realtech.xiaocan.ad.floating.FloatingAdManager", false, cl);
            for (Method m : floatingClass.getDeclaredMethods()) {
                String n = m.getName();
                if ("show".equals(n) || "load".equals(n) || "attach".equals(n)) {
                    xposed.hook(m).intercept(chain -> on("enabled") ? null : chain.proceed());
                }
            }
            Log.i(TAG, "Hooked FloatingAdManager"); repOk("FloatingAdManager");
        } catch (Throwable t) { repOptional(t); }
    }

    // ------------------------------------------------------------------
    // 搜索右侧横幅
    // ------------------------------------------------------------------
    private static void hookSearchRightBanner(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> barLayoutClass = Class.forName("com.realtech.promotion.widget.HomeAppBarLayoutView", false, cl);
            for (Method m : barLayoutClass.getDeclaredMethods()) {
                if ("renderSearchRightBanner".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        Object barLayout = chain.getThisObject();
                        try {
                            Field searchViewField = barLayout.getClass().getField("searchView");
                            Object searchView = searchViewField.get(barLayout);
                            if (searchView != null) {
                                Field bannerField = searchView.getClass().getField("searchRightBanner");
                                Object banner = bannerField.get(searchView);
                                if (banner instanceof View) ((View) banner).setVisibility(View.GONE);
                                Field ivField = searchView.getClass().getField("ivSearchRight");
                                Object iv = ivField.get(searchView);
                                if (iv instanceof View) ((View) iv).setVisibility(View.GONE);
                                Log.i(TAG, "SearchRightBanner hidden");
                            }
                        } catch (Throwable t) {
                            Log.w(TAG, "Failed to hide SearchRightBanner: " + t);
                        }
                        return null;
                    });
                    Log.i(TAG, "Hooked HomeAppBarLayoutView.renderSearchRightBanner"); repOk("HomeAppBarLayoutView.renderSearchRightBanner");
                    break;
                }
            }
        } catch (Throwable t) {
            repFail("Failed to hook HomeAppBarLayoutView", t);
        }
    }

    // ------------------------------------------------------------------
    // Flutter 广告插件全套（MethodChannel 分发 + AmSdk + Windmill 原生工厂）
    // ------------------------------------------------------------------
    private static final String[] FLUTTER_HANDLERS = {
            "com.windmill.windmill_ad_plugin.WindmillAdPluginDelegate",
            "com.windmill.windmill_ad_plugin.interstitial.InterstitialAd",
            "com.windmill.windmill_ad_plugin.reward.RewardVideoAd",
            "com.windmill.windmill_ad_plugin.splashAd.SplashAd",
            "com.windmill.windmill_ad_plugin.banner.BannerAd",
            "com.windmill.windmill_ad_plugin.feedAd.NativeAd",
            "com.realtech.xiaocan.flutter.NativeAdBridge"
    };

    private static boolean isAdMethod(String method) {
        if (method == null) return false;
        switch (method) {
            case "initRequest": case "load": case "showAd": case "isReady":
            case "getAdInfo": case "getCacheAdInfoList": case "destroy":
            case "init_interstitial": case "load_interstitial": case "pre_load_interstitial":
            case "init_with_id": case "load_with_id": case "pre_load_with_id":
            case "show_dyds":
                return true;
            default:
                return false;
        }
    }

    private static void hookFlutterAdPlugins(XposedInterface xposed, ClassLoader cl) {
        for (String name : FLUTTER_HANDLERS) {
            try {
                Class<?> handler = Class.forName(name, false, cl);
                Class<?> callType = Class.forName("io.flutter.plugin.common.MethodCall", false, cl);
                Class<?> resultType = Class.forName("io.flutter.plugin.common.MethodChannel$Result", false, cl);
                Field methodField = callType.getField("method");
                Method success = resultType.getMethod("success", Object.class);
                Method error = resultType.getMethod("error", String.class, String.class, Object.class);
                String entry = name.endsWith("NativeAdBridge") ? "createBridge$lambda$3" : "onMethodCall";
                Method target;
                try {
                    target = handler.getDeclaredMethod(entry, callType, resultType);
                } catch (Throwable e) {
                    target = DexFinder.findMethodByFeature(handler, void.class, new Class<?>[]{callType, resultType}, 0);
                }
                if (target == null) continue;
                xposed.hook(target).intercept(chain -> {
                    if (!on("enabled")) return chain.proceed();
                    String method = (String) methodField.get(chain.getArgs().get(0));
                    if (!isAdMethod(method)) return chain.proceed();
                    Object reply = chain.getArgs().get(1);
                    if ("isReady".equals(method) || "show_dyds".equals(method)) {
                        success.invoke(reply, Boolean.FALSE);
                    } else if ("getCacheAdInfoList".equals(method)) {
                        success.invoke(reply, Collections.emptyList());
                    } else if ("load".equals(method) || "showAd".equals(method)) {
                        error.invoke(reply, "AD_BLOCKED", "Ad unavailable", null);
                    } else {
                        success.invoke(reply, (Object) null);
                    }
                    Log.i(TAG, "Blocked Flutter ad method: " + method);
                    return null;
                });
                Log.i(TAG, "Hooked ad channel: " + name);
            } catch (Throwable t) {
                Log.w(TAG, "Ad channel hook unavailable: " + name + " (" + t.getClass().getSimpleName() + ")");
            }
        }

        // AmSdkPlugin
        try {
            Class<?> amPlugin = Class.forName("com.realtech.am_sdk_plugin.AmSdkPlugin", false, cl);
            for (Method m : amPlugin.getDeclaredMethods()) {
                String n = m.getName();
                if ("initSdk".equals(n) || "registerInterstitial".equals(n)) {
                    xposed.hook(m).intercept(chain -> on("enabled") ? null : chain.proceed());
                }
            }
        } catch (Throwable t) { repOptional(t); }

        try {
            Class<?> amHandler = Class.forName("com.realtech.am_sdk_plugin.AmInterstitialAdHandler", false, cl);
            for (Method m : amHandler.getDeclaredMethods()) {
                String n = m.getName();
                if ("loadAd".equals(n) || "show".equals(n)) {
                    xposed.hook(m).intercept(chain -> on("enabled") ? null : chain.proceed());
                }
            }
        } catch (Throwable t) { repOptional(t); }

        // WindMill 原生广告工厂：返回空视图代理
        try {
            Class<?> factoryClass = Class.forName("com.windmill.windmill_ad_plugin.WindMillNativeAdViewFactory", false, cl);
            Class<?> platformViewInterface = Class.forName("io.flutter.plugin.platform.PlatformView", false, cl);
            for (Method m : factoryClass.getDeclaredMethods()) {
                if ("create".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> {
                        if (!on("enabled")) return chain.proceed();
                        Context ctx = (Context) chain.getArgs().get(0);
                        return Proxy.newProxyInstance(cl, new Class<?>[]{platformViewInterface},
                                (p, method, args) -> {
                                    if ("getView".equals(method.getName())) {
                                        View v = new View(ctx);
                                        v.setVisibility(View.GONE);
                                        v.setLayoutParams(new ViewGroup.LayoutParams(0, 0));
                                        return v;
                                    }
                                    return null;
                                });
                    });
                    Log.i(TAG, "Hooked WindMillNativeAdViewFactory.create"); repOk("WindMillNativeAdViewFactory.create");
                    break;
                }
            }
        } catch (Throwable t) {
            repFail("Failed to hook WindMillNativeAdViewFactory", t);
        }

        try {
            Class<?> bannerClass = Class.forName("com.windmill.windmill_ad_plugin.banner.BannerAd", false, cl);
            for (Method m : bannerClass.getDeclaredMethods()) {
                String n = m.getName();
                if ("load".equals(n) || "showAd".equals(n)) {
                    xposed.hook(m).intercept(chain -> on("feed") ? null : chain.proceed());
                } else if ("isReady".equals(n)) {
                    xposed.hook(m).intercept(chain -> on("feed") ? Boolean.FALSE : chain.proceed());
                }
            }
        } catch (Throwable t) { repOptional(t); }

        try {
            Class<?> nativeClass = Class.forName("com.windmill.windmill_ad_plugin.feedAd.NativeAd", false, cl);
            for (Method m : nativeClass.getDeclaredMethods()) {
                String n = m.getName();
                if ("load".equals(n) || "showAd".equals(n)) {
                    xposed.hook(m).intercept(chain -> on("feed") ? null : chain.proceed());
                } else if ("isReady".equals(n)) {
                    xposed.hook(m).intercept(chain -> on("feed") ? Boolean.FALSE : chain.proceed());
                }
            }
        } catch (Throwable t) { repOptional(t); }

        try {
            Class<?> wmBannerView = Class.forName("com.windmill.sdk.banner.WMBannerView", false, cl);
            for (Method m : wmBannerView.getDeclaredMethods()) {
                if ("loadAd".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> on("feed") ? null : chain.proceed());
                }
            }
        } catch (Throwable t) { repOptional(t); }
    }

    // ------------------------------------------------------------------
    // 广告 Activity 秒关清单（原版精确清单：不含 TTFullAdActivity/openadsdk，
    // 它们是抽奖任务落地页宿主，秒关会导致任务页卡死）
    // ------------------------------------------------------------------
    private static void hookAdActivities(XposedInterface xposed) {
        try {
            Method actOnCreate = Activity.class.getDeclaredMethod("onCreate", Bundle.class);
            xposed.hook(actOnCreate).intercept(chain -> {
                Activity activity = (Activity) chain.getThisObject();
                if (activity != null && on("enabled")) {
                    String name = activity.getClass().getName().toLowerCase(Locale.ROOT);
                    if (isAdActivityName(name)) {
                        Log.i(TAG, "Auto finishing ad activity: " + activity.getClass().getName());
                        activity.finish();
                    }
                }
                return chain.proceed();
            });
        } catch (Throwable t) { repOptional(t); }
    }

    private static boolean isAdActivityName(String lowerName) {
        return lowerName.contains("tobidrewardvideoactivity")
                || lowerName.contains("douyindramaactivity")
                || lowerName.contains("kwad.sdk")
                || lowerName.contains("sigmob")
                || lowerName.contains("beizi")
                || lowerName.contains("qq.e.ads")
                || lowerName.contains("mcto.sspsdk")
                || lowerName.contains("aggmoread")
                || lowerName.contains("gameley.template")
                || lowerName.contains("ubix.ssp")
                || lowerName.contains("wj.mobads")
                || lowerName.contains("alliance.ssp");
    }

    // ------------------------------------------------------------------
    // 弹窗：按文案拦截（show 前扫描窗口内容，命中即跳过；show 后装配的立即 dismiss）
    // ------------------------------------------------------------------
    private static final String[] POPUP_MARKERS = {
            "你有一单可免单", "抽免单资格", "立即免单", "必得奖励", "开心收下",
            "恭喜订单完成", "关注小蚕霸王餐", "关注订单状态自动推送",
            "添加专属客服", "绑定专属客服", "获取重要通知",
    };

    private static void hookDialogShowByContent(XposedInterface xposed) {
        try {
            Method show = Dialog.class.getDeclaredMethod("show");
            xposed.hook(show).intercept(chain -> {
                Dialog dialog = chain.getThisObject() instanceof Dialog
                        ? (Dialog) chain.getThisObject() : null;
                if (dialog != null && on("popup") && containsBlockedText(dialog)) {
                    Log.i(TAG, "Blocked dialog by content before show");
                    return null;
                }
                Object result = chain.proceed();
                if (dialog != null && on("popup") && containsBlockedText(dialog)) {
                    Log.i(TAG, "Blocked dialog by content after show, dismissing");
                    try {
                        dialog.dismiss();
                    } catch (Throwable t) { repOptional(t); }
                    return null;
                }
                return result;
            });
            Log.i(TAG, "Content-based dialog guard installed"); repOk("Dialog.show content guard");
        } catch (Throwable t) {
            repFail("Failed to hook Dialog.show by content", t);
        }
    }

    private static boolean containsBlockedText(Dialog dialog) {
        try {
            android.view.Window window = dialog.getWindow();
            if (window == null) return false;
            View decor = window.getDecorView();
            if (decor == null) return false;
            StringBuilder sb = new StringBuilder();
            collectText(decor, sb);
            String text = sb.toString();
            if (text.contains("愉悦推") || text.contains("读取您的应用列表")
                    || text.contains("小蚕卫士")) {
                return true;
            }
            for (String marker : POPUP_MARKERS) {
                if (text.contains(marker)) return true;
            }
            return text.contains("今日免单难度降低")
                    || text.contains("当前店铺不在营业时间")
                    || text.contains("再签")
                    || text.contains("订单审核通过")
                    || text.contains("入群新人礼包")
                    || text.contains("即将自动跳转")
                    || text.contains("大额出行福利券")
                    || text.contains("跳转领取大额福利");
        } catch (Throwable t) {
            return false;   // 纯防御：弹窗文本抓取失败按无匹配处理（不拦截该弹窗，绝不误关正常弹窗）
        }
    }

    private static void collectText(View v, StringBuilder sb) {
        if (v == null || sb.length() > 4000) return;
        if (v instanceof android.widget.TextView) {
            CharSequence cs = ((android.widget.TextView) v).getText();
            if (cs != null) sb.append(cs).append('\n');
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) collectText(g.getChildAt(i), sb);
        }
    }

    // ------------------------------------------------------------------
    // 指定营销弹窗类
    // ------------------------------------------------------------------
    private static final String[] DIALOG_CLASSES = {
            "com.realtech.promotion.dialog.SharerHomePopupDialog",
            "com.realtech.xiaocan.dialog.agency.FirstOrderFullAmountAgencyDialog",
            "com.realtech.xiaocan.dialog.DouyinMallBonusDialog",
            "com.realtech.common.ui.dialog.update.AppUpdateDialog",
            "com.realtech.promotion.dialog.OpenSchoolshareDialog",
            "com.realtech.promotion.dialog.details.AnnualReportShareDialog",
            "com.realtech.promotion.dialog.share.AnnualReportSecondShareDialog",
            "com.realtech.promotion.dialog.share.SecondShareDialog",
            "com.realtech.vip.dialog.SyceeUpdateNoticeDialog"
    };

    private static void hookSpecificDialogClasses(XposedInterface xposed, ClassLoader cl) {
        for (String cName : DIALOG_CLASSES) {
            try {
                Class<?> dClass = Class.forName(cName, false, cl);
                for (Method m : dClass.getMethods()) {
                    if ("show".equals(m.getName()) && m.getParameterTypes().length == 0
                            && !Modifier.isStatic(m.getModifiers())) {
                        xposed.hook(m).intercept(chain -> {
                            Object dialog = chain.getThisObject();
                            if (!dClass.isInstance(dialog) || !on("popup")) return chain.proceed();
                            Log.i(TAG, "Blocked " + cName + ".show");
                            return null;
                        });
                    }
                }
                Log.i(TAG, "Hooked dialog class: " + cName); repOk(cName);
            } catch (Throwable t) { repOptional(t); }
        }
    }

    // ------------------------------------------------------------------
    // 全局弹窗队列 / 升级检查 / ViewModel 弹窗
    // ------------------------------------------------------------------
    private static void hookDialogUtils(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> dialogUtilsClass = Class.forName("com.realtech.common.dialog.DialogUtils", false, cl);
            for (Method m : dialogUtilsClass.getDeclaredMethods()) {
                if ("addDialog".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> on("popup") ? null : chain.proceed());
                    Log.i(TAG, "Hooked DialogUtils.addDialog");
                    break;
                }
            }
        } catch (Throwable t) { repOptional(t); }
    }

    private static void hookAppUpdateService(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> updateSvc = Class.forName("com.realtech.xiaocan.service.AppUpdateServiceImpl", false, cl);
            for (Method m : updateSvc.getDeclaredMethods()) {
                if ("checkUpdate".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> on("popup") ? null : chain.proceed());
                    Log.i(TAG, "Hooked AppUpdateServiceImpl.checkUpdate");
                    break;
                }
            }
        } catch (Throwable t) { repOptional(t); }
    }

    private static void hookMainViewModelPopups(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> vmClass = Class.forName("com.realtech.xiaocan.MainViewModel", false, cl);
            String[] popupMethods = {"checkUpdate", "checkSyceeUpdateDialog", "checkSchoolStarts", "takeGiftReq"};
            for (Method m : vmClass.getDeclaredMethods()) {
                for (String target : popupMethods) {
                    if (target.equals(m.getName())) {
                        xposed.hook(m).intercept(chain -> on("popup") ? null : chain.proceed());
                        Log.i(TAG, "Hooked MainViewModel." + target);
                    }
                }
            }
        } catch (Throwable t) { repOptional(t); }
    }

    private static void hookKuiklyDialogQueue(XposedInterface xposed, ClassLoader cl) {
        try {
            Class<?> gdqClass = Class.forName("com.realtech.xiaocankuikly.utils.GlobalDialogQueue", false, cl);
            for (Method m : gdqClass.getDeclaredMethods()) {
                if ("show".equals(m.getName())) {
                    xposed.hook(m).intercept(chain -> on("popup") ? null : chain.proceed());
                    Log.i(TAG, "Hooked GlobalDialogQueue.show");
                    break;
                }
            }
        } catch (Throwable t) { repOptional(t); }

        try {
            Class<?> dqmClass = Class.forName("com.realtech.xiaocankuikly.utils.DialogQueueManager", false, cl);
            for (Method m : dqmClass.getDeclaredMethods()) {
                String n = m.getName();
                if ("show".equals(n) || "enqueue".equals(n)) {
                    xposed.hook(m).intercept(chain -> on("popup") ? null : chain.proceed());
                }
            }
        } catch (Throwable t) { repOptional(t); }
    }
}
