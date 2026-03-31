package io.github.hiro.lime.hooks;

import android.content.Context;
import android.content.pm.PackageManager;
import io.github.hiro.lime.LimeModule;

public class Constants {
    public static String PACKAGE_NAME = "jp.naver.line.android";
    public static HookTarget USER_AGENT_HOOK = new HookTarget("qi1.c", "j");
    public static HookTarget WEBVIEW_CLIENT_HOOK = new HookTarget("VP0.k", "onPageFinished");
    public static HookTarget MUTE_MESSAGE_HOOK = new HookTarget("jh1.b", "I");
    public static HookTarget MARK_AS_READ_HOOK = new HookTarget("nP.d$d", "run");
    public static HookTarget Archive = new HookTarget("LB.W", "invokeSuspend");
    public static HookTarget REQUEST_HOOK = new HookTarget("org.apache.thrift.o", "b");
    public static HookTarget RESPONSE_HOOK = new HookTarget("org.apache.thrift.o", "a");
    public static HookTarget RemoveVoiceRecord_Hook_a = new HookTarget("q.j", "run");
    public static HookTarget ChatRestore = new HookTarget("", "onActivityResult");

    public static void initializeHooks(Context context, LimeModule module) {
        try {
            String versionName = context.getPackageManager().getPackageInfo(PACKAGE_NAME, 0).versionName;
            module.log(2, "LIMEs", "偵測到版本: " + versionName);

            if (versionName.equals("14.19.1")) {
                assignV14_19_1();
            } else if (versionName.equals("15.9.3")) {
                assignV15_9_3();
            }
            // ... 其他版本邏輯可依此類推
        } catch (Exception e) {
            module.log(4, "LIMEs", "初始化失敗: " + e.getMessage());
        }
    }

    private static void assignV14_19_1() {
        USER_AGENT_HOOK = new HookTarget("Wc1.c", "h");
        WEBVIEW_CLIENT_HOOK = new HookTarget("OK0.l", "onPageFinished");
        MUTE_MESSAGE_HOOK = new HookTarget("Ob1.b", "H");
        MARK_AS_READ_HOOK = new HookTarget("WM.c$d", "run");
        Archive = new HookTarget("sB.Q", "invokeSuspend");
        REQUEST_HOOK = new HookTarget("org.apache.thrift.l", "b");
        RESPONSE_HOOK = new HookTarget("org.apache.thrift.l", "a");
        ChatRestore = new HookTarget("androidx.fragment.app.r", "onActivityResult");
    }

    private static void assignV15_9_3() {
        USER_AGENT_HOOK = new HookTarget("Bk1.d", "j");
        WEBVIEW_CLIENT_HOOK = new HookTarget("ZT0.l", "onPageFinished");
        MUTE_MESSAGE_HOOK = new HookTarget("uj1.b", "I");
        MARK_AS_READ_HOOK = new HookTarget("dS.e$d", "run");
        Archive = new HookTarget("LD.Q", "invokeSuspend");
        REQUEST_HOOK = new HookTarget("org.apache.thrift.l", "b");
        RESPONSE_HOOK = new HookTarget("org.apache.thrift.l", "a");
        ChatRestore = new HookTarget("androidx.fragment.app.r", "onActivityResult");
    }

    public static class HookTarget {
        public String className, methodName;
        public HookTarget(String c, String m) { this.className = c; this.methodName = m; }
    }
}
