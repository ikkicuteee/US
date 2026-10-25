# Verification — Firebase Spark migration

Date: 2026-10-01 (Asia/Manila). App version 3.0, versionCode 4.

## Version 3.10.1 update - 2026-10-25

- Removed the drawing widget's pink background frame, 8 dp padding and caption strip. A dedicated rounded Mist background matches the bitmap, leaving no visible border. Image proportions and tap-to-open behavior are preserved. The photo-frame widget drawable is unchanged.
- Added drawing widget refresh on app startup, using the existing unchanged-content check.
- APK assembly and lint passed. Log: .tools/us-3.10.1-build.log. Installed versionCode 15 / versionName 3.10.1 using adb install -r on the connected TECNO phone; app data was preserved.
- Visually confirmed the existing home-screen widget updated without re-adding it and no longer shows the frame. Tapping it opened Our drawing successfully. Screenshot: .tools/us-widget-3.10.1.png. No new unit tests for this presentation-only change; no Firebase rule changes.
- APK: US-3.10.1.apk.

## Version 3.10 update - 2026-10-25

- Savings now uses separate deposit/deduction dialogs. Deposits require only an amount; deductions require a reason. Goals have name, amount and target date. Reason is no longer required to close a goal. History is collapsed, and an absent goal no longer shows an empty progress bar.
- Added a bounded 384x384 shared drawing canvas and resizable home-screen widget. Each author owns a separate transparent layer; eraser/Clear only affect that layer. Share updates the current layer in Realtime Database. Local drafts are isolated by account/room. Widget refresh skips unchanged image content and does not introduce a periodic timer. Both phones still need an active app connection to receive updates promptly.
- Added Firebase drawing ownership/size/field checks and reserved drawing IDs against collisions from other item types. Updated saving/goal validation to match the requested forms. All 150 rules checks passed in the local demo emulator; it was stopped afterward. Production rules were NOT deployed. Publish firebase/database.rules.json before using the new forms/drawing sync.
- Map markers now render bounded profile photo pins, falling back to an initial. Profiles regenerate pins only when changed; GPS updates reuse three overlays. Accuracy remains in labels.
- Added accurate Firebase-versus-internet status, a throttled retry button and one reconnect attempt on a validated network change. Existing pending edits are preserved. No global network or router settings were changed.
- APK assembly, 79 JVM/Robolectric tests and lint pass (0 errors, 53 warnings). Native rendering checks cover erasing/clearing while preserving the partner layer, rejecting malformed/oversized images, photo rendering/fallback, and canvas drawing staying inside its bounds. The physical phone revealed a canvas background overdraw issue, fixed before the final build. Logs: .tools/us-3.10-build-final.log and .tools/us-3.10-rules.log.
- Installed versionCode 14 / versionName 3.10 via adb install -r on the authorized TECNO CM8. No uninstall, data clear, sign-out, or test transactions. Visually checked the corrected drawing page and local profile pin; inspected deposit, deduction and goal form fields without saving. Screenshot artifacts remain local under .tools. The partner phone was not connected; shared drawing/widget delivery between two phones and Redmi performance remain unverified.
- Affected Wi-Fi: project database IPv4 TLS/WebSocket upgrade returned HTTP 101; IPv6 TCP connections timed out after seven seconds. Google sign-in/token hosts likewise responded over IPv4 and timed out over IPv6. The app remained disconnected from Firebase despite Android validating internet access. This supports a router/ISP IPv6-path problem, but the precise Firebase SDK failure was not captured. No claim that the Wi-Fi issue is fixed. Firebase retry cannot repair the network path.

## Version 3.9 update - 2026-10-17

- Removed the large Map heading and repeated inline sharing/setup guidance. A compact sharing row exposes settings through a sliders icon. Accuracy/routing explanations and full stop reasons are available in the information dialog; location age and accuracy remain visible.
- Refresh is an icon beside the distance. The normal layout has a 380 dp map and scrolls on small screens, so explanatory text cannot collapse the viewport. Full-screen and recenter controls remain on the map.
- APK assembly and all 72 regression tests passed. Lint: 0 errors, 49 warnings. Log: .tools/us-3.9-build.log. APK: US-3.9.apk, versionCode 13 / versionName 3.9.
- No phone was connected. The new layout has not been visually verified on the Redmi or installed on either phone. No location-service behavior, Firebase schema or rules were changed for 3.9.

## Version 3.8 update - 2026-10-17

