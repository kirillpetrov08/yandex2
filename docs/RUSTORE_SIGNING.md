# Подпись для RuStore — Копилка (com.copilka.app)

Два ключа: локальный `app-signing.keystore` (ваш) и загрузочный для RuStore через PEPK.
SHA-256 сертификата печатается в Summary прогона — сверяйте с консолью RuStore.

Ошибки:
- `SigningConfig "release" is missing` — проверьте имена секретов `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS=sign`, `KEY_PASSWORD`.
- `No key with alias` — алиас в файле не `sign`, проверьте `keytool -list`.

Секреты ставятся в Settings → Secrets → Actions.
