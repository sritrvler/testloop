# Final Samsung execution QA report — TestLoop

Run ID: `run-20260726-202305+0530`

Artifact root: `/Users/srinathsmac/Documents/Android testing/qa-artifacts/samsung-agent/run-20260726-202305+0530`

Device serial used: `RZCY51TRYMT` only.

## Environment and metadata

- Device: Samsung SM-M356B (`m35xins` / `m35x`)
- OS: Android 16 / API 36
- Build: `BP4A.251205.006.M356BXXU8DZE3`
- Display: 1080x2340
- Density: physical 450, override 420
- Font scale during visual pass: `1.15`
- Screen timeout setting: `600000` ms / 10 minutes
- Stay-awake over USB: verified after user enabled it; `global stay_on_while_plugged_in=15`, `mStayOn=true`, `mIsPowered=true`, `mPlugType=2`
- Insets from WindowManager:
  - Status bar: `[0,0][1080,100]`
  - Navigation bar: `[0,2214][1080,2340]`
- APK: `/Users/srinathsmac/Documents/Android testing/app/build/outputs/apk/debug/app-debug.apk`
- Package: `com.testloop.app`
- Version: versionCode 13, versionName 0.1.5
- Install/update: firstInstallTime `2026-07-26 13:01:45`, lastUpdateTime `2026-07-26 20:14:51`
- Connectivity: validated cellular internet observed
- Initial transient condition: phone was locked at run start, recorded and not bypassed. User later unlocked it. Final pass confirmed `mDreamingLockscreen=false` and TestLoop foreground.
- Device-awake note: user confirmed Pixel timeout 30m / Samsung timeout 10m and enabled `adb shell svc power stayon true` on both phones. This Samsung run verified the setting only on serial `RZCY51TRYMT` per device scope; evidence is in `metadata/stay-awake-verification-samsung.txt`.

## Permissions, AppOps, notifications

- Requested permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `PACKAGE_USAGE_STATS`
- `GET_USAGE_STATS`: `allow`
- `POST_NOTIFICATIONS`: denied / AppOps `ignore`
- Notification app importance: `NONE`
- Reminder alarms: `com.testloop.app.action.EVENING_REMINDER` entries observed in `dumpsys alarm`

## Installed dependency matrix

| App | Package | Samsung state |
| --- | --- | --- |
| CRED | `com.dreamplug.androidapp` | Installed, launcher resolves |
| GPay | `com.google.android.apps.nbu.paisa.user` | Installed, launcher resolves |
| WhatsApp | `com.whatsapp` | Installed, launcher resolves |
| Amazon | `in.amazon.mShop.android.shopping` | Installed; package-only resolve showed Android resolver, but package exists |
| Agoda | `com.agoda.mobile.consumer` | Absent |
| Zomato | `com.application.zomato` | Absent |
| Chrome | `com.android.chrome` | Installed |
| Maps | `com.google.android.apps.maps` | Installed |
| Samsung Browser | `com.sec.android.app.sbrowser` | Absent |

## Executed cases

| Area | Result | Evidence |
| --- | --- | --- |
| Lock/unlock handling | Initial lock treated as transient after user unlock; no credential bypass. | `metadata/initial-device-lock-check.txt`, `metadata/012-final-pass-lock-recheck.txt` |
| Onboarding / Usage Access | Step 2 showed Usage Access enabled; tapping Finish setup reached main app. | `screenshots/002-testloop-launched.png`, `screenshots/003-after-finish-setup.png`, matching UI dumps |
| Empty Today | First-run empty state shown for preserved user `@selva`; primary `Add my app`, secondary `Find an app to test`. | `screenshots/003-after-finish-setup.png`, `metadata/003-after-finish-setup-state.txt` |
| Empty My apps | `0 active apps`; Add-first-app empty state shown. | `screenshots/004-my-apps-empty.png`, `metadata/004-my-apps-empty-texts.txt` |
| Empty Discover | `No apps to discover yet`; search/filter controls and Add my app CTA shown. | `screenshots/005-discover-empty.png`, `metadata/005-discover-empty-texts.txt` |
| Profile | Preserved user profile `@selva` visible; guide/help rows visible. | `screenshots/006-profile.png`, `metadata/006-profile-texts.txt` |
| Listing validation Step 1 | Empty Continue stayed on Step 1; invalid Play URL displayed format error; valid-format Play URL derived package ID. | `screenshots/008-add-app-empty-validation.png`, `009-add-app-invalid-url-validation.png`, `013-add-app-valid-continue-retry.png` |
| Listing validation Step 2 | Required Google Groups validation displayed; invalid group link displayed example-format error. | `screenshots/014-add-app-step2-invalid-group.png`, `metadata/014-add-app-step2-invalid-group-texts.txt` |
| Back stack | Backing out of Add App returned to My apps without creating listing. | `screenshots/015-back-out-from-add-app.png` |
| Official testing guide | Guide page opened; official requirements CTA remained in TestLoop after taps. | `screenshots/016-google-play-guide-screen.png`, `017-official-guide-tap-result.png`, `020-guide-button-scrolled-tap-result.png` |
| Safe-area / visual usability | Completed focused pass across reached screens. | `safe-area-visual-usability-report.md`, `metadata/safe-area-candidate-bounds.tsv` |
| Logs | Final filtered logcat captured; no TestLoop fatal crash seen in tail. | `logs/final-logcat-filtered-tail.txt` |

