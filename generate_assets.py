#!/usr/bin/env python3
# Генерация ассетов для RuStore: Копилка — Финансы офлайн
# 6 скриншотов 1080x1920, feature graphic 1024x500, иконки 512/1024
import os, pathlib
from PIL import Image, ImageDraw, ImageFont

ROOT = pathlib.Path("/home/user/yandex2")
OUT = ROOT / "store-assets"
SCREENS = OUT / "screens"
OUT.mkdir(parents=True, exist_ok=True)
SCREENS.mkdir(parents=True, exist_ok=True)

W, H = 1080, 1920
BG_LIGHT = (246, 248, 244)
BG_DARK = (14, 20, 16)
WHITE = (255,255,255)
PRIMARY = (27, 140, 74)
PRIMARY_DARK = (14, 100, 55)
TEXT_PRIMARY = (15, 26, 20)
TEXT_SECONDARY = (91, 107, 98)
DIVIDER = (225, 232, 227)
CARD_BG = (255,255,255)
SUCCESS = (27, 140, 74)
ERROR = (217, 79, 48)
GOLD = (230, 180, 34)
TEAL = (14, 122, 155)

def font(size, bold=False):
    # DejaVu
    path = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
    try:
        return ImageFont.truetype(path, size)
    except:
        return ImageFont.load_default()

def rounded_rect(draw, xy, r, fill):
    x0,y0,x1,y1 = xy
    draw.rounded_rectangle(xy, radius=r, fill=fill)

