# 💎 Loupe & Ledger — Goldsmith Billing System

A fully offline, encrypted Android application for goldsmith jewellery billing.  
**Kotlin + Jetpack Compose · Room + SQLCipher · Hilt · WorkManager**

---

## 📱 Features

| Module | Features |
|--------|---------|
| 🔐 **Auth** | 4-digit PIN (PBKDF2 hashed + salted), Biometric (optional), Auto-lock 30s, Android Keystore |
| 🏠 **Dashboard** | Live gold rates (24K/22K/18K), Daily sales summary, Quick actions |
| 💰 **Billing** | 3-step wizard: Customer → Items → Payment, Multi-method payments |
| 👥 **Customers** | Search, create, edit, balance tracking (gold + cash) |
| 📜 **History** | Invoice list, filters by date/status, PDF export, WhatsApp share |
| 🔥 **Melting** | Old jewellery exchange tracking, image attachments |
| ⚙️ **Settings** | Company profile, language (EN/தமிழ்), theme, gold rates, GST |
| ☁️ **Backup** | Daily auto-backup via WorkManager, AES-256-GCM encrypted, Google Drive |

---

## 🎨 Design System — Aura Lumina

- **Glassmorphism** — rgba(255,255,255,0.05) surfaces, backdrop blur
- **Gold accent** — #D4AF37 / #F2CA50 primary, black text on gold buttons
- **Deep Charcoal** — #131313 background
- **Inter** typeface — Light display, SemiBold labels
- **24dp radius** cards, 16dp buttons, 12dp inputs

---

## 🏗️ Project Structure

```
app/src/main/java/com/goldsmith/billing/
├── GoldsmithApp.kt              # Hilt + WorkManager init
├── MainActivity.kt              # FLAG_SECURE, inactivity lock
├── data/
│   ├── db/GoldsmithDatabase.kt  # Room + SQLCipher encrypted DB
│   ├── dao/Daos.kt              # All DAO interfaces
│   ├── model/Models.kt          # Room entities + TypeConverters
│   └── repository/SettingsRepository.kt  # DataStore preferences
├── security/
│   └── KeystoreManager.kt       # Android Keystore, PIN PBKDF2, AES backup encryption
├── di/AppModule.kt              # Hilt providers
├── navigation/Navigation.kt     # NavHost + Screen sealed class
├── worker/DailyBackupWorker.kt  # WorkManager auto-backup
└── ui/
    ├── theme/Theme.kt           # Aura Lumina color system + typography
    ├── components/Components.kt # GlassCard, GoldButton, PinDots, etc.
    ├── auth/AuthScreens.kt      # PIN setup + verify + biometric
    ├── dashboard/DashboardScreen.kt
    ├── billing/BillingScreen.kt # 3-step billing wizard
    ├── customer/CustomerScreens.kt
    ├── history/InvoiceHistoryScreen.kt
    ├── melting/MeltingScreen.kt
    ├── settings/SettingsScreen.kt
    └── backup/BackupScreen.kt
```

---

## 🔒 Security Architecture

```
PIN Entry ──► PBKDF2WithHmacSHA256 (100,000 iterations) ──► EncryptedSharedPreferences
                                                                      ↓
Database ──► SQLCipher AES-256 ──► Key from Android Keystore ──► Encrypted .db file
                                                                      ↓
Backup ──► AES-256-GCM ──► Keystore-backed key ──► Encrypted .enc ──► Google Drive
```

- **FLAG_SECURE** — prevents screenshots in release
- **ProGuard** obfuscation in release builds
- **No logs** in production (ProGuard rules)
- **Auto-lock** after 30 seconds inactivity

---

## 🛠️ Build Instructions

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34
- Minimum device: Android 8.0 (API 26)

### Step 1 — Open Project
```bash
# Open in Android Studio
File → Open → Select /GoldsmithBilling folder
```

### Step 2 — Sync Gradle
Android Studio will auto-sync. If not:
```
File → Sync Project with Gradle Files
```

### Step 3 — Google Drive Setup (Optional for backup)
1. Go to [Google Cloud Console](https://console.cloud.google.com)
2. Create a new project → Enable **Google Drive API**
3. Create OAuth 2.0 credentials → Download `google-services.json`
4. Place `google-services.json` in `/app/` folder
5. Add plugin to `app/build.gradle.kts`:
   ```kotlin
   alias(libs.plugins.google.services)
   ```

### Step 4 — Build Debug APK
```bash
# Via Android Studio
Build → Build Bundle(s) / APK(s) → Build APK(s)

# Via terminal
cd GoldsmithBilling
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

### Step 5 — Build Release APK
```bash
# 1. Generate keystore
keytool -genkey -v -keystore goldsmith.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias goldsmith

# 2. Add to app/build.gradle.kts signing config:
signingConfigs {
    create("release") {
        storeFile = file("goldsmith.jks")
        storePassword = "YOUR_STORE_PASSWORD"
        keyAlias = "goldsmith"
        keyPassword = "YOUR_KEY_PASSWORD"
    }
}

# 3. Build
./gradlew assembleRelease
# APK at: app/build/outputs/apk/release/app-release.apk
```

### Step 6 — Install on Device
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 📦 Dependencies Summary

| Library | Purpose |
|---------|---------|
| Jetpack Compose BOM 2024.06 | Modern declarative UI |
| Room 2.6.1 | Local offline database |
| SQLCipher 4.5.4 | Database encryption |
| Hilt 2.51.1 | Dependency injection |
| DataStore 1.1.1 | Encrypted preferences |
| WorkManager 2.9.0 | Background auto-backup |
| Security Crypto 1.1.0 | EncryptedSharedPreferences |
| Biometric 1.2.0 | Fingerprint authentication |
| Coil 2.6.0 | Image loading (profile/melting photos) |
| Google Play Auth | Google Drive sign-in |
| Navigation Compose 2.7.7 | Screen navigation |

---

## 📐 Gold Rate Calculation

```
22K Rate = 24K Rate × 0.916  (91.6%)
18K Rate = 24K Rate × 0.75   (75.0%)

Net Weight  = Gross Weight - Less Weight (stone/dust deduction)
Fine Gold   = Net Weight × (Purity% / 100)
Item Amount = (Net Weight × Gold Rate × Purity%) + (Net Weight × Making Charge/g) + Stone Value
GST         = Sub Total × 3% (configurable)
Total       = Sub Total + GST
```

---

## 🌐 Language Support

- **English** — `res/values/strings.xml`
- **Tamil** (தமிழ்) — `res/values-ta/strings.xml`

Switch in Settings → Appearance → Language

---

## 📋 First Launch Flow

1. App opens → **Set PIN** screen
2. Enter 4-digit PIN twice to confirm
3. Goes to **Dashboard**
4. Set up Company Profile in Settings
5. Enter today's gold rate
6. Start creating bills!

---

## 🤝 WhatsApp Share

Invoice detail screen → Share button → Auto-opens WhatsApp with customer's number pre-filled and PDF attached.

---

*Built with ❤️ for Tamil Nadu goldsmiths*
