package io.github.hiro.lime.hooks

import io.github.hiro.lime.LimeModule
import io.github.hiro.lime.LimeOptions

interface IHook {
    /**
     * Hook 介面定義
     * @param module 傳入 LimeModule 實例，用於調用 log 或執行 hook 方法
     * @param classLoader 目標 App (LINE) 的 ClassLoader
     * @param options 模組配置選項
     */
    @Throws(Throwable::class)
    fun hook(module: LimeModule, classLoader: ClassLoader, options: LimeOptions)
}
