# APK релиза с рабочей рекламой — Копилка

Сборка берёт ID блоков РСЯ из `gradle.properties`:

```
ads.testMode=false
ads.bannerUnitId=R-M-20230616-2
ads.interstitialUnitId=R-M-20230616-4
ads.rewardedUnitId=R-M-20230616-7
```

Если `testMode=false`, а ID не вида `R-M-XXXXXXX-Y`, сборка падает — защита от релиза с демо-блоками.

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease -PversionCode=2 -PversionName=1.0.1
./gradlew bundleRelease
```

Подпись: переменные окружения `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (секреты GitHub Actions).
Без них APK будет `app-release-unsigned.apk`, скопированный в `app-release.apk` для проверки, но без подписи в RuStore не примут.