def status_bar(img):
    draw = ImageDraw.Draw(img)
    draw.rectangle([0,0,W,60], fill=(248,248,248))
    draw.text((40,18), "9:41", fill=(0,0,0), font=font(28))
    draw.text((W-140,18), "100% ●", fill=(0,0,0), font=font(26))
    # notch
    draw.rounded_rectangle([W//2-120,0,W//2+120,30], radius=15, fill=(0,0,0))

def nav_bar(draw, y, selected=0):
    # bottom nav 4 tabs
    h = 140
    y0 = H - h
    draw.rectangle([0,y0,W,H], fill=WHITE)
    draw.line([0,y0,W,y0], fill=DIVIDER, width=2)
    tabs = [("Главная","■"), ("История","≡"), ("Статистика","◈"), ("Настройки","⚙")]
    for i,(label,icon) in enumerate(tabs):
        x = i*W//4 + W//8
        col = PRIMARY if i==selected else TEXT_SECONDARY
        draw.text((x-30, y0+18), icon, fill=col, font=font(36, bold=True))
        draw.text((x-50, y0+65), label, fill=col, font=font(22))
    return y0

def banner(draw, y):
    # fake banner 90dp
    h = 90
    draw.rectangle([0,y,W,y+h], fill=(235,235,235))
    draw.rectangle([W//2-100,y+20,W//2+100,y+60], fill=(200,200,200))
    draw.text((W//2-60, y+28), "Реклама", fill=(120,120,120), font=font(22))
    return y+h

def card_balance(draw, y):
    # баланс
    r=24
    x0,y0 = 32, y
    x1,y1 = W-32, y+320
    rounded_rect(draw, [x0,y0,x1,y1], r, PRIMARY)
    draw.text((x0+32, y0+32), "Общий баланс", fill=WHITE, font=font(28))
    draw.text((x0+32, y0+80), "128 450,00 ₽", fill=WHITE, font=font(54, bold=True))
    draw.text((x0+32, y0+160), "Потрачено в октябре: 42 300 ₽ · Доход: 85 000 ₽", fill=(230,255,240), font=font(22))
    # small chart line
    points = []
    for i in range(7):
        px = x0+40 + i*140
        py = y0+240 + ( (-1)**i * 20 )
        points.append((px,py))
    for i in range(len(points)-1):
        draw.line([points[i], points[i+1]], fill=WHITE, width=4)
        draw.ellipse([points[i][0]-6, points[i][1]-6, points[i][0]+6, points[i][1]+6], fill=WHITE)
    draw.text((x0+32, y1-36), "−2,3% к прошлому месяцу", fill=(200,255,220), font=font(22))
    return y1

def card_budgets(draw, y):
    # budgets
    x0,y0 = 32, y
    x1,y1 = W-32, y+420
    rounded_rect(draw, [x0,y0,x1,y1], r=24, fill=WHITE)
    draw.rectangle([x0,y0,x1,y0+6], fill=PRIMARY)
    draw.text((x0+24, y0+20), "Бюджеты на месяц", fill=TEXT_PRIMARY, font=font(30, bold=True))
    items = [
        ("Продукты", 12000, 18500, PRIMARY),
        ("Транспорт", 3400, 5000, TEAL),
        ("Жильё", 25000, 25000, GOLD),
        ("Развлечения", 8200, 7000, ERROR),
    ]
    cur_y = y0+70
    for name, spent, limit, col in items:
        draw.text((x0+24, cur_y), name, fill=TEXT_PRIMARY, font=font(26, bold=True))
        left = limit - spent
        txt = f"{spent:,} ₽ / {limit:,} ₽".replace(",", " ") + f" · осталось {left:,} ₽".replace(",", " ") if left>=0 else f"{spent:,} ₽ / {limit:,} ₽ · превышен".replace(",", " ")
        draw.text((x0+24, cur_y+32), txt, fill=TEXT_SECONDARY, font=font(20))
        # progress bar
        bar_y = cur_y+62
        draw.rounded_rectangle([x0+24, bar_y, x1-24, bar_y+12], radius=6, fill=(230,240,230))
        prog = min(spent/limit, 1.0) if limit!=0 else 0
        draw.rounded_rectangle([x0+24, bar_y, x0+24 + int((x1-48)*prog), bar_y+12], radius=6, fill=col)
        cur_y += 88
    return y1

def transaction_row(draw, y, icon, title, subtitle, amount, color):
    x0,y0 = 32, y
    x1,y1 = W-32, y+110
    rounded_rect(draw, [x0,y0,x1,y1], r=18, fill=WHITE)
    # icon bg
    draw.ellipse([x0+20, y0+20, x0+80, y0+80], fill=color)
    draw.text((x0+36, y0+34), icon, fill=WHITE, font=font(28, bold=True))
    draw.text((x0+110, y0+18), title, fill=TEXT_PRIMARY, font=font(26, bold=True))
    draw.text((x0+110, y0+52), subtitle, fill=TEXT_SECONDARY, font=font(20))
    draw.text((x1-200, y0+36), amount, fill=color if "-" in amount else SUCCESS, font=font(26, bold=True))
    return y1+12

def screen1_home():
    img = Image.new("RGB", (W,H), BG_LIGHT)
    status_bar(img)
    draw = ImageDraw.Draw(img)
    draw.rectangle([0,60,W,180], fill=WHITE)
    draw.text((40,80), "Копилка", fill=TEXT_PRIMARY, font=font(40, bold=True))
    draw.text((40,124), "Финансы офлайн · всё на устройстве", fill=TEXT_SECONDARY, font=font(24))
    # balance
    y = card_balance(draw, 200)
    y2 = card_budgets(draw, y+20)
    # recent title
    draw.text((40, y2+20), "Последние операции", fill=TEXT_PRIMARY, font=font(28, bold=True))
    y3 = y2+60
    y3 = transaction_row(draw, y3, "🛒", "Пятёрочка", "Сегодня · Карта · Продукты", "−1 240 ₽", ERROR)
    y3 = transaction_row(draw, y3, "💰", "Зарплата", "Сегодня · Карта · Доход", "+85 000 ₽", SUCCESS)
    y3 = transaction_row(draw, y3, "🚕", "Яндекс Такси", "Вчера · Нал · Транспорт", "−560 ₽", TEAL)
    # fab
    draw.rounded_rectangle([W-90-32, H-180-90, W-32, H-180], radius=28, fill=PRIMARY)
    draw.text((W-82, H-180-64), "+", fill=WHITE, font=font(44, bold=True))
    nav_bar(draw, 0, selected=0)
    # banner above nav
    banner(draw, H-230)
    return img

def screen2_add():
    img = Image.new("RGB", (W,H), BG_LIGHT)
    status_bar(img)
    draw = ImageDraw.Draw(img)
    # header
    draw.rectangle([0,60,W,150], fill=WHITE)
    draw.text((40,80), "←  Новая операция", fill=TEXT_PRIMARY, font=font(32, bold=True))
    # type chips
    y=170
    chips = [("Расход", PRIMARY, WHITE), ("Доход", WHITE, TEXT_PRIMARY), ("Перевод", WHITE, TEXT_PRIMARY)]
    for i,(label, bg, fg) in enumerate(chips):
        x0 = 40 + i*320
        draw.rounded_rectangle([x0, y, x0+300, y+56], radius=28, fill=bg, outline=DIVIDER if bg==WHITE else None)
        draw.text((x0+90, y+14), label, fill=fg, font=font(24, bold=True))
    # amount card
    y+=90
    draw.rounded_rectangle([32,y,W-32,y+220], radius=24, fill=WHITE)
    draw.text((60, y+20), "Сумма", fill=TEXT_SECONDARY, font=font(24))
    draw.text((60, y+60), "1 240,00 ₽", fill=TEXT_PRIMARY, font=font(48, bold=True))
    draw.line([60, y+120, W-60, y+120], fill=PRIMARY, width=3)
    # calculator grid hint
    draw.text((60, y+140), "Категории: Продукты · Транспорт · Жильё · Ещё", fill=TEXT_SECONDARY, font=font(20))
    draw.text((W-200, y+140), "⌨", fill=PRIMARY, font=font(32))
    # category selector
    y+=260
    draw.rounded_rectangle([32,y,W-32,y+140], radius=20, fill=WHITE)
    draw.text((60, y+20), "Категория", fill=TEXT_SECONDARY, font=font(22))
    draw.text((60, y+50), "🛒  Продукты", fill=TEXT_PRIMARY, font=font(28, bold=True))
    draw.text((W-120, y+50), "›", fill=TEXT_SECONDARY, font=font(28))
    draw.text((60, y+90), "Счёт: Карта · Сегодня 19:15", fill=TEXT_SECONDARY, font=font(20))
    # comment
    y+=160
    draw.rounded_rectangle([32,y,W-32,y+100], radius=20, fill=WHITE)
    draw.text((60, y+20), "Комментарий", fill=TEXT_SECONDARY, font=font(22))
    draw.text((60, y+50), "Пятёрочка, продукты на неделю", fill=TEXT_PRIMARY, font=font(22))
    # receipt
    y+=120
    draw.rounded_rectangle([32,y,W-32,y+140], radius=20, fill=WHITE)
    draw.text((60, y+20), "Чек", fill=TEXT_SECONDARY, font=font(22))
    draw.rounded_rectangle([60, y+60, W-60, y+110], radius=12, fill=(245,245,240))
    draw.text((W//2-110, y+72), "+  Прикрепить фото чека", fill=PRIMARY, font=font(20, bold=True))
    # CTA
    draw.rounded_rectangle([32, H-220, W-32, H-160], radius=16, fill=PRIMARY)
    draw.text((W//2-90, H-200), "Сохранить", fill=WHITE, font=font(28, bold=True))
    nav_bar(draw, 0, selected=0)
    banner(draw, H-320)
    return img

def screen3_history():
    img = Image.new("RGB", (W,H), BG_LIGHT)
    status_bar(img)
    draw = ImageDraw.Draw(img)
    draw.rectangle([0,60,W,150], fill=WHITE)
    draw.text((40,80), "История", fill=TEXT_PRIMARY, font=font(34, bold=True))
    # search
    draw.rounded_rectangle([32,170,W-32,230], radius=16, fill=WHITE)
    draw.text((60,186), "🔍  Поиск по комментарию", fill=TEXT_SECONDARY, font=font(22))
    # chips
    draw.rounded_rectangle([32,250,120,290], radius=20, fill=PRIMARY)
    draw.text((52,260), "Все", fill=WHITE, font=font(20, bold=True))
    for i, txt in enumerate(["Продукты","Транспорт","Жильё"]):
        x0=140+i*220
        draw.rounded_rectangle([x0,250,x0+200,290], radius=20, fill=WHITE)
        draw.text((x0+20,260), txt, fill=TEXT_SECONDARY, font=font(18))
    # grouping
    y=310
    draw.text((40,y), "Сегодня", fill=TEXT_SECONDARY, font=font(20))
    y+=36
    y=transaction_row(draw, y, "🛒", "Магнит", "Карта · Продукты", "−890 ₽", ERROR)
    y=transaction_row(draw, y, "⛽", "Лукойл", "Нал · Транспорт", "−2 400 ₽", TEAL)
    draw.text((40,y+8), "Вчера", fill=TEXT_SECONDARY, font=font(20))
    y+=44
    y=transaction_row(draw, y, "🏠", "ЖКХ", "Карта · Жильё", "−8 500 ₽", GOLD)
    y=transaction_row(draw, y, "🎬", "Кино", "Карта · Развлечения", "−1 200 ₽", ERROR)
    draw.text((40,y+8), "Ранее", fill=TEXT_SECONDARY, font=font(20))
    y+=44
    y=transaction_row(draw, y, "💼", "Фриланс", "Карта · Доход", "+42 000 ₽", SUCCESS)
    # badge
    draw.ellipse([W-80, y-90, W-40, y-50], fill=PRIMARY)
    draw.text((W-68, y-80), "5", fill=WHITE, font=font(20, bold=True))
    nav_bar(draw, 0, selected=1)
    banner(draw, H-230)
    return img

def screen4_stats():
    img = Image.new("RGB", (W,H), BG_LIGHT)
    status_bar(img)
    draw = ImageDraw.Draw(img)
    draw.rectangle([0,60,W,150], fill=WHITE)
    draw.text((40,80), "Статистика", fill=TEXT_PRIMARY, font=font(34, bold=True))
    # period chips
    draw.rounded_rectangle([32,170,180,210], radius=20, fill=WHITE)
    draw.text((50,180), "Неделя", fill=TEXT_SECONDARY, font=font(18))
    draw.rounded_rectangle([200,170,360,210], radius=20, fill=PRIMARY)
    draw.text((230,180), "Месяц", fill=WHITE, font=font(18, bold=True))
    draw.rounded_rectangle([380,170,540,210], radius=20, fill=WHITE)
    draw.text((400,180), "Всё время", fill=TEXT_SECONDARY, font=font(18))
    # totals
    y=230
    draw.rounded_rectangle([32,y, W//2-16, y+180], radius=20, fill=WHITE)
    draw.text((60, y+20), "Расход", fill=TEXT_SECONDARY, font=font(20))
    draw.text((60, y+60), "42 300 ₽", fill=ERROR, font=font(32, bold=True))
    draw.text((60, y+110), "▲ +12% к сентябрю", fill=ERROR, font=font(18))
    draw.rounded_rectangle([W//2+16,y, W-32, y+180], radius=20, fill=WHITE)
    draw.text((W//2+40, y+20), "Доход", fill=TEXT_SECONDARY, font=font(20))
    draw.text((W//2+40, y+60), "85 000 ₽", fill=SUCCESS, font=font(32, bold=True))
    # pie chart
    y+=200
    draw.rounded_rectangle([32,y,W-32,y+520], radius=24, fill=WHITE)
    draw.text((60,y+20), "Расходы по категориям", fill=TEXT_PRIMARY, font=font(26, bold=True))
    # pie
    cx, cy = W//2, y+220
    r=120
    # segments: 35% food, 25% housing, 20% transport, 12% fun, 8% health
    segs = [(0.35, PRIMARY), (0.25, GOLD), (0.20, TEAL), (0.12, ERROR), (0.08, (140,80,140))]
    start=-90
    for pct, col in segs:
        sweep= pct*360
        draw.pieslice([cx-r, cy-r, cx+r, cy+r], start=start, end=start+sweep, fill=col)
        start+=sweep
    # legend
    leg_y = y+380
    legs = [("Продукты 34%", PRIMARY), ("Жильё 26%", GOLD), ("Транспорт 18%", TEAL), ("Развлечения 14%", ERROR)]
    for i,(txt,col) in enumerate(legs):
        x0=60 + (i%2)* (W//2)
        yy=leg_y + (i//2)*40
        draw.ellipse([x0, yy, x0+20, yy+20], fill=col)
        draw.text((x0+28, yy-2), txt, fill=TEXT_PRIMARY, font=font(18))
    # balance card
    y+=540
    draw.rounded_rectangle([32,y,W-32,y+160], radius=20, fill=PRIMARY)
    draw.text((60,y+24), "Баланс за месяц", fill=WHITE, font=font(22))
    draw.text((60,y+60), "+42 700 ₽", fill=WHITE, font=font(36, bold=True))
    draw.text((60,y+110), "Доход покрывает расход на 201%", fill=(220,255,230), font=font(18))
    nav_bar(draw, 0, selected=2)
    banner(draw, H-230)
    return img

def screen5_budgets():
    img = Image.new("RGB", (W,H), BG_LIGHT)
    status_bar(img)
    draw = ImageDraw.Draw(img)
    draw.rectangle([0,60,W,150], fill=WHITE)
    draw.text((40,80), "Бюджеты", fill=TEXT_PRIMARY, font=font(34, bold=True))
    draw.text((W-180,92), "+ Лимит", fill=PRIMARY, font=font(22, bold=True))
    y=170
    budgets = [
        ("🛒 Продукты", 15400, 18500, "осталось 3 100 ₽", PRIMARY),
        ("🚕 Транспорт", 3400, 5000, "осталось 1 600 ₽", TEAL),
        ("🏠 Жильё", 25000, 25000, "лимит исчерпан", GOLD),
        ("🎮 Развлечения", 8200, 7000, "превышен на 1 200 ₽", ERROR),
    ]
    for name, spent, limit, hint, col in budgets:
        draw.rounded_rectangle([32,y,W-32,y+200], radius=20, fill=WHITE)
        draw.text((60,y+20), name, fill=TEXT_PRIMARY, font=font(26, bold=True))
        draw.text((60,y+56), f"{spent:,} ₽ из {limit:,} ₽".replace(",", " "), fill=TEXT_SECONDARY, font=font(18))
        draw.text((60,y+82), hint, fill=col, font=font(18, bold=True))
        # bar
        bar_y=y+120
        draw.rounded_rectangle([60, bar_y, W-60, bar_y+16], radius=8, fill=(235,242,235))
        prog = min(spent/limit, 1.2)
        fill_w = int((W-120)* min(prog,1.0))
        draw.rounded_rectangle([60, bar_y, 60+fill_w, bar_y+16], radius=8, fill=col)
        if prog>1:
            draw.ellipse([W-60-12, bar_y-4, W-60+12, bar_y+20], fill=ERROR)
            draw.text((W-60-4, bar_y-2), "!", fill=WHITE, font=font(16, bold=True))
        y+=220
    # tip card
    draw.rounded_rectangle([32,y,W-32,y+140], radius=20, fill=(255,251,220))
    draw.text((60,y+20), "💡 Совет", fill=(120,90,0), font=font(20, bold=True))
    draw.text((60,y+50), "Траты на продукты выросли на 18% —", fill=TEXT_PRIMARY, font=font(18))
    draw.text((60,y+76), "попробуйте лимит 16 000 ₽ в ноябре.", fill=TEXT_SECONDARY, font=font(18))
    nav_bar(draw, 0, selected=0)
    return img

def screen6_settings():
    img = Image.new("RGB", (W,H), BG_LIGHT)
    status_bar(img)
    draw = ImageDraw.Draw(img)
    draw.rectangle([0,60,W,150], fill=WHITE)
    draw.text((40,80), "Настройки", fill=TEXT_PRIMARY, font=font(34, bold=True))
    y=170
    sections = [
        ("Внешний вид", [("Тема", "Как в системе ›"), ("Вибрация", "Вкл")]),
        ("Файлы", [("Папка сохранения", "Загрузки ›"), ("Экспорт CSV/PDF", "›")]),
        ("Реклама", [("Убрать рекламу", "30 мин без рекламы"), ("Персонализация", "Вкл")]),
        ("О приложении", [("Версия 1.0.0", "Сборка 1"), ("Политика конфиденциальности", "›"), ("Написать разработчику", "›")]),
    ]
    for title, rows in sections:
        draw.text((40,y), title, fill=TEXT_SECONDARY, font=font(20, bold=True))
        y+=32
        draw.rounded_rectangle([32,y,W-32,y+ len(rows)*76+20], radius=16, fill=WHITE)
        for i,(label, val) in enumerate(rows):
            yy=y+10+i*76
            draw.text((60, yy+16), label, fill=TEXT_PRIMARY, font=font(22))
            draw.text((W-40 - len(val)*12, yy+16), val, fill=TEXT_SECONDARY, font=font(20))
            if i != len(rows)-1:
                draw.line([60, yy+56, W-60, yy+56], fill=DIVIDER, width=2)
        y+= len(rows)*76 + 40
    # promo card
    draw.rounded_rectangle([32,y,W-32,y+160], radius=20, fill=PRIMARY)
    draw.text((60,y+24), "Уберите рекламу на 30 минут", fill=WHITE, font=font(22, bold=True))
    draw.text((60,y+58), "Посмотрите короткое видео — водяной", fill=(220,255,230), font=font(18))
    draw.text((60,y+82), "знак с экспорта исчезнет.", fill=(220,255,230), font=font(18))
    draw.rounded_rectangle([60,y+110,W-200,y+140], radius=12, fill=WHITE)
    draw.text((80,y+118), "Посмотреть видео", fill=PRIMARY, font=font(18, bold=True))
    nav_bar(draw, 0, selected=3)
    return img

def feature_graphic():
    Wf, Hf = 1024, 500
    img = Image.new("RGB", (Wf, Hf), PRIMARY)
    draw = ImageDraw.Draw(img)
    # gradient-like
    for i in range(Hf):
        alpha = i/Hf
        r = int(PRIMARY[0]*(1-alpha) + PRIMARY_DARK[0]*alpha)
        g = int(PRIMARY[1]*(1-alpha) + PRIMARY_DARK[1]*alpha)
        b = int(PRIMARY[2]*(1-alpha) + PRIMARY_DARK[2]*alpha)
        draw.line([0,i,Wf,i], fill=(r,g,b))
    # icon left
    draw.rounded_rectangle([60, 60, 340, 340], radius=40, fill=WHITE)
    draw.text((110, 100), "₽", fill=PRIMARY, font=font(180, bold=True))
    # text
    draw.text((400, 80), "Копилка", fill=WHITE, font=font(78, bold=True))
    draw.text((400, 180), "Финансы офлайн", fill=(220,255,230), font=font(42))
    draw.text((400, 260), "Учёт расходов · Бюджеты · Статистика", fill=(240,255,240), font=font(26))
    draw.text((400, 310), "Без интернета · Без подписок · Без регистрации", fill=(220,230,220), font=font(22))
    # badges
    draw.rounded_rectangle([400, 360, 560, 400], radius=12, fill=(255,255,255,200))
    draw.text((420, 368), "RuStore 12+", fill=PRIMARY, font=font(18, bold=True))
    draw.rounded_rectangle([580, 360, 820, 400], radius=12, fill=WHITE)
    draw.text((600, 368), "Офлайн · Безопасно", fill=PRIMARY, font=font(18, bold=True))
    return img

def icon(size):
    img = Image.new("RGB", (size, size), PRIMARY)
    draw = ImageDraw.Draw(img)
    # inner white rounded square
    pad = size//8
    draw.rounded_rectangle([pad, pad, size-pad, size-pad], radius=size//6, fill=WHITE)
    # ₽ sign
    f = font(size//2, bold=True)
    draw.text((size//2 - size//6, size//2 - size//4), "₽", fill=PRIMARY, font=f)
    # small accent
    draw.rounded_rectangle([size//2 - size//12, size-pad+ size//20, size//2+ size//12, size-pad+ size//16], radius=size//40, fill=GOLD)
    # ensure no alpha
    return img.convert("RGB")

def main():
    # icons
    for s, name in [(1024, "icon-1024.png"), (512, "icon-512.png")]:
        im = icon(s)
        im.save(OUT / name, "PNG")
        print(f"saved {name} {im.size} {os.path.getsize(OUT/name)}")
    # feature
    fg = feature_graphic()
    fg.save(OUT / "feature-graphic-1024x500.png", "PNG")
    print(f"feature {fg.size} {os.path.getsize(OUT/'feature-graphic-1024x500.png')}")
    # screens
    funcs = [screen1_home, screen2_add, screen3_history, screen4_stats, screen5_budgets, screen6_settings]
    for i, fn in enumerate(funcs, 1):
        im = fn()
        path = SCREENS / f"{i:02d}-{['home','add','history','stats','budgets','settings'][i-1]}.png"
        im.save(path, "PNG", optimize=True)
        print(f"screen {path.name} {im.size} {os.path.getsize(path)}")
    # zip
    import zipfile
    zpath = OUT / "store-assets.zip"
    with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
        for p in sorted(OUT.rglob("*.png")):
            z.write(p, p.relative_to(OUT))
        # also include README
        readme = OUT / "README.md"
        readme.write_text("# Копилка — store assets\n\nСодержит иконки, баннер и 6 скриншотов 1080x1920 для RuStore.\n")
        z.write(readme, "README.md")
    print(f"zip {zpath} {os.path.getsize(zpath)} bytes, {len(z.namelist())} entries")
    # index.html gallery
    html = OUT / "index.html"
    html.write_text(f"""<!doctype html><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1">
<title>Копилка — ассеты RuStore</title><style>
body{{font-family:system-ui;margin:0;background:#f6f8f4;color:#0f1a14}}
header{{padding:24px 20px;background:#1b8c4a;color:#fff}}
h1{{margin:0 0 6px}}a{{color:#1b8c4a}}
.grid{{display:grid;gap:16px;grid-template-columns:repeat(auto-fill,minmax(280px,1fr));padding:20px}}
.card{{background:#fff;border-radius:16px;overflow:hidden;box-shadow:0 2px 12px rgba(0,0,0,.07)}}
.card img{{width:100%;display:block}}
.card .p{{padding:12px 14px}}
.zip{{margin:20px;background:#fff;border-radius:16px;padding:16px;display:flex;justify-content:space-between;align-items:center}}
.btn{{background:#1b8c4a;color:#fff;padding:12px 18px;border-radius:12px;text-decoration:none;font-weight:700}}
</style>
<header><h1>Копилка — Финансы офлайн</h1><div>Иконки, баннер 1024×500 и 6 скриншотов 1080×1920 · пакет com.copilka.app</div></header>
<div class=zip><div><b>store-assets.zip</b> — всё одним архивом (11 файлов)</div><a class=btn href="store-assets.zip" download>Скачать архив</a></div>
<div class=grid>
<a class=card href="icon-512.png" download><img src="icon-512.png"><div class=p>Иконка 512×512 — для RuStore</div></a>
<a class=card href="icon-1024.png" download><img src="icon-1024.png"><div class=p>Иконка 1024×1024</div></a>
<a class=card href="feature-graphic-1024x500.png" download><img src="feature-graphic-1024x500.png"><div class=p>Баннер 1024×500</div></a>
{"".join(f'<a class=card href="screens/{p.name}" download><img src="screens/{p.name}"><div class=p>{p.name}</div></a>' for p in sorted(SCREENS.glob("*.png")))}
</div>
""")
    print(f"index {html}")

if __name__ == "__main__":
    main()
