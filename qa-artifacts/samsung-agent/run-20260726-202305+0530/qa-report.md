# Samsung execution QA report — blocked by locked device

Run: `run-20260726-202305+0530`

Artifact root: `/Users/srinathsmac/Documents/Android testing/qa-artifacts/samsung-agent/run-20260726-202305+0530`

Timestamp:

- Host/device timezone: 2026-07-26T20:23:05+05:30 through 2026-07-26T20:26:32+05:30
- UTC: 2026-07-26T14:53:05Z through 2026-07-26T14:56:32Z

## Environment blocker

Samsung serial `RZCY51TRYMT` was connected, but locked for the duration of the run. `dumpsys window` repeatedly reported `mDreamingLockscreen=true` with current focus on `NotificationShade`. Per instruction, I did not bypass unknown credentials and did not capture lockscreen screenshots/UI dumps because those could expose private lockscreen notifications.

Result: actual UI execution is blocked until the phone is physically unlocked.

## Device/app metadata collected

- Target serial used: `RZCY51TRYMT` only.
- Device: Samsung SM-M356B (`m35xins` / `m35x`)
- OS: Android 16 / API 36
- Build: `BP4A.251205.006.M356BXXU8DZE3`
- Fingerprint: `samsung/m35xins/m35x:16/BP4A.251205.006/M356BXXU8DZE3:user/release-keys`
- Display: 1080x2340
- Density: physical 450, override 420
- Connectivity: validated cellular internet observed
- APK: `/Users/srinathsmac/Documents/Android testing/app/build/outputs/apk/debug/app-debug.apk`
- Package: `com.testloop.app`
- Installed version: versionCode 13, versionName 0.1.5
- Install/update: firstInstallTime `2026-07-26 13:01:45`; lastUpdateTime `2026-07-26 20:14:51`; installerPackageName `null`
- Current process/activity state: pid `11249`, task `#2257`, `MainActivity`, process state top-sleeping behind lockscreen

## Permissions/AppOps

- Manifest permissions requested: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `PACKAGE_USAGE_STATS`
- Runtime notification permission: `POST_NOTIFICATIONS` granted=false
- AppOps: `GET_USAGE_STATS: allow`; `POST_NOTIFICATION: ignore`
- Notification app setting: `importance=NONE`
- Usage Access query: `cmd appops query-op GET_USAGE_STATS allow` included `com.testloop.app`
- Reminder alarms: historical/pending `com.testloop.app.action.EVENING_REMINDER` alarms observed in `dumpsys alarm`

## Installed dependency matrix

- `com.dreamplug.androidapp` (CRED): installed; launcher resolves to `.CredFlutterActivity`
- `com.google.android.apps.nbu.paisa.user` (GPay): installed; launcher resolves to GPay launcher activity
- `com.whatsapp`: installed; launcher resolves to `.Main`
- `in.amazon.mShop.android.shopping`: installed, but package-only `resolve-activity` returned Android resolver rather than a direct default launcher
- `com.agoda.mobile.consumer`: absent
- `com.application.zomato`: absent
- `com.google.android.apps.maps`: installed
- `com.android.chrome`: installed
- `com.sec.android.app.sbrowser`: absent

## Executed / blocked / not applicable

Executed, adb/source only:

- Device identity, display, density, build, connectivity.
- APK/package/version install metadata.
- Permissions/AppOps/notification policy.
- Installed external-app package matrix.
- Current TestLoop process/activity state.
- Filtered logcat capture.
- Source investigation of listed suspected blockers.

Blocked by lockscreen:

- Onboarding and Usage Access settings round-trip.
- Session/process/lifecycle UI behavior.
- Empty Discover and My apps after backend reset.
- Listing form validation.
- External-app missing/installed UI behavior for CRED/GPay/WhatsApp/Amazon/Agoda/Zomato.
- Notifications/reminders/OEM behavior from UI.
- Offline/retry/rapid-tap/back-stack cases.
- Pixel-vs-Samsung screen/state comparison.
- Device reproduction of source-suspected defects.

Not applicable / intentionally avoided:

- Chat UI, encryption, realtime, and message behavior.
- Clearing app data, uninstalling, changing accounts, altering permissions, or deleting/overwriting backend data.

## Defects and source-only suspicions

