# R8 rules. Deliberately tiny: no reflection, no serialization, no third-party code.

# Keep line numbers useful in logcat from the (minified) debug build.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlin-stdlib ships annotations R8 does not need to resolve.
-dontwarn org.jetbrains.annotations.**

# Debug-only test hooks (declared in src/debug/AndroidManifest.xml).
-keep class com.ilyaas.einkclock.debug.** { *; }
