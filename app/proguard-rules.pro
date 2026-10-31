# Xposed entry points and Hook internals contain reflective lookups. Preserve all
# app classes and members unchanged; R8 may shrink AndroidX/Compose dependencies.
-keep class com.yz8023.purestart.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault
# libxposed API is provided by the host at runtime and is intentionally compileOnly.
-dontwarn io.github.libxposed.api.**

# Reduce peak memory while preserving shrinking and name rules.
-dontoptimize
