# Политика безопасности

[English](../SECURITY.md) | **Русский**

Полная модель угроз и инвентарь криптографии — в репозитории
stille-messenger-core:
[SECURITY.md](https://github.com/puxxalwl/stille-messenger-core/blob/master/SECURITY.md)
(англ.).

Для Android-клиента:

- Клиент **никогда не работает с ключами** — seed личности лежит в
  `~/.stille-messenger/zig-seed` (0600) и принадлежит процессу
  `stille-messenger-core serve`.
- Криптографии в клиенте нет.
- Телеметрии, crash-репортёров и аналитики нет.

## Сообщение об уязвимости

Не открывайте публичный issue. Используйте приватную вкладку GitHub
*Security → Report a vulnerability* в этом репозитории или в
[stille-messenger-core](https://github.com/puxxalwl/stille-messenger-core).
Укажите затронутый коммит, описание и, если возможно, proof of concept.
Подтверждение — в течение 72 часов.
