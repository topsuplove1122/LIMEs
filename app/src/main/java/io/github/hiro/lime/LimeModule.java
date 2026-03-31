package io.github.hiro.lime;

import androidx.annotation.NonNull;
import io.github.hiro.lime.hooks.*;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class LimeModule extends XposedModule {
    private final LimeOptions limeOptions = new LimeOptions();

    public LimeModule(@NonNull XposedInterface base, @NonNull XposedModuleInterface.ModuleLoadedParam param) {
        super(); 
        attachFramework(base); 
    }

    @Override
    public void onPackageReady(@NonNull XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);
        if (!param.getPackageName().equals("jp.naver.line.android")) return;

        log(2, "LIMEs", "LINE Ready. Starting Hooks (API 101)...");
        Constants.initializeHooks(getContext(), this);
        runAllHooks(param.getClassLoader());
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
                log(4, "LIMEs", "Hook failed: " + t.getMessage());
            }
        }
    }
}
