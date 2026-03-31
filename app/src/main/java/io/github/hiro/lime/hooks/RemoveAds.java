package io.github.hiro.lime.hooks;

import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Method;
import java.util.List;
import io.github.hiro.lime.LimeModule;
import io.github.hiro.lime.LimeOptions;

public class RemoveAds implements IHook {
    @Override
    public void hook(LimeModule module, ClassLoader classLoader, LimeOptions limeOptions) throws Throwable {
        // 攔截廣告請求
        try {
            Class<?> requestClass = classLoader.loadClass(Constants.REQUEST_HOOK.className);
            Method reqMethod = requestClass.getDeclaredMethod(Constants.REQUEST_HOOK.methodName, Object.class, Object.class);
            
            module.hook(reqMethod).intercept(chain -> {
                List<Object> args = chain.getArgs();
                if (args != null && args.size() > 0 && args.get(0) != null) {
                    String reqName = args.get(0).toString();
                    if (reqName.contains("Banners")) return null; // 直接擋下廣告 Request
                }
                return chain.proceed();
            });
        } catch (Exception e) { module.log(4, "LIMEs", "RemoveAds (Request): " + e.getMessage()); }

        // 隱藏頂部廣告
        try {
            Class<?> smartChannelClass = classLoader.loadClass("com.linecorp.line.admolin.smartch.v2.view.SmartChannelViewLayout");
            Method dispatchDrawMethod = smartChannelClass.getDeclaredMethod("dispatchDraw", Canvas.class);
            
            module.hook(dispatchDrawMethod).intercept(chain -> {
                View view = (View) chain.getThisObject();
                if (view != null && view.getParent() instanceof View) {
                    ((View) view.getParent()).setVisibility(View.GONE);
                }
                return chain.proceed();
            });
        } catch (Exception e) { module.log(4, "LIMEs", "RemoveAds (SmartChannel): " + e.getMessage()); }

        // 隱藏貼文串或其他的 Ad View
        try {
            Method addView = ViewGroup.class.getDeclaredMethod("addView", View.class, ViewGroup.LayoutParams.class);
            
            module.hook(addView).intercept(chain -> {
                Object result = chain.proceed();
                List<Object> args = chain.getArgs();
                if (args != null && args.size() > 0 && args.get(0) instanceof View) {
                    View v = (View) args.get(0);
                    if (v.getClass().getName().contains("Ad")) v.setVisibility(View.GONE);
                }
                return result;
            });
        } catch (Exception e) { module.log(4, "LIMEs", "RemoveAds (addView): " + e.getMessage()); }
    }
}
