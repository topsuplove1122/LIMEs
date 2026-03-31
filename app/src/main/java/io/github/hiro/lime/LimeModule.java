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

    // 🚀 改回 onPackageLoaded，這是在 APP 剛被喚醒的最早期階段
    @Override
    public void onPackageLoaded(@NonNull XposedModuleInterface.PackageLoadedParam param) {
        super.onPackageLoaded(param);
        if (!param.getPackageName().equals("jp.naver.line.android")) return;

        log(2, "LIMEs", "1. LINE 已進入 onPackageLoaded，準備設置陷阱...");

        try {
            // 設置陷阱：等待 Application.attachBaseContext 執行
            Method attachBaseContextMethod = Application.class.getDeclaredMethod("attachBaseContext", Context.class);
            hook(attachBaseContextMethod).intercept(chain -> {
                Object result = chain.proceed();
                
                if (mContext == null) {
                    mContext = (Context) chain.getArgs().get(0);
                    log(2, "LIMEs", "2. 成功取得 Context！列車抵達，開始初始化...");
                    
                    // 初始化常數
                    Constants.initializeHooks(mContext, LimeModule.this);
                    
                    // 🚀 關鍵解法：直接從拿到的 Context 獲取 ClassLoader
                    ClassLoader classLoader = mContext.getClassLoader();
                    runAllHooks(classLoader);
                }
                return result;
            });
        } catch (Exception e) {
            log(4, "LIMEs", "LIMEs 掛載 attachBaseContext 失敗: " + e.getMessage());
        }
    }

    private void runAllHooks(ClassLoader classLoader) {
        log(2, "LIMEs", "3. 開始分發所有的 Hooks...");
        IHook[] hooks = {
            new RemoveAds(), new ChatList(), new UnsentRec(),
            new PreventUnsendMessage(), new ReadChecker()
        };
        for (IHook hookItem : hooks) {
            try {
                hookItem.hook(this, classLoader, limeOptions);
            } catch (Throwable t) {
                log(4, "LIMEs", "❌ Hook 執行發生例外: " + t.getMessage());
            }
        }
        log(2, "LIMEs", "4. 所有 Hooks 分發完畢！模組啟動完成！");
    }
}
