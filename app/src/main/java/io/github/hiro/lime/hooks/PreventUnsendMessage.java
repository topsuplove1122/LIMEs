package io.github.hiro.lime.hooks;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import io.github.hiro.lime.LimeModule;
import io.github.hiro.lime.LimeOptions;

public class PreventUnsendMessage implements IHook {
    @Override
    public void hook(LimeModule module, ClassLoader classLoader, LimeOptions limeOptions) throws Throwable {
        Class<?> responseClass = classLoader.loadClass(Constants.RESPONSE_HOOK.className);
        Method targetMethod = responseClass.getDeclaredMethod(Constants.RESPONSE_HOOK.methodName, Object.class, Object.class);

        // 🛠️ 使用 Lambda 寫法
        module.hook(targetMethod).intercept(chain -> {
            Object result = chain.proceed();
            List<Object> args = chain.getArgs();
            
            if (args != null && args.size() >= 2 && args.get(0) != null && "sync".equals(args.get(0).toString())) {
                try {
                    Field wrapperField = args.get(1).getClass().getDeclaredField("a");
                    wrapperField.setAccessible(true);
                    Object wrapper = wrapperField.get(args.get(1));
                    if (wrapper == null) return result;

                    Field valueField = wrapper.getClass().getSuperclass().getDeclaredField("value_");
                    valueField.setAccessible(true);
                    Object opResponse = valueField.get(wrapper);
                    
                    Field opsField = opResponse.getClass().getDeclaredField("a");
                    opsField.setAccessible(true);
                    ArrayList<?> operations = (ArrayList<?>) opsField.get(opResponse);

                    for (Object op : operations) {
                        Field typeField = op.getClass().getDeclaredField("c");
                        typeField.setAccessible(true);
                        if (typeField.get(op) != null && "NOTIFIED_DESTROY_MESSAGE".equals(typeField.get(op).toString())) {
                            typeField.set(op, typeField.get(op).getClass().getMethod("valueOf", String.class).invoke(null, "DUMMY"));
                            module.log(2, "LIMEs", "攔截收回指令成功");
                        }
                    }
                } catch (Exception ignored) {}
            }
            return result;
        });
    }
}
