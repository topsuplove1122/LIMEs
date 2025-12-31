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

        // --- 功能 1: 在訊息旁邊生成按鈕 ---
        XposedHelpers.findAndHookMethod(ViewGroup.class, "onViewAdded", View.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View addedView = (View) param.args[0];
                int id = addedView.getId();
                if (id <= 0) return; // 解決 Resources$NotFoundException

                try {
                    String resName = addedView.getContext().getResources().getResourceEntryName(id);
                    // 根據 Log，這是每則訊息的最外層容器
                    if ("chat_ui_row_message_layout".equals(resName)) {
                        injectSideShareButton(addedView);
                    }
                } catch (Exception ignored) {}
            }
        });

        // --- 功能 2 & 3: 監控所有 Activity 來尋找分享介面 ---
        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                String className = activity.getClass().getName();
                
                // 只要 Activity 名字包含 Picker 或是 Share 且在 LINE 包名下
                if (className.contains("jp.naver.line") && 
                   (className.contains("Picker") || className.contains("Share"))) {
                    injectSharePickerControls(activity);
                }
            }
        });
    }

    private void injectSideShareButton(View messageRow) {
        Context context = messageRow.getContext();
        ViewGroup parent = (ViewGroup) messageRow;

        if (parent.findViewWithTag("lime_side_share") != null) return;

        Button btn = new Button(context);
        btn.setTag("lime_side_share");
        btn.setText("分享");
        btn.setTextSize(10);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.parseColor("#8800B900")); // 半透明 LINE 綠
        
        // 將按鈕放在訊息容器的右下角
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dpToPx(40, context), dpToPx(30, context));
        lp.gravity = Gravity.BOTTOM | Gravity.END;
        lp.setMargins(0, 0, dpToPx(5, context), dpToPx(5, context));

        btn.setOnClickListener(v -> {
            try {
                // 使用更通用的 Intent，避免類名找不到導致閃退
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, "Quick Share");
                intent.setPackage("jp.naver.line.android");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e) {
                XposedBridge.log("Lime: Share Intent Failed: " + e.getMessage());
            }
        });

        parent.addView(btn, lp);
        btn.bringToFront();
    }

    private void injectSharePickerControls(Activity activity) {
        ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
        if (decorView.findViewWithTag("lime_picker_panel") != null) return;

        LinearLayout panel = new LinearLayout(activity);
        panel.setTag("lime_picker_panel");
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setBackgroundColor(Color.parseColor("#DDDDDD"));

        Button save = new Button(activity); save.setText("儲存預設");
        Button auto = new Button(activity); auto.setText("自動勾選");

        save.setOnClickListener(v -> {
            try {
                // 15.9.0 混淆適配：嘗試從常見變量名中抓取 ViewModel 或選中集
                Object viewModel = findFieldInObject(activity, "viewModel");
                if (viewModel != null) {
                    Set<String> selected = (Set<String>) findFieldInObject(viewModel, "selectedTargetMids");
                    if (selected != null && !selected.isEmpty()) {
                        saveMids(activity, selected);
                        Toast.makeText(activity, "已儲存 " + selected.size() + " 個對象", Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (Exception e) {
                Toast.makeText(activity, "儲存功能需適配混淆名", Toast.LENGTH_SHORT).show();
            }
        });

        auto.setOnClickListener(v -> {
            try {
                Set<String> saved = loadMids(activity);
                Object viewModel = findFieldInObject(activity, "viewModel");
                if (viewModel != null && !saved.isEmpty()) {
                    for (String mid : saved) {
                        // 嘗試執行選中動作 (15.9.0 可能的方法名)
                        XposedHelpers.callMethod(viewModel, "selectTarget", mid, true);
                    }
                    Toast.makeText(activity, "嘗試自動勾選...", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception ignored) {}
        });

        panel.addView(save, new LinearLayout.LayoutParams(0, -2, 1f));
        panel.addView(auto, new LinearLayout.LayoutParams(0, -2, 1f));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        decorView.addView(panel, lp);
        panel.bringToFront();
    }

    // 輔助方法：處理混淆後的欄位尋找
    private Object findFieldInObject(Object obj, String fieldName) {
        try { return XposedHelpers.getObjectField(obj, fieldName); } 
        catch (Exception e) {
            // 如果找不到，這裡可以擴充嘗試 a, b, c 等名字
            return null;
        }
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
