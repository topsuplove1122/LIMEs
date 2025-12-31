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
    private static final String SHARE_PICKER = "jp.naver.line.android.activity.share.SharePickerActivity";
    private static final String FILE_NAME = "fast_share_configs.txt";

    @Override
    public void hook(LimeOptions options, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!options.fastShare.checked) return;

        // --- 功能 1: 修正長按選單按鈕 ---
        XposedHelpers.findAndHookMethod(ViewGroup.class, "onViewAdded", View.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View addedView = (View) param.args[0];
                int id = addedView.getId();
                if (id == View.NO_ID) return;

                String resName = addedView.getContext().getResources().getResourceEntryName(id);
                
                // 根據 Log，選單最外層容器是這個
                if ("chat_ui_message_context_menu_action_content_container".equals(resName)) {
                    injectQuickShareButton(addedView);
                }
            }
        });

        // --- 功能 2 & 3: 針對 SharePickerActivity 注入 ---
        XposedHelpers.findAndHookMethod(SHARE_PICKER, lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                injectShareControls((Activity) param.thisObject);
            }
        });
    }

    private void injectQuickShareButton(View menuContainer) {
        Context context = menuContainer.getContext();
        ViewGroup parent = (ViewGroup) menuContainer;

        // 防止在同一個菜單內重複添加
        if (parent.findViewWithTag("lime_fast_share") != null) return;

        Button btn = new Button(context);
        btn.setTag("lime_fast_share");
        btn.setText("分享至其他聊天室");
        btn.setBackgroundColor(Color.parseColor("#06C755")); // LINE 綠
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(14);
        btn.setAllCaps(false);

        // 放置在菜單的最上方
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dpToPx(45, context));
        lp.setMargins(dpToPx(10, context), dpToPx(5, context), dpToPx(10, context), dpToPx(5, context));

        btn.setOnClickListener(v -> {
            // 觸發 LINE 原生分享選擇器
            Intent intent = new Intent();
            intent.setClassName("jp.naver.line.android", SHARE_PICKER);
            // 這裡模擬分享動作，通常 LINE 會檢查 Intent 是否有內容
            intent.putExtra("android.intent.extra.TEXT", "FastShare Trigger"); 
            v.getContext().startActivity(intent);
        });

        // 加入到菜單容器的第一個位置
        parent.addView(btn, 0, lp);
    }

    private void injectShareControls(Activity activity) {
        ViewGroup root = (ViewGroup) activity.getWindow().getDecorView();
        if (root.findViewWithTag("lime_control_panel") != null) return;

        LinearLayout panel = new LinearLayout(activity);
        panel.setTag("lime_control_panel");
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setBackgroundColor(Color.parseColor("#EEEEEE"));
        panel.setPadding(10, 10, 10, 10);

        Button save = new Button(activity); save.setText("設定預設");
        Button auto = new Button(activity); auto.setText("一鍵勾選");

        save.setOnClickListener(v -> {
            try {
                // 獲取當前選中的對象 (需分析具體混淆名，暫以選中的 UI 狀態模擬)
                // 在 SharePickerActivity 中通常有個 viewModel 儲存選中狀態
                Object viewModel = XposedHelpers.getObjectField(activity, "viewModel");
                Set<String> selected = (Set<String>) XposedHelpers.getObjectField(viewModel, "selectedTargetMids");
                if (selected != null && !selected.isEmpty()) {
                    saveMids(activity, selected);
                    Toast.makeText(activity, "已儲存預設分享對象", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(activity, "請先手動勾選欲儲存的聊天室", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(activity, "儲存失敗：需適配混淆名", Toast.LENGTH_SHORT).show();
            }
        });

        auto.setOnClickListener(v -> {
            try {
                Set<String> saved = loadMids(activity);
                Object viewModel = XposedHelpers.getObjectField(activity, "viewModel");
                if (saved != null && viewModel != null) {
                    for (String mid : saved) {
                        // 調用 LINE 的選取方法 (通常是 a 或 selectTarget)
                        XposedHelpers.callMethod(viewModel, "selectTarget", mid, true);
                    }
                    Toast.makeText(activity, "自動勾選完成", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                XposedBridge.log("Lime: Auto select failed " + e.getMessage());
            }
        });

        panel.addView(save, new LinearLayout.LayoutParams(0, -2, 1));
        panel.addView(auto, new LinearLayout.LayoutParams(0, -2, 1));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        root.addView(panel, lp);
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
