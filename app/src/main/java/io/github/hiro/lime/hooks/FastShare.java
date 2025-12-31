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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.hiro.lime.LimeOptions;

public class FastShare implements IHook {
    // 基於 15.9.3 的分析，LINE 的分享介面通常是這個
    private static final String SELECT_TARGET_ACTIVITY = "jp.naver.line.android.activity.selecttarget.SelectTargetActivity";

    @Override
    public void hook(LimeOptions options, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        
        // --- 功能 1: 訊息旁新增分享按鈕 (Hook 訊息長按選單顯示時) ---
        // 這裡我們 Hook 訊息長按彈出的選單或 ReactionList 所在的容器
        XposedHelpers.findAndHookMethod(ViewGroup.class, "onViewAdded", View.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View addedView = (View) param.args[0];
                String resName = "";
                try {
                    resName = addedView.getContext().getResources().getResourceEntryName(addedView.getId());
                } catch (Exception ignored) {}

                // 當長按選單容器 (或者 ReactionBar) 出現時，注入按鈕
                if ("chat_ui_reactionsheet_close".equals(resName)) {
                    injectQuickShareButton(addedView);
                }
            }
        });

        // --- 功能 2 & 3: 分享列表畫面儲存與自動勾選 ---
        XposedHelpers.findAndHookMethod(SELECT_TARGET_ACTIVITY, lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                injectShareControls(activity);
            }
        });
    }

    private void injectQuickShareButton(View anchorView) {
        Context context = anchorView.getContext();
        ViewGroup parent = (ViewGroup) anchorView.getParent();
        
        Button btn = new Button(context);
        btn.setText("快速轉傳");
        btn.setBackgroundColor(Color.parseColor("#00B900")); // LINE 綠
        btn.setTextColor(Color.WHITE);
        
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2);
        lp.gravity = Gravity.BOTTOM | Gravity.END;
        lp.setMargins(0, 0, dpToPx(16, context), dpToPx(80, context));
        
        btn.setOnClickListener(v -> {
            // 觸發 LINE 原生分享：這裡假設點擊後直接開起分享頁
            Intent intent = new Intent();
            intent.setClassName("jp.naver.line.android", SELECT_TARGET_ACTIVITY);
            // 注意：實際使用需從當前 ChatRoom 取得選中的 messageId 傳入
            v.getContext().startActivity(intent);
        });
        
        parent.addView(btn, lp);
    }

    private void injectShareControls(Activity activity) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER);

        Button saveBtn = new Button(activity);
        saveBtn.setText("儲存預設");
        
        Button autoBtn = new Button(activity);
        autoBtn.setText("自動勾選");

        saveBtn.setOnClickListener(v -> {
            try {
                // 利用反射取得 SelectTargetViewModel 中的選中列表
                // 這裡需要針對 15.9.3 尋找對應的成員變量，通常是 Set<String> 類型
                Object viewModel = XposedHelpers.getObjectField(activity, "viewModel");
                Set<String> selectedMids = (Set<String>) XposedHelpers.getObjectField(viewModel, "selectedTargetMids");
                
                if (selectedMids != null && !selectedMids.isEmpty()) {
                    saveShareList(activity, selectedMids);
                    Toast.makeText(activity, "已儲存 " + selectedMids.size() + " 個對象", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                XposedBridge.log("FastShare Save Error: " + e.getMessage());
            }
        });

        autoBtn.setOnClickListener(v -> {
            try {
                Set<String> savedMids = loadShareList(activity);
                Object viewModel = XposedHelpers.getObjectField(activity, "viewModel");
                
                // 調用 ViewModel 的選取方法
                for (String mid : savedMids) {
                    XposedHelpers.callMethod(viewModel, "selectTarget", mid, true);
                }
                Toast.makeText(activity, "自動勾選完成", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                XposedBridge.log("FastShare Auto Error: " + e.getMessage());
            }
        });

        layout.addView(saveBtn);
        layout.addView(autoBtn);

        // 將控制列加入分享頁面頂部
        ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
        decorView.addView(layout, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
    }

    // --- 資料持久化 (仿照 ChatList 邏輯) ---
    private void saveShareList(Context context, Set<String> mids) {
        File file = new File(context.getFilesDir(), "fast_share_list.txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (String mid : mids) {
                writer.write(mid);
                writer.newLine();
            }
        } catch (IOException ignored) {}
    }

    private Set<String> loadShareList(Context context) {
        Set<String> mids = new HashSet<>();
        File file = new File(context.getFilesDir(), "fast_share_list.txt");
        if (!file.exists()) return mids;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                mids.add(line.trim());
            }
        } catch (IOException ignored) {}
        return mids;
    }
}
