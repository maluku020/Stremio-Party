# Native bibliotheken (via JNI): niets weghalen of hernoemen
-keep class org.libtorrent4j.** { *; }
-keep class com.frostwire.** { *; }
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keep class org.jellyfin.** { *; }
-keepclasseswithmembernames class * { native <methods>; }

# OkHttp / Okio
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Overig
-dontwarn javax.annotation.**
-dontwarn org.slf4j.**
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
