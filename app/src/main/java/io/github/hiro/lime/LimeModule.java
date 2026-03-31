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
        super();
        attachFramework(base);
    }

    @Override
    public void onPackageReady(@NonNull XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);
        if (!param.getPackageName().equals("jp.naver.line.android")) return;

        log(2, "LIMEs", "LINE 已就緒，啟動 Hook (API 101)");

        // 必須先透過 Application 取得 Context 才能初始化
        try {
            Method attachBaseContextMethod = Application.class.getDeclaredMethod("attachBaseContext", Context.class);
            // 🛠️ 修正：加入明確的泛型標籤 <XposedInterface.BeforeHookCallback>
            hook(attachBaseContextMethod, new XposedInterface.Hooker<XposedInterface.BeforeHookCallback>() {
                @Override
                public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
                    Object result = chain.proceed();
                    if (mContext == null) {
                        mContext = (Context) chain.getArgs().get(0);
                        // 🛠️ 修正：使用剛取得的 mContext
                        Constants.initializeHooks(mContext, LimeModule.this);
                        runAllHooks(mContext.getClassLoader());
                    }
                    return result;
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
                log(4, "LIMEs", "Hook 失敗: " + t.getMessage());
            }
        }
    }
}
