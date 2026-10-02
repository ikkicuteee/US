# Verification — Firebase Spark migration

Date: 2026-10-01 (Asia/Manila). App version 3.0, versionCode 4.

## Version 3.5 update — 2026-10-02

- Built versionCode 9 / versionName 3.5. APK: android-app/app/build/outputs/apk/debug/app-debug.apk.
- Added optional Road and Walk routing through public FOSSGIS OSRM endpoints, route geometry, distance, estimated duration, and calculation age. Direct mode keeps straight-line distance and disables routing requests.
- Requested live-location cadence remains 20 seconds. Route calculations run only while Map is visible with two fresh positions; after 30 m movement, at most once per minute. Stationary results are reused for 10 minutes; failures back off for two minutes. HTTP has a 15-second overall deadline, cancellation/disconnect, bounded response parsing, and an app-wide minimum request gap.
- Added full-screen map mode, floating routing controls, recenter/fit route, exit button and Android Back behavior. The app header and bottom navigation are hidden while expanded. Route/map state remains in the same screen composition.
- Added provider/OSM attribution, Fix the map link, and disclosure that choosing routing sends both coordinates to the provider. Estimates exclude live traffic and public-transport schedules. No route history is stored on disk.
- All 36 JVM tests passed (7 routing tests plus 29 previous tests). Final APK assembly and lint passed: 0 errors, 48 warnings. Logs: us-3.5-build.log and us-3.5-final-build.log.
- Public Berlin sample calls returned valid car and foot routes. Later public Manila sample requests and a curl/IPv4 retry timed out during connection/TLS, so live availability was intermittent from this workspace. Log: us-3.5-routing-smoke.log. No actual phone coordinates were sent during checks.
- No connected phone or configured emulator was available. Full-screen rendering, actual phone network calls, movement tracking and two-phone delivery remain unverified; see DEVICE_TESTS.md. This build was NOT installed on a phone.
- No Firebase rule/schema or Android permission changes were needed for 3.5. The 3.4 rules still need to be published if not already applied.
- Provider/API references: https://routing.openstreetmap.de/about.html and https://project-osrm.org/docs/v5.24.0/api/ . The provider's linked full German usage page was access-denied; the official about page exposes the attribution, user-agent, one-request-per-second and light-usage requirements used here.

## Version 3.4 update — 2026-10-02

- Built versionCode 8 / versionName 3.4. APK: android-app/app/build/outputs/apk/debug/app-debug.apk.
- Added goal closure/history, preserving the target, final balance, target date, closing time and success/failure outcome. Closing does not alter transactions or the current balance. New goals use new IDs; legacy shared-goal records remain usable. Older goals missing required date/reason details must be edited before closing.
- History is immutable under the new rules. Closed server records take precedence over stale offline edits; simultaneous closures keep the first result accepted by Firebase. Multiple goals created concurrently remain selectable rather than disappearing from the UI.
- Replaced all built-in “your person” text under app/src/main with the partner profile name, falling back to Your partner. Profile sync refreshes connection status and observed UI labels.
- Added separate Add to-do and Add reminder buttons, distinct lists, and matching Together/widget behavior. Only tasks have checkmarks; timed notifications and recurring dates remain available for both types.
- Final assembleDebug, 29 JVM tests, and lint passed. Lint: 0 errors, 48 warnings. All 87 local Firebase rules checks passed, including history validation, preventing reopen/overwrite/deletion, and creating subsequent goals. The database emulator was stopped afterward.
- No device was connected when checked. Version 3.4 was NOT installed or tested on a phone; the last installed version remains 3.3 from the prior update. Screen and two-phone checks remain in DEVICE_TESTS.md. No production data was changed.
- Publish updated firebase/database.rules.json and upgrade both phones before using history. Production rules were NOT deployed from this workspace.
- Logs: us-3.4-build.log, us-3.4-rules-tests.log.

## Version 3.3 update — 2026-10-02

