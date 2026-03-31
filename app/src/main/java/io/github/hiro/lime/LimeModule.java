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

    public LimeModule(@NonNull XposedInterface base, @NonNull XposedModuleInterface.ModuleLoadedParam param) {
        super(base); 
    }

    @Override
    public void onPackageLoaded(@NonNull XposedModuleInterface.PackageLoadedParam param) {
        super.onPackageLoaded(param);
        if (!param.getPackageName().equals("jp.naver.line.android")) return;

        log(2, "LIMEs", "開始注入 LINE...");

        try {
            Method attachBaseContextMethod = Application.class.getDeclaredMethod("attachBaseContext", Context.class);
            // 🛠️ 關鍵修正：加上泛型類型宣告
            hook(attachBaseContextMethod, new XposedInterface.Hooker<XposedInterface.BeforeHookCallback>() {
                @Override
                public Object intercept(@NonNull XposedInterface.BeforeHookCallback callback) throws Throwable {
                    if (mContext == null) {
                        mContext = (Context) callback.getArgs()[0];
                        log(2, "LIMEs", "已獲取 Context");
                        Constants.initializeHooks(mContext, LimeModule.this);
                        runAllHooks(mContext.getClassLoader());
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
            new RemoveAds(), new ChatList(), new UnsentRec(),
            new PreventUnsendMessage(), new ReadChecker()
        };
        for (IHook hookItem : hooks) {
            try {
                hookItem.hook(this, classLoader, limeOptions);
            } catch (Throwable t) {
                log(4, "LIMEs", "Hook 執行失敗: " + t.getMessage());
            }
        }
    }
}
