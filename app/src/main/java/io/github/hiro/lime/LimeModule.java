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

    @Override
    public void onPackageLoaded(@NonNull XposedModuleInterface.PackageLoadedParam param) {
        super.onPackageLoaded(param);
        if (!param.getPackageName().equals("jp.naver.line.android")) return;

        try {
            // 🚀 致命修正：attachBaseContext 是宣告在 ContextWrapper 裡的！
            Method attachBaseContextMethod = android.content.ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
            
            hook(attachBaseContextMethod).intercept(chain -> {
                Object result = chain.proceed();
                
                // 確認這是 Application 的啟動階段，而不是其他 Service 或 Activity
                if (chain.getThisObject() instanceof Application && mContext == null) {
                    mContext = (Context) chain.getArgs().get(0);
                    log(2, "LIMEs", "成功攔截 Context！開始載入模組...");
                    
                    // 從 Context 取得最穩定的 ClassLoader
                    ClassLoader classLoader = mContext.getClassLoader();
                    
                    // 初始化常數與所有掛載點
                    Constants.initializeHooks(mContext, LimeModule.this);
                    runAllHooks(classLoader);
                }
                return result;
            });
        } catch (Exception e) {
            log(4, "LIMEs", "LIMEs 掛載失敗: " + e.getMessage());
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
                log(4, "LIMEs", "Hook 例外錯誤: " + t.getMessage());
            }
        }
        log(2, "LIMEs", "LIMEs 模組所有功能已成功啟動！");
    }
}
