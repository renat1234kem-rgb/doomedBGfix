# pip install pillow  (или: python -m pip install pillow)
import zipfile, io, os
from PIL import Image

JAR = "libs/doomedmatu-0.4.7.3.jar"
OUT = "src/main/resources/assets/doomedbgfix/textures/item"
os.makedirs(OUT, exist_ok=True)

z = zipfile.ZipFile(JAR)
def load(p): return Image.open(io.BytesIO(z.read(p))).convert("RGBA")

full_blood = load("assets/doomedmatu/textures/item/bloodbaghuman.png")
full_saline = load("assets/doomedmatu/textures/item/saline.png")
empty      = load("assets/doomedmatu/textures/item/emptybloodbag.png")

def variant(full, fill):
    """Пакет целиком, но выше уровня жидкости — пустой пластик."""
    img = full.copy()
    bbox = full.getbbox()
    top, bottom = bbox[1], bbox[3] - 1
    body = bottom - top + 1
    level = bottom - int(round(fill * body)) + 1
    for y in range(top, level):
        for x in range(full.width):
            er, eg, eb, ea = empty.getpixel((x, y))
            if ea > 0:
                img.putpixel((x, y), (er, eg, eb, ea))
    # блик на поверхности жидкости
    for x in range(full.width):
        r, g, b, a = full.getpixel((x, min(level, bottom)))
        if a > 0 and level - 1 >= top:
            img.putpixel((x, level - 1), (min(255, r + 60), min(255, g + 60), min(255, b + 60), a))
    img.save(f"{OUT}/{full._bgfix_name}_{tag}.png") if False else None
    return img

for name, full in [("bloodbaghuman", full_blood), ("saline", full_saline)]:
    for fill, tag in [(0.75, "75"), (0.50, "50"), (0.25, "25")]:
        img = variant(full, fill)
        img.save(f"{OUT}/{name}_{tag}.png")
        print("saved", name, tag)
print("done ->", OUT)