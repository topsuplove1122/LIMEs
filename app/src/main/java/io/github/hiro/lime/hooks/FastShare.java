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
import android.widget.ImageView;
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
    private static final String FILE_NAME = "fast_share_v8.txt";

    @Override
    public void hook(LimeOptions options, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!options.fastShare.checked) return;

        // --- 1. 訊息旁按鈕 (暴力特徵識別) ---
        XposedHelpers.findAndHookMethod(ViewGroup.class, "addView", View.class, int.class, ViewGroup.LayoutParams.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View child = (View) param.args[0];
                // 延遲執行，確保子 View 已經長出來
                child.post(() -> checkAndInjectButton(child));
            }
        });

        // --- 2. 分享介面控制 (模糊匹配類名) ---
        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                String name = activity.getClass().getName();
                // 只要包名是 LINE 且類名包含關鍵字
                if (name.startsWith("jp.naver.line") && (name.contains("Share") || name.contains("Picker"))) {
                    injectPickerControls(activity);
                }
            }
        });
    }

    // 檢查 View 是否具備訊息列的特徵 (頭像+訊息氣泡)
    private void checkAndInjectButton(View view) {
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;

        // 避免重複
        if (group.findViewWithTag("lime_share") != null) return;

        // 檢查是否包含特定的 View 類型組合
        boolean hasImage = false;
        boolean hasText = false;
        for (int i = 0; i < group.getChildCount(); i++) {
            View v = group.getChildAt(i);
            if (v instanceof ImageView) hasImage = true;
            if (v instanceof TextView || v instanceof ViewGroup) hasText = true; // 氣泡可能是 ViewGroup
        }

        // 如果符合特徵，嘗試注入
        // 為了避免誤判，我們還可以檢查 ID
        String resName = "";
        try { resName = view.getContext().getResources().getResourceEntryName(view.getId()); } catch (Exception ignored) {}
        
        if ((hasImage && hasText) || resName.contains("message_row") || resName.contains("message_bubble")) {
            injectSideButton(group);
        }
    }

    private void injectSideButton(ViewGroup parent) {
        Context context = parent.getContext();
        Button btn = new Button(context);
        btn.setTag("lime_share");
        btn.setText("轉");
        btn.setTextSize(9);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.parseColor("#9900B900"));
        btn.setPadding(0, 0, 0, 0);

        // 使用 FrameLayout LayoutParams (相容性最好)
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dpToPx(30, context), dpToPx(20, context));
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
                Toast.makeText(context, "啟動失敗", Toast.LENGTH_SHORT).show();
            }
        });

        // 嘗試加入，如果 parent 不是 FrameLayout，Android 會自動轉換 LayoutParams，通常不會崩潰
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

    // --- 安全掃描邏輯 ---
    private Set<String> safeScanForMids(Object root) {
        Set<String> results = new HashSet<>();
        try {
            Class<?> clazz = root.getClass();
            while (clazz != null) {
                for (Field f : clazz.getDeclaredFields()) {
                    f.setAccessible(true);
                    Object val = f.get(root);
                    if (val == null) continue;

                    if (val instanceof Set) checkSet((Set<?>) val, results);
                    else if (val.getClass().getName().contains("LiveData")) {
                        try {
                            Object data = XposedHelpers.getObjectField(val, "mData"); 
                            if (data instanceof Set) checkSet((Set<?>) data, results);
                        } catch (Exception ignored) {}
                    }
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

    private Object findObjectByType(Object parent, String typeName) {
        Class<?> clazz = parent.getClass();
        while (clazz != null) {
            for (Field f : clazz.getDeclaredFields()) {
                f.setAccessible(true);
                try {
                    Object o = f.get(parent);
                    if (o != null && o.getClass().getName().contains(typeName)) return o;
                } catch (Exception ignored) {}
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    private void saveMids(Context c, Set<String> mids) {
        try (PrintWriter p = new PrintWriter(new FileWriter(new File(c.getFilesDir(), FILE_NAME)))) {
            for (String m : mids) p.println(m);
        } catch (Exception ignored) {}
    }

    private Set<String> loadMids(Context c) {
        Set<String> res = new HashSet<>();
        File f = new File(c.getFilesDir(), FILE_NAME);
        if (!f.exists()) return res;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String l; while ((l = r.readLine()) != null) res.add(l.trim());
        } catch (Exception ignored) {}
        return res;
    }
}
