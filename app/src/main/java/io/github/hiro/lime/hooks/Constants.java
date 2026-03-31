package io.github.hiro.lime.hooks;

import android.content.Context;
import android.content.pm.PackageManager;

// 1. 導入 libxposed 的接口以使用 LOG 等級
import io.github.libxposed.api.XposedInterface;
import io.github.hiro.lime.LimeModule;

public class Constants {
    public static String PACKAGE_NAME = "jp.naver.line.android";
    public static String MODULE_NAME = "io.github.hiro.lime";

    // --- 以下 HookTarget 保持不變 ---
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
            // 2. 修正 log 呼叫方式 (Level, Tag, Message)
            module.log(XposedInterface.LOG_LEVEL_INFO, "LIMEs", "目前偵測到的 LINE 版本為 " + versionName);
        } catch (PackageManager.NameNotFoundException e) {
            module.log(XposedInterface.LOG_LEVEL_ERROR, "LIMEs", "無法取得 LINE 版本號");
            return;
        }

        // バージョンネームに応じてHookTargetを変更
        if (versionName.equals("14.19.1")) {
            USER_AGENT_HOOK = new HookTarget("Wc1.c", "h");
            WEBVIEW_CLIENT_HOOK = new HookTarget("OK0.l", "onPageFinished");
            MUTE_MESSAGE_HOOK = new HookTarget("Ob1.b", "H");
            MARK_AS_READ_HOOK = new HookTarget("WM.c$d", "run");
            Archive = new HookTarget("sB.Q", "invokeSuspend");
            REQUEST_HOOK = new HookTarget("org.apache.thrift.l", "b");
            RESPONSE_HOOK = new HookTarget("org.apache.thrift.l", "a");
            RemoveVoiceRecord_Hook_a = new HookTarget("af0.e", "run");
            ChatRestore = new HookTarget("androidx.fragment.app.r", "onActivityResult");
        } else if (versionName.equals("14.21.1")) {
            USER_AGENT_HOOK = new HookTarget("vf1.c", "j");
            WEBVIEW_CLIENT_HOOK = new HookTarget("pN0.l", "onPageFinished");
            MARK_AS_READ_HOOK = new HookTarget("xN.b$d", "run");
            MUTE_MESSAGE_HOOK = new HookTarget("ne1.b", "H");
            Archive = new HookTarget("tB.N", "invokeSuspend");
            REQUEST_HOOK = new HookTarget("org.apache.thrift.l", "b");
            RESPONSE_HOOK = new HookTarget("org.apache.thrift.l", "a");
            RemoveVoiceRecord_Hook_a = new HookTarget("q.j", "run");
            ChatRestore = new HookTarget("androidx.fragment.app.o", "onActivityResult");
        } else if (versionName.equals("15.0.0")) {
            USER_AGENT_HOOK = new HookTarget("Sg1.c", "j");
            WEBVIEW_CLIENT_HOOK = new HookTarget("FO0.l", "onPageFinished");
            MUTE_MESSAGE_HOOK = new HookTarget("Lf1.b", "I");
            MARK_AS_READ_HOOK = new HookTarget("KO.d$d", "run");
            Archive = new HookTarget("tB.P", "invokeSuspend");
            REQUEST_HOOK = new HookTarget("org.apache.thrift.l", "b");
            RESPONSE_HOOK = new HookTarget("org.apache.thrift.l", "a");
            RemoveVoiceRecord_Hook_a = new HookTarget("q.j", "run");
            ChatRestore = new HookTarget("androidx.fragment.app.o", "onActivityResult");
        } else if (isVersionInRange(versionName, "15.1.0", "15.2.0")) {
            USER_AGENT_HOOK = new HookTarget("qi1.c", "j");
            WEBVIEW_CLIENT_HOOK = new HookTarget("VP0.k", "onPageFinished");
            MUTE_MESSAGE_HOOK = new HookTarget("jh1.b", "I");
            MARK_AS_READ_HOOK = new HookTarget("nP.d$d", "run");
            Archive = new HookTarget("LB.U", "invokeSuspend");
            REQUEST_HOOK = new HookTarget("org.apache.thrift.n", "b");
            RESPONSE_HOOK = new HookTarget("org.apache.thrift.n", "a");
            RemoveVoiceRecord_Hook_a = new HookTarget("q.j", "run");
            ChatRestore = new HookTarget("androidx.fragment.app.o", "onActivityResult");
        } else if (isVersionInRange(versionName, "15.2.0", "15.2.1")) {
            USER_AGENT_HOOK = new HookTarget("Mi1.c", "j");
            WEBVIEW_CLIENT_HOOK = new HookTarget("sQ0.l", "onPageFinished");
            MUTE_MESSAGE_HOOK = new HookTarget("Fh1.b", "I");
            MARK_AS_READ_HOOK = new HookTarget("GP.e$d", "run");
            Archive = new HookTarget("aC.P", "invokeSuspend");
            REQUEST_HOOK = new HookTarget("org.apache.thrift.l", "b");
            RESPONSE_HOOK = new HookTarget("org.apache.thrift.l", "a");
            RemoveVoiceRecord_Hook_a = new HookTarget("q.j", "run");
            ChatRestore = new HookTarget("androidx.fragment.app.p", "onActivityResult");
        } else if (isVersionInRange(versionName, "15.3.0", "15.4.0")) {
            USER_AGENT_HOOK = new HookTarget("ek1.c", "j");
            WEBVIEW_CLIENT_HOOK = new HookTarget("CR0.m", "onPageFinished");
            MUTE_MESSAGE_HOOK = new HookTarget("Xi1.b", "I");
            MARK_AS_READ_HOOK = new HookTarget("aQ.c$d", "run");
            Archive = new HookTarget("tC.S", "invokeSuspend");
            REQUEST_HOOK = new HookTarget("org.apache.thrift.l", "b");
            RESPONSE_HOOK = new HookTarget("org.apache.thrift.l", "a");
            RemoveVoiceRecord_Hook_a = new HookTarget("q.j", "run");
            ChatRestore = new HookTarget("androidx.fragment.app.n", "onActivityResult");
            PhotoSave = new HookTarget("Dh1.p0", "");
            PhotoSave1 = new HookTarget("Ec1.U", "");
            PhotoSave2 = new HookTarget("XQ.g", "");
            PhotoSave3 = new HookTarget("lm.K$b", "");
        } else if (isVersionInRange(versionName, "15.4.0", "15.4.1")) {
            module.log(XposedInterface.LOG_LEVEL_INFO, "LIMEs", "15.4.0 Patched ");
            USER_AGENT_HOOK = new HookTarget("Rj1.c", "j");
            WEBVIEW_CLIENT_HOOK = new HookTarget("jS0.l", "onPageFinished");
            MUTE_MESSAGE_HOOK = new HookTarget("Ki1.b", "I");
            MARK_AS_READ_HOOK = new HookTarget("pQ.d$d", "run");
            Archive = new HookTarget("GC.Z", "invokeSuspend");
            REQUEST_HOOK = new HookTarget("org.apache.thrift.l", "b");
            RESPONSE_HOOK = new HookTarget("org.apache.thrift.l", "a");
            RemoveVoiceRecord_Hook_a = new HookTarget("h.i", "run");
            ChatRestore = new HookTarget("androidx.fragment.app.p", "onActivityResult");
            PhotoSave = new HookTarget("qh1.i0", "");
            PhotoSave1 = new HookTarget("rc1.C", "");
            PhotoSave2 = new HookTarget("mR.g", "");
            PhotoSave3 = new HookTarget("gm.J$b", "");
        } else if (isVersionInRange(versionName, "15.4.1", "15.5.0")) {
            module.log(XposedInterface.LOG_LEVEL_INFO, "LIMEs", "15.4.1 Patched ");
            USER_AGENT_HOOK = new HookTarget("Rj1.c", "j");
            WEBVIEW_CLIENT_HOOK = new HookTarget("jS0.l", "onPageFinished");
            MUTE_MESSAGE_HOOK = new HookTarget("Ki1.b", "I");
            MARK_AS_READ_HOOK = new HookTarget("pQ.c$d", "run");
            Archive = new HookTarget("GC.a0", "invokeSuspend");
            REQUEST_HOOK = new HookTarget("org.apache.thrift.l", "b");
            RESPONSE_HOOK = new HookTarget("org.apache.thrift.l", "a");
            RemoveVoiceRecord_Hook_a = new HookTarget("h.j", "run");
            ChatRestore = new HookTarget("androidx.fragment.app.n", "onActivityResult");
            PhotoSave = new HookTarget("qh1.k0", "");
            PhotoSave1 = new HookTarget("rc1.D", "");
            PhotoSave2 = new HookTarget("mR.g", "");
            PhotoSave3 = new HookTarget("gm.K", "");
            ReactionList = new HookTarget("Iy.l", "");
            WhiteToDark0 = new HookTarget("Xv0.m$b", "");
        } else if (isVersionInRange(versionName, "15.5.0", "15.6.0")) {
            module.log(XposedInterface.LOG_LEVEL_INFO, "LIMEs", "15.5.1-15.5.4 Patched ");
            USER_AGENT_HOOK = new HookTarget("ej1.c", "j");
            WEBVIEW_CLIENT_HOOK = new HookTarget("FS0.l", "onPageFinished");
            MUTE_MESSAGE_HOOK = new HookTarget("Xh1.b", "I");
            MARK_AS_READ_HOOK = new HookTarget("mQ.c$d", "run");
            Archive = new HookTarget("JC.Y", "invokeSuspend");
            REQUEST_HOOK = new HookTarget("org.apache.thrift.l", "b");
            RESPONSE_HOOK = new HookTarget("org.apache.thrift.l", "a");
            RemoveVoiceRecord_Hook_a = new HookTarget("h.j", "run");
            ChatRestore = new HookTarget("androidx.fragment.app.n", "onActivityResult");
            PhotoSave = new HookTarget("Cg1.s0", "");
            PhotoSave1 = new HookTarget("Ib1.L", "");
            PhotoSave2 = new HookTarget("jR.g", "");
            PhotoSave3 = new HookTarget("gm.y", "");
            ReactionList = new HookTarget("Ky.m", "");
            Video = new HookTarget("YP.I", "");
        } else if (isVersionInRange(versionName, "15.6.0", "15.7.0")) {
            module.log(XposedInterface.LOG_LEVEL_INFO, "LIMEs", "15.6.0 Patched ");
            USER_AGENT_HOOK = new HookTarget("xj1.c", "j");
            WEBVIEW_CLIENT_HOOK = new HookTarget("bT0.l", "onPageFinished");
            MUTE_MESSAGE_HOOK = new HookTarget("qi1.b", "I");
            MARK_AS_READ_HOOK = new HookTarget("vQ.c$d", "run");
            Archive = new HookTarget("JC.b0", "invokeSuspend");
            REQUEST_HOOK = new HookTarget("org.apache.thrift.m", "b");
            RESPONSE_HOOK = new HookTarget("org.apache.thrift.m", "a");
            RemoveVoiceRecord_Hook_a = new HookTarget("h.j", "run");
            ChatRestore = new HookTarget("androidx.fragment.app.m", "onActivityResult");
            PhotoSave = new HookTarget("Vg1.l0", "");
            PhotoSave1 = new HookTarget("hc1.C", "");
            PhotoSave2 = new HookTarget("sR.g", "");
            PhotoSave3 = new HookTarget("gm.A", "");
            Video = new HookTarget("hQ.J", "");
            ReactionList = new HookTarget("Iy.m", "");
        } else if (isVersionInRange(versionName, "15.7.0", "15.8.0")) {
            module.log(XposedInterface.LOG_LEVEL_INFO, "LIMEs", "15.7.0 Patched ");
            USER_AGENT_HOOK = new HookTarget("Si1.c", "j");
            WEBVIEW_CLIENT_HOOK = new HookTarget("CS0.m", "onPageFinished");
            MUTE_MESSAGE_HOOK = new HookTarget("Lh1.b", "I");
            MARK_AS_READ_HOOK = new HookTarget("dR.d$d", "run");
            Archive = new HookTarget("jD.S", "invokeSuspend");
            REQUEST_HOOK = new HookTarget("org.apache.thrift.l", "b");
            RESPONSE_HOOK = new HookTarget("org.apache.thrift.l", "a");
            RemoveVoiceRecord_Hook_a = new HookTarget("h.j", "run");
            ChatRestore = new HookTarget("androidx.fragment.app.n", "onActivityResult");
            PhotoSave = new HookTarget("rg1.n0", "");
            PhotoSave1 = new HookTarget("Db1.U", "");
            PhotoSave2 = new HookTarget("aS.g", "");
            PhotoSave3 = new HookTarget("zm.C", "");
            ReactionList = new HookTarget("iz.j", "");
            Video = new HookTarget("PQ.J", "");
        } else if (isVersionInRange(versionName, "15.9.0", "15.10.0")) {
            module.log(XposedInterface.LOG_LEVEL_INFO, "LIMEs", "15.9.0 Patched ");
            USER_AGENT_HOOK = new HookTarget("Bk1.d", "j");
            WEBVIEW_CLIENT_HOOK = new HookTarget("ZT0.l", "onPageFinished");
            MUTE_MESSAGE_HOOK = new HookTarget("uj1.b", "I");
            MARK_AS_READ_HOOK = new HookTarget("dS.e$d", "run");
            
            String ChatListClassName;
            if (versionName.equals("15.9.2")) {
                ChatListClassName = "LD.S";
            } else if (versionName.equals("15.9.3")) {
                ChatListClassName = "LD.Q";
                module.log(XposedInterface.LOG_LEVEL_INFO, "LIMEs", "15.9.3 Patched ");
            } else {
                return;
            }
            Archive = new HookTarget(ChatListClassName, "invokeSuspend");
            REQUEST_HOOK = new HookTarget("org.apache.thrift.l", "b");
            RESPONSE_HOOK = new HookTarget("org.apache.thrift.l", "a");
            RemoveVoiceRecord_Hook_a = new HookTarget("g.k", "run");
            PhotoSave = new HookTarget("ai1.p0", "");
            PhotoSave1 = new HookTarget("ld1.F", "");
            PhotoSave2 = new HookTarget("aT.g", "");
            PhotoSave3 = new HookTarget("Nm.L", "");
            ReactionList = new HookTarget("Jz.l", "");
            Video = new HookTarget("PR.I", "");
            
            String chatRestoreClassName;
            if (versionName.equals("15.9.2")) {
                chatRestoreClassName = "androidx.fragment.app.t";
            } else if (versionName.equals("15.9.3")) {
                chatRestoreClassName = "androidx.fragment.app.r";
                module.log(XposedInterface.LOG_LEVEL_INFO, "LIMEs", "15.9.3 Patched ");
            } else {
                chatRestoreClassName = "androidx.fragment.app.r";
                module.log(XposedInterface.LOG_LEVEL_INFO, "LIMEs", "15.9.0 Patched ");
            }
            ChatRestore = new HookTarget(chatRestoreClassName, "onActivityResult");
        }
    }

    // --- 以下輔助方法保持不變 ---
    private static boolean isVersionInRange(String versionName, String minVersion, String maxVersion) {
        try {
            int[] currentVersion = parseVersion(versionName);
            int[] minVersionArray = parseVersion(minVersion);
            int[] maxVersionArray = parseVersion(maxVersion);
            boolean isGreaterOrEqualMin = compareVersions(currentVersion, minVersionArray) >= 0;
            boolean isLessThanMax = compareVersions(currentVersion, maxVersionArray) < 0;
            return isGreaterOrEqualMin && isLessThanMax;
        } catch (Exception e) {
            return false;
        }
    }

    private static int[] parseVersion(String version) {
        String[] parts = version.split("\\.");
        int[] versionArray = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            versionArray[i] = Integer.parseInt(parts[i]);
        }
        return versionArray;
    }

    private static int compareVersions(int[] version1, int[] version2) {
        for (int i = 0; i < Math.min(version1.length, version2.length); i++) {
            if (version1[i] < version2[i]) return -1;
            if (version1[i] > version2[i]) return 1;
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