| ID | Type | Status | Repro steps | Expected | Observed | Device/evidence |
| --- | --- | --- | --- | --- | --- | --- |
| ENV-SAM-001 | Environment blocker | Device-confirmed | Connect serial `RZCY51TRYMT`; inspect `dumpsys window`. | Device should be unlocked for UI QA, or QA should stop without bypassing credentials. | Device stayed locked: `mDreamingLockscreen=true`, current focus `NotificationShade`. | `metadata/initial-device-lock-check.txt`, `metadata/final-lock-recheck.txt`; Samsung SM-M356B Android 16 |
| SRC-001 | Account deletion control | Source-only suspicion | Open Profile and inspect/tap “Delete account” once UI is available. | A destructive account action should provide a working, confirmed control or not be shown. | Source renders static “Delete account” text only; `SupabaseAuthRepository.deleteAccount()` only signs out and does not delete backend auth/profile data. | `source-snippets/FlowsAndProfile-profile-guide-delete.kt.txt`, `source-snippets/SupabaseAuthRepository-delete.kt.txt` |
| SRC-002 | Official testing-guide URL handling | Source-confirmed, not device-reproduced | Profile → Google Play testing guide → “Read Google’s official requirements”. | Opens Google support URL. | `GOOGLE_PLAY_TESTING_REQUIREMENTS_URL` is `https://support.google.com/...`, but `ExternalAppLauncher.openUrl()` delegates to `PlayUrlPolicy`, whose parser only trusts `play.google.com` and `groups.google.com`; source path therefore returns `Blocked` and snackbar says to open in browser. | `source-snippets/PlayUrlPolicy.kt.txt`, `source-snippets/TestLoopApp-official-guide-url.txt` |
| SRC-003 | Empty UsageStats false-negative | Source-only suspicion | With Usage Access allowed, open a target app briefly; return and verify daily-open status around first launch/aggregation delays. | App should avoid marking a valid open as false if UsageStats returns delayed/empty data. | Source treats no matching `queryUsageStats` row as `false`, not “unknown/pending”. That may create false negatives on OEM/aggregation delays. | `source-snippets/UsageAccessReporter.kt.txt`; device has `GET_USAGE_STATS: allow` |
| SRC-004 | Blank/loading frame | Source-only suspicion | Cold-launch signed-in account after reset or slow network. | User should see a loading state while repository refreshes; then empty states. | `dataLoaded=false` maps to an explicit Today loading state. No blank frame confirmed from source snippet; needs device reproduction. | `source-snippets/OnboardingAndToday-loading-empty.kt.txt`, `source-snippets/TestLoopApp-navigation.kt.txt` |
| SRC-005 | Android/OEM package visibility/launch behavior | Partly source/adb-supported | From an active commitment, use Open/Install for installed and absent packages. | Installed launchable packages open; absent packages route to Play/link guidance; non-launchable packages show a clear message. | Source uses `getLaunchIntentForPackage()` and `getApplicationInfo()` fallback, which should mitigate Android 15+ package visibility issues. Samsung adb shows CRED/GPay/WhatsApp launchable, Amazon installed but package-only resolve hit ResolverActivity; Agoda/Zomato absent. Needs app UI reproduction. | `metadata/installed-dependency-matrix.csv`, `source-snippets/ExternalAppLauncher.kt.txt`, `source-snippets/InstallStatusReporter.kt.txt` |

## UX concerns

- Locked-device state is a hard QA blocker; screenshots/UI dumps were intentionally not captured to avoid exposing lockscreen private content.
- Notification permission is denied and app notification importance is `NONE`; reminder UX/OEM behavior must be validated after unlock because reminders may be scheduled but not visible to the user.
- Usage Access is already allowed, so a full onboarding/settings round-trip may require user coordination if the test needs the disabled-to-enabled path; I did not change AppOps.
- The official guide button appears likely to fail by policy even though it is a first-party Google support link.
- Account deletion copy/control is potentially misleading because the visible text is not wired in the inspected UI snippet, and the repository method signs out rather than deleting the account.

## Backend/product mutations

None. I did not create listings, commitments, reminders, notifications, messages, accounts, or profile/backend rows. I did not clear app data, uninstall, change accounts, alter permissions, or delete backend data.

## Residual risks / resume plan after unlock

1. Capture screenshot and UI dump of current TestLoop screen after physical unlock.
2. Confirm navigation is not broken by chat initialization without entering chat.
3. Execute Usage Access settings round-trip without changing state unless coordinated.
4. Validate empty Today/Discover/My apps states after backend reset.
5. Run listing validation without submitting unless explicit test-data creation is approved; if submitted, document created rows for cleanup.
6. Validate external-app behavior for CRED, GPay, WhatsApp, Amazon, Agoda, and Zomato from actual TestLoop UI.
7. Validate official guide button, account deletion presentation, notification/reminder UX, offline/retry, rapid tap, and back stack.
8. Capture logcat/screenshots/UI dumps for each case under this artifact root or a resumed run.
