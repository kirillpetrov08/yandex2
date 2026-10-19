# Реклама РСЯ — Копилка

В `gradle.properties` уже стоят боевые ID приложения-донора. Перед релизом Копилки замените их на свои три блока из кабинета РСЯ:

```
ads.testMode=false
ads.bannerUnitId=R-M-XXXXXXXX-1
ads.interstitialUnitId=R-M-XXXXXXXX-2
ads.rewardedUnitId=R-M-XXXXXXXX-3
```

Формат строго `R-M-цифры-цифры`. Если `testMode=false` и ID демо — сборка падает.

Политика:
- согласие до `YandexAds.initialize()`
- баннер на Главной, Истории, Статистике, ≥12 dp от кнопок
- нет рекламы в онбординге, настройках, форме добавления и шите результата
- interstitial после сохранения операции, каждая 3-я и ≥120 сек
- rewarded — 30 минут без рекламы, награда только в `onAdRewarded()`

app-ads.txt: `yandex.com, 110347355, DIRECT`
