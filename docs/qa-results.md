# TestLoop hostile QA results

Date: 2026-07-23  
Build inspected: `com.testloop.app` version `0.1.0`  
Device: Android Emulator, SDK 35, Android 15, `emulator-5554`  
Scope: onboarding, OAuth callback behavior, multiple-app and reciprocal assumptions, install detection, activity reporting, permissions, and failure states.

## Release recommendation

**Do not ship.** The current build is a visual prototype with real Google OAuth launch plumbing, but its core coordination, package-state, activity, notification, and backend behavior is either demo-only or absent. Several UI labels present fabricated signals as live device data.

## Severity-ranked findings

### P0: The entire coordination experience uses demo data, not Supabase

**Evidence**

- `TestLoopApp` creates `DemoTestLoopRepository()` directly.
- `SupabaseAuthRepository` is only used for identity. No production repository queries listings, campaigns, commitments, activity summaries, reminders, or notifications.
- The app can display hard-coded listings, testers, activity, and reminder states after a successful sign-in even if the remote database is empty or unavailable.

**Reproduce**

1. Inspect `app/src/main/java/com/testloop/app/ui/TestLoopApp.kt`.
2. Observe `val repo = remember { DemoTestLoopRepository() }`.
3. Inspect `app/src/main/java/com/testloop/app/data/DemoRepositories.kt` for fixed users, apps, commitments, and notifications.

**Impact**: All claimed marketplace and reciprocal state is fabricated and device-local. A user cannot coordinate with another real user.

### P0: Install detection is not implemented, and the UI can report invented install state

**Evidence**

- No production source calls `PackageManager`, `getPackageInfo`, `UsageStatsManager`, or a package-install callback.
- `ScopedActivityTracker` is only an unused interface.
- The APK manifest has no `<queries>` declaration, no `QUERY_ALL_PACKAGES`, and no Usage Access-related permission. `aapt dump badging` lists only the generated dynamic-receiver permission.
- `DemoTestLoopRepository` hard-codes `INSTALLED` and `NOT_INSTALLED` values.

**Runtime package comparison**

```text
adb shell pm path com.google.android.apps.maps
package:/product/app/Maps/Maps.apk

adb shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
  com.google.android.apps.maps
com.google.android.apps.maps/com.google.android.maps.MapsActivity

adb shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
  com.testloop.qa.absent
No activity found
```

Google Maps opened successfully via an explicit launcher intent. The absent package did not resolve. TestLoop does not perform either operation.

**Impact**: The product cannot distinguish installed from not installed today. It also cannot show a truthful last-checked time. An explicit package-targeted check can avoid inventory scanning, but on Android 11+ arbitrary package visibility must be designed and validated carefully. The current app has neither implementation nor visibility strategy.

### P0: “Open app”, “Install”, and “Fix activity access” fabricate completion instead of taking the advertised action

**Evidence**

- Pair-detail actions call `repo.markOpened(id)` for every label.
- `DemoTestLoopRepository.markOpened` changes the signal to `INSTALLED`, `OPENED_TODAY`, and `minutesToday = 8` without launching an app, checking a package, checking Usage Access, or collecting usage.
- `AndroidExternalAppLauncher` exists but is never injected or invoked.

**Impact**: A tester can generate an “opened today” result by tapping a TestLoop button. Publishers will be misled.

### P0: Usage Access onboarding is a false permission flow

**Evidence**

- The button says `Allow Usage Access`.
- The screen itself states: `This foundation records your choice only. It does not open Android settings or read usage data yet.`
- `completeTrackingSetup()` unconditionally returns success.
- The profile later displays `Activity access: On` regardless of Android permission state.

**Impact**: The app claims an enabled privacy-sensitive capability that is neither requested nor present.

### P1: OAuth cancellation and malformed callback lack user-facing recovery

**Runtime evidence**

1. Fresh app data was launched successfully with `adb shell am start -W -n com.testloop.app/.MainActivity` in 625 ms.
2. Tapping `Continue with Google` opened Chrome at the Supabase authorization URL with PKCE parameters.
3. Pressing Back returned to the unchanged welcome screen. No cancellation, offline, or retry message was rendered.
4. Delivering an error callback also returned to the unchanged welcome screen:

```text
adb shell am start -W -a android.intent.action.VIEW \
  -c android.intent.category.BROWSABLE \
  -d 'testloop://auth-callback?error=access_denied&error_description=User%20cancelled' \
  com.testloop.app
```

