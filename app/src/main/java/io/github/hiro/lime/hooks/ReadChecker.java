package io.github.hiro.lime.hooks;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.io.File;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.hiro.lime.hooks.Constants;
import io.github.hiro.lime.LimeModule;
import io.github.hiro.lime.LimeOptions;
import io.github.libxposed.api.XposedInterface;

public class ReadChecker implements IHook {
    private SQLiteDatabase db_line = null;
    private SQLiteDatabase db_contact = null;
    private String currentChatId = null;

    @Override
    public void hook(LimeModule module, ClassLoader classLoader, LimeOptions limeOptions) throws Throwable {

        // 1. 初始化資料庫與攔截封包 (Hook Application.onCreate)
        try {
            Method onCreateMethod = Application.class.getDeclaredMethod("onCreate");
            module.hook(onCreateMethod, new XposedInterface.Hooker() {
                @Override
                // 【修正】回傳改為 Object
                public Object intercept(@NonNull XposedInterface.BeforeHookCallback<?> callback) throws Throwable {
                    // 【修正】執行原本方法並保留回傳值
                    Object result = callback.callOriginal();
                    
                    Application appContext = (Application) callback.getThisObject();
                    if (appContext == null) return result;

                    File dbFileLine = appContext.getDatabasePath("naver_line");
                    File dbFileContact = appContext.getDatabasePath("contact");

                    if (dbFileLine.exists() && dbFileContact.exists()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            SQLiteDatabase.OpenParams params = new SQLiteDatabase.OpenParams.Builder()
                                    .addOpenFlags(SQLiteDatabase.OPEN_READWRITE)
                                    .build();

                            db_line = SQLiteDatabase.openDatabase(dbFileLine, params);
                            db_contact = SQLiteDatabase.openDatabase(dbFileContact, params);

                            try {
                                db_line.execSQL("CREATE TABLE IF NOT EXISTS lime_read_watermarks (chat_id TEXT, reader_mid TEXT, max_msg_id INTEGER, PRIMARY KEY(chat_id, reader_mid))");
                            } catch (Exception ignored) {}

                            hookNetwork(module, classLoader);
                        }
                    }
                    return result;
                }
            });
        } catch (Exception e) {
            module.log(XposedInterface.LOG_LEVEL_ERROR, "LIMEs", "ReadChecker (onCreate) 失敗: " + e.getMessage());
        }

        // 2. 獲取目前進入的聊天室 ID (Hook ChatHistoryRequest.getChatId)
        try {
            Class<?> chatHistoryRequestClass = classLoader.loadClass("com.linecorp.line.chat.request.ChatHistoryRequest");
            Method getChatIdMethod = chatHistoryRequestClass.getDeclaredMethod("getChatId");
            
            module.hook(getChatIdMethod, new XposedInterface.Hooker() {
                @Override
                public Object intercept(@NonNull XposedInterface.BeforeHookCallback<?> callback) throws Throwable {
                    Object result = callback.callOriginal();
                    currentChatId = (String) callback.getResult();
                    return result;
                }
            });
        } catch (Exception e) {
            module.log(XposedInterface.LOG_LEVEL_ERROR, "LIMEs", "ReadChecker (getChatId) 失敗: " + e.getMessage());
        }