- Installed versionCode 7 / versionName 3.3 on TECNO CM8 (Android 16) with `adb install -r`. The final APK is `android-app/app/build/outputs/apk/debug/app-debug.apk`.
- Added four local photo widgets (2×2, 3×2, 2×3, 3×3), per-widget SQLite settings and private image copies, crop/fit display, reconfiguration and resize support, without periodic updates.
- Added both-location map framing, distinct pins, connecting line, straight-line distance, stale-position labels, and optional stop-sharing reasons. Sharing stops before asking for a reason.
- Plans now render from observed entries, support 24-hour times and once/monthly/annual recurrence, and store completion per occurrence. Timed plans use one inexact local alarm, preserving delayed same-day alarms across unrelated sync updates.
- Savings require reasons; goals require a reason and target date. Added the Kinda horny priority alert.
- Profile decoding handles EXIF orientation and offers zoom, horizontal/vertical positioning, and manual rotation before saving a bounded avatar.
- Final build, 22 JVM tests, and lint passed: 0 errors, 48 warnings. Eight device tests passed (six widget storage/rendering, two profile orientation/crop). The calendar UI regression test was skipped because the phone was locked; its earlier attempt could not inspect a checkbox. The calendar update calculation is covered by JVM tests. Actual unlocked screen behavior and two-phone delivery still require the checks in DEVICE_TESTS.md.
- All 74 Firebase rules emulator checks passed. The emulator was stopped afterward. New rules are required for recurrence/time fields, goal reasons, and location status. Production rules were NOT deployed; publish firebase/database.rules.json through Firebase Console.
- Logs: us-3.3-final-checks.log, us-3.3-rules-tests.log. The final connected tests left the app and a controlled local preservation marker intact, verified again after installation.

### Test cleanup incident

The first photo-widget instrumentation runs used Gradle's default `uninstall_after_test: true`, which removed US and cleared its phone-local app data. This was an agent mistake. The APK was reinstalled, but no local backup was available and phone-only data was not recovered. No Firebase data was deleted; synced data should return after signing into the same account. Computer-side archives remain available.

`android.injected.androidTest.leaveApksInstalledAfterRun=true` now prevents that cleanup. Subsequent runs preserved the app and a controlled local marker. Use an emulator or a dedicated device for future instrumentation testing. Earlier statements below about preserving on-phone data apply only to those earlier installs, before this incident.

## Version 3.1 update ? 2026-10-01

- Renamed launcher, widget heading, in-app heading, and location notification to US.
- Added calculated monthsaries every first and anniversaries every September 1, based on September 1, 2021. Anniversary dates include both celebrations. These appear in the calendar, home agenda, and widget without cloud writes.
- Build and 14 JVM tests passed (9 existing plus 5 recurrence tests). Lint: 0 errors, 24 warnings.
- Installed versionCode 5 / versionName 3.1 on TECNO CM8 using adb install -r, preserving app data. Launched successfully; no AndroidRuntime errors were reported in the checked log.
- The phone shows the connection/sign-in screen, so calendar rendering behind pairing was not checked on the device. Date calculations were verified by the JVM tests.
- Build log: monthsary-build.log. Database rules were unchanged by this update.

## Original version 3.0 checks

- Debug APK assembled: android-app/app/build/outputs/apk/debug/app-debug.apk (21,808,029 bytes, about 21.8 MB).
- Nine JVM tests pass: money precision, merge convergence, savings deduplication, deletion tombstones, invitation format, rejecting invalid/stale location fixes, and outbox acknowledgement races.
- Android lint: 0 errors, 24 warnings. See firebase-build.log and the Gradle lint report.
- 54 Realtime Database rules checks pass in a local database emulator. Coverage includes two-account membership, simultaneous invite claims, expiration, third-party denial, spoofed authors/positions, record validation, stale edits, profile size limits, and removing location.
- Merged manifest checked: no camera, microphone, audio, full-screen-call, or connected-device foreground-service permissions.
- Requested location cadence: 20 seconds, zero distance threshold. Map freshness changes to “Last known” at 60 seconds. These are requested/label intervals; Android can delay delivery.

## Remaining external setup and checks

- Firebase configuration file is present for us-together-c34d5, using the Singapore database.
- Production database rules have NOT been deployed. The saved Firebase CLI credentials could not be refreshed. Publish firebase/database.rules.json through Firebase Console.
- Email/Password sign-in must be enabled in Firebase Authentication; this console setting was not verified.
- No production test accounts or cloud data were created.
- USB device was disconnected at installation time. The final APK was not installed or smoke-tested on a phone.
- Actual two-phone sign-in, pairing, syncing, background GPS behavior, notifications during sleep, and battery use remain unverified. Use DEVICE_TESTS.md.
- Previous on-phone/server data is preserved but is not automatically imported into Firebase. Accounts start a fresh shared space.

## Logs

- firebase-build.log
- firebase-rules-tests.log
- database-emulator.log (local test namespace only)

The local database emulator was stopped after the tests. This app needs no running PC server, Tailscale, paid Firebase services, or call relay.

