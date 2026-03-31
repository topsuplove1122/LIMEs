package io.github.hiro.lime.hooks

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import io.github.hiro.lime.LimeModule
import io.github.hiro.lime.LimeOptions
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class ReadChecker : IHook {
    private var dbLine: SQLiteDatabase? = null
    private var dbContact: SQLiteDatabase? = null
    private var currentChatId: String? = null

    override fun hook(module: LimeModule, classLoader: ClassLoader, limeOptions: LimeOptions) {
        // 1. 初始化資料庫連線 (在 Application onCreate)
        runCatching {
            val onCreateMethod = Application::class.java.getDeclaredMethod("onCreate")
            module.hook(onCreateMethod, module.newHookBuilder().setInterceptor { chain ->
                val result = chain.proceed()
                val appContext = chain.thisObject as? Application ?: return@setInterceptor result

                val dbPathLine = appContext.getDatabasePath("naver_line")
                val dbPathContact = appContext.getDatabasePath("contact")

                if (dbPathLine.exists() && dbPathContact.exists() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    val params = SQLiteDatabase.OpenParams.Builder()
                        .addOpenFlags(SQLiteDatabase.OPEN_READWRITE)
                        .build()
                    
                    runCatching {
                        dbLine = SQLiteDatabase.openDatabase(dbPathLine, params)
                        dbContact = SQLiteDatabase.openDatabase(dbPathContact, params)
                        
                        // 建立自定義的已讀記錄表
                        dbLine?.execSQL("CREATE TABLE IF NOT EXISTS lime_read_watermarks (chat_id TEXT, reader_mid TEXT, max_msg_id INTEGER, PRIMARY KEY(chat_id, reader_mid))")
                        
                        hookNetwork(module, classLoader)
                    }
                }
                result
            }.build())
        }.onFailure { e -> module.log(Log.ERROR, "LIMEs", "ReadChecker (onCreate) 失敗: ${e.message}") }

        // 2. 攔截當前聊天室 ID (ChatHistoryRequest)
        runCatching {
            val chatHistoryRequestClass = classLoader.loadClass("com.linecorp.line.chat.request.ChatHistoryRequest")
            val getChatIdMethod = chatHistoryRequestClass.getDeclaredMethod("getChatId")
            module.hook(getChatIdMethod, module.newHookBuilder().setInterceptor { chain ->
                val result = chain.proceed()
                currentChatId = result as? String
                result
            }.build())
        }.onFailure { e -> module.log(Log.ERROR, "LIMEs", "ReadChecker (getChatId) 失敗: ${e.message}") }

        // 3. 注入 UI 懸浮按鈕 (ChatHistoryActivity)
        runCatching {
            val chatHistoryActivityClass = classLoader.loadClass("jp.naver.line.android.activity.chathistory.ChatHistoryActivity")
            val activityOnCreate = chatHistoryActivityClass.getDeclaredMethod("onCreate", Bundle::class.java)
            module.hook(activityOnCreate, module.newHookBuilder().setInterceptor { chain ->
                val result = chain.proceed()
                val activity = chain.thisObject as? Activity
                if (currentChatId != null && activity != null) {
                    addTopButton(activity)
                }
                result
            }.build())
        }.onFailure { e -> module.log(Log.ERROR, "LIMEs", "ReadChecker (Activity) 失敗: ${e.message}") }
    }

    private fun hookNetwork(module: LimeModule, classLoader: ClassLoader) {
        runCatching {
            val responseClass = classLoader.loadClass(Constants.RESPONSE_HOOK.className)
            responseClass.declaredMethods.forEach { method ->
                if (method.name == Constants.RESPONSE_HOOK.methodName) {
                    module.hook(method, module.newHookBuilder().setInterceptor { chain ->
                        val result = chain.proceed()
                        val args = chain.args
                        if (args.size < 2 || args[1] == null) return@setInterceptor result

                        val paramValue = args[1].toString()
                        if (paramValue.contains("type:NOTIFIED_READ_MESSAGE")) {
                            paramValue.split("Operation(").forEach { op ->
                                if (op.contains("type:NOTIFIED_READ_MESSAGE")) {
                                    val readerMid = extractParam(op, "param2:")
                                    val msgIdStr = extractParam(op, "param3:")
                                    
                                    if (readerMid != null && msgIdStr != null) {
                                        dbLine?.let { db ->
                                            db.rawQuery("SELECT chat_id FROM chat_history WHERE server_id = ?", arrayOf(msgIdStr)).use { c ->
                                                if (c.moveToFirst()) {
                                                    val exactChatId = c.getString(0)
                                                    db.execSQL("INSERT OR REPLACE INTO lime_read_watermarks (chat_id, reader_mid, max_msg_id) VALUES (?, ?, ?)",
                                                        arrayOf(exactChatId, readerMid, msgIdStr.toLong()))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        result
                    }.build())
                }
            }
        }
    }

    private fun addTopButton(activity: Activity) {
        val buttonId = 95279527
        val layout = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (activity.findViewById<View>(buttonId) != null) return

        val btn = Button(activity).apply {
            id = buttonId
            text = "👀"
            setBackgroundColor(Color.parseColor("#AA000000"))
            setTextColor(Color.WHITE)
            textSize = 12f
        }

        val prefs = activity.getSharedPreferences("LimeReadCheckerPrefs", Context.MODE_PRIVATE)
        val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(prefs.getInt("btn_x", 150), prefs.getInt("btn_y", 180), 0, 0)
        }
        btn.layoutParams = params

        btn.setOnClickListener {
            if (currentChatId == null || dbLine == null) return@setOnClickListener
            Toast.makeText(activity, "資料撈取中...", Toast.LENGTH_SHORT).show()
            Thread {
                val resultText = buildReadDataString()
                activity.runOnUiThread {
                    val textView = TextView(activity).apply {
                        text = resultText
                        setPadding(40, 40, 40, 40)
                    }
                    val scrollView = ScrollView(activity).apply { addView(textView) }
                    AlertDialog.Builder(activity)
                        .setTitle("已讀名單 (最近 15 則文字)")
                        .setView(scrollView)
                        .setPositiveButton("關閉", null)
                        .show()
                }
            }.start()
        }

        // 處理按鈕拖拽
        btn.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isMoved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val lp = v.layoutParams as FrameLayout.LayoutParams
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = lp.leftMargin
                        initialY = lp.topMargin
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isMoved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isMoved = true
                        lp.leftMargin = initialX + dx
                        lp.topMargin = initialY + dy
                        v.layoutParams = lp
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isMoved) v.performClick()
                        else prefs.edit().putInt("btn_x", lp.leftMargin).putInt("btn_y", lp.topMargin).apply()
                        return true
                    }
                }
                return false
            }
        })
        layout.addView(btn)
    }

    private fun buildReadDataString(): String {
        val sb = StringBuilder()
        runCatching {
            val watermarks = mutableMapOf<String, Long>()
            dbLine?.rawQuery("SELECT reader_mid, max_msg_id FROM lime_read_watermarks WHERE chat_id = ?", arrayOf(currentChatId)).use { c ->
                while (c?.moveToNext() == true) watermarks[c.getString(0)] = c.getLong(1)
            }

            dbLine?.rawQuery("SELECT server_id, content, created_time FROM chat_history WHERE chat_id = ? AND from_mid IS NULL AND type = 1 ORDER BY CAST(server_id AS INTEGER) DESC LIMIT 15", arrayOf(currentChatId)).use { c ->
                if (c == null || c.count == 0) return "最近沒有傳送文字訊息。"
                while (c.moveToNext()) {
                    val serverId = c.getLong(0)
                    sb.append("💬 ").append(c.getString(1)).append("\n🕒 ").append(formatTime(c.getString(2))).append("\n")
                    
                    val readers = watermarks.filter { it.value >= serverId }.keys.map { getContactName(it) ?: "未知名稱" }
                    
                    if (readers.isEmpty()) sb.append("❌ 尚無人已讀\n\n")
                    else {
                        sb.append("👀 已讀 (${readers.size})：\n")
                        readers.forEach { sb.append("  - $it\n") }
                        sb.append("\n")
                    }
                }
            }
        }.onFailure { sb.append("發生錯誤：${it.message}") }
        return sb.toString()
    }

    private fun getContactName(mid: String): String? {
        return dbContact?.rawQuery("SELECT profile_name FROM contacts WHERE mid = ?", arrayOf(mid)).use { c ->
            if (c?.moveToFirst() == true) c.getString(0) else null
        }
    }

    private fun extractParam(operation: String, prefix: String): String? {
        val matcher = Pattern.compile("$prefix([a-zA-Z0-9]+)").matcher(operation)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun formatTime(epochStr: String): String {
        return runCatching {
            SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(epochStr.toLong()))
        }.getOrDefault("未知時間")
    }
}
