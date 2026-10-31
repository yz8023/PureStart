package com.yz8023.purestart;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SharedPreferences 单点收口（P1-2）：所有键名常量与读写方法集中于此，
 * 其他文件不许再出现裸字符串键名（scripts/check.py 不放行漂移）。
 *
 * 默认值语义（P0-1）：Hook 类键（hook_/sub_*）未设置 = false（不动作）；
 * 界面功能键保持各自原默认。磨砂参数取值借鉴 ui 模板设置页（同名同范围同默认），
 * 但行为映射为自创近似（见设置页差异声明）；两条染色公式为模板 Glass.kt 原式
 * （backdrop 路径: tintAlpha + opacity×0.04；退化路径: 0.26 + tintAlpha + opacity×0.18）。
 */
public final class Prefs {
    private Prefs() {}

    /** 偏好文件名（框架远程偏好同名，Main 兜底通道用） */
    public static final String FILE = "settings";
    /** Hook 自检回传存储（Provider insert 写，设置页读） */
    public static final String FILE_SELFCHECK = "selfcheck";

    // ==== 键前缀 / 键名（唯一出处） ====
    public static final String PREFIX_HOOK = "hook_";
    public static final String PREFIX_SUB_SPLASH = "sub_splash_";
    public static final String PREFIX_SUB_FEED = "sub_feed_";
    public static final String PREFIX_SUB_POPUP = "sub_popup_";
    public static final String PREFIX_SUB_DEXFINDER = "sub_dexfinder_";

    public static final String KEY_THEME_MODE = "theme_mode";
    public static final String KEY_LOCALE_MODE = "locale_mode";
    public static final String KEY_LIQUID_BLUR = "set_liquid_blur";
    public static final String KEY_APP_BADGE = "set_app_badge";
    public static final String KEY_AUTO_UPDATE = "set_auto_update";
    /** 页面左右滑动开关（v15.1.2 新增；默认开） */
    public static final String KEY_PAGE_SWIPE = "set_page_swipe";
    /** 应用背景来源：0 极光 / 1 系统壁纸 / 2 自定义图片（v15.1.3 新增；默认极光） */
    public static final String KEY_WALLPAPER_MODE = "set_wallpaper_mode";
    /** 自定义背景图片的私有绝对路径（v15.1.3 新增） */
    public static final String KEY_WALLPAPER_PATH = "wallpaper_path";
    /** 壁纸压暗强度 0..70（v15.1.3 新增；默认 30） */
    public static final String KEY_WALLPAPER_DIM = "wallpaper_dim";
    /** 系统壁纸读取失败原因（v15.1.4；空 = 正常/未尝试） */
    public static final String KEY_WALLPAPER_ERROR = "wallpaper_error";
    /** 卡片底色 0..40（v15.1.5；默认 10，可拉到 0 得到完全透明卡片） */
    public static final String KEY_CARD_TINT = "card_tint";
    /** 普通模式（v15.1.6）：true = 实心 Material 卡片，不做玻璃/折射/模糊 */
    public static final String KEY_PLAIN_MODE = "plain_mode";
    public static final String KEY_DESKTOP_ICON = "set_desktop_icon";

    public static final String KEY_GLASS_OPACITY = "glass_opacity";
    public static final String KEY_GLASS_BLUR = "glass_blur";
    public static final String KEY_GLASS_REFRACT_HEIGHT = "glass_refract_height";
    public static final String KEY_GLASS_REFRACT_AMOUNT = "glass_refract_amount";
    public static final String KEY_GLASS_TINT_ALPHA = "glass_tint_alpha";
    public static final String KEY_GLASS_BORDER = "glass_border";
    public static final String KEY_GLASS_DAMPING = "glass_damping";
    public static final String KEY_GLASS_VIBRANCY = "glass_vibrancy";
    public static final String KEY_GLASS_CHROMATIC = "glass_chromatic";

    public static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ==== Hook 开关（默认 false：未设置 = 不动作） ====

    public static boolean hookEnabled(Context c, String pkg) {
        return sp(c).getBoolean(PREFIX_HOOK + pkg, false);
    }

    public static void setHookEnabled(Context c, String pkg, boolean v) {
        sp(c).edit().putBoolean(PREFIX_HOOK + pkg, v).apply();
    }

    public static boolean subEnabled(Context c, String prefix, String pkg) {
        return sp(c).getBoolean(prefix + pkg, false);
    }

    public static void setSubEnabled(Context c, String prefix, String pkg, boolean v) {
        sp(c).edit().putBoolean(prefix + pkg, v).apply();
    }

    /** setRow 用：该键是否属于 Hook 类（决定默认关语义） */
    public static boolean isHookKey(String key) {
        return key != null && (key.startsWith(PREFIX_HOOK)
                || key.startsWith(PREFIX_SUB_SPLASH)
                || key.startsWith("sub_"));
    }

    // ==== 界面偏好 ====

    public static int themeMode(Context c) { return sp(c).getInt(KEY_THEME_MODE, 0); }
    public static void setThemeMode(Context c, int v) { sp(c).edit().putInt(KEY_THEME_MODE, v).apply(); }
    public static int localeMode(Context c) { return sp(c).getInt(KEY_LOCALE_MODE, 0); }
    public static boolean liquidBlurEnabled(Context c) { return sp(c).getBoolean(KEY_LIQUID_BLUR, true); }
    public static boolean appBadgeEnabled(Context c) { return sp(c).getBoolean(KEY_APP_BADGE, true); }
    public static boolean autoUpdateEnabled(Context c) { return sp(c).getBoolean(KEY_AUTO_UPDATE, true); }
    public static boolean pageSwipeEnabled(Context c) { return sp(c).getBoolean(KEY_PAGE_SWIPE, true); }

