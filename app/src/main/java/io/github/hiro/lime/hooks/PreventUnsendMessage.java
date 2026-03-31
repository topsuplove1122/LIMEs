package io.github.hiro.lime.hooks;

import androidx.annotation.NonNull;
import io.github.hiro.lime.LimeModule;
import io.github.hiro.lime.LimeOptions;
import io.github.hiro.lime.hooks.Constants;
import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.Method;

public class PreventUnsendMessage implements IHook {
    @Override
    public void hook(LimeModule module, ClassLoader classLoader, LimeOptions limeOptions) throws Throwable {
        Class<?> responseClass = classLoader.loadClass(Constants.RESPONSE_HOOK.className);
        Method targetMethod = responseClass.getDeclaredMethod(Constants.RESPONSE_HOOK.methodName, Object.class, Object.class);

        // 使用 Interceptor Chain 模型
        module.hook(targetMethod, new XposedInterface.Hooker() {
            @Override
            public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
                // 1. 執行原方法 (相當於以前的 callOriginal)
                Object result = chain.proceed();

                // 2. 業務邏輯 (例如攔截收回)
                Object[] args = chain.getArgs();
                if (args.length >= 2 && args[0].toString().equals("sync")) {
                    // 解析與竄改 Thrift 封包邏輯...
                    module.log("LIMEs: 已攔截並處理同步封包");
                }

                return result;
            }
        });
    }
}
