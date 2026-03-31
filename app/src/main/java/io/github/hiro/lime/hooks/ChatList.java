package io.github.hiro.lime.hooks;

import android.app.Application;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.github.hiro.lime.LimeModule;
import io.github.hiro.lime.LimeOptions;

public class ChatList implements IHook {
    @Override
    public void hook(LimeModule module, ClassLoader classLoader, LimeOptions limeOptions) throws Throwable {
        try {
            Method onCreateMethod = Application.class.getDeclaredMethod("onCreate");
            module.hook(onCreateMethod).intercept(chain -> {
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
                            db.execSQL("CREATE TABLE IF NOT EXISTS lime_hidden_chats (chat_id TEXT PRIMARY KEY)");
                            db.execSQL("CREATE TRIGGER IF NOT EXISTS enforce_hide_on_msg AFTER INSERT ON chat_history FOR EACH ROW WHEN EXISTS (SELECT 1 FROM lime_hidden_chats WHERE chat_id = NEW.chat_id) BEGIN UPDATE chat SET is_archived = 1 WHERE chat_id = NEW.chat_id; END;");
                            db.execSQL("CREATE TRIGGER IF NOT EXISTS enforce_hide_on_update AFTER UPDATE OF is_archived ON chat FOR EACH ROW WHEN NEW.is_archived = 0 AND EXISTS (SELECT 1 FROM lime_hidden_chats WHERE chat_id = NEW.chat_id) BEGIN UPDATE chat SET is_archived = 1 WHERE chat_id = NEW.chat_id; END;");
                            hookMessageDeletion(module, classLoader, db);
                        } catch (Exception e) {
                            module.log(4, "LIMEs", "Lime Trigger Error: " + e.getMessage());
                        }
                    }
                }
                return result;
            });
        } catch (Exception e) {
            module.log(4, "LIMEs", "ChatList onCreate Hook 失敗: " + e.getMessage());
        }
    }

    private void hookMessageDeletion(LimeModule module, ClassLoader classLoader, SQLiteDatabase db) {
        try {
            Class<?> requestClass = classLoader.loadClass(Constants.REQUEST_HOOK.className);
            for (Method method : requestClass.getDeclaredMethods()) {
                if (method.getName().equals(Constants.REQUEST_HOOK.methodName)) {
                    module.hook(method).intercept(chain -> {
                        Object result = chain.proceed();
                        List<Object> args = chain.getArgs();
                        if (args == null || args.size() < 2 || args.get(1) == null) return result;
                        
                        String paramValue = args.get(1).toString();
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
                    });
                }
            }

            Class<?> responseClass = classLoader.loadClass(Constants.RESPONSE_HOOK.className);
            for (Method method : responseClass.getDeclaredMethods()) {
                if (method.getName().equals(Constants.RESPONSE_HOOK.methodName)) {
                    module.hook(method).intercept(chain -> {
                        Object result = chain.proceed();
                        try {
                            db.execSQL("UPDATE chat SET is_archived = 1 WHERE chat_id IN (SELECT chat_id FROM lime_hidden_chats)");
                        } catch (Exception ignored) {}
                        return result;
                    });
                }
            }
        } catch (Exception e) {
            module.log(4, "LIMEs", "ChatList 找不到 Class: " + e.getMessage());
        }
    }

    private String extractTalkId(String paramValue) {
        try {
            Pattern pattern = Pattern.compile("chatMid:([^,\\)]+)");
            Matcher matcher = pattern.matcher(paramValue);
            if (matcher.find()) return matcher.group(1).trim();
        } catch (Exception ignored) {}
        return null;
    }
}
