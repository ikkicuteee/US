# US — Firebase Spark edition

A private Android app for two people: text chat, live location, shared plans and todos, a daily widget, profiles, and a savings ledger.

## Set up a fresh clone

1. Install JDK 17 or 21 and the Android SDK (platform 35). Set `JAVA_HOME` to your JDK.
2. Set `ANDROID_HOME` to your Android SDK folder, or create `android-app/local.properties` with your machine's `sdk.dir`.
3. In your Firebase project's settings, register an Android app with package name `com.example.localtwouserapp`. Download its `google-services.json` and place it in `android-app/app/`.
4. Create a Realtime Database before downloading the configuration, so it includes the database URL. Then complete the Firebase setup below and run the build command in **Build and checks**.

Personal Firebase configuration, signing keys, SDK paths, generated APKs/logs, and the retired server's private data are excluded from Git. Keep using the original signing key when updating an existing installation. A new computer's default debug key cannot update an app signed with a different key.

## Finish Firebase setup

The local Android configuration points to project `us-together-c34d5` and its Singapore Realtime Database. A fresh clone uses the Firebase project from the configuration you supply.

1. Keep the project on **Spark**. This version does not use Cloud Functions, Cloud Storage, App Hosting, or a call relay.
2. In Firebase Console → Authentication → Sign-in method, enable **Email/Password**.
3. Open **Realtime Database → Rules**. Replace the rules with the complete contents of [firebase/database.rules.json](firebase/database.rules.json), then click **Publish**.
4. Install `android-app/app/build/outputs/apk/debug/app-debug.apk` on both phones.
5. Create separate accounts. On one phone choose **Invite my partner**, then copy the invitation privately to the other person.
6. On the other phone choose **Join my partner** and paste the full code. The invitation expires after 24 hours and admits one partner. The owner can extend an unclaimed invitation.
7. On each phone, open **Map → Share my live location**. Grant location access and then **Allow all the time**. Keep internet and the phone's Location switch enabled.

There is no Tailscale/IP address setup. No computer needs to stay running after installation. The saved Firebase CLI credentials could not be refreshed; rules must be published through your console or a renewed CLI login.

## Drawing widget appearance (3.10.1)

The drawing widget now uses one rounded Mist-colored surface with no pink frame, padding or caption strip. Tap anywhere to draw. Its image keeps its original proportions when resized. Install US-3.10.1.apk over the existing app; no additional Firebase rule changes are needed beyond the 3.10 rules.

## Savings, drawing widget and photo pins (3.10)

- Savings has separate **Add money** and **Deduct** dialogs. Add asks for an amount; only Deduct requires a reason. Goals ask for name, amount and target date. Goal history is collapsed until opened.
- Open **Our drawing** on Together or in Settings. Draw with colors, erase your own strokes, then tap **Share**. Each person's transparent layer is stored separately. **Clear mine** clears your layer locally; tap Share to sync the change. Unfinished drafts stay on the phone. The other person's layer cannot be edited or erased by you.
- **Add drawing widget** pins a resizable home-screen board. Tap it to open the canvas; Android home-screen widgets do not support drawing gestures directly. Both layers appear after sync. The existing app connection updates the widget without periodic polling; Android can delay updates if the app process is stopped. Open US to catch up. Drawings are bounded 384x384 images in Realtime Database, with one current layer per person (no Storage bucket or image history).
- Map pins show each profile photo, with an initial when a photo is missing. Accuracy remains available as text.
- Connection status distinguishes no internet from Firebase being unreachable. **Settings > Retry connection** restarts the Firebase connection without clearing local changes. Changing to a validated network triggers one retry, followed by the SDK's usual backoff.

Install **US-3.10.apk** over the existing app on both phones. **Publish firebase/database.rules.json** in Firebase Console > Realtime Database > Rules > Publish. These rules are required for the new savings forms and drawing sync; this build does not deploy them automatically.

Wi-Fi diagnostics on the connected TECNO phone (2026-10-25): authenticated-free TLS/WebSocket reachability succeeded to the project database over IPv4; IPv6 connections timed out. Google sign-in endpoints showed the same IPv4/IPv6 difference. This points to a Wi-Fi/router/ISP IPv6 path issue. The app changes improve status and retry behavior; they do not repair the router or guarantee connectivity on that network.

## Map layout update (3.9)