    /** 背景来源：0 极光 / 1 系统壁纸 / 2 自定义图片 */
    public static int wallpaperMode(Context c) { return (int) clamp(sp(c).getInt(KEY_WALLPAPER_MODE, 0), 0, 2); }
    public static void setWallpaperMode(Context c, int v) { sp(c).edit().putInt(KEY_WALLPAPER_MODE, v).apply(); }
    public static String wallpaperPath(Context c) { return sp(c).getString(KEY_WALLPAPER_PATH, null); }
    public static void setWallpaperPath(Context c, String v) { sp(c).edit().putString(KEY_WALLPAPER_PATH, v).apply(); }
    /** 壁纸压暗强度 0..0.7（默认 0.30） */
    public static float wallpaperDim(Context c) { return clamp(sp(c).getInt(KEY_WALLPAPER_DIM, 30) / 100f, 0f, .7f); }
    /**
     * 卡片底色 0..0.40（默认 0.10）。这是**唯一**决定卡片是否「发白」的参数：
     * 拉到 0 时卡片只剩一圈极淡的描边，完全透出背景。
     */
    public static float cardTint(Context c) { return clamp(sp(c).getInt(KEY_CARD_TINT, 10) / 100f, 0f, .40f); }

    /**
     * 普通模式（对照 yz8023/UI 的 VisualMode.Normal）：卡片是**实心** Material 表面，
     * 不采样背景、不模糊、不折射；深色主题用深色实心、浅色主题用白色实心卡片。
     */
    public static boolean plainMode(Context c) { return sp(c).getBoolean(KEY_PLAIN_MODE, false); }
    public static void setPlainMode(Context c, boolean v) { sp(c).edit().putBoolean(KEY_PLAIN_MODE, v).apply(); }
    public static String wallpaperError(Context c) { return sp(c).getString(KEY_WALLPAPER_ERROR, null); }
    public static void setWallpaperError(Context c, String v) {
        sp(c).edit().putString(KEY_WALLPAPER_ERROR, v).apply();
    }
    public static void setDesktopIconPref(Context c, boolean v) {
        sp(c).edit().putBoolean(KEY_DESKTOP_ICON, v).apply();
    }

    // ==== 磨砂玻璃（View 近似）参数（取值借鉴模板，映射为自创近似） ====

    /** 不透明度 0..1（容器 alpha = 0.10 + opacity × 0.55） */
    public static float opacity(Context c) {
        return clamp(sp(c).getInt(KEY_GLASS_OPACITY, 55) / 100f, 0f, 1f);
    }

    /** 玻璃染色 0..0.35（默认 0.08） */
    public static float tintAlpha(Context c) {
        return clamp(sp(c).getInt(KEY_GLASS_TINT_ALPHA, 8) / 100f, 0f, 0.35f);
    }

    /** 模糊强度 dp 0..40（默认 0） */
    public static int blurDp(Context c) {
        return (int) clamp(sp(c).getInt(KEY_GLASS_BLUR, 0), 0, 40);
    }

    /** 折射高度 dp 0..40（默认 15；近似映射 = 棱光线宽，非模板原值） */
    public static int prismHeightDp(Context c) {
        return (int) clamp(sp(c).getInt(KEY_GLASS_REFRACT_HEIGHT, 15), 0, 40);
    }

    /** 折射幅度 dp 0..60（默认 20；近似映射 = 背景层放大，非模板原值） */
    public static int prismAmountDp(Context c) {
        return (int) clamp(sp(c).getInt(KEY_GLASS_REFRACT_AMOUNT, 20), 0, 60);
    }

    /** 高光边框 0..0.45（默认 0.20） */
    public static float borderAlpha(Context c) {
        return clamp(sp(c).getInt(KEY_GLASS_BORDER, 20) / 100f, 0f, 0.45f);
    }

    /** 动效阻尼 0..1（默认 0.5） */
    public static float damping(Context c) {
        return clamp(sp(c).getInt(KEY_GLASS_DAMPING, 50) / 100f, 0f, 1f);
    }

    /** 增强色彩层次（饱和度 +18%） */
    public static boolean vibrancy(Context c) {
        return sp(c).getBoolean(KEY_GLASS_VIBRANCY, true);
    }

    /** 色差折射（棱光 RGB 色散近似） */
    public static boolean chromatic(Context c) {
        return sp(c).getBoolean(KEY_GLASS_CHROMATIC, true);
    }

    // ==== Hook 自检（Provider 写 / 设置页读） ====

    /** 读一行自检上报：ok|miss|fails|version|at；无上报返回 null */
    public static String readSelfCheck(Context c, String pkg) {
        return c.getSharedPreferences(FILE_SELFCHECK, Context.MODE_PRIVATE).getString(pkg, null);
    }

    /** 写一行自检上报（HookStateProvider.insert 调用） */
    public static void writeSelfCheck(Context c, String pkg, String row) {
        c.getSharedPreferences(FILE_SELFCHECK, Context.MODE_PRIVATE)
                .edit().putString(pkg, row).apply();
    }

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
