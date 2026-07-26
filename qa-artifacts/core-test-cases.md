# TestLoop core test cases

Scope: listing, commitment, install detection, reminders/notifications, and cross-device behavior.

Out of scope: chat UI, messaging, encryption, realtime chat, account onboarding, and the onboarding/safe-area cases already completed.

## Current Supabase fixtures

Both existing users were preserved. Active app listings/campaigns are owned by `selva`:

| App | Package | Owned-app ID | Campaign ID |
|---|---|---|---|
| CRED | `com.dreamplug.androidapp` | `303d089b-59bd-4b60-8282-e02490219744` | `1185091d-cfd5-4d5c-b3cc-9344dc8c5542` |
| Google Pay | `com.google.android.apps.nbu.paisa.user` | `b4b94f02-0eca-4eb1-aa7e-9fd571de1e23` | `9a796572-998e-4bf3-954e-c20fe5831cb5` |
| Agoda | `com.agoda.mobile.consumer` | `447c46bb-d6f1-443b-8c5a-3e088cb8fd2b` | `1876908d-7215-46b5-b148-fe777093bd78` |
| Zomato | `com.application.zomato` | `2c17f7c9-ce0b-40e6-b516-99e107446142` | `0cef3046-73d4-4ebc-8d08-40659f41e378` |
| Amazon India | `in.amazon.mShop.android.shopping` | `c6dc1e13-5ea6-4c15-ab23-4bbcbfd46367` | `c4dbc91a-3df9-4c6b-970e-d4bae0e0eb52` |
| WhatsApp | `com.whatsapp` | `4addb148-9abe-4987-a380-27bc7cd7f133` | `dc44d084-9bfe-4bd8-bac0-a2be22d89e2b` |

Ignore archived historical QA rows. Do not delete or modify profiles/auth users. WhatsApp is only an installed-package/listing control; do not test chat.

The intended device matrix is:

| App | Samsung | Pixel |
|---|---|---|
| CRED, Google Pay | available per supplied device setup; verify package IDs before execution | available per supplied device setup; verify package IDs before execution |
| Agoda, Zomato | absent | installed |
| Amazon, WhatsApp | installed | installed |

Record actual `adb shell pm path <package>` results before relying on a matrix row.

## Preflight

1. Record device serial, model, Android version, app version, timezone, network state, notification permission, and Usage Access state.
2. Confirm both users remain present and identify which user is active on each phone.
3. Query the six active listings and campaigns above; capture counts and IDs.
4. Confirm no commitments, install states, reminders, or test notifications from earlier runs will contaminate the baseline.
5. Verify package availability on both phones with `adb shell pm path`.
6. Capture a baseline screenshot of Discover and My Apps on each phone.

## Listing cases

### LST-01 — Listing visibility and metadata

As each user, open Discover and inspect CRED, Google Pay, Agoda, Zomato, Amazon, and WhatsApp.

Expected: each active listing is visible; title, package, install URL, opt-in/group URL, instructions, owner, target count, and lifecycle match Supabase. A listing remains visible when its package is absent locally.

Evidence: screenshots, UI XML, package-manager output, and a matching SQL query.

### LST-02 — Owner view and self-join prevention

As `selva`, open each owned listing and attempt to join it.

Expected: owner view is coherent, but self-join is rejected/disabled; no commitment, slot reservation, reminder, or notification is created.

### LST-03 — Persistence and refresh

Open each listing, background/foreground the app, force-stop/start the app, and refresh the relevant screen.

Expected: no duplicate cards, metadata changes, orphan campaign, or disappearance of active listings.

### LST-04 — Invalid and duplicate listing validation

If adding a temporary QA listing is authorized, test malformed Play URL, package/URL mismatch, missing required group URL, duplicate package, rapid Continue taps, back navigation, and retry after a failed save.

Expected: specific validation; no partial `owned_apps`/`campaigns` pair; no duplicate listing or campaign; entered data is preserved or clearly discarded.

## Discover and commitment cases

### COM-01 — Non-owner joins CRED and Google Pay

As the non-owner user (`sri`), open the active CRED and Google Pay campaigns and attempt to join.

Expected: join is allowed only for a non-owner, exactly one commitment is created, lifecycle begins in the documented pending state, and the reserved slot changes once.

If the UI requires the tester to offer an owned app, verify whether `sri` has an active owned app. If not, record the exact blocker; do not create a duplicate listing just to force a pass.

### COM-02 — Non-owner joins Agoda and Zomato

As `sri`, join the Samsung-owned Agoda and Zomato campaigns.

Expected: remote listings are joinable even though Agoda/Zomato are absent on Samsung; local absence does not hide or corrupt the listing.

### COM-03 — Duplicate join and rapid taps

Tap Join repeatedly, retry after success, rotate/background during submission, and reopen the listing.

Expected: at most one commitment for `(campaign_id, tester_id)`, one slot reservation, one success state, and no duplicate notifications.

