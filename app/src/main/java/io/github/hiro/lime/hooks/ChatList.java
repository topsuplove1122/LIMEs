package io.github.hiro.lime.hooks;

import android.app.Application;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import java.io.File;

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

                File dbFile = appContext.getDatabasePath("naver_line");
                if (dbFile.exists()) {
                    SQLiteDatabase.OpenParams.Builder builder = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        builder = new SQLiteDatabase.OpenParams.Builder();
                        builder.addOpenFlags(SQLiteDatabase.OPEN_READWRITE);
                        SQLiteDatabase.OpenParams dbParams = builder.build();
                        SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile, dbParams);

                        // ==========================================
                        // 💥 核心魔法：資料庫層級的絕對防禦 (零延遲、無閃爍)
                        // ==========================================
                        try {
                            // 1. 在 LINE 的資料庫裡，偷偷建一個我們專用的「隱藏黑名單」表格
                            db.execSQL("CREATE TABLE IF NOT EXISTS lime_hidden_chats (chat_id TEXT PRIMARY KEY)");
                            
                            // 2. 埋入 SQLite 觸發器 (Trigger)
                            // 只要 LINE 試圖把隱藏狀態改回 0，且這個 ID 在我們的黑名單裡，底層引擎就會瞬間把它改回 1！
                            db.execSQL("CREATE TRIGGER IF NOT EXISTS enforce_hide " +
                                    "AFTER UPDATE OF is_archived ON chat " +
                                    "FOR EACH ROW " +
                                    "WHEN NEW.is_archived = 0 AND EXISTS (SELECT 1 FROM lime_hidden_chats WHERE chat_id = NEW.chat_id) " +
                                    "BEGIN " +
                                    "UPDATE chat SET is_archived = 1 WHERE chat_id = NEW.chat_id; " +
                                    "END;");
                        } catch (Exception e) {
                            XposedBridge.log("Lime: Trigger creation failed: " + e.getMessage());
                        }

                        // 啟動網路指令攔截器
                        hookMessageDeletion(loadPackageParam, db);
                    }
                }
            }
        });
    }

    private void hookMessageDeletion(XC_LoadPackage.LoadPackageParam loadPackageParam, SQLiteDatabase db) throws ClassNotFoundException {
        XposedBridge.hookAllMethods(
                loadPackageParam.classLoader.loadClass(Constants.REQUEST_HOOK.className),
                Constants.REQUEST_HOOK.methodName,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        String paramValue = param.args[1].toString();
                        
                        // 當你「手動隱藏」聊天室時...
                        if (paramValue.contains("hidden:true")) {
                            String talkId = extractTalkId(paramValue);
                            if (talkId != null) {
                                try {
                                    // 寫入我們的專屬黑名單，並立刻隱藏
                                    db.execSQL("INSERT OR IGNORE INTO lime_hidden_chats (chat_id) VALUES (?)", new Object[]{talkId});
                                    db.execSQL("UPDATE chat SET is_archived = 1 WHERE chat_id = ?", new Object[]{talkId});
                                } catch (Exception e) {}
                            }
                        }
                        
                        // 當你「手動解除隱藏」聊天室時...
                        if (paramValue.contains("hidden:false")) {
                            String talkId = extractTalkId(paramValue);
                            if (talkId != null) {
                                try {
                                    // 從黑名單中刪除，並解除隱藏
                                    db.execSQL("DELETE FROM lime_hidden_chats WHERE chat_id = ?", new Object[]{talkId});
                                    db.execSQL("UPDATE chat SET is_archived = 0 WHERE chat_id = ?", new Object[]{talkId});
                                } catch (Exception e) {}
                            }
                        }
                    }
                });
    }

    private String extractTalkId(String paramValue) {
        String talkId = null;
        String requestPrefix = "setChatHiddenStatusRequest:SetChatHiddenStatusRequest(reqSeq:0, chatMid:";
        int startIndex = paramValue.indexOf(requestPrefix);

        if (startIndex != -1) {
            int chatMidStartIndex = startIndex + requestPrefix.length();
            int endIndex = paramValue.indexOf(",", chatMidStartIndex);
            if (endIndex == -1) endIndex = paramValue.indexOf(")", chatMidStartIndex);
            if (endIndex != -1) talkId = paramValue.substring(chatMidStartIndex, endIndex).trim();
        }
        return talkId;
    }
}
