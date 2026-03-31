package io.github.hiro.lime.hooks

import android.content.Context
import android.util.Log
import io.github.hiro.lime.LimeModule

object Constants {
    const val PACKAGE_NAME = "jp.naver.line.android"
    
    // 使用 var 因為 initializeHooks 可能會動態修改它們
    var USER_AGENT_HOOK = HookTarget("qi1.c", "j")
    var REQUEST_HOOK = HookTarget("org.apache.thrift.o", "b")
    var RESPONSE_HOOK = HookTarget("org.apache.thrift.o", "a")
    var WEBVIEW_CLIENT_HOOK = HookTarget("VP0.k", "onPageFinished")

    /**
     * 根據 LINE 版本號動態修正 Hook 目標類別
     */
    @JvmStatic
    fun initializeHooks(context: Context?, module: LimeModule) {
        var versionName = "14.19.1" // 🪂 預設降落傘
        
        runCatching {
            context?.let {
                val packageInfo = it.packageManager.getPackageInfo(PACKAGE_NAME, 0)
                versionName = packageInfo.versionName
            }
            
            module.log(Log.ERROR, "LIMEs", "LINE 目標版本: $versionName")

            // 根據版本號動態切換混淆路徑
            when (versionName) {
                "14.19.1" -> {
                    REQUEST_HOOK = HookTarget("org.apache.thrift.l", "b")
                    RESPONSE_HOOK = HookTarget("org.apache.thrift.l", "a")
                }
                "15.9.3" -> {
                    RESPONSE_HOOK = HookTarget("org.apache.thrift.l", "a")
                }
                // 未來可以在這裡輕鬆擴充更多版本
            }
        }.onFailure { e ->
            module.log(Log.ERROR, "LIMEs", "Constants 初始化錯誤: ${e.message}")
        }
    }

    /**
     * 定義 Hook 目標的數據結構
     */
    data class HookTarget(
        val className: String,
        val methodName: String
    )
}
