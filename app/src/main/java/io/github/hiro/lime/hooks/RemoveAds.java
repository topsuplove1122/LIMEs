package io.github.hiro.lime.hooks;

import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import java.lang.reflect.Method;

// 替換舊版依賴
import io.github.hiro.lime.Constants;
import io.github.hiro.lime.LimeModule;
import io.github.hiro.lime.LimeOptions;
import io.github.libxposed.api.XposedInterface;

public class RemoveAds implements IHook {

    @Override
    public void hook(LimeModule module, ClassLoader classLoader, LimeOptions limeOptions) throws Throwable {
        
        // 1. 攔截廣告請求 (getBanners / getPrefetchableBanners)
        try {
            Class<?> requestClass = classLoader.loadClass(Constants.REQUEST_HOOK.className);
            for (Method method : requestClass.getDeclaredMethods()) {
                if (method.getName().equals(Constants.REQUEST_HOOK.methodName)) {
                    module.hook(method, new XposedInterface.Hooker() {
                        @Override
                        public void beforeInvoke(@NonNull XposedInterface.BeforeHookCallback callback) {
                            Object[] args = callback.getArgs();
                            if (args != null && args.length > 0 && args[0] != null) {
                                String request = args[0].toString();
                                if (request.equals("getBanners") || request.equals("getPrefetchableBanners")) {
                                    // 強制回傳 null，阻擋廣告請求
                                    callback.setResult(null);
                                }
                            }
                        }
                        @Override
                        public void afterInvoke(@NonNull XposedInterface.AfterHookCallback callback) {}
                    });
                }
            }
        } catch (Exception e) {
            module.log("RemoveAds (Request) Hook 失敗: " + e.getMessage());
        }

        // 2. 隱藏 SmartChannel (頂部廣告)
        try {
            Class<?> smartChannelClass = classLoader.loadClass("com.linecorp.line.admolin.smartch.v2.view.SmartChannelViewLayout");
            Method dispatchDrawMethod = smartChannelClass.getDeclaredMethod("dispatchDraw", Canvas.class);
            
            module.hook(dispatchDrawMethod, new XposedInterface.Hooker() {
                @Override
                public void beforeInvoke(@NonNull XposedInterface.BeforeHookCallback callback) {
                    View view = (View) callback.getThisObject();
                    if (view != null && view.getParent() instanceof View) {
                        ((View) view.getParent()).setVisibility(View.GONE);
                    }
                }
                @Override
                public void afterInvoke(@NonNull XposedInterface.AfterHookCallback callback) {}
            });
        } catch (Exception e) {
            module.log("RemoveAds (SmartChannel) Hook 失敗: " + e.getMessage());
        }

        // 3. 隱藏 LadAdView (貼文串/文章廣告)
        try {
            Class<?> ladAdViewClass = classLoader.loadClass("com.linecorp.line.ladsdk.ui.common.view.lifecycle.LadAdView");
            Method onAttachedMethod = ladAdViewClass.getDeclaredMethod("onAttachedToWindow");
            
            module.hook(onAttachedMethod, new XposedInterface.Hooker() {
                @Override
                public void beforeInvoke(@NonNull XposedInterface.BeforeHookCallback callback) {
                    View view = (View) callback.getThisObject();
                    if (view != null && view.getParent() != null && view.getParent().getParent() instanceof View) {
                        View grandParent = (View) view.getParent().getParent();
                        ViewGroup.LayoutParams layoutParams = grandParent.getLayoutParams();
                        if (layoutParams != null) {
                            layoutParams.height = 0;
                            grandParent.setLayoutParams(layoutParams);
                        }
                        grandParent.setVisibility(View.GONE);
                    }
                }
                @Override
                public void afterInvoke(@NonNull XposedInterface.AfterHookCallback callback) {}
            });
        } catch (Exception e) {
            module.log("RemoveAds (LadAdView) Hook 失敗: " + e.getMessage());
        }

        // 4. 動態攔截帶有 "Ad" 關鍵字的 View (ViewGroup.addView)
        try {
            Method addViewMethod = ViewGroup.class.getDeclaredMethod("addView", View.class, ViewGroup.LayoutParams.class);
            module.hook(addViewMethod, new XposedInterface.Hooker() {
                @Override
                public void beforeInvoke(@NonNull XposedInterface.BeforeHookCallback callback) {}

                @Override
                public void afterInvoke(@NonNull XposedInterface.AfterHookCallback callback) {
                    Object[] args = callback.getArgs();
                    if (args != null && args.length > 0 && args[0] instanceof View) {
                        View view = (View) args[0];
                        String className = view.getClass().getName();
                        if (className.contains("Ad")) {
                            view.setVisibility(View.GONE);
                        }
                    }
                }
            });
        } catch (Exception e) {
            module.log("RemoveAds (addView) Hook 失敗: " + e.getMessage());
        }

        // 5. WebView JS 注入去除網頁內廣告
        try {
            Class<?> webViewClientClass = classLoader.loadClass(Constants.WEBVIEW_CLIENT_HOOK.className);
            for (Method method : webViewClientClass.getDeclaredMethods()) {
                if (method.getName().equals(Constants.WEBVIEW_CLIENT_HOOK.methodName)) {
                    Class<?>[] paramTypes = method.getParameterTypes();
                    // 確保攔截的是有 (WebView, String) 參數的目標方法
                    if (paramTypes.length == 2 && paramTypes[0] == WebView.class && paramTypes[1] == String.class) {
                        module.hook(method, new XposedInterface.Hooker() {
                            @Override
                            public void beforeInvoke(@NonNull XposedInterface.BeforeHookCallback callback) {}

                            @Override
                            public void afterInvoke(@NonNull XposedInterface.AfterHookCallback callback) {
                                Object[] args = callback.getArgs();
                                if (args != null && args.length > 0 && args[0] instanceof WebView) {
                                    WebView webView = (WebView) args[0];
                                    webView.evaluateJavascript("(() => {\n" +
                                            "    const observer = new MutationObserver(mutations => {\n" +
                                            "        mutations.forEach(mutation => {\n" +
                                            "            mutation.addedNodes.forEach(node => {\n" +
                                            "                if (!node.querySelectorAll) return;\n" +
                                            "                node.querySelectorAll('.ad_wrap, .lc__ad_root, .lc__ad_element').forEach(ad => ad.remove());\n" +
                                            "            });\n" +
                                            "        });\n" +
                                            "    });\n" +
                                            "    const config = {\n" +
                                            "        childList: true,\n" +
                                            "        subtree: true\n" +
                                            "    };\n" +
                                            "    observer.observe(document.body, config);\n" +
                                            "})();", null);
                                }
                            }
                        });
                    }
                }
            }
        } catch (Exception e) {
            module.log("RemoveAds (WebView) Hook 失敗: " + e.getMessage());
        }
    }
}
