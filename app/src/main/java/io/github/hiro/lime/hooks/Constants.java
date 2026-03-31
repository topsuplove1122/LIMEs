package io.github.hiro.lime.hooks;

import android.content.Context;
import io.github.hiro.lime.LimeModule;

public class Constants {
    public static String PACKAGE_NAME = "jp.naver.line.android";
    public static HookTarget USER_AGENT_HOOK = new HookTarget("qi1.c", "j");
    public static HookTarget REQUEST_HOOK = new HookTarget("org.apache.thrift.o", "b");
    public static HookTarget RESPONSE_HOOK = new HookTarget("org.apache.thrift.o", "a");
    // ... 其他變數保持不變 ...

    public static void initializeHooks(Context context, LimeModule module) {
        try {
            String versionName = context.getPackageManager().getPackageInfo(PACKAGE_NAME, 0).versionName;
            module.log(2, "LIMEs", "初始化版本: " + versionName);

            // 🛠️ 確保這裡呼叫的每一個方法下面都有定義
            if (versionName.equals("14.19.1")) {
                assignV14_19_1();
            } else if (versionName.equals("14.21.1")) {
                assignV14_21_1();
            } else if (versionName.equals("15.0.0")) {
                assignV15_0_0();
            } else if (versionName.startsWith("15.1")) {
                assignV15_1_0();
            }
        } catch (Exception e) {
            module.log(4, "LIMEs", "Constants 初始化失敗");
        }
    }

    // 🛠️ 補齊所有方法，避免 "cannot find symbol"
    private static void assignV14_19_1() {
        REQUEST_HOOK = new HookTarget("org.apache.thrift.l", "b");
        RESPONSE_HOOK = new HookTarget("org.apache.thrift.l", "a");
    }

    private static void assignV14_21_1() {
        REQUEST_HOOK = new HookTarget("org.apache.thrift.l", "b");
        RESPONSE_HOOK = new HookTarget("org.apache.thrift.l", "a");
    }

    private static void assignV15_0_0() {
        REQUEST_HOOK = new HookTarget("org.apache.thrift.l", "b");
        RESPONSE_HOOK = new HookTarget("org.apache.thrift.l", "a");
    }

    private static void assignV15_1_0() {
        REQUEST_HOOK = new HookTarget("org.apache.thrift.n", "b");
        RESPONSE_HOOK = new HookTarget("org.apache.thrift.n", "a");
    }

    public static class HookTarget {
        public String className, methodName;
        public HookTarget(String c, String m) { this.className = c; this.methodName = m; }
    }
}
