# TrueWeb release obfuscation rules.
#
# Android/Compose/HMS dependencies provide their own consumer rules. The Xray
# Android AAR exposes JNI-backed classes through gomobile, so these namespaces
# must retain stable names and members across R8.

-keep class libv2ray.** { *; }
-keep class go.** { *; }

# Keep JNI/native methods and runtime annotations used by libraries.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod

# Preserve enum helpers used by JSON/preferences and reflective framework code.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
