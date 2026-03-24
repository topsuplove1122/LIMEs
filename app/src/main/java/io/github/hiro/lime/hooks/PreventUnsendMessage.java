package io.github.hiro.lime.hooks;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.hiro.lime.LimeOptions;

public class PreventUnsendMessage implements IHook {
    @Override
    public void hook(LimeOptions limeOptions, XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        // if (!limeOptions.preventUnsendMessage.checked) return;

        XposedBridge.hookAllMethods(
                loadPackageParam.classLoader.loadClass(Constants.RESPONSE_HOOK.className),
                Constants.RESPONSE_HOOK.methodName,
                new XC_MethodHook() {
                                        @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        // 🟢 裝上監視器：只要有網路封包進來，就把第一個參數印出來看看！
                        XposedBridge.log("Lime Probe: Hook triggered! arg[0] = " + param.args[0]);

                        if (!"sync".equals(param.args[0].toString())) return;
                        
                        try {
                        // ... 後面維持原樣 ...

                            Object wrapper = param.args[1].getClass().getDeclaredField("a").get(param.args[1]);
                            Field operationResponseField = wrapper.getClass().getSuperclass().getDeclaredField("value_");
                            operationResponseField.setAccessible(true);
                            Object operationResponse = operationResponseField.get(wrapper);
                            if (operationResponse == null) return;

                            ArrayList<?> operations = (ArrayList<?>) operationResponse.getClass().getDeclaredField("a").get(operationResponse);
                            if (operations == null) return;

                            for (Object operation : operations) {
                                Field typeField = operation.getClass().getDeclaredField("c");
                                typeField.setAccessible(true);
                                Object type = typeField.get(operation);

                                if ("NOTIFIED_DESTROY_MESSAGE".equals(type.toString())) {
                                    typeField.set(operation, type.getClass().getMethod("valueOf", String.class).invoke(operation, "DUMMY"));
                                } else if ("RECEIVE_MESSAGE".equals(type.toString())) {
                                    Object message = operation.getClass().getDeclaredField("j").get(operation);
                                    if (message == null) continue;
                                    Map<String, String> contentMetadata = (Map<String, String>) message.getClass().getDeclaredField("k").get(message);
                                    if (contentMetadata != null) {
                                        contentMetadata.remove("UNSENT");
                                    }
                                }
                            }
                                                } catch (Exception e) {
                            // 逼它把詳細的錯誤原因印到 Log 裡！
                            XposedBridge.log("Lime PreventUnsend Error: " + android.util.Log.getStackTraceString(e));
                        }

                    }
                }
        );

    }
}
