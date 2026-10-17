# Contributing to Stille-Messenger (RU)

**English** | [Русский](ru/CONTRIBUTING.md)

Правила взноса, адаптированы из shopot-core.

## 1. Инварианты
- Без собственных серверов, телеметрии, аналитики
- Нет Firebase / crash-репортёров / аналитики
- Нет plaintext на диске
- Нет JSON в протоколе (только бинарный UDS)

## 2. Настройка
```bash
git clone https://github.com/Puxxalwl/Stille-Messenger-Android.git
cd Stille-Messenger-Android
./gradlew :app:assembleDebug
```

Инструменты: JDK 17, AGP 8.7+, Kotlin 2.1+.

## 3. Архитектура исходников
| Подсистема | Папка |
|---|---|
| Протокол | `data/src/main/java/com/stille/coreapi/` |
| Сервис | `app/src/main/java/com/stille/messenger/service/` |
| UI | `app/src/main/java/com/stille/messenger/ui/` |

## 4. Коммиты
Формат: `component: imperative summary` (≤ 72 символов).
Префиксы: `ui`, `coreapi`, `service`, `build`, `docs`, `i18n`.
Обязательно: `Signed-off-by: Name <email>` (DCO).

## 5. Чек-лист PR
- [ ] `:data:testDebugUnitTest` и `:app:testDebugUnitTest` проходят
- [ ] Новые тесты для нового поведения
- [ ] Нет логирования тел тел, ключей, кодов приглашений
- [ ] Протокол соответствует CORE-API.md
- [ ] Обновлена документация (EN + RU)
- [ ] Добавлен CHANGELOG entry

## 6. Лицензия
Оригинальный Android-клиент распространяется по AGPL-3.0-or-later.
Вклад лицензируется на тех же условиях. `core-src/` сохраняет лицензию
upstream shopot-core.
