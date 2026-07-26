# Samsung focused core execution report

Run ID: `core-20260726-205625+0530`

Artifact root: `/Users/srinathsmac/Documents/Android testing/qa-artifacts/samsung-agent/core-20260726-205625+0530`

Device used: Samsung only, serial `RZCY51TRYMT`.

## Scope guardrails followed

- Chat was not opened or tested.
- Previously completed onboarding, safe-area, and general visual cases were not repeated.
- No app data clear, uninstall, account change, permission change, or personal connectivity change was performed.
- No Supabase fixture rows were created/modified/deleted by this Samsung pass.
- I stopped before Join/Commitment/Install/Reminder mutations because the corrected Samsung-primary fixture manifest was not available.

## Device and app preflight

- Samsung SM-M356B, Android 16/API 36
- Display: 1080x2340; density override 420
- Build: `BP4A.251205.006.M356BXXU8DZE3`
- TestLoop package: `com.testloop.app`, versionCode 13, versionName 0.1.5
- Stay-awake over USB: active
  - `global.stay_on_while_plugged_in=15`
  - `mStayOn=true`
  - `mIsPowered=true`
  - `mPlugType=2`
- Lock state: unlocked; `mDreamingLockscreen=false`
- Foreground: TestLoop `MainActivity`
- AppOps:
  - `GET_USAGE_STATS: allow`
  - `POST_NOTIFICATION: ignore`

Evidence:

- `metadata/preflight-device.txt`
- `metadata/core-notification-reminder-dumpsys.txt`

## Fixture coordination result

User correction received during run:

- Samsung is the primary listing/fixture device.
- CRED and Google Pay should be added through Samsung UI.
- Agoda and Zomato should exist as Samsung-visible listing records but be missing on Samsung and installed on Pixel.
- Do not mutate Supabase until the corrected fixture manifest is available.

I found a `core-fixtures-ready.md`, but it is not the corrected Samsung-primary manifest. It explicitly says:

- Pixel is the primary execution device.
- Samsung is read-only/handoff.
- CRED/GPay are Pixel-owned listings.

Therefore I treated the available marker as stale/invalid for the corrected plan and did not proceed with mutating Samsung fixture flows.

Evidence:

- Pixel/stale marker: `/Users/srinathsmac/Documents/Android testing/qa-artifacts/pixel-agent/core-run/core-fixtures-ready.md`
- Captured search/output: `metadata/corrected-fixture-manifest-search.txt`

## Source/schema preflight

Inspected and mapped the relevant paths:

- Repository core flows: `SupabaseTestLoopRepository.kt`
- Package install detection: `InstallStatusReporter.kt`
- External launch behavior: `ExternalAppLauncher.kt`
- URL policy: `PlayUrlPolicy.kt`
- Usage stats: `UsageAccessReporter.kt`
- UI navigation/join/report/reminder paths: `TestLoopApp.kt`, `CommitmentScreens.kt`, `PortfolioAndDiscover.kt`
- Supabase migrations for commitments, install states, daily opens, reminders, notifications, public listings, replacement/restart, usage-access enforcement.

Evidence:

- `source/source-schema-map.txt`
- `supabase/supabase-preflight.txt`
- `supabase/supabase-query-method-preflight.txt`

## Supabase read-only state

Final read-only counts:

| Table | Count |
| --- | ---: |
| profiles | 2 |
| owned_apps | 7 |
| campaigns | 7 |
| public_app_listings | 7 |
| reciprocal_commitments | 0 |
| commitment_install_states | 0 |
| commitment_daily_open_states | 0 |
| reminders | 0 |
| notifications | 0 |
| app_bookmarks | 0 |

Final listing snapshot showed:

- `sri` owns Pixel-created CRED/GPay plus Agoda/Zomato “Pixel Only” listings.
- `selva` owns Amazon, WhatsApp, and synthetic missing-pixel listings.
- No commitments/install/open/reminder/notification rows exist.

Evidence:

- `supabase/final-readonly-fixture-state-result.txt`

## Samsung package availability matrix

| Package | Expected bucket | Samsung state | Launcher resolution |
| --- | --- | --- | --- |
| `com.dreamplug.androidapp` | installed target | installed | `com.dreamplug.androidapp/.CredFlutterActivity` |
| `com.google.android.apps.nbu.paisa.user` | installed target | installed | GPay launcher activity |
| `in.amazon.mShop.android.shopping` | installed target | installed | resolves to Android `ResolverActivity` for package-only query |
| `com.whatsapp` | installed target | installed | `com.whatsapp/.Main` |
| `com.agoda.mobile.consumer` | missing target | absent | no activity found |
| `com.application.zomato` | missing target | absent | no activity found |

