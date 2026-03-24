package io.github.hiro.lime.hooks;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.AndroidAppHelper;
import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Build;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.hiro.lime.LimeOptions;
import io.github.hiro.lime.R;

public class UnsentRec implements IHook {
    public static final String Main_file = "UNSENT_REC.txt";
    public static final String Main_backup = "BackUpFile.txt";
    @Override
    public void hook(LimeOptions limeOptions, XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        // if (!limeOptions.preventUnsendMessage.checked) return;
        

        XposedBridge.hookAllMethods(Application.class, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Application appContext = (Application) param.thisObject;
                if (appContext == null) {
                    return;
                }
                Context moduleContext;
                try {
                    moduleContext = appContext.createPackageContext(
                            "io.github.hiro.lime", Context.CONTEXT_IGNORE_SECURITY);
                } catch (PackageManager.NameNotFoundException ignored) {
                    return;
                }
                File dbFile1 = appContext.getDatabasePath("naver_line");
                File dbFile2 = appContext.getDatabasePath("contact");
                if (dbFile1.exists() && dbFile2.exists()) {

                    SQLiteDatabase.OpenParams.Builder builder1 = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        builder1 = new SQLiteDatabase.OpenParams.Builder();
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        builder1.addOpenFlags(SQLiteDatabase.OPEN_READWRITE);
                    }
                    SQLiteDatabase.OpenParams dbParams1 = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        dbParams1 = builder1.build();
                    }

                    SQLiteDatabase.OpenParams.Builder builder2 = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        builder2 = new SQLiteDatabase.OpenParams.Builder();
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        builder2.addOpenFlags(SQLiteDatabase.OPEN_READWRITE);
                    }
                    SQLiteDatabase.OpenParams dbParams2 = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        dbParams2 = builder2.build();
                    }

                    SQLiteDatabase db1 = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        db1 = SQLiteDatabase.openDatabase(dbFile1, dbParams1);
                    }
                    SQLiteDatabase db2 = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        db2 = SQLiteDatabase.openDatabase(dbFile2, dbParams2);
                    }


                    hookMessageDeletion(loadPackageParam, appContext, db1, db2,moduleContext);
                    resolveUnresolvedIds(loadPackageParam, appContext, db1, db2, moduleContext);

                }
            }
        });

    }



    private String queryDatabase(SQLiteDatabase db, String query, String... selectionArgs) {
        if (db == null) {
            return null;
        }
        Cursor cursor = db.rawQuery(query, selectionArgs);
        String result = null;
        if (cursor.moveToFirst()) {
            result = cursor.getString(0);
        }
        cursor.close();
        return result;
    }



    private int countLinesInFile(File file) {
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!(line.contains("No content") || line.contains("No name"))) {
                    count++;
                }
            }
        } catch (IOException ignored) {
        }
            return count;

    }

    private void hookMessageDeletion(XC_LoadPackage.LoadPackageParam loadPackageParam, Context context, SQLiteDatabase db1, SQLiteDatabase db2,Context moduleContext) {
        try {
            XposedBridge.hookAllMethods(
                    loadPackageParam.classLoader.loadClass(Constants.RESPONSE_HOOK.className),
                    Constants.RESPONSE_HOOK.methodName,

                    new XC_MethodHook() {

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String paramValue = param.args[1].toString();
                            if (paramValue.contains("type:NOTIFIED_DESTROY_MESSAGE,")) {
                                Context moduleContext = AndroidAppHelper.currentApplication().createPackageContext(
                                        "io.github.hiro.lime", Context.CONTEXT_IGNORE_SECURITY);
                                processMessage(paramValue, moduleContext, db1, db2, context);
                            }
                        }
                    });
        } catch (ClassNotFoundException ignored) {
        }
    }
    private void processMessage(String paramValue, Context moduleContext, SQLiteDatabase db1, SQLiteDatabase db2, Context context) {
        String unresolvedFilePath = context.getFilesDir() + "/UnresolvedIds.txt";

        String[] operations = paramValue.split("Operation\\(");
        for (String operation : operations) {
            if (operation.trim().isEmpty()) continue;
            String revision = null;
            String createdTime = null;
            String type = null;
            String from = null;
            String to = null;
            String param12 = null;
            String param22 = null;
            String operationContent = null;
            String talkId = null;
            String serverId = null; // serverIdをここで宣言

            String[] parts = operation.split(",");
            for (String part : parts) {
                part = part.trim();
                if (part.startsWith("param1:")) {
                    talkId = part.substring("param1:".length()).trim();
                } else if (part.startsWith("revision:")) {
                    revision = part.substring("revision:".length()).trim();
                } else if (part.startsWith("createdTime:")) {
                    createdTime = part.substring("createdTime:".length()).trim();
                } else if (part.startsWith("type:")) {
                    type = part.substring("type:".length()).trim();
                } else if (part.startsWith("from:")) {
                    from = part.substring("from:".length()).trim();
                } else if (part.startsWith("to:")) {
                    to = part.substring("to:".length()).trim();
                } else if (part.startsWith("contentMetadata:")) {
                    param12 = part.substring("contentMetadata:".length()).trim();
                } else if (part.startsWith("operationContent:")) {
                    operationContent = part.substring("operationContent:".length()).trim();
                }
            }

            // typeがNOTIFIED_DESTROY_MESSAGEの場合のみserverIdを設定
            if ("NOTIFIED_DESTROY_MESSAGE".equals(type)) {
                for (String part : parts) {
                    part = part.trim();
                    if (part.startsWith("param2:")) {
                        serverId = part.substring("param2:".length()).trim();
                        break; // param2が見つかったらループを抜ける
                    }
                }
                updateMessageAsCanceled(db1, serverId,context,moduleContext);
                // serverIdを使用した処理をここに追加
                XposedBridge.log("Server ID for NOTIFIED_DESTROY_MESSAGE: " + serverId);
            }

            // serverIdとtalkIdが両方ともnullでない場合に処理を行う
            if (serverId != null && talkId != null) {
                XposedBridge.log(paramValue + serverId);
                String content = queryDatabase(db1, "SELECT content FROM chat_history WHERE server_id=?", serverId);
                String imageCheck = queryDatabase(db1, "SELECT attachement_image FROM chat_history WHERE server_id=?", serverId);
                String timeEpochStr = queryDatabase(db1, "SELECT created_time FROM chat_history WHERE server_id=?", serverId);
                String timeFormatted = formatMessageTime(timeEpochStr);
                String groupName = queryDatabase(db1, "SELECT name FROM groups WHERE id=?", talkId);
                String media = queryDatabase(db1, "SELECT attachement_type FROM chat_history WHERE server_id=?", serverId);
                String talkName = queryDatabase(db2, "SELECT profile_name FROM contacts WHERE mid=?", talkId);

                String name = (groupName != null ? groupName : (talkName != null ? talkName : "No Name" + ":" + ":" + "talkId" + talkId));

                if (timeEpochStr == null) {
                    saveUnresolvedIds(serverId, talkId, unresolvedFilePath);
                }
                String from_mid = null;
                String sender_name = null;
                if (groupName != null) {
                    from_mid = queryDatabase(db1, "SELECT from_mid FROM chat_history WHERE server_id=?", serverId);
                    if (from_mid != null) {
                        sender_name = queryDatabase(db2, "SELECT profile_name FROM contacts WHERE mid=?", from_mid);
                    }
                }
                if (sender_name != null) {
                    name = groupName + ": " + sender_name;
                }
                String mediaDescription = "";
                if (media != null) {
                    switch (media) {
                        case "7":
                            mediaDescription = moduleContext.getResources().getString(R.string.sticker);
                            break;
                        case "1":
                            mediaDescription = moduleContext.getResources().getString(R.string.picture);
                            break;
                        case "2":
                            mediaDescription = moduleContext.getResources().getString(R.string.video);
                            break;
                        default:
                            mediaDescription = "";
                            break;
                    }
                }
               // XposedBridge.log("S" + serverId);
                // 新しいメソッドを呼び出して、メッセージを取り消し済みとして更新

                String logEntry = (timeFormatted != null ? timeFormatted : "No Time: ")
                        + name
                        + ": "
                        + ((content != null) ? content : (mediaDescription.isEmpty() ? "No content:" + serverId : ""))
                        + mediaDescription;
                File fileToWrite = new File(context.getFilesDir(), Main_file);

                try {
                    if (!fileToWrite.getParentFile().exists()) {
                        if (!fileToWrite.getParentFile().mkdirs()) {
                            XposedBridge.log("Failed to create directory " + fileToWrite.getParent());
                        }
                    }
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileToWrite, true))) {
                        writer.write(logEntry);
                        writer.newLine();
                    }
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void updateMessageAsCanceled(SQLiteDatabase db1, String serverId, Context context, Context moduleContext) {
        // canceledContent をファイルから取得
        String canceledContent = "🚫 對方收回訊息";
        // 既存のメッセージを取得
        Cursor cursor = db1.rawQuery("SELECT * FROM chat_history WHERE server_id=?", new String[]{serverId});


        if (cursor.moveToFirst()) {
            // カラムの値を取得（null の場合もそのまま代入）
            @SuppressLint("Range") String type = cursor.isNull(cursor.getColumnIndex("type")) ? null : cursor.getString(cursor.getColumnIndex("type"));
            @SuppressLint("Range") String chatId = cursor.isNull(cursor.getColumnIndex("chat_id")) ? null : cursor.getString(cursor.getColumnIndex("chat_id"));
            @SuppressLint("Range") String fromMid = cursor.isNull(cursor.getColumnIndex("from_mid")) ? null : cursor.getString(cursor.getColumnIndex("from_mid"));
            @SuppressLint("Range") String createdTime = cursor.isNull(cursor.getColumnIndex("created_time")) ? null : cursor.getString(cursor.getColumnIndex("created_time"));
            @SuppressLint("Range")String deliveredTime = cursor.isNull(cursor.getColumnIndex("delivered_time")) ? null : cursor.getString(cursor.getColumnIndex("delivered_time"));
            @SuppressLint("Range")String status = cursor.isNull(cursor.getColumnIndex("status")) ? null : cursor.getString(cursor.getColumnIndex("status"));
            @SuppressLint("Range")Integer sentCount = cursor.isNull(cursor.getColumnIndex("sent_count")) ? null : cursor.getInt(cursor.getColumnIndex("sent_count"));
            @SuppressLint("Range")Integer readCount = cursor.isNull(cursor.getColumnIndex("read_count")) ? null : cursor.getInt(cursor.getColumnIndex("read_count"));
            @SuppressLint("Range")String locationName = cursor.isNull(cursor.getColumnIndex("location_name")) ? null : cursor.getString(cursor.getColumnIndex("location_name"));
            @SuppressLint("Range")String locationAddress = cursor.isNull(cursor.getColumnIndex("location_address")) ? null : cursor.getString(cursor.getColumnIndex("location_address"));
            @SuppressLint("Range")String locationPhone = cursor.isNull(cursor.getColumnIndex("location_phone")) ? null : cursor.getString(cursor.getColumnIndex("location_phone"));
            @SuppressLint("Range")Double locationLatitude = cursor.isNull(cursor.getColumnIndex("location_latitude")) ? null : cursor.getDouble(cursor.getColumnIndex("location_latitude"));
            @SuppressLint("Range") Double locationLongitude = cursor.isNull(cursor.getColumnIndex("location_longitude")) ? null : cursor.getDouble(cursor.getColumnIndex("location_longitude"));
            @SuppressLint("Range") String attachmentImage = cursor.isNull(cursor.getColumnIndex("attachement_image")) ? null : cursor.getString(cursor.getColumnIndex("attachement_image"));
            @SuppressLint("Range") Integer attachmentImageHeight = cursor.isNull(cursor.getColumnIndex("attachement_image_height")) ? null : cursor.getInt(cursor.getColumnIndex("attachement_image_height"));
            @SuppressLint("Range") Integer attachmentImageWidth = cursor.isNull(cursor.getColumnIndex("attachement_image_width")) ? null : cursor.getInt(cursor.getColumnIndex("attachement_image_width"));
            @SuppressLint("Range") Integer attachmentImageSize = cursor.isNull(cursor.getColumnIndex("attachement_image_size")) ? null : cursor.getInt(cursor.getColumnIndex("attachement_image_size"));
            @SuppressLint("Range") String attachmentType = cursor.isNull(cursor.getColumnIndex("attachement_type")) ? null : cursor.getString(cursor.getColumnIndex("attachement_type"));
            @SuppressLint("Range") String attachmentLocalUri = cursor.isNull(cursor.getColumnIndex("attachement_local_uri")) ? null : cursor.getString(cursor.getColumnIndex("attachement_local_uri"));
            @SuppressLint("Range") String parameter = cursor.isNull(cursor.getColumnIndex("parameter")) ? null : cursor.getString(cursor.getColumnIndex("parameter"));
            @SuppressLint("Range")  String chunks = cursor.isNull(cursor.getColumnIndex("chunks")) ? null : cursor.getString(cursor.getColumnIndex("chunks"));

            // 既存のレコードがあるか確認
            Cursor existingCursor = db1.rawQuery("SELECT * FROM chat_history WHERE server_id=? AND content=?", new String[]{serverId, chatId});
            if (!existingCursor.moveToFirst()) {
                XposedBridge.log(serverId);
                // 新しいレコードを挿入
                ContentValues values = new ContentValues();
                values.put("server_id", serverId);
                values.put("type", "1");
                values.put("chat_id", chatId);
                values.put("from_mid", fromMid);
                values.put("content", canceledContent);
                values.put("created_time", createdTime);
                values.put("delivered_time", deliveredTime);
                values.put("status", status);
                if (sentCount != null) values.put("sent_count", sentCount);
                if (readCount != null) values.put("read_count", readCount);
                values.put("location_name", locationName);
                values.put("location_address", locationAddress);
                values.put("location_phone", locationPhone);
                if (locationLatitude != null) values.put("location_latitude", locationLatitude);
                if (locationLongitude != null) values.put("location_longitude", locationLongitude);
                values.put("attachement_image", "0");
                if (attachmentImageHeight != null) values.put("attachement_image_height", attachmentImageHeight);
                else values.put("attachement_image_height", (String) null);
                if (attachmentImageWidth != null) values.put("attachement_image_width", attachmentImageWidth);
                else values.put("attachement_image_width", (String) null);
                if (attachmentImageSize != null) values.put("attachement_image_size", attachmentImageSize);
                else values.put("attachement_image_size", (String) null);
                values.put("attachement_type", "0");
                values.put("attachement_local_uri", attachmentLocalUri);
                values.put("parameter", "LIMEsUnsend"); // ここを修正
                values.put("chunks", chunks);

                db1.insert("chat_history", null, values);
            }
            existingCursor.close();
        }

        cursor.close();
    }
    private void saveUnresolvedIds(String serverId, String talkId, String filePath) {
        String newEntry = "serverId:" + serverId + ",talkId:" + talkId;
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.equals(newEntry)) {

                    return;
                }
            }
        } catch (IOException ignored) {
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
            writer.write(newEntry);
            writer.newLine();
        } catch (IOException ignored) {
        }
    }

    private String getCanceledContentFromFile(Context context,Context moduleContext) {
        // フォルダのパスを設定
        File dir = new File(context.getFilesDir(), "LimeBackup/Setting");
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                return moduleContext.getResources().getString(R.string.canceled_message_txt); // フォルダ作成失敗時のデフォルト値
            }
        }

        // ファイルのパスを設定
        File file = new File(dir, "canceled_message.txt");

        // ファイルが存在しない場合、デフォルトの文字列を書き込む
        if (!file.exists()) {
            try (FileOutputStream fos = new FileOutputStream(file)) {
                
                String defaultContent = moduleContext.getResources().getString(R.string.canceled_message_txt);
                fos.write(defaultContent.getBytes());
            } catch (IOException e) {
                String defaultContent = moduleContext.getResources().getString(R.string.canceled_message_txt);
                return defaultContent; // ファイル書き込み失敗時のデフォルト値
            }
        }

        // ファイルから文字列を読み取る
        StringBuilder contentBuilder = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                contentBuilder.append(line).append("\n");
            }
        } catch (IOException e) {
            return moduleContext.getResources().getString(R.string.canceled_message_txt); // ファイル読み込み失敗時のデフォルト値
        }

        // 読み取った内容を返す（末尾の改行を削除）
        String content = contentBuilder.toString().trim();
        return content.isEmpty() ? moduleContext.getResources().getString(R.string.canceled_message_txt) : content; // 空ファイルの場合のデフォルト値
    }
    private void resolveUnresolvedIds(XC_LoadPackage.LoadPackageParam loadPackageParam, Context context, SQLiteDatabase db1, SQLiteDatabase db2, Context moduleContext) {
        String unresolvedFilePath = context.getFilesDir() + "/UnresolvedIds.txt";

        File unresolvedFile = new File(unresolvedFilePath);
        File testFile = new File(context.getFilesDir(), Main_file);

        if (!unresolvedFile.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(unresolvedFile));
             BufferedWriter testWriter = new BufferedWriter(new FileWriter(testFile, true))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                String serverId = parts[0].split(":")[1];
                String talkId = parts[1].split(":")[1];

                String content = queryDatabase(db1, "SELECT content FROM chat_history WHERE server_id=?", serverId);
                String imageCheck = queryDatabase(db1, "SELECT attachement_image FROM chat_history WHERE server_id=?", serverId);
                String timeEpochStr = queryDatabase(db1, "SELECT created_time FROM chat_history WHERE server_id=?", serverId);
                String timeFormatted = formatMessageTime(timeEpochStr);
                String groupName = queryDatabase(db1, "SELECT name FROM groups WHERE id=?", talkId);
                String media = queryDatabase(db1, "SELECT attachement_type FROM chat_history WHERE server_id=?", serverId);
                String talkName = queryDatabase(db2, "SELECT profile_name FROM contacts WHERE mid=?", talkId);
                String name = (groupName != null ? groupName : (talkName != null ? talkName : "No Name" + ":" + ":" + "talkId" + talkId));
                String from_mid = null;
                String sender_name = null;
                if (groupName != null) {
                    from_mid = queryDatabase(db1, "SELECT from_mid FROM chat_history WHERE server_id=?", serverId);
                    if (from_mid != null) {
                        sender_name = queryDatabase(db2, "SELECT profile_name FROM contacts WHERE mid=?", from_mid);
                    }
                }

                if (sender_name != null) {
                    name = groupName + ": " + sender_name;
                }
                String mediaDescription = "";
                if (media != null) {
                    switch (media) {
                        case "7":
                            mediaDescription = moduleContext.getResources().getString(R.string.sticker);
                            break;
                        case "1":
                            mediaDescription = moduleContext.getResources().getString(R.string.picture);
                            break;
                        case "2":
                            mediaDescription = moduleContext.getResources().getString(R.string.video);
                            break;
                        default:
                            mediaDescription = "";
                            break;
                    }
                }

                String logEntry = (timeFormatted != null ? timeFormatted : "No Time: ")
                        + name
                        + ": " + (content != null ? content : "NO get id:" + serverId)
                        + mediaDescription;
                if (timeEpochStr == null) {
                    saveUnresolvedIds(serverId, talkId, unresolvedFilePath);
                }
                testWriter.write(moduleContext.getResources().getString(R.string.reacquisition) + logEntry);
                testWriter.newLine();
                updateMessageAsCanceled(db1, serverId,context,moduleContext);

            }


            try (BufferedWriter clearWriter = new BufferedWriter(new FileWriter(unresolvedFile))) {
                clearWriter.write("");
            }

        } catch (IOException ignored) {
        }
    }


    private String formatMessageTime(String timeEpochStr) {
        if (timeEpochStr == null) return null;
        long timeEpoch = Long.parseLong(timeEpochStr);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timeEpoch));
    }


}
