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

    // 🛠️ 根據 API 101，建構子不需要參數
    public LimeModule() {
        super();
    }

    @Override
    public void onPackageLoaded(@NonNull XposedModuleInterface.PackageLoadedParam param) {
        super.onPackageLoaded(param);
        if (!param.getPackageName().equals("jp.naver.line.android")) return;

        try {
            Method attachBaseContextMethod = Application.class.getDeclaredMethod("attachBaseContext", Context.class);
            // 🛠️ 完美優雅的 Lambda 寫法
            hook(attachBaseContextMethod).intercept(chain -> {
                Object result = chain.proceed();
                if (mContext == null) {
                    mContext = (Context) chain.getArgs().get(0);
                    // 在這裡初始化常數與呼叫 Hook
                    Constants.initializeHooks(mContext, LimeModule.this);
                    runAllHooks(param.getClassLoader());
                }
                return result;
            });
        } catch (Exception e) {
            // Android 內建 Log，保證安全印出錯誤
            android.util.Log.e("LIMEs", "啟動失敗: " + e.getMessage());
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
