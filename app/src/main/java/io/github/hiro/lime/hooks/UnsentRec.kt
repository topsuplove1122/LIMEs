package io.github.hiro.lime.hooks

import android.app.Application
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.util.Log
import io.github.hiro.lime.LimeModule
import io.github.hiro.lime.LimeOptions
import java.io.File

class UnsentRec : IHook {

    override fun hook(module: LimeModule, classLoader: ClassLoader, limeOptions: LimeOptions) {
        runCatching {
            val onCreateMethod = Application::class.java.getDeclaredMethod("onCreate")
            module.hook(onCreateMethod, module.newHookBuilder().setInterceptor { chain ->
                val result = chain.proceed()
                val appContext = chain.thisObject as? Application ?: return@setInterceptor result

                val dbFile = appContext.getDatabasePath("naver_line")
                if (dbFile.exists() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    val params = SQLiteDatabase.OpenParams.Builder()
                        .addOpenFlags(SQLiteDatabase.OPEN_READWRITE)
                        .build()
                    
                    runCatching {
                        val db = SQLiteDatabase.openDatabase(dbFile, params)
                        hookMessageDeletion(module, classLoader, db)
                    }.onFailure { e ->
                        module.log(Log.ERROR, "LIMEs", "UnsentRec 開啟資料庫失敗: ${e.message}")
                    }
                }
                result
            }.build())
        }.onFailure { e ->
            module.log(Log.ERROR, "LIMEs", "UnsentRec (onCreate) 失敗: ${e.message}")
        }
    }

    private fun hookMessageDeletion(module: LimeModule, classLoader: ClassLoader, db: SQLiteDatabase) {
        runCatching {
            val responseClass = classLoader.loadClass(Constants.RESPONSE_HOOK.className)
            responseClass.declaredMethods.forEach { method ->
                if (method.name == Constants.RESPONSE_HOOK.methodName) {
                    module.hook(method, module.newHookBuilder().setInterceptor { chain ->
                        val result = chain.proceed()
                        val args = chain.args
                        if (args.size < 2 || args[1] == null) return@setInterceptor result

                        val paramValue = args[1].toString()
                        if (paramValue.contains("type:NOTIFIED_DESTROY_MESSAGE,")) {
                            processMessage(module, paramValue, db)
                        }
                        result
                    }.build())
                }
            }
        }.onFailure {
            module.log(Log.ERROR, "LIMEs", "UnsentRec 找不到 Response Class 或 Hook 失敗")
        }
    }

    private fun processMessage(module: LimeModule, paramValue: String, db: SQLiteDatabase) {
        runCatching {
            val operations = paramValue.split("Operation(")
            for (operation in operations) {
                if (operation.isBlank()) continue
                
                var type: String? = null
                var serverId: String? = null
                val parts = operation.split(",")

                for (part in parts) {
                    val trimmedPart = part.trim()
                    if (trimmedPart.startsWith("type:")) {
                        type = trimmedPart.substring("type:".length).trim()
                    }
                }

                if (type == "NOTIFIED_DESTROY_MESSAGE") {
                    for (part in parts) {
                        val trimmedPart = part.trim()
                        if (trimmedPart.startsWith("param2:")) {
                            serverId = trimmedPart.substring("param2:".length).trim()
                            break
                        }
                    }
                    serverId?.let { updateMessageAsCanceled(module, db, it) }
                }
            }
        }.onFailure { e ->
            module.log(Log.ERROR, "LIMEs", "UnsentRec 處理失敗: ${e.message}")
        }
    }

    private fun updateMessageAsCanceled(module: LimeModule, db: SQLiteDatabase, serverId: String) {
        val canceledContent = "🚫 對方嘗試收回訊息"
        runCatching {
            db.rawQuery("SELECT * FROM chat_history WHERE server_id=?", arrayOf(serverId)).use { cursor ->
                if (cursor.moveToFirst()) {
                    val chatId = cursor.getString(cursor.getColumnIndexOrThrow("chat_id"))
                    val fromMid = cursor.getString(cursor.getColumnIndexOrThrow("from_mid"))
                    val createdTime = cursor.getString(cursor.getColumnIndexOrThrow("created_time"))
                    val deliveredTime = cursor.getString(cursor.getColumnIndexOrThrow("delivered_time"))

                    db.rawQuery("SELECT 1 FROM chat_history WHERE server_id=? AND content=?", arrayOf(serverId, canceledContent)).use { existingCursor ->
                        if (!existingCursor.moveToFirst()) {
                            val values = ContentValues().apply {
                                put("server_id", serverId)
                                put("type", "1")
                                put("chat_id", chatId)
                                put("from_mid", fromMid)
                                put("content", canceledContent)
                                put("created_time", createdTime)
                                put("delivered_time", deliveredTime)
                                put("status", "1")
                                put("attachement_image", "0")
                                put("attachement_type", "0")
                                put("parameter", "LIMEsUnsend")
                            }

                            db.insert("chat_history", null, values)
                            module.log(Log.INFO, "LIMEs", "UnsentRec: 成功寫入防收回紀錄！")
                        }
                    }
                }
            }
        }.onFailure { e ->
            module.log(Log.ERROR, "LIMEs", "UnsentRec 寫入失敗: ${e.message}")
        }
    }
}
