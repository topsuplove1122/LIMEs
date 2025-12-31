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

import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.hiro.lime.LimeOptions;

public class FastShare implements IHook {

    @Override
    public void hook(LimeOptions options, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!options.fastShare.checked) return;
        XposedBridge.log("Lime: FastShare Hook Starting...");

        // 1. 功能一：監控所有 View 添加動作，尋找彈出選單
        XposedHelpers.findAndHookMethod(ViewGroup.class, "onViewAdded", View.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View addedView = (View) param.args[0];
                String resName = "";
                try {
                    resName = addedView.getContext().getResources().getResourceEntryName(addedView.getId());
                } catch (Exception ignored) {}

                // 調試用：如果你長按訊息，看 Log 裡會出現什麼 ID
                if (resName != null && !resName.isEmpty()) {
                    // XposedBridge.log("Lime: View Added ID -> " + resName); 
                }

                // 嘗試多個可能的選單關閉按鈕 ID (15.9.0 可能變動)
                if ("chat_ui_reactionsheet_close".equals(resName) || 
                    "common_close_button".equals(resName) || 
                    resName.contains("reactionsheet")) {
                    XposedBridge.log("Lime: Found menu anchor: " + resName);
                    injectQuickShareButton(addedView);
                }
            }
        });

        // 2. 功能二 & 三：Hook 分享介面
        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                String name = activity.getClass().getName();
                
                // 只要 Activity 名字包含 SelectTarget 或是 Share，我們就嘗試注入
                if (name.contains("SelectTarget") || name.contains("ShareActivity")) {
                    XposedBridge.log("Lime: Target Activity Detected: " + name);
                    injectShareControls(activity);
                }
            }
        });
    }

    private void injectQuickShareButton(View anchorView) {
        try {
            Context context = anchorView.getContext();
            ViewGroup parent = (ViewGroup) anchorView.getParent();
            if (parent == null) return;

            // 避免重複添加
            if (parent.findViewWithTag("lime_quick_share") != null) return;

            Button btn = new Button(context);
            btn.setTag("lime_quick_share");
            btn.setText("快速轉傳");
            btn.setBackgroundColor(Color.parseColor("#06C755"));
            btn.setTextColor(Color.WHITE);

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, dpToPx(40, context));
            lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
            lp.topMargin = dpToPx(50, context); // 調整位置，不要擋到原有的

            btn.setOnClickListener(v -> Toast.makeText(context, "快速轉傳點擊成功", Toast.LENGTH_SHORT).show());

            parent.addView(btn, lp);
            btn.bringToFront();
            XposedBridge.log("Lime: QuickShare Button Injected Successfully");
        } catch (Exception e) {
            XposedBridge.log("Lime: Injection Error -> " + e.getMessage());
        }
    }

    private void injectShareControls(Activity activity) {
        try {
            ViewGroup root = (ViewGroup) activity.getWindow().getDecorView();
            
            // 避免重複添加
            if (root.findViewWithTag("lime_share_panel") != null) return;

            LinearLayout panel = new LinearLayout(activity);
            panel.setTag("lime_share_panel");
            panel.setOrientation(LinearLayout.HORIZONTAL);
            panel.setBackgroundColor(Color.LTGRAY);
            panel.setAlpha(0.9f);

            Button save = new Button(activity);
            save.setText("儲存");
            Button auto = new Button(activity);
            auto.setText("自動勾選");

            panel.addView(save, new LinearLayout.LayoutParams(0, -2, 1));
            panel.addView(auto, new LinearLayout.LayoutParams(0, -2, 1));

            // 將面板放在最頂端
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
            lp.topMargin = dpToPx(100, activity); // 避開狀態欄

            root.addView(panel, lp);
            panel.bringToFront();
            XposedBridge.log("Lime: Share Controls Panel Injected");
        } catch (Exception e) {
            XposedBridge.log("Lime: Panel Injection Error -> " + e.getMessage());
        }
    }
}
