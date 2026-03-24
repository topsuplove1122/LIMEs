package io.github.hiro.lime.hooks;

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.hiro.lime.LimeOptions;

public class ChatList implements IHook {
    @Override
    public void hook(LimeOptions limeOptions, XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {

        // 【魔法護盾 1：攔截 Android 底層資料庫寫入】(防閃爍核心，永遠不怕 LINE 更新)
        XposedBridge.hookAllMethods(SQLiteDatabase.class, "updateWithOnConflict", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                String table = (String) param.args[0];
                // 只要是針對 chat 資料表的更新
                if ("chat".equals(table)) {
                    ContentValues values = (ContentValues) param.args[1];
                    // 檢查 LINE 是否試圖把 is_archived 設為 0 (解除隱藏)
                    if (values != null && values.containsKey("is_archived")) {
                        Integer isArchived = values.getAsInteger("is_archived");
                        if (isArchived != null && isArchived == 0) {
                            String[] whereArgs = (String[]) param.args[3];
                            if (whereArgs != null && whereArgs.length > 0) {
                                String chatId = whereArgs[0]; // 通常第一個參數就是 chat_id
                                Context context = android.app.AndroidAppHelper.currentApplication();
                                // 如果這個聊天室在我們的小本本(黑名單)裡
                                if (context != null && readChatIdsFromFile(context).contains(chatId)) {
                                    // 🛑 霸王硬上弓：在寫入資料庫的前一刻，強制把它改回 1！
                                    values.put("is_archived", 1);
                                }
                            }
                        }
                    }
                }
            }
        });

        // 【魔法護盾 2：攔截你的手動操作與網路同步兜底】
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

                        hookMessageDeletion(loadPackageParam, appContext, db);
                    }
                }
            }
        });
    }

    private void hookMessageDeletion(XC_LoadPackage.LoadPackageParam loadPackageParam, Context context, SQLiteDatabase db) throws ClassNotFoundException {
        // 1. 攔截手動隱藏/解除隱藏 (寫入或刪除小本本)
        XposedBridge.hookAllMethods(
                loadPackageParam.classLoader.loadClass(Constants.REQUEST_HOOK.className),
                Constants.REQUEST_HOOK.methodName,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        String paramValue = param.args[1].toString();
                        if (paramValue.contains("hidden:true")) {
                            String talkId = extractTalkId(paramValue);
                            if (talkId != null) {
                                saveTalkIdToFile(talkId, context);
                                updateIsArchived(db, talkId); // 立即生效
                            }
                        }
                        if (paramValue.contains("hidden:false")) {
                            String talkId = extractTalkId(paramValue);
                            if (talkId != null) {
                                deleteTalkIdFromFile(talkId, context);
                            }
                        }
                    }
                });

        // 2. 網路同步後兜底（防漏網之魚）
        XposedBridge.hookAllMethods(
                loadPackageParam.classLoader.loadClass(Constants.RESPONSE_HOOK.className),
                Constants.RESPONSE_HOOK.methodName,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        updateArchivedChatsFromFile(db, context);
                    }
                });
    }

    // --- 下面是純粹的檔案與資料庫讀寫工具，無需更動 ---
    private void saveTalkIdToFile(String talkId, Context context) {
        File dir = context.getFilesDir();
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "hidelist.txt");

        try {
            if (!file.exists()) file.createNewFile();
            List<String> existingIds = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) existingIds.add(line.trim());
            } catch (IOException ignored) {}
            
            if (!existingIds.contains(talkId.trim())) {
                try (FileWriter writer = new FileWriter(file, true)) {
                    writer.write(talkId + "\n");
                }
            }
        } catch (IOException ignored) {}
    }

    private void deleteTalkIdFromFile(String talkId, Context context) {
        File dir = context.getFilesDir();
        File file = new File(dir, "hidelist.txt");

        if (file.exists()) {
            try {
                List<String> lines = new ArrayList<>();
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().equals(talkId)) lines.add(line);
                }
                reader.close();

                BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                for (String remainingLine : lines) {
                    writer.write(remainingLine);
                    writer.newLine();
                }
                writer.close();
            } catch (IOException ignored) {}
        }
    }

    private void updateArchivedChatsFromFile(SQLiteDatabase db, Context context) {
        File dir = context.getFilesDir();
        File file = new File(dir, "hidelist.txt");
        if (!file.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String chatId;
            while ((chatId = reader.readLine()) != null) {
                chatId = chatId.trim();
                if (!chatId.isEmpty()) updateIsArchived(db, chatId);
            }
        } catch (IOException ignored) {}
    }

    private List<String> readChatIdsFromFile(Context context) {
        List<String> chatIds = new ArrayList<>();
        File dir = context.getFilesDir();
        File file = new File(dir, "hidelist.txt");

        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException ignored) {}
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) chatIds.add(line.trim());
        } catch (IOException ignored) {}
        return chatIds;
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

    private void updateDatabase(SQLiteDatabase db, String query, Object... bindArgs) {
        if (db == null) return;
        try {
            db.beginTransaction();
            db.execSQL(query, bindArgs);
            db.setTransactionSuccessful();
        } catch (Exception ignored) {} 
        finally { db.endTransaction(); }
    }

    private void updateIsArchived(SQLiteDatabase db, String chatId) {
        String updateQuery = "UPDATE chat SET is_archived = 1 WHERE chat_id = ?";
        updateDatabase(db, updateQuery, chatId);
    }
}
