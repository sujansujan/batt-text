# Add project specific ProGuard rules here.
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature
-keep class com.batttext.app.data.db.** { *; }
-dontwarn kotlin.**
