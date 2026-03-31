package io.github.hiro.lime.hooks;

import android.content.Context;
import android.util.Log;
import io.github.hiro.lime.LimeModule;

public class Constants {
    public static String PACKAGE_NAME = "jp.naver.line.android";
    public static HookTarget USER_AGENT_HOOK = new HookTarget("qi1.c", "j");
    public static HookTarget REQUEST_HOOK = new HookTarget("org.apache.thrift.o", "b");
    public static HookTarget RESPONSE_HOOK = new HookTarget("org.apache.thrift.o", "a");
    public static HookTarget WEBVIEW_CLIENT_HOOK = new HookTarget("VP0.k", "onPageFinished");

    public static void initializeHooks(Context context, LimeModule module) {
        String versionName = "14.19.1"; // 🪂 預設降落傘，防止 Context 抓不到時出錯
        try {
            if (context != null) {
                versionName = context.getPackageManager().getPackageInfo(PACKAGE_NAME, 0).versionName;
            }
            Log.e("LIMEs", "LINE 目標版本: " + versionName);

            if (versionName.equals("14.19.1")) {
                REQUEST_HOOK = new HookTarget("org.apache.thrift.l", "b");
                RESPONSE_HOOK = new HookTarget("org.apache.thrift.l", "a");
            } else if (versionName.equals("15.9.3")) {
                RESPONSE_HOOK = new HookTarget("org.apache.thrift.l", "a");
            }
        } catch (Exception e) {
            Log.e("LIMEs", "Constants 初始化錯誤: " + e.getMessage());
        }
    }

    public static class HookTarget {
        public String className, methodName;
        public HookTarget(String c, String m) { this.className = c; this.methodName = m; }
    }
}
