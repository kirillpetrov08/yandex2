pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Нужен для Tesseract4Android (этап 4 — OCR). Репозиторий подключён заранее,
        // чтобы добавление зависимости не ломало сборку.
        maven("https://jitpack.io")
    }
}

rootProject.name = "ConverterPlus"
include(":app")
