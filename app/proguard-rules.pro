# kotlinx.serialization ships its own R8 rules; keep our @Serializable models' companions.
-keepclassmembers @kotlinx.serialization.Serializable class com.suppprith.dms.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# The accessibility service, receivers and workers are referenced from the manifest or by name.
-keep class com.suppprith.dms.notify.UnreadWorker { <init>(...); }
