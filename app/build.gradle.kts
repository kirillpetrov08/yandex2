import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.Certificate

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun adProperty(key: String, default: String): String {
    val raw = project.findProperty(key) as? String
    return if (raw.isNullOrBlank()) default else raw.trim()
}

fun ghAnnotation(kind: String, title: String, message: String) {
    val safeTitle = title.replace("%", "%25").replace(",", "%2C").replace(":", "%3A")
    val safeMessage = message.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    println("::$kind title=$safeTitle::$safeMessage")
}

fun appendStepSummary(text: String) {
    val path = System.getenv("GITHUB_STEP_SUMMARY")?.takeIf { it.isNotBlank() } ?: return
    try { File(path).appendText(text) } catch (e: IOException) {
        project.logger.warn("Не удалось дописать Summary ($path): ${e.message}")
    }
}

fun findApksigner(): File? {
    val sdkRoot = System.getenv("ANDROID_SDK_ROOT") ?: System.getenv("ANDROID_HOME") ?: return null
    val versions = File(sdkRoot, "build-tools").listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.asReversed() ?: return null
    for (v in versions) for (n in listOf("apksigner", "apksigner.bat")) {
        val c = File(v, n); if (c.isFile) return c
    }
    return null
}

fun runApksigner(apksigner: File, apk: File): Pair<Int, String> {
    val p = ProcessBuilder(apksigner.absolutePath, "verify", "--print-certs", apk.absolutePath).redirectErrorStream(true).start()
    val o = p.inputStream.bufferedReader().use { it.readText() }
    return p.waitFor() to o
}

fun certificateSha256(certificate: Certificate?): String? {
    if (certificate == null) return null
    return MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString(":") { "%02X".format(it) }
}

fun loadKeyStore(file: File, password: CharArray, preferredType: String?): Pair<KeyStore?, Exception?> {
    var last: Exception? = null
    for (type in listOfNotNull(preferredType, "PKCS12", "JKS").distinct()) {
        try {
            val ks = KeyStore.getInstance(type); file.inputStream().use { ks.load(it, password) }; return ks to last
        } catch (e: IOException) { last = e } catch (e: GeneralSecurityException) { last = e }
    }
    return null to last
}

val adsTestMode: Boolean = adProperty("ads.testMode", "true").toBoolean()
val adsBannerUnitId: String = adProperty("ads.bannerUnitId", "demo-banner-yandex")
val adsInterstitialUnitId: String = adProperty("ads.interstitialUnitId", "demo-interstitial-yandex")
val adsRewardedUnitId: String = adProperty("ads.rewardedUnitId", "demo-rewarded-yandex")
val adsInterstitialEveryNth: Int = adProperty("ads.interstitialEveryNthConversion", "3").toIntOrNull() ?: 3
val adsInterstitialMinInterval: Int = adProperty("ads.interstitialMinIntervalSeconds", "120").toIntOrNull() ?: 120
val adsRewardedNoAdsMinutes: Int = adProperty("ads.rewardedNoAdsMinutes", "30").toIntOrNull() ?: 30

if (!adsTestMode) {
    val realUnitId = Regex("^R-M-\\d{4,}-\\d{1,3}$")
    val unitIds = listOf("ads.bannerUnitId" to adsBannerUnitId, "ads.interstitialUnitId" to adsInterstitialUnitId, "ads.rewardedUnitId" to adsRewardedUnitId)
    val broken = unitIds.filterNot { realUnitId.matches(it.second) }
    require(broken.isEmpty()) {
        "ads.testMode=false, но ID блоков не похожи на реальные (нужен вид R-M-XXXXXXX-Y): " + broken.joinToString { "${it.first}=${it.second}" } + ". Возьмите ID из кабинета РСЯ: partner.yandex.ru → Реклама в приложениях → Приложения."
    }
}

val versionCodeOverride: Int? = (System.getenv("VERSION_CODE") ?: (project.findProperty("versionCode") as? String))?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()
val versionNameOverride: String? = (System.getenv("VERSION_NAME") ?: (project.findProperty("versionName") as? String))?.trim()?.takeIf { it.isNotEmpty() }

val appVersionCode: Int = versionCodeOverride ?: 1
val appVersionName: String = versionNameOverride ?: "1.0.0"

if (versionCodeOverride != null || versionNameOverride != null) logger.lifecycle("Версия переопределена: versionCode=$appVersionCode, versionName=$appVersionName")

