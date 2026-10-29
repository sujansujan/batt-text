# Keep Room entities, DAOs, and databases
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature
-keep class com.batttext.app.data.db.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# Keep DataStore preferences & protobuf classes safe from R8 stripping
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}
-keep class rikka.shizuku.** { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.coroutines.**
-dontwarn rikka.shizuku.**
