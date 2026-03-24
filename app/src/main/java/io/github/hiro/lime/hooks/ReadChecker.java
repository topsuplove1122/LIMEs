package io.github.hiro.lime.hooks;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.hiro.lime.LimeOptions;

public class ReadChecker implements IHook {
    private SQLiteDatabase db_line = null;
    private SQLiteDatabase db_contact = null;
    private String currentChatId = null;

    @Override
    public void hook(LimeOptions limeOptions, XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        
        // 1. 初始化資料庫與攔截封包
        XposedBridge.hookAllMethods(Application.class, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Application appContext = (Application) param.thisObject;
                if (appContext == null) return;

                File dbFileLine = appContext.getDatabasePath("naver_line");
                File dbFileContact = appContext.getDatabasePath("contact");

                if (dbFileLine.exists() && dbFileContact.exists()) {
                    SQLiteDatabase.OpenParams.Builder builder = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        builder = new SQLiteDatabase.OpenParams.Builder();
                        builder.addOpenFlags(SQLiteDatabase.OPEN_READWRITE);
                        SQLiteDatabase.OpenParams params = builder.build();
                        
                        db_line = SQLiteDatabase.openDatabase(dbFileLine, params);
                        db_contact = SQLiteDatabase.openDatabase(dbFileContact, params);

                        // 建立輕量級的「水位線」進度表 (只記錄每個人最新讀到哪)
                        try {
                            db_line.execSQL("CREATE TABLE IF NOT EXISTS lime_read_watermarks (chat_id TEXT, reader_mid TEXT, max_msg_id INTEGER, PRIMARY KEY(chat_id, reader_mid))");
                        } catch (Exception e) {}

                        hookNetwork(loadPackageParam);
                    }
                }
            }
        });

        // 2. 獲取目前進入的聊天室 ID
        Class<?> chatHistoryRequestClass = XposedHelpers.findClass("com.linecorp.line.chat.request.ChatHistoryRequest", loadPackageParam.classLoader);
        XposedHelpers.findAndHookMethod(chatHistoryRequestClass, "getChatId", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                currentChatId = (String) param.getResult();
            }
        });

        // 3. 在畫面上方加入「查看已讀」按鈕
        Class<?> chatHistoryActivityClass = XposedHelpers.findClass("jp.naver.line.android.activity.chathistory.ChatHistoryActivity", loadPackageParam.classLoader);
        XposedHelpers.findAndHookMethod(chatHistoryActivityClass, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Activity activity = (Activity) param.thisObject;
                if (currentChatId != null) {
                    addTopButton(activity);
                }
            }
        });
    }

    // ==========================================
    // 引擎一：攔截封包，精準反查並更新水位線
    // ==========================================
    private void hookNetwork(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        try {
            XposedBridge.hookAllMethods(
                    loadPackageParam.classLoader.loadClass(Constants.RESPONSE_HOOK.className),
                    Constants.RESPONSE_HOOK.methodName,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String paramValue = param.args[1].toString();
                            
                            // 只要封包裡有「已讀」事件
                            if (paramValue.contains("type:NOTIFIED_READ_MESSAGE")) {
                                
                                // LINE 會把多個操作綁在一起，先切開它們
                                String[] operations = paramValue.split("Operation\\(");
                                
                                for (String op : operations) {
                                    if (op.contains("type:NOTIFIED_READ_MESSAGE")) {
                                        String readerMid = extractParam(op, "param2:");
                                        String msgIdStr = extractParam(op, "param3:");

                                        if (readerMid != null && msgIdStr != null && db_line != null) {
                                            try {
                                                long msgId = Long.parseLong(msgIdStr);
                                                
                                                // 💥 核心修正：拿訊息 ID 去反查真正的 Chat ID！
                                                Cursor c = db_line.rawQuery("SELECT chat_id FROM chat_history WHERE server_id = ?", new String[]{msgIdStr});
                                                if (c.moveToFirst()) {
                                                    String exactChatId = c.getString(0);
                                                    
                                                    // 寫入底層進度表
                                                    db_line.execSQL("INSERT OR REPLACE INTO lime_read_watermarks (chat_id, reader_mid, max_msg_id) VALUES (?, ?, ?)",
                                                            new Object[]{exactChatId, readerMid, msgId});
                                                    
                                                    XposedBridge.log("Lime ReadChecker: Captured Read! Chat=" + exactChatId + ", Reader=" + readerMid + ", MsgID=" + msgId);
                                                }
                                                c.close();
                                                
                                            } catch (Exception e) {
                                                XposedBridge.log("Lime ReadChecker Error: " + e.getMessage());
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    });
        } catch (Exception e) {}
    }

    // ==========================================
    // 引擎二：全局記憶位置的拖曳按鈕
    // ==========================================
    private void addTopButton(Activity activity) {
        final int BUTTON_ID = 95279527;
        ViewGroup layout = activity.findViewById(android.R.id.content);
        
        // 確保畫面準備好，且還沒有重複加過按鈕
        if (layout == null || activity.findViewById(BUTTON_ID) != null) {
            return;
        }

        Button btn = new Button(activity);
        btn.setId(BUTTON_ID);
        btn.setText("👀 誰已讀");
        btn.setBackgroundColor(android.graphics.Color.parseColor("#AA000000")); // 半透明黑
        btn.setTextColor(android.graphics.Color.WHITE);
        btn.setTextSize(12);

        // 💥 記憶魔法 1：讀取上次儲存的座標
        android.content.SharedPreferences prefs = activity.getSharedPreferences("LimeReadCheckerPrefs", android.content.Context.MODE_PRIVATE);
        // 如果是第一次用，預設值給 150 和 180
        int savedX = prefs.getInt("btn_x", 150);
        int savedY = prefs.getInt("btn_y", 180);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(savedX, savedY, 0, 0); // 套用記憶的座標
        btn.setLayoutParams(params);

        // 點擊事件：查資料庫 (維持不變)
        btn.setOnClickListener(v -> {
            if (currentChatId == null || db_line == null || db_contact == null) return;
            
            Toast.makeText(activity, "資料撈取中，請稍候...", Toast.LENGTH_SHORT).show();

            new Thread(() -> {
                String resultText = buildReadDataString();
                activity.runOnUiThread(() -> {
                    TextView textView = new TextView(activity);
                    textView.setText(resultText);
                    textView.setPadding(40, 40, 40, 40);

                    ScrollView scrollView = new ScrollView(activity);
                    scrollView.addView(textView);

                    new AlertDialog.Builder(activity)
                            .setTitle("已讀名單 (最近 15 則文字)")
                            .setView(scrollView)
                            .setPositiveButton("關閉", null)
                            .show();
                });
            }).start();
        });

        // 觸控事件：處理隨意拖曳並儲存位置
        btn.setOnTouchListener(new android.view.View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private boolean isMoved = false;
            private static final int CLICK_DRAG_TOLERANCE = 10; 

            @Override
            public boolean onTouch(android.view.View v, android.view.MotionEvent event) {
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) v.getLayoutParams();

                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        initialX = layoutParams.leftMargin;
                        initialY = layoutParams.topMargin;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isMoved = false;
                        return true;

                    case android.view.MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);

                        if (Math.abs(dx) > CLICK_DRAG_TOLERANCE || Math.abs(dy) > CLICK_DRAG_TOLERANCE) {
                            isMoved = true;
                        }

                        layoutParams.leftMargin = initialX + dx;
                        layoutParams.topMargin = initialY + dy;
                        layoutParams.rightMargin = 0;
                        layoutParams.bottomMargin = 0;
                        v.setLayoutParams(layoutParams);
                        return true;

                    case android.view.MotionEvent.ACTION_UP:
                        if (!isMoved) {
                            v.performClick(); // 沒有移動，當作點擊
                        } else {
                            // 💥 記憶魔法 2：拖曳結束，把最新的座標寫進系統儲存！
                            prefs.edit()
                                 .putInt("btn_x", layoutParams.leftMargin)
                                 .putInt("btn_y", layoutParams.topMargin)
                                 .apply();
                        }
                        return true;
                }
                return false;
            }
        });

        layout.addView(btn);
    }

    // ==========================================
    // 資料庫撈取邏輯 (跑在背景)
    // ==========================================
    private String buildReadDataString() {
        StringBuilder sb = new StringBuilder();
        try {
            // 1. 先把這個聊天室所有的「讀者MID」跟「最新讀到的訊息ID」撈出來
            Map<String, Long> watermarks = new HashMap<>();
            Cursor cWater = db_line.rawQuery("SELECT reader_mid, max_msg_id FROM lime_read_watermarks WHERE chat_id = ?", new String[]{currentChatId});
            while (cWater.moveToNext()) {
                watermarks.put(cWater.getString(0), cWater.getLong(1));
            }
            cWater.close();

            // 2. 撈出「我」在這個聊天室最近傳送的 15 則文字訊息 (from_mid IS NULL 通常代表自己)
            Cursor cMsg = db_line.rawQuery("SELECT server_id, content, created_time FROM chat_history WHERE chat_id = ? AND from_mid IS NULL AND type = 1 ORDER BY CAST(server_id AS INTEGER) DESC LIMIT 15", new String[]{currentChatId});
            
            if (cMsg.getCount() == 0) {
                cMsg.close();
                return "最近沒有傳送文字訊息，或資料庫尚未同步。";
            }

            while (cMsg.moveToNext()) {
                long serverId = cMsg.getLong(0);
                String content = cMsg.getString(1);
                String timeStr = cMsg.getString(2);
                
                sb.append("💬 ").append(content).append("\n");
                sb.append("🕒 ").append(formatTime(timeStr)).append("\n");

                // 3. 比對誰的「最新已讀 ID」 大於等於 這則訊息的 ID
                List<String> readers = new ArrayList<>();
                for (Map.Entry<String, Long> entry : watermarks.entrySet()) {
                    if (entry.getValue() >= serverId) {
                        // 這個人已讀了！去聯絡人資料庫找他的大名
                        String name = getContactName(entry.getKey());
                        readers.add(name != null ? name : "未知名稱");
                    }
                }

                if (readers.isEmpty()) {
                    sb.append("❌ 尚無人已讀\n\n");
                } else {
                    sb.append("👀 已讀 (").append(readers.size()).append(")：\n");
                    for (String r : readers) {
                        sb.append("  - ").append(r).append("\n");
                    }
                    sb.append("\n");
                }
            }
            cMsg.close();
            
        } catch (Exception e) {
            sb.append("發生錯誤：").append(e.getMessage());
        }
        return sb.toString();
    }

    private String getContactName(String mid) {
        try {
            Cursor c = db_contact.rawQuery("SELECT profile_name FROM contacts WHERE mid = ?", new String[]{mid});
            if (c.moveToFirst()) {
                String name = c.getString(0);
                c.close();
                return name;
            }
            c.close();
        } catch (Exception e) {}
        return null;
    }

    private String extractParam(String operation, String prefix) {
        try {
            // 💥 完美防護：只抓取連續的英文與數字，自動把後面的 )] 或逗號全部過濾掉！
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(prefix + "([a-zA-Z0-9]+)");
            java.util.regex.Matcher matcher = pattern.matcher(operation);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {}
        return null;
    }

    private String formatTime(String epochStr) {
        try {
            long time = Long.parseLong(epochStr);
            return new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(time));
        } catch (Exception e) {
            return "未知時間";
        }
    }
}
