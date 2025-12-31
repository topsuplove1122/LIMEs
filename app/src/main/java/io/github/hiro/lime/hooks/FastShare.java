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

    @Override
    public void hook(LimeOptions options, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 1. 檢查 LimeOptions 中的開關 (對應你剛剛加的 fastShare)
        if (!options.fastShare.checked) return;

        // 2. 功能一：在聊天室長按選單出現時注入「快速轉傳」按鈕
        try {
            XposedHelpers.findAndHookMethod(ViewGroup.class, "onViewAdded", View.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    View addedView = (View) param.args[0];
                    String resName = "";
                    try {
                        resName = addedView.getContext().getResources().getResourceEntryName(addedView.getId());
                    } catch (Exception ignored) {}

                    // 這是回應選單/長按選單的關鍵 ID
                    if ("chat_ui_reactionsheet_close".equals(resName)) {
                        injectQuickShareButton(addedView);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("Lime: FastShare Message Button Hook Failed");
        }

        // 3. 功能二 & 三：Hook 分享好友選擇介面 (處理 15.9.0 混淆問題)
        // 我們不直接寫死類名，嘗試多種可能的路徑
        String[] possibleActivityNames = {
            "jp.naver.line.android.activity.selecttarget.SelectTargetActivity",
            "jp.naver.line.android.activity.selecttarget.a",
            "com.linecorp.line.share.SelectTargetActivity" 
        };

        for (String className : possibleActivityNames) {
            try {
                Class<?> targetClass = XposedHelpers.findClass(className, lpparam.classLoader);
                XposedHelpers.findAndHookMethod(targetClass, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        injectShareControls((Activity) param.thisObject);
                        XposedBridge.log("Lime: FastShare controls injected into " + param.thisObject.getClass().getName());
                    }
                });
                XposedBridge.log("Lime: Successfully hooked SelectTarget class: " + className);
                break; // 只要 Hook 成功一個就停止嘗試
            } catch (XposedHelpers.ClassNotFoundError e) {
                // 繼續嘗試下一個
            }
        }
    }

    private void injectQuickShareButton(View anchorView) {
        Context context = anchorView.getContext();
        ViewGroup parent = (ViewGroup) anchorView.getParent();
        if (parent == null) return;

        Button btn = new Button(context);
        btn.setText("快速轉傳");
        btn.setBackgroundColor(Color.parseColor("#06C755")); // LINE 綠
        btn.setTextColor(Color.WHITE);
        btn.setPadding(dpToPx(10, context), 0, dpToPx(10, context), 0);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, 
                dpToPx(40, context));
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = dpToPx(100, context);

        btn.setOnClickListener(v -> {
            // 由於 15.9.0 混淆，直接用 Intent 可能跳轉失敗，
            // 這裡建議發送一個全域廣播或模擬點擊原生的「轉傳」按鈕
            Toast.makeText(context, "功能開發中：需配合 MessageId 獲取", Toast.LENGTH_SHORT).show();
        });

        parent.addView(btn, lp);
        btn.bringToFront();
    }

    private void injectShareControls(Activity activity) {
        // 建立控制列容器
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setBackgroundColor(Color.parseColor("#F4F4F4"));
        container.setPadding(dpToPx(10, activity), dpToPx(5, activity), dpToPx(10, activity), dpToPx(5, activity));

        Button saveBtn = new Button(activity);
        saveBtn.setText("儲存清單");
        saveBtn.setAllCaps(false);

        Button autoBtn = new Button(activity);
        autoBtn.setText("自動勾選");
        autoBtn.setAllCaps(false);

        container.addView(saveBtn, new LinearLayout.LayoutParams(0, -2, 1));
        container.addView(autoBtn, new LinearLayout.LayoutParams(0, -2, 1));

        saveBtn.setOnClickListener(v -> {
            try {
                // 這裡需要透過反射抓取 ViewModel
                // 在 15.9.0 中，你需要找出 SelectTargetActivity 裡面哪個欄位是 ViewModel
                Object viewModel = findViewModel(activity);
                if (viewModel != null) {
                    // 假設選中目標儲存在某個 Set 或 List
                    Set<String> selectedMids = getSelectedMidsFromViewModel(viewModel);
                    if (selectedMids != null && !selectedMids.isEmpty()) {
                        saveMids(activity, selectedMids);
                        Toast.makeText(activity, "已儲存 " + selectedMids.size() + " 個聊天室", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(activity, "尚未勾選任何對象", Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (Exception e) {
                XposedBridge.log("Lime: Save Error " + e.getMessage());
            }
        });

        autoBtn.setOnClickListener(v -> {
            try {
                Set<String> mids = loadMids(activity);
                Object viewModel = findViewModel(activity);
                if (viewModel != null && !mids.isEmpty()) {
                    for (String mid : mids) {
                        // 嘗試調用 ViewModel 的選取方法
                        // 15.9.0 的方法名需要透過 JADX 確認，常見為 selectTarget 或 a
                        XposedHelpers.callMethod(viewModel, "selectTarget", mid, true);
                    }
                    Toast.makeText(activity, "自動勾選完成", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                XposedBridge.log("Lime: Auto Select Error " + e.getMessage());
            }
        });

        ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        decorView.addView(container, lp);
        container.bringToFront();
    }

    // 輔助方法：嘗試找出 ViewModel
    private Object findViewModel(Activity activity) {
        try {
            // 通常 LINE 會在 Activity 中定義一個 ViewModel 成員
            return XposedHelpers.getObjectField(activity, "viewModel");
        } catch (Exception e) {
            // 如果找不到，可能被混淆成 a, b, c... 需要進一步分析
            return null;
        }
    }

    // 輔助方法：從 ViewModel 抓出目前勾選的 MID
    private Set<String> getSelectedMidsFromViewModel(Object viewModel) {
        try {
            // 這裡需要針對 15.9.0 的混淆變數名進行修改
            return (Set<String>) XposedHelpers.getObjectField(viewModel, "selectedTargetMids");
        } catch (Exception e) {
            return null;
        }
    }

    private void saveMids(Context context, Set<String> mids) {
        File file = new File(context.getFilesDir(), "fast_share_targets.txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (String mid : mids) {
                writer.write(mid + "\n");
            }
        } catch (IOException ignored) {}
    }

    private Set<String> loadMids(Context context) {
        Set<String> mids = new HashSet<>();
        File file = new File(context.getFilesDir(), "fast_share_targets.txt");
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
