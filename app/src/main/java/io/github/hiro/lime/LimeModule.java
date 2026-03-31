package io.github.hiro.lime;

import androidx.annotation.NonNull;
import io.github.hiro.lime.hooks.*;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Method;

public class LimeModule extends XposedModule {
    private final LimeOptions limeOptions = new LimeOptions();

    // 核心架構：構造函數必須調用 attachFramework
    public LimeModule(@NonNull XposedInterface base, @NonNull XposedModuleInterface.ModuleLoadedParam param) {
        super(base, param);
        attachFramework(base);
    }

    // 生命週期：onPackageReady 是 Modern API 的主要 Hook 點
    @Override
    public void onPackageReady(@NonNull XposedModuleInterface.PackageLoadedParam param) {
        super.onPackageReady(param);
        if (!param.getPackageName().equals("jp.naver.line.android")) return;

        log("LIMEs: 檢測到 LINE，準備注入 (API 101)");

        // 這裡不需要手動 Hook Application.onCreate 獲取 Context
        // 因為 Modern API 已經在 onPackageReady 提供穩定的 ClassLoader
        runAllHooks(param.getClassLoader());
    }

    private void runAllHooks(ClassLoader classLoader) {
        IHook[] hooks = {
            new RemoveAds(),
            new ChatList(),
            new UnsendRec(),
            new PreventUnsendMessage(),
            new ReadChecker()
        };

        for (IHook hookItem : hooks) {
            try {
                hookItem.hook(this, classLoader, limeOptions);
            } catch (Throwable t) {
                log("LIMEs: Hook 項目執行失敗: " + t.getMessage());
            }
        }
    }
}
