# Two-phone checks — Spark edition

Publish firebase/database.rules.json and enable Email/Password before these checks.

1. Install the new APK on both phones. Create two separate email/password accounts.
2. Invite on phone A and join on B. Both show paired status. A third account cannot join the same used code.
3. Send text in both directions over separate networks, with Tailscale disconnected. Confirm messages arrive and server acknowledgement is shown accurately.
4. Disable the internet on A. Send text and add a savings entry. Close/reopen A, reconnect, and confirm each change appears once on B.
5. Edit the same event on both phones offline, reconnect both, and confirm their final state converges. Check that different savings contributions both survive.
6. Sign out and sign in as a different account on one phone. No previous room's chat, map position, plans, avatar, or widget data should appear.
7. Enable location sharing on A. Give foreground location permission, then Allow all the time. The sharing notification should appear. B can view A without enabling its own sharing.
8. Move outdoors with both screens on. Expect new positions around every 20 seconds when providers and networks allow it. Check accuracy and timestamp.
9. Lock A and use other apps for at least 15 minutes. B should receive updates while Android permits background operation. Repeat with battery optimization enabled and disabled; record actual delay and battery use.
10. Turn off A's internet or Location switch. B must show “Last known” after 60 seconds; it must not imply a frozen point is still live. Restore connectivity and check recovery.
11. Use the notification's Stop sharing action. A's position is removed from B once the database receives the stop. Repeat stopping offline and reconnecting.
12. Reboot A while sharing is enabled. Confirm consent-based restoration where Android permits. Force-stop A and verify reopening is required.
13. With A running and connected, send a message/priority alert from B; allow notification permission. Repeat after A sleeps/is stopped and document Spark's notification delay.
14. Set plans and check the daily widget. Add/subtract savings and set a goal. Save profile text and an avatar; verify on the other phone.
15. Confirm chat has no call, recording, attachment, save-file, or media-player controls. Android should no longer request microphone/camera permissions.

A single connected device cannot verify two-phone delivery or long-term battery behavior.


16. In version 3.1, verify the launcher says US. Open October 1, 2026: the calendar shows the 61st monthsary. Open September 1, 2026: it shows the 5th anniversary and 60th monthsary. Check matching milestones in the home agenda and daily widget when the corresponding date arrives.


## Version 3.3 acceptance checks

- Add all four photo-frame sizes, choose different pictures, test Fill frame / Whole photo, resize, move/delete originals, reboot, and tap to reconfigure. Removing one frame must not affect another.
- Enable sharing on both phones outdoors. Confirm distinct pins, Show both locations, and a plausible straight-line distance. Lose connectivity on one phone and check the stale-position label after 60 seconds.
- Stop sharing, enter a reason, and verify it appears on the partner map. Skip the reason and confirm sharing remains off. Start again and check the old reason disappears.
- Add a to-do at 23:05. Check/uncheck from Plans and Together without leaving the screen. Confirm both phones and today's widget agree after syncing.
- Add monthly January 31 and annual February 29 plans. Check shorter-month dates and separate checkmarks for each occurrence. Check timed reminders with US backgrounded; document actual Android delivery delay.
- Send the Kinda horny alert after confirming its text.
- Verify deposits need only an amount and blank/whitespace reasons prevent deductions. Set a goal with name, amount and target date; confirm partner display after syncing.
- Choose a portrait photo with EXIF rotation, adjust crop position/zoom, rotate an existing sideways avatar, save the profile, and verify the partner sees the chosen result.

Automated device tests use synthetic photos and an isolated widget host. The calendar regression test supplies sample entries to the real screen without changing an account or cloud data. Keep the Gradle APK-preservation setting enabled.


## Version 3.4 acceptance checks

