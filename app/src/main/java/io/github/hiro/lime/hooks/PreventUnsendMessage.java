package io.github.hiro.lime.hooks;

import androidx.annotation.NonNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;

import io.github.hiro.lime.LimeModule;
import io.github.hiro.lime.LimeOptions;
import io.github.libxposed.api.XposedInterface;

public class PreventUnsendMessage implements IHook {

    @Override
    public void hook(LimeModule module, ClassLoader classLoader, LimeOptions limeOptions) throws Throwable {
        // 1. 取得目標類別與方法 (基於 Constants 定義的 Thrift Hook 點)
        Class<?> responseClass = classLoader.loadClass(Constants.RESPONSE_HOOK.className);
        // 通常 Thrift 的 a 方法接收兩個 Object 參數
        Method targetMethod = responseClass.getDeclaredMethod(Constants.RESPONSE_HOOK.methodName, Object.class, Object.class);

        // 2. 使用 Modern API 的 Interceptor Chain 模型
        module.hook(targetMethod, new XposedInterface.Hooker() {
            @Override
            public Object intercept(@NonNull XposedInterface.Chain chain) throws Throwable {
                // 執行原方法並獲取回傳值 (等同於舊版的 callOriginal)
                Object result = chain.proceed();

                List<Object> args = chain.getArgs();
                // 嚴謹檢查：確保參數存在且第一個參數是 "sync" (LINE 同步訊息的特徵)
                if (args == null || args.length < 2 || args[0] == null || args[1] == null) return result;

                if (!"sync".equals(args[0].toString())) return result;

                try {
                    // --- 開始深層反射解析 Thrift 封包內容 ---
                    
                    // 取得 Response 封裝物件中的欄位 'a'
                    Field wrapperField = args[1].getClass().getDeclaredField("a");
                    wrapperField.setAccessible(true);
                    Object wrapper = wrapperField.get(args[1]);
                    if (wrapper == null) return result;

                    // 取得父類別中的 value_ 欄位 (這是 Thrift 實際存放 Response 的地方)
                    Field operationResponseField = wrapper.getClass().getSuperclass().getDeclaredField("value_");
                    operationResponseField.setAccessible(true);
                    Object operationResponse = operationResponseField.get(wrapper);
                    if (operationResponse == null) return result;

                    // 取得 operations 列表 (欄位 'a')
                    Field operationsField = operationResponse.getClass().getDeclaredField("a");
                    operationsField.setAccessible(true);
                    ArrayList<?> operations = (ArrayList<?>) operationsField.get(operationResponse);
                    if (operations == null) return result;

                    // 遍歷所有同步操作
                    for (Object operation : operations) {
                        // 取得操作類型 (欄位 'c')
                        Field typeField = operation.getClass().getDeclaredField("c");
                        typeField.setAccessible(true);
                        Object type = typeField.get(operation);
                        if (type == null) continue;

                        String typeName = type.toString();

                        // 核心：如果偵測到「收回訊息」指令 (NOTIFIED_DESTROY_MESSAGE)
                        if ("NOTIFIED_DESTROY_MESSAGE".equals(typeName)) {
                            // 將該指令竄改為 DUMMY (無效指令)，讓 LINE 忽略它
                            typeField.set(operation, type.getClass().getMethod("valueOf", String.class).invoke(null, "DUMMY"));
                            module.log("LIMEs: [PreventUnsend] 已成功將收回指令竄改為 DUMMY");
                            
                        } else if ("RECEIVE_MESSAGE".equals(typeName)) {
                            // 額外保險：如果訊息本身帶有 UNSENT 標記，也將其移除
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
                    // API 101 建議使用 PROTECTIVE 模式，這裡只記錄錯誤不讓 App 崩潰
                    module.log("LIMEs: [PreventUnsend] 反射解析錯誤: " + e.getMessage());
                }

                return result;
            }
        });
    }
}
