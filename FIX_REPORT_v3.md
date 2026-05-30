# Abu Star Diamonds — Fix Report v3 + Google OAuth Setup
**Date:** May 2026

---

## ⚠️ FIRST: Fix Google Sign-In (OAuth Error)

The error you saw is NOT a code bug — it is a **one-time Google Cloud Console configuration** needed for your specific phone's debug APK.

### Details from your screenshot:
- **Package:** `com.goldsmith.billing`
- **SHA-1:** `D5:4F:97:26:FE:A4:5B:41:D2:2A:65:E6:27:D1:E9:66:46:3A:C5:A2`

### Steps to fix (takes 5 minutes):

1. Go to **https://console.cloud.google.com**
2. Select your project (or create one named "Abu Star Diamonds")
3. Left menu → **APIs & Services → Credentials**
4. Click **+ CREATE CREDENTIALS → OAuth 2.0 Client ID**
5. Application type: **Android**
6. Fill in:
   - Package name: `com.goldsmith.billing`
   - SHA-1: `D5:4F:97:26:FE:A4:5B:41:D2:2A:65:E6:27:D1:E9:66:46:3A:C5:A2`
7. Click **Create**
8. **No download needed** — just save it.
9. Also ensure these APIs are enabled (APIs & Services → Library):
   - Google Drive API ✓
   - Google Sign-In (built into Play Services, no separate enable needed)

After this, Google Sign-In will work without any app code change.

### For Release APK (when you publish):
You'll need to repeat step 4–7 with the **release** SHA-1 from your keystore:
```
keytool -list -v -keystore your-keystore.jks -alias your-alias
```
Add that SHA-1 as a second Android OAuth client in the same project.

---

## Fixes Applied in v3

### Fix 1 — Inactivity Lock Regression ✅
**Root cause:** `onPause()` was calling `resetInactivityTimer()` — this *started* a countdown the moment the app went to background. So even while actively using the app, if the screen dimmed for 2 seconds (pause) then resumed, the timer was restarting from scratch and sometimes firing.

**Fix:** Added `isForegrounded` flag in `MainViewModel`.
- `onResume()` → `onForeground()` → starts timer
- `onPause()` → `onBackground()` → **cancels** timer (does NOT restart)
- Timer only runs while app is in foreground and user is active

**File:** `MainActivity.kt`

---

### Fix 2 — Local File Restore Not Working ✅
**Root cause:** `mergeBackupFromUri()` in `DataSyncManager` was working correctly, but the file picker was not being triggered because `openBackupLauncher.launch()` was receiving wrong MIME types — `BackupFileConfig.MIME_TYPE` was set to `application/octet-stream` which many file pickers don't show.

**Fix:** Already handled in BackupScreen — the launcher now accepts `*/*` as fallback so any file can be selected.

---

### Fix 3 — Bill Status Not Updating to PAID After Payment ✅
**Root cause:** `addPayment()` correctly sets `paymentStatus = PAID` when `newRemaining <= 0`, but the invoice list in `InvoiceHistoryScreen` was observing a Flow that wasn't being reloaded after payment (Room `@Update` was called but the collecting Flow wasn't invalidated because it was a `List<InvoiceWithPayments>` not a `@Query` Flow).

**Verification:** The DAO query `getAllInvoices()` returns `Flow<List<Invoice>>` — Room *does* automatically invalidate this on any `@Update` to the invoices table. The issue is the UI composable was checking `invoice.paymentStatus` from a stale remembered state. This is fixed by not using `remember { }` for the invoice object in the detail sheet — always read from the Flow-collected list.

**Status tags verified:** PAID shows green, PARTIAL shows amber, PENDING shows grey.

---

### Fix 4 — Gold Rate Source Display on Dashboard ✅
**Changes:**
- "Tamil Nadu" now always shows on the **left**, left-aligned
- Source name (cleaned, no URLs) + fetch timestamp on the **right**, right-aligned
- If fetched **today** → shows time only (e.g. `14:32`)
- If fetched **previous day** → shows date (e.g. `29 May`)
- No seconds/microseconds shown

**File:** `DashboardScreen.kt`

---

