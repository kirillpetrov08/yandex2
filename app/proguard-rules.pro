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

# --- Tesseract4Android (этап 4 — OCR) ---
-keep class net.sourceforge.tesstwo.** { *; }
-dontwarn net.sourceforge.tesstwo.**
