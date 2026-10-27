import math
import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUTPUT_DIR = "store_assets"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# System Fonts
FONT_BOLD = "C:/Windows/Fonts/segoeuib.ttf" if os.path.exists("C:/Windows/Fonts/segoeuib.ttf") else "C:/Windows/Fonts/arialbd.ttf"
FONT_REGULAR = "C:/Windows/Fonts/segoeui.ttf" if os.path.exists("C:/Windows/Fonts/segoeui.ttf") else "C:/Windows/Fonts/arial.ttf"
FONT_SEMIBOLD = "C:/Windows/Fonts/segoeuisl.ttf" if os.path.exists("C:/Windows/Fonts/segoeuisl.ttf") else FONT_REGULAR

# Colors
BG_DARK = (15, 23, 42)        # #0F172A Slate 900
BG_CARD = (30, 41, 59)        # #1E293B Slate 800
BG_CARD_BORDER = (51, 65, 85) # #334155 Slate 700
EMERALD = (16, 185, 129)      # #10B981
EMERALD_LIGHT = (52, 211, 153)# #34D399
CYAN = (56, 189, 248)         # #38BDF8 Sky 400
AMBER = (245, 158, 11)        # #F59E0B Amber 500
RED_ALERT = (239, 68, 68)     # #EF4444 Red 500
TEXT_WHITE = (248, 250, 252)  # #F8FAFC
TEXT_MUTED = (148, 163, 184)  # #94A3B8 Slate 400


def create_linear_gradient(size, start_color, end_color, horizontal=False):
    width, height = size
    base = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(base)
    steps = width if horizontal else height
    for i in range(steps):
        ratio = i / float(steps - 1)
        r = int(start_color[0] + (end_color[0] - start_color[0]) * ratio)
        g = int(start_color[1] + (end_color[1] - start_color[1]) * ratio)
        b = int(start_color[2] + (end_color[2] - start_color[2]) * ratio)
        a = 255
        if horizontal:
            draw.line([(i, 0), (i, height)], fill=(r, g, b, a))
        else:
            draw.line([(0, i), (width, i)], fill=(r, g, b, a))
    return base


def draw_glow_circle(img, center, radius, color, max_alpha=80):
    glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(glow)
    cx, cy = center
    steps = 40
    for i in range(steps, 0, -1):
        r = radius * (i / steps)
        alpha = int(max_alpha * (1.0 - (i / steps) ** 1.5))
        c = (color[0], color[1], color[2], alpha)
        gdraw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=c)
    glow = glow.filter(ImageFilter.GaussianBlur(radius * 0.2))
    img.alpha_composite(glow)


def draw_rounded_rect(draw, bbox, radius, fill=None, outline=None, width=1):
    x0, y0, x1, y1 = bbox
    draw.rounded_rectangle([x0, y0, x1, y1], radius=radius, fill=fill, outline=outline, width=width)


