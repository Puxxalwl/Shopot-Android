# SECURITY (RU)

**English** | [Русский](ru/SECURITY.md)

Для Android-клиента:
- Клиент **никогда** не работает с ключами — seed лежит в
  `$HOME/.shopot/zig-seed` (0600), принадлежит процессу `shopot serve`.
- Нет телеметрии, crash-репортёров, аналитики.

Политика кибербезопасности и инвентарь криптографии — в репозитории
[shopot-core](https://github.com/Puxxalwl/shopot-core/blob/master/SECURITY.md).

Сообщайте о уязвимостях приватно: откройте GitHub Security Advisory в
shopot-core, или напишите на security@shopot.io.
