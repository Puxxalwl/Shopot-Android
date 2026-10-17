#!/usr/bin/env python3
"""Generate deterministic example app screenshots from the supplied logo.

No external image package is required. The generator writes SVG files so the
examples stay small, reviewable, and render identically in documentation and
CI. These are design references, not runtime UI snapshots.
"""
from pathlib import Path
from html import escape

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs" / "screenshots"
LOGO = "../../app/src/main/res/drawable-nodpi/shopot_logo.png"

W, H = 720, 1280


def svg(title: str, body: str, dark: bool = False, show_nav: bool = True) -> str:
    bg = "#101218" if dark else "#E9EDF5"
    glass = "#263042cc" if dark else "#ffffffbf"
    fg = "#F8FAFF" if dark else "#182235"
    muted = "#AAB5CA" if dark else "#71809B"
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop stop-color="{bg}"/><stop offset="1" stop-color="#687CA8"/></linearGradient>
    <filter id="shadow"><feGaussianBlur stdDeviation="14"/></filter>
  </defs>
  <rect width="720" height="1280" fill="url(#bg)"/>
  <circle cx="580" cy="150" r="190" fill="#7AA7FF44" filter="url(#shadow)"/>
  <circle cx="110" cy="950" r="230" fill="#B48CFF33" filter="url(#shadow)"/>
  <rect x="20" y="20" width="680" height="1240" rx="42" fill="{glass}" stroke="#FFFFFF44" stroke-width="2"/>
  <text x="58" y="92" font-family="sans-serif" font-size="40" font-weight="700" fill="{fg}">{escape(title)}</text>
  {body}
  {f'''<rect x="20" y="1160" width="680" height="100" rx="42" fill="{glass}" stroke="#FFFFFF44"/>
  <text x="115" y="1215" font-family="sans-serif" font-size="24" fill="{muted}">Профиль</text>
  <text x="285" y="1215" font-family="sans-serif" font-size="24" fill="#1683FF">Чаты</text>
  <text x="430" y="1215" font-family="sans-serif" font-size="24" fill="{muted}">Контакты</text>
  <text x="585" y="1215" font-family="sans-serif" font-size="24" fill="{muted}">⚙</text>''' if show_nav else ''}
