package io.github.hiro.lime;

import androidx.annotation.NonNull;
import io.github.hiro.lime.hooks.*;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class LimeModule extends XposedModule {
    private final LimeOptions limeOptions = new LimeOptions();

    public LimeModule(@NonNull XposedInterface base, @NonNull XposedModuleInterface.ModuleLoadedParam param) {
        super(); // 編譯器要求無參數
        attachFramework(base); // API 101 必要步驟
    }

    @Override
    public void onPackageReady(@NonNull XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);
        if (!param.getPackageName().equals("jp.naver.line.android")) return;

        // 修正 log 呼叫方式 (2 = INFO)
        log(2, "LIMEs", "LINE 已就緒，啟動 Hook (API 101)");
        runAllHooks(param.getClassLoader());
    }

    private void runAllHooks(ClassLoader classLoader) {
        IHook[] hooks = {
            new RemoveAds(), 
            new ChatList(), 
            new UnsentRec(), // 確保此檔案存在且名稱正確
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
