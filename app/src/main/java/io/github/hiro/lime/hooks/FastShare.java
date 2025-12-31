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
import android.widget.TextView;
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
    private static final String FILE_NAME = "fast_share_v6.txt";

    @Override
    public void hook(LimeOptions options, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!options.fastShare.checked) return;

        // --- 1. 超強版訊息按鈕注入 (不再依賴單一 ID) ---
        XposedHelpers.findAndHookMethod(ViewGroup.class, "addView", View.class, int.class, ViewGroup.LayoutParams.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View child = (View) param.args[0];
                // 延遲執行，確保 View 的內容已填充
                child.post(() -> checkAndInject(child));
            }
        });

        // --- 2. 分享介面控制 ---
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

    // 檢查 View 是否為訊息氣泡，如果是則注入按鈕
    private void checkAndInject(View view) {
        try {
            if (!(view instanceof ViewGroup)) return;
            ViewGroup group = (ViewGroup) view;
            
            // 避免重複注入
            if (group.findViewWithTag("lime_share") != null) return;

            String resName = "";
            try { resName = view.getContext().getResources().getResourceEntryName(view.getId()); } catch (Exception ignored) {}

            // 判斷條件：ID 包含 message 且 bubble，或者是 row_message_layout
            if (resName.contains("message_bubble") || resName.contains("message_content") || resName.equals("chat_ui_row_message_layout")) {
                injectSideButton(group);
            }
        } catch (Exception ignored) {}
    }

    private void injectSideButton(ViewGroup parent) {
        Context context = parent.getContext();
        Button btn = new Button(context);
        btn.setTag("lime_share");
        btn.setText("轉");
        btn.setTextSize(9);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.parseColor("#8800B900"));
        btn.setPadding(0, 0, 0, 0);

        // 強制使用 FrameLayout LayoutParams (因為大多數氣泡容器是 FrameLayout)
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dpToPx(28, context), dpToPx(20, context));
        lp.gravity = Gravity.BOTTOM | Gravity.END;
        lp.setMargins(0, 0, dpToPx(2, context), dpToPx(2, context));

        btn.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, " ");
                intent.setPackage("jp.naver.line.android");
                context.startActivity(Intent.createChooser(intent, "轉傳"));
            } catch (Exception e) {
                Toast.makeText(context, "錯誤", Toast.LENGTH_SHORT).show();
            }
        });

        parent.addView(btn, lp);
        btn.bringToFront();
    }

    private void injectPickerControls(Activity activity) {
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        if (decor.findViewWithTag("lime_ctrl") != null) return;

        LinearLayout root = new LinearLayout(activity);
        root.setTag("lime_ctrl");
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Color.DKGRAY);

        Button btnSave = new Button(activity); btnSave.setText("儲存"); btnSave.setTextColor(Color.WHITE);
        Button btnAuto = new Button(activity); btnAuto.setText("自動"); btnAuto.setTextColor(Color.WHITE);

        btnSave.setOnClickListener(v -> {
            // 深度掃描：不僅找 ViewModel，還找 LiveData (androidx.lifecycle.E0)
            Set<String> selectedMids = deepScanForMids(activity);
            if (!selectedMids.isEmpty()) {
                saveMids(activity, selectedMids);
                Toast.makeText(activity, "已儲存 " + selectedMids.size() + " 筆", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity, "找不到勾選資料 (LiveData/Set)", Toast.LENGTH_SHORT).show();
            }
        });

        btnAuto.setOnClickListener(v -> {
            Set<String> saved = loadMids(activity);
            if (saved.isEmpty()) return;
            
            // 嘗試觸發點擊：由於方法名混淆，我們嘗試模擬點擊 UI
            // 這裡依然保留反射調用作為首選
            Object vm = findObjectByType(activity, "ViewModel");
            if (vm != null) {
                for (String mid : saved) {
                    try { XposedHelpers.callMethod(vm, "selectTarget", mid, true); } catch (Exception ignored) {}
                    try { XposedHelpers.callMethod(vm, "a", mid, true); } catch (Exception ignored) {}
                }
            }
            Toast.makeText(activity, "已嘗試自動勾選", Toast.LENGTH_SHORT).show();
        });

        root.addView(btnSave, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(btnAuto, new LinearLayout.LayoutParams(0, -2, 1));
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        fl.topMargin = dpToPx(80, activity);
        decor.addView(root, fl);
        root.bringToFront();
    }

    // --- 深度掃描：針對 Log 裡的 androidx.lifecycle.E0 (LiveData) ---
    private Set<String> deepScanForMids(Object root) {
        Set<String> results = new HashSet<>();
        try {
            Class<?> clazz = root.getClass();
            while (clazz != null) {
                for (Field f : clazz.getDeclaredFields()) {
                    f.setAccessible(true);
                    Object val = f.get(root);
                    if (val == null) continue;

                    // 1. 直接檢查 Set
                    if (val instanceof Set) {
                        checkSet((Set<?>) val, results);
                    }
                    // 2. 檢查 LiveData (androidx.lifecycle.LiveData / MutableLiveData)
                    else if (val.getClass().getName().contains("androidx.lifecycle")) {
                        try {
                            // 獲取 LiveData 的值 (getValue)
                            Object data = XposedHelpers.callMethod(val, "getValue");
                            if (data instanceof Set) {
                                checkSet((Set<?>) data, results);
                            }
                        } catch (Exception ignored) {}
                    }
                    // 3. 檢查 ViewModel 內部
                    else if (val.getClass().getName().contains("ViewModel")) {
                        results.addAll(deepScanForMids(val)); // 遞歸掃描 ViewModel
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Exception ignored) {}
        return results;
    }

    private void checkSet(Set<?> set, Set<String> results) {
        if (set.isEmpty()) return;
        for (Object item : set) {
            if (item instanceof String) {
                String s = (String) item;
                if (s.startsWith("u") || s.startsWith("c") || s.startsWith("r")) {
                    results.add(s);
                }
            }
        }
    }

    // 輔助方法
    private Object findObjectByType(Object parent, String typeName) { /* 同上個版本 */ return null; } // 簡化展示，實際代碼請保留 V5 的實現
    private void saveMids(Context c, Set<String> mids) { /* 同上個版本 */ }
    private Set<String> loadMids(Context c) { /* 同上個版本 */ return new HashSet<>(); }
}