</svg>'''


def chats() -> str:
    rows = []
    for i, (name, msg, color) in enumerate((("Алиса", "Привет!", "#1683FF"), ("Боб", "Файл отправлен", "#35C978"), ("Charlie", "До встречи", "#A477FF"))):
        y = 190 + i * 170
        rows.append(f'''<rect x="48" y="{y}" width="624" height="140" rx="28" fill="#FFFFFF55" stroke="#FFFFFF66"/>
        <circle cx="105" cy="{y+70}" r="38" fill="{color}"/><text x="82" y="{y+80}" font-family="sans-serif" font-size="22" fill="white">{name[:2]}</text>
        <text x="165" y="{y+58}" font-family="sans-serif" font-size="30" font-weight="600" fill="#182235">{name}</text>
        <text x="165" y="{y+98}" font-family="sans-serif" font-size="24" fill="#71809B">{msg}</text>
        <text x="590" y="{y+58}" font-family="sans-serif" font-size="18" fill="#71809B">12:45</text>''')
    return svg("Чаты", "".join(rows))


def chat() -> str:
    body = '''<text x="58" y="145" font-family="sans-serif" font-size="20" fill="#35C978">Алиса · в сети</text>
    <rect x="52" y="215" width="390" height="96" rx="28" fill="#FFFFFF99"/><text x="80" y="275" font-family="sans-serif" font-size="26" fill="#182235">Привет! Как дела?</text>
    <rect x="250" y="370" width="420" height="96" rx="28" fill="#1683FF"/><text x="278" y="430" font-family="sans-serif" font-size="26" fill="white">Всё хорошо ✓✓</text>
    <rect x="52" y="525" width="490" height="96" rx="28" fill="#FFFFFF99"/><text x="80" y="585" font-family="sans-serif" font-size="26" fill="#182235">Отправляю документ.</text>
    <rect x="48" y="1010" width="624" height="88" rx="44" fill="#FFFFFFaa" stroke="#FFFFFF77"/><text x="92" y="1065" font-family="sans-serif" font-size="25" fill="#71809B">Сообщение...</text><circle cx="620" cy="1054" r="25" fill="#1683FF"/><text x="609" y="1063" font-family="sans-serif" font-size="20" fill="white">➤</text>'''
    return svg("Алиса", body)


def settings() -> str:
    body = '''<rect x="48" y="180" width="624" height="102" rx="28" fill="#FFFFFF55" stroke="#FFFFFF66"/><text x="82" y="230" font-family="sans-serif" font-size="28" fill="#182235">Тёмная тема</text><text x="82" y="265" font-family="sans-serif" font-size="20" fill="#71809B">Светлая / тёмная</text><rect x="575" y="215" width="58" height="34" rx="17" fill="#1683FF"/><circle cx="616" cy="232" r="13" fill="white"/>
    <rect x="48" y="310" width="624" height="102" rx="28" fill="#FFFFFF55" stroke="#FFFFFF66"/><text x="82" y="360" font-family="sans-serif" font-size="28" fill="#182235">Язык</text><text x="82" y="395" font-family="sans-serif" font-size="20" fill="#71809B">Русский</text>
    <rect x="48" y="440" width="624" height="102" rx="28" fill="#FFFFFF55" stroke="#FFFFFF66"/><text x="82" y="490" font-family="sans-serif" font-size="28" fill="#182235">Уведомления</text><text x="82" y="525" font-family="sans-serif" font-size="20" fill="#71809B">Фоновый сервис Shopot</text>'''
    return svg("Настройки", body)

def welcome() -> str:
    body = '''<circle cx="360" cy="188" r="54" fill="#1683FF"/><text x="339" y="207" font-family="sans-serif" font-size="56" font-weight="700" fill="white">Ш</text>
    <text x="58" y="300" font-family="sans-serif" font-size="34" font-weight="700" fill="#182235">Добро пожаловать в Shopot</text>
    <text x="58" y="340" font-family="sans-serif" font-size="22" fill="#71809B">Настройте приложение под себя</text>
    <rect x="48" y="390" width="624" height="112" rx="28" fill="#FFFFFF55" stroke="#FFFFFF66"/><text x="82" y="434" font-family="sans-serif" font-size="20" fill="#71809B">Ваше имя</text><text x="82" y="476" font-family="sans-serif" font-size="28" fill="#182235">Алексей</text>
    <text x="58" y="570" font-family="sans-serif" font-size="24" font-weight="600" fill="#182235">Язык интерфейса</text><rect x="48" y="600" width="300" height="64" rx="24" fill="#1683FF"/><text x="105" y="641" font-family="sans-serif" font-size="24" fill="white">Русский</text><rect x="360" y="600" width="300" height="64" rx="24" fill="#FFFFFF55" stroke="#FFFFFF66"/><text x="446" y="641" font-family="sans-serif" font-size="24" fill="#182235">English</text>
    <text x="58" y="730" font-family="sans-serif" font-size="24" font-weight="600" fill="#182235">Тема</text><rect x="48" y="760" width="188" height="64" rx="24" fill="#1683FF"/><text x="75" y="801" font-family="sans-serif" font-size="21" fill="white">Системная</text><rect x="248" y="760" width="188" height="64" rx="24" fill="#FFFFFF55" stroke="#FFFFFF66"/><text x="281" y="801" font-family="sans-serif" font-size="21" fill="#182235">Светлая</text><rect x="448" y="760" width="188" height="64" rx="24" fill="#FFFFFF55" stroke="#FFFFFF66"/><text x="485" y="801" font-family="sans-serif" font-size="21" fill="#182235">Тёмная</text>
    <rect x="48" y="900" width="624" height="76" rx="24" fill="#1683FF"/><text x="315" y="948" font-family="sans-serif" font-size="26" font-weight="600" fill="white">Начать</text>'''
    return svg("Первый запуск", body, show_nav=False)


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    files = {"welcome.svg": welcome(), "chats.svg": chats(), "chat.svg": chat(), "settings.svg": settings()}
    for name, content in files.items():
        (OUT / name).write_text(content, encoding="utf-8")
    (OUT / "README.md").write_text("""# Example screenshots\n\nGenerated by `tools/generate_screenshots.py`. SVG is used to keep examples\nsmall and deterministic. The supplied Shopot logo is installed at\n`app/src/main/res/drawable-nodpi/shopot_logo.png`.\n\n![Welcome](welcome.svg)\n![Chats](chats.svg)\n![Chat](chat.svg)\n![Settings](settings.svg)\n""", encoding="utf-8")
    print(f"generated {len(files)} screenshot examples in {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