- User reports app-only crashes on manual map refresh on a Redmi 13C (6/128 GB), and delayed/stopped background updates. No affected-device crash trace was available; the exact reported exception is not confirmed.
- Fixed a reproducible scheduling flaw: the previous manual-refresh branch held regular uploads while waiting up to 20 seconds for accuracy <=25 m. Refresh now accepts a fresh measured position, finishes within 15 seconds, and leaves the regular 15-second delivery deadline intact. Timer-driven delivery, five-second GPS/network sampling, independent provider registration and reconnect retries of only recent measurements are implemented.
- Map resources survive temporary view detachment and are released once when the map screen is disposed. Marker/circle/line instances are reused, zoom padding is bounded by the viewport, and normal-layout scrolling preserves map height. These address lifecycle and allocation risks; they do not establish the cause of the Redmi crash without its log.
- The opted-in foreground location service holds a timed, renewed partial wake lock, released on stop/error/destruction. The app offers the Android battery exemption and Redmi autostart guidance; force-stop and OEM restrictions remain outside the app's guarantees. Extra battery use during continuous sharing is expected.
- Automated regression coverage: delivery with poor accuracy, manual refresh without deadline shifts, stale-fix rejection, reconnect retry, refresh timeout/stop cleanup, 1,000 map updates retaining five overlays, temporary detach, tiny viewport, and safe updates after final release. Map/provider lifecycle tests use Robolectric Android API 28; this does not simulate actual GPS, Doze, Xiaomi process management or device memory pressure.
- No Firebase rule changes for 3.8. The 3.7 cycle rules and their prior 126 passing emulator checks remain applicable. No production deployment, phone installation, physical-device performance measurement or real background-location test was performed for this update.
- Final APK assembly and all 72 tests passed (58 previous, eight delivery/freshness tests, four map lifecycle tests, two provider/refresh lifecycle tests). Lint: 0 errors, 49 warnings, including the battery-exemption policy reminder. This request supports the explicitly enabled family location-sharing use case; exemption remains the phone owner's choice. Log: .tools/us-3.8-final-build.log. APK metadata verified: versionCode 12 / versionName 3.8. A copy is available at US-3.8.apk. Build uses a locally downloaded, SHA-256-verified Temurin JDK 21 because the previous VS Code Java runtime was removed by an extension update.

## Version 3.7 update — 2026-10-17

- Built versionCode 11 / versionName 3.7 with a shared cycle tracker in Plans. Both paired accounts can log/edit periods and daily flow, pain, symptoms, mood, energy, sleep and notes. Calendar markers distinguish recorded bleeding from the estimated next start.
- Estimate settings support a manual usual length or a median of up to six recorded intervals after three period starts. Overlaps, widely varying intervals, ongoing bleeding and estimates within logged bleeding suppress automatic dates as appropriate. No ovulation, fertility, pregnancy or contraceptive predictions are made. Missed estimates do not generate imaginary cycles.
- Uses the existing per-account/room cache and pending outbox. Cycle changes do not create health-detail notifications or widget entries. UI shows pending sync and detects edits received while a form is open. Firebase rules add a validated cycle type restricted to room members.
- All 58 JVM tests passed (16 cycle tests plus 42 previous tests). All 126 Firebase emulator rules checks passed, including shared cycle reads/edits and rejection of outsiders, malformed records and invalid bounds. APK assembly and lint passed. Logs: .tools/us-3.7-final-build.log and .tools/us-3.7-rules-tests.log.
- No phone was connected; calendar rendering and two-phone sync were not device-tested. The new APK has NOT been installed on a phone. Production Firebase rules have NOT been deployed; publish firebase/database.rules.json before using the new tracker. The local emulator was stopped after tests.
- Reference: https://share.upmc.com/2026/01/tracking-menstrual-cycle-beginners-guide/ . Cross-check: https://womenshealth.gov/menstrual-cycle/your-menstrual-cycle . The estimate algorithm is a product heuristic, not a validated medical prediction model.

## Version 3.6 update — 2026-10-16

- VersionCode 10 / versionName 3.6. Location sharing targets 15 seconds with 1-second GPS/network sampling, early accuracy improvements, stale-fix rejection and per-provider sample selection.
- Added manual local refresh (20-second deadline, requires a measurement after tapping), partner position reload (12-second deadline), reported-accuracy circles and labels, and a shortcut to Precise location/battery settings. Refresh does not enable sharing or change the partner's measurement timestamp.
- Added five normal alerts and PASUGAT / About to cry priority alerts. Removed all priority-alert confirmation dialogs. Settings credits jwcaburog.
- All 42 JVM tests passed, including 6 new location-selection tests. APK assembly and lint passed with 0 errors and 48 warnings. Logs are kept locally in .tools/us-3.6-build.log and .tools/us-3.6-final-build.log.
- No device was connected; actual GPS accuracy, background cadence, map layout and two-phone notification delivery remain unverified. No phone installation or Firebase rules deployment was performed. Existing rules and permissions are unchanged.
- Frequent GPS sampling increases battery use. Android/GPS/network delays and the existing Spark-only notification architecture still prevent any emergency-delivery guarantee. The previously diagnosed Wi-Fi IPv6 failure is not addressed by this change.

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

