package io.github.hiro.lime; // 確保只有一個 .hiro

import android.app.Application;
import android.content.Context;
import androidx.annotation.NonNull;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.hiro.lime.hooks.*; // 這樣會自動 import ChatList, Constants 等
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class LimeModule extends XposedModule {

    private Context mContext = null;
    private final LimeOptions limeOptions = new LimeOptions();

    // 修正：libxposed 101+ 的建構子只需要這兩個參數
    public LimeModule(@NonNull XposedInterface base, @NonNull XposedModuleInterface.ModuleLoadedParam param) {
        super(base, param);
    }

    @Override
    public void onPackageLoaded(@NonNull XposedModuleInterface.PackageLoadedParam param) {
        super.onPackageLoaded(param);

        if (!param.getPackageName().equals("jp.naver.line.android")) return;

        // 修正 log：需要 (級別, 標籤, 內容)
        log(XposedInterface.LOG_LEVEL_INFO, "LIMEs", "開始注入 LINE...");

        try {
            Method attachBaseContextMethod = Application.class.getDeclaredMethod("attachBaseContext", Context.class);
            
            // 修正 hook：Java 版的參數順序與介面要求
            hook(attachBaseContextMethod, new XposedInterface.Hooker() {
                @Override
                public void intercept(@NonNull XposedInterface.BeforeHookCallback callback) throws Throwable {
                    if (mContext == null) {
                        mContext = (Context) callback.getArgs()[0];
                        log(XposedInterface.LOG_LEVEL_INFO, "LIMEs", "已獲取 Context");
                        Constants.initializeHooks(mContext, LimeModule.this);
                        runAllHooks(param.getClassLoader());
                    }
                    callback.callOriginal(); // 必須呼叫，否則 App 會卡死
                }
            });
        } catch (Exception e) {
            log(XposedInterface.LOG_LEVEL_ERROR, "LIMEs", "啟動失敗: " + e.getMessage());
        }
    }

    private void runAllHooks(ClassLoader classLoader) {
        IHook[] hooks = {
            new RemoveAds(),
            new ChatList(),
            new UnsentRec(),
            new PreventUnsendMessage(),
            new ReadChecker()
        };

        for (IHook hookItem : hooks) {
            try {
                hookItem.hook(this, classLoader, limeOptions);
            } catch (Throwable t) {
                log(XposedInterface.LOG_LEVEL_ERROR, "LIMEs", "Hook 失敗: " + t.getMessage());
            }
        }
    }
}