val keystoreFilePath: String? = System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
val keystorePassword: String? = System.getenv("KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
val signingKeyAlias: String? = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() }
val signingKeyPassword: String? = System.getenv("KEY_PASSWORD")?.takeIf { it.isNotBlank() }

val missingSigningEnv: List<String> = listOfNotNull(
    "KEYSTORE_FILE".takeIf { keystoreFilePath == null },
    "KEYSTORE_PASSWORD".takeIf { keystorePassword == null },
    "KEY_ALIAS".takeIf { signingKeyAlias == null },
    "KEY_PASSWORD".takeIf { signingKeyPassword == null },
)
val hasSigningSecrets: Boolean = missingSigningEnv.isEmpty()

android {
    namespace = "com.copilka.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.copilka.app"
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
                storeFile = rootProject.file(keystoreFilePath!!)
                storePassword = keystorePassword
                this.keyAlias = signingKeyAlias
                this.keyPassword = signingKeyPassword
            }
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; isMinifyEnabled = false }
        release {
            isMinifyEnabled = true; isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigningSecrets) signingConfig = signingConfigs.getByName("release")
            else logger.warn("APK и AAB будут без подписи: не заданы " + missingSigningEnv.joinToString(", ") + ". В RuStore такой файл не примут — см. docs/CI_BUILD.md, шаг B.")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources { excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/DEPENDENCIES") } }
    lint { abortOnError = false; warningsAsErrors = false }
    if (hasSigningSecrets) {
        val releaseSigning = signingConfigs.getByName("release")
        val emptyFields = listOfNotNull(
            "storeFile (путь KEYSTORE_FILE)".takeIf { releaseSigning.storeFile == null },
            "storePassword (секрет KEYSTORE_PASSWORD)".takeIf { releaseSigning.storePassword.isNullOrEmpty() },
            "keyAlias (секрет KEY_ALIAS)".takeIf { releaseSigning.keyAlias.isNullOrEmpty() },
            "keyPassword (секрет KEY_PASSWORD)".takeIf { releaseSigning.keyPassword.isNullOrEmpty() },
        )
        if (emptyFields.isNotEmpty()) throw GradleException("Секреты подписи пришли не полностью — не заполнено: " + emptyFields.joinToString(", ") + ". Проверьте имена секретов репозитория (docs/CI_BUILD.md, шаг B): KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS=sign, KEY_PASSWORD.")
        val storeFile = releaseSigning.storeFile!!
        val (keyStore, storeProblem) = loadKeyStore(storeFile, releaseSigning.storePassword!!.toCharArray(), releaseSigning.storeType)
        if (keyStore == null) {
            val m = "Не удалось открыть хранилище ключей «${storeFile.name}» ни как PKCS12, ни как JKS: ${storeProblem?.message}. Проверьте секреты KEYSTORE_BASE64 и KEYSTORE_PASSWORD — docs/CI_BUILD.md, шаг B."
            ghAnnotation("error", "Ключ подписи не открывается", m); throw GradleException(m, storeProblem)
        }
        val requiredAlias = releaseSigning.keyAlias!!
        val resolvedAlias: String = if (keyStore.containsAlias(requiredAlias)) requiredAlias else {
            val keyAliases = keyStore.aliases().toList().filter { keyStore.isKeyEntry(it) }.sorted()
            if (keyAliases.size == 1) {
                val single = keyAliases.first()
                ghAnnotation("warning", "Алиас ключа не совпал", "В секрете KEY_ALIAS=$requiredAlias, а в файле единственная запись «$single» — подписываю ею. Исправьте секрет на $single.")
                appendStepSummary("\n### Ключ подписи\n\n- алиас из секрета `$requiredAlias` не найден, использован `$single`\n")
                single
            } else {
                val m = "В хранилище нет записи «$requiredAlias». Доступны: ${keyAliases.joinToString(", ").ifEmpty { "нет записей с ключом" }}. Проверьте KEY_ALIAS — docs/CI_BUILD.md, шаг B."
                ghAnnotation("error", "Алиас ключа не найден", m); throw GradleException(m)
            }
        }
        try { keyStore.getKey(resolvedAlias, releaseSigning.keyPassword!!.toCharArray()) } catch (e: Exception) {
            val m = "Запись «$resolvedAlias» в файле есть, но не открывается паролем из секрета KEY_PASSWORD: ${e.message}."
            ghAnnotation("error", "Неверный пароль ключа", m); throw GradleException(m, e)
        }
        val pk = try { keyStore.getKey(resolvedAlias, releaseSigning.keyPassword!!.toCharArray()) } catch (e: Exception) { null }
        if (pk == null) {
            val m = "Запись «$resolvedAlias» не является приватным ключом. В секрет KEYSTORE_BASE64 нужно положить хранилище с PrivateKeyEntry — docs/CI_BUILD.md, шаг B1."
            ghAnnotation("error", "В ключе нет приватного ключа", m); throw GradleException(m)
        }
        val cert = keyStore.getCertificate(resolvedAlias) ?: keyStore.getCertificateChain(resolvedAlias)?.firstOrNull()
        val fp = certificateSha256(cert) ?: "хранилище не отдало сертификат"
        ghAnnotation("notice", "Ключ подписи", "алиас «$resolvedAlias», SHA-256: $fp")
        appendStepSummary("\n### Ключ подписи\n\n- алиас: `$resolvedAlias`\n- SHA-256: `$fp`\n")
    }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.04.01")
    implementation(composeBom); androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.16.0")
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
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("com.yandex.android:mobileads:8.6.0")
    implementation("androidx.media3:media3-transformer:1.11.1")
    implementation("androidx.media3:media3-common:1.11.1")
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}

