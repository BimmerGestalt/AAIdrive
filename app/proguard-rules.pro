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

# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn com.fasterxml.jackson.databind.deser.std.StdDeserializer
-dontwarn com.fasterxml.jackson.databind.ser.std.StdSerializer
-dontwarn com.google.errorprone.annotations.FormatMethod
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE

# And some more for Google Maps
-dontwarn com.google.appengine.api.urlfetch.**
-dontwarn org.joda.convert.FromString
-dontwarn org.joda.convert.ToString
-dontwarn org.slf4j.impl.StaticLoggerBinder

# And from Mapbox
-dontwarn com.google.auto.**
-dontwarn com.mapbox.maps.plugin.**

# And from Sentry
-dontwarn javax.**
