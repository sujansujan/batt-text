# Keep Room entities, DAOs, and databases
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature
-keep class com.batttext.app.data.db.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# Keep Shizuku IPC classes
-keep class rikka.shizuku.** { *; }

-dontwarn kotlin.**
-dontwarn kotlinx.coroutines.**
-dontwarn rikka.shizuku.**