### COM-04 — Cancel/leave/reject/retry

For every supported terminal action, cancel/leave/reject a test commitment, refresh both devices, then retry the action.

Expected: terminal state is consistent on both devices, slot release is exactly once, and follow-up actions are blocked or clearly explained.

### COM-05 — Concurrent commitment race

Use both phones to attempt the last available slot at nearly the same time.

Expected: one winner, one clear loser/retry state, no negative slots, duplicate rows, or contradictory UI.

## Install-detection cases

### INS-01 — Installed-app detection

For CRED, Google Pay, Amazon, and WhatsApp, run the product’s install/open check on a device where the package is installed.

Expected: the package launches or is recognized, the state is `installed`, and returning to TestLoop preserves the commitment and latest state.

Do not enter WhatsApp or test messaging behavior.

### INS-02 — Missing-app detection

On Samsung, run the check for Agoda and Zomato.

Expected: state is `not_installed` or documented `unknown`; never `installed`, never a crash, and the user receives a usable install/retry path.

### INS-03 — Pixel installed comparison

On Pixel, run the same Agoda/Zomato checks.

Expected: the result differs from Samsung only because of actual package availability; Pixel reports `installed`.

### INS-04 — Package/URL mismatch and missing external app

For a controlled QA case, use a listing whose package is absent or whose Play URL points to a different package.

Expected: mismatch is detected or clearly reported; no false install state, broken external handoff, or silent success.

### INS-05 — Repeat/upsert/idempotency

Repeat checks, rapid-tap the check action, background during the check, and retry after a transient failure.

Expected: one install-state row per intended unique key, status updates in place, timestamps are sensible, and lifecycle changes once.

## Reminder and notification cases

### REM-01 — Reminder eligibility and send

With a valid commitment whose counterpart signal is due, send the quick check-in/reminder.

Expected: one reminder row with correct sender, recipient, commitment, target app, date, and message; one recipient notification; UI changes to a sent-today state.

If reciprocal reminders require an active app owned by `sri` and none exists, record this as a fixture blocker rather than using an unrelated account.

### REM-02 — Same-day duplicate protection

Send the same reminder again on the same UTC date and repeat after refresh.

Expected: no duplicate reminder or notification; a specific already-sent state or error is shown.

### REM-03 — Notification permission denied/granted

Repeat reminder and lifecycle notification tests with POST_NOTIFICATIONS denied, then granted.

Expected: server/in-app state remains correct; the app explains notification limitations and does not claim delivery when Android blocks it.

### REM-04 — Activation notification

Capture notifications before and after a pending commitment becomes active through install detection.

Expected: pending creation does not duplicate activation notices; the pending-to-active transition creates exactly one correctly addressed notification.

### REM-05 — Local scheduled reminder

With an active commitment and pending daily work, verify the scheduled reminder/alarm, channel, wording, tap destination, rescheduling, and duplicate suppression. Test a device restart and app force-stop before the scheduled time.

Expected: no false alarm success, no duplicate alarm, and tapping opens the relevant TestLoop state.

## Cross-device cases

### XDV-01 — Pixel installed state reaches Samsung

Pixel performs an installed check; Samsung refreshes the matching commitment/detail view.

Expected: lifecycle, install state, slot/tester count, and notification state converge with Supabase without stale UI.

### XDV-02 — Samsung missing state versus Pixel installed state

Check Agoda/Zomato on Samsung and Pixel independently, then refresh both.

Expected: Samsung remains missing/not-installed, Pixel remains installed, and one device’s result is never copied to the other.

### XDV-03 — Offline/retry

Disable network only for the test, attempt join/install/reminder, restore network, retry, and refresh.

Expected: no false success or fabricated local row; one valid retry result after reconnection; both devices converge.

### XDV-04 — Background/process death/device restart

Background during each meaningful request, force-stop/restart TestLoop, and restart one device between state changes.

Expected: no lost commitment, duplicate request, false lifecycle transition, or permanently stuck loading state.

### XDV-05 — Stale data and concurrent refresh

Leave one phone on a stale detail screen while the other changes the commitment, then refresh both and perform simultaneous checks.

Expected: explicit refresh/reconciliation; idempotent install-state updates; no duplicate notifications or contradictory terminal states.

## Evidence and release gate

For every case record: case ID, user, device serial/model/Android version, app build, setup state, exact actions, expected result, observed result, timestamp, screenshot/UI XML, relevant logcat/dumpsys, and SQL before/after rows.

Classify each result as Pass, Fail, Blocked, Not applicable, Intermittent, Device-specific, Environment limitation, or UX concern. A test is not a pass when it only matches source code or when required fixture/account state is missing.

Production readiness should remain blocked until critical failures are fixed and retested, and until any fixture-dependent commitment/install/reminder gaps are either executed with valid two-user data or explicitly accepted as residual risk.
