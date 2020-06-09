package com.android.internal.util;

/**
 * Created by @author:rabi on 18-6-13.
 */

public class AngleHelper {

    static {
        System.loadLibrary("Jniangle");
    }

    // 初始化
    public static native void init();

    // 关闭
    public static native void close();

    // 调整0
    public static native void angle(int angle);

}
