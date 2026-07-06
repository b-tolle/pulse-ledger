# Pulse Ledger

Local-first Android app that reads your **Fitbit Air** (via the Google Health
app) and **Omron BP** data out of **Health Connect**, stores it in a local Room
database, and adds the calculations the stock apps don't: AHA-style 7-day AM/PM
averages, MAP, pulse pressure, HRV baseline deviation, and sleep→next-morning-BP
correlation. No cloud APIs; data never leaves the phone unless you enable the
optional encrypted Drive backup.

## Before you build

1. **Fitbit side**: in the Google Health app → Connections → Partner apps →
   "Sync your favorite health apps" → allow Google Health to **write** to
   Health Connect (steps, heart rate, sleep, etc.).
2. **Omron side**: check Health Connect (Settings → Health Connect → App
   permissions) for Omron Connect. If it can write Blood pressure, enable it.
   If it can't on your phone, use the CSV export from Omron's History tab and
   the `CsvImporter` fallback (or manual entry).
3. Open this folder in Android Studio (Ladybug or newer), let Gradle sync.
   You may need to add the Gradle wrapper: `gradle wrapper` or let Studio do it.

## Architecture

- `data/HealthConnectManager.kt` — permission set + paged reads for BP, HR,
  resting HR, HRV (RMSSD), sleep sessions, steps, SpO2
- `data/db/` — Room: raw BP readings + one derived `DailySummary` row per day
- `domain/Calculations.kt` — MAP, pulse pressure, ACC/AHA category, weekly
  AM/PM averages, HRV baseline delta, sleep↔systolic Pearson correlation
- `sync/SyncWorker.kt` — WorkManager job every 4h pulling the last 30 days
- `backup/DriveBackup.kt` — stub for encrypted appDataFolder backup (needs an
  OAuth client ID in Google Cloud console; see comments)
- `ui/` — Compose skeleton; port the visual design from the React mockup

## Notes

- Health Connect rate-limits foreground/background reads; the paged reader and
  4-hour sync cadence stay well inside them.
- Background reads on Android 14+ use `READ_HEALTH_DATA_IN_BACKGROUND`; on
  older versions sync happens when the app is opened.
- Categories/insights are informational, not medical advice.

## New in this drop

- `domain/ChargeEngine.kt` — Body-Battery-style Charge score (5–100): sleep
  charges it; stress (HR over resting while idle) and exertion drain it
- `meds/` — medication list, dose log, **refill forecasting** (days-of-supply
  → "order by" date + reminder), and before/after BP effect summaries to bring
  to your prescriber (the app never advises dose changes itself)
- `meds/PrivateEntry` — private log (cannabis, psilocybin, …) gated behind
  BiometricPrompt, reachable only via Settings, correlated in Trends as
  anonymous "tagged days"
- `life/TogetherDetector.kt` — partner presence via BLE. **Read the header
  comment**: bond your phones once, then detection is reliable; her
  accessories work as proxies; fused with co-arrival geofences + calendar
- `life/GeofenceManager.kt` — Places, visits, together sessions
- `env/EnvSampler.kt` — barometer, ambient light, evening screen minutes
- `import/TakeoutImporter.kt` — **backfills years of phone steps** from a
  Google Takeout export (Google Fit and Fitbit/Google Health JSON shapes)

## Getting your historical steps (do this soon)

Google Fit is being phased out during 2026. Export now:
1. takeout.google.com → Deselect all → tick **Fit** (and **Fitbit** if you
   ever used one) → Export. You'll get JSONs of your full step history.
2. Unzip on your phone, open Pulse Ledger → Settings → Import Takeout,
   point it at the folder. Years of steps land in the local DB.

## Installing on your phone (sideload)

1. Install **Android Studio** on your computer, open this folder, let it sync.
2. On the phone: Settings → About phone → tap **Build number** 7× to enable
   Developer options → enable **USB debugging**.
3. Plug in the phone, pick it in Studio's device dropdown, press **Run ▶**.
   (Or Build → Generate APKs, copy the APK over, and tap to install.)
4. First launch: grant Health Connect permissions, then in system settings
   give **Usage access** (screen time), **Location → Allow all the time**
   (geofences), and **Nearby devices** (Bluetooth).
5. Works **today, before the Fitbit Air arrives**: Omron BP via Health
   Connect or CSV, phone step history via Takeout import, live phone steps,
   barometer, screen time, places. The Air just starts pouring in HR/HRV/
   sleep/SpO2/skin-temp when it shows up.
