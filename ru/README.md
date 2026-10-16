# Shopot Messager (Android)

**English** | [Русский](ru/README.md)

Android-клиент для демона [shopot-core](https://github.com/Puxxalwl/shopot-core).

## Возможности
- **P2P — без серверов** — прямой TCP (порты 7777–7786), LAN discovery или self-hosted relay
- **Сквозное шифрование (E2EE)** — клиент никогда не работает с ключами
- **Без телеметрии, аналитики, Firebase**
- **Glassmorphism UI** — iPhone-стиль с размытием фона
- **Богатые сообщения** — bold, italic, spoiler, code, links
- **Реакции** — любой Unicode эмодзи
- **Файлы и фото** — до 64 МБ
- **Голосовые звонки** — 16 кГц E2EE, ADPCM + DTX
- **Офлайн-доставка** — зашифрованный outbox с ACK и dedup

## Сборка

```bash
# 1. Сборка shopot-core для Android (требует Zig 0.17.0)
./tools/build-core.sh

# 2. Сборка Android-приложения
./gradlew :app:assembleDebug

# 3. Тесты
./gradlew :data:testDebugUnitTest :app:testDebugUnitTest
```

**Требования:** Android 8.0+ (API 26), JDK 17, Zig 0.17.0 для сборки core.

## Скриншоты

Примеры UI генерируются скриптом `tools/generate_screenshots.py` и находятся
в [docs/screenshots](../docs/screenshots). Логотип установлен в
`app/src/main/res/drawable-nodpi/shopot_logo.png`.

## Linux-клиент

Официальный Linux-клиент — [EryyynIT/shopot](https://github.com/EryyynIT/shopot).
Он использует тот же shopot-core и бинарный UDS API, поэтому служит
референсом desktop UX и интеграции.

## Архитектура

```
app/                      # Android-приложение: UI + foreground service
data/                     # Кодек бинарного протокола (соответствует CORE-API.md)
core-src/                 # Исходники shopot-core (cloned)
tools/                    # Скрипты кросс-компиляции
```

`ShopotService` — foreground service, который:
1. Распаковывает и запускает бинарник `shopot` из assets
2. Подключается к UDS `$HOME/.shopot/api.sock`
3. Отправляет `GET_VERSION` для проверки совместимости
4. Слушает события и рассылает их в UI через `StateFlow`

## Добавление друга

```
A: CREATE_INVITE → "shopot://i/ABC123..."
   (делимся в любом мессенджере)
B: ACCEPT_INVITE → соединение установлено
```

## Лицензия

Оригинальный код Android-клиента распространяется по **AGPL-3.0-or-later**.
Все форки, модифицированные версии и распространения покрываемого кода
должны использовать ту же лицензию и сохранять указание правообладателей
`puxxalwl` и `EryyynIT`. См. [LICENSE](../LICENSE),
[LICENSE-AGPL-3.0.txt](../LICENSE-AGPL-3.0.txt) и [NOTICE](../NOTICE).

Submodule `core-src/` — независимый проект shopot-core с собственной MIT
лицензией. Linux-клиент [EryyynIT/shopot](https://github.com/EryyynIT/shopot)
только указан как референс и не копируется в этот репозиторий.