val verifyReleaseApkSignature by tasks.registering {
    description = "Сверяет сертификат релизного APK с ключом из секретов подписи"; group = "verification"
    doLast {
        if (!hasSigningSecrets) { logger.warn("Подпись APK не сверялась: секреты KEYSTORE_* не заданы."); return@doLast }
        val apk = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
        if (!apk.isFile) { logger.warn("Подпись APK не сверялась: файла ${apk.name} нет."); return@doLast }
        val apksigner = findApksigner()
        if (apksigner == null) { logger.warn("Подпись APK не сверялась: apksigner не найден."); return@doLast }
        val (exitCode, output) = runApksigner(apksigner, apk)
        if (exitCode != 0) {
            val m = "apksigner не подтвердил подпись ${apk.name}: " + output.trim().lines().takeLast(6).joinToString(" | ")
            ghAnnotation("error", "APK не подписан", m); throw GradleException(m)
        }
        val apkSha = output.lineSequence().firstOrNull { it.contains("SHA-256 digest", ignoreCase = true) }?.substringAfterLast(":")?.replace(":", "")?.trim()?.uppercase()
        if (apkSha.isNullOrEmpty()) { logger.warn("apksigner не напечатал отпечаток — сравнивать нечего."); return@doLast }
        val apkFp = apkSha.chunked(2).joinToString(":")
        val storeFile = rootProject.file(keystoreFilePath!!)
        val (ks, prob) = loadKeyStore(storeFile, keystorePassword!!.toCharArray(), null)
        if (ks == null) { val m = "Не удалось открыть ключ для сверки: ${prob?.message}"; ghAnnotation("error", "Ключ не открывается", m); throw GradleException(m, prob) }
        val fps = ks.aliases().toList().filter { ks.isKeyEntry(it) }.sorted().map { a -> a to (certificateSha256(ks.getCertificate(a)) ?: certificateSha256(ks.getCertificateChain(a)?.firstOrNull())) }
        val matched = fps.firstOrNull { (_, sha) -> sha.equals(apkFp, ignoreCase = true) }
        if (matched == null) {
            val av = fps.joinToString(", ") { (al, sha) -> "$al ($sha)" }.ifEmpty { "нет записей" }
            val m = "APK подписан не тем ключом. У APK: $apkFp. В ключе: $av. Проверьте KEYSTORE_BASE64 и KEY_ALIAS."
            ghAnnotation("error", "Подпись APK не совпала", m); throw GradleException(m)
        }
        val (alias, _) = matched
        ghAnnotation("notice", "Подпись APK", "APK подписан ключом $alias, SHA-256: $apkFp")
        appendStepSummary("\n### Подпись APK\n\n- сертификат APK: `$apkFp`\n- совпадает с ключом `$alias`\n")
    }
}
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(verifyReleaseApkSignature) }

val aliasUnsignedReleaseApk by tasks.registering {
    description = "Копирует app-release-unsigned.apk в app-release.apk, если подписи нет"; group = "build"
    doLast {
        val dir = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        val u = dir.resolve("app-release-unsigned.apk"); val p = dir.resolve("app-release.apk")
        if (u.isFile && !p.isFile) { u.copyTo(p, overwrite = true); logger.lifecycle("Неподписанный релизный APK доступен как ${p.name}") }
    }
}
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(aliasUnsignedReleaseApk) }