**Impact**: A user cannot tell whether login was cancelled, blocked by network, or failed at Supabase.

### P1: OAuth deep links create duplicate MainActivity instances

**Runtime logcat evidence**

Delivering `testloop://auth-callback?code=invalid-qa-code` produced:

```text
ActivityTaskManager: START ... cmp=com.testloop.app/.MainActivity ...
WindowManagerShell: ... numActivities=2 ...
```

Pressing Back returned to the previous TestLoop activity. `MainActivity` has the default launch mode, so `onNewIntent` does not solve callbacks delivered as a new instance.

**Impact**: A completed login can return to a stale screen or create confusing Back behavior.

### P1: Multiple-app and reciprocal-pair support is only local and does not enforce production invariants

**Evidence**

- The UI models multiple owned apps and lets a tester choose one for a pair.
- Adding an app appends only to the in-memory demo list. Restarting the process loses it.
- Joining a campaign changes only this device's demo state and increments a local campaign count. It does not reserve a remote slot, notify a real app owner, establish the other person's obligation, or survive restart.
- The add-app form accepts `0` testers and arbitrary large values because it only checks `toIntOrNull()`.

**Impact**: The happy-path design is present but no actual two-party reciprocal contract exists. Slot counts can be wrong and cannot be protected from concurrent joins.

### P1: Reminders and notifications are simulated, with no Android permission or delivery path

**Evidence**

- No `POST_NOTIFICATIONS` permission, notification channel, notification manager, push token, push provider, or WorkManager implementation is present.
- Sending a reminder only flips `youCanSendReminder` in demo memory.
- The notification feed is hard-coded.

**Impact**: Users can be told a reminder was sent when no notification is ever delivered.

### P1: Account deletion is a nonfunctional UI affordance

**Evidence**

- `AuthRepository.deleteAccount()` only signs out in the Supabase implementation.
- The visible `Delete account` label in Profile has no click handler.

**Impact**: The app does not provide the advertised deletion control, nor does it delete the user from Supabase.

### P2: Test coverage and reproducible automated checks are absent

**Evidence**

- No unit, instrumented, or UI test source files were found under `app/src`.
- The only test artifact is an adversarial QA plan, not executable coverage.

**Impact**: Core flows will regress without detection.

## What passed

- Fresh launch completed without crash on the available Android 15 emulator.
- The welcome screen rendered with the expected sign-in control.
- Tapping Google sign-in opened the Supabase OAuth URL in Chrome and included a PKCE challenge.
- The custom `testloop://auth-callback` deep link resolves to `MainActivity`.
- The supplied APK has no broad package-inventory permission, which is the right privacy position. It has not yet implemented the narrower committed-package alternative.

## Test limitations and blockers

- The emulator had no usable internet connection, so Google account selection and a successful end-to-end OAuth exchange could not be completed.
- The machine currently has no Java runtime, so `./gradlew :app:assembleDebug` could not run during this pass. The existing APK was built at 11:51:51, after the inspected source files, and was used for runtime tests.
- The Supabase CLI is not currently on `PATH`, so remote migration state and RLS could not be exercised from this environment. The SQL migration was reviewed statically only.

## Required exit criteria before the next QA pass

1. Replace `DemoTestLoopRepository` with an authenticated Supabase implementation and prove it with two accounts on two devices or emulator profiles.
2. Implement an explicit package-targeted install state service. It must report `detected`, `not detected`, or `unavailable` with a timestamp and must not upload a device inventory.
3. Make `Open app` launch only the selected commitment package. Separate launch, install detection, Usage Access state, and daily activity collection. Never create activity minutes from a button tap.
4. Implement the Android Settings round-trip for Usage Access and render the actual permission state. Remove the `On` claim until it is true.
5. Handle OAuth cancellation, offline failure, invalid callbacks, and one task instance correctly.
6. Enforce server-authoritative app, campaign, reciprocal-pair, slot-allocation, reminder, and idempotency rules. Add database integration tests for RLS and concurrent final-slot joins.
7. Implement notification permission, channels, delivery, and a real reminder path before showing reminder success.
8. Add tests for package present, absent, disabled, non-launchable, permission denied, permission revoked, deep-link error, OAuth cancellation, duplicate join, two owned apps, and restart persistence.
