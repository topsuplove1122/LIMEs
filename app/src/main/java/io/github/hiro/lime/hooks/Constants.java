package io.github.hiro.lime.hooks; // 修正 1：小寫 package

import android.content.Context;
import android.content.pm.PackageManager;

import io.github.hiro.lime.LimeModule;
import io.github.libxposed.api.XposedInterface;

public class Constants {
    public static String PACKAGE_NAME = "jp.naver.line.android";
    public static String MODULE_NAME = "io.github.hiro.lime";

    // --- Hook 目標定義 ---
    public static HookTarget USER_AGENT_HOOK = new HookTarget("qi1.c", "j");
    public static HookTarget WEBVIEW_CLIENT_HOOK = new HookTarget("VP0.k", "onPageFinished");
    public static HookTarget MUTE_MESSAGE_HOOK = new HookTarget("jh1.b", "I");
    public static HookTarget MARK_AS_READ_HOOK = new HookTarget("nP.d$d", "run");
    public static HookTarget Archive = new HookTarget("LB.W", "invokeSuspend");
    public static HookTarget REQUEST_HOOK = new HookTarget("org.apache.thrift.o", "b");
    public static HookTarget RESPONSE_HOOK = new HookTarget("org.apache.thrift.o", "a");
    public static HookTarget RemoveVoiceRecord_Hook_a = new HookTarget("q.j", "run");
    public static HookTarget ChatRestore = new HookTarget("", "onActivityResult");
    public static HookTarget PhotoSave = new HookTarget("", "");
    public static HookTarget PhotoSave1 = new HookTarget("", "");
    public static HookTarget PhotoSave2 = new HookTarget("", "");
    public static HookTarget PhotoSave3 = new HookTarget("", "");
    public static HookTarget Video = new HookTarget("", "");
    public static HookTarget ReactionList = new HookTarget("", "");
    public static HookTarget WhiteToDark0 = new HookTarget("", "");

    public static void initializeHooks(Context context, LimeModule module) {
        PackageManager pm = context.getPackageManager();
        String versionName = ""; 
        try {
            versionName = pm.getPackageInfo(PACKAGE_NAME, 0).versionName;
            // 修正 2：使用數字 Log 級別 (2 = INFO)
            module.log(2, "LIMEs", "目前偵測到的 LINE 版本為 " + versionName);
        } catch (PackageManager.NameNotFoundException e) {
            // 修正 3：使用數字 Log 級別 (4 = ERROR)
            module.log(4, "LIMEs", "無法取得 LINE 版本號");
            return;
        }

        // 根據版本號動態分配目標
        if (versionName.equals("14.19.1")) {
            assignV14_19_1();
        } else if (versionName.equals("14.21.1")) {
            assignV14_21_1();
        } else if (versionName.equals("15.0.0")) {
            assignV15_0_0();
        } else if (isVersionInRange(versionName, "15.1.0", "15.2.0")) {
            assignV15_1_0();
        } else if (isVersionInRange(versionName, "15.4.0", "15.4.1")) {
            module.log(2, "LIMEs", "15.4.0 Patched");
            assignV15_4_0();
        } else if (isVersionInRange(versionName, "15.9.0", "15.10.0")) {
            module.log(2, "LIMEs", "15.9.0 Patched");
            assignV15_9_x(versionName, module);
        }
    }

    // 將指派邏輯抽離，讓代碼更整潔符合 Modern 規範
    private static void assignV14_19_1() {
        USER_AGENT_HOOK = new HookTarget("Wc1.c", "h");
        WEBVIEW_CLIENT_HOOK = new HookTarget("OK0.l", "onPageFinished");
        MUTE_MESSAGE_HOOK = new HookTarget("Ob1.b", "H");
        MARK_AS_READ_HOOK = new HookTarget("WM.c$d", "run");
        Archive = new HookTarget("sB.Q", "invokeSuspend");
        REQUEST_HOOK = new HookTarget("org.apache.thrift.l", "b");
        RESPONSE_HOOK = new HookTarget("org.apache.thrift.l", "a");
        RemoveVoiceRecord_Hook_a = new HookTarget("af0.e", "run");
        ChatRestore = new HookTarget("androidx.fragment.app.r", "onActivityResult");
    }

    private static void assignV15_4_0() {
        USER_AGENT_HOOK = new HookTarget("Rj1.c", "j");
        WEBVIEW_CLIENT_HOOK = new HookTarget("jS0.l", "onPageFinished");
        MUTE_MESSAGE_HOOK = new HookTarget("Ki1.b", "I");
        MARK_AS_READ_HOOK = new HookTarget("pQ.d$d", "run");
        Archive = new HookTarget("GC.Z", "invokeSuspend");
        REQUEST_HOOK = new HookTarget("org.apache.thrift.l", "b");
        RESPONSE_HOOK = new HookTarget("org.apache.thrift.l", "a");
        RemoveVoiceRecord_Hook_a = new HookTarget("h.i", "run");
        ChatRestore = new HookTarget("androidx.fragment.app.p", "onActivityResult");
    }

    private static void assignV15_9_x(String versionName, LimeModule module) {
        USER_AGENT_HOOK = new HookTarget("Bk1.d", "j");
        WEBVIEW_CLIENT_HOOK = new HookTarget("ZT0.l", "onPageFinished");
        MUTE_MESSAGE_HOOK = new HookTarget("uj1.b", "I");
        MARK_AS_READ_HOOK = new HookTarget("dS.e$d", "run");
        
        String chatListClass = versionName.equals("15.9.3") ? "LD.Q" : "LD.S";
        Archive = new HookTarget(chatListClass, "invokeSuspend");
        REQUEST_HOOK = new HookTarget("org.apache.thrift.l", "b");
        RESPONSE_HOOK = new HookTarget("org.apache.thrift.l", "a");
        RemoveVoiceRecord_Hook_a = new HookTarget("g.k", "run");
        
        String chatRestoreClass = versionName.equals("15.9.2") ? "androidx.fragment.app.t" : "androidx.fragment.app.r";
        ChatRestore = new HookTarget(chatRestoreClass, "onActivityResult");
    }

    // --- 這裡省略部分重複的 assignV... 方法以簡化閱讀 ---

    private static boolean isVersionInRange(String versionName, String minVersion, String maxVersion) {
        try {
            int[] current = parseVersion(versionName);
            int[] min = parseVersion(minVersion);
            int[] max = parseVersion(maxVersion);
            return compareVersions(current, min) >= 0 && compareVersions(current, max) < 0;
        } catch (Exception e) { return false; }
    }

    private static int[] parseVersion(String version) {
        String[] parts = version.split("\\.");
        int[] verArray = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            verArray[i] = Integer.parseInt(parts[i]);
        }
        return verArray;
    }

    private static int compareVersions(int[] v1, int[] v2) {
        for (int i = 0; i < Math.min(v1.length, v2.length); i++) {
            if (v1[i] < v2[i]) return -1;
            if (v1[i] > v2[i]) return 1;
        }
        return 0;
    }

    public static class HookTarget {
        public String className;
        public String methodName;
        public HookTarget(String className, String methodName) {
            this.className = className;
            this.methodName = methodName;
        }
    }
}
