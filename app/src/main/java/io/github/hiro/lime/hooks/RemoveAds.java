package io.github.hiro.lime.hooks;

import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import java.lang.reflect.Method;
import io.github.hiro.lime.LimeModule;
import io.github.hiro.lime.LimeOptions;
import io.github.libxposed.api.XposedInterface;

public class RemoveAds implements IHook {
    @Override
    public void hook(LimeModule module, ClassLoader classLoader, LimeOptions limeOptions) throws Throwable {
        // 攔截廣告 Request
        Class<?> requestClass = classLoader.loadClass(Constants.REQUEST_HOOK.className);
        Method reqMethod = requestClass.getDeclaredMethod(Constants.REQUEST_HOOK.methodName, Object.class, Object.class);
        module.hook(reqMethod, new XposedInterface.Hooker() {
            @Override
            public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
                java.util.List<Object> args = chain.getArgs();
                if (args != null && args.size() > 0 && args.get(0) != null) {
                    String reqName = args.get(0).toString();
                    if (reqName.contains("Banners")) return null; // 阻斷請求
                }
                return chain.proceed();
            }
        });

        // 攔截 View 加入
        Method addView = ViewGroup.class.getDeclaredMethod("addView", View.class, ViewGroup.LayoutParams.class);
        module.hook(addView, new XposedInterface.Hooker() {
            @Override
            public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
                Object result = chain.proceed();
                java.util.List<Object> args = chain.getArgs();
                if (args.size() > 0 && args.get(0) instanceof View) {
                    View v = (View) args.get(0);
                    if (v.getClass().getName().contains("Ad")) v.setVisibility(View.GONE);
                }
                return result;
            }
        });
    }
}
