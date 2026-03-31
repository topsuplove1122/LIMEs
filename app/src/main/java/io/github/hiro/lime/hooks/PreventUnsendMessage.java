package io.github.hiro.lime.hooks;

import androidx.annotation.NonNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;

// 替換舊版依賴
import io.github.hiro.lime.Constants;
import io.github.hiro.lime.LimeModule;
import io.github.hiro.lime.LimeOptions;
import io.github.libxposed.api.XposedInterface;

public class PreventUnsendMessage implements IHook {
    
    @Override
    public void hook(LimeModule module, ClassLoader classLoader, LimeOptions limeOptions) throws Throwable {
        // if (!limeOptions.preventUnsendMessage.checked) return;

        try {
            Class<?> responseClass = classLoader.loadClass(Constants.RESPONSE_HOOK.className);
            
            // 遍歷方法，取代舊版的 hookAllMethods
            for (Method method : responseClass.getDeclaredMethods()) {
                if (method.getName().equals(Constants.RESPONSE_HOOK.methodName)) {
                    
                    module.hook(method, new XposedInterface.Hooker() {
                        @Override
                        public void beforeInvoke(@NonNull XposedInterface.BeforeHookCallback callback) {}

                        @Override
                        public void afterInvoke(@NonNull XposedInterface.AfterHookCallback callback) {
                            Object[] args = callback.getArgs();
                            
                            // 加上嚴謹的 null 檢查
                            if (args == null || args.length < 2 || args[0] == null || args[1] == null) return;

                            // 🟢 裝上監視器：只要有網路封包進來，就把第一個參數印出來看看！
                            // 將 XposedBridge.log 替換為 module.log
                            module.log("Lime Probe: Hook triggered! arg[0] = " + args[0]);

                            if (!"sync".equals(args[0].toString())) return;
                            
                            try {
                                // 開始深層反射解析 Thrift 封包
                                Field wrapperField = args[1].getClass().getDeclaredField("a");
                                wrapperField.setAccessible(true); // 增加安全性
                                Object wrapper = wrapperField.get(args[1]);
                                if (wrapper == null) return;

                                Field operationResponseField = wrapper.getClass().getSuperclass().getDeclaredField("value_");
                                operationResponseField.setAccessible(true);
                                Object operationResponse = operationResponseField.get(wrapper);
                                if (operationResponse == null) return;

                                Field operationsField = operationResponse.getClass().getDeclaredField("a");
                                operationsField.setAccessible(true); // 增加安全性
                                ArrayList<?> operations = (ArrayList<?>) operationsField.get(operationResponse);
                                if (operations == null) return;

                                for (Object operation : operations) {
                                    Field typeField = operation.getClass().getDeclaredField("c");
                                    typeField.setAccessible(true);
                                    Object type = typeField.get(operation);
                                    if (type == null) continue;

                                    if ("NOTIFIED_DESTROY_MESSAGE".equals(type.toString())) {
                                        typeField.set(operation, type.getClass().getMethod("valueOf", String.class).invoke(operation, "DUMMY"));
                                    } else if ("RECEIVE_MESSAGE".equals(type.toString())) {
                                        Field messageField = operation.getClass().getDeclaredField("j");
                                        messageField.setAccessible(true); // 增加安全性
                                        Object message = messageField.get(operation);
                                        if (message == null) continue;
                                        
                                        Field metadataField = message.getClass().getDeclaredField("k");
                                        metadataField.setAccessible(true); // 增加安全性
                                        
                                        @SuppressWarnings("unchecked")
                                        Map<String, String> contentMetadata = (Map<String, String>) metadataField.get(message);
                                        if (contentMetadata != null) {
                                            contentMetadata.remove("UNSENT");
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                // 逼它把詳細的錯誤原因印到 Log 裡！
                                module.log("Lime PreventUnsend Error: " + android.util.Log.getStackTraceString(e));
                            }
                        }
                    });
                }
            }
        } catch (ClassNotFoundException e) {
            module.log("PreventUnsendMessage 找不到 Response Class");
        }
    }
}
