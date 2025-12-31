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
    private static final String FILE_NAME = "fast_share_v7.txt";
    // 15.9.0 的正確分享 Activity 類名 (根據 Log 確認)
    private static final String TARGET_ACTIVITY = "jp.naver.line.android.activity.share.SharePickerActivity";

    @Override
    public void hook(LimeOptions options, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!options.fastShare.checked) return;

        // --- 1. 訊息按鈕 ---
        XposedHelpers.findAndHookMethod(ViewGroup.class, "addView", View.class, int.class, ViewGroup.LayoutParams.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View child = (View) param.args[0];
                // 延遲注入以確保 ID 可讀取
                child.post(() -> {
                    try {
                        String name = child.getContext().getResources().getResourceEntryName(child.getId());
                        // 針對訊息氣泡容器注入
                        if ("chat_ui_message_bubble".equals(name) || "chat_ui_message_content".equals(name)) {
                            injectSideButton((ViewGroup) child);
                        }
                    } catch (Exception ignored) {}
                });
            }
        });

        // --- 2. 分享介面控制 ---
        // 直接 Hook 目標 Activity，不再模糊匹配
        try {
            XposedHelpers.findAndHookMethod(TARGET_ACTIVITY, lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    injectPickerControls((Activity) param.thisObject);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("Lime: Failed to hook SharePickerActivity -> " + t);
        }
    }

    private void injectSideButton(ViewGroup bubble) {
        if (bubble.findViewWithTag("lime_share") != null) return;

        Context context = bubble.getContext();
        Button btn = new Button(context);
        btn.setTag("lime_share");
        btn.setText("轉");
        btn.setTextSize(9);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.parseColor("#9900B900"));
        btn.setPadding(0, 0, 0, 0);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dpToPx(26, context), dpToPx(20, context));
        lp.gravity = Gravity.BOTTOM | Gravity.END;
        lp.setMargins(0, 0, dpToPx(4, context), dpToPx(4, context));

        btn.setOnClickListener(v -> {
            try {
                // 使用 LINE 內部分享邏輯
                Intent intent = new Intent();
                intent.setClassName("jp.naver.line.android", TARGET_ACTIVITY);
                intent.putExtra("otp_id", "otp_share_server"); // 模擬內部參數
                intent.putExtra("from_chat_app", true);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e) {
                // 如果還是失敗，退回系統分享但提示錯誤
                Toast.makeText(context, "內部跳轉失敗，請回報 Log", Toast.LENGTH_SHORT).show();
            }
        });

        bubble.addView(btn, lp);
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
            // 修正閃退：使用安全掃描
            Set<String> selectedMids = safeScanForMids(activity);
            if (!selectedMids.isEmpty()) {
                saveMids(activity, selectedMids);
                Toast.makeText(activity, "已儲存 " + selectedMids.size() + " 筆", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity, "未找到勾選資料", Toast.LENGTH_SHORT).show();
            }
        });

        btnAuto.setOnClickListener(v -> {
            Set<String> saved = loadMids(activity);
            if (saved.isEmpty()) return;
            
            Object vm = findObjectByType(activity, "ViewModel");
            if (vm != null) {
                for (String mid : saved) {
                    // 嘗試觸發選取
                    try { XposedHelpers.callMethod(vm, "selectTarget", mid, true); } catch (Exception ignored) {}
                    try { XposedHelpers.callMethod(vm, "a", mid, true); } catch (Exception ignored) {}
                }
            }
            Toast.makeText(activity, "已執行自動勾選", Toast.LENGTH_SHORT).show();
        });

        root.addView(btnSave, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(btnAuto, new LinearLayout.LayoutParams(0, -2, 1));
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        fl.topMargin = dpToPx(80, activity);
        decor.addView(root, fl);
        root.bringToFront();
    }

    // --- 安全掃描：解決 getValue() 報錯 ---
    private Set<String> safeScanForMids(Object root) {
        Set<String> results = new HashSet<>();
        try {
            Class<?> clazz = root.getClass();
            while (clazz != null) {
                for (Field f : clazz.getDeclaredFields()) {
                    f.setAccessible(true);
                    Object val = f.get(root);
                    if (val == null) continue;

                    // 1. 直接是 Set
                    if (val instanceof Set) checkSet((Set<?>) val, results);
                    
                    // 2. LiveData 處理 (直接讀欄位，不 call 方法)
                    else if (val.getClass().getName().contains("LiveData")) {
                        try {
                            // LiveData 內部通常把值存在 mData 或 mValue
                            Object data = XposedHelpers.getObjectField(val, "mData"); 
                            if (data == XposedHelpers.getObjectField(val, "NOT_SET")) continue; // 忽略空值
                            if (data instanceof Set) checkSet((Set<?>) data, results);
                        } catch (Exception e) {
                            // 如果 mData 失敗，嘗試 mValue
                            try {
                                Object data = XposedHelpers.getObjectField(val, "mValue");
                                if (data instanceof Set) checkSet((Set<?>) data, results);
                            } catch (Exception ignored) {}
                        }
                    }
                    
                    // 3. ViewModel 遞歸
                    else if (val.getClass().getName().contains("ViewModel")) {
                        results.addAll(safeScanForMids(val));
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
                if (s.startsWith("u") || s.startsWith("c")) results.add(s);
            }
        }
    }

    // (保留之前的輔助方法: findObjectByType, saveMids, loadMids)
    private Object findObjectByType(Object parent, String typeName) { /* 同 V6 */ return null; } // 佔位
    private void saveMids(Context c, Set<String> mids) { /* 同 V6 */ }
    private Set<String> loadMids(Context c) { /* 同 V6 */ return new HashSet<>(); }
}
