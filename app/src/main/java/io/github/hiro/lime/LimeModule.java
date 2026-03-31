package io.github.hiro.lime;

import android.app.Application;
import android.content.Context;
import androidx.annotation.NonNull;
import java.lang.reflect.Method;
import io.github.hiro.lime.hooks.*; 
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class LimeModule extends XposedModule {
    private Context mContext = null;
    private final LimeOptions limeOptions = new LimeOptions();

    // 修正：只需傳 base 即可
    public LimeModule(@NonNull XposedInterface base, @NonNull XposedModuleInterface.ModuleLoadedParam param) {
        super(base); 
    }

    @Override
    public void onPackageLoaded(@NonNull XposedModuleInterface.PackageLoadedParam param) {
        super.onPackageLoaded(param);
        if (!param.getPackageName().equals("jp.naver.line.android")) return;

        // 修正：使用數字代表 Level 最保險 (2=INFO, 4=ERROR)
        log(2, "LIMEs", "開始注入 LINE...");

        try {
            Method attachBaseContextMethod = Application.class.getDeclaredMethod("attachBaseContext", Context.class);
            hook(attachBaseContextMethod, new XposedInterface.Hooker() {
                @Override
                // 修正：必須回傳 Object
                public Object intercept(@NonNull XposedInterface.BeforeHookCallback callback) throws Throwable {
                    if (mContext == null) {
                        mContext = (Context) callback.getArgs()[0];
                        log(2, "LIMEs", "已獲取 Context");
                        // 這裡確保 import 了 io.github.hiro.lime.hooks.Constants
                        Constants.initializeHooks(mContext, LimeModule.this);
                        runAllHooks(mContext.getClassLoader()); // 這裡改用 context 的 loader
                    }
                    return callback.callOriginal();
                }
            });
        } catch (Exception e) {
            log(4, "LIMEs", "啟動失敗: " + e.getMessage());
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
                log(4, "LIMEs", "Hook 失敗: " + t.getMessage());
            }
        }
    }
}
