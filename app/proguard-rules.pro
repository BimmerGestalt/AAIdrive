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
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# https://stackoverflow.com/questions/9651703/using-proguard-with-android-without-obfuscation
-dontobfuscate
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*,!code/allocation/variable

# randomly hardcoding things to make connected tests pass
-keep class com.google.maps.** { *; }
-keep class kotlin.collections.CollectionsKt
-keep class kotlin.collections.MapsKt
-keep class kotlin.collections.SetsKt
-keep class kotlin.coroutines.intrinsics.IntrinsicsKt
-keep class io.wax911.emojify.model.Emoji { *; }
-keep class me.hufman.androidautoidrive.** { *; }

# Needed only for ConnectedTests
-keep class androidx.drawerlayout.widget.** { boolean isDrawer*(int); }
-keep class kotlin.reflect.jvm.internal.** { *; }