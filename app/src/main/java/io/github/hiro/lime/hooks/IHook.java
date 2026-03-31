package io.github.hiro.lime.hooks;

import io.github.hiro.lime.LimeModule;
import io.github.hiro.lime.LimeOptions;

public interface IHook {
    void hook(LimeModule module, ClassLoader classLoader, LimeOptions limeOptions) throws Throwable;
}
