# 📱 Abu Star Diamonds — Install Guide

## Option A: Build & Install (Developer)

### Prerequisites
1. Download and install [Android Studio](https://developer.android.com/studio) (free)
2. During setup, install **Android SDK 34**
3. Make sure Java 17 is installed

### Steps

```bash
# 1. Extract the zip
unzip GoldsmithBilling_AbuStarDiamonds.zip
cd GoldsmithBilling

# 2. Create local.properties (replace with your SDK path)
echo "sdk.dir=/Users/YourName/Library/Android/sdk" > local.properties
# Windows: sdk.dir=C\:\\Users\\YourName\\AppData\\Local\\Android\\Sdk

# 3. Run build script
chmod +x build_apk.sh
./build_apk.sh

# 4. APK will be at:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## Option B: Open in Android Studio (Easier)

1. Extract the zip
2. Open **Android Studio**
3. Click **File → Open** → select the `GoldsmithBilling` folder
4. Wait for Gradle sync to complete (~2-3 minutes first time)
5. Click **Build → Build Bundle(s)/APK(s) → Build APK(s)**
6. APK will be at `app/build/outputs/apk/debug/app-debug.apk`

---

## Option C: Install the APK on your Phone

### Transfer APK to phone:
- **USB cable**: Copy APK to phone storage
- **WhatsApp** (easiest): Send APK to yourself on WhatsApp, download on phone
- **Google Drive**: Upload APK, open on phone
- **Email**: Send to yourself, open attachment

### Enable Unknown Sources:
```
Android 8+: Settings → Apps → Special App Access → Install Unknown Apps
           → (your browser/file manager) → Allow from this source

Older Android: Settings → Security → Unknown Sources → Enable
```

### Install:
1. Open your file manager
2. Navigate to the APK file
3. Tap it
4. Tap **Install**
5. Tap **Open**

---

## First Launch

1. **Create PIN** — Enter a 4-digit PIN (you'll need this every time)
2. **Company Setup** — Go to Settings → Company Profile → Fill in Abu Star Diamonds details
3. **Gold Rate** — Set today's gold rate on the Dashboard
4. **Start Billing!** — Tap "NEW BILL" to create your first invoice

---

## App Features Quick Guide

| Where | What |
|-------|------|
| Dashboard | Gold rates, today's sales, quick actions |
| New Bill | 3-step: Customer → Items → Payment |
| Clients | All customers + gold/cash balances |
| History | All invoices with PDF/WhatsApp share |
| Melting | Old jewellery exchange records |
| Settings → App Icon | Choose: **Abu Star Logo** / Diamond / Custom image |
| Settings → Backup | Google Drive backup (daily auto + manual) |

---

## Troubleshooting

**"App not installed" error:**
- Make sure you enabled Unknown Sources
- Delete any previous version first: `adb uninstall com.goldsmith.billing`

**Gradle sync fails:**
- Check internet connection (downloads dependencies ~200MB first time)
- Try: `./gradlew clean` then rebuild

**"SDK not found" error:**
- Verify your SDK path in `local.properties`
- Android Studio SDK location: `File → Settings → Android SDK → Android SDK Location`

---

*Abu Star Diamonds Billing Suite v1.0 · Trust · Purity · Elegance*
