package io.github.hiro.lime.hooks

import android.util.Log
import io.github.hiro.lime.LimeModule
import io.github.hiro.lime.LimeOptions
import io.github.libxposed.api.XposedModuleInterface.ExceptionMode

class PreventUnsendMessage : IHook {
    
    override fun hook(module: LimeModule, classLoader: ClassLoader, options: LimeOptions) {
        runCatching {
            // 1. 根據 Constants 找到 Thrift Response 處理類別
            val responseClass = classLoader.loadClass(Constants.RESPONSE_HOOK.className)
            val targetMethod = responseClass.getDeclaredMethod(
                Constants.RESPONSE_HOOK.methodName, 
                Any::class.java, 
                Any::class.java
            )

            // 2. 使用 API 101 的 HookBuilder
            module.hook(targetMethod, module.newHookBuilder()
                .setPriority(Int.MAX_VALUE)
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .setInterceptor { chain ->
                    val result = chain.proceed()
                    val args = chain.args

                    // 檢查是否為同步請求 (sync)
                    if (args.size >= 2 && args[0]?.toString() == "sync") {
                        runCatching {
                            // 第一層：取出 wrapper (欄位 "a")
                            val wrapper = args[1]?.javaClass?.getDeclaredField("a")?.apply { 
                                isAccessible = true 
                            }?.get(args[1]) ?: return@runCatching

                            // 第二層：取出 opResponse (父類別中的 "value_")
                            val opResponse = wrapper.javaClass.superclass?.getDeclaredField("value_")?.apply { 
                                isAccessible = true 
                            }?.get(wrapper) ?: return@runCatching
                            
                            // 第三層：取出操作列表 (ArrayList 形式，欄位 "a")
                            val operations = opResponse.javaClass.getDeclaredField("a")?.apply { 
                                isAccessible = true 
                            }?.get(opResponse) as? ArrayList<*> ?: return@runCatching

                            // 第四層：遍歷所有操作 (Operations)
                            for (op in operations) {
                                if (op == null) continue
                                
                                val typeField = op.javaClass.getDeclaredField("c").apply { 
                                    isAccessible = true 
                                }
                                val currentType = typeField.get(op)?.toString()

                                // 關鍵點：攔截收回指令 (NOTIFIED_DESTROY_MESSAGE)
                                if (currentType == "NOTIFIED_DESTROY_MESSAGE") {
                                    // 將指令修改為無效值 (DUMMY)，讓 LINE 忽略收回動作
                                    val dummyType = typeField.get(op)?.javaClass
                                        ?.getMethod("valueOf", String::class.java)
                                        ?.invoke(null, "DUMMY")
                                    
                                    typeField.set(op, dummyType)
                                    module.log(Log.INFO, "LIMEs", "✅ 成功攔截訊息收回指令")
                                }
                            }
                        }
                    }
                    result
                }.build()
            )
        }.onFailure { e ->
            module.log(Log.ERROR, "LIMEs", "PreventUnsendMessage Hook 失敗: ${e.message}")
        }
    }
}
