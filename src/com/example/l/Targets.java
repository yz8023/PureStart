package com.example.l;

/**
 * 目标应用清单 —— 单一事实来源（纯数据类）。
 *
 * 为什么独立成类：设置页运行在模块自身的普通进程里，该进程没有 libxposed
 * 运行时（LSPosed 只把 API 注入目标应用）。早期版本曾因设置页直接引用
 * Main.TARGETS，导致加载 Main → 解析其父类 XposedModule →
 * NoClassDefFoundError 崩溃。UI 类与 Hook 类必须彻底隔离，本类不得引用
 * Main / XiaoCanHook / UniversalAdHook / DexFinder / libxposed 任何符号。
 *
 * 新增目标 = 本文件 + xposed/scope.list + SettingsActivity.APP_DESC
 * （scripts/check_consistency.py 构建前把关三处一致 + UI/Hook 隔离）。
 */
public final class Targets {
    private Targets() {}

    public static final String[][] LIST = {
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
}
