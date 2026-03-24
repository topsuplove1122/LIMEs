package io.github.hiro.lime.hooks;

import android.app.Application;
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
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.hiro.lime.LimeOptions;

public class ChatList implements IHook {
    @Override
    public void hook(LimeOptions limeOptions, XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {

        XposedBridge.hookAllMethods(Application.class, "onCreate", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Application appContext = (Application) param.thisObject;

                if (appContext == null) {
                    return;
                }

                // 取得 LINE 自身的資料庫
                File dbFile = appContext.getDatabasePath("naver_line");

                if (dbFile.exists()) {
                    SQLiteDatabase.OpenParams.Builder builder = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        builder = new SQLiteDatabase.OpenParams.Builder();
                        builder.addOpenFlags(SQLiteDatabase.OPEN_READWRITE);
                        SQLiteDatabase.OpenParams dbParams = builder.build();

                        SQLiteDatabase db = SQLiteDatabase.openDatabase(dbFile, dbParams);
                        
                        // 啟動攔截器，將 appContext 傳入取代原本會崩潰的 moduleContext
                        // hookSAMethod(loadPackageParam, db, appContext);
                        hookMessageDeletion(loadPackageParam, appContext, db);
                    }
                }
            }
        });
    }

    private void hookMessageDeletion(XC_LoadPackage.LoadPackageParam loadPackageParam, Context context, SQLiteDatabase db) throws ClassNotFoundException {
        
        // 1. 攔截你「手動操作」隱藏/解除隱藏 (攔截發送給伺服器的 REQUEST)
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
                                updateArchivedChatsFromFile(db, context);
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

        // 2. 【全新加入的無情守衛】攔截「接收新訊息」 (攔截伺服器傳來的 RESPONSE)
        XposedBridge.hookAllMethods(
                loadPackageParam.classLoader.loadClass(Constants.RESPONSE_HOOK.className),
                Constants.RESPONSE_HOOK.methodName,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        // 每當有新訊息進來，LINE 原廠機制會偷偷把 is_archived 改成 0 讓它彈出來。
                        // 我們在這裡攔截它，每次同步完，就強迫把名單內的聊天室再次死死地釘在 1 (隱藏)！
                        updateArchivedChatsFromFile(db, context);
                    }
                });
    }

    // private void hookSAMethod(XC_LoadPackage.LoadPackageParam loadPackageParam, SQLiteDatabase db, Context appContext) {
    //     Class<?> targetClass = XposedHelpers.findClass(Constants.Archive.className, loadPackageParam.classLoader);

    //     XposedBridge.hookAllMethods(targetClass, Constants.Archive.methodName, new XC_MethodHook() {
    //         @Override
    //         protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    //             // 強制執行隱藏邏輯，移除 PinList 廢話碼
    //             List<String> chatIds = readChatIdsFromFile(appContext);
    //             for (String chatId : chatIds) {
    //                 if (!chatId.isEmpty()) {
    //                     updateIsArchived(db, chatId);
    //                 }
    //             }
    //         }
    //     });
    // }

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

    private String queryDatabase(SQLiteDatabase db, String query, String... selectionArgs) {
        if (db == null) return null;
        try (Cursor cursor = db.rawQuery(query, selectionArgs)) {
            if (cursor.moveToFirst()) return cursor.getString(0);
        } catch (Exception ignored) {}
        return null;
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
