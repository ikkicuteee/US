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
- Verify blank/whitespace savings reasons prevent both add/subtract. Set a goal with a reason and target date; confirm partner display after syncing.
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