Evidence:

- `metadata/core-installed-missing-package-matrix-clean.tsv`

## Device/UI checks performed safely

These were read-only and did not create commitments:

1. Background/return refresh:
   - Sent Samsung home, relaunched TestLoop, allowed refresh.
   - Evidence: `screenshots/001-resume-refresh.png`

2. Discover visibility against stale Pixel-owned CRED/GPay:
   - Discover showed CRED and GPay under “Apps you can test.”
   - CRED title appears as `QA%20Core%20CRED`, matching Pixel’s adb-encoded title issue.
   - Evidence: `screenshots/004-discover-listings-readonly.png`, `metadata/004-discover-listings-readonly-texts.txt`

3. Listing detail read-only:
   - Opened CRED detail and backed out without joining.
   - Opened GPay detail and backed out without joining.
   - Both showed `Join and choose my app`; not tapped.
   - Evidence: `screenshots/005-cred-detail-readonly.png`, `screenshots/006-gpay-detail-readonly.png`

4. Notification/reminder dumpsys:
   - Notification permission blocked (`POST_NOTIFICATION: ignore`, app importance `NONE`).
   - Historical evening reminder alarms exist, but fixture reminders table is empty.
   - Evidence: `metadata/core-notification-reminder-dumpsys.txt`, `supabase/final-readonly-fixture-state-result.txt`

## Blocked cases

Blocked by missing corrected Samsung-primary fixture manifest:

- Samsung UI creation of CRED and Google Pay listings.
- Samsung-owned Agoda/Zomato fixture listing validation in the corrected direction.
- Samsung missing-app behavior through real commitments for Agoda/Zomato.
- Cross-device commitment lifecycle as the second user.
- Install detection transitions: installed → active, missing → not installed, stale/unknown, URL-package mismatch.
- Reminder send/duplicate/cancel/firing behavior.
- Notification permission prompt/denial/retry behavior in active commitment context.
- Cross-device state propagation with refresh/background/return/retry after real Samsung-owned fixtures.

## Defects / findings

| ID | Severity | Status | Finding | Evidence |
| --- | --- | --- | --- | --- |
| CORE-BLOCK-001 | High | Blocker | Corrected Samsung-primary fixture manifest is not available. Existing marker is Pixel-primary and conflicts with the correction. I did not mutate Supabase or join stale records. | `metadata/corrected-fixture-manifest-search.txt`, Pixel `core-fixtures-ready.md` |
| CORE-DATA-001 | Medium | Data/fixture issue | Existing CRED listing title is `QA%20Core%20CRED`, indicating adb text input encoded spaces literally during Pixel setup. | `metadata/004-discover-listings-readonly-texts.txt`, Pixel marker |
| CORE-ANDROID-001 | Medium | Device package behavior | Amazon is installed on Samsung, but package-only `cmd package resolve-activity --brief in.amazon.mShop.android.shopping` resolves to Android `ResolverActivity`, not a direct launcher component. Product code uses `getLaunchIntentForPackage()`, so this needs fixture-backed UI confirmation later. | `metadata/core-installed-missing-package-matrix-clean.tsv`, source `ExternalAppLauncher.kt` |
| CORE-NOTIF-001 | Medium | Environment/product risk | Notifications are denied on Samsung (`POST_NOTIFICATION: ignore`, app importance `NONE`). Reminder firing/visibility cannot be validated until a commitment context exists and permission behavior is exercised. | `metadata/core-notification-reminder-dumpsys.txt` |

## Backend mutations

None by this Samsung pass.

Verified no rows exist in:

- `reciprocal_commitments`
- `commitment_install_states`
- `commitment_daily_open_states`
- `reminders`
- `notifications`
- `app_bookmarks`

## Required next action

Provide or generate a corrected Samsung-primary fixture manifest before continuing. It should explicitly include:

- Samsung user/profile expected to own CRED/GPay listings.
- Samsung-created CRED/GPay owned app IDs and campaign IDs.
- Samsung-visible Agoda/Zomato listing IDs/campaign IDs with Pixel-installed/Samsung-missing dependency expectation.
- Which profile should join from Samsung vs Pixel for each cross-device commitment.
- Cleanup list of all fixture-owned app IDs/campaign IDs/commitment IDs.

Once that corrected manifest exists, Samsung can safely proceed with fixture-backed commitment/install/reminder flows without inventing passes or mutating the wrong records.