1. Save partner profile names on both phones. Verify chat headings, incoming-message labels, connection status, map marker/legend, stop-sharing reasons, alerts and savings attribution reflect those names after syncing. Test a changed name and a missing/blank profile fallback.
2. In Plans, use Add to-do and Add reminder separately. Verify tasks have checkboxes and events do not; verify both groups on Together and the daily widget. Test times and repeats for both kinds.
3. Set a savings goal. End it below its target and confirm Failure in history. Create another goal, meet its target and close it to record Success. Verify snapshots retain target, saved balance and dates; the ledger/balance should not change when closing.
4. Verify history on the other phone, across reopening and offline/reconnect. Try closing the same goal on both phones: one history record should remain with the first server-accepted result. A stale goal edit must not reopen history.
5. Create a new goal after closing. Confirm the previous history remains, including after further deposits/withdrawals. If both phones create new goals offline, both should remain selectable as open goals.

A phone was not connected for the 3.4 build, so these UI and two-phone checks remain pending.


## Version 3.5 map acceptance checks

1. With both phones sharing fresh positions, choose Road and Walk. Compare mapped distances, route geometry and estimates. Check that the routing direction is You → partner and that the two modes use different transport profiles where appropriate.
2. Walk/ride with one phone. Confirm position timestamps change around the requested 20-second interval and route recalculation waits at least 60 seconds after movement of 30 m. Recenter should include the route and both current pins.
3. Stop either person's sharing or let their location become stale. The route must disappear, and no routing requests should continue until both locations are fresh again.
4. Disable internet; verify the unavailable/previous-estimate label. Background the app during a request and confirm cancellation and no ongoing route polling. Reopen and verify retry behavior.
5. Toggle full screen, pan/zoom, rotate the phone, recenter, exit with the button and with Android Back. Check map controls remain accessible and the app header/navigation return on exit.
6. Rapidly switch modes and leave/re-enter Map. A late response must not appear under the wrong mode, and requests must remain throttled. Confirm Direct stops routing requests.

No connected phone or configured emulator was available for 3.5 visual or two-phone tests.

## Version 3.6 location and alert acceptance checks

1. Upgrade both phones. With Precise location and sharing enabled, check fixes outdoors while standing and walking. Uploads should target 15 seconds, with early updates for major accuracy improvements. Compare the accuracy labels against the actual surroundings; sensor accuracy is an estimate.
2. Tap Refresh locations in normal and full-screen Map. Verify a new local measurement is requested, the button shows progress, and the partner's saved position retains its original age. Indoors or with Location disabled, the local request must time out after 20 seconds without giving an old point a new timestamp.
3. Disable your sharing and tap Refresh. It must remain off while the partner's saved position reloads. Stop the partner's sharing and confirm their pin stays hidden. Test offline refresh and an account change during a pending refresh.
4. Use approximate-only permission, then switch to Precise location through the map's settings button. Check that weak accuracy is visible. Stop sharing during a refresh: the progress indicator and sensor requests must stop.
5. Send each new normal alert and priority alert with the receiving app connected. Normal alerts use Messages and shared updates; PASUGAT and About to cry use Priority partner alerts. Existing priority alerts also send on one tap. Verify queued offline alerts appear in chat on reconnect; background notification delivery is still subject to Android and the existing Spark-only connection design.
6. Open Settings and confirm Developed by jwcaburog. On a small screen, verify map refresh, full-screen, recenter and sharing controls remain accessible.

No phone was connected during the 3.6 build; these device checks remain pending.

## Version 3.7 cycle tracker acceptance checks

1. Publish the 3.7 database rules and upgrade both phones. In Plans, open Cycle tracker. Confirm its sharing explanation and that a period logged on either phone appears on both after sync.
2. Log a start date, leave it ongoing, and later edit its end date. Check inclusive calendar markers, month boundaries, leap days and an anniversary on the same date. Future and overlapping actual periods must be rejected. Delete an incorrect record and check both calendars.
3. Select a past/today date and save flow, pain, symptoms, mood, energy, sleep and a note. Reopen, edit from the other phone, and verify the result. Blank pain must remain unlogged rather than become zero. Spotting must not start a period automatically. Future daily logs are disabled.
4. Enter three valid nonoverlapping period starts. Check the next-start estimate and history intervals. Change the usual length, disable estimates, and test widely varying intervals and overlapping records created concurrently while offline. Estimates must stay distinct from actual bleeding and never claim ovulation or safe days.
5. Go offline, edit a record, and verify the pending-sync label. Reconnect and confirm convergence. Open the same edit on both phones; a detected newer version should require reopening. Sign out and confirm the old room's records are not shown in another account/room.
6. Verify long notes/history scroll in dialogs and cards, calendar markers have accessible labels, and the UPMC guide link opens. Ensure cycle updates do not create symptom notifications or widget entries.

