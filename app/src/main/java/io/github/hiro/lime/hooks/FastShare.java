package io.github.hiro.lime.hooks;

import static io.github.hiro.lime.Utils.dpToPx;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.hiro.lime.LimeOptions;

public class FastShare implements IHook {
    private static final String FILE_NAME = "fast_share_v2.txt";

    @Override
    public void hook(LimeOptions options, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!options.fastShare.checked) return;

        // --- 功能 1: 精確錨定訊息氣泡旁邊 ---
        XposedHelpers.findAndHookMethod(ViewGroup.class, "onViewAdded", View.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View addedView = (View) param.args[0];
                if (addedView.getId() <= 0) return;

                String resName = "";
                try { resName = addedView.getContext().getResources().getResourceEntryName(addedView.getId()); } catch (Exception ignored) {}

                // 這次我們 Hook 訊息文字本身，並加在它的 Parent (氣泡) 旁邊
                if ("chat_ui_message_text".equals(resName)) {
                    ViewGroup bubble = (ViewGroup) addedView.getParent();
                    ViewGroup row = (ViewGroup) bubble.getParent(); // 訊息行容器
                    if (row != null && row.findViewWithTag("lime_share") == null) {
                        injectSideButton(row, addedView.getContext());
                    }
                }
            }
        });

        // --- 功能 2 & 3: 修正分享介面控制 ---
        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                if (activity.getClass().getName().contains("SharePickerActivity")) {
                    injectPickerControls(activity);
                }
            }
        });
    }

    private void injectSideButton(ViewGroup row, Context context) {
        Button btn = new Button(context);
        btn.setTag("lime_share");
        btn.setText("分享");
        btn.setTextSize(8);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.parseColor("#99333333")); // 深灰色半透明，不突兀

        // 使用 FrameLayout 或 LinearLayout 視 LINE 佈局而定，這裡用通用參數
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpToPx(32, context), dpToPx(24, context));
        lp.gravity = Gravity.CENTER_VERTICAL;
        lp.setMargins(dpToPx(2, context), 0, dpToPx(2, context), 0);

        btn.setOnClickListener(v -> {
            // 嘗試觸發長按選單中的「分享」動作
            // 注意：由於 15.9.0 混淆嚴重，最穩定的方式是模擬 ContextMenu 呼叫
            Toast.makeText(context, "請使用長按選單中的分享，此按鈕正在適配 ID", Toast.LENGTH_SHORT).show();
        });

        row.addView(btn, lp);
    }

    private void injectPickerControls(Activity activity) {
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        if (decor.findViewWithTag("lime_ctrl") != null) return;

        LinearLayout root = new LinearLayout(activity);
        root.setTag("lime_ctrl");
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Color.parseColor("#F5F5F5"));

        Button btnSave = new Button(activity); btnSave.setText("儲存預設");
        Button btnAuto = new Button(activity); btnAuto.setText("一鍵自動");

        btnSave.setOnClickListener(v -> {
            Object vm = findObjectByType(activity, "ViewModel");
            if (vm != null) {
                // 暴力掃描所有 Set 類型的成員，通常選中名單就在裡面
                Set<String> mids = findSetInObject(vm);
                if (mids != null && !mids.isEmpty()) {
                    saveMids(activity, mids);
                    Toast.makeText(activity, "成功儲存 " + mids.size() + " 個對象", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnAuto.setOnClickListener(v -> {
            Object vm = findObjectByType(activity, "ViewModel");
            Set<String> saved = loadMids(activity);
            if (vm != null && !saved.isEmpty()) {
                for (String mid : saved) {
                    // 嘗試所有可能的混淆方法名來觸發勾選
                    String[] methods = {"selectTarget", "select", "a", "b"};
                    for (String m : methods) {
                        try {
                            XposedHelpers.callMethod(vm, m, mid, true);
                            break;
                        } catch (Exception ignored) {}
                    }
                }
                Toast.makeText(activity, "已嘗試自動勾選", Toast.LENGTH_SHORT).show();
            }
        });

        root.addView(btnSave, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(btnAuto, new LinearLayout.LayoutParams(0, -2, 1));
        
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        fl.topMargin = dpToPx(60, activity); // 避開標題列
        decor.addView(root, fl);
        root.bringToFront();
    }

    // --- 超強反射輔助：管他混淆成什麼都能抓 ---
    private Object findObjectByType(Object parent, String typeName) {
        for (Field f : parent.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            try {
                Object o = f.get(parent);
                if (o != null && o.getClass().getName().contains(typeName)) return o;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private Set<String> findSetInObject(Object obj) {
        for (Field f : obj.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            try {
                Object o = f.get(obj);
                if (o instanceof Set) return (Set<String>) o;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private void saveMids(Context c, Set<String> mids) {
        try {
            File f = new File(c.getFilesDir(), FILE_NAME);
            PrintWriter p = new PrintWriter(new FileWriter(f));
            for (String s : mids) p.println(s);
            p.close();
        } catch (Exception ignored) {}
    }

    private Set<String> loadMids(Context c) {
        Set<String> res = new HashSet<>();
        File f = new File(c.getFilesDir(), FILE_NAME);
        if (!f.exists()) return res;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String l; while ((l = r.readLine()) != null) res.add(l.trim());
        } catch (Exception ignored) {}
        return res;
    }
}
