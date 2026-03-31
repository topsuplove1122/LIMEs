package io.github.hiro.lime

import android.content.Context
import android.util.Log
import io.github.hiro.lime.hooks.*
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

class LimeModule : XposedModule() {

    private val limeOptions = LimeOptions()

    /**
     * 階段 1：PackageLoaded
     * API 29+ 早期階段，此時 ClassLoader 尚未準備好，禁止執行 Hook 操作。
     */
    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)

        // 測試用：記錄所有掃描到的 APP，確保模組正在運作
        log(Log.INFO, "LIMEs", "-> 模組掃描中，當前載入 APP: ${param.packageName}")

        if (param.packageName == "jp.naver.line.android") {
            log(Log.ERROR, "LIMEs", "1. 💥 LINE onPackageLoaded 觸發！(早期階段，等待 ClassLoader...)")
        }
    }

    /**
     * 階段 2：PackageReady
     * ClassLoader 完全就緒，獲取 ClassLoader 並執行 Hook 必須在這裡！
     */
    override fun onPackageReady(param: PackageReadyParam) {
        super.onPackageReady(param)

        if (param.packageName != "jp.naver.line.android") return

        log(Log.ERROR, "LIMEs", "2. 💥 LINE onPackageReady 觸發！ClassLoader 已就緒！")

        // 使用 Kotlin 的 runCatching 替代 try-catch
        runCatching {
            // 🚀 暴力解法：向系統底層拿 SystemContext
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThreadMethod = activityThreadClass.getMethod("currentActivityThread")
            val thread = currentActivityThreadMethod.invoke(null)
            
            val getSystemContextMethod = thread.javaClass.getMethod("getSystemContext")
            val systemContext = getSystemContextMethod.invoke(thread) as Context

            log(Log.INFO, "LIMEs", "3. 成功取得 SystemContext！")

            // 初始化常數
            Constants.initializeHooks(systemContext, this)
            
            // 執行所有 Hook
            runAllHooks(param.classLoader)

        }.onFailure { e ->
            log(Log.ERROR, "LIMEs", "LIMEs 初始化 Context 失敗: ${e.message}")
            
            // 備用方案：即使 Context 拿不到，也要嘗試執行 Hooks (傳入 null)
            Constants.initializeHooks(null, this)
            runAllHooks(param.classLoader)
        }
    }

    /**
     * 註冊並運行所有 Hook 腳本
     */
    private fun runAllHooks(classLoader: ClassLoader) {
        log(Log.INFO, "LIMEs", "4. 開始註冊所有 Hooks (準備進入 Interceptor Chain 模式)...")

        val hooks: Array<IHook> = arrayOf(
            RemoveAds(),
            ChatList(),
            UnsentRec(),
            PreventUnsendMessage(),
            ReadChecker()
        )

        for (hookItem in hooks) {
            runCatching {
                // 將 module 本身 (this), classLoader 與設定傳遞給各個 Hook 類別
                hookItem.hook(this, classLoader, limeOptions)
            }.onFailure { t ->
                log(Log.ERROR, "LIMEs", "❌ Hook 例外錯誤 (${hookItem.javaClass.simpleName}): ${t.message}")
            }
        }

        log(Log.ERROR, "LIMEs", "5. 所有 Hooks 註冊完畢！")
    }
}
