package io.github.hiro.lime.hooks;

// 1. 移除舊版的 de.robv.android.xposed.callbacks.XC_LoadPackage 匯入
import io.github.hiro.lime.LimeModule;
import io.github.hiro.lime.LimeOptions;

public interface IHook {
    /**
     * @param module      傳入你的 LimeModule 實體，用來呼叫 module.hook() 與 module.log()
     * @param classLoader 用來取代舊版 param.classLoader，負責尋找 LINE 的 Class
     * @param limeOptions 你的設定檔，用來判斷開關狀態
     */
    void hook(LimeModule module, ClassLoader classLoader, LimeOptions limeOptions) throws Throwable;
}
