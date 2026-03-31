package io.github.hiro.hiro.lime;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.hiro.hiro.lime.hooks.ChatList;
import io.github.hiro.hiro.lime.hooks.Constants;
import io.github.hiro.hiro.lime.hooks.IHook;
import io.github.hiro.hiro.lime.hooks.PreventUnsendMessage;
import io.github.hiro.hiro.lime.hooks.RemoveAds;
import io.github.hiro.hiro.lime.hooks.UnsentRec;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class LimeModule extends XposedModule {

    private Context mContext = null;
    private final LimeOptions limeOptions = new LimeOptions();

    // 必須的建構子
    public LimeModule(@NonNull XposedInterface base, @NonNull XposedModuleInterface.ModuleLoadedParam param) {
        super(base, param);
    }

    @Override
    public void onPackageLoaded(@NonNull XposedModuleInterface.PackageLoadedParam param) {
        super.onPackageLoaded(param);

        // 只針對 LINE 進行處理
        if (!param.getPackageName().equals("jp.naver.line.android")) {
            return;
        }

        log("LIMEs: 開始注入 LINE (API 101.1)...");

        try {
            // 透過 Hook Application.attachBaseContext 獲取最乾淨的 Context
            Method attachBaseContextMethod = Application.class.getDeclaredMethod("attachBaseContext", Context.class);
            
            hook(attachBaseContextMethod, new XposedInterface.Hooker() {
                @Override
                public void beforeInvoke(@NonNull XposedInterface.BeforeHookCallback callback) {
                    if (mContext == null) {
                        mContext = (Context) callback.getArgs()[0];
                        log("LIMEs: 已獲取全局 Context，啟動初始化流程...");

                        // 1. 初始化 Constants (判斷 LINE 版本並載入 Hook 點)
                        Constants.initializeHooks(mContext, LimeModule.this);

                        // 2. 載入使用者的設定檔 (透過你原本在 MainActivity 存好的 SAF URI)
                        // 注意：這裡假設你已經把 loadSettings 邏輯移入 LimeModule 或相關類別
                        // loadSettings(mContext); 

                        // 3. 執行功能 Hook
                        runAllHooks(param.getClassLoader());
                    }
                }

                @Override
                public void afterInvoke(@NonNull XposedInterface.AfterHookCallback callback) {}
            });
        } catch (Exception e) {
            log("LIMEs 總部啟動失敗: " + e.getMessage());
        }
    }

    /**
     * 執行所有分散在 hooks 資料夾中的功能
     */
    private void runAllHooks(ClassLoader classLoader) {
        // 將你要啟用的功能類別加入清單
        List<IHook> hooks = new ArrayList<>();
        hooks.add(new RemoveAds());
        hooks.add(new ChatList());
        hooks.add(new UnsentRec());
        hooks.add(new PreventUnsendMessage());
        hooks.add(new ReadChecker());

        for (IHook hookItem : hooks) {
            try {
                // 將 'this' (LimeModule 實例) 傳進去，讓子類別能呼叫 module.hook()
                hookItem.hook(this, classLoader, limeOptions);
            } catch (Throwable t) {
                log("LIMEs 功能載入失敗 [" + hookItem.getClass().getSimpleName() + "]: " + t.getMessage());
            }
        }
        log("LIMEs: 所有功能 Hook 執行完畢。");
    }
}
