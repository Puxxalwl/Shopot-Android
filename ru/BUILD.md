# Сборка Stille

[English](../docs/BUILD.md) | **Русский**

Всё, что нужно для сборки, тестирования и публикации APK. Поведение клиента
во время работы — в [ARCHITECTURE.md](ARCHITECTURE.md).

## Необходимое

| Инструмент | Версия | Для чего |
|---|---|---|
| JDK | 17 | все сборки |
| Android SDK | target API 35, minSdk 26 | приложение |
| [Zig](https://ziglang.org/download/) | 0.17.0 | кросс-компиляция ядра |

Результат — APK для `arm64-v8a`; ядро упаковывается как `libstille.so`.

## Отладочная сборка и тесты

```bash
bash ./tools/build-core.sh     # ядро → app/src/main/jniLibs/arm64-v8a/libstille.so
./gradlew :app:assembleDebug   # приложение
./gradlew :data:testDebugUnitTest :app:testDebugUnitTest
```

`tools/build-core.sh` кросс-компилирует `core-src/` и кладёт бинарник в
`app/src/main/jniLibs/arm64-v8a/libstille.so` (исключён из Git). Упаковка
APK завершается ошибкой без AArch64-ядра — проверке только Kotlin
(`:app:compileDebugKotlin`) бинарник не нужен. Распаковка native-библиотек
остаётся включённой: Android 10+ запрещает исполнение из записываемого
хранилища приложения, поэтому копия в `filesDir` не годится.

Быстрая инкрементальная проверка Kotlin с тёплыми daemon-ами Gradle/Kotlin:

```bash
./gradlew :app:compileDebugKotlin --daemon --offline --max-workers=2
```

CI (`.github/workflows/ci.yml`) повторяет сборку ядра и сверяет версию
закреплённого `CORE-API.md` с кодеком.

## Подпись релизов

Релизные сборки требуют постоянный приватный keystore, задаваемый
переменными `STILLE_KEYSTORE_PATH`, `STILLE_STORE_PASSWORD`,
`STILLE_KEY_ALIAS` и `STILLE_KEY_PASSWORD`. Отладочные сборки от него не
зависят. Не коммитьте keystore и не выводите его содержимое в логи.

### GitHub Actions

Релизный workflow требует secrets репозитория `ANDROID_KEYSTORE_BASE64`,
`ANDROID_STORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` и
переменную `ANDROID_SIGNING_CERT_SHA256` (SHA-256 публичного сертификата,
64 hex-символа). Перед публикацией проверяются подпись APK, закреплённый
сертификат, целостность ZIP и выравнивание. Если конфигурации нет, релиз
останавливается — запасного debug-ключа не существует.

Настройка GitHub Actions secrets требует доступа Secrets write к
репозиторию; одного git push недостаточно.

### Локальная защищённая схема

Для локальной схемы `~/.local/share/stille-signing/release.p12` и
`release.password` (alias `stille-release`):

```bash
python3 tools/build-signed-release.py --offline
```

Помощник никогда не пересоздаёт отсутствующий ключ, отключает
configuration cache Gradle для подписи и работает с одним worker-ом, heap
Gradle 1536 МиБ и компиляцией Kotlin внутри того же процесса. Права
каталога — 700, файлов — 600; храните защищённую резервную копию обоих
вне репозитория.

## Почему ключ подписи важен

Опубликованные v0.2.3 и v0.2.4 подписаны разными временными ключами
runner-а: оба APK валидны, но Android не может установить один поверх
другого. Обновление установленного приложения требует его исходного
приватного ключа — в APK лежит только публичный сертификат. Постоянный
ключ чинит совместимость будущих релизов, но не старые установки.

## Диагностика проблем

- **«Ядро недоступно», ошибки приглашений/аватара** — в APK нет
  `lib/arm64-v8a/libstille.so`; пересоберите его после
  `bash ./tools/build-core.sh`.
- **Сбой запуска после обновления** — устанавливайте новый APK *поверх*
  существующего приложения. Не очищайте данные: личность и история хранятся
  локально.
- Обезличенный файл диагностики (фиксированные этапы и типы ошибок — без
  сообщений, контактов и секретов) можно отправить из настроек, быстро
  нажав пять раз на **О Stille**.