## Blocked or not reached

- External-app product behavior for CRED/GPay/WhatsApp/Amazon/Agoda/Zomato: not reachable because backend app data was reset and no active commitments/listings existed. I did not create backend test data just to force this.
- Notifications/reminders from product UI: not reachable without active commitments; additionally notification permission is denied.
- Offline/retry: not executed in bounded completion pass to avoid altering the user’s live cellular state on a personal phone.
- Account deletion UI device reproduction: Delete account row was below the captured Profile viewport and not exercised to avoid destructive ambiguity; source was inspected.
- Chat: intentionally not attempted.

## Defects and concerns

| ID | Severity | Type | Status | Repro / condition | Expected | Observed | Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- |
| SAM-001 | High | Safe-area / tappability | Device-confirmed | Profile → Google Play testing guide; scroll bottom naturally. | Bottom CTA should sit fully above Samsung nav bar and be easy to tap. | `Read Google’s official requirements` is clipped/visually merged with the 3-button nav area; XML bounds `[239,2162][925,2214]`, nav starts at `y=2214`, visual button extends into nav area. | `screenshots/016-google-play-guide-screen.png`, `safe-area-visual-usability-report.md` |
| SAM-002 | High | URL handling | Device-confirmed + source-confirmed | Tap `Read Google’s official requirements`. | External browser opens Google support URL. | Focus stayed in `com.testloop.app`; no browser opened. Source explains cause: `support.google.com` URL is passed through `openUrl()`, but `PlayUrlPolicy` only allows `play.google.com` and `groups.google.com`. | `screenshots/017-official-guide-tap-result.png`, `screenshots/020-guide-button-scrolled-tap-result.png`, `source-snippets/PlayUrlPolicy.kt.txt`, `source-snippets/TestLoopApp-official-guide-url.txt` |
| SAM-003 | Medium | Edge-to-edge top inset | Device-confirmed | Scroll Google Play testing guide so bottom CTA becomes visible. | Header should remain below status bar. | Header/title intrudes into status bar region; title bounds `[201,79][889,158]`, status bar is `y=0–100`. | `screenshots/020-guide-button-scrolled-tap-result.png`, `metadata/samsung-window-insets-final.txt` |
| SAM-004 | Medium | Form usability / IME | Device-confirmed | Add App Step 2, tap Testing group link and type invalid value. | User should see field context, error, and next action or an obvious way forward. | Samsung keyboard obscures lower fields and Continue; error is clear but progression requires keyboard dismissal/scroll. | `screenshots/014-add-app-step2-invalid-group.png` |
| SRC-001 | Medium | Account deletion | Source-only | Profile Delete account control. | Destructive account action should be confirmed and actually delete/deactivate account data, or copy should not imply deletion. | Source shows static `Delete account` text in Profile; repository `deleteAccount()` only signs out and sets local account null. Not device-reproduced. | `source-snippets/FlowsAndProfile-profile-guide-delete.kt.txt`, `source-snippets/SupabaseAuthRepository-delete.kt.txt` |
| SRC-002 | Medium | UsageStats false-negative risk | Source-only | Usage Access allowed, target app opened briefly, UsageStats delayed/empty. | App should distinguish unknown/pending from confirmed not opened. | Source returns `false` when no usage row is found. On Samsung, Usage Access is allowed, but no active commitment existed to device-reproduce. | `source-snippets/UsageAccessReporter.kt.txt`, `metadata/permissions-appops.txt` |
| UX-001 | Low-Medium | Visual density | Device-confirmed | Today first-run at font scale 1.15. | Primary path clear while supporting content remains digestible. | Primary/secondary CTAs are clear, but the hero card is tall and pushes “How TestLoop works” toward the lower scroll area. | `screenshots/003-after-finish-setup.png` |
| UX-002 | Low | Discover visual density | Device-confirmed | Discover empty at font scale 1.15. | Search/filter controls should not overwhelm empty-state CTA. | Primary `Add my app` remains obvious; filter chips are slightly clumped, `Fewest slots` is horizontally clipped. | `screenshots/005-discover-empty.png` |

## UX/safe-area summary

See `safe-area-visual-usability-report.md` for exact bounds and severity. The biggest Samsung-specific issue is edge-to-edge Compose padding on the Google Play testing guide: the bottom CTA merges with Samsung’s navigation bar, and after scrolling the header enters the status bar safe zone. Add App is technically functional but keyboard ergonomics are weak on the 2340px Samsung layout with font scale 1.15.

## Backend/product mutations

- Created backend data: none.
- Completed onboarding/tracking setup for preserved user `@selva` through the product by tapping `Finish setup`; this likely updated the existing profile’s tracking setup state.
- No listings, commitments, reminders, notifications, messages, accounts, or chat data were created.
- No app data clear, uninstall, account change, permission change, or backend delete/overwrite was performed.

## Residual risks

- External-app launch behavior must be retested after there is a real active commitment/listing set, or with explicit approval to create disposable test data.
- Notification/reminder UX needs a state with pending commitments and notification permission handling.
- Offline/retry needs explicit approval because it would alter live device connectivity.
- Large-text stress beyond current `font_scale=1.15` was not performed to avoid changing user display settings.
