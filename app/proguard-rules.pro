# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep modern LSPosed API entry class name referenced by META-INF/xposed/java_init.list.
-keep class com.mi.onextbox.lsp.OosLspModuleEntry { *; }

# KavaRef references this Java 8 reflection API on newer runtimes; Android's R8
# classpath does not provide it for the configured minSdk.
-dontwarn java.lang.reflect.AnnotatedType
-keep class com.mi.onextbox.tools.SurfaceFlingerModeTool { *; }
