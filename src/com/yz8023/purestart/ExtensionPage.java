package com.yz8023.purestart;

import android.graphics.Typeface;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 「拓展」页组装（P1-2 自 SettingsActivity 拆出，行为不变）。
 *
 * 内容：激活指引（LSPosed 步骤/排查）、数据管理（备份/恢复 JSON 经 SAF）、
 * 关于（开源地址/版本/构建时间）。文件选择结果仍由 activity onActivityResult
 * 分发回 exportPrefs()/importPrefs()。
 */
final class ExtensionPage {
    private final SettingsActivity a;

    ExtensionPage(SettingsActivity a) {
        this.a = a;
    }

    View build() {
        ScrollView scroll = new ScrollView(a);
        scroll.setFillViewport(true);

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (22 * a.dp);
        box.setPadding(pad, (int) (26 * a.dp), pad, (int) (108 * a.dp));
        scroll.addView(box, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(a);
        title.setText("拓展");
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(a.INK);
        box.addView(title);
        TextView sub = new TextView(a);
        sub.setText("激活指引 · 数据管理 · 关于模块");
        sub.setTextSize(14);
        sub.setTextColor(a.SUB);
        sub.setPadding(0, (int) (6 * a.dp), 0, (int) (16 * a.dp));
        box.addView(sub);

        // ===== 分组：激活指引 =====
        box.addView(a.sectionHeader("激活指引"));
        LinearLayout guide = a.makeCard();
        guide.setOrientation(LinearLayout.VERTICAL);
        guide.setPadding((int) (18 * a.dp), (int) (16 * a.dp), (int) (18 * a.dp), (int) (16 * a.dp));
        String[] steps = {
                "① 打开 LSPosed →「模块」→ 找到 PureStart 并启用",
                "② 在「作用域」中勾选需要净化的应用（本模块已内置推荐作用域）",
                "③ 强制停止或重启勾选的目标应用（无需重启手机）",
                "④ 回到本模块「应用」页确认对应应用已显示启用状态",
        };
        for (String s : steps) {
            TextView t = new TextView(a);
            t.setText(s);
            t.setTextSize(13.5f);
            t.setTextColor(a.INK);
            t.setLineSpacing(3 * a.dp, 1f);
            t.setPadding(0, (int) (3 * a.dp), 0, (int) (3 * a.dp));
            guide.addView(t);
        }
        TextView faq = new TextView(a);
        faq.setText("排查：拦截无效时请依次确认模块已启用、作用域已勾选、目标应用已重启；"
                + "本模块基于 libxposed API 102，需 LSPosed 1.9.2+ 等现代框架。");
        faq.setTextSize(12);
        faq.setTextColor(a.SUB);
        faq.setLineSpacing(3 * a.dp, 1f);
        faq.setPadding(0, (int) (10 * a.dp), 0, 0);
        guide.addView(faq);
        a.addCard(box, guide);

        // ===== 分组：数据管理 =====
        box.addView(a.sectionHeader("数据管理"));
        LinearLayout backupCard = a.setRow(R.drawable.ic_set_refresh, "备份配置",
                "导出全部应用开关与偏好为 JSON 文件", null);
        backupCard.setOnClickListener(v -> {
            try {
                android.content.Intent i = new android.content.Intent(
                        android.content.Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(android.content.Intent.EXTRA_TITLE, "purestart-backup.json");
                a.startActivityForResult(i, 41);
            } catch (Throwable t) {
                toast("无法打开文件选择器：" + t.getMessage());
            }
        });
        a.addCard(box, backupCard);

        LinearLayout restoreCard = a.setRow(R.drawable.ic_set_drop, "恢复配置",
                "从备份 JSON 导入应用开关与偏好", null);
        restoreCard.setOnClickListener(v -> {
            try {
                android.content.Intent i = new android.content.Intent(
                        android.content.Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                a.startActivityForResult(i, 42);
            } catch (Throwable t) {
                toast("无法打开文件选择器：" + t.getMessage());
            }
        });
        a.addCard(box, restoreCard);

        // ===== 分组：关于 =====
        box.addView(a.sectionHeader("关于"));
        LinearLayout about = a.makeCard();
        about.setOrientation(LinearLayout.VERTICAL);
        about.setPadding((int) (18 * a.dp), (int) (16 * a.dp), (int) (18 * a.dp), (int) (16 * a.dp));
        String[] infos = {
                "开源地址", "github.com/yz8023/PureStart",
                "模块版本", "v" + a.versionName() + " (ModKit Edition)",
                "Hook 接口", "libxposed API 102（Modern Xposed）",
                "构建时间", BuildInfo.BUILD_TIME,
        };
        for (int i = 0; i < infos.length; i += 2) {
            TextView line = new TextView(a);
            line.setText(infos[i] + "：" + infos[i + 1]);
            line.setTextSize(13);
            line.setTextColor(i % 4 == 0 ? a.SUB : a.INK);
            line.setPadding(0, (int) (3 * a.dp), 0, (int) (3 * a.dp));
            about.addView(line);
        }
        a.addCard(box, about);
        return scroll;
    }

    private void toast(String msg) {
        android.widget.Toast.makeText(a, msg, android.widget.Toast.LENGTH_SHORT).show();
    }

    /** 备份：全部偏好导出为 JSON */
    void exportPrefs(android.net.Uri uri) {
        try {
            org.json.JSONObject jo = new org.json.JSONObject();
            java.util.Map<String, ?> all = a.hookPrefs().getAll();
            for (java.util.Map.Entry<String, ?> e : all.entrySet()) {
                Object v = e.getValue();
                if (v instanceof Boolean) jo.put(e.getKey(), (Boolean) v);
                else if (v instanceof Integer) jo.put(e.getKey(), (Integer) v);
                else if (v instanceof Long) jo.put(e.getKey(), (Long) v);
                else if (v instanceof Float) jo.put(e.getKey(), (Float) v);
                else if (v instanceof String) jo.put(e.getKey(), (String) v);
            }
            java.io.OutputStream os = a.getContentResolver().openOutputStream(uri);
            if (os == null) { toast("导出失败：无法写入"); return; }
            os.write(jo.toString().getBytes("UTF-8"));
            os.close();
            toast("已导出 " + jo.length() + " 项配置");
        } catch (Throwable t) {
            toast("导出失败：" + t.getMessage());
        }
    }

    /** 恢复：从 JSON 导入偏好并重建界面 */
    void importPrefs(android.net.Uri uri) {
        try {
            java.io.InputStream is = a.getContentResolver().openInputStream(uri);
            if (is == null) { toast("导入失败：无法读取"); return; }
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            is.close();
            org.json.JSONObject jo = new org.json.JSONObject(
                    new String(bos.toByteArray(), "UTF-8"));
            android.content.SharedPreferences.Editor ed = a.hookPrefs().edit();
            java.util.Iterator<String> it = jo.keys();
            int count = 0;
            while (it.hasNext()) {
                String k = it.next();
                Object v = jo.get(k);
                if (v instanceof Boolean) ed.putBoolean(k, (Boolean) v);
                else if (v instanceof Integer) ed.putInt(k, (Integer) v);
                else if (v instanceof Long) ed.putLong(k, (Long) v);
                else if (v instanceof Float) ed.putFloat(k, (Float) v);
                else if (v instanceof String) ed.putString(k, (String) v);
                else continue;
                count++;
            }
            ed.apply();
            toast("已导入 " + count + " 项配置，界面即将刷新");
            a.barWrap.postDelayed(a::recreate, 600);
        } catch (Throwable t) {
            toast("导入失败：" + t.getMessage());
        }
    }
}