No phone was connected during 3.7 development; these device checks remain pending.


## Version 3.8: Redmi 13C refresh and background sharing

Use both phones running 3.8. Install updates over the existing app; never uninstall or clear app data. Use an emulator or dedicated device for automated instrumentation tests.

1. Verify Settings > About US displays 3.8 on both phones. Enable Precise location, Allow all the time, the in-app Android battery exemption, and Redmi Background autostart/No restrictions.
2. With a GPS fix outdoors, observe sensor timestamps arriving approximately every 15 seconds for five minutes. Tap Refresh several times, including with a poor/indoor fix. Only one request should run at a time; regular uploads continue and a rough measurement keeps its accuracy label.
3. Refresh then navigate away, rotate, enter/exit full screen and return. Repeat with a marker popup open and larger system font settings. Verify no crash, collapsed viewport, invalid zoom or accumulating overlays. Check adb logcat crash buffer on the affected phone if a failure repeats.
4. Leave sharing enabled, turn the screen off for at least 15 minutes, then swipe US from Recents and observe the other phone. Repeat on Wi-Fi and mobile data, including a connection loss/recovery. Check real timestamps; an unchanged marker alone does not mean updates stopped.
5. With sharing on, the US location notification should remain. Stop via its action; verify partner removal, sensor listener removal, and no US:LiveLocation wake lock. Repeat permission revocation and sign-out. Force-stop intentionally stops sharing until the user reopens US.
6. On a dedicated test device only, use Android's documented Doze test sequence, restore device-idle/battery settings afterward, and compare exempt/non-exempt behavior. Check memory and battery over a 30-minute map session and screen-off session; automated tests do not establish Redmi performance or real GPS delivery.


## Version 3.9: compact map layout

On the Redmi 13C, verify the map is visible below the compact sharing/route controls. Pan and zoom it, scroll the page outside the map, and use refresh, full screen and recenter. Repeat with large text enabled. Open the sliders icon for permissions/battery settings and the information icon for full location/route details. Check that a long partner name or stop reason does not push the map off-screen. Verify the live/last-known age and accuracy remain available. Physical layout verification is pending a connected device.

## Version 3.10: savings, drawings, photo pins and connectivity

1. Publish the updated database rules and upgrade both phones without uninstalling. Add money with no reason; deduct with a reason, confirming a blank reason disables Deduct. Set a goal using the three fields and close it after reaching its target. Verify history and totals on both phones.
2. Open Our drawing on both phones. Draw and Share from A; verify B's canvas and home-screen widget show it. Draw on B, then erase/Clear mine on A and Share. B's layer must remain visible on both phones and widgets. Repeat offline and reconnect. Close/reopen the editor without sharing and confirm the local draft survives. Rotate the editor while drawing.
3. Pin US drawing from its page; resize it. Tap to reopen the editor. With location sharing enabled and the app in the background, share from the other phone and check the widget updates. Force-stop behavior is not guaranteed; reopen US to catch up.
4. Confirm each map pin shows the correct profile photo. Change a profile photo, then check the map updates without recreating overlays on every GPS update. Test missing/invalid avatar fallback and full screen on Redmi 13C.
5. On affected Wi-Fi, compare the Settings status with actual Firebase connectivity. Retry connection; pending local entries must survive and sync once connected. Switch between Wi-Fi and mobile data and verify recovery. Do not assume a validated Android network means Firebase is reachable. Router/ISP IPv6 diagnosis remains separate.
