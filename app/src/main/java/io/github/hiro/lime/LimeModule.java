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

    public LimeModule() {
        super();
    }

    // 🛠️ 修正點：使用 onPackageReady 而非 onPackageLoaded
    // API 101 規定在 onPackageReady 才能獲取穩定的 ClassLoader
    @Override
    public void onPackageReady(@NonNull XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);
        if (!param.getPackageName().equals("jp.naver.line.android")) return;

        try {
            Method attachBaseContextMethod = Application.class.getDeclaredMethod("attachBaseContext", Context.class);
            hook(attachBaseContextMethod).intercept(chain -> {
                Object result = chain.proceed();
                if (mContext == null) {
                    mContext = (Context) chain.getArgs().get(0);
                    // 初始化常數與啟動所有 Hook
                    Constants.initializeHooks(mContext, LimeModule.this);
                    
                    // 這裡的 param.getClassLoader() 現在絕對找得到了！
                    runAllHooks(param.getClassLoader());
                }
                return result;
            });
        } catch (Exception e) {
            // Android 內建 Log 避免報錯
            android.util.Log.e("LIMEs", "LIMEs 啟動失敗: " + e.getMessage());
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
                log(4, "LIMEs", "Hook 失敗: " + t.getMessage());
            }
        }
    }
}
