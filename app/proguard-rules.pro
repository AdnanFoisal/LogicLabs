# Logic Labs release shrinking rules.
#
# The app ships R8 full mode (per the project rules in GEMINI.md). Compose, Navigation
# and DataStore all carry their own consumer rules; the only project-specific surface
# that needs help is kotlinx-serialization, which looks up generated serializers
# reflectively at first (de)serialization of each type.

# --- kotlinx-serialization ---------------------------------------------------
# Keep the generated serializers for the project's own @Serializable models, and the
# Companion.serializer() entry points R8 cannot see through reflection.
-keep,includedescriptorclasses class com.logiclabs.core.**$$serializer { *; }
-keepclassmembers class com.logiclabs.core.** {
    *** Companion;
}
-keepclasseswithmembers class com.logiclabs.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Line numbers for crash triage, without leaking source in stack traces ------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
