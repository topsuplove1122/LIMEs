package io.github.hiro.lime_1.hooks;

import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.hiro.lime_1.LimeOptions;

public interface IHook {
    void hook(LimeOptions limeOptions, XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable;
}
