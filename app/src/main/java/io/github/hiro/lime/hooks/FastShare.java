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
    private static final String FILE_NAME = "fast_share_v3.txt";

    @Override
    public void hook(LimeOptions options, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!options.fastShare.checked) return;

        // --- 功能 1: 訊息旁按鈕 (放寬條件) ---
        XposedHelpers.findAndHookMethod(ViewGroup.class, "onViewAdded", View.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View addedView = (View) param.args[0];
                if (addedView.getId() <= 0) return;

                String resName = "";
                try { resName = addedView.getContext().getResources().getResourceEntryName(addedView.getId()); } catch (Exception ignored) {}

                if ("chat_ui_message_text".equals(resName)) {
                    ViewGroup bubble = (ViewGroup) addedView.getParent();
                    ViewGroup row = (ViewGroup) bubble.getParent();
                    if (row != null && row.findViewWithTag("lime_share") == null) {
                        injectSideButton(row, addedView.getContext());
                    }
                }
            }
        });

        // --- 功能 2 & 3: 分享介面 ---
        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                // 擴大匹配範圍，有些版本叫 PickerActivity
                if (activity.getClass().getName().matches(".*(Share|Picker).*Activity")) {
                    injectPickerControls(activity);
                }
            }
        });
    }

    private void injectSideButton(ViewGroup row, Context context) {
        Button btn = new Button(context);
        btn.setTag("lime_share");
        btn.setText("轉");
        btn.setTextSize(9);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.parseColor("#8800B900"));
        btn.setPadding(0, 0, 0, 0);

        // 使用 FrameLayout.LayoutParams (兼容性最高)
        // 如果 parent 是 LinearLayout，這會被自動轉換，不用擔心崩潰
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dpToPx(24, context), dpToPx(24, context));
        lp.gravity = Gravity.BOTTOM | Gravity.END; 
        // 對於 LinearLayout，gravity 參數可能無效，所以我們依靠 Margins
        // 這裡設定負的 Margin 嘗試讓它浮在氣泡外
        lp.setMargins(0, 0, dpToPx(2, context), dpToPx(2, context));

        btn.setOnClickListener(v -> {
            try {
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, "Quick Forward");
                sendIntent.setType("text/plain");
                sendIntent.setPackage("jp.naver.line.android");
                Intent shareIntent = Intent.createChooser(sendIntent, null);
                shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(shareIntent);
            } catch (Exception e) {
                Toast.makeText(context, "啟動失敗", Toast.LENGTH_SHORT).show();
            }
        });

        row.addView(btn, lp);
    }

    private void injectPickerControls(Activity activity) {
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        if (decor.findViewWithTag("lime_ctrl") != null) return;

        LinearLayout root = new LinearLayout(activity);
        root.setTag("lime_ctrl");
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Color.DKGRAY);

        Button btnSave = new Button(activity); btnSave.setText("偵錯與儲存"); btnSave.setTextColor(Color.WHITE);
        Button btnAuto = new Button(activity); btnAuto.setText("自動"); btnAuto.setTextColor(Color.WHITE);

        // --- 偵錯模式開啟 ---
        btnSave.setOnClickListener(v -> {
            XposedBridge.log("Lime: DEBUGGING ACTIVITY FIELDS for " + activity.getClass().getName());
            
            // 1. 先把所有欄位印出來，讓我們在 Log 裡找答案
            dumpActivityFields(activity);

            // 2. 嘗試自動尋找
            Object vm = findObjectByType(activity, "ViewModel");
            if (vm != null) {
                XposedBridge.log("Lime: Found ViewModel -> " + vm.getClass().getName());
                Set<String> mids = findSetInObject(vm);
                if (mids != null && !mids.isEmpty()) {
                    saveMids(activity, mids);
                    Toast.makeText(activity, "已存 " + mids.size() + " 筆 (看Log確認)", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(activity, "ViewModel 內無 Set 數據", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(activity, "找不到 ViewModel", Toast.LENGTH_SHORT).show();
            }
        });

        btnAuto.setOnClickListener(v -> {
            Object vm = findObjectByType(activity, "ViewModel");
            Set<String> saved = loadMids(activity);
            if (vm != null && !saved.isEmpty()) {
                for (String mid : saved) {
                    try { XposedHelpers.callMethod(vm, "selectTarget", mid, true); } 
                    catch (Exception ignored) {}
                }
                Toast.makeText(activity, "執行自動勾選", Toast.LENGTH_SHORT).show();
            }
        });

        root.addView(btnSave, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(btnAuto, new LinearLayout.LayoutParams(0, -2, 1));
        
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        fl.bottomMargin = dpToPx(50, activity); // 往上提一點避免被導航列擋住
        decor.addView(root, fl);
        root.bringToFront();
    }

    // --- 強大的偵錯工具 ---
    private void dumpActivityFields(Object obj) {
        try {
            Class<?> clazz = obj.getClass();
            XposedBridge.log("--- DUMP START: " + clazz.getName() + " ---");
            while (clazz != null) {
                for (Field f : clazz.getDeclaredFields()) {
                    f.setAccessible(true);
                    Object val = f.get(obj);
                    String valStr = (val == null) ? "null" : val.getClass().getName();
                    XposedBridge.log("Field: " + f.getName() + " | Type: " + f.getType().getName() + " | Value: " + valStr);
                    
                    // 如果這是一個 Set，印出它的內容看看是不是我們要的 MID
                    if (val instanceof Set) {
                        XposedBridge.log("   -> Set Content: " + val.toString());
                    }
                }
                clazz = clazz.getSuperclass();
            }
            XposedBridge.log("--- DUMP END ---");
        } catch (Exception e) {
            XposedBridge.log("Dump failed: " + e.getMessage());
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
            clazz = clazz.getSuperclass(); // 往父類別找
        }
        return null;
    }

    private Set<String> findSetInObject(Object obj) {
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            for (Field f : clazz.getDeclaredFields()) {
                f.setAccessible(true);
                try {
                    Object o = f.get(obj);
                    if (o instanceof Set) {
                        Set<?> s = (Set<?>) o;
                        if (!s.isEmpty() && s.iterator().next() instanceof String) {
                            return (Set<String>) o;
                        }
                    }
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