The Map page now shows a compact sharing row, distance and route controls, and a 380 dp map viewport. The normal page scrolls on smaller screens. Refresh is an icon beside the distance; full screen and recenter stay on the map. Live/last-known age and accuracy remain visible below it.

Use the sliders icon beside the sharing switch for location permissions and screen-off battery access. The information icon opens accuracy and routing details, including the full reason when a partner stops sharing. Repeated setup guidance and the large heading have been removed from the main map screen. Install US-3.9.apk over the existing app; no Firebase rule changes are needed.

## Location reliability update (3.8)

Install 3.8 on **both phones**. Check **Settings > About US > Version**. Location delivery targets 15 seconds; GPS/network samples are requested every five seconds with no movement threshold. Manual refresh accepts the next fresh measurement (including a rough one with its accuracy shown), times out after 15 seconds, and does not postpone regular uploads. A recent fix is retried when Firebase reconnects; old measurements are not fabricated or relabeled as new GPS fixes. The partner refresh reloads their saved position and cannot remotely turn on their location sharing.

While sharing is enabled, a foreground service and a timed, renewed partial wake lock keep location processing available with the screen off. Stop sharing releases the lock and sensor listeners. This uses extra battery. In Map, select **Allow reliable screen-off sharing** to grant Android's battery exemption; enable **Precise location** and **Allow all the time** as well. On Redmi 13C, also enable **Settings > Apps > Permissions > Background autostart > US** and choose **No restrictions** in US app battery settings (wording varies). A swipe from Recents is supported by the service configuration, but Android/OEM process management can still interrupt it. Force-stop, phone shutdown, lost GPS and lost internet cannot be bypassed; 15 seconds is a target, not a delivery guarantee.

The map reuses five overlay objects, limits concurrent tile downloads, preserves its resources across temporary window detachment, cancels the partner-refresh wait when leaving the page, and uses bounded zoom padding. The normal map has a scrollable layout with a fixed-height viewport so controls cannot collapse it on smaller screens. No Firebase database rule changes are needed for this update; the 3.7 cycle rules remain required.

