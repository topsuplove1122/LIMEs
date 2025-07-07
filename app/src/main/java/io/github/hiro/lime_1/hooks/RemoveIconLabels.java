package io.github.hiro.lime_1.hooks;

import android.view.View;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.hiro.lime_1.LimeOptions;

public class RemoveIconLabels implements IHook {
    @Override
    public void hook(LimeOptions limeOptions, XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        if (!limeOptions.removeIconLabels.checked) return;

        XposedBridge.hookAllConstructors(
                loadPackageParam.classLoader.loadClass("jp.naver.line1.android.activity.main.bottomnavigationbar.BottomNavigationBarTextView"),
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        ((View) param.thisObject).setVisibility(View.GONE);
                    }
                }
        );
    }
}
