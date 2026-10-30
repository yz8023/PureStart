package com.example.l;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 「应用」页组装（P1-2 自 SettingsActivity 拆出，行为不变）。
 *
 * 内容：后台读 APK 内 META-INF/xposed/scope.list 解析安装状态/名称/版本，
 * 搜索过滤 + 单卡勾选启用（写偏好经 Provider 供 Hook 侧读取）+ 点击进细分弹窗。
 * 数据模型 AppEntry 与 appsData 仍持于 activity（首页盾牌/角标共用）。
 */
final class AppsPage {
    private final SettingsActivity a;

    private LinearLayout appsListContainer;   // 列表容器
    private android.widget.EditText appsSearch;   // 搜索框
    private TextView appsCountView;           // 标题旁数量

    AppsPage(SettingsActivity a) {
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

        // ① 标题行：适配应用 + 数量
        LinearLayout titleRow = new LinearLayout(a);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(a);
        title.setText("适配应用");
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(a.INK);
        title.setPadding((int) (2 * a.dp), 0, 0, 0);
        titleRow.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        appsCountView = new TextView(a);
        appsCountView.setText("…");
        appsCountView.setTextSize(13);
        appsCountView.setTextColor(a.SUB);
        titleRow.addView(appsCountView);
        box.addView(titleRow);

        // ② 搜索框
        appsSearch = new android.widget.EditText(a);
        appsSearch.setHint("搜索应用");
        appsSearch.setTextSize(14);
        appsSearch.setSingleLine(true);
        appsSearch.setTextColor(a.INK);
        appsSearch.setHintTextColor(a.HINT);
        appsSearch.setPadding((int) (16 * a.dp), (int) (12 * a.dp), (int) (16 * a.dp), (int) (12 * a.dp));
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(a.FIELD_BG);
        searchBg.setCornerRadius(14 * a.dp);
        appsSearch.setBackground(searchBg);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        searchLp.topMargin = (int) (10 * a.dp);
        searchLp.bottomMargin = (int) (12 * a.dp);
        box.addView(appsSearch, searchLp);
        appsSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override public void afterTextChanged(android.text.Editable s) {
                renderList();
            }
        });

        // ③ 列表容器（后台加载后填充）
        appsListContainer = new LinearLayout(a);
        appsListContainer.setOrientation(LinearLayout.VERTICAL);
        box.addView(appsListContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView loading = new TextView(a);
        loading.setText("正在读取作用域应用…");
        loading.setTextSize(13);
        loading.setTextColor(a.SUB);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(0, (int) (40 * a.dp), 0, (int) (40 * a.dp));
        appsListContainer.addView(loading);

        loadAppsAsync();
        return scroll;
    }

    /** 后台读 scope.list，解析每个包的安装状态/名称/版本 */
    void loadAppsAsync() {
        final PackageManager pm = a.getPackageManager();
        new Thread(() -> {
            List<SettingsActivity.AppEntry> list = new ArrayList<>();
            java.util.zip.ZipFile zf = null;
            try {
                zf = new java.util.zip.ZipFile(a.getPackageCodePath());
                java.util.zip.ZipEntry entry = zf.getEntry("META-INF/xposed/scope.list");
                if (entry != null) {
                    java.io.BufferedReader r = new java.io.BufferedReader(
                            new java.io.InputStreamReader(zf.getInputStream(entry), "UTF-8"));
                    String line;
                    while ((line = r.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        String label = line;
                        String version = "未安装";
                        boolean installed = false;
                        try {
                            PackageInfo pi = pm.getPackageInfo(line, 0);
                            installed = true;
                            label = pi.applicationInfo.loadLabel(pm).toString();
                            version = pi.versionName == null ? "—" : pi.versionName;
                        } catch (Throwable ignored) {
                        }
                        list.add(new SettingsActivity.AppEntry(line, label, version, installed));
                    }
                    r.close();
                }
            } catch (Throwable t) {
                // 读不到就按空列表处理
            } finally {
                if (zf != null) try { zf.close(); } catch (Throwable ignored) {
                }
            }
            // 已安装在前，名称排序
            Collections.sort(list, (a, b) -> {
                if (a.installed != b.installed) return a.installed ? -1 : 1;
                return a.label.compareToIgnoreCase(b.label);
            });
            final List<SettingsActivity.AppEntry> data = list;
            a.runOnUiThread(() -> {
                a.appsData = data;
                if (appsCountView != null) {
                    int inst = 0;
                    for (SettingsActivity.AppEntry e : data) if (e.installed) inst++;
                    appsCountView.setText(data.isEmpty() ? "0 个" : inst + " / " + data.size() + " 个");
                }
                renderList();
                a.updateScopeBadge();   // 数据就绪后刷新底栏角标
            });
        }, "apps-page").start();
    }

    /** 按搜索词过滤渲染列表（activity 侧 renderAppsList() 为薄委托） */
    void renderList() {
        if (appsListContainer == null || a.appsData == null) return;
        appsListContainer.removeAllViews();
        String q = appsSearch.getText().toString().trim().toLowerCase();
        int shown = 0;
        for (SettingsActivity.AppEntry e : a.appsData) {
            if (!q.isEmpty()
                    && !e.label.toLowerCase().contains(q)
                    && !e.pkg.toLowerCase().contains(q)) {
                continue;
            }
            // 卡片间距：与首页一致（底部 12dp）
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            cardLp.bottomMargin = (int) (12 * a.dp);
            appsListContainer.addView(appCard(e), cardLp);
            shown++;
        }
        if (shown == 0) {
            TextView empty = new TextView(a);
            empty.setText(a.appsData.isEmpty()
                    ? "scope.list 暂无作用域应用\n往 xposed/scope.list 加一行包名并重新构建"
                    : "没有匹配的应用");
            empty.setTextSize(13);
            empty.setTextColor(a.SUB);
            empty.setGravity(Gravity.CENTER);
            empty.setLineSpacing(4 * a.dp, 1f);
            empty.setPadding(0, (int) (48 * a.dp), 0, (int) (48 * a.dp));
            appsListContainer.addView(empty);
        }
    }

    /** 单张应用卡：图标 + 名称/包名/版本/功能 + 状态胶囊（对标 FFV 应用页） */
    private LinearLayout appCard(SettingsActivity.AppEntry e) {
        LinearLayout card = a.makeCard();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding((int) (14 * a.dp), (int) (14 * a.dp),
                (int) (14 * a.dp), (int) (14 * a.dp));

        ImageView icon = new ImageView(a);
        if (e.installed) {
            try {
                icon.setImageDrawable(a.getPackageManager().getApplicationIcon(e.pkg));
            } catch (Throwable t) {
                icon.setImageResource(android.R.drawable.sym_def_app_icon);
            }
        } else {
            // 未安装：默认安卓机器人图标（与 FFV 一致）
            icon.setImageResource(android.R.drawable.sym_def_app_icon);
        }
        card.addView(icon, new LinearLayout.LayoutParams(
                (int) (48 * a.dp), (int) (48 * a.dp)));

        LinearLayout mid = new LinearLayout(a);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding((int) (12 * a.dp), 0, 0, 0);
        TextView name = new TextView(a);
        name.setText(e.label);
        name.setTextSize(15);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(e.installed ? a.INK : a.HINT);
        mid.addView(name);
        TextView pkg = new TextView(a);
        pkg.setText(e.pkg);
        pkg.setTextSize(11.5f);
        pkg.setTextColor(a.SUB);
        pkg.setPadding(0, (int) (2 * a.dp), 0, 0);
        mid.addView(pkg);
        if (e.installed) {
            TextView ver = new TextView(a);
            ver.setText("v" + e.version);
            ver.setTextSize(11.5f);
            ver.setTextColor(a.SUB);
            ver.setPadding(0, (int) (2 * a.dp), 0, 0);
            mid.addView(ver);
        }
        TextView desc = new TextView(a);
        desc.setText(e.desc);
        desc.setTextSize(12);
        desc.setTextColor(a.SUB);
        desc.setPadding(0, (int) (4 * a.dp), 0, 0);
        mid.addView(desc);
        card.addView(mid, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // 右侧勾选框：仅已安装应用（勾选=启用该应用 Hook；写入偏好供 Hook 侧 getRemotePreferences 读取）
        if (e.installed) {
            android.widget.CheckBox cb = new android.widget.CheckBox(a);
            cb.setChecked(a.isHookEnabled(e.pkg));
            cb.setButtonTintList(ColorStateList.valueOf(a.ACCENT));
            cb.setOnCheckedChangeListener((v, checked) -> {
                a.setHookEnabled(e.pkg, checked);
                a.updateScopeBadge();   // 角标跟随勾选实时变化
            });
            card.addView(cb, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        // 点击卡片进入细分选项液态玻璃弹窗
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> {
            AppDetailDialog.show(a, e.pkg, e.label, e.desc, a.darkMode, () -> {
                // 子项开关更新回调
                renderList();
            });
        });
        return card;
    }
}