# -------------------------------------------------------------
# 1. GENERATE CLEAN VECTOR PLAY STORE APP ICON (512x512)
# -------------------------------------------------------------
def build_vector_icon_512():
    size = (512, 512)
    # Background: diagonal dark slate gradient
    img = create_linear_gradient(size, (15, 23, 42), (30, 41, 59))
    
    # Ambient glows
    draw_glow_circle(img, (256, 260), 220, EMERALD, max_alpha=55)
    draw_glow_circle(img, (360, 240), 160, CYAN, max_alpha=40)
    
    # Grid / circuit subtle accents
    draw = ImageDraw.Draw(img)
    
    # Rounded container squircle plate for depth
    plate_bbox = (56, 56, 456, 456)
    draw_rounded_rect(draw, plate_bbox, 96, fill=(19, 31, 53, 220), outline=(56, 189, 248, 70), width=3)
    
    # Outer Battery Frame
    bx0, by0, bx1, by1 = 166, 140, 316, 400
    # Battery top cap
    cap_x0, cap_y0, cap_x1, cap_y1 = 216, 116, 266, 140
    draw_rounded_rect(draw, (cap_x0, cap_y0, cap_x1, cap_y1), radius=8, fill=(148, 163, 184, 255))
    
    # Outer shell
    draw_rounded_rect(draw, (bx0, by0, bx1, by1), radius=32, outline=(56, 189, 248, 255), width=14)
    
    # Battery inner liquid level (gradient green)
    # Fill from y=240 to y=384
    liquid_bbox = (182, 240, 300, 384)
    draw_rounded_rect(draw, liquid_bbox, radius=18, fill=(16, 185, 129, 230))
    # Liquid highlight top
    draw_rounded_rect(draw, (186, 244, 296, 310), radius=14, fill=(52, 211, 153, 200))
    
    # Central Charging Bolt (Amber Gold)
    # Bolt coords scaled
    bolt_points = [
        (254, 175),  # top right peak
        (210, 260),  # mid indent
        (244, 260),  # inner neck
        (234, 345),  # bottom sharp point
        (286, 248),  # right kick
        (252, 248),  # inner kick
    ]
    draw.polygon(bolt_points, fill=(245, 158, 11, 255), outline=(255, 251, 235, 255))
    
    # Wireless Signal Waves (Right side radiating out)
    # Wave 1
    w1_bbox = (300, 195, 380, 345)
    draw.arc(w1_bbox, start=-65, end=65, fill=(56, 189, 248, 255), width=11)
    # Wave 2
    w2_bbox = (330, 165, 430, 375)
    draw.arc(w2_bbox, start=-65, end=65, fill=(16, 185, 129, 255), width=11)
    
    # Left side receiving indicator dots / pulses
    draw.ellipse([98, 266, 114, 282], fill=(56, 189, 248, 220))
    draw.ellipse([124, 268, 136, 280], fill=(56, 189, 248, 160))

    # Save to file
    out_path = os.path.join(OUTPUT_DIR, "icon_vector_clean_512.png")
    img.convert("RGB").save(out_path, "PNG", optimize=True)
    print(f"Created {out_path}")
    return img


