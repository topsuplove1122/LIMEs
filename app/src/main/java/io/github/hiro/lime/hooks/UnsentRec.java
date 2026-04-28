package io.github.hiro.lime.hooks;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.hiro.lime.LimeOptions;

public class UnsentRec implements IHook {
    @Override
    public void hook(LimeOptions limeOptions, XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        XposedBridge.hookAllMethods(Application.class, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Application appContext = (Application) param.thisObject;
                if (appContext == null) return;

                File dbFile1 = appContext.getDatabasePath("naver_line");
                if (dbFile1.exists()) {
                    SQLiteDatabase.OpenParams.Builder builder1 = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        builder1 = new SQLiteDatabase.OpenParams.Builder();
                        builder1.addOpenFlags(SQLiteDatabase.OPEN_READWRITE);
                        SQLiteDatabase db1 = SQLiteDatabase.openDatabase(dbFile1, builder1.build());
                        
                        hookMessageDeletion(loadPackageParam, db1);
                    }
                }
            }
        });
    }

    private void hookMessageDeletion(XC_LoadPackage.LoadPackageParam loadPackageParam, SQLiteDatabase db1) {
        try {
            XposedBridge.hookAllMethods(
                    loadPackageParam.classLoader.loadClass(Constants.RESPONSE_HOOK.className),
                    Constants.RESPONSE_HOOK.methodName,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String paramValue = param.args[1].toString();
                            
                            if (paramValue.contains("type:NOTIFIED_DESTROY_MESSAGE")) {
                                // 💥 完美切割封包，避免多個操作黏在一起
                                String[] operations = paramValue.split("Operation\\(");
                                for (String op : operations) {
                                    if (op.contains("type:NOTIFIED_DESTROY_MESSAGE")) {
                                        // 用正則表達式精準抓取純數字的 Server ID
                                        String serverId = extractParam(op, "param2:");
                                        if (serverId != null) {
                                            updateMessageAsCanceled(db1, serverId);
                                            XposedBridge.log("Lime: Canceled Message ID grabbed -> " + serverId);
                                        }
                                    }
                                }
                            }
                        }
                    });
        } catch (ClassNotFoundException ignored) {}
    }

    // 💥 防彈正則擷取引擎
    private String extractParam(String operation, String prefix) {
        try {
            Pattern pattern = Pattern.compile(prefix + "([0-9]+)");
            Matcher matcher = pattern.matcher(operation);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {}
        return null;
    }

    private void updateMessageAsCanceled(SQLiteDatabase db1, String serverId) {
        String canceledContent = "🚫 對方嘗試收回訊息";

        Cursor cursor = db1.rawQuery("SELECT * FROM chat_history WHERE server_id=?", new String[]{serverId});
        if (cursor.moveToFirst()) {
            @SuppressLint("Range") String chatId = cursor.isNull(cursor.getColumnIndex("chat_id")) ? null : cursor.getString(cursor.getColumnIndex("chat_id"));
            @SuppressLint("Range") String fromMid = cursor.isNull(cursor.getColumnIndex("from_mid")) ? null : cursor.getString(cursor.getColumnIndex("from_mid"));
            @SuppressLint("Range") String createdTime = cursor.isNull(cursor.getColumnIndex("created_time")) ? null : cursor.getString(cursor.getColumnIndex("created_time"));
            @SuppressLint("Range") String deliveredTime = cursor.isNull(cursor.getColumnIndex("delivered_time")) ? null : cursor.getString(cursor.getColumnIndex("delivered_time"));

            Cursor existingCursor = db1.rawQuery("SELECT * FROM chat_history WHERE server_id=? AND content=?", new String[]{serverId, canceledContent});
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
            }
            existingCursor.close();
        }
        cursor.close();
    }
}
                }
            }
        });
    }

    private void hookMessageDeletion(XC_LoadPackage.LoadPackageParam loadPackageParam, SQLiteDatabase db1) {
        try {
            XposedBridge.hookAllMethods(
                    loadPackageParam.classLoader.loadClass(Constants.RESPONSE_HOOK.className),
                    Constants.RESPONSE_HOOK.methodName,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String paramValue = param.args[1].toString();
                            if (paramValue.contains("type:NOTIFIED_DESTROY_MESSAGE,")) {
                                processMessage(paramValue, db1);
                            }
                        }
                    });
        } catch (ClassNotFoundException ignored) {}
    }

    private void processMessage(String paramValue, SQLiteDatabase db1) {
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
                    updateMessageAsCanceled(db1, serverId);
                }
            }
        }
    }

    private void updateMessageAsCanceled(SQLiteDatabase db1, String serverId) {
        // 💥 直接寫死你想要的註記文字
        String canceledContent = "🚫 對方嘗試收回訊息";

        Cursor cursor = db1.rawQuery("SELECT * FROM chat_history WHERE server_id=?", new String[]{serverId});
        if (cursor.moveToFirst()) {
            @SuppressLint("Range") String chatId = cursor.isNull(cursor.getColumnIndex("chat_id")) ? null : cursor.getString(cursor.getColumnIndex("chat_id"));
            @SuppressLint("Range") String fromMid = cursor.isNull(cursor.getColumnIndex("from_mid")) ? null : cursor.getString(cursor.getColumnIndex("from_mid"));
            @SuppressLint("Range") String createdTime = cursor.isNull(cursor.getColumnIndex("created_time")) ? null : cursor.getString(cursor.getColumnIndex("created_time"));
            @SuppressLint("Range") String deliveredTime = cursor.isNull(cursor.getColumnIndex("delivered_time")) ? null : cursor.getString(cursor.getColumnIndex("delivered_time"));

            Cursor existingCursor = db1.rawQuery("SELECT * FROM chat_history WHERE server_id=? AND content=?", new String[]{serverId, canceledContent});
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
            }
            existingCursor.close();
        }
        cursor.close();
    }
}
