import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.MessageDigest

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

/**
 * Печатает аннотацию GitHub Actions. Строка вида `::error title=…::текст` попадает
 * в шапку прогона и в API прогона, поэтому причину падения видно даже там, где лог
 * шага недоступен. Спецсимволы экранируются, как требует формат workflow-команд.
 */
fun ghAnnotation(kind: String, title: String, message: String) {
    val safeTitle = title.replace("%", "%25").replace(",", "%2C").replace(":", "%3A")
    val safeMessage = message.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    println("::$kind title=$safeTitle::$safeMessage")
}

/** Дописывает текст в раздел Summary прогона, если он доступен (в CI — да). */
fun appendStepSummary(text: String) {
    val path = System.getenv("GITHUB_STEP_SUMMARY")?.takeIf { it.isNotBlank() } ?: return
    try {
        File(path).appendText(text)
    } catch (e: IOException) {
        project.logger.warn("Не удалось дописать Summary ($path): ${e.message}")
    }
}

/** Открывает хранилище ключей, перебирая типы: заданный, затем PKCS12 и JKS. */
fun loadKeyStore(file: File, password: CharArray, preferredType: String?): Pair<KeyStore?, Exception?> {
    var lastError: Exception? = null
    for (type in listOfNotNull(preferredType, "PKCS12", "JKS").distinct()) {
        try {
            val store = KeyStore.getInstance(type)
            file.inputStream().use { stream -> store.load(stream, password) }
            return store to lastError
        } catch (e: IOException) {
            lastError = e
        } catch (e: GeneralSecurityException) {
            lastError = e
        }
    }
    return null to lastError
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
// Если секретов нет — сборка проходит, но APK и AAB остаются неподписанными.
//
// Имена локальных переменных НАМЕРЕННО отличаются от свойств SigningConfig
// (keyAlias, keyPassword). Внутри `create("release") { ... }` правая часть
// выражения `keyAlias = keyAlias` разрешается в свойство самого SigningConfig,
// а не в переменную скрипта: получается присваивание null, и упаковка APK падает
// с «SigningConfig "release" is missing required property "keyPassword"».
// Поэтому: signingKeyAlias, signingKeyPassword и проверка ниже.
// ---------------------------------------------------------------------------
val keystoreFilePath: String? = System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
val keystorePassword: String? = System.getenv("KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
val signingKeyAlias: String? = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() }
val signingKeyPassword: String? = System.getenv("KEY_PASSWORD")?.takeIf { it.isNotBlank() }

/** Какие именно секреты не доехали — чтобы в логе было видно, что дописать. */
val missingSigningEnv: List<String> = listOfNotNull(
    "KEYSTORE_FILE".takeIf { keystoreFilePath == null },
    "KEYSTORE_PASSWORD".takeIf { keystorePassword == null },
    "KEY_ALIAS".takeIf { signingKeyAlias == null },
    "KEY_PASSWORD".takeIf { signingKeyPassword == null },
)
val hasSigningSecrets: Boolean = missingSigningEnv.isEmpty()

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
                this.keyAlias = signingKeyAlias
                this.keyPassword = signingKeyPassword
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
                    "APK и AAB будут без подписи: не заданы " + missingSigningEnv.joinToString(", ") +
                        ". В RuStore такой файл не примут — см. docs/CI_BUILD.md, шаг B.",
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

    // Проверяем, что четыре поля SigningConfig действительно заполнились: читаем их
    // обратно и падаем с понятным текстом. Без этой проверки опечатка в именах выше
    // доходит до AGP и падает уже на упаковке APK — «SigningConfig "release" is missing
    // required property "keyPassword"» — без указания на настоящую причину.
    if (hasSigningSecrets) {
        val releaseSigning = signingConfigs.getByName("release")
        val emptyFields = listOfNotNull(
            "storeFile (путь KEYSTORE_FILE)".takeIf { releaseSigning.storeFile == null },
            "storePassword (секрет KEYSTORE_PASSWORD)".takeIf { releaseSigning.storePassword.isNullOrEmpty() },
            "keyAlias (секрет KEY_ALIAS)".takeIf { releaseSigning.keyAlias.isNullOrEmpty() },
            "keyPassword (секрет KEY_PASSWORD)".takeIf { releaseSigning.keyPassword.isNullOrEmpty() },
        )
        if (emptyFields.isNotEmpty()) {
            throw GradleException(
                "Секреты подписи пришли не полностью — не заполнено: " + emptyFields.joinToString(", ") +
                    ". Проверьте имена секретов репозитория (docs/CI_BUILD.md, шаг B): " +
                    "KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS=sign, KEY_PASSWORD.",
            )
        }

        // Дальше проверяем сам файл ключа теми же значениями, что уйдут в AGP: открываем
        // хранилище, ищем алиас из секрета KEY_ALIAS, читаем приватный ключ паролем из
        // KEY_PASSWORD. Без этой проверки расхождение алиаса всплывает только на упаковке
        // APK, через несколько минут сборки: «No key with alias … found in keystore» — и по
        // нему не видно, какие алиасы в файле есть. Частый случай: ключ создан keytool без
        // «-alias sign» (там алиас по умолчанию mykey) или мастером Android Studio.
        // Каждая неудача печатает аннотацию ::error — она видна и в шапке прогона.
        val storeFile = releaseSigning.storeFile!!
        val (keyStore, storeProblem) = loadKeyStore(
            storeFile,
            releaseSigning.storePassword!!.toCharArray(),
            releaseSigning.storeType,
        )
        if (keyStore == null) {
            val message = "Не удалось открыть хранилище ключей «${storeFile.name}» ни как PKCS12, " +
                "ни как JKS: ${storeProblem?.message}. Проверьте секреты KEYSTORE_BASE64 " +
                "(файл ключа в base64) и KEYSTORE_PASSWORD — docs/CI_BUILD.md, шаг B."
            ghAnnotation("error", "Ключ подписи не открывается", message)
            throw GradleException(message, storeProblem)
        }

        val requiredAlias = releaseSigning.keyAlias!!
        val resolvedAlias: String = if (keyStore.containsAlias(requiredAlias)) {
            requiredAlias
        } else {
            val keyAliases = keyStore.aliases().toList().filter { keyStore.isKeyEntry(it) }.sorted()
            if (keyAliases.size == 1) {
                // В ключе ровно одна запись с приватным ключом — алиас однозначен, поэтому
                // не срываем релиз из-за расхождения с секретом: подписываем ею и говорим,
                // что поправить. Ярлык внутри файла на подпись не влияет: магазин сверяет
                // сертификат, а не имя записи.
                val only = keyAliases.first()
                val message = "Секрет KEY_ALIAS = «$requiredAlias», но в ключе такой записи нет. " +
                    "В файле ровно одна запись с ключом — «$only», подписываю ею. " +
                    "Чтобы убрать расхождение, поставьте KEY_ALIAS=$only " +
                    "(Settings → Secrets and variables → Actions → Update secret). " +
                    "Подробно: docs/CI_BUILD.md, шаг B1."
                ghAnnotation("warning", "KEY_ALIAS не совпал с ключом", message)
                appendStepSummary(
                    "\n### Ключ подписи\n\n" +
                        "- секрет `KEY_ALIAS`: `$requiredAlias` — такой записи в ключе нет\n" +
                        "- используется алиас: `$only`\n",
                )
                only
            } else {
                val allAliases = keyStore.aliases().toList().sorted()
                val available = (if (keyAliases.isNotEmpty()) keyAliases else allAliases)
                    .joinToString(", ")
                    .ifEmpty { "ни одного" }
                val message = "В ключе нет записи с алиасом «$requiredAlias» (секрет KEY_ALIAS), " +
                    "а записей с ключом в файле несколько: $available. Выберите нужную и впишите " +
                    "в секрет KEY_ALIAS (Settings → Secrets and variables → Actions → Update " +
                    "secret). Подробно: docs/CI_BUILD.md, шаг B1."
                ghAnnotation("error", "Алиас не найден в ключе", message)
                appendStepSummary(
                    "\n### Ключ подписи\n\n" +
                        "- алиас из секрета `KEY_ALIAS`: `$requiredAlias`\n" +
                        "- алиасы в файле: `$available`\n",
                )
                throw GradleException(message)
            }
        }

        // AGP подписывает тем алиасом, который стоит в signingConfig, поэтому фиксируем
        // выбранное значение обратно — иначе упаковка снова возьмёт несуществующий алиас.
        releaseSigning.keyAlias = resolvedAlias

        val privateKey = try {
            keyStore.getKey(resolvedAlias, releaseSigning.keyPassword!!.toCharArray())
        } catch (e: GeneralSecurityException) {
            val message = "Ключ «$resolvedAlias» в файле есть, но не открывается паролем из " +
                "секрета KEY_PASSWORD: ${e.message}. Для хранилища PKCS12 пароль ключа " +
                "совпадает с паролем хранилища — docs/CI_BUILD.md, шаг B."
            ghAnnotation("error", "Неверный пароль ключа", message)
            throw GradleException(message, e)
        }
        if (privateKey == null) {
            val message = "Запись «$resolvedAlias» не является приватным ключом (нет пары " +
                "ключ/сертификат), подписывать нечем. В секрет KEYSTORE_BASE64 нужно положить " +
                "хранилище с записью типа PrivateKeyEntry — docs/CI_BUILD.md, шаг B1."
            ghAnnotation("error", "В ключе нет приватного ключа", message)
            throw GradleException(message)
        }

        // Сертификат берём по фактически выбранному алиасу: если алиас пришёл из файла
        // (а не из секрета), запрос по значению секрета вернул бы null.
        val certificate = keyStore.getCertificate(resolvedAlias)
            ?: keyStore.getCertificateChain(resolvedAlias)?.firstOrNull()
        val fingerprint = certificate?.let { cert ->
            MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString(":") { byte ->
                "%02X".format(byte)
            }
        } ?: "хранилище не отдало сертификат — сверьте отпечаток в Summary прогона"
        ghAnnotation("notice", "Ключ подписи", "алиас «$resolvedAlias», SHA-256: $fingerprint")
        appendStepSummary(
            "\n### Ключ подписи\n\n" +
                "- алиас: `$resolvedAlias`\n" +
                "- SHA-256 сертификата: `$fingerprint`\n",
        )
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

// ---------------------------------------------------------------------------
// Готовый APK без секретов ключа. AGP называет неподписанный релизный APK
// «app-release-unsigned.apk», а workflow (шаг «Проверка релизного APK» и артефакт
// release-apk) ждёт «app-release.apk». Поэтому копируем файл под обычным именем:
// так артефакт с APK появляется и до настройки подписи (docs/CI_BUILD.md, шаг B),
// и сборку можно сразу поставить на телефон. Когда секреты KEYSTORE_* заданы,
// AGP создаёт подписанный app-release.apk сам, и этот шаг ничего не делает —
// неподписанного файла рядом уже нет.
// ---------------------------------------------------------------------------
val aliasUnsignedReleaseApk by tasks.registering {
    description = "Копирует app-release-unsigned.apk в app-release.apk, если подписи нет"
    group = "build"
    doLast {
        val dir = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        val unsigned = dir.resolve("app-release-unsigned.apk")
        val plain = dir.resolve("app-release.apk")
        if (unsigned.isFile && !plain.isFile) {
            unsigned.copyTo(plain, overwrite = true)
            logger.lifecycle(
                "Неподписанный релизный APK доступен как ${plain.name}: " +
                    "для проверки на телефоне годится, для RuStore нужен подписанный " +
                    "(секреты KEYSTORE_*, docs/CI_BUILD.md, шаг B).",
            )
        }
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(aliasUnsignedReleaseApk)
}
