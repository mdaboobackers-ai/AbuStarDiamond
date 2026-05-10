ABU STAR DIAMONDS - JEWELLERY ERP BILLING APP
Version: 3.0.0-r1

ABOUT THE APP
Abu Star Diamonds is an Android jewellery billing and customer ledger app for gold and diamond wholesalers. It manages customer records, invoices, gold-rate based billing, payments in cash or gold, melting/purity adjustment, invoice history, Google Drive backup, bulk import/export, hallmark OCR, analytics, and app security.

HOW THE APP WORKS
The app stores business data in an encrypted local database. Users log in with app PIN and, when enabled, mobile biometric security. Billing uses the current 24K rate, purity, weight, making charge, stone value, GST, cash payments, and gold payments to calculate item totals, equivalent grams, pending cash, and pending pure gold. Customer ledgers and invoice history are updated from invoices, payments, and melting records.

LOGIN AND SECURITY
- First-time setup creates an app PIN and invoice prefix.
- Biometric/mobile security can be enabled from Settings.
- If biometric login fails three times, the app falls back to the app PIN.
- Export with customer or billing data requires the login PIN.
- The app blocks screenshots through FLAG_SECURE.

DASHBOARD
- Shows business summary, market pulse, pending balances, quick actions, and navigation.
- Quick actions include new bill, customers, history, backup, analytics, melting, hallmark OCR, and settings.
- Company logo shown here comes from Settings. The app launch logo is a fixed app brand asset.

BILLING
- Select or search customer.
- Add jewellery items with description, gross weight, less weight, purity/karat, making charge, and stone value.
- Less weight can be calculated through a count/gram table drawer.
- Eq grams include purity, making, and stone value converted through the 24K rate.
- Payment supports cash and split gold payment by karat.
- Review & Seal step shows final invoice impact before saving.
- Saved invoices update customer ledger, payment records, and melting queue when old gold is received.

CUSTOMERS AND LEDGER
- Create, edit, search, and view customers.
- Customer cards show gold wallet and cash ledger.
- Customer ledger shows invoices, payments, gold received, melting/purity correction, and balance impact in a timeline.
- Pending invoice balances show cash due at current rate and pure gold equivalent.

INVOICE HISTORY
- View saved invoices with customer details and payment status.
- Invoice detail can show items, totals, payment history, and attachments where available.
- PDF generation uses company profile/logo when configured.

MELTING / PURITY CHECK
- Records old gold received from customers.
- Tracks raw weight, tested purity, final pure weight, attachments, notes, and linked invoice.
- Editing a melting record adjusts linked invoice/customer balance when purity changes.
- Delete requires confirmation and reverses the customer gold adjustment.

HALLMARK OCR
- Lets user capture/select hallmark images and extract visible text using ML Kit OCR.
- Version 3 supports both direct camera scan and gallery photo scan.
- Best result: clear photo, good light, hallmark centered, no blur, no reflection.

BACKUP AND RESTORE
- Backup uploads an encrypted app data file to the selected Google Drive app data folder.
- Restore downloads the latest app backup from the selected account and merges records.
- Version 3 backup format is portable across app reinstall/upgrade because it no longer depends only on a device-local keystore backup key.
- Auto daily backup is scheduled through WorkManager when enabled.
- Real-device verification is still required for Google account selection, Drive upload, restore, and latest-backup selection.

IMPORT / EXPORT
- Available from Settings > Bulk Import.
- Supports customer and billing import from XLSX/CSV or Google Sheet URL.
- Can export a blank template without PIN.
- Export with customer/billing details requires login PIN.
- Customer template includes customer creation fields such as name, phone, shop name, split address, city, state, pincode, GST, email, DOB, and anniversary.

SETTINGS
- Company profile: shop name, owner name, phone, address, GST, and logo.
- Rate settings: gold rate values used by billing and settlement.
- Theme: light/dark mode.
- Language: app language preference. Full Tamil coverage is still in progress.
- Security: biometric/mobile security enable/disable.
- Backup: auto backup status and last backup information.
- Bulk import/export access.
- App icon/profile sections where supported.

ANALYTICS
- Shows invoice count, sales, customer and balance summary, and business metrics.

CURRENT COMPLETION STATUS
- Core billing, customer ledger, melting, invoice history, import/export, biometric login, launch animation, ERP dashboard control cards, and APK build are working at compile/test level.
- Real-device QA cannot be completed from this workstation unless Android platform tools/ADB and a device/emulator are connected.

REAL-DEVICE QA CHECKLIST
1. Google Drive Backup
   - Open Backup & Restore.
   - Choose Google account.
   - Tap Backup Now.
   - Confirm status says backup completed and selected account is shown.
   - Reopen app and confirm last backup time remains.
2. Google Drive Restore
   - Use the same Google account.
   - Tap Restore from Drive.
   - Confirm customers, invoices, payments, melting records, and company profile are present.
   - Test after reinstall/upgrade because Version 3 backup uses portable encryption.
3. WhatsApp PDF Visual Check
   - Open an invoice from history.
   - Tap share.
   - Send PDF to WhatsApp.
   - Open the shared PDF and verify logo, header, bill-to, table columns, summary, balance, and melting adjustment do not overlap.
4. Hallmark OCR
   - Test Scan with Camera on a clear HUID/BIS/916/750 hallmark.
   - Test Pick Hallmark Photo from Gallery.
   - Verify detected text and summary are shown.
5. Tamil Translation
   - Switch language to Tamil in Settings.
   - Confirm high-traffic screens: dashboard, billing, customers, settings, backup, import/export, melting, OCR, and history.
   - User-entered text such as customer names and addresses should remain unchanged.
