# Сборка через GitHub Actions — Копилка

Секреты репозитория: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS=sign`, `KEY_PASSWORD`.

Воркфлоу: `.github/workflows/android.yml` (копия в `docs/ci-android.yml`). Запускается на push в `main` и вручную.

Артефакты: `release-apk`, `build-outputs`, `test-report`.

Перед загрузкой в магазин поднимайте `versionCode` (форма запуска или `-PversionCode=`).
