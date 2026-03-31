package io.github.hiro.lime.hooks;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import io.github.hiro.lime.LimeModule;
import io.github.hiro.lime.LimeOptions;

public class UnsentRec implements IHook {
    @Override
    public void hook(LimeModule module, ClassLoader classLoader, LimeOptions limeOptions) throws Throwable {
        try {
            Method onCreateMethod = Application.class.getDeclaredMethod("onCreate");
            module.hook(onCreateMethod).intercept(chain -> {
                Object result = chain.proceed();
                Application appContext = (Application) chain.getThisObject();
                if (appContext == null) return result;

                File dbFile1 = appContext.getDatabasePath("naver_line");
                if (dbFile1.exists()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        SQLiteDatabase.OpenParams params = new SQLiteDatabase.OpenParams.Builder()
                                .addOpenFlags(SQLiteDatabase.OPEN_READWRITE)
                                .build();
                        try {
                            SQLiteDatabase db1 = SQLiteDatabase.openDatabase(dbFile1, params);
                            hookMessageDeletion(module, classLoader, db1);
                        } catch (Exception e) {
                            module.log(4, "LIMEs", "UnsentRec 開啟資料庫失敗: " + e.getMessage());
                        }
                    }
                }
                return result;
            });
        } catch (Exception e) {
            module.log(4, "LIMEs", "UnsentRec (onCreate) 失敗: " + e.getMessage());
        }
    }

    private void hookMessageDeletion(LimeModule module, ClassLoader classLoader, SQLiteDatabase db1) {
        try {
            Class<?> responseClass = classLoader.loadClass(Constants.RESPONSE_HOOK.className);
            for (Method method : responseClass.getDeclaredMethods()) {
                if (method.getName().equals(Constants.RESPONSE_HOOK.methodName)) {
                    module.hook(method).intercept(chain -> {
                        Object result = chain.proceed();
                        List<Object> args = chain.getArgs();
                        if (args == null || args.size() < 2 || args.get(1) == null) return result;

                        String paramValue = args.get(1).toString();
                        if (paramValue.contains("type:NOTIFIED_DESTROY_MESSAGE,")) {
                            processMessage(module, paramValue, db1);
                        }
                        return result;
                    });
                }
            }
        } catch (Exception e) {
            module.log(4, "LIMEs", "UnsentRec 找不到 Response Class");
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
                    if (part.startsWith("type:")) type = part.substring("type:".length()).trim();
                }

                if ("NOTIFIED_DESTROY_MESSAGE".equals(type)) {
                    for (String part : parts) {
                        part = part.trim();
                        if (part.startsWith("param2:")) {
                            serverId = part.substring("param2:".length()).trim();
                            break;
                        }
                    }
                    if (serverId != null) updateMessageAsCanceled(module, db1, serverId);
                }
            }
        } catch (Exception e) {
            module.log(4, "LIMEs", "UnsentRec 處理失敗: " + e.getMessage());
        }
    }

    private void updateMessageAsCanceled(LimeModule module, SQLiteDatabase db1, String serverId) {
        String canceledContent = "🚫 對方嘗試收回訊息";
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
                        values.put("type", "1");
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
                        module.log(2, "LIMEs", "UnsentRec: 成功寫入防收回紀錄！");
                    }
                }
            }
        } catch (Exception e) {
            module.log(4, "LIMEs", "UnsentRec 寫入失敗: " + e.getMessage());
        }
    }
}
