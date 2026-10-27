# WebRTC calls through JNI: keep the whole Java API.
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# kotlinx.serialization (models are decoded reflectively through generated serializers)
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**
-keepclassmembers class com.par.chat.** { *** Companion; }
-keepclasseswithmembers class com.par.chat.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.par.chat.**$$serializer { *; }

# OkHttp optional platforms
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Do not fail the build on warnings about optional classes of libraries.
-ignorewarnings