References: [Android Doze and battery exemptions](https://developer.android.com/training/monitoring-device-state/doze-standby), [Xiaomi Redmi 13C background autostart](https://www.mi.com/hk/support/faq/details/KA-500695/).

## Shared cycle tracker (3.7)

Open **Plans → Open cycle tracker**. Both paired accounts can view and edit the same tracker. Log actual period start/end dates, mark an ongoing period, and use the selected calendar day for flow, optional pain (0–10), symptoms, mood, energy, sleep and notes. History supports correction and deletion. Cycle details sync through the existing account/room outbox; pending changes are labeled. The feature does not send symptom details in notifications or add them to the home-screen agenda widget.

Filled pink circles mark logged period days; outlined circles mark an estimated next start. Existing anniversaries, to-dos and reminders remain visible. Estimates require three logged starts or an explicitly entered usual cycle length. Automatic estimates use the median of up to six recent start-to-start intervals and pause when those intervals differ by more than seven days, records overlap, or intervals exceed the supported range. This is a conservative product rule, not a medical diagnostic threshold. A missed estimate stays tied to the last actual log instead of inventing a new period. Estimates can be disabled.

Logging follows the [UPMC beginner's guide](https://share.upmc.com/2026/01/tracking-menstrual-cycle-beginners-guide/) and [Office on Women's Health tracking guidance](https://womenshealth.gov/menstrual-cycle/your-menstrual-cycle). Calendar estimates do not establish ovulation, pregnancy or safe days for contraception. Discuss pain or bleeding that disrupts daily activities with a clinician.

**Before using 3.7 on both phones, publish the updated [Firebase rules](firebase/database.rules.json).** They add the shared `cycle` item type with room-member access, date/key checks, allowed fields and value bounds. Older deployed rules reject cycle writes. Rules were tested locally; production publication still needs to happen in Firebase Console. No new paid service or Android permission is needed.

## Location and alerts (3.6, superseded by 3.8 above)

- While sharing is enabled, GPS/network fixes are requested every second and positions are shared about every **15 seconds**, with no movement threshold. A substantially better fix can be sent sooner. Selection favors accurate fixes from the last five seconds; older and invalid fixes are rejected, and an old fix is never republished with a new sensor timestamp.
- **Refresh locations** works in the normal and full-screen map. It waits up to 20 seconds for a new local fix and separately reloads the partner's latest saved position with a 12-second timeout. It preserves sharing consent and does not activate the partner's GPS remotely. Position age remains visible; cached coordinates are not relabeled as newly measured.
- Accuracy labels show each phone's reported uncertainty. Use **Precise location & battery settings → Permissions → Location** to enable Precise location for the best result. GPS reception, Android scheduling and internet availability still affect accuracy and delivery. More frequent sampling uses extra battery; this app cannot guarantee emergency tracking or alert delivery.
- Added normal alerts: **Angry**, **What're you doing rn**, **Head hurts**, **I'm tired**, **Cuddle**. These use the existing message notification channel. Added priority alerts: **PASUGAT** and **About to cry**. All priority alerts now send on one tap without a confirmation dialog.
- Settings includes **Developed by jwcaburog**. No Firebase rule changes or new permissions are required. Install the new version on both phones for 15-second sharing from both sides.
- Road/Walk routing retains its separate request limits; refreshing locations does not bypass route throttling or map tile caching.

## Relationship calendar

Version 3.1 includes a monthsary on the first of every month and an anniversary on September 1, counted from September 1, 2021. September 1 displays both milestones. They appear in the calendar, today's agenda, and the widget, and are calculated locally without database writes.

## Road distance and full-screen map (3.5)

Open Map and choose **Road** or **Walk** to calculate a route from your shared location to your partner's shared location. The map displays that route, its distance, a travel-time estimate, and when it was calculated. **Direct** shows straight-line distance and disables routing requests. Use the expand button for a full-screen map; the exit button or Android Back returns to the normal layout. Recenter fits both pins and the route.

- Location sharing now targets uploads every 15 seconds, including while moving or using other apps. Android, GPS, network conditions and battery settings can delay delivery.
- Routes recalculate after either endpoint moves at least 30 m, at most once per minute while the map is visible. Stationary routes are reused for up to 10 minutes. Errors retry after two minutes. Closing/backgrounding the map cancels routing work; opted-in location sharing continues independently.
- Both locations must be fresh. Stale/removed locations hide the route and pause calculations. Failed routing shows an unavailable message or the previous labeled estimate; it never calls straight-line distance a road distance.
- Road uses a driving profile; Walk uses a walking profile. Estimates do not include live traffic, actual bus/jeepney routes, public-transport schedules, stops, or your measured walking speed. Routes connect mapped paths/roads near the coordinates (within 200 m).
- Routing uses the public [FOSSGIS OSRM service](https://routing.openstreetmap.de/about.html) without a key or Firebase billing change. Selecting Road/Walk sends both coordinates to that provider, which logs route requests. The app keeps the route cache in memory only. The service has usage limits and may be unavailable. Attribution, a Fix the map link, an identifying user agent, and request throttling are included.
- No additional Android permissions, Firebase fields, or rule changes are required for 3.5. The 3.4 rules remain required for savings history and previous features.

## Goals, partner names, and separate plans (3.4)

- Savings has **Goal reached · move to history** when the current balance meets the target, and **End goal · move to history** otherwise. Confirming records Success or Failure, final balance, target amount, original target date, and closing time. It clears the active goal while retaining the balance and all transactions. A new goal gets a new ID and cannot replace an archived goal. Closing uses the balance currently known to that phone; later contributions do not rewrite the history snapshot. Closed history is immutable. If both phones close at once, the first closure accepted by Firebase is kept.
- Older goals without a target date must have it filled in through **Edit goal** before closing. No empty history entry is created.
- The partner's saved profile name replaces “your person” across chat, map, status, alerts, and other screens. **Your partner** is the fallback until a name is available.
- Plans has separate **Add to-do** and **Add reminder** buttons and separate lists. Only to-dos have completion checkboxes. Reminders are events for the selected day; both kinds still support a 24-hour time and monthly/annual repeats. Together and the daily widget use the same distinction. Existing entries keep their stored to-do/event type.
- Publish the updated database rules before using goal history. Upgrade both phones to 3.4, since earlier apps do not understand archived goals.

## Photo frame widgets

Version 3.3 includes 2×2, 3×2, 2×3, and 3×3 photo frames. Open **Settings → Photo frames → Add a photo widget**, or long-press the home screen → **Widgets → US**. Add a frame, tap it, choose a picture, select **Fill frame** or **Whole photo**, and save. Long-press to resize; exact cell sizes depend on the launcher.

Each frame has its own photo. Settings are stored in SQLite and a resized image copy is kept in private phone storage. Moving or deleting the original does not remove the saved frame image. No Firebase storage, new photo permission, or periodic background work is needed. Removing a frame removes its private copy. Uninstalling US or clearing its storage removes local frames.

## Plans, map, savings, and profiles (3.3)

- Plans support a 24-hour time and one-time, monthly, or annual recurrence. The Plans screen and Together agenda use the same observed entries. Recurring plans have separate checkmarks for each date. Month-end plans use the last day in shorter months; February 29 annual plans use February 28 in non-leap years.
- Timed plans schedule a local reminder on each phone after syncing, using one inexact Android alarm. Sleep/battery settings may delay it. Completed or deleted plans do not notify; remote changes need to sync first.
- The map fits both shared positions, draws a connecting line, and shows straight-line distance. Both phones must choose to share. Stale positions are labeled last known; distance is not a driving-route distance.
- Stopping location sharing is immediate. An optional text field shares the reason with the partner; skipping does not restart sharing.
- As of 3.10, only savings deductions require a reason. Goals require a name, amount and target date.
- Profile images apply EXIF orientation before cropping. Choose a picture, adjust zoom and horizontal/vertical position, rotate if needed, then use it and save the profile. Existing sideways avatars can be rotated using **Adjust / rotate current picture**. Choose the original again to recover areas removed by the previous crop.
- The Together page includes a **Kinda horny** priority alert. As of 3.6, priority alerts send without confirmation.

**Publish the updated [database rules](firebase/database.rules.json) before using 3.3 on both phones.** Earlier rules reject the new plan, location-status, and goal fields. Production rules have not been deployed from this workspace.

## Behavior

- **Chat:** text and priority alerts only. Calls, recording, attachments, media viewing, downloads, file servers, and their permissions/dependencies are removed. “Saved to our space” confirms a database write, not that the partner has read it.
- **Offline changes:** documents and an outbox are saved per account and shared space. Reconnecting retries transactions; UUIDs prevent duplicate savings contributions. Chat initially reads the newest 100 messages; “Earlier messages” fetches another page.
- **Live location:** GPS/network updates are sampled every second and shared about every 15 seconds, with no movement threshold. Only the latest selected position is uploaded. Stale sensor fixes are rejected; unsent location history is not persisted. The map shows accuracy and seconds since the latest server update, and labels positions older than 60 seconds “Last known.”
- **Background location:** uses an explicitly enabled location foreground service with a persistent Stop sharing action. Consent can survive process recreation/reboot. Android force-stop requires reopening the app; revoked permissions, disabled Location, offline networks, and battery management can interrupt updates. 15 seconds is a target interval, not a delivery guarantee; frequent GPS sampling uses more battery.
- **Notifications:** generated from incoming database changes while the app process is connected. There is no FCM sending backend. Android sleep/process termination may delay new-message alerts. The app does not run a fake connected-device service to keep chat alive.
- **Profiles:** text preferences and a cropped 192×192 JPEG avatar, capped at 24 KB before encoding. Avatars live in the private database and count toward database usage; no original image transfer or Cloud Storage is used.
- **Privacy:** rules restrict each room to its owner and one partner, prevent location spoofing and profile edits by the partner, prohibit invitation listing/reuse, and validate record sizes/types. Sign-out stops location sharing and hides that account's local cache.
- **Existing data:** archived server files on this computer remain available and are not automatically imported. During photo-widget testing, the original Gradle test cleanup uninstalled the phone app and cleared its local storage. The app was reinstalled; Firebase data was not deleted. Sign in to recover synced data. Phone-only data cannot be restored from this workspace. The test cleanup setting has since been corrected and checked with a local preservation marker.

## Build and checks

JDK 17 or 21 and an Android SDK are needed to build. Android Studio is optional.

```powershell
./android-app/gradlew.bat -p android-app :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Rules tests run only against a local emulator (port 9000), under the demo namespace `demo-us-spark`:

```powershell
# With Firebase CLI installed:
firebase emulators:start --only database --config firebase/firebase.json --project demo-us-spark
# In another terminal:
python firebase/test_rules.py
```

Connected tests now set `android.injected.androidTest.leaveApksInstalledAfterRun=true` in `android-app/gradle.properties` so Gradle retains the app and its data. Keep this setting enabled; use an emulator or a dedicated test phone for future instrumentation runs.

See [docs/DEVICE_TESTS.md](docs/DEVICE_TESTS.md) for the two-phone acceptance checks.

