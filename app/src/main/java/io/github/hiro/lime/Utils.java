package io.github.hiro.lime;

import android.content.Context;

public class Utils {
    
    // 保留：用於 MainActivity 中動態生成 UI 時計算邊距
    public static int dpToPx(int dp, Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }
    
    // addModuleAssetPath 已完全移除
}
