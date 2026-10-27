# Google Play Store Assets - BatteryNtfy

All assets strictly adhere to Google Play Console specifications.

---

## 📱 1. App Icon (512 x 512 px)
- **Specification**: Exactly 512 x 512 px, 32-bit PNG / RGB PNG, under 1024 KB.
- **Default Ready-to-Upload**:
  - `app_icon_512x512.png`: Modern 3D glassmorphic battery with glowing emerald charge, amber lightning bolt, and cyan telemetry waves on a deep dark slate background.
- **Alternative Styles Available**:
  - `icon_3d_glassmorphic_512.png`: 3D glassmorphic style.
  - `icon_vector_clean_512.png`: Geometric, crisp vector design matching Android adaptive vector style.

---

## 🎨 2. Feature Graphic (1024 x 500 px)
- **Specification**: Exactly 1024 x 500 px, 24-bit PNG / RGB PNG (no alpha channel), under 15 MB.
- **Default Ready-to-Upload**:
  - `feature_graphic_1024x500.png`: Hybrid showcase combining high-resolution visual telemetry render with clean typography and core value propositions.
- **Alternative Styles Available**:
  - `feature_graphic_hybrid_showcase_1024x500.png`: Left side branding + bullet points; right side 3D interconnected device mesh.
  - `feature_graphic_clean_1024x500.png`: Minimalist modern dashboard with real live device mockups (Pixel phone charging 94%, tablet 76%, laptop low battery alarm 14%).
  - `feature_graphic_3d_render_1024x500.png`: Full-bleed 3D visual render.

---

## 🛠️ 3. Launcher Mipmaps (In-App)
The app launcher icons inside `app/src/main/res/` have also been updated across all screen densities:
- `mipmap-mdpi`: 48 x 48 px
- `mipmap-hdpi`: 72 x 72 px
- `mipmap-xhdpi`: 96 x 96 px
- `mipmap-xxhdpi`: 144 x 144 px
- `mipmap-xxxhdpi`: 192 x 192 px

---

## 🚀 How to Upload to Google Play Console
1. Log in to [Google Play Console](https://play.google.com/console).
2. Select **BatteryNtfy** from your app list.
3. In the left navigation menu, go to **Grow users** → **Store presence** → **Main store listing**.
4. Scroll to **Graphics**:
   - Under **App icon**, drag & drop `store_assets/app_icon_512x512.png`.
   - Under **Feature graphic**, drag & drop `store_assets/feature_graphic_1024x500.png`.
5. Click **Save** at the bottom right.
