"""Generates every texture Atomic Sky ships (16x16 PNGs) with Pillow. Run from the mod root."""
import random, math
from PIL import Image, ImageDraw
random.seed(7)
R = 'src/main/resources/assets/atomicsky/textures'

def noise_fill(img, base, var, alpha=255):
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            n = random.randint(-var, var)
            px[x, y] = (max(0, min(255, base[0]+n)), max(0, min(255, base[1]+n)), max(0, min(255, base[2]+n)), alpha)

def metal(base=(96, 102, 108), var=10):
    img = Image.new('RGBA', (16, 16)); noise_fill(img, base, var)
    d = ImageDraw.Draw(img)
    d.line([(0, 0), (15, 0)], fill=(140, 146, 152, 255)); d.line([(0, 0), (0, 15)], fill=(140, 146, 152, 255))
    d.line([(0, 15), (15, 15)], fill=(60, 64, 68, 255)); d.line([(15, 0), (15, 15)], fill=(60, 64, 68, 255))
    for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        img.putpixel((x, y), (40, 42, 46, 255))
    return img

def trefoil(img, cx, cy, r, col=(250, 210, 30, 255), bg=(20, 20, 22, 255)):
    d = ImageDraw.Draw(img)
    d.ellipse([cx-r, cy-r, cx+r, cy+r], fill=bg)
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            dx, dy = x+0.5-cx, y+0.5-cy
            dist = math.hypot(dx, dy)
            if dist > r-0.6: continue
            ang = (math.degrees(math.atan2(dy, dx)) + 90) % 360
            if dist < 1.2: px[x, y] = col; continue
            if dist < 2.0: continue
            if (ang % 120) < 60: px[x, y] = col

# Bomb casing
side = metal(); d = ImageDraw.Draw(side)
d.rectangle([0, 6, 15, 9], fill=(220, 170, 20, 255)); d.rectangle([0, 7, 15, 8], fill=(240, 196, 40, 255))
for x in range(0, 16, 4): d.rectangle([x, 6, x+1, 9], fill=(30, 30, 32, 255))
side.save(f'{R}/block/nuke_side.png')
armed = side.copy(); d = ImageDraw.Draw(armed)
d.rectangle([0, 6, 15, 9], fill=(230, 40, 30, 255)); d.rectangle([0, 7, 15, 8], fill=(255, 90, 60, 255))
for x in range(0, 16, 4): d.rectangle([x, 6, x+1, 9], fill=(30, 30, 32, 255))
armed.save(f'{R}/block/nuke_side_armed.png')
front = metal(); trefoil(front, 8, 8, 6); front.save(f'{R}/block/nuke_front.png')
front_armed = metal((110, 60, 60)); trefoil(front_armed, 8, 8, 6, col=(255, 60, 40, 255)); 
ImageDraw.Draw(front_armed).rectangle([6, 13, 9, 14], fill=(255, 30, 30, 255)); front_armed.save(f'{R}/block/nuke_front_armed.png')
top = metal(); d = ImageDraw.Draw(top); d.rectangle([3, 3, 12, 12], outline=(60, 64, 68, 255)); d.rectangle([6, 6, 9, 9], fill=(200, 60, 40, 255)); d.rectangle([7, 7, 8, 8], fill=(255, 120, 90, 255)); top.save(f'{R}/block/nuke_top.png')
bottom = metal((80, 84, 90)); bottom.save(f'{R}/block/nuke_bottom.png')

# Fallout: pale grey ash with darker flecks and the faintest green cast
fo = Image.new('RGBA', (16, 16)); noise_fill(fo, (150, 150, 142), 10)
px = fo.load()
for _ in range(26):
    x, y = random.randrange(16), random.randrange(16); px[x, y] = (110, 112, 104, 255)
for _ in range(8):
    x, y = random.randrange(16), random.randrange(16); px[x, y] = (175, 182, 150, 255)
fo.save(f'{R}/block/fallout.png')

# Fissile core: dark steel sphere with a green glow
core = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(core)
d.ellipse([2, 2, 13, 13], fill=(70, 74, 80, 255)); d.ellipse([3, 3, 12, 12], fill=(90, 96, 104, 255))
d.ellipse([5, 5, 10, 10], fill=(90, 200, 60, 255)); d.ellipse([6, 6, 9, 9], fill=(190, 255, 120, 255))
for (x, y) in [(4, 7), (11, 8), (7, 4), (8, 11)]: core.putpixel((x, y), (120, 230, 80, 255))
core.save(f'{R}/item/fissile_core.png')

# Geiger counter: yellow handheld box with a dial and a probe
g = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(g)
d.rectangle([3, 5, 12, 14], fill=(226, 184, 40, 255), outline=(90, 70, 20, 255))
d.rectangle([5, 7, 10, 10], fill=(230, 236, 220, 255), outline=(60, 60, 60, 255))
d.line([(7, 10), (9, 8)], fill=(200, 30, 30, 255))
d.rectangle([6, 12, 9, 12], fill=(40, 40, 40, 255))
d.line([(12, 6), (15, 1)], fill=(80, 80, 85, 255), width=1); d.rectangle([13, 0, 15, 2], fill=(120, 120, 125, 255))
g.save(f'{R}/item/geiger_counter.png')

# Particles: a soft ash flake and a round ember
ash = Image.new('RGBA', (8, 8), (0, 0, 0, 0)); d = ImageDraw.Draw(ash)
d.ellipse([1, 1, 6, 6], fill=(255, 255, 255, 220)); d.point([(2, 1), (5, 6), (1, 5)], fill=(255, 255, 255, 120))
ash.save(f'{R}/particle/ash.png')
em = Image.new('RGBA', (8, 8), (0, 0, 0, 0)); d = ImageDraw.Draw(em)
d.ellipse([1, 1, 6, 6], fill=(255, 255, 255, 255)); d.ellipse([0, 0, 7, 7], outline=(255, 255, 255, 90))
em.save(f'{R}/particle/ember.png')
print('textures ok')
