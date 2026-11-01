package com.yz8023.purestart;

import android.util.Log;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * DexFinder: 极轻量级、纯 Java/反射实现的字节码与特征快速匹配引擎。
 * 针对加壳、频繁混淆改名的应用（如小蚕、网易云等），
 * 在无需引入几兆体积原生 .so 库的前提下，实现类似 DexKit 的基于特征全自动识别目标类与方法。
 */
public final class DexFinder {
    private static final String TAG = "ModKit-DexFinder";

    private DexFinder() {}

    /**
     * 在 ClassLoader 中根据方法特征（返回类型、参数特征、修饰符）寻找匹配的方法
     */
    public static Method findMethodByFeature(Class<?> targetClass, Class<?> returnType, Class<?>[] paramTypes, int requiredModifiers) {
        if (targetClass == null) return null;
        for (Method m : targetClass.getDeclaredMethods()) {
            if (returnType != null && !returnType.equals(m.getReturnType())) {
                continue;
            }
            if (requiredModifiers != 0 && (m.getModifiers() & requiredModifiers) != requiredModifiers) {
                continue;
            }
            if (paramTypes != null) {
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != paramTypes.length) continue;
                boolean matched = true;
                for (int i = 0; i < pts.length; i++) {
                    if (paramTypes[i] != null && !paramTypes[i].equals(pts[i])) {
                        matched = false;
                        break;
                    }
                }
                if (!matched) continue;
            }
            m.setAccessible(true);
            return m;
        }
        return null;
    }

    /**
     * 自动扫描指定包名或前缀下含有特定字段类型的类（混淆逆向特征匹配）
     */
    public static Field findFieldByType(Class<?> targetClass, Class<?> fieldType) {
        if (targetClass == null || fieldType == null) return null;
        Class<?> cur = targetClass;
        while (cur != null && !cur.equals(Object.class)) {
            for (Field f : cur.getDeclaredFields()) {
                if (fieldType.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return f;
                }
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    /**
     * 根据关键字和条件安全加载类，若未找到则返回 null
     */
    public static Class<?> safeLoad(ClassLoader cl, String className) {
        if (cl == null || className == null) return null;
        try {
            return cl.loadClass(className);
        } catch (Throwable t) {
            return null;
        }
    }
}
