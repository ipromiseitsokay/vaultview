# The hidden game page calls back into the app through this (download capture).
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
-keepattributes JavascriptInterface

# 7z unpacking: keep the codecs intact.
-keep class org.apache.commons.compress.archivers.sevenz.** { *; }
-keep class org.tukaani.xz.** { *; }

# Optional pieces of libraries the app doesn't use.
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.commons.io.**
-dontwarn org.apache.commons.lang3.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**
-dontwarn org.osgi.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.google.re2j.**
