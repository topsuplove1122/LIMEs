package io.github.hiro.lime;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import io.github.hiro.lime.hooks.*;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class LimeModule extends XposedModule {
    private final LimeOptions limeOptions = new LimeOptions();

    // 🚨 致命關鍵：LibXposed API 101 強制要求這個特定的建構子，否則 LSPosed 絕對不會載入！
    public LimeModule(@NonNull XposedInterface base, @NonNull XposedModuleInterface.ModuleLoadedParam param) {
        super(base, param);
        // 只要模組一被 LSPosed 實例化，這行絕對會印出來！
        log(Log.INFO, "LIMEs", "0. 🚀 LimeModule 實例化成功！建構子已呼叫！");
    }

    // 階段 1：PackageLoaded (API 29+ 早期階段，此時 ClassLoader 尚未準備好，禁止操作)
    @Override
    public void onPackageLoaded(@NonNull XposedModuleInterface.PackageLoadedParam param) {
        super.onPackageLoaded(param);
        
        // 測試用：把經過的 APP 都印出來，確保 LSPosed 有在工作
        log(Log.INFO, "LIMEs", "-> 模組掃描中，當前載入 APP: " + param.getPackageName());

        if (!param.getPackageName().equals("jp.naver.line.android")) return;
        
        log(Log.ERROR, "LIMEs", "1. 💥 LINE onPackageLoaded 觸發！(早期階段，等待 ClassLoader...)");
    }

    // 階段 2：PackageReady (ClassLoader 完全就緒，獲取 ClassLoader 必須在這裡！)
    @Override
    public void onPackageReady(@NonNull XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);
        
        if (!param.getPackageName().equals("jp.naver.line.android")) return;

        log(Log.ERROR, "LIMEs", "2. 💥 LINE onPackageReady 觸發！ClassLoader 已就緒！");

        try {
            // 🚀 暴力解法：不等 Application，直接向系統底層拿 SystemContext！
            Object thread = Class.forName("android.app.ActivityThread").getMethod("currentActivityThread").invoke(null);
            Context systemContext = (Context) thread.getClass().getMethod("getSystemContext").invoke(thread);
            
            log(Log.INFO, "LIMEs", "3. 成功取得 SystemContext！");

            // 初始化常數
            Constants.initializeHooks(systemContext, this);
            
            // 在 Ready 階段，終於可以合法呼叫 getDefaultClassLoader() 了！
            runAllHooks(param.getDefaultClassLoader());
            
        } catch (Exception e) {
            log(Log.ERROR, "LIMEs", "LIMEs 初始化 Context 失敗: " + e.getMessage());
            // 萬一反射失敗的備用方案，確保 Hooks 依然會執行
            Constants.initializeHooks(null, this);
            runAllHooks(param.getDefaultClassLoader());
        }
    }

    private void runAllHooks(ClassLoader classLoader) {
        log(Log.INFO, "LIMEs", "4. 開始註冊所有 Hooks (準備進入 Interceptor Chain 模式)...");
        IHook[] hooks = {
            new RemoveAds(), new ChatList(), new UnsentRec(),
            new PreventUnsendMessage(), new ReadChecker()
        };
        for (IHook hookItem : hooks) {
            try {
                // 將 module 本身 (this) 和 classLoader 傳遞給各個 Hook 腳本
                hookItem.hook(this, classLoader, limeOptions);
            } catch (Throwable t) {
                log(Log.ERROR, "LIMEs", "❌ Hook 例外錯誤 (" + hookItem.getClass().getSimpleName() + "): " + t.getMessage());
            }
        }
        log(Log.ERROR, "LIMEs", "5. 所有 Hooks 註冊完畢！");
    }
}
