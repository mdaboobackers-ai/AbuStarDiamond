# Abu Star Enhancements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve existing data across app upgrades and improve billing, invoice, payment, auth, backup, and jewellery workflow behavior.

**Architecture:** Move calculation and validation into reusable Kotlin utilities with unit coverage, then bind those results into Compose screens, Room entities, history views, PDF output, and sync payloads. Platform integrations remain behind focused helpers (`GoogleDriveHelper`, `BluetoothPrinter`, biometric prompt wrappers) so UI can report real status without duplicating platform logic.

**Tech Stack:** Kotlin, Android Compose, Room/SQLCipher, DataStore, Hilt, WorkManager, Android BiometricPrompt, Google Drive API, Android PDF Canvas, JUnit.

---

### Task 1: Data Preservation And Billing Core

**Files:**
- Modify: `app/src/main/java/com/goldsmith/billing/data/db/GoldsmithDatabase.kt`
- Modify: `app/src/main/java/com/goldsmith/billing/util/GoldCalc.kt`
- Modify: `app/src/test/java/com/goldsmith/billing/BillingLogicTest.kt`
- Modify: `app/src/main/java/com/goldsmith/billing/ui/billing/BillingScreen.kt`

- [ ] Write failing tests for net weight, pure gold, 91.6 conversion, split karat payments, invalid decimal input, and remaining gold equivalent.
- [ ] Replace destructive Room migration with explicit `MIGRATION_1_2` and safe migration scaffolding.
- [ ] Use `GoldCalc` for all billing and history calculations.
- [ ] Add field-level billing validation and block invoice save until mandatory fields are valid.

### Task 2: Invoice And History

**Files:**
- Modify: `app/src/main/java/com/goldsmith/billing/util/PdfGenerator.kt`
- Modify: `app/src/main/java/com/goldsmith/billing/ui/history/InvoiceHistoryScreen.kt`
- Modify: `app/src/main/java/com/goldsmith/billing/data/dao/Daos.kt`
- Modify: `app/src/main/java/com/goldsmith/billing/data/model/Models.kt`

- [ ] Redesign the invoice PDF with bordered header, bill-to block, item table with Eq.g, and payment/balance section.
- [ ] Bind customer details in strict order: Shop Name, Owner Name, Address, Phone Number.
- [ ] Show subtotal as total grams in app summaries where requested while keeping monetary totals separate.
- [ ] Add editable/deletable payment history with delete confirmation.

### Task 3: UX And App Shell

**Files:**
- Create/modify: `app/src/main/java/com/goldsmith/billing/ui/splash/SplashAnimation.kt`
- Modify: `app/src/main/java/com/goldsmith/billing/MainActivity.kt`
- Modify: `app/src/main/java/com/goldsmith/billing/ui/dashboard/DashboardScreen.kt`
- Modify: `app/src/main/java/com/goldsmith/billing/ui/theme/Theme.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-ta/strings.xml`

- [ ] Add elegant jewellery splash animation before auth/dashboard.
- [ ] Remove dashboard Market Pulse edit action and route rate edits through settings.
- [ ] Fix light/dark theme color switching.
- [ ] Add Tamil strings for newly visible text and replace hard-coded text where practical.

### Task 4: Auth, Backup, Printing, And Alerts

**Files:**
- Modify: `app/src/main/java/com/goldsmith/billing/ui/auth/AuthScreens.kt`
- Modify: `app/src/main/java/com/goldsmith/billing/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/goldsmith/billing/ui/backup/BackupScreen.kt`
- Modify: `app/src/main/java/com/goldsmith/billing/util/DataSyncManager.kt`
- Modify: `app/src/main/java/com/goldsmith/billing/util/GoogleDriveHelper.kt`
- Modify: `app/src/main/java/com/goldsmith/billing/util/BluetoothPrinter.kt`
- Create/modify: `app/src/main/java/com/goldsmith/billing/worker/BirthdayAlertWorker.kt`

- [ ] Require PIN or biometric confirmation before enabling or disabling biometric login.
- [ ] Show selected Google account and reuse it for backup/restore after first consent.
- [ ] Make Drive sync upload/download a full merge payload including payments and profile.
- [ ] Improve ESC/POS text output and printer error reporting.
- [ ] Add daily birthday/anniversary notification worker using existing customer dates.

### Task 5: Jewellery Workflows

**Files:**
- Modify: `app/src/main/java/com/goldsmith/billing/ui/billing/BillingScreen.kt`
- Modify: `app/src/main/java/com/goldsmith/billing/ui/melting/MeltingScreen.kt`
- Modify: `app/src/main/java/com/goldsmith/billing/data/model/Models.kt`
- Modify: `app/src/main/java/com/goldsmith/billing/data/dao/Daos.kt`

- [ ] Add 3-column expandable less-weight drawer and save/cancel behavior.
- [ ] Add multi-karat gold payment rows.
- [ ] Add attachments for invoice/payment/melting records.
- [ ] Make melting records editable and linked to invoices for raw metal, tested purity, adjusted gold, and carry-forward balance.
- [ ] Add confirmation before destructive deletes.

