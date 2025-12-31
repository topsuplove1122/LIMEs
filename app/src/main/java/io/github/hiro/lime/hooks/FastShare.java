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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

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

        // --- 功能 1: 在訊息「旁邊」生成按鈕 ---
        XposedHelpers.findAndHookMethod(ViewGroup.class, "onViewAdded", View.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View addedView = (View) param.args[0];
                int id = addedView.getId();
                if (id <= 0) return;

                try {
                    String resName = addedView.getContext().getResources().getResourceEntryName(id);
                    // Hook 住訊息氣泡的容器，並在其父節點添加按鈕
                    if ("chat_ui_message_container".equals(resName)) {
                        injectSideButton(addedView);
                    }
                } catch (Exception ignored) {}
            }
        });

        // --- 功能 2 & 3: 暴力 Hook 所有 Activity 以捕捉分享介面 ---
        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                String name = activity.getClass().getName();
                
                // 只要類名包含 Share 或 Picker，就嘗試注入控制面板
                if (name.contains("Share") || name.contains("Picker")) {
                    injectSharePickerControls(activity);
                }
            }
        });
    }

    private void injectSideButton(View messageContainer) {
        // 取得訊息氣泡的父節點 (通常是一個橫向的 LinearLayout)
        ViewGroup parent = (ViewGroup) messageContainer.getParent();
        if (parent == null || parent.findViewWithTag("lime_side_share") != null) return;

        Context context = messageContainer.getContext();
        Button btn = new Button(context);
        btn.setTag("lime_side_share");
        btn.setText("轉傳");
        btn.setTextSize(9);
        btn.setTextColor(Color.WHITE);
        btn.setPadding(0, 0, 0, 0);
        btn.setBackgroundColor(Color.parseColor("#AA00B900")); // 半透明綠色

        // 設定按鈕大小與間距，放在氣泡旁邊
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpToPx(35, context), dpToPx(25, context));
        lp.gravity = Gravity.CENTER_VERTICAL;
        lp.setMargins(dpToPx(4, context), 0, dpToPx(4, context), 0);

        btn.setOnClickListener(v -> {
            try {
                // 核心：直接啟動 LINE 的轉傳選擇介面
                Intent intent = new Intent();
                // 15.9.0 嘗試使用這個內部 Action，它會直接開啟「選擇聊天室」
                intent.setClassName("jp.naver.line.android", "jp.naver.line.android.activity.share.SharePickerActivity");
                intent.putExtra("share_type", 1); // 內部定義的分享類型
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                v.getContext().startActivity(intent);
            } catch (Exception e) {
                // 如果失敗，嘗試備用方法
                Toast.makeText(context, "請手動長按分享 (適配中)", Toast.LENGTH_SHORT).show();
            }
        });

        parent.addView(btn, lp); // 將按鈕加入橫向佈局中
    }

    private void injectSharePickerControls(Activity activity) {
        // 取得最頂層視窗
        ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
        if (decorView.findViewWithTag("lime_picker_panel") != null) return;

        LinearLayout panel = new LinearLayout(activity);
        panel.setTag("lime_picker_panel");
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setBackgroundColor(Color.parseColor("#F0F0F0"));
        panel.setElevation(dpToPx(10, activity));

        Button save = new Button(activity); save.setText("儲存預設");
        Button auto = new Button(activity); auto.setText("自動勾選");

        save.setOnClickListener(v -> {
            try {
                // 15.9.0 混淆適配
                Object viewModel = XposedHelpers.getObjectField(activity, "viewModel");
                Set<String> selected = (Set<String>) XposedHelpers.getObjectField(viewModel, "selectedTargetMids");
                if (selected != null && !selected.isEmpty()) {
                    saveMids(activity, selected);
                    Toast.makeText(activity, "已設定預設分享對象", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                XposedBridge.log("Lime: Save failed - " + e.getMessage());
            }
        });

        auto.setOnClickListener(v -> {
            try {
                Set<String> saved = loadMids(activity);
                Object viewModel = XposedHelpers.getObjectField(activity, "viewModel");
                if (viewModel != null && !saved.isEmpty()) {
                    for (String mid : saved) {
                        // 調用 LINE 內部的選擇方法
                        XposedHelpers.callMethod(viewModel, "selectTarget", mid, true);
                    }
                }
            } catch (Exception ignored) {}
        });

        panel.addView(save, new LinearLayout.LayoutParams(0, -2, 1f));
        panel.addView(auto, new LinearLayout.LayoutParams(0, -2, 1f));

        // 將面板強制釘在螢幕底部
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        decorView.addView(panel, lp);
        panel.bringToFront();
    }

    private void saveMids(Context c, Set<String> mids) throws IOException {
        File f = new File(c.getFilesDir(), FILE_NAME);
        try (BufferedWriter w = new BufferedWriter(new FileWriter(f))) {
            for (String m : mids) w.write(m + "\n");
        }
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
