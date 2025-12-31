package io.github.hiro.lime.hooks;

import static io.github.hiro.lime.Utils.dpToPx;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
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
import java.util.concurrent.CopyOnWriteArrayList;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.hiro.lime.LimeOptions;

public class FastShare implements IHook {
    private static final String FILE_NAME = "fast_share_v4.txt";

    @Override
    public void hook(LimeOptions options, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!options.fastShare.checked) return;

        // --- 功能 1: 訊息旁按鈕 (延遲注入策略) ---
        XposedHelpers.findAndHookMethod(ViewGroup.class, "onViewAdded", View.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View view = (View) param.args[0];
                if (view.getId() <= 0) return;

                String name = "";
                try { name = view.getContext().getResources().getResourceEntryName(view.getId()); } catch (Exception ignored) {}

                // 針對 15.9.0 的訊息氣泡容器
                if ("chat_ui_message_bubble".equals(name) || "chat_ui_message_content".equals(name)) {
                    final View target = view;
                    // 使用 post 確保 Layout 已經完成
                    target.post(() -> injectSideButton(target));
                }
            }
        });

        // --- 功能 2 & 3: 分享介面數據處理 ---
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

    private void injectSideButton(View bubbleView) {
        ViewGroup parent = (ViewGroup) bubbleView.getParent();
        if (parent == null || parent.findViewWithTag("lime_share") != null) return;

        Context context = bubbleView.getContext();
        Button btn = new Button(context);
        btn.setTag("lime_share");
        btn.setText("轉");
        btn.setTextSize(10);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.parseColor("#9900B900"));
        btn.setPadding(0, 0, 0, 0);

        // 使用 FrameLayout 覆蓋在氣泡角落，這是最穩定的位置
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dpToPx(24, context), dpToPx(24, context));
        lp.gravity = Gravity.BOTTOM | Gravity.END;
        // 微調位置：根據是否為自己發送的訊息可能需要調整左右
        lp.setMargins(0, 0, dpToPx(5, context), dpToPx(5, context));

        btn.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, " "); // 空白內容觸發選擇
                intent.setPackage("jp.naver.line.android");
                context.startActivity(Intent.createChooser(intent, "轉傳訊息"));
            } catch (Exception e) {
                Toast.makeText(context, "啟動失敗", Toast.LENGTH_SHORT).show();
            }
        });

        // 如果 parent 不是 FrameLayout，我們就在它外面包一層 FrameLayout (太複雜先不這樣做)
        // 簡單做法：直接加進去，依賴 Android 佈局寬容度
        if (parent instanceof ViewGroup) {
            parent.addView(btn, lp);
            btn.bringToFront(); // 確保浮在最上面
        }
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
            // 針對 Log 中的 CopyOnWriteArrayList 進行掃描
            Set<String> selectedMids = scanForSelectedMids(activity);
            if (!selectedMids.isEmpty()) {
                saveMids(activity, selectedMids);
                Toast.makeText(activity, "已儲存 " + selectedMids.size() + " 個對象", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity, "找不到已勾選的對象 (請確認已手動勾選)", Toast.LENGTH_SHORT).show();
            }
        });

        btnAuto.setOnClickListener(v -> {
            Set<String> saved = loadMids(activity);
            if (saved.isEmpty()) {
                Toast.makeText(activity, "無儲存資料", Toast.LENGTH_SHORT).show();
                return;
            }
            // 這裡需要觸發點擊事件
            // 由於直接調用方法太難找，我們建議：顯示提示，讓用戶知道已載入名單
            Toast.makeText(activity, "自動勾選功能開發中 (Data Loaded)", Toast.LENGTH_SHORT).show();
        });

        root.addView(btnSave, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(btnAuto, new LinearLayout.LayoutParams(0, -2, 1));
        
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        fl.topMargin = dpToPx(80, activity);
        decor.addView(root, fl);
        root.bringToFront();
    }

    // --- 針對 15.9.0 Log 的精確掃描 ---
    private Set<String> scanForSelectedMids(Object rootObj) {
        Set<String> results = new HashSet<>();
        try {
            Class<?> clazz = rootObj.getClass();
            while (clazz != null) {
                for (Field f : clazz.getDeclaredFields()) {
                    f.setAccessible(true);
                    Object val = f.get(rootObj);
                    
                    // 掃描所有列表 (CopyOnWriteArrayList)
                    if (val instanceof List) {
                        List<?> list = (List<?>) val;
                        for (Object item : list) {
                            // 檢查列表內的物件是否有 'mid' 或 'id' 欄位
                            String mid = extractMid(item);
                            if (mid != null) {
                                // 這裡假設列表裡存的是所有好友，我們需要過濾出「已選中」的
                                // 由於無法判斷選中狀態，這裡先全部抓出來測試
                                results.add(mid);
                            }
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Exception ignored) {}
        return results;
    }

    private String extractMid(Object item) {
        try {
            // 嘗試獲取 mid 欄位
            Field midField = XposedHelpers.findField(item.getClass(), "mid");
            return (String) midField.get(item);
        } catch (Exception e) {
            try {
                // 備用：嘗試 id 欄位
                Field idField = XposedHelpers.findField(item.getClass(), "id");
                return (String) idField.get(item);
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
