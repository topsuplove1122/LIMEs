package io.github.hiro.lime.hooks

import android.graphics.Canvas
import android.util.Log
import android.view.View
import android.view.ViewGroup
import io.github.hiro.lime.LimeModule
import io.github.hiro.lime.LimeOptions
import io.github.libxposed.api.XposedModuleInterface.ExceptionMode

class RemoveAds : IHook {

    override fun hook(module: LimeModule, classLoader: ClassLoader, options: LimeOptions) {
        
        // 1. 攔截廣告請求 (Thrift Request 層)
        runCatching {
            val requestClass = classLoader.loadClass(Constants.REQUEST_HOOK.className)
            val reqMethod = requestClass.getDeclaredMethod(
                Constants.REQUEST_HOOK.methodName, 
                Any::class.java, 
                Any::class.java
            )

            module.hook(reqMethod, module.newHookBuilder()
                .setInterceptor { chain ->
                    val args = chain.args
                    if (args.isNotEmpty() && args[0]?.toString()?.contains("Banners") == true) {
                        module.log(Log.DEBUG, "LIMEs", "🚫 已阻斷廣告請求: ${args[0]}")
                        return@setInterceptor null // 直接擋下廣告 Request，不執行 proceed
                    }
                    chain.proceed()
                }.build()
            )
        }.onFailure { e -> module.log(Log.ERROR, "LIMEs", "RemoveAds (Request) 失敗: ${e.message}") }

        // 2. 隱藏頂部廣告 SmartChannel (View 繪製層)
        runCatching {
            val smartChannelClass = classLoader.loadClass("com.linecorp.line.admolin.smartch.v2.view.SmartChannelViewLayout")
            val dispatchDrawMethod = smartChannelClass.getDeclaredMethod("dispatchDraw", Canvas::class.java)

            module.hook(dispatchDrawMethod, module.newHookBuilder()
                .setInterceptor { chain ->
                    val view = chain.thisObject as? View
                    // 找到 SmartChannelView 的父容器並將其設為 GONE
                    (view?.parent as? View)?.visibility = View.GONE
                    chain.proceed()
                }.build()
            )
        }.onFailure { e -> module.log(Log.ERROR, "LIMEs", "RemoveAds (SmartChannel) 失敗: ${e.message}") }

        // 3. 隱藏動態產生的 Ad View (Layout 佈局層)
        runCatching {
            val addViewMethod = ViewGroup::class.java.getDeclaredMethod(
                "addView", 
                View::class.java, 
                ViewGroup.LayoutParams::class.java
            )

            module.hook(addViewMethod, module.newHookBuilder()
                .setPriority(Int.MIN_VALUE) // 較低優先權，確保在 View 被加入後處理
                .setInterceptor { chain ->
                    val result = chain.proceed()
                    val args = chain.args
                    
                    if (args.isNotEmpty()) {
                        val v = args[0] as? View
                        // 檢查 View 的類別名稱是否包含 "Ad"，如果是則隱藏
                        if (v?.javaClass?.name?.contains("Ad") == true) {
                            v.visibility = View.GONE
                            // module.log(Log.DEBUG, "LIMEs", "🕵️ 已隱藏動態廣告元件: ${v.javaClass.name}")
                        }
                    }
                    result
                }.build()
            )
        }.onFailure { e -> module.log(Log.ERROR, "LIMEs", "RemoveAds (addView) 失敗: ${e.message}") }
    }
}
