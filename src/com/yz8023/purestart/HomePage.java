package com.yz8023.purestart;

import android.graphics.Typeface;
import android.os.Build;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;

/**
 * 「首页」页组装（P1-2 自 SettingsActivity 拆出，行为不变）。
 *
 * 内容：状态卡（激活检测/版本/Beta 徽章/长按盾牌全局开关）、双统计卡
 * （适配应用/目标应用已装，后台线程回填）、设备信息卡、反馈卡。
 * 附带自检卡与桌面图标行（供「设置」页引用，见 selfCheckCard()/desktopIconRow()）。
 */
final class HomePage {
    private final SettingsActivity a;

    HomePage(SettingsActivity a) {
        this.a = a;
    }

    View build() {
        ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(true);

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (22 * a.dp);
        box.setPadding(pad, (int) (10 * a.dp), pad, (int) (108 * a.dp));
        scroll.addView(box, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        // ① 大标题
        TextView title = new TextView(a);
        title.setText(a.getApplicationInfo().loadLabel(a.getPackageManager()));
        title.setTextSize(34);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(a.INK);
        title.setPadding((int) (2 * a.dp), 0, 0, (int) (20 * a.dp));
        box.addView(title);

        // ② 状态卡：图标 + 激活状态 + 版本号 + Beta 徽章
        LinearLayout statusCard = a.makeCard();
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setGravity(Gravity.CENTER_VERTICAL);
        statusCard.setPadding((int) (20 * a.dp), (int) (18 * a.dp),
                (int) (14 * a.dp), (int) (18 * a.dp));

        final HoldShieldView shield = new HoldShieldView(a);
        boolean allOn = true;
        if (a.appsData != null && !a.appsData.isEmpty()) {
            for (SettingsActivity.AppEntry entry : a.appsData) {
                if (entry.installed && !a.isHookEnabled(entry.pkg)) {
                    allOn = false;
                    break;
                }
            }
        }
        shield.setActive(allOn);
        shield.setListener(new HoldShieldView.Listener() {
            @Override
            public void onHoldComplete() {
                boolean targetState = !shield.isActive();
                shield.setActive(targetState);
                if (a.appsData != null) {
                    for (SettingsActivity.AppEntry e : a.appsData) {
                        a.setHookEnabled(e.pkg, targetState);
                    }
                }
                a.updateScopeBadge();
                a.renderAppsList();
                android.widget.Toast.makeText(a,
                        targetState ? "已一键开启所有应用广告拦截与优化" : "已重置并关闭所有应用适配拦截",
                        android.widget.Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onTapHint() {
                android.widget.Toast.makeText(a,
                        "长按盾牌图标 1 秒，一键全局开关全部应用净化",
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        statusCard.addView(shield, new LinearLayout.LayoutParams(
                (int) (48 * a.dp), (int) (48 * a.dp)));

        LinearLayout mid = new LinearLayout(a);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding((int) (14 * a.dp), 0, 0, 0);
        // 真实激活检测：LSPosed 激活时会把 XposedBridge 注入 boot classpath（任何进程可查）
        boolean fwActive = a.isFrameworkActive();
        int fwVer = fwActive ? a.frameworkVersion() : -1;
        TextView status = new TextView(a);
        status.setText(fwActive ? "已激活" : "未激活");
        status.setTextSize(17);
        status.setTypeface(Typeface.DEFAULT_BOLD);
        status.setTextColor(fwActive ? a.STATUS_GREEN : a.BADGE_RED);
        mid.addView(status);
        TextView ver = new TextView(a);
        ver.setText(fwActive
                ? a.appVersion() + (fwVer > 0 ? " · 框架 v" + fwVer : "")
                : a.appVersion() + " · 未检测到 LSPosed / Xposed 框架");
        ver.setTextSize(12.5f);
        ver.setTextColor(a.SUB);
        ver.setPadding(0, (int) (3 * a.dp), 0, 0);
        mid.addView(ver);
        statusCard.addView(mid, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView chip = new TextView(a);
        chip.setText("Beta");
        chip.setTextSize(11);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setTextColor(0xFFFFFFFF);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding((int) (12 * a.dp), (int) (5 * a.dp), (int) (12 * a.dp), (int) (5 * a.dp));
        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setColor(a.ACCENT);
        chipBg.setCornerRadius(99 * a.dp);
        chip.setBackground(chipBg);
        statusCard.addView(chip, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        a.addCard(box, statusCard);

        // ③ 双统计卡
        LinearLayout statRow = new LinearLayout(a);
        statRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams half1 = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        // 适配应用卡：a.scopeAppCount() 要读 APK zip + 逐包 getPackageInfo（慢），
        // 与“已安装软件”卡同策略：先占位，后台线程统计后回填（v1.0.11 接真）
        LinearLayout scopeCard = a.makeCard();
        scopeCard.setPadding((int) (20 * a.dp), (int) (16 * a.dp),
                (int) (20 * a.dp), (int) (16 * a.dp));
        TextView scopeLabel = new TextView(a);
        scopeLabel.setText("适配应用");
        scopeLabel.setTextSize(13);
        scopeLabel.setTextColor(a.SUB);
        scopeCard.addView(scopeLabel);
        final TextView scopeVal = new TextView(a);
        scopeVal.setText("…");
        scopeVal.setTextSize(24);
        scopeVal.setTypeface(Typeface.DEFAULT_BOLD);
        scopeVal.setTextColor(a.INK);
        scopeVal.setPadding(0, (int) (4 * a.dp), 0, 0);
        scopeCard.addView(scopeVal);
        statRow.addView(scopeCard, half1);
        new Thread(() -> {
            final int n = a.scopeAppCount();
            a.runOnUiThread(() -> scopeVal.setText(String.valueOf(n)));
        }, "scope-count").start();
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        half2.leftMargin = (int) (12 * a.dp);
        // 目标应用卡：逐包查 17 个目标（<queries> 已声明可见），后台线程统计后回填
        LinearLayout appsCard = a.makeCard();
        appsCard.setPadding((int) (20 * a.dp), (int) (16 * a.dp),
                (int) (20 * a.dp), (int) (16 * a.dp));
        TextView appsLabel = new TextView(a);
        appsLabel.setText("目标应用已装");
        appsLabel.setTextSize(13);
        appsLabel.setTextColor(a.SUB);
        appsCard.addView(appsLabel);
        final TextView appsVal = new TextView(a);
        appsVal.setText("…");
        appsVal.setTextSize(24);
        appsVal.setTypeface(Typeface.DEFAULT_BOLD);
        appsVal.setTextColor(a.INK);
        appsVal.setPadding(0, (int) (4 * a.dp), 0, 0);
        appsCard.addView(appsVal);
        statRow.addView(appsCard, half2);
        new Thread(() -> {
            final int n = a.installedAppCount();
            a.runOnUiThread(() -> appsVal.setText(String.valueOf(n)));
        }, "app-count").start();
        LinearLayout.LayoutParams statLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        statLp.bottomMargin = (int) (12 * a.dp);
        box.addView(statRow, statLp);

        // ④ 信息卡列表（全部为运行时真实设备数据）
        String[][] infos = {
                {"构建时间", BuildInfo.BUILD_TIME},
                {"安卓版本", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ", REL)"},
                {"设备型号", Build.MODEL + " (" + Build.PRODUCT + ")"},
                {"系统架构", Build.SUPPORTED_ABIS[0]},
                {"框架 & API", "libxposed API 102（Modern Xposed）"},
        };
        for (String[] row : infos) {
            LinearLayout card = a.makeCard();
            card.setPadding((int) (20 * a.dp), (int) (16 * a.dp),
                    (int) (20 * a.dp), (int) (16 * a.dp));
            TextView l = new TextView(a);
            l.setText(row[0]);
            l.setTextSize(13);
            l.setTextColor(a.SUB);
            card.addView(l);
            TextView v = new TextView(a);
            v.setText(row[1]);
            v.setTextSize(15);
            v.setTypeface(Typeface.DEFAULT_BOLD);
            v.setTextColor(a.INK);
            v.setPadding(0, (int) (3 * a.dp), 0, 0);
            card.addView(v);
            a.addCard(box, card);
        }

        // ⑤ 反馈卡：文字 + 两个圆形图标按钮
        LinearLayout feedCard = a.makeCard();
        feedCard.setOrientation(LinearLayout.HORIZONTAL);
        feedCard.setGravity(Gravity.CENTER_VERTICAL);
        feedCard.setPadding((int) (20 * a.dp), (int) (18 * a.dp),
                (int) (14 * a.dp), (int) (18 * a.dp));
        TextView feed = new TextView(a);
        feed.setText("在 GitHub 反馈");
        feed.setTextSize(15);
        feed.setTypeface(Typeface.DEFAULT_BOLD);
        feed.setTextColor(a.INK);
        feedCard.addView(feed, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        feedCard.addView(a.roundButton(R.drawable.ic_action_code), a.roundLp(0));
        feedCard.addView(a.roundButton(R.drawable.ic_action_chat), a.roundLp((int) (12 * a.dp)));
        a.addCard(box, feedCard);

        return scroll;
    }

    /** Hook 自检卡：17 目标三态（类命中/未命中/未安装）+ 失败明细（Provider 回传，P1-3） */
    LinearLayout selfCheckCard() {
        LinearLayout card = a.makeCard();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding((int) (16 * a.dp), (int) (14 * a.dp), (int) (16 * a.dp), (int) (8 * a.dp));
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(a);
        title.setText("目标应用安装状态");
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(a.INK);
        head.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        TextView refresh = new TextView(a);
        refresh.setText("刷新");
        refresh.setTextSize(13);
        refresh.setTextColor(a.ACCENT);
        refresh.setPadding((int) (10 * a.dp), (int) (4 * a.dp), (int) (10 * a.dp), (int) (4 * a.dp));
        refresh.setOnClickListener(v -> renderSelfCheck(list));
        head.addView(refresh);
        card.addView(head);
        card.addView(list);
        renderSelfCheck(list);
        return card;
    }

    private void renderSelfCheck(LinearLayout list) {
        list.removeAllViews();
        android.content.SharedPreferences sp =
                a.getSharedPreferences(Prefs.FILE_SELFCHECK, android.content.Context.MODE_PRIVATE);
        java.text.DateFormat tf = new java.text.SimpleDateFormat("MM-dd HH:mm",
                java.util.Locale.getDefault());
        for (String[] t : Targets.LIST) {
            String row = sp.getString(t[0], null);
            TextView line = new TextView(a);
            line.setTextSize(12);
            line.setPadding(0, (int) (4 * a.dp), 0, (int) (4 * a.dp));
            String name = t[1].split(" · ")[0];
            // 三态之一：未安装（PackageManager 实查，<queries> 已声明可见）
            boolean installed = false;
            try {
                a.getPackageManager().getPackageInfo(t[0], 0);
                installed = true;
            } catch (Throwable e) {
                // 纯防御：NameNotFoundException 即未安装，属自检三态的预期分支
            }
            if (!installed) {
                line.setText(name + "：未安装");
                line.setTextColor(a.HINT);
            } else if (row == null) {
                line.setText(name + "：未上报（打开一次目标应用后自动回传）");
                line.setTextColor(a.SUB);
            } else {
                String[] p = row.split("\\|", -1);   // ok|miss|fails|version|at
                // 纯防御：自检行字段缺失/非数字按 0 处理（兼容旧版数据），无需留痕
                int ok = 0, miss = 0;
                try { ok = Integer.parseInt(p[0]); } catch (Throwable ignored) {}
                try { miss = Integer.parseInt(p[1]); } catch (Throwable ignored) {}
                String fails = p.length > 2 ? p[2] : "";
                String ver = p.length > 3 ? p[3] : "?";
                long at = 0L;
                try { at = Long.parseLong(p.length > 4 ? p[4] : "0"); } catch (Throwable ignored) {}
                StringBuilder sb = new StringBuilder();
                if (ok > 0) {
                    // 三态之二：类命中（至少一路 hook 已装上）
                    sb.append(name).append("：类命中 · 已装 ").append(ok).append(" 项");
                    if (miss > 0) sb.append(" · 缺 ").append(miss);
                } else {
                    // 三态之三：未命中（进程在跑但一路都没装上：主类失配/框架钩子全部失败）
                    sb.append(name).append("：未命中（主类或框架钩子未装上，详见日志）");
                }
                if (fails != null && !fails.isEmpty()) {
                    sb.append(" · 失败: ").append(fails, 0, Math.min(fails.length(), 80));
                    line.setTextColor(a.BADGE_RED);
                } else {
                    line.setTextColor(ok > 0 ? a.STATUS_GREEN : a.BADGE_RED);
                }
                sb.append("\n").append("v").append(ver).append(" · ")
                        .append(at > 0 ? tf.format(new java.util.Date(at)) : "—");
                line.setText(sb.toString());
            }
            list.addView(line, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    /** 桌面图标开关：真实切换 activity-alias（禁用即从桌面隐藏，启用即恢复） */
    LinearLayout desktopIconRow() {
        LinearLayout card = a.makeCard();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int vpad = (int) (16 * a.dp);
        card.setPadding((int) (14 * a.dp), vpad, (int) (14 * a.dp), vpad);

        ImageView icon = new ImageView(a);
        icon.setImageResource(R.drawable.ic_set_home);
        icon.setColorFilter(a.ACCENT);
        card.addView(icon, new LinearLayout.LayoutParams((int) (26 * a.dp), (int) (26 * a.dp)));

        LinearLayout mid = new LinearLayout(a);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding((int) (12 * a.dp), 0, 0, 0);
        TextView t1 = new TextView(a);
        t1.setText("在桌面显示图标");
        t1.setTextSize(15);
        t1.setTypeface(Typeface.DEFAULT_BOLD);
        t1.setTextColor(a.INK);
        mid.addView(t1);
        TextView t2 = new TextView(a);
        t2.setText("隐藏后可从 LSPosed 模块列表重新打开本页；若图标未消失，请检查 LSPosed 是否开启了强制显示桌面图标");
        t2.setTextSize(12);
        t2.setTextColor(a.SUB);
        t2.setPadding(0, (int) (2 * a.dp), 0, 0);
        mid.addView(t2);
        card.addView(mid, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final android.widget.Switch sw = new android.widget.Switch(a);
        sw.setChecked(isLauncherAliasEnabled());
        sw.setOnCheckedChangeListener((v, checked) -> {
            if (!checked) {
                // 隐藏前必须确认：隐藏后桌面与 LSPosed「打开」都会失效，给足恢复方式
                sw.setChecked(true);   // 先回弹，确认后才真正隐藏
                new android.app.AlertDialog.Builder(a)
                        .setTitle("隐藏桌面图标")
                        .setMessage("隐藏后将无法从桌面打开，LSPosed 的「打开」也可能失效。\n\n"
                                + "恢复方式（任选其一）：\n"
                                + "① adb shell am broadcast -a com.yz8023.purestart.action.SHOW_ICON\n"
                                + "② adb shell pm enable com.yz8023.purestart/.IconAlias\n"
                                + "③ 系统设置 → 应用 → PureStart → 卸载后重装（设置会丢失）")
                        .setPositiveButton("仍要隐藏", (d, w) -> setDesktopIcon(sw, false))
                        .setNegativeButton("取消", null)
                        .show();
            } else {
                setDesktopIcon(sw, true);
            }
        });
        card.addView(sw);
        return card;
    }

    private void setDesktopIcon(android.widget.Switch sw, boolean show) {
        try {
            a.getPackageManager().setComponentEnabledSetting(
                    new android.content.ComponentName(a, "com.yz8023.purestart.IconAlias"),
                    show ? android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            : android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP);
            Prefs.setDesktopIconPref(a, show);
            sw.setChecked(show);
        } catch (Throwable t) {
            android.widget.Toast.makeText(a, "切换失败：" + t,
                    android.widget.Toast.LENGTH_SHORT).show();
            sw.setChecked(isLauncherAliasEnabled());
        }
    }

    /** 桌面图标的真实状态以 PackageManager 为准（LSPosed 强制显示等外部改动也能正确反映） */
    private boolean isLauncherAliasEnabled() {
        try {
            return a.getPackageManager().getComponentEnabledSetting(
                    new android.content.ComponentName(a, "com.yz8023.purestart.IconAlias"))
                    != android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        } catch (Throwable t) {
            return true;
        }
    }
}
