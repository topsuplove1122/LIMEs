package io.github.hiro.lime.hooks

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.util.Log
import io.github.hiro.lime.LimeModule
import io.github.hiro.lime.LimeOptions
import java.lang.reflect.Method
import java.util.regex.Pattern

class ChatList : IHook {

    override fun hook(module: LimeModule, classLoader: ClassLoader, limeOptions: LimeOptions) {
        try {
            // Hook Application.onCreate 來獲取 Context 並操作資料庫
            val onCreateMethod = Application::class.java.getDeclaredMethod("onCreate")
            
            module.hook(onCreateMethod, module.newHookBuilder()
                .setInterceptor { chain ->
                    val result = chain.proceed()
                    val appContext = chain.thisObject as? Application ?: return@setInterceptor result

                    val dbFile = appContext.getDatabasePath("naver_line")
                    if (dbFile.exists() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        val params = SQLiteDatabase.OpenParams.Builder()
                            .addOpenFlags(SQLiteDatabase.OPEN_READWRITE)
                            .build()
                        
                        runCatching {
                            val db = SQLiteDatabase.openDatabase(dbFile, params)
                            
                            // 建立隱藏聊天室的表
                            db.execSQL("CREATE TABLE IF NOT EXISTS lime_hidden_chats (chat_id TEXT PRIMARY KEY)")
                            
                            // 建立觸發器：當有新訊息插入時，如果該 chat_id 在隱藏名單，強制將 chat 表設為已封存(is_archived = 1)
                            db.execSQL("CREATE TRIGGER IF NOT EXISTS enforce_hide_on_msg AFTER INSERT ON chat_history FOR EACH ROW WHEN EXISTS (SELECT 1 FROM lime_hidden_chats WHERE chat_id = NEW.chat_id) BEGIN UPDATE chat SET is_archived = 1 WHERE chat_id = NEW.chat_id; END;")
                            
                            // 建立觸發器：當手動解除封存時，如果該 chat_id 在隱藏名單，立刻重新封存
                            db.execSQL("CREATE TRIGGER IF NOT EXISTS enforce_hide_on_update AFTER UPDATE OF is_archived ON chat FOR EACH ROW WHEN NEW.is_archived = 0 AND EXISTS (SELECT 1 FROM lime_hidden_chats WHERE chat_id = NEW.chat_id) BEGIN UPDATE chat SET is_archived = 1 WHERE chat_id = NEW.chat_id; END;")
                            
                            // 接著掛載網路請求的 Hook 來監聽隱藏動作
                            hookMessageDeletion(module, classLoader, db)
                            
                        }.onFailure { e ->
                            module.log(Log.ERROR, "LIMEs", "Lime Trigger Error: ${e.message}")
                        }
                    }
                    result
                }.build()
            )
        } catch (e: Exception) {
            module.log(Log.ERROR, "LIMEs", "ChatList onCreate Hook 失敗: ${e.message}")
        }
    }

    private fun hookMessageDeletion(module: LimeModule, classLoader: ClassLoader, db: SQLiteDatabase) {
        runCatching {
            // Hook Request (監聽發送隱藏/顯示指令)
            val requestClass = classLoader.loadClass(Constants.REQUEST_HOOK.className)
            requestClass.declaredMethods.forEach { method ->
                if (method.name == Constants.REQUEST_HOOK.methodName) {
                    module.hook(method, module.newHookBuilder().setInterceptor { chain ->
                        val result = chain.proceed()
                        val args = chain.args
                        
                        if (args.size >= 2 && args[1] != null) {
                            val paramValue = args[1].toString()
                            
                            if (paramValue.contains("setChatHiddenStatusRequest")) {
                                val talkId = extractTalkId(paramValue)
                                if (talkId != null) {
                                    if (paramValue.contains("hiddenStatus:true") || paramValue.contains("hidden:true")) {
                                        db.execSQL("INSERT OR IGNORE INTO lime_hidden_chats (chat_id) VALUES (?)", arrayOf(talkId))
                                        db.execSQL("UPDATE chat SET is_archived = 1 WHERE chat_id = ?", arrayOf(talkId))
                                        module.log(Log.DEBUG, "LIMEs", "Lime: Successfully Hid Chat $talkId")
                                    } else if (paramValue.contains("hiddenStatus:false") || paramValue.contains("hidden:false")) {
                                        db.execSQL("DELETE FROM lime_hidden_chats WHERE chat_id = ?", arrayOf(talkId))
                                        db.execSQL("UPDATE chat SET is_archived = 0 WHERE chat_id = ?", arrayOf(talkId))
                                        module.log(Log.DEBUG, "LIMEs", "Lime: Unhid Chat $talkId")
                                    }
                                }
                            }
                        }
                        result
                    }.build())
                }
            }

            // Hook Response (確保資料庫與本地名單同步)
            val responseClass = classLoader.loadClass(Constants.RESPONSE_HOOK.className)
            responseClass.declaredMethods.forEach { method ->
                if (method.name == Constants.RESPONSE_HOOK.methodName) {
                    module.hook(method, module.newHookBuilder().setInterceptor { chain ->
                        val result = chain.proceed()
                        runCatching {
                            db.execSQL("UPDATE chat SET is_archived = 1 WHERE chat_id IN (SELECT chat_id FROM lime_hidden_chats)")
                        }
                        result
                    }.build())
                }
            }
        }.onFailure { e ->
            module.log(Log.ERROR, "LIMEs", "ChatList 找不到 Class 或 Hook 失敗: ${e.message}")
        }
    }

    private fun extractTalkId(paramValue: String): String? {
        return runCatching {
            val pattern = Pattern.compile("chatMid:([^,\\)]+)")
            val matcher = pattern.matcher(paramValue)
            if (matcher.find()) matcher.group(1)?.trim() else null
        }.getOrNull()
    }
}
