# --- Яндекс Мобил Ads / Метрика ---
# Без этих правил release-сборка (R8) падает или реклама молча не показывается.
-keep class com.yandex.mobile.ads.** { *; }
-dontwarn com.yandex.mobile.ads.**
-keep class com.yandex.metrica.** { *; }
-dontwarn com.yandex.metrica.**

# --- WorkManager ---
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**
-keep class com.converterplus.app.work.ConversionWorker { <init>(...); }

# --- Compose / Kotlin ---
-keepattributes *Annotation*, InnerClasses, Signature
-dontwarn org.jetbrains.annotations.**

# --- Tesseract4Android (распознавание текста) ---
# Все вызовы идут через JNI: без этих правил R8 переименует классы и нативный движок не найдёт их.
-keep class com.googlecode.tesseract.android.** { *; }
-dontwarn com.googlecode.tesseract.android.**

# --- media3 Transformer (видео) ---
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
