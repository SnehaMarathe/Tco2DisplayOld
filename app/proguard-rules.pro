# Keep Retrofit/Gson models safe (optional for this app, since we don't obfuscate)
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
