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

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.hiro.lime.LimeOptions;

public class FastShare implements IHook {

    @Override
    public void hook(LimeOptions options, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!options.fastShare.checked) return;
        XposedBridge.log("Lime: FastShare Monitoring Start...");

        // --- 策略 A: 監控所有 Activity 的啟動 ---
        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                String className = activity.getClass().getName();
                
                // 只要是 LINE 的 Activity 我們就印出來看看
                if (className.contains("jp.naver.line")) {
                    XposedBridge.log("Lime: Activity Started -> " + className);
                }

                // 強制在「任何」疑似分享的頁面注入
                if (className.toLowerCase().contains("select") || className.toLowerCase().contains("share")) {
                    injectShareControls(activity);
                }
            }
        });

        // --- 策略 B: 捕捉所有新增的 View ID (幫助找出 15.9.0 的選單 ID) ---
        XposedHelpers.findAndHookMethod(ViewGroup.class, "onViewAdded", View.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View addedView = (View) param.args[0];
                try {
                    int id = addedView.getId();
                    if (id != View.NO_ID) {
                        String resName = addedView.getContext().getResources().getResourceEntryName(id);
                        // 如果 ID 包含 chat_ui 或 reaction 就印出來
                        if (resName.contains("chat_ui") || resName.contains("reaction")) {
                            XposedBridge.log("Lime: Detected View ID -> " + resName);
                            
                            // 如果抓到疑似長按選單的容器，直接注入
                            if (resName.contains("reactionsheet") || resName.contains("menu")) {
                                injectQuickShareButton(addedView);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    private void injectQuickShareButton(View anchorView) {
        try {
            ViewGroup parent = (ViewGroup) anchorView.getParent();
            if (parent == null || parent.findViewWithTag("lime_fast") != null) return;

            Button btn = new Button(anchorView.getContext());
            btn.setTag("lime_fast");
            btn.setText("快速轉傳");
            btn.setBackgroundColor(Color.RED); // 用紅色最明顯

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2);
            lp.gravity = Gravity.CENTER;
            parent.addView(btn, lp);
            btn.bringToFront();
        } catch (Exception ignored) {}
    }

    private void injectShareControls(Activity activity) {
        try {
            ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
            if (decorView.findViewWithTag("lime_panel") != null) return;

            LinearLayout panel = new LinearLayout(activity);
            panel.setTag("lime_panel");
            panel.setBackgroundColor(Color.BLUE); // 用藍色最明顯
            
            Button b1 = new Button(activity); b1.setText("儲存");
            Button b2 = new Button(activity); b2.setText("自動");
            panel.addView(b1); panel.addView(b2);

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            decorView.addView(panel, lp);
            panel.bringToFront();
            XposedBridge.log("Lime: Panel Force Injected to " + activity.getClass().getSimpleName());
        } catch (Exception ignored) {}
    }
}
