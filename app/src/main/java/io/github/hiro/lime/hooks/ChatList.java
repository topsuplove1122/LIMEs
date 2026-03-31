package io.github.hiro.lime.hooks;

import android.app.Application;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 1. 移除了所有 de.robv.android.xposed 的依賴
import io.github.hiro.lime.Constants;
import io.github.hiro.lime.LimeModule;
import io.github.hiro.lime.LimeOptions;
import io.github.libxposed.api.XposedInterface;

public class ChatList implements IHook {
    
    // 2. 套用新的介面簽名，接收 module, classLoader, limeOptions
    @Override
    public void hook(LimeModule module, ClassLoader classLoader, LimeOptions limeOptions) throws Throwable {
        
        try {
            // 尋找 Application 的 onCreate 方法
            Method onCreateMethod = Application.class.getDeclaredMethod("onCreate");

            // 3. 使用 module.hook 進行掛載
            module.hook(onCreateMethod, new XposedInterface.Hooker() {
                @Override
                public void beforeInvoke(@NonNull XposedInterface.BeforeHookCallback callback) {
                    // 4. param.thisObject 變成了 callback.getThisObject()
                    Application appContext = (Application) callback.getThisObject();
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
                                // 5. XposedBridge.log 改為 module.log
                                module.log("Lime Trigger Error: " + e.getMessage());
                            }

                            // 執行後續的訊息攔截
                            hookMessageDeletion(module, classLoader, db);
                        }
                    }
                }

                @Override
                public void afterInvoke(@NonNull XposedInterface.AfterHookCallback callback) {
                }
            });
        } catch (Exception e) {
            module.log("ChatList onCreate Hook 失敗: " + e.getMessage());
        }
    }

    // 傳入 module 與 classLoader 以便在內部使用
    private void hookMessageDeletion(LimeModule module, ClassLoader classLoader, SQLiteDatabase db) {
        try {
            // 攔截手動操作 (寫入黑名單)
            Class<?> requestClass = classLoader.loadClass(Constants.REQUEST_HOOK.className);
            
            // 6. 取代 hookAllMethods 的寫法：遍歷所有方法，找到同名的就 Hook
            for (Method method : requestClass.getDeclaredMethods()) {
                if (method.getName().equals(Constants.REQUEST_HOOK.methodName)) {
                    
                    module.hook(method, new XposedInterface.Hooker() {
                        @Override
                        public void beforeInvoke(@NonNull XposedInterface.BeforeHookCallback callback) {}

                        @Override
                        public void afterInvoke(@NonNull XposedInterface.AfterHookCallback callback) {
                            // 7. param.args[1] 變成了 callback.getArgs()[1]
                            Object[] args = callback.getArgs();
                            if (args == null || args.length < 2 || args[1] == null) return;
                            
                            String paramValue = args[1].toString();
                            
                            if (paramValue.contains("setChatHiddenStatusRequest")) {
                                String talkId = extractTalkId(paramValue);
                                if (talkId != null) {
                                    if (paramValue.contains("hiddenStatus:true") || paramValue.contains("hidden:true")) {
                                        db.execSQL("INSERT OR IGNORE INTO lime_hidden_chats (chat_id) VALUES (?)", new Object[]{talkId});
                                        db.execSQL("UPDATE chat SET is_archived = 1 WHERE chat_id = ?", new Object[]{talkId});
                                        module.log("Lime: Successfully Hid Chat " + talkId);
                                    } else if (paramValue.contains("hiddenStatus:false") || paramValue.contains("hidden:false")) {
                                        db.execSQL("DELETE FROM lime_hidden_chats WHERE chat_id = ?", new Object[]{talkId});
                                        db.execSQL("UPDATE chat SET is_archived = 0 WHERE chat_id = ?", new Object[]{talkId});
                                        module.log("Lime: Unhid Chat " + talkId);
                                    }
                                }
                            }
                        }
                    });
                }
            }

            // 網路同步兜底 (保底機制)
            Class<?> responseClass = classLoader.loadClass(Constants.RESPONSE_HOOK.className);
            for (Method method : responseClass.getDeclaredMethods()) {
                if (method.getName().equals(Constants.RESPONSE_HOOK.methodName)) {
                    
                    module.hook(method, new XposedInterface.Hooker() {
                        @Override
                        public void beforeInvoke(@NonNull XposedInterface.BeforeHookCallback callback) {}

                        @Override
                        public void afterInvoke(@NonNull XposedInterface.AfterHookCallback callback) {
                            try {
                                db.execSQL("UPDATE chat SET is_archived = 1 WHERE chat_id IN (SELECT chat_id FROM lime_hidden_chats)");
                            } catch (Exception ignored) {}
                        }
                    });
                }
            }

        } catch (ClassNotFoundException ignored) {
            module.log("ChatList 找不到 Request/Response Class");
        }
    }

    // 💥 完美字串擷取 (完全保留，不需修改)
    private String extractTalkId(String paramValue) {
        try {
            Pattern pattern = Pattern.compile("chatMid:([^,\\)]+)");
            Matcher matcher = pattern.matcher(paramValue);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
