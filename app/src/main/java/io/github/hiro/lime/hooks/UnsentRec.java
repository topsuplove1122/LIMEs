package io.github.hiro.lime.hooks;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import androidx.annotation.NonNull;

import java.io.File;
import java.lang.reflect.Method;

// 替換舊版依賴
import io.github.hiro.lime.Constants;
import io.github.hiro.lime.LimeModule;
import io.github.hiro.lime.LimeOptions;
import io.github.libxposed.api.XposedInterface;

public class UnsentRec implements IHook {

    @Override
    public void hook(LimeModule module, ClassLoader classLoader, LimeOptions limeOptions) throws Throwable {
        
        try {
            // 尋找 Application 的 onCreate 方法
            Method onCreateMethod = Application.class.getDeclaredMethod("onCreate");

            module.hook(onCreateMethod, new XposedInterface.Hooker() {
                @Override
                public void beforeInvoke(@NonNull XposedInterface.BeforeHookCallback callback) {}

                @Override
                public void afterInvoke(@NonNull XposedInterface.AfterHookCallback callback) {
                    // 取得 Application Context
                    Application appContext = (Application) callback.getThisObject();
                    if (appContext == null) return;

                    File dbFile1 = appContext.getDatabasePath("naver_line");
                    if (dbFile1.exists()) {
                        SQLiteDatabase.OpenParams.Builder builder1 = null;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            builder1 = new SQLiteDatabase.OpenParams.Builder();
                            builder1.addOpenFlags(SQLiteDatabase.OPEN_READWRITE);
                            
                            try {
                                SQLiteDatabase db1 = SQLiteDatabase.openDatabase(dbFile1, builder1.build());
                                // 啟動攔截器
                                hookMessageDeletion(module, classLoader, db1);
                            } catch (Exception e) {
                                module.log("UnsentRec 開啟資料庫失敗: " + e.getMessage());
                            }
                        }
                    }
                }
            });
        } catch (Exception e) {
            module.log("UnsentRec (onCreate) Hook 失敗: " + e.getMessage());
        }
    }

    private void hookMessageDeletion(LimeModule module, ClassLoader classLoader, SQLiteDatabase db1) {
        try {
            Class<?> responseClass = classLoader.loadClass(Constants.RESPONSE_HOOK.className);
            
            // 遍歷目標類別中的所有方法，尋找 RESPONSE_HOOK 
            for (Method method : responseClass.getDeclaredMethods()) {
                if (method.getName().equals(Constants.RESPONSE_HOOK.methodName)) {
                    
                    module.hook(method, new XposedInterface.Hooker() {
                        @Override
                        public void beforeInvoke(@NonNull XposedInterface.BeforeHookCallback callback) {}

                        @Override
                        public void afterInvoke(@NonNull XposedInterface.AfterHookCallback callback) {
                            Object[] args = callback.getArgs();
                            // 加入嚴謹的 null 檢查，避免 LINE 更新後結構改變導致崩潰
                            if (args == null || args.length < 2 || args[1] == null) return;

                            String paramValue = args[1].toString();
                            if (paramValue.contains("type:NOTIFIED_DESTROY_MESSAGE,")) {
                                processMessage(module, paramValue, db1);
                            }
                        }
                    });
                }
            }
        } catch (ClassNotFoundException e) {
            module.log("UnsentRec 找不到 Response Class");
        }
    }

    private void processMessage(LimeModule module, String paramValue, SQLiteDatabase db1) {
        try {
            String[] operations = paramValue.split("Operation\\(");
            for (String operation : operations) {
                if (operation.trim().isEmpty()) continue;
                String type = null;
                String serverId = null;

                String[] parts = operation.split(",");
                for (String part : parts) {
                    part = part.trim();
                    if (part.startsWith("type:")) {
                        type = part.substring("type:".length()).trim();
                    }
                }

                if ("NOTIFIED_DESTROY_MESSAGE".equals(type)) {
                    for (String part : parts) {
                        part = part.trim();
                        if (part.startsWith("param2:")) {
                            serverId = part.substring("param2:".length()).trim();
                            break;
                        }
                    }
                    if (serverId != null) {
                        updateMessageAsCanceled(module, db1, serverId);
                    }
                }
            }
        } catch (Exception e) {
            module.log("UnsentRec 處理訊息失敗: " + e.getMessage());
        }
    }

    private void updateMessageAsCanceled(LimeModule module, SQLiteDatabase db1, String serverId) {
        // 💥 直接寫死你想要的註記文字
        String canceledContent = "🚫 對方嘗試收回訊息";

        // 使用 try-with-resources 確保 Cursor 無論如何都會被關閉，避免 Memory Leak
        try (Cursor cursor = db1.rawQuery("SELECT * FROM chat_history WHERE server_id=?", new String[]{serverId})) {
            
            if (cursor.moveToFirst()) {
                @SuppressLint("Range") String chatId = cursor.isNull(cursor.getColumnIndex("chat_id")) ? null : cursor.getString(cursor.getColumnIndex("chat_id"));
                @SuppressLint("Range") String fromMid = cursor.isNull(cursor.getColumnIndex("from_mid")) ? null : cursor.getString(cursor.getColumnIndex("from_mid"));
                @SuppressLint("Range") String createdTime = cursor.isNull(cursor.getColumnIndex("created_time")) ? null : cursor.getString(cursor.getColumnIndex("created_time"));
                @SuppressLint("Range") String deliveredTime = cursor.isNull(cursor.getColumnIndex("delivered_time")) ? null : cursor.getString(cursor.getColumnIndex("delivered_time"));

                try (Cursor existingCursor = db1.rawQuery("SELECT * FROM chat_history WHERE server_id=? AND content=?", new String[]{serverId, canceledContent})) {
                    
                    if (!existingCursor.moveToFirst()) {
                        ContentValues values = new ContentValues();
                        values.put("server_id", serverId);
                        values.put("type", "1"); // 1 代表一般文字訊息
                        values.put("chat_id", chatId);
                        values.put("from_mid", fromMid);
                        values.put("content", canceledContent);
                        values.put("created_time", createdTime);
                        values.put("delivered_time", deliveredTime);
                        values.put("status", "1");
                        values.put("attachement_image", "0");
                        values.put("attachement_type", "0");
                        values.put("parameter", "LIMEsUnsend");

                        db1.insert("chat_history", null, values);
                        module.log("UnsentRec: 成功寫入防收回紀錄！");
                    }
                }
            }
        } catch (Exception e) {
            module.log("UnsentRec 寫入資料庫失敗: " + e.getMessage());
        }
    }
}