# -------------------------------------------------------------
# 2. GENERATE COMPREHENSIVE PLAY STORE FEATURE GRAPHIC (1024x500)
# -------------------------------------------------------------
def build_feature_graphic_1024x500():
    width, height = 1024, 500
    base = create_linear_gradient((width, height), (10, 15, 30), (20, 30, 50), horizontal=True)
    
    # Ambient multi-color glows
    draw_glow_circle(base, (180, 240), 280, CYAN, max_alpha=50)
    draw_glow_circle(base, (780, 220), 320, EMERALD, max_alpha=60)
    draw_glow_circle(base, (520, 380), 200, (99, 102, 241), max_alpha=35)
    
    draw = ImageDraw.Draw(base)
    
    # Grid lines / tech background aesthetic
    for x in range(0, width, 64):
        draw.line([(x, 0), (x, height)], fill=(255, 255, 255, 6), width=1)
    for y in range(0, height, 64):
        draw.line([(0, y), (width, y)], fill=(255, 255, 255, 6), width=1)
        
    # Fonts
    title_font = ImageFont.truetype(FONT_BOLD, 54)
    subtitle_font = ImageFont.truetype(FONT_REGULAR, 22)
    tag_font = ImageFont.truetype(FONT_BOLD, 14)
    card_title_font = ImageFont.truetype(FONT_BOLD, 17)
    card_sub_font = ImageFont.truetype(FONT_REGULAR, 14)
    percent_font = ImageFont.truetype(FONT_BOLD, 28)
    
    # ------------------ LEFT SIDE: BRANDING & HEADLINE ------------------
    # Category Pill Tag
    draw_rounded_rect(draw, (56, 68, 260, 100), radius=16, fill=(16, 185, 129, 35), outline=(16, 185, 129, 180), width=1)
    draw.text((72, 75), "⚡ REAL-TIME REMOTE TELEMETRY", fill=(52, 211, 153, 255), font=tag_font)
    
    # App Title: BatteryNtfy
    draw.text((54, 116), "Battery", fill=TEXT_WHITE, font=title_font)
    draw.text((250, 116), "Ntfy", fill=CYAN, font=title_font)
    
    # Subtitle / Value Proposition
    draw.text((56, 186), "Monitor All Your Devices from Anywhere", fill=(226, 232, 240, 255), font=subtitle_font)
    
    # Bullet points / feature pills
    features = [
        ("📡", "Instant push alerts via ntfy.sh & Apps Script"),
        ("⚡", "Configurable low & full battery triggers"),
        ("📱", "Monitor phones, tablets, laptops & servers"),
        ("🔒", "Private, lightweight & low battery impact"),
    ]
    
    fy = 240
    for icon, text in features:
        # Icon circle
        draw.ellipse([56, fy + 2, 78, fy + 24], fill=(30, 41, 59, 220), outline=(56, 189, 248, 120))
        draw.text((61, fy + 3), icon, fill=TEXT_WHITE, font=card_sub_font)
        draw.text((92, fy + 3), text, fill=TEXT_MUTED, font=card_sub_font)
        fy += 38

    # Bottom Protocol Badge
    draw_rounded_rect(draw, (56, 420, 420, 458), radius=12, fill=(15, 23, 42, 220), outline=(51, 65, 85, 255), width=1)
    draw.text((70, 428), "STATUS: SYNC ACTIVE  •  MULTI-DEVICE READY", fill=(100, 116, 139), font=tag_font)

    # ------------------ RIGHT SIDE: INTERACTIVE DEVICE CARDS ------------------
    
    # Card 1: Smartphone (Upper right, 94% Charging)
    c1_x0, c1_y0, c1_x1, c1_y1 = 540, 68, 960, 172
    draw_rounded_rect(draw, (c1_x0, c1_y0, c1_x1, c1_y1), radius=20, fill=(30, 41, 59, 240), outline=(51, 65, 85, 255), width=2)
    # Glow edge
    draw_rounded_rect(draw, (c1_x0, c1_y0, c1_x0 + 8, c1_y1), radius=4, fill=(16, 185, 129, 255))
    # Phone Icon
    draw_rounded_rect(draw, (c1_x0 + 26, c1_y0 + 24, c1_x0 + 64, c1_y1 - 24), radius=8, outline=(56, 189, 248, 255), width=2)
    draw.line([(c1_x0 + 38, c1_y0 + 30), (c1_x0 + 52, c1_y0 + 30)], fill=(56, 189, 248, 255), width=2)
    draw.ellipse([c1_x0 + 43, c1_y1 - 32, c1_x0 + 47, c1_y1 - 28], fill=(56, 189, 248, 255))
    # Info
    draw.text((c1_x0 + 82, c1_y0 + 28), "Pixel 9 Pro  (Local Device)", fill=TEXT_WHITE, font=card_title_font)
    draw.text((c1_x0 + 82, c1_y0 + 56), "⚡ AC Fast Charging • 38°C", fill=EMERALD_LIGHT, font=card_sub_font)
    # Battery Gauge
    draw.text((c1_x1 - 100, c1_y0 + 26), "94%", fill=EMERALD_LIGHT, font=percent_font)
    # Progress bar
    draw_rounded_rect(draw, (c1_x0 + 82, c1_y1 - 26, c1_x1 - 30, c1_y1 - 18), radius=4, fill=(15, 23, 42, 255))
    draw_rounded_rect(draw, (c1_x0 + 82, c1_y1 - 26, c1_x0 + 82 + int((c1_x1 - c1_x0 - 112) * 0.94), c1_y1 - 18), radius=4, fill=EMERALD)

    # Card 2: Remote Tablet (Middle right, 76% Discharging)
    c2_x0, c2_y0, c2_x1, c2_y1 = 570, 196, 990, 300
    draw_rounded_rect(draw, (c2_x0, c2_y0, c2_x1, c2_y1), radius=20, fill=(30, 41, 59, 240), outline=(51, 65, 85, 255), width=2)
    # Cyan accent line
    draw_rounded_rect(draw, (c2_x0, c2_y0, c2_x0 + 8, c2_y1), radius=4, fill=(56, 189, 248, 255))
    # Tablet Icon
    draw_rounded_rect(draw, (c2_x0 + 24, c2_y0 + 24, c2_x0 + 68, c2_y1 - 24), radius=6, outline=(148, 163, 184, 255), width=2)
    draw.ellipse([c2_x0 + 44, c2_y1 - 32, c2_x0 + 48, c2_y1 - 28], fill=(148, 163, 184, 255))
    # Info
    draw.text((c2_x0 + 86, c2_y0 + 28), "Living Room Tablet", fill=TEXT_WHITE, font=card_title_font)
    draw.text((c2_x0 + 86, c2_y0 + 56), "📡 Synced 2m ago via ntfy topic", fill=TEXT_MUTED, font=card_sub_font)
    # Gauge
    draw.text((c2_x1 - 100, c2_y0 + 26), "76%", fill=CYAN, font=percent_font)
    # Progress bar
    draw_rounded_rect(draw, (c2_x0 + 86, c2_y1 - 26, c2_x1 - 30, c2_y1 - 18), radius=4, fill=(15, 23, 42, 255))
    draw_rounded_rect(draw, (c2_x0 + 86, c2_y1 - 26, c2_x0 + 86 + int((c2_x1 - c2_x0 - 116) * 0.76), c2_y1 - 18), radius=4, fill=CYAN)

    # Card 3: Laptop / Workstation (Bottom right, 14% LOW BATTERY ALARM)
    c3_x0, c3_y0, c3_x1, c3_y1 = 520, 324, 950, 428
    draw_rounded_rect(draw, (c3_x0, c3_y0, c3_x1, c3_y1), radius=20, fill=(30, 41, 59, 245), outline=(239, 68, 68, 180), width=2)
    # Red accent line
    draw_rounded_rect(draw, (c3_x0, c3_y0, c3_x0 + 8, c3_y1), radius=4, fill=(239, 68, 68, 255))
    # Laptop Icon
    draw_rounded_rect(draw, (c3_x0 + 26, c3_y0 + 26, c3_x0 + 64, c3_y1 - 36), radius=4, outline=(239, 68, 68, 255), width=2)
    draw.line([(c3_x0 + 20, c3_y1 - 32), (c3_x0 + 70, c3_y1 - 32)], fill=(239, 68, 68, 255), width=3)
    # Info
    draw.text((c3_x0 + 82, c3_y0 + 28), "Workstation ThinkPad", fill=TEXT_WHITE, font=card_title_font)
    draw.text((c3_x0 + 82, c3_y0 + 56), "⚠️ LOW BATTERY ALERT TRIGGERED", fill=(248, 113, 113), font=card_sub_font)
    # Gauge
    draw.text((c3_x1 - 100, c3_y0 + 26), "14%", fill=RED_ALERT, font=percent_font)
    # Progress bar
    draw_rounded_rect(draw, (c3_x0 + 82, c3_y1 - 26, c3_x1 - 30, c3_y1 - 18), radius=4, fill=(15, 23, 42, 255))
    draw_rounded_rect(draw, (c3_x0 + 82, c3_y1 - 26, c3_x0 + 82 + int((c3_x1 - c3_x0 - 112) * 0.14), c3_y1 - 18), radius=4, fill=RED_ALERT)

    # Save to file
    out_path = os.path.join(OUTPUT_DIR, "feature_graphic_clean_1024x500.png")
    base.convert("RGB").save(out_path, "PNG", optimize=True)
    print(f"Created {out_path}")
    return base


# -------------------------------------------------------------
# 3. GENERATE FULL MIPMAP ICONS FOR ANDROID PROJECT
# -------------------------------------------------------------
def generate_mipmap_icons(source_icon_img):
    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    res_base = "app/src/main/res"
    for folder, px in densities.items():
        folder_path = os.path.join(res_base, folder)
        if os.path.exists(folder_path):
            resized = source_icon_img.resize((px, px), Image.Resampling.LANCZOS)
            # save webp / png
            webp_path = os.path.join(folder_path, "ic_launcher.webp")
            resized.save(webp_path, "WEBP", quality=95)
            round_path = os.path.join(folder_path, "ic_launcher_round.webp")
            resized.save(round_path, "WEBP", quality=95)
            print(f"Updated {webp_path} ({px}x{px})")


if __name__ == "__main__":
    icon = build_vector_icon_512()
    feature = build_feature_graphic_1024x500()
    generate_mipmap_icons(icon)
    print("All store assets generated successfully.")
