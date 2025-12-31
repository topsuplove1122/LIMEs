package io.github.hiro.lime.hooks;

import static io.github.hiro.lime.Utils.dpToPx;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
    private static final String FILE_NAME = "fast_share_configs.txt";

    @Override
    public void hook(LimeOptions options, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!options.fastShare.checked) return;

        // --- 功能 1: 修正按鈕位置與動作 ---
        XposedHelpers.findAndHookMethod(ViewGroup.class, "onViewAdded", View.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View addedView = (View) param.args[0];
                if (addedView.getId() <= 0) return;

                String resName = "";
                try { resName = addedView.getContext().getResources().getResourceEntryName(addedView.getId()); } catch (Exception ignored) {}

                // 只針對訊息列表中的容器注入
                if ("chat_ui_message_container".equals(resName)) {
                    ViewGroup parent = (ViewGroup) addedView.getParent();
                    // 確保是在列表 Row 裡面，而不是輸入框旁
                    if (parent instanceof LinearLayout && parent.findViewWithTag("lime_side_share") == null) {
                        injectSideButton(addedView, parent);
                    }
                }
            }
        });

        // --- 功能 2 & 3: 修正閃退與控制項 ---
        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                String className = activity.getClass().getName();
                if (className.contains("SharePickerActivity")) {
                    injectSharePickerControls(activity);
                }
            }
        });
    }

    private void injectSideButton(View anchor, ViewGroup parent) {
        Context context = anchor.getContext();
        Button btn = new Button(context);
        btn.setTag("lime_side_share");
        btn.setText("轉傳");
        btn.setTextSize(8);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.parseColor("#99000000")); // 深色半透明

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpToPx(30, context), dpToPx(24, context));
        lp.gravity = Gravity.BOTTOM;
        lp.setMargins(dpToPx(2, context), 0, dpToPx(2, context), dpToPx(2, context));

        btn.setOnClickListener(v -> {
            // 模擬點擊系統分享，這會自動開啟 LINE 的好友選擇介面
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, "Quick Forward");
            sendIntent.setType("text/plain");
            sendIntent.setPackage("jp.naver.line.android");
            Intent shareIntent = Intent.createChooser(sendIntent, null);
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(shareIntent);
        });

        parent.addView(btn, lp);
    }

    private void injectSharePickerControls(Activity activity) {
        ViewGroup root = (ViewGroup) activity.getWindow().getDecorView();
        if (root.findViewWithTag("lime_picker_panel") != null) return;

        LinearLayout panel = new LinearLayout(activity);
        panel.setTag("lime_picker_panel");
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setBackgroundColor(Color.DKGRAY);

        Button save = new Button(activity); save.setText("儲存"); save.setTextColor(Color.WHITE);
        Button auto = new Button(activity); auto.setText("自動"); auto.setTextColor(Color.WHITE);

        save.setOnClickListener(v -> {
            Object vm = findPossibleViewModel(activity);
            if (vm != null) {
                Set<String> selected = findSelectedMids(vm);
                if (!selected.isEmpty()) {
                    saveMids(activity, selected);
                    Toast.makeText(activity, "已存 " + selected.size() + " 個對象", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(activity, "請先手動勾選", Toast.LENGTH_SHORT).show();
                }
            }
        });

        auto.setOnClickListener(v -> {
            Object vm = findPossibleViewModel(activity);
            Set<String> saved = loadMids(activity);
            if (vm != null && !saved.isEmpty()) {
                for (String mid : saved) {
                    try {
                        // 嘗試多種可能的方法名以防混淆
                        XposedHelpers.callMethod(vm, "selectTarget", mid, true);
                    } catch (Exception e) {
                        try { XposedHelpers.callMethod(vm, "a", mid, true); } catch (Exception ignored) {}
                    }
                }
                Toast.makeText(activity, "執行自動勾選", Toast.LENGTH_SHORT).show();
            }
        });

        panel.addView(save, new LinearLayout.LayoutParams(0, -2, 1f));
        panel.addView(auto, new LinearLayout.LayoutParams(0, -2, 1f));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        root.addView(panel, lp);
    }

    // --- 暴力搜尋混淆後的對象 (15.9.0 關鍵) ---
    private Object findPossibleViewModel(Activity act) {
        for (Field f : act.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            try {
                Object o = f.get(act);
                if (o != null && o.getClass().getName().contains("ViewModel")) return o;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private Set<String> findSelectedMids(Object vm) {
        for (Field f : vm.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            try {
                Object o = f.get(vm);
                if (o instanceof Set) return (Set<String>) o;
            } catch (Exception ignored) {}
        }
        return new HashSet<>();
    }

    private void saveMids(Context c, Set<String> mids) {
        try (PrintWriter p = new PrintWriter(new FileWriter(new File(c.getFilesDir(), FILE_NAME)))) {
            for (String m : mids) p.println(m);
        } catch (Exception ignored) {}
    }

    private Set<String> loadMids(Context c) {
        Set<String> mids = new HashSet<>();
        File f = new File(c.getFilesDir(), FILE_NAME);
        if (!f.exists()) return mids;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String l; while ((l = r.readLine()) != null) mids.add(l.trim());
        } catch (Exception ignored) {}
        return mids;
    }
}
