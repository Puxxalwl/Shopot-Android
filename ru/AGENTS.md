# AGENTS.md — Stille-Messenger client

**English** | [Русский](ru/AGENTS.md)

*(This is the Russian mirror of AGENTS.md. It must be updated in the same commit as the English version.)*

Этот файл — контракт для контрибьюторов, ИИ-агентов и обзорщиков.
Он адаптирует `AGENTS.md` из stille-messenger-core к Android-клиенту.

## 1. Инварианты
1. Без собственных серверов, телеметрии, аналитики.
2. Минимум зависимостей: Kotlin + Compose + Hilt + Coil, плюс
   `androidx.credentials` только для passkey-восстановления аккаунта.
3. Нет plaintext на диске.
4. Только бинарный протокол (UDS).
5. Аутентификация перед доверием.
6. Ограниченные ресурсы (кеш ≤ 200 сообщений, ≤ 20 аватаров, ≤ 50 МБ файлов).
7. Корректность важнее скорости.
8. Главный поток отзывчив.

## 2. Источник правды
- Активная реализация stille-messenger-core — в `core-src/core/`.
- Клиент должен следить за `CORE-API.md`.
