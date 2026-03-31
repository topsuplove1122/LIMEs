package io.github.hiro.lime.hooks; // 修正 P 為小寫

import android.app.Application;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.hiro.lime.hooks.Constants;
import io.github.hiro.lime.LimeModule;
import io.github.hiro.lime.LimeOptions;
import io.github.libxposed.api.XposedInterface;

public class ChatList implements IHook {

    @Override
    public void hook(LimeModule module, ClassLoader classLoader, LimeOptions limeOptions) throws Throwable {
        
        try {
            // 尋找 Application 的 onCreate 方法
            Method onCreateMethod = Application.class.getDeclaredMethod("onCreate");

            // 🛠️ 修正 1：改用 API 101 的 Chain 模式
            module.hook(onCreateMethod, new XposedInterface.Hooker() {
                @Override
                public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
                    // 1. 執行原始方法 (手動呼叫並獲取回傳值)
                    Object result = chain.proceed();
                    
                    Application appContext = (Application) chain.getThisObject();
                    if (appContext == null) return result;

                    java.io.File dbFile = appContext.getDatabasePath("naver_line");
                    if (dbFile.exists()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            SQLiteDatabase.OpenParams params = new SQLiteDatabase.OpenParams.Builder()
                                    .addOpenFlags(SQLiteDatabase.OPEN_READWRITE)
                                    .build();
                            
                            try {
                                SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile, params);

                                // 建立專屬黑名單表格
                                db.execSQL("CREATE TABLE IF NOT EXISTS lime_hidden_chats (chat_id TEXT PRIMARY KEY)");
                                
                                // 防閃爍觸發器 A
                                db.execSQL("CREATE TRIGGER IF NOT EXISTS enforce_hide_on_msg " +
                                        "AFTER INSERT ON chat_history " +
                                        "FOR EACH ROW " +
                                        "WHEN EXISTS (SELECT 1 FROM lime_hidden_chats WHERE chat_id = NEW.chat_id) " +
                                        "BEGIN " +
                                        "UPDATE chat SET is_archived = 1 WHERE chat_id = NEW.chat_id; " +
                                        "END;");

                                // 防閃爍觸發器 B
                                db.execSQL("CREATE TRIGGER IF NOT EXISTS enforce_hide_on_update " +
                                        "AFTER UPDATE OF is_archived ON chat " +
                                        "FOR EACH ROW " +
                                        "WHEN NEW.is_archived = 0 AND EXISTS (SELECT 1 FROM lime_hidden_chats WHERE chat_id = NEW.chat_id) " +
                                        "BEGIN " +
                                        "UPDATE chat SET is_archived = 1 WHERE chat_id = NEW.chat_id; " +
                                        "END;");

                                // 執行後續的訊息攔截
                                hookMessageDeletion(module, classLoader, db);
                            } catch (Exception e) {
                                // 使用數字 4 代表 ERROR 等級
                                module.log(4, "LIMEs", "Lime Trigger Error: " + e.getMessage());
                            }
                        }
                    }
                    return result;
                }
            });
        } catch (Exception e) {
            module.log(4, "LIMEs", "ChatList onCreate Hook 失敗: " + e.getMessage());
        }
    }

    private void hookMessageDeletion(LimeModule module, ClassLoader classLoader, SQLiteDatabase db) {
        try {
            // 🛠️ 修正 2：Thrift 請求攔截
            Class<?> requestClass = classLoader.loadClass(Constants.REQUEST_HOOK.className);
            for (Method method : requestClass.getDeclaredMethods()) {
                if (method.getName().equals(Constants.REQUEST_HOOK.methodName)) {
                    module.hook(method, new XposedInterface.Hooker() {
                        @Override
                        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
                            Object result = chain.proceed();
                            
                            List<Object> args = chain.getArgs();
                            if (args == null || args.length < 2 || args[1] == null) return result;
                            
                            String paramValue = args[1].toString();
                            
                            if (paramValue.contains("setChatHiddenStatusRequest")) {
                                String talkId = extractTalkId(paramValue);
                                if (talkId != null) {
                                    if (paramValue.contains("hiddenStatus:true") || paramValue.contains("hidden:true")) {
                                        db.execSQL("INSERT OR IGNORE INTO lime_hidden_chats (chat_id) VALUES (?)", new Object[]{talkId});
                                        db.execSQL("UPDATE chat SET is_archived = 1 WHERE chat_id = ?", new Object[]{talkId});
                                        module.log(2, "LIMEs", "Lime: Successfully Hid Chat " + talkId);
                                    } else if (paramValue.contains("hiddenStatus:false") || paramValue.contains("hidden:false")) {
                                        db.execSQL("DELETE FROM lime_hidden_chats WHERE chat_id = ?", new Object[]{talkId});
                                        db.execSQL("UPDATE chat SET is_archived = 0 WHERE chat_id = ?", new Object[]{talkId});
                                        module.log(2, "LIMEs", "Lime: Unhid Chat " + talkId);
                                    }
                                }
                            }
                            return result;
                        }
                    });
                }
            }

            // 🛠️ 修正 3：Thrift 回應攔截
            Class<?> responseClass = classLoader.loadClass(Constants.RESPONSE_HOOK.className);
            for (Method method : responseClass.getDeclaredMethods()) {
                if (method.getName().equals(Constants.RESPONSE_HOOK.methodName)) {
                    module.hook(method, new XposedInterface.Hooker() {
                        @Override
                        public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
                            Object result = chain.proceed();
                            try {
                                // 每次有回應時強制同步黑名單狀態，防止 LINE 擅自恢復
                                db.execSQL("UPDATE chat SET is_archived = 1 WHERE chat_id IN (SELECT chat_id FROM lime_hidden_chats)");
                            } catch (Exception ignored) {}
                            return result;
                        }
                    });
                }
            }

        } catch (Exception e) {
            module.log(4, "LIMEs", "ChatList 找不到 Request/Response Class: " + e.getMessage());
        }
    }

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
