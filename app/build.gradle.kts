import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---------------------------------------------------------------------------
// Реклама: значения берутся из gradle.properties (раздел «Реклама Яндекс РСЯ»).
// ---------------------------------------------------------------------------
fun adProperty(key: String, default: String): String {
    val raw = project.findProperty(key) as? String
    return if (raw.isNullOrBlank()) default else raw.trim()
}

val adsTestMode: Boolean = adProperty("ads.testMode", "true").toBoolean()
val adsBannerUnitId: String = adProperty("ads.bannerUnitId", "demo-banner-yandex")
val adsInterstitialUnitId: String = adProperty("ads.interstitialUnitId", "demo-interstitial-yandex")
val adsRewardedUnitId: String = adProperty("ads.rewardedUnitId", "demo-rewarded-yandex")
val adsInterstitialEveryNth: Int = adProperty("ads.interstitialEveryNthConversion", "3").toIntOrNull() ?: 3
val adsInterstitialMinInterval: Int = adProperty("ads.interstitialMinIntervalSeconds", "120").toIntOrNull() ?: 120
val adsRewardedNoAdsMinutes: Int = adProperty("ads.rewardedNoAdsMinutes", "30").toIntOrNull() ?: 30

// Релиз с ads.testMode=false и демо- или опечатанными ID = показы без дохода.
// Поэтому сборка падает сразу, а не через месяц в кабинете РСЯ.
if (!adsTestMode) {
    val realUnitId = Regex("^R-M-\\d{4,}-\\d{1,3}$")
    val unitIds = listOf(
        "ads.bannerUnitId" to adsBannerUnitId,
        "ads.interstitialUnitId" to adsInterstitialUnitId,
        "ads.rewardedUnitId" to adsRewardedUnitId,
    )
    val broken = unitIds.filterNot { realUnitId.matches(it.second) }
    require(broken.isEmpty()) {
        "ads.testMode=false, но ID блоков не похожи на реальные (нужен вид R-M-XXXXXXX-Y): " +
            broken.joinToString { "${it.first}=${it.second}" } +
            ". Возьмите ID из кабинета РСЯ: partner.yandex.ru → Реклама в приложениях → Приложения."
    }
}

// ---------------------------------------------------------------------------
// Версия приложения. По умолчанию 1 / «1.0.0».
// Перед загрузкой в магазин версию можно поднять, не трогая код:
//   gradlew.bat assembleRelease -PversionCode=2 -PversionName=1.0.1
//   или переменными окружения VERSION_CODE / VERSION_NAME (так делает CI —
//   в форме запуска воркфлоу есть поля versionCode и versionName, см. docs/CI_BUILD.md).
// ---------------------------------------------------------------------------
val versionCodeOverride: Int? =
    (System.getenv("VERSION_CODE") ?: (project.findProperty("versionCode") as? String))
        ?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()
val versionNameOverride: String? =
    (System.getenv("VERSION_NAME") ?: (project.findProperty("versionName") as? String))
        ?.trim()?.takeIf { it.isNotEmpty() }

val appVersionCode: Int = versionCodeOverride ?: 1
val appVersionName: String = versionNameOverride ?: "1.0.0"

if (versionCodeOverride != null || versionNameOverride != null) {
    logger.lifecycle("Версия переопределена: versionCode=$appVersionCode, versionName=$appVersionName")
}

// ---------------------------------------------------------------------------
// Подпись релиза. Секреты приходят из переменных окружения (CI) или из shell.
// Если секретов нет — сборка проходит, но AAB остаётся неподписанным.
// ---------------------------------------------------------------------------
val keystoreFilePath: String? = System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
val keystorePassword: String? = System.getenv("KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
val keyAlias: String? = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() }
val keyPassword: String? = System.getenv("KEY_PASSWORD")?.takeIf { it.isNotBlank() }
val hasSigningSecrets: Boolean = keystoreFilePath != null && keystorePassword != null &&
    keyAlias != null && keyPassword != null

android {
    namespace = "com.converterplus.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.converterplus.app"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        resourceConfigurations += "ru"

        buildConfigField("boolean", "ADS_TEST_MODE", adsTestMode.toString())
        buildConfigField("String", "ADS_BANNER_UNIT_ID", "\"$adsBannerUnitId\"")
        buildConfigField("String", "ADS_INTERSTITIAL_UNIT_ID", "\"$adsInterstitialUnitId\"")
        buildConfigField("String", "ADS_REWARDED_UNIT_ID", "\"$adsRewardedUnitId\"")
        buildConfigField("int", "ADS_INTERSTITIAL_EVERY_NTH_CONVERSION", adsInterstitialEveryNth.toString())
        buildConfigField("int", "ADS_INTERSTITIAL_MIN_INTERVAL_SECONDS", adsInterstitialMinInterval.toString())
        buildConfigField("int", "ADS_REWARDED_NO_ADS_MINUTES", adsRewardedNoAdsMinutes.toString())
    }

    signingConfigs {
        create("release") {
            if (hasSigningSecrets) {
                // Именно rootProject.file(): путь задаётся от корня репозитория, иначе CI падает.
                storeFile = rootProject.file(keystoreFilePath!!)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasSigningSecrets) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "AAB не подписан, для магазина нужны секреты: " +
                        "KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD",
                )
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
            )
        }
    }

    lint {
        abortOnError = false
        warningsAsErrors = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.04.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    // AppCompat нужен, в том числе, как транзитивная зависимость SDK Яндекс Мобил Ads.
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.navigation:navigation-compose:2.8.9")

    // Фоновая конвертация
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Файлы: SAF-папка, EXIF-ориентация
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.exifinterface:exifinterface:1.4.1")

    // Реклама Яндекс РСЯ (баннер, межстраничная, за вознаграждение)
    implementation("com.yandex.android:mobileads:8.6.0")

    // Видео: перекодирование в MP4 и извлечение звука (этап 2)
    implementation("androidx.media3:media3-transformer:1.11.1")
    implementation("androidx.media3:media3-common:1.11.1")

    // Распознавание текста (этап 4). Библиотека распространяется через JitPack — см. settings.gradle.kts
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
