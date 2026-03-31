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

    // 🛠️ 修正 1：libxposed 的 XposedModule 在 Java 中通常只需要 base
    public LimeModule(@NonNull XposedInterface base, @NonNull XposedModuleInterface.ModuleLoadedParam param) {
        super(base); 
    }

    @Override
    public void onPackageLoaded(@NonNull XposedModuleInterface.PackageLoadedParam param) {
        super.onPackageLoaded(param);

        if (!param.getPackageName().equals("jp.naver.line.android")) return;

        // 🛠️ 修正 2：使用正確的日誌級別常數 (數字或全名)
        log(2, "LIMEs", "開始注入 LINE (API 101)...");

        try {
            Method attachBaseContextMethod = Application.class.getDeclaredMethod("attachBaseContext", Context.class);
            
            hook(attachBaseContextMethod, new XposedInterface.Hooker() {
                @Override
                // 🛠️ 修正 3：必須回傳 Object，不能是 void
                public Object intercept(@NonNull XposedInterface.BeforeHookCallback callback) throws Throwable {
                    if (mContext == null) {
                        mContext = (Context) callback.getArgs()[0];
                        log(2, "LIMEs", "已獲取 Context");
                        Constants.initializeHooks(mContext, LimeModule.this);
                        runAllHooks(param.getClassLoader());
                    }
                    // 🛠️ 修正 4：必須回傳原本方法的結果
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
                log(4, "LIMEs", "Hook 項目執行失敗: " + t.getMessage());
            }
        }
    }
}
