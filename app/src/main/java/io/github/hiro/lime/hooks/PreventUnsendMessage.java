package io.github.hiro.lime.hooks;

import androidx.annotation.NonNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;

import io.github.hiro.lime.hooks.Constants;
import io.github.hiro.lime.LimeModule;
import io.github.hiro.lime.LimeOptions;
import io.github.libxposed.api.XposedInterface;

public class PreventUnsendMessage implements IHook {

    @Override
    public void hook(LimeModule module, ClassLoader classLoader, LimeOptions limeOptions) throws Throwable {
        // if (!limeOptions.preventUnsendMessage.checked) return;

        try {
            Class responseClass = classLoader.loadClass(Constants.RESPONSE_HOOK.className);

            for (Method method : responseClass.getDeclaredMethods()) {
                if (method.getName().equals(Constants.RESPONSE_HOOK.methodName)) {

                    module.hook(method, new XposedInterface.Hooker() {
                        @Override
                        // 1. 【修正】void 改為 Object
                        public Object intercept(@NonNull XposedInterface.BeforeHookCallback callback) throws Throwable {
                            // 2. 【修正】先執行原始方法並保留結果
                            Object result = callback.callOriginal();

                            Object[] args = callback.getArgs();
                            // 嚴謹檢查
                            if (args == null || args.length < 2 || args[0] == null || args[1] == null) return result;

                            // 修正 log 呼叫方式 (Level, Tag, Message)
                            module.log(XposedInterface.LOG_LEVEL_INFO, "LIMEs", "Lime Probe: Hook triggered! arg[0] = " + args[0]);

                            if (!"sync".equals(args[0].toString())) return result;

                            try {
                                // 開始深層反射解析 Thrift 封包
                                Field wrapperField = args[1].getClass().getDeclaredField("a");
                                wrapperField.setAccessible(true); 
                                Object wrapper = wrapperField.get(args[1]);
                                if (wrapper == null) return result;

                                Field operationResponseField = wrapper.getClass().getSuperclass().getDeclaredField("value_");
                                operationResponseField.setAccessible(true);
                                Object operationResponse = operationResponseField.get(wrapper);
                                if (operationResponse == null) return result;

                                Field operationsField = operationResponse.getClass().getDeclaredField("a");
                                operationsField.setAccessible(true); 
                                ArrayList operations = (ArrayList) operationsField.get(operationResponse);
                                if (operations == null) return result;

                                for (Object operation : operations) {
                                    Field typeField = operation.getClass().getDeclaredField("c");
                                    typeField.setAccessible(true);
                                    Object type = typeField.get(operation);
                                    if (type == null) continue;

                                    if ("NOTIFIED_DESTROY_MESSAGE".equals(type.toString())) {
                                        // 竄改指令為 DUMMY
                                        typeField.set(operation, type.getClass().getMethod("valueOf", String.class).invoke(null, "DUMMY"));
                                    } else if ("RECEIVE_MESSAGE".equals(type.toString())) {
                                        Field messageField = operation.getClass().getDeclaredField("j");
                                        messageField.setAccessible(true);
                                        Object message = messageField.get(operation);
                                        if (message == null) continue;

                                        Field metadataField = message.getClass().getDeclaredField("k");
                                        metadataField.setAccessible(true);

                                        @SuppressWarnings("unchecked")
                                        Map<String, String> contentMetadata = (Map<String, String>) metadataField.get(message);
                                        if (contentMetadata != null) {
                                            contentMetadata.remove("UNSENT");
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                module.log(XposedInterface.LOG_LEVEL_ERROR, "LIMEs", "PreventUnsend Error: " + android.util.Log.getStackTraceString(e));
                            }
                            
                            // 3. 【修正】返回原始執行結果
                            return result;
                        }
                    });
                }
            }
        } catch (ClassNotFoundException e) {
            module.log(XposedInterface.LOG_LEVEL_ERROR, "LIMEs", "PreventUnsendMessage 找不到 Response Class");
        }
    }
}
