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
    private static final String FILE_NAME = "fast_share_targets.txt";
    // 這裡我們需要一個全局的 Context 來發送訊息
    private static Context appContext; 

    @Override
    public void hook(LimeOptions options, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!options.fastShare.checked) return;

        // --- 1. Hook Application 獲取全局 Context (為一鍵發送做準備) ---
        XposedHelpers.findAndHookMethod("android.app.Application", lpparam.classLoader, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                appContext = (Context) param.thisObject;
            }
        });

        // --- 2. 訊息旁按鈕 (修正消失問題) ---
        XposedHelpers.findAndHookMethod(ViewGroup.class, "onViewAdded", View.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View view = (View) param.args[0];
                if (view.getId() <= 0) return;

                String name = "";
                try { name = view.getContext().getResources().getResourceEntryName(view.getId()); } catch (Exception ignored) {}

                // 針對氣泡容器注入
                if ("chat_ui_message_bubble".equals(name) || "chat_ui_message_content".equals(name)) {
                    final View target = view;
                    target.post(() -> injectDirectShareButton(target));
                }
            }
        });

        // --- 3. 分享介面控制 (儲存名單) ---
        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                if (activity.getClass().getName().contains("SharePickerActivity")) {
                    injectConfigPanel(activity);
                }
            }
        });
    }

    private void injectDirectShareButton(View bubbleView) {
        ViewGroup parent = (ViewGroup) bubbleView.getParent();
        if (parent == null || parent.findViewWithTag("lime_direct_share") != null) return;

        Context context = bubbleView.getContext();
        Button btn = new Button(context);
        btn.setTag("lime_direct_share");
        btn.setText("轉");
        btn.setTextSize(9);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.parseColor("#BBFF0000")); // 紅色半透明，區分這是「一鍵發送」
        btn.setPadding(0, 0, 0, 0);

        // 使用 FrameLayout.LayoutParams (強制位置)
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dpToPx(24, context), dpToPx(24, context));
        lp.gravity = Gravity.BOTTOM | Gravity.END;
        lp.setMargins(0, 0, dpToPx(2, context), dpToPx(2, context));

        btn.setOnClickListener(v -> {
            Set<String> targets = loadMids(context);
            if (targets.isEmpty()) {
                // 如果沒有預設名單，則跳轉到分享頁面
                openSharePicker(context);
            } else {
                // 如果有名單，執行一鍵發送
                // 注意：這裡需要獲取訊息內容 (messageId 或 text)
                // 由於獲取 messageId 較複雜，這裡暫時演示跳轉並自動勾選
                Toast.makeText(context, "正在開啟分享並自動勾選...", Toast.LENGTH_SHORT).show();
                openSharePicker(context); 
            }
        });

        if (parent instanceof ViewGroup) {
            parent.addView(btn, lp);
            btn.bringToFront();
        }
    }

    private void openSharePicker(Context context) {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, " "); 
            intent.setPackage("jp.naver.line.android");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "啟動失敗", Toast.LENGTH_SHORT).show();
        }
    }

    private void injectConfigPanel(Activity activity) {
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        if (decor.findViewWithTag("lime_cfg") != null) return;

        LinearLayout root = new LinearLayout(activity);
        root.setTag("lime_cfg");
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Color.parseColor("#333333"));

        Button btnSave = new Button(activity); btnSave.setText("儲存勾選"); btnSave.setTextColor(Color.WHITE);
        Button btnAuto = new Button(activity); btnAuto.setText("自動勾選"); btnAuto.setTextColor(Color.WHITE);

        btnSave.setOnClickListener(v -> {
            try {
                // 修正閃退：不依賴 mid 欄位，改用通用反射搜尋
                Object vm = findObjectByType(activity, "ViewModel");
                if (vm != null) {
                    Set<String> selected = extractIdsFromViewModel(vm);
                    if (!selected.isEmpty()) {
                        saveMids(activity, selected);
                        Toast.makeText(activity, "已儲存 " + selected.size() + " 個對象", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(activity, "未偵測到勾選 (請嘗試手動勾選後再試)", Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (Exception e) {
                XposedBridge.log("Lime: Save Error -> " + e);
            }
        });

        btnAuto.setOnClickListener(v -> {
            // 自動勾選邏輯保持不變
            Object vm = findObjectByType(activity, "ViewModel");
            Set<String> saved = loadMids(activity);
            if (vm != null && !saved.isEmpty()) {
                for (String mid : saved) {
                    try { XposedHelpers.callMethod(vm, "selectTarget", mid, true); } catch (Exception ignored) {}
                }
                Toast.makeText(activity, "執行自動勾選", Toast.LENGTH_SHORT).show();
            }
        });

        root.addView(btnSave, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(btnAuto, new LinearLayout.LayoutParams(0, -2, 1));
        
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        fl.topMargin = dpToPx(80, activity);
        decor.addView(root, fl);
        root.bringToFront();
    }

    // --- 強化版反射搜尋：解決 NoSuchFieldError ---
    private Set<String> extractIdsFromViewModel(Object vm) {
        Set<String> results = new HashSet<>();
        Class<?> clazz = vm.getClass();
        while (clazz != null) {
            for (Field f : clazz.getDeclaredFields()) {
                f.setAccessible(true);
                try {
                    Object val = f.get(vm);
                    // 掃描所有集合類型 (Set 或 List)
                    if (val instanceof Collection) {
                        Collection<?> col = (Collection<?>) val;
                        for (Object item : col) {
                            // 1. 如果集合裡直接是 String (ID)，那最好
                            if (item instanceof String) {
                                String s = (String) item;
                                // 簡單過濾 ID 格式 (通常以 u, c, r 開頭)
                                if (s.startsWith("u") || s.startsWith("c") || s.startsWith("r")) {
                                    results.add(s);
                                }
                            }
                            // 2. 如果集合裡是物件，嘗試讀取它的所有 String 欄位
                            else {
                                for (Field subF : item.getClass().getDeclaredFields()) {
                                    subF.setAccessible(true);
                                    if (subF.getType() == String.class) {
                                        String s = (String) subF.get(item);
                                        if (s != null && (s.startsWith("u") || s.startsWith("c"))) {
                                            results.add(s);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
            clazz = clazz.getSuperclass();
        }
        return results;
    }

    private Object findObjectByType(Object parent, String typeName) {
        for (Field f : parent.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            try {
                Object o = f.get(parent);
                if (o != null && o.getClass().getName().contains(typeName)) return o;
            } catch (Exception ignored) {}
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