        // 3. 加入按鈕 (Hook ChatHistoryActivity.onCreate)
        try {
            Class<?> chatHistoryActivityClass = classLoader.loadClass("jp.naver.line.android.activity.chathistory.ChatHistoryActivity");
            Method activityOnCreate = chatHistoryActivityClass.getDeclaredMethod("onCreate", Bundle.class);

            module.hook(activityOnCreate, new XposedInterface.Hooker() {
                @Override
                public Object intercept(@NonNull XposedInterface.BeforeHookCallback<?> callback) throws Throwable {
                    Object result = callback.callOriginal();
                    Activity activity = (Activity) callback.getThisObject();
                    if (currentChatId != null) {
                        addTopButton(activity);
                    }
                    return result;
                }
            });
        } catch (Exception e) {
            module.log(XposedInterface.LOG_LEVEL_ERROR, "LIMEs", "ReadChecker (Activity) 失敗: " + e.getMessage());
        }
    }

    private void hookNetwork(LimeModule module, ClassLoader classLoader) {
        try {
            Class<?> responseClass = classLoader.loadClass(Constants.RESPONSE_HOOK.className);
            for (Method method : responseClass.getDeclaredMethods()) {
                if (method.getName().equals(Constants.RESPONSE_HOOK.methodName)) {
                    module.hook(method, new XposedInterface.Hooker() {
                        @Override
                        public Object intercept(@NonNull XposedInterface.BeforeHookCallback<?> callback) throws Throwable {
                            Object result = callback.callOriginal();
                            
                            Object[] args = callback.getArgs();
                            if (args == null || args.length < 2 || args[1] == null) return result;

                            String paramValue = args[1].toString();
                            if (paramValue.contains("type:NOTIFIED_READ_MESSAGE")) {
                                String[] operations = paramValue.split("Operation\\(");
                                for (String op : operations) {
                                    if (op.contains("type:NOTIFIED_READ_MESSAGE")) {
                                        String readerMid = extractParam(op, "param2:");
                                        String msgIdStr = extractParam(op, "param3:");

                                        if (readerMid != null && msgIdStr != null && db_line != null) {
                                            try {
                                                long msgId = Long.parseLong(msgIdStr);
                                                try (Cursor c = db_line.rawQuery("SELECT chat_id FROM chat_history WHERE server_id = ?", new String[]{msgIdStr})) {
                                                    if (c.moveToFirst()) {
                                                        String exactChatId = c.getString(0);
                                                        db_line.execSQL("INSERT OR REPLACE INTO lime_read_watermarks (chat_id, reader_mid, max_msg_id) VALUES (?, ?, ?)",
                                                                new Object[]{exactChatId, readerMid, msgId});
                                                    }
                                                }
                                            } catch (Exception e) {
                                                module.log(XposedInterface.LOG_LEVEL_ERROR, "LIMEs", "ReadChecker Network Error: " + e.getMessage());
                                            }
                                        }
                                    }
                                }
                            }
                            return result;
                        }
                    });
                }
            }
        } catch (Exception e) {
            module.log(XposedInterface.LOG_LEVEL_ERROR, "LIMEs", "ReadChecker (Network) 失敗: " + e.getMessage());
        }
    }

    // 後續 UI 與邏輯保持不變...
    private void addTopButton(Activity activity) {
        final int BUTTON_ID = 95279527;
        ViewGroup layout = activity.findViewById(android.R.id.content);
        if (layout == null || activity.findViewById(BUTTON_ID) != null) return;

        Button btn = new Button(activity);
        btn.setId(BUTTON_ID);
        btn.setText("👀");
        btn.setBackgroundColor(android.graphics.Color.parseColor("#AA000000"));
        btn.setTextColor(android.graphics.Color.WHITE);
        btn.setTextSize(12);

        android.content.SharedPreferences prefs = activity.getSharedPreferences("LimeReadCheckerPrefs", android.content.Context.MODE_PRIVATE);
        int savedX = prefs.getInt("btn_x", 150);
        int savedY = prefs.getInt("btn_y", 180);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(savedX, savedY, 0, 0);
        btn.setLayoutParams(params);

        btn.setOnClickListener(v -> {
            if (currentChatId == null || db_line == null || db_contact == null) return;
            Toast.makeText(activity, "資料撈取中...", Toast.LENGTH_SHORT).show();
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

        btn.setOnTouchListener(new android.view.View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isMoved = false;
            @Override
            public boolean onTouch(android.view.View v, android.view.MotionEvent event) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) v.getLayoutParams();
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        initialX = lp.leftMargin; initialY = lp.topMargin;
                        initialTouchX = event.getRawX(); initialTouchY = event.getRawY();
                        isMoved = false; return true;
                    case android.view.MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isMoved = true;
                        lp.leftMargin = initialX + dx; lp.topMargin = initialY + dy;
                        v.setLayoutParams(lp); return true;
                    case android.view.MotionEvent.ACTION_UP:
                        if (!isMoved) v.performClick();
                        else prefs.edit().putInt("btn_x", lp.leftMargin).putInt("btn_y", lp.topMargin).apply();
                        return true;
                }
                return false;
            }
        });
        layout.addView(btn);
    }

    private String buildReadDataString() {
        StringBuilder sb = new StringBuilder();
        try {
            Map<String, Long> watermarks = new HashMap<>();
            try (Cursor cWater = db_line.rawQuery("SELECT reader_mid, max_msg_id FROM lime_read_watermarks WHERE chat_id = ?", new String[]{currentChatId})) {
                while (cWater.moveToNext()) { watermarks.put(cWater.getString(0), cWater.getLong(1)); }
            }
            try (Cursor cMsg = db_line.rawQuery("SELECT server_id, content, created_time FROM chat_history WHERE chat_id = ? AND from_mid IS NULL AND type = 1 ORDER BY CAST(server_id AS INTEGER) DESC LIMIT 15", new String[]{currentChatId})) {
                if (cMsg.getCount() == 0) return "最近沒有傳送文字訊息。";
                while (cMsg.moveToNext()) {
                    long serverId = cMsg.getLong(0);
                    sb.append("💬 ").append(cMsg.getString(1)).append("\n🕒 ").append(formatTime(cMsg.getString(2))).append("\n");
                    List<String> readers = new ArrayList<>();
                    for (Map.Entry<String, Long> entry : watermarks.entrySet()) {
                        if (entry.getValue() >= serverId) {
                            String name = getContactName(entry.getKey());
                            readers.add(name != null ? name : "未知名稱");
                        }
                    }
                    if (readers.isEmpty()) sb.append("❌ 尚無人已讀\n\n");
                    else {
                        sb.append("👀 已讀 (").append(readers.size()).append(")：\n");
                        for (String r : readers) sb.append("  - ").append(r).append("\n");
                        sb.append("\n");
                    }
                }
            }
        } catch (Exception e) { sb.append("發生錯誤：").append(e.getMessage()); }
        return sb.toString();
    }

    private String getContactName(String mid) {
        try (Cursor c = db_contact.rawQuery("SELECT profile_name FROM contacts WHERE mid = ?", new String[]{mid})) {
            if (c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {}
        return null;
    }

    private String extractParam(String operation, String prefix) {
        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(prefix + "([a-zA-Z0-9]+)");
            java.util.regex.Matcher matcher = pattern.matcher(operation);
            if (matcher.find()) return matcher.group(1);
        } catch (Exception ignored) {}
        return null;
    }

    private String formatTime(String epochStr) {
        try {
            return new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(Long.parseLong(epochStr)));
        } catch (Exception e) { return "未知時間"; }
    }
}
