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

    public LimeModule() {
        super();
    }

    @Override
    public void onPackageLoaded(@NonNull XposedModuleInterface.PackageLoadedParam param) {
        super.onPackageLoaded(param);
        if (!param.getPackageName().equals("jp.naver.line.android")) return;

        // 💥 使用大紅字 Log，保證絕對看得到
        Log.e("LIMEs", "1. 💥 LINE onPackageLoaded 觸發！模組已成功喚醒！");

        try {
            // 🚀 暴力解法：不等 Application 了，直接向系統底層拿 SystemContext！
            Object thread = Class.forName("android.app.ActivityThread").getMethod("currentActivityThread").invoke(null);
            Context systemContext = (Context) thread.getClass().getMethod("getSystemContext").invoke(thread);
            
            Log.e("LIMEs", "2. 成功取得 SystemContext！");

            // 初始化常數
            Constants.initializeHooks(systemContext, this);
            
            // 執行所有 Hook
            runAllHooks(param.getClassLoader());
            
        } catch (Exception e) {
            Log.e("LIMEs", "LIMEs 初始化 Context 失敗: " + e.getMessage());
            // 萬一反射失敗，給它一個備用方案，保證一定會載入功能
            Constants.initializeHooks(null, this);
            runAllHooks(param.getClassLoader());
        }
    }

    private void runAllHooks(ClassLoader classLoader) {
        Log.e("LIMEs", "3. 開始註冊所有 Hooks...");
        IHook[] hooks = {
            new RemoveAds(), new ChatList(), new UnsentRec(),
            new PreventUnsendMessage(), new ReadChecker()
        };
        for (IHook hookItem : hooks) {
            try {
                hookItem.hook(this, classLoader, limeOptions);
            } catch (Throwable t) {
                Log.e("LIMEs", "❌ Hook 例外錯誤: " + t.getMessage());
            }
        }
        Log.e("LIMEs", "4. 所有 Hooks 註冊完畢！快去測試功能吧！");
    }
}
