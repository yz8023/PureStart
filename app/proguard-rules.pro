# PureStart R8 规则（v15.2 第三阶段：从「整包保留」收窄到「只保留入口」）
#
# 旧规则是 `-keep class com.yz8023.purestart.** { *; }`：它把本模块 100% 的类、方法、
# 字段名全部锁死，R8 只能删「完全没人引用」的代码，混淆与成员收缩全部失效。
#
# 现在只保留**确实需要按名字存在**的东西，其余允许收缩/混淆：
# 本模块内部的调用都是编译期静态引用，反射目标只有 Android 框架类与目标 App 的类
# （见 XiaoOkHook / UniversalAdHook / DexFinder），不存在「按类名反射自己的类」的情况——
# 这一条由 `tests/check_release_config.py` 静态守门（源码里一旦出现
# `Class.forName("com.yz8023.purestart…")` 就直接失败）。

# ---------------------------------------------------------------------------
# 1) Xposed 入口：必须按类名保留
# ---------------------------------------------------------------------------
# libxposed 在**目标应用进程**里按 `xposed/java_init.list` 的类名加载入口类，
# 并调用它从 api.jar 继承的生命周期方法。api.jar 是 compileOnly（不在 R8 库路径上，
# 见下面的 -dontwarn），所以 R8 无法自行推断「这些 @Override 方法必须保留原名」——
# 一旦改名，模块在目标进程里加载失败，而且是静默失败（日志里只剩 NoClassDefFoundError）。
-keep class com.yz8023.purestart.Main { *; }

# ---------------------------------------------------------------------------
# 2) 清单里声明的组件：框架按类名实例化
# ---------------------------------------------------------------------------
# AGP 通常会自动为清单组件生成 keep 规则，这里显式再写一遍：写错的代价是设置页
# 或图标恢复功能启动即崩，而不是体积变大一丁点。
-keep class com.yz8023.purestart.SettingsActivity { *; }
-keep class com.yz8023.purestart.HookStateProvider { *; }
-keep class com.yz8023.purestart.IconControlReceiver { *; }
# BuildInfo 只有静态字段，保留名字便于在 LSPosed 日志/自检里对照构建时间
-keep class com.yz8023.purestart.BuildInfo { *; }

# ---------------------------------------------------------------------------
# 3) 其余类：允许收缩 + 混淆
# ---------------------------------------------------------------------------
# 属性保留：注解/内部类是 Compose 与 libxposed 回调需要的最小集合。
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault
# libxposed API 由宿主在运行期提供，编译期只有 api.jar：
-dontwarn io.github.libxposed.api.**

# 保留行号便于在 LSPosed 日志里定位崩溃（体积代价 <2KB），但抹掉源文件名。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 低内存主机上 R8 的优化阶段容易 OOM（本项目在 1C/1G 构建机上验证过）；只做
# 收缩 + 混淆已经能拿到绝大部分体积收益。若有富余内存，可试着删掉这一行再对比产物。
-dontoptimize
