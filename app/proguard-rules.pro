# CalmSonic release shrinking. Keep playback, Room, and JSON models intact.

-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

-keep class com.heavencanthandle.calmsonic.** { *; }
-keep class com.apple.** { *; }

-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keep class org.schabi.newpipe.** { *; }
-dontwarn org.schabi.newpipe.**
-dontwarn org.jsoup.**
-dontwarn org.mozilla.javascript.**

-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**

-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

-keep class retrofit2.** { *; }
-dontwarn retrofit2.**

# javacpp / jaudiotagger bring unused Maven/SLF4J compile-only types
-dontwarn org.apache.maven.plugins.annotations.LifecyclePhase
-dontwarn org.apache.maven.plugins.annotations.Mojo
-dontwarn org.slf4j.Logger
-dontwarn org.slf4j.LoggerFactory
-dontwarn org.bytedeco.**
-dontwarn org.apache.maven.**
-dontwarn org.slf4j.**
