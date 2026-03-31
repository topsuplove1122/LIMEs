package io.github.hiro.lime;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import io.github.hiro.lime.hooks.*;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class LimeModule extends XposedModule {
    private final LimeOptions limeOptions = new LimeOptions();

    public LimeModule() {
        super();
    }

    // 階段 1：PackageLoaded (極早期階段，禁止操作 ClassLoader)
    @Override
    public void onPackageLoaded(@NonNull XposedModuleInterface.PackageLoadedParam param) {
        super.onPackageLoaded(param);
        if (!param.getPackageName().equals("jp.naver.line.android")) return;

        log(Log.INFO, "LIMEs", "1. 💥 LINE onPackageLoaded 觸發！(早期階段，等待 ClassLoader...)");
    }

    // 階段 2：PackageReady (ClassLoader 完全就緒，這才是我們的主戰場)
    @Override
    public void onPackageReady(@NonNull XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);
        if (!param.getPackageName().equals("jp.naver.line.android")) return;

        log(Log.ERROR, "LIMEs", "2. 💥 LINE onPackageReady 觸發！ClassLoader 已就緒！");

        try {
            // 🚀 暴力解法：拿 SystemContext
            Object thread = Class.forName("android.app.ActivityThread").getMethod("currentActivityThread").invoke(null);
            Context systemContext = (Context) thread.getClass().getMethod("getSystemContext").invoke(thread);
            
            log(Log.INFO, "LIMEs", "3. 成功取得 SystemContext！");

            // 初始化常數
            Constants.initializeHooks(systemContext, this);
            
            // 這裡終於可以合法呼叫 getDefaultClassLoader() 了！
            runAllHooks(param.getDefaultClassLoader());
            
        } catch (Exception e) {
            log(Log.ERROR, "LIMEs", "LIMEs 初始化 Context 失敗: " + e.getMessage());
            // 備用方案
            Constants.initializeHooks(null, this);
            runAllHooks(param.getDefaultClassLoader());
        }
    }

    private void runAllHooks(ClassLoader classLoader) {
        log(Log.INFO, "LIMEs", "4. 開始註冊所有 Hooks (Interceptor Chain 模式)...");
        IHook[] hooks = {
            new RemoveAds(), new ChatList(), new UnsentRec(),
            new PreventUnsendMessage(), new ReadChecker()
        };
        for (IHook hookItem : hooks) {
            try {
                hookItem.hook(this, classLoader, limeOptions);
            } catch (Throwable t) {
                log(Log.ERROR, "LIMEs", "❌ Hook 例外錯誤 (" + hookItem.getClass().getSimpleName() + "): " + t.getMessage());
            }
        }
        log(Log.ERROR, "LIMEs", "5. 所有 Hooks 註冊完畢！");
    }
}
