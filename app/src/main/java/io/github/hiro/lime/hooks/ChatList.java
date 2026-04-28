package io.github.hiro.lime.hooks;

import android.app.Application;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.hiro.lime.LimeOptions;

public class ChatList implements IHook {
    @Override
    public void hook(LimeOptions limeOptions, XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        XposedBridge.hookAllMethods(Application.class, "onCreate", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Application appContext = (Application) param.thisObject;
                if (appContext == null) return;

                java.io.File dbFile = appContext.getDatabasePath("naver_line");
                if (dbFile.exists()) {
                    SQLiteDatabase.OpenParams.Builder builder = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        builder = new SQLiteDatabase.OpenParams.Builder();
                        builder.addOpenFlags(SQLiteDatabase.OPEN_READWRITE);
                        SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile, builder.build());

                        try {
                            db.execSQL("CREATE TABLE IF NOT EXISTS lime_hidden_chats (chat_id TEXT PRIMARY KEY)");
                            
                            // 如果這裡報錯，代表 is_archived 欄位被改名了！
                            db.execSQL("CREATE TRIGGER IF NOT EXISTS enforce_hide_on_msg " +
                                    "AFTER INSERT ON chat_history " +
                                    "FOR EACH ROW " +
                                    "WHEN EXISTS (SELECT 1 FROM lime_hidden_chats WHERE chat_id = NEW.chat_id) " +
                                    "BEGIN " +
                                    "UPDATE chat SET is_archived = 1 WHERE chat_id = NEW.chat_id; " +
                                    "END;");

                            db.execSQL("CREATE TRIGGER IF NOT EXISTS enforce_hide_on_update " +
                                    "AFTER UPDATE OF is_archived ON chat " +
                                    "FOR EACH ROW " +
                                    "WHEN NEW.is_archived = 0 AND EXISTS (SELECT 1 FROM lime_hidden_chats WHERE chat_id = NEW.chat_id) " +
                                    "BEGIN " +
                                    "UPDATE chat SET is_archived = 1 WHERE chat_id = NEW.chat_id; " +
                                    "END;");

                            db.execSQL("CREATE TRIGGER IF NOT EXISTS enforce_hide_on_insert_chat " +
                                    "AFTER INSERT ON chat " +
                                    "FOR EACH ROW " +
                                    "WHEN NEW.is_archived = 0 AND EXISTS (SELECT 1 FROM lime_hidden_chats WHERE chat_id = NEW.chat_id) " +
                                    "BEGIN " +
                                    "UPDATE chat SET is_archived = 1 WHERE chat_id = NEW.chat_id; " +
                                    "END;");
                        } catch (Exception e) {
                            // 💥 裝上雷達：如果資料庫寫入失敗，馬上印出紅字
                            XposedBridge.log("Lime Hide DB CRASH: " + e.getMessage());
                        }

                        hookMessageDeletion(loadPackageParam, db);
                    }
                }
            }
        });
    }

    private void hookMessageDeletion(XC_LoadPackage.LoadPackageParam loadPackageParam, SQLiteDatabase db) {
        try {
            XposedBridge.hookAllMethods(
                    loadPackageParam.classLoader.loadClass(Constants.REQUEST_HOOK.className),
                    Constants.REQUEST_HOOK.methodName,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String paramValue = param.args[1].toString();
                            
                            // 💥 裝上雷達：只要操作包含聊天室 ID，一律印出來看 LINE 改了什麼指令
                            if (paramValue.contains("chatMid:")) {
                                XposedBridge.log("Lime Hide Request Probe: " + paramValue);
                                
                                String talkId = extractTalkId(paramValue);
                                if (talkId != null) {
                                    // 盡量放寬條件，只要有 hide 或 hidden 相關字眼就抓
                                    if (paramValue.contains("true") && paramValue.toLowerCase().contains("hid")) {
                                        db.execSQL("INSERT OR IGNORE INTO lime_hidden_chats (chat_id) VALUES (?)", new Object[]{talkId});
                                        db.execSQL("UPDATE chat SET is_archived = 1 WHERE chat_id = ?", new Object[]{talkId});
                                        XposedBridge.log("Lime: Successfully Hid Chat " + talkId);
                                    } else if (paramValue.contains("false") && paramValue.toLowerCase().contains("hid")) {
                                        db.execSQL("DELETE FROM lime_hidden_chats WHERE chat_id = ?", new Object[]{talkId});
                                        db.execSQL("UPDATE chat SET is_archived = 0 WHERE chat_id = ?", new Object[]{talkId});
                                        XposedBridge.log("Lime: Unhid Chat " + talkId);
                                    }
                                }
                            }
                        }
                    });

            XposedBridge.hookAllMethods(
                    loadPackageParam.classLoader.loadClass(Constants.RESPONSE_HOOK.className),
                    Constants.RESPONSE_HOOK.methodName,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                db.execSQL("UPDATE chat SET is_archived = 1 WHERE chat_id IN (SELECT chat_id FROM lime_hidden_chats)");
                            } catch (Exception e) {}
                        }
                    });
        } catch (ClassNotFoundException ignored) {}
    }

    private String extractTalkId(String paramValue) {
        try {
            Pattern pattern = Pattern.compile("chatMid:([a-zA-Z0-9]+)");
            Matcher matcher = pattern.matcher(paramValue);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {}
        return null;
    }
}