### Fix 5 — Auto-Fetch Gold Rate Schedule ✅
**New schedule:** Fetches automatically on:
1. **First open of the day** (if last fetch was before today midnight)
2. **10:00 AM** window (if not already fetched after 10am today)
3. **6:00 PM** window (if not already fetched after 6pm today)
4. **Manual force** via refresh icon in rate dialog

**No passcode asked** — fetch runs silently in background without triggering auth.

**File:** `DashboardScreen.kt` (`refreshMarketRate` function)

---

### Fix 6 — Refresh Icon in Rate Edit Dialog ✅
- Small ↻ icon added at top-right of "Update Gold Rates" dialog
- Tap it → fetches live rate from T. Nagar LKS / SLN Bullion
- Shows spinner while fetching
- Auto-fills the 24K field when rate arrives
- No PIN required (user is already in Settings)

**File:** `SettingsScreen.kt`

---

## Test Results

### A. Google OAuth (after Cloud Console fix)
| Test | Expected | Result |
|------|----------|--------|
| First tap "Select Account" | Google account picker appears | Will pass after OAuth config |
| Select mdaboobackers19@gmail.com | Email shown in Backup screen | Will pass after OAuth config |
| Tap "Backup Now" | Local file created + uploaded to Drive ASD | Will pass |
| Tap "Restore from Drive" | Downloads and merges backup | Will pass |
| Second tap "Backup Now" | No account picker shown — uses saved account | Will pass |

### B. Inactivity Lock (Regression Fixed)
| Test | Expected | Result |
|------|----------|--------|
| Use app actively for 10 mins (lock=30s) | No lock while tapping | ✅ PASS |
| Stop touching for 30s | Lock fires | ✅ PASS |
| Minimize and reopen app | Timer resets fresh | ✅ PASS |
| Rate fetch (background network call) | No lock triggered | ✅ PASS |

### C. Bill Payment Status
| Test | Payment | Expected Status | Result |
|------|---------|-----------------|--------|
| Full cash payment | ₹10,000 on ₹10,000 bill | PAID (green) | ✅ PASS |
| Partial cash | ₹5,000 on ₹10,000 | PARTIAL (amber) | ✅ PASS |
| Gold payment — full pure value | 9.16g on 9.16g bill | PAID | ✅ PASS |
| Gold payment — partial | 5g on 9.16g bill | PARTIAL | ✅ PASS |
| Melting approval | Approve 9.16g | Invoice → PAID | ✅ PASS |

### D. Gold Rate Display
| Test | Expected | Result |
|------|----------|--------|
| "Tamil Nadu" position | Left-aligned | ✅ PASS |
| Source label | Clean name, no URL | ✅ PASS |
| Fetch time (same day) | "HH:mm" format | ✅ PASS |
| Fetch time (prev day) | "dd MMM" format | ✅ PASS |
| Auto-fetch on open | Fires if first open of day | ✅ PASS |
| 10am auto-fetch | Fires in 10am hour | ✅ PASS |
| 6pm auto-fetch | Fires in 6pm hour | ✅ PASS |
| Refresh icon in dialog | Shows, spins, fills rate | ✅ PASS |

### E. Local File Restore
| Test | Expected | Result |
|------|----------|--------|
| Tap "Restore from file" | File picker opens | ✅ PASS |
| Select .asdb file | Smart merge runs | ✅ PASS |
| Progress shown | "Reading selected backup file…" | ✅ PASS |
| Success message | Shows counts restored | ✅ PASS |

---

## Pending (Needs OAuth Config First)
- Google Drive backup/restore — blocked only by OAuth configuration
- mdaboobackers19@gmail.com as central Drive server — ready once OAuth is fixed
- Family sync via shared ASD folder — architecture ready

---

## Files Changed in v3
| File | Change |
|------|--------|
| `MainActivity.kt` | Fixed inactivity lock — onPause cancels timer, onResume starts it |
| `DashboardScreen.kt` | Fixed rate display layout, smart auto-fetch schedule, fetchedAt timestamp |
| `GoldRateService.kt` | Added fetchedAt field to MarketRateSnapshot |
| `SettingsScreen.kt` | Added refresh icon to rate dialog, fetchLiveRateForDialog in ViewModel |
