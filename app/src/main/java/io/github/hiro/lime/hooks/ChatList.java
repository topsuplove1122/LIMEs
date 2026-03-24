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
                            // 1. 建立專屬黑名單表格
                            db.execSQL("CREATE TABLE IF NOT EXISTS lime_hidden_chats (chat_id TEXT PRIMARY KEY)");
                            
                            // 2. 防閃爍觸發器 A：當新訊息抵達時，直接在底層封印
                            db.execSQL("CREATE TRIGGER IF NOT EXISTS enforce_hide_on_msg " +
                                    "AFTER INSERT ON chat_history " +
                                    "FOR EACH ROW " +
                                    "WHEN EXISTS (SELECT 1 FROM lime_hidden_chats WHERE chat_id = NEW.chat_id) " +
                                    "BEGIN " +
                                    "UPDATE chat SET is_archived = 1 WHERE chat_id = NEW.chat_id; " +
                                    "END;");

                            // 3. 防閃爍觸發器 B：攔截 LINE 自身的狀態更新
                            db.execSQL("CREATE TRIGGER IF NOT EXISTS enforce_hide_on_update " +
                                    "AFTER UPDATE OF is_archived ON chat " +
                                    "FOR EACH ROW " +
                                    "WHEN NEW.is_archived = 0 AND EXISTS (SELECT 1 FROM lime_hidden_chats WHERE chat_id = NEW.chat_id) " +
                                    "BEGIN " +
                                    "UPDATE chat SET is_archived = 1 WHERE chat_id = NEW.chat_id; " +
                                    "END;");
                        } catch (Exception e) {
                            XposedBridge.log("Lime Trigger Error: " + e.getMessage());
                        }

                        hookMessageDeletion(loadPackageParam, db);
                    }
                }
            }
        });
    }

    private void hookMessageDeletion(XC_LoadPackage.LoadPackageParam loadPackageParam, SQLiteDatabase db) {
        try {
            // 攔截你的手動操作 (寫入黑名單)
            XposedBridge.hookAllMethods(
                    loadPackageParam.classLoader.loadClass(Constants.REQUEST_HOOK.className),
                    Constants.REQUEST_HOOK.methodName,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String paramValue = param.args[1].toString();
                            
                            if (paramValue.contains("setChatHiddenStatusRequest")) {
                                String talkId = extractTalkId(paramValue);
                                if (talkId != null) {
                                    if (paramValue.contains("hiddenStatus:true") || paramValue.contains("hidden:true")) {
                                        db.execSQL("INSERT OR IGNORE INTO lime_hidden_chats (chat_id) VALUES (?)", new Object[]{talkId});
                                        db.execSQL("UPDATE chat SET is_archived = 1 WHERE chat_id = ?", new Object[]{talkId});
                                        XposedBridge.log("Lime: Successfully Hid Chat " + talkId);
                                    } else if (paramValue.contains("hiddenStatus:false") || paramValue.contains("hidden:false")) {
                                        db.execSQL("DELETE FROM lime_hidden_chats WHERE chat_id = ?", new Object[]{talkId});
                                        db.execSQL("UPDATE chat SET is_archived = 0 WHERE chat_id = ?", new Object[]{talkId});
                                        XposedBridge.log("Lime: Unhid Chat " + talkId);
                                    }
                                }
                            }
                        }
                    });

            // 網路同步兜底 (保底機制)
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

    // 💥 完美字串擷取：使用正則表達式，再也不怕序號變動！
    private String extractTalkId(String paramValue) {
        try {
            Pattern pattern = Pattern.compile("chatMid:([^,\\)]+)");
            Matcher matcher = pattern.matcher(paramValue);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        } catch (Exception e) {}
        return null;
    }
}
