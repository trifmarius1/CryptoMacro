-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, Exceptions, RuntimeVisibleAnnotations, AnnotationDefault

# kotlinx.serialization generated serializers (models only — do not keep the whole app)
-keep,includedescriptorclasses class com.cryptomacro.app.domain.model.**$$serializer { *; }
-keepclassmembers class com.cryptomacro.app.domain.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.cryptomacro.app.domain.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class com.cryptomacro.app.domain.model.** { *; }

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.bouncycastle.**
-keep class androidx.security.crypto.** { *; }

# Glance / tiles keep constructors used by the system
-keep class com.cryptomacro.app.widget.** { *; }
-keep class com.cryptomacro.app.tile.** { *; }
-keep class com.cryptomacro.app.worker.** { *; }
