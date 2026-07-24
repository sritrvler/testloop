# TestLoop adversarial QA plan

Status: V1 pre-implementation review plan  
Owner: Principal mobile QA, security, privacy, and UX  
Scope: Android client, backend APIs, database authorization, links, notifications, and deletion workflow

## 1. Quality position

TestLoop coordinates reciprocal closed testing. It does not verify Google Play enrollment, compliance, effort, test quality, or continuous 14-day eligibility. Every status shown to a user must reflect only what TestLoop directly observed.

The highest product risk is false certainty. A green badge that implies more than the app can know may cause a developer to rely on incorrect evidence and lose time during Play review. Privacy is the second highest risk because Usage Access exposes device-wide usage data even if TestLoop intends to process only joined packages.

V1 explicitly excludes payments, donations, ratings, chat, ad SDKs, and automatic broad app scanning. Any appearance of these features in a release build is a scope and privacy defect.

### Priority definitions

- P0, release blocker: can mislead users about Google status, expose or retain personal data, break authorization or reciprocal consistency, lose data, create an unrecoverable primary-flow failure, or crash a core flow.
- P1, high: materially harms reliability, accessibility, abuse resistance, or comprehension but has a safe workaround.
- P2, medium: degrades a secondary flow or creates bounded confusion without corrupting state.
- P3, low: cosmetic or low-impact issue with no material trust or task-completion effect.

A failed expected result inherits the scenario priority unless the observed harm warrants escalation. `Blocked` is not a pass. A blocked P0 scenario blocks release until its environment or dependency is available and the scenario passes.

## 2. Technical truth constraints

These are product facts that all UI, API names, analytics, support content, and marketing copy must preserve.

1. Google sign-in identifies the TestLoop account. It does not prove which Google account is enrolled in a Play test.
2. Opening an opt-in URL does not prove enrollment.
3. Detecting a package does not prove it came from Google Play, corresponds to the expected signing key, belongs to the current Android profile, or is enrolled in a closed test.
4. Usage Access is granted in Android Settings. It is broad device-level access, not permission for one selected package.
5. Usage data is an estimate. Events can be missing, delayed, aggregated, unavailable while the user is locked, affected by OEM behavior, and retained by Android for only a limited period.
6. Foreground duration does not prove useful testing. Zero detected activity does not prove dishonesty.
7. Package absence is not definitive proof of uninstall. Package visibility, work profiles, archived apps, disabled apps, multiple users, and transient platform errors can affect detection.
8. TestLoop cannot remove a tester from a Play Console test, remotely uninstall another app, or guarantee a Play production-access outcome.
9. TestLoop may open an app only after an explicit user action. The launch can fail if the package is absent, disabled, in another profile, or has no launchable activity.
10. Notification permission is optional. Denial, dismissal, per-channel blocking, and later revocation are supported states.
11. Day counts must be defined by a documented clock and must never be presented as Google's official 14-day counter.
12. Deleting a TestLoop account must delete or irreversibly anonymize associated personal data, subject only to clearly disclosed and justified retention.

### Required language

Use:

- `Install detected on this device`
- `Activity detected today`
- `No activity detected yet`
- `Joined 6 days ago`
- `TestLoop estimate`
- `Open opt-in page`
- `Open app`

Do not use:

- `Verified tester`
- `Google verified`
- `Testing requirement completed`
- `Guaranteed 14 days`
- `Tester cheated`
- `Definitely uninstalled`
- `Tested for 20 minutes` when the signal only represents foreground activity

## 3. Personas and stage risks

### P1: First-time solo developer

Needs a simple path to list an app and find reciprocal testers. Fears failing Play review after waiting 14 days. May confuse TestLoop evidence with Google evidence. Likely to paste incorrect links or an app display name instead of a package ID.

Design response: explain uncertainty next to statuses, derive package ID from the Play URL where possible, validate all links, show one clear next action, and never use compliance language.

### P2: Beginner tester

Needs to understand what to install, what task to perform, and what data is shared. Fears spyware, scams, and breaking their phone. May deny permissions, leave system Settings without granting access, or use the wrong Google account in Play.

Design response: progressive disclosure, plain-language permission rationale, a usable no-permission state, safe link previews, explicit confirmation before joining, and persistent access to privacy controls.

### P3: High-volume returning developer

Needs multiple listings, reliable slot counts, and fast status scanning. Fears stale dashboards, duplicate commitments, and unreliable partners.

Design response: server-authoritative state, idempotent actions, clear stale-data timestamps, filters, and conflict-safe slot allocation.

### P4: Privacy-sensitive user

Needs reciprocal coordination without exposing unrelated app behavior. Fears broad surveillance and retention after leaving.

Design response: collect only joined package summaries, never upload raw device-wide events, show exactly what each partner can see, allow revocation, and delete data promptly.

### P5: Malicious participant

Attempts to reserve slots without reciprocating, spoof package state, spam reminders, impersonate users, publish phishing links, enumerate private data, manipulate clocks, replay requests, and report fabricated activity.

Design response: server-side authorization and invariants, rate limits, canonical link checks, abuse reporting, audit records without sensitive payloads, and cautious status language.

### Stage map

| Stage | User need | Likely fear or failure | Required safeguard |
|---|---|---|---|
| Sign in | Start quickly | Wrong Google account, cancelled OAuth, account confusion | Show selected account, support retry and sign-out, never infer Play identity |
| Username | Recognizable public identity | Doxxing, impersonation, offensive names | Explain visibility, validate, normalize, reserve uniqueness server-side |
| Permission education | Understand value and risk | Spyware concern, accidental denial | Plain-language rationale, optional flow, settings-return recheck |
| Create listing | Publish correct app | Wrong package, phishing URL, accidental duplicate | Parse and cross-check URLs, preview before publish, edit capability |
| Join | Know exact commitment | Slot race, hidden obligations, wrong account | Confirmation summary, atomic reservation, clear leave rules |
| Install and opt in | Reach correct Play destination | Malformed or mismatched link | Trusted-host allowlist, package match, external-app warning |
| Daily testing | Know today's actions | False no-activity accusation, inaccessible app | Timestamped evidence, manual refresh, explicit Open button, uncertainty copy |
| Reminders | Recover missed action | Harassment and notification fatigue | One reminder per pair per day, quiet hours, block and report |
| Completion or leave | End cleanly | Partner loses slot unexpectedly | Confirmation, reciprocal status update, no false blame |
| Delete account | Leave permanently | Hidden retention or accidental deletion | Reauthentication, consequence summary, immediate access revocation, completion receipt |

## 4. Threat model

### Assets

- Google account identifier and tokens
- Public username and profile metadata
- Listing ownership, package IDs, Play links, tasks, and slot limits
- Commitment relationships between users
- Install observations and activity summaries
- Notification tokens and reminder history
- Account-deletion requests and audit metadata
- User trust in status labels

### Trust boundaries

1. Android device to TestLoop client
2. TestLoop client to backend API
3. Backend API to database and scheduled jobs
4. Backend to push notification provider
5. TestLoop to Google OAuth and external Play URLs
6. One user to another through listings, commitments, and reminders

The client is untrusted. Device clocks, local databases, API requests, package observations, and usage summaries can all be modified on rooted devices or through a custom client. Authorization and reciprocal invariants must be enforced server-side.

### Abuse, fraud, spoofing, and privacy cases

| ID | Threat | Attack | Harm | Required mitigation | Release severity |
|---|---|---|---|---|---|
| T01 | OAuth account confusion | User signs into TestLoop with account A but Play uses account B | False confidence about enrollment | Explicit disclaimer, never label account as Play-enrolled | Blocker if claimed as verified |
| T02 | Username impersonation | Attacker registers a visually similar or case variant name | Reputation theft and harassment | Unicode normalization, case-insensitive uniqueness, confusable review strategy | High |
| T03 | Phishing listing | Publisher submits a lookalike or redirect URL | Credential theft or malware | HTTPS-only trusted hosts, reject userinfo, redirects, IP hosts, and mismatched package IDs | Blocker |
| T04 | Package mismatch | Install link targets package B while listing declares package A | Wrong install and false tracking | Parse canonical package from every Play URL and require exact equality | Blocker |
| T05 | Sideload spoof | Attacker installs any APK with the target package ID | False install status | Label only as package detected, document source uncertainty, never verify Play install | Blocker if represented as Play install |
| T06 | Usage fabrication | Modified client uploads arbitrary minutes | False partner evidence | Treat client data as untrusted, cap impossible values, attach source and collection timestamp, avoid verified language | High |
| T07 | Clock manipulation | User changes time zone or device date | Extra days or backdated activity | Server timestamps for commitment age, explicit reporting-zone policy, deduplicate by server-defined day | Blocker |
| T08 | Slot hoarding | Bot or user joins many listings and never reciprocates | Denial of service to publishers | Atomic slot claims, reasonable active-commitment caps, expiration, abuse controls | High |
| T09 | Double allocation | Concurrent join requests consume final slot twice | Broken commitments and distrust | Database transaction or constraint, idempotency key, deterministic loser response | Blocker |
| T10 | Reciprocal state split | A sees active while B sees pending or left | Conflicting obligations | One server record or transactionally linked records with invariant checks | Blocker |
| T11 | IDOR | User changes listing, commitment, or user ID in API request | Private data exposure or unauthorized mutation | Row-level authorization on every read and write, negative API tests | Blocker |
| T12 | Excessive data response | API returns emails, OAuth IDs, raw usage events, or push tokens | Privacy breach | Explicit response schemas, field allowlists, log redaction | Blocker |
| T13 | Reminder harassment | User repeatedly sends reminders or targets former partners | Abuse and notification fatigue | Active-pair authorization, daily cap, cooldown, block/report, no custom text in V1 | High |
| T14 | Notification leakage | Lock-screen text names private test apps or behavior | Sensitive disclosure | Privacy-safe channel and lock-screen copy, configurable previews | High |
| T15 | Deleted-user resurrection | Offline queue recreates records after deletion | Violates deletion promise | Server tombstone or account epoch, reject stale writes and tokens | Blocker |
| T16 | Token replay | Stolen or old token remains valid after deletion/sign-out | Account takeover or data recreation | Short-lived sessions, refresh revocation, backend deletion checks | Blocker |
| T17 | Cross-profile inference | App reports packages or activity from work profile or another user | Enterprise/privacy breach | Test each profile boundary, process only data visible to current profile | Blocker |
| T18 | Broad app inventory collection | Client scans or uploads unrelated installed apps | Policy and privacy violation | Query only explicitly joined package IDs, prohibit `QUERY_ALL_PACKAGES` in V1 | Blocker |
| T19 | Raw usage exfiltration | Client uploads device-wide events then filters server-side | Severe privacy breach | Filter on device before serialization, network inspection test, no raw event storage | Blocker |
| T20 | Enumeration | Attacker pages through user or commitment IDs | Relationship graph exposure | Scope endpoints, opaque IDs, authorization, rate limiting | Blocker |
| T21 | Listing injection | Task or username contains control characters, HTML, script, or oversized text | UI breakage, logs corruption, web-view injection | Length limits, safe rendering, control-character rejection | High |
| T22 | Self-reciprocity | User joins their own listing or two accounts collude | Slot manipulation | Reject owner as tester, flag suspicious high-volume patterns | High |
| T23 | Stale completion | One side completes while queued leave/reminder arrives later | State rollback or harassment | Monotonic state transitions and server version checks | Blocker |
| T24 | Data inference after leave | Former partner can still view activity | Ongoing surveillance | Stop sharing at leave/completion, revoke reads immediately | Blocker |

## 5. High-risk Android cases

### Usage Access

- The manifest declaration alone does not grant access. The user must grant it in system Settings.
- Returning from Settings without granting must not advance onboarding or show tracking as enabled.
- Permission can be revoked while the app is open, backgrounded, or offline.
- Android may return no data while the device is locked. Empty results must be handled as unavailable, not zero activity.
- Raw usage events are retained only for a limited period. V1 must not promise historical reconstruction.
- Aggregated usage intervals may extend beyond the exact requested time range. Calculations need explicit local-day clipping or event-based logic and tests around midnight.
- Split-screen, picture-in-picture, rapid task switching, crashes, screen-off, and force-stop can distort foreground duration.
- The app must filter locally to packages in active commitments before any upload.

### Package visibility and installation

- Android 11 and later filter package queries. V1 must not request `QUERY_ALL_PACKAGES`.
- Dynamic arbitrary package IDs make static `<queries><package>` entries insufficient. Validate the chosen implementation against each supported API level before promising reliable install detection.
- Attempting to open a URL with `ACTION_VIEW` does not require broad package visibility, but the app must catch `ActivityNotFoundException`.
- A package can be installed but disabled, archived, hidden by an OEM launcher, lack a launcher activity, or exist only in another Android profile.
- Multiple apps cannot normally share a package ID in one profile, but a sideloaded build can replace or represent the expected package without proving Play origin.
- Uninstall signals are not guaranteed when TestLoop is force-stopped, offline, or not running. Show a last-checked timestamp.

### Notifications

- Android 13 and later start with notifications off until permission is granted.
- Allow, deny, and dismiss are distinct paths.
- Users can block one channel, all channels, or revoke permission later.
- Reminder delivery is best effort. A sent server event is not proof of device delivery or user action.
- Notification taps must route to the correct active commitment and safely handle expired or deleted targets.

### Google sign-in and account state

- Cancellation, network loss, revoked consent, disabled Google account, stale session, account switching, and device restore must be tested.
- A Google email must never be public by default.
- Backend identity must come from validated tokens, never a client-submitted user ID or email.

## 6. State model and invariants

Recommended commitment states:

`invited -> pending_reciprocal -> active -> completed | left | cancelled | expired`

Recommended evidence states are independent:

- Opt-in page: `not_opened | opened_at`
- Package: `unknown | detected | not_detected`, always with `last_checked_at`
- Activity: `unavailable | none_detected | detected`, always with reporting date, zone, duration estimate, and collected timestamp
- Notification: `not_requested | allowed | denied | channel_blocked | unknown`

Required invariants:

1. A user cannot join their own listing.
2. Active slot count equals active plus reserved commitments according to one documented rule.
3. Slot allocation is atomic and never negative or above capacity.
4. Repeated join, leave, complete, upload, and reminder requests are idempotent.
5. A reciprocal pair cannot be active on one side and pending, left, or completed on the other.
6. Server time determines join age and state transitions.
7. Activity summaries are unique by commitment, tester, package, and reporting date.
8. A stale client cannot move a terminal state back to active.
9. Only current participants and the listing owner can read commitment evidence.
10. Leaving or deletion immediately ends future evidence sharing.
11. Deletion invalidates all sessions and queued client writes.
12. Public listing responses contain no email, OAuth subject, push token, device identifier, raw usage event, or private relationship data.

## 7. Test environment and evidence

### Minimum device matrix

| Dimension | Required coverage |
|---|---|
| Android versions | Lowest supported API, Android 11, Android 12, Android 13, Android 14, latest stable |
| Devices | Pixel reference device, Samsung One UI, one aggressive battery-management OEM such as Xiaomi, Oppo, or Vivo |
| Profiles | Primary profile, work profile, secondary user if supported |
| Display | Small phone, large phone or tablet, font scale 1.0, 1.3, and 2.0, display size default and largest |
| Themes | Light, dark, high contrast where available |
| Network | Wi-Fi, mobile data, offline, high latency, packet loss, captive portal |
| Account | One Google account, multiple Google accounts, revoked Google consent |
| App state | Fresh install, upgrade, restored backup, force-stopped, background restricted |

Use at least three TestLoop accounts: Publisher A, Tester B, and Attacker C. Use at least two test packages: Package X with a launcher activity and Package Y with no launcher activity or a disabled launcher. Keep backend timestamps, request IDs, screenshots, screen recordings, and network captures for all release-blocking failures. Network captures must use synthetic accounts and must be stored securely.

## 8. Executable manual QA scenarios

Each scenario must be run on every relevant API level. Record actual result, build number, device, Android version, account, network state, and evidence link.

### ONB-01: Cancel Google sign-in

Priority: P0  
Precondition: Fresh TestLoop install with no session.  
Steps:

1. Tap `Continue with Google`.
2. Dismiss the account chooser or press Back.
3. Repeat once while offline.

Expected:

- The app remains signed out, does not create a partial account, and shows a plain retry message.
- No username or permission screen is skipped.
- Back works and no loading spinner persists.

### ONB-02: Switch Google account

Priority: P0  
Precondition: Device has Google accounts A and B.  
Steps:

1. Sign in with A, set username `publisher_a`, then sign out.
2. Sign in with B.
3. Inspect profile, listings, commitments, and cached screens.

Expected:

- B receives a distinct account and sees none of A's private data.
- A's email, username, cached evidence, and push destinations do not appear for B.
- UI states that TestLoop sign-in does not determine the Google account used by Play.

### ONB-03: Username validation and race

Priority: P0  
Precondition: Two devices with different signed-in accounts.  
Steps:

1. On both devices enter `TestUser` and submit at the same time.
2. Try `testuser`, leading and trailing spaces, an empty name, 1 character, maximum length, maximum plus 1, emoji, right-to-left text, newline, zero-width characters, and a confusable name such as `TestUsеr` using a Cyrillic character.
3. Restart the app during submission.

Expected:

- Only one account obtains the normalized unique name.
- Validation rules are clear before submission and enforced server-side.
- Control characters and invisible-only names are rejected.
- Restart cannot create duplicate profiles or bypass validation.

### ONB-04: Usage Access denied or abandoned

Priority: P0  
Precondition: Usage Access is off.  
Steps:

1. Open the Usage Access education screen.
2. Verify it explains that Android grants broad access and that TestLoop processes only joined apps.
3. Open Settings, do not enable access, and return.
4. Repeat, enable access, return, then revoke it while TestLoop is backgrounded.

Expected:

- Returning without access never shows `Enabled`.
- V1 remains usable for listing and coordination if tracking is optional.
- Revocation is detected on resume and before each collection job.
- No activity is uploaded after revocation.

### ONB-05: Notification allow, deny, and dismiss

Priority: P1  
Precondition: Fresh install on Android 13 or later.  
Steps:

1. Trigger the notification education UI after a contextually relevant action.
2. Deny the system prompt.
3. Confirm core app use still works.
4. Reset permission, trigger again, dismiss the prompt without choosing.
5. Grant permission, then disable only the reminder channel in Settings.

Expected:

- Denial is not treated as an onboarding failure.
- Dismissal stays undecided and does not loop prompts.
- Channel-specific status is reflected accurately.
- The app provides a settings path but does not nag on every launch.

### ONB-06: Process death and rotation during onboarding

Priority: P1  
Precondition: Partially completed onboarding.  
Steps:

1. Enter a username but do not submit.
2. Rotate the device, change theme, background the app, and kill the process.
3. Reopen TestLoop.
4. Repeat while returning from system permission Settings.

Expected:

- No duplicate submission occurs.
- Completed steps remain complete and incomplete steps are recoverable.
- Sensitive data is not exposed in Recents snapshots if the product chooses to protect it.

### LST-01: Create a valid listing

Priority: P0  
Precondition: Publisher A is signed in. Package X has known valid Play test and store links.  
Steps:

1. Enter Package X ID, opt-in link, install link, 12 slots, and a short test task.
2. Review the preview.
3. Publish once, then rapidly tap Publish five more times.

Expected:

- Exactly one listing is created.
- Parsed package IDs match across fields.
- Listing shows the correct owner, capacity, links, and task.
- The UI calls all evidence TestLoop observations, not Google verification.

### LST-02: Reject package and link mismatch

Priority: P0  
Precondition: Package X and Package Y are known.  
Steps:

1. Enter Package X in the package field.
2. Paste Package Y's opt-in link and Package X's install link.
3. Attempt preview and publish.
4. Reverse the mismatch and repeat.

Expected:

- Publication is blocked in both cases.
- Error identifies the mismatched field and expected package without opening either link.

### LST-03: Reject dangerous and malformed links

Priority: P0  
Precondition: Publisher A is on create listing.  
Steps:

1. Try `http`, `javascript:`, `intent:`, `file:`, `content:`, mixed-case host, userinfo such as `play.google.com@evil.example`, an IP address, Unicode lookalike host, URL shortener, redirect URL, extra whitespace, newline injection, fragments, duplicate `id` parameters, missing package, and an overlong URL.
2. Try valid canonical Play URLs with harmless parameter order changes.

Expected:

- Only explicitly supported HTTPS hosts and paths are accepted.
- Host parsing is structural, not string-prefix based.
- Unsupported redirects and ambiguous duplicate parameters are rejected.
- Valid canonical variants are normalized consistently.

### LST-04: Content injection and size limits

Priority: P0  
Precondition: Publisher A is on create listing.  
Steps:

1. Put HTML, script tags, Markdown links, SQL-like text, emoji sequences, bidirectional controls, 10,000 characters, and repeated newlines into app name and task fields.
2. Publish each accepted input and view it on another device and any admin surface.

Expected:

- Text renders as inert text with no script, link, or layout execution.
- Length and line limits are enforced client-side and server-side.
- Bidirectional or control-character abuse is rejected or safely isolated.
- Logs remain parseable and do not contain unescaped injected lines.

### LST-05: Duplicate and edited listings

Priority: P1  
Precondition: Publisher A already owns a listing for Package X.  
Steps:

1. Attempt to create a second active listing for Package X.
2. Edit slots below current active commitment count.
3. Change the package ID after users have joined.
4. Change task text while Tester B is offline.

Expected:

- Product applies a documented duplicate policy consistently.
- Capacity cannot be reduced below committed occupancy.
- Package ID cannot silently change for an active commitment.
- Material edits are versioned or clearly surfaced to existing participants.

### LST-06: Unauthorized listing mutation

Priority: P0  
Precondition: Publisher A owns a listing. Attacker C can inspect network traffic.  
Steps:

1. As C, replay A's update and delete requests using C's valid token and A's listing ID.
2. Modify owner ID in create and update payloads.
3. Request any non-public listing detail endpoint directly.

Expected:

- Every unauthorized operation returns 401 or 403 without revealing private fields.
- Ownership always comes from validated authentication context.
- A's listing remains unchanged.

### CMT-01: Join an available reciprocal commitment

Priority: P0  
Precondition: A and B each own one listing with available slots.  
Steps:

1. B opens A's listing and taps Join.
2. Review the reciprocal package, task, privacy summary, and leave consequences.
3. Confirm once.
4. Observe both accounts.

Expected:

- One reciprocal commitment is created with one canonical state.
- Both dashboards show the same partner, packages, start timestamp, and state.
- Exactly one slot is reserved according to the documented capacity rule.

### CMT-02: Race for the final slot

Priority: P0  
Precondition: A's listing has exactly one slot. B and C have eligible reciprocal listings.  
Steps:

1. Place B and C on A's confirmation screen.
2. Confirm on both devices within the same second.
3. Refresh all dashboards and query backend records.

Expected:

- Exactly one tester succeeds and one receives an unambiguous full-slot response.
- Capacity is zero, never negative.
- No orphan reciprocal record or phantom slot exists.

### CMT-03: Duplicate and replayed join

Priority: P0  
Precondition: B can join A.  
Steps:

1. Tap Join repeatedly under high latency.
2. Capture the successful request and replay it ten times.
3. Kill and reopen the app before the first response completes.

Expected:

- One commitment exists and one slot is consumed.
- Replays return the existing result or a safe idempotent response.

### CMT-04: Reject self-join and invalid reciprocity

Priority: P0  
Precondition: A owns listings X and Y.  
Steps:

1. Attempt to join A's listing as A through the UI.
2. Send the equivalent API request directly.
3. Attempt to offer an archived, full, deleted, or mismatched listing as the reciprocal listing.

Expected:

- All attempts are rejected server-side without consuming slots.
- Error messages explain the recoverable action without exposing internals.

### CMT-05: Simultaneous leave and complete

Priority: P0  
Precondition: A and B share an active commitment.  
Steps:

1. Put both devices offline and queue actions if supported.
2. A chooses Complete while B chooses Leave.
3. Restore connectivity simultaneously.
4. Refresh both clients and inspect backend history.

Expected:

- A deterministic terminal outcome is applied according to a documented rule.
- Both users see the same outcome.
- Slots are released exactly once and no future reminders are allowed.

### CMT-06: Stale client cannot roll back terminal state

Priority: P0  
Precondition: Device 1 and Device 2 are signed into B. Both cache the same active commitment.  
Steps:

1. On Device 1, complete or leave the commitment online.
2. On Device 2, while offline, trigger refresh, reminder, or any active-state write.
3. Reconnect Device 2.

Expected:

- Terminal state remains terminal.
- Stale writes are rejected using record version or equivalent concurrency control.
- Device 2 refreshes to the server state without recreating evidence sharing.

### CMT-07: Private relationship enumeration

Priority: P0  
Precondition: A and B have a private commitment. C has a valid unrelated account.  
Steps:

1. As C, request sequential, random, and observed commitment IDs.
2. Query A and B profile endpoints, listing feeds, and activity endpoints.
3. Vary pagination, filters, nested selects, and owner/tester parameters.

Expected:

- C cannot discover that A and B are paired or retrieve evidence.
- Public responses expose only intentionally public fields.
- Errors do not distinguish valid private IDs from nonexistent IDs where that distinction enables enumeration.

### TRK-01: Detect a joined package only

Priority: P0  
Precondition: Usage Access is granted. Package X is joined and installed. Several unrelated apps are installed.  
Steps:

1. Open Package X for 2 minutes and unrelated App Z for 3 minutes.
2. Trigger collection and inspect local logs, serialized request body, backend row, and partner UI.

Expected:

- Only Package X summary is serialized and uploaded.
- No package name, event, or duration for Z appears in network traffic, logs, database, analytics, or crash reports.
- Partner sees `Activity detected` with an estimate and collection timestamp.

### TRK-02: Permission revoked during collection

Priority: P0  
Precondition: An active commitment and granted Usage Access.  
Steps:

1. Start or schedule collection.
2. Revoke Usage Access immediately in Settings.
3. Lock and unlock the device, then open TestLoop.

Expected:

- The client handles null, empty, or security failures without crash.
- No stale duration is labeled as current.
- Status becomes `Activity unavailable` with a clear action for the tester only.
- Partner does not receive the private reason unless explicitly designed and disclosed.

### TRK-03: Midnight and partial-day clipping

Priority: P0  
Precondition: Device time zone is Asia/Kolkata. Package X is active.  
Steps:

1. Open X from 23:58 to 00:03 local time.
2. Sync after midnight.
3. Inspect both day summaries.
4. Repeat in a zone with a daylight-saving transition.

Expected:

- Duration is split or assigned according to the documented reporting-day policy.
- The same foreground interval is never counted twice.
- Day labels include enough zone context to prevent partner confusion.

### TRK-04: Split-screen, picture-in-picture, lock, and crash

Priority: P1  
Precondition: X supports relevant modes.  
Steps:

1. Use X in split-screen with another app for 2 minutes.
2. Put X in picture-in-picture for 2 minutes.
3. Lock the screen while X is foregrounded for 2 minutes.
4. Force X to crash or force-stop it.
5. Sync after each phase.

Expected:

- No negative, overlapping, or impossible duration is produced.
- The UI calls the value foreground activity estimate, not active hands-on testing.
- Screen-off time is handled according to the documented algorithm.

### TRK-05: Package absent, disabled, archived, and wrong profile

Priority: P0  
Precondition: Active commitment for X. Device supports archive or work profile where available.  
Steps:

1. Check status with X never installed.
2. Install X, refresh, then disable or archive it and refresh.
3. Install X only in the work profile while TestLoop runs in personal profile.
4. Re-enable or restore X.

Expected:

- Every state includes a last-checked timestamp and uncertainty.
- TestLoop never says `definitely uninstalled`.
- It does not cross profile boundaries or claim visibility it lacks.
- Restored state does not create a second commitment or reset join age.

### TRK-06: Sideloaded package with matching ID

Priority: P0  
Precondition: A debug or alternate build using Package X ID can be installed in a controlled environment.  
Steps:

1. Install the alternate build outside Play.
2. Refresh package and activity evidence.
3. View the result as both users.

Expected:

- The strongest label is `Package detected on this device`.
- No UI or API field claims Play installation, enrollment, signature verification, or Google verification unless a separate trustworthy mechanism exists.

### TRK-07: Open app action

Priority: P0  
Precondition: Active commitments for X and Y, where X has a launcher and Y does not.  
Steps:

1. Tap Open for installed X.
2. Return and uninstall X, then tap Open again.
3. Tap Open for Y.
4. Disable the default browser and open opt-in and install links.

Expected:

- X launches only after the tap.
- Missing or unlaunchable apps produce a recoverable message and an install-link option.
- No crash occurs from `ActivityNotFoundException`.
- TestLoop never auto-launches target apps in the background.

### TRK-08: Impossible and fabricated usage upload

Priority: P0  
Precondition: C can replay or modify its own authenticated API request.  
Steps:

1. Upload negative duration, over 24 hours for one day, future timestamps, dates before joining, duplicate records, wrong package, wrong commitment, NaN-like values, and extreme integers.
2. Replay a previously valid summary after leaving.

Expected:

- Server rejects invalid range, ownership, package, date, and state combinations.
- Duplicate valid summaries are idempotent or safely replace the correct version.
- Upload after leave is rejected.
- No invalid value reaches partner UI.

### TRK-09: Genuine zero activity and unknown activity

Priority: P0  
Precondition: B has an active commitment for installed Package X and Usage Access is granted.  
Steps:

1. Do not open X during the entire documented reporting day, then sync while unlocked and online.
2. On the next day, keep the device locked during collection so Android returns unavailable or empty data where reproducible.
3. On the third day, revoke Usage Access before collection.
4. Compare all three days as B and as partner A.

Expected:

- A confirmed complete query with zero observed use may show `No activity detected`.
- Locked, revoked, unsynced, and query-failure states show `Unavailable` or `Not synced`, never `No activity`.
- No state labels B dishonest, non-cooperative, or non-compliant.
- Each status includes the relevant date and last collection or sync time.

### OFF-01: Create and join while network fails

Priority: P0  
Precondition: Network proxy can drop requests after upload but before response.  
Steps:

1. Submit a listing, then drop the response.
2. Reconnect and retry.
3. Repeat for Join and Leave.

Expected:

- Each logical action occurs once.
- The client reconciles unknown outcomes from the server instead of assuming failure.
- No duplicate listing, commitment, slot change, or terminal transition occurs.

### OFF-02: Conflicting edits from two devices

Priority: P0  
Precondition: A is signed in on two devices with the same listing cached.  
Steps:

1. Take both devices offline.
2. Change task on Device 1 and capacity on Device 2.
3. Reconnect in both possible orders.

Expected:

- Conflict strategy is explicit and deterministic.
- One stale full-record update cannot silently overwrite an unrelated newer field.
- Existing commitments remain valid.

### OFF-03: Device clock and time-zone manipulation

Priority: P0  
Precondition: A and B have an active commitment.  
Steps:

1. Note the server join time.
2. Move B's device date forward 20 days and reopen TestLoop.
3. Move it backward 10 days and sync.
4. Change zones across the International Date Line without changing actual time.

Expected:

- `Days since joining` is based on server time and cannot be advanced by device clock.
- Activity-day assignment follows the documented policy without duplicate or missing server records.
- No automatic completion is triggered by local clock changes.

### OFF-04: Long offline period

Priority: P1  
Precondition: B has an active commitment and Usage Access.  
Steps:

1. Keep B offline for longer than Android retains detailed usage events.
2. Use X on several days.
3. Reconnect and sync.

Expected:

- The app does not invent or reconstruct unavailable daily history.
- Missing days are shown as unavailable or not synced, not no activity.
- User-facing copy explains the limitation without blaming B.

### NTF-01: Reminder authorization and rate limit

Priority: P0  
Precondition: A and B have an active commitment. C is unrelated.  
Steps:

1. A sends B one reminder.
2. A immediately sends 10 more through UI and direct API replay.
3. C sends a request naming B and A's commitment.
4. A attempts another reminder after the commitment ends.

Expected:

- At most one permitted reminder is accepted within the documented cooldown.
- C and post-terminal attempts are rejected server-side.
- No slot, activity, or relationship data leaks in errors.

### NTF-02: Notification privacy and deep-link safety

Priority: P0  
Precondition: Notifications allowed; lock-screen previews enabled.  
Steps:

1. Receive every V1 notification type while the phone is locked.
2. Tap each notification after signing out, after switching account, and after the target listing or commitment is deleted.
3. Modify any deep-link ID if possible.

Expected:

- Lock-screen text does not expose sensitive app usage or accusations.
- Tap opens only authorized current data after authentication.
- Missing or unauthorized targets show a safe generic state.
- Deep links cannot bypass authorization or onboarding.

### NTF-03: Permission and channel revocation

Priority: P1  
Precondition: Notifications initially allowed.  
Steps:

1. Revoke all notifications in Android Settings while TestLoop is backgrounded.
2. Send a reminder from the partner.
3. Re-enable notifications but keep the reminder channel blocked.
4. Open TestLoop.

Expected:

- App remains functional and accurately reflects disabled delivery capability.
- It does not claim the reminder was delivered.
- In-app pending actions remain discoverable.

### NTF-04: Package disappearance notification

Priority: P0  
Precondition: A and B have an active commitment, X was previously detected on B's device, and notifications are allowed.  
Steps:

1. Uninstall X from B's current profile and trigger the next permitted package check.
2. Observe B's local state and any notification sent to A.
3. Repeat by disabling, archiving, or moving X to another profile where supported.
4. Repeat while B is offline, then reconnect.

Expected:

- Any notification uses uncertain language such as `Package no longer detected` and includes a last-checked time.
- It never states that B definitely uninstalled, left the Play test, or acted dishonestly.
- Repeated checks are deduplicated and do not spam A.
- Ambiguous disabled, archived, profile, offline, and visibility cases do not become definitive uninstall claims.

### NTF-05: Reciprocal install reminder

Priority: P0  
Precondition: A and B have an active reciprocal commitment. A's package is detected on B, but B's package is not detected on A.  
Steps:

1. Trigger the system's reciprocal-install reminder for A.
2. Install B's package and refresh, then attempt to trigger the same reminder again.
3. Make package detection unavailable rather than absent and trigger the scheduler.
4. End the commitment and trigger the scheduler once more.

Expected:

- A receives at most one rate-limited reminder while absence is confidently observed.
- No reminder is sent after detection, during an unknown state, or after the commitment ends.
- Notification opens the correct active commitment and offers a safe install or opt-in action.

### DEL-01: Delete account with reauthentication

Priority: P0  
Precondition: A owns a listing and has active and completed commitments plus usage summaries.  
Steps:

1. Open profile and choose Delete account.
2. Review the explicit data and relationship consequences.
3. Cancel once and verify no change.
4. Repeat, reauthenticate, and confirm.

Expected:

- Cancellation changes nothing.
- Confirmation immediately revokes A's access and terminates active sharing.
- Listings are removed or anonymized according to the disclosed policy.
- Partners receive a neutral unavailable/left state, not A's email or deletion reason.

### DEL-02: Verify deletion across storage and services

Priority: P0  
Precondition: DEL-01 completed with synthetic account A.  
Steps:

1. Query all database tables, object storage, authentication records, notification tokens, caches, search indexes, logs, backups policy, and third-party processors using A's internal ID.
2. Attempt all old authenticated requests.
3. Ask B to open cached A details.

Expected:

- Personal data is deleted or irreversibly anonymized within the stated period.
- Any retained security record is minimal, justified, access-controlled, and disclosed.
- Old tokens fail and B cannot retrieve deleted evidence.
- No push is sent to A's former device token.

### DEL-03: Offline resurrection after deletion

Priority: P0  
Precondition: Device 1 and Device 2 use A. Device 2 is offline with queued edits and activity uploads.  
Steps:

1. Delete A on Device 1.
2. Reconnect Device 2.
3. Let every queued operation retry.
4. Restart Device 2 and attempt token refresh.

Expected:

- All queued writes are rejected.
- A is not recreated and no deleted listing or activity reappears.
- Device 2 clears private local data and returns to sign-in.

### DEL-04: Recreate with the same Google account

Priority: P0  
Precondition: A was deleted successfully.  
Steps:

1. Sign in again with the same Google account.
2. Select a new username and complete onboarding.
3. Inspect old listings, relationships, reminders, and evidence.

Expected:

- Behavior matches the disclosed recreation policy.
- The new account cannot access old private data or inherit old active commitments.
- A deleted username is released or reserved according to an explicit abuse-prevention policy.

### DEL-05: External deletion path

Priority: P0  
Precondition: Public deletion URL is configured for the Play listing.  
Steps:

1. Open the URL without TestLoop installed and in a private browser session.
2. Submit a deletion request using the documented identity-verification path.
3. Try to request deletion of another user.
4. Follow the full request to completion.

Expected:

- Page loads, names TestLoop, and provides a discoverable deletion request path.
- Identity is verified without exposing whether arbitrary accounts exist.
- The request reaches the same deletion guarantees as in-app deletion.

### SEC-01: API authorization matrix

Priority: P0  
Precondition: A owns Listing X, B is paired with A, C is unrelated, and one anonymous client is available.  
Steps:

1. For every endpoint, execute the action as anonymous, A, B, and C.
2. Repeat with missing, expired, malformed, and valid-but-wrong-user tokens.
3. Change resource IDs and nested owner/tester IDs.

Expected:

- Access matches a written endpoint authorization matrix.
- No client-provided identity overrides the authenticated identity.
- Unauthorized responses contain no sensitive object fields.

### SEC-02: Sensitive data network and log audit

Priority: P0  
Precondition: Debuggable QA build and production-like release build with synthetic data.  
Steps:

1. Exercise sign-in, create, join, tracking, reminder, sign-out, and deletion.
2. Capture HTTPS payloads in the authorized QA setup and inspect logcat, crash breadcrumbs, local files, clipboard, screenshots, and backups.
3. Search for email, OAuth token, refresh token, raw usage event, unrelated package, push token, and full deletion payload.

Expected:

- Only minimum declared fields cross the network.
- Tokens, unrelated packages, and raw usage events never appear in logs or crash reports.
- Release logs do not expose personal data.
- Local sensitive data is encrypted or avoided and is cleared on sign-out/deletion.

### SEC-03: Session revocation and token replay

Priority: P0  
Precondition: Capture A's own test session token in an authorized environment.  
Steps:

1. Sign out A and replay the token.
2. Sign in again, delete A, and replay old access and refresh tokens.
3. Revoke Google consent and test refresh behavior.

Expected:

- Behavior follows the documented sign-out revocation model.
- Deletion invalidates all sessions immediately.
- Revoked or expired tokens cannot read, mutate, or recreate data.

### SEC-04: Scope exclusion audit

Priority: P0  
Precondition: Candidate release APK or bundle.  
Steps:

1. Inspect manifest permissions, merged manifest, SDK dependency list, network destinations, UI routes, and feature flags.
2. Search for payment, donation, rating, chat, advertising, broad package scan, and `QUERY_ALL_PACKAGES` capabilities.

Expected:

- V1 contains none of the excluded capabilities or dormant third-party SDK collection.
- Any unexpected permission, SDK, host, or feature blocks release until removed or explicitly reviewed.

### A11Y-01: Screen reader journey

Priority: P0  
Precondition: TalkBack enabled with gesture navigation.  
Steps:

1. Complete sign-in, username, create listing, browse, join, open app, send reminder, leave, and delete account using TalkBack only.
2. Traverse every status card and permission explanation.

Expected:

- Focus order follows visual order and never traps.
- Every control has a unique, action-oriented label.
- Status is not conveyed only by color or icon.
- Dynamic changes announce once without reading the whole screen repeatedly.
- Destructive actions and external navigation are clearly announced.

### A11Y-02: Large text and display size

Priority: P0  
Precondition: Font scale 2.0 and largest display size.  
Steps:

1. Repeat the full primary journey on a small supported phone.
2. Enter maximum-length valid names, tasks, and app labels.
3. Open dialogs, bottom sheets, errors, snackbars, and system-settings return states.

Expected:

- No clipped text, overlap, hidden button, horizontal scrolling, or inaccessible content.
- Screens scroll and retain context.
- Primary and destructive actions remain distinguishable.

### A11Y-03: Contrast, dark theme, and non-color status

Priority: P0  
Precondition: Light and dark themes, grayscale or color-correction mode.  
Steps:

1. Inspect all text, icons, disabled states, focus states, links, charts if any, and status chips.
2. Compare `detected`, `not detected`, `unavailable`, `pending`, and `completed` without color.

Expected:

- Text and controls meet WCAG 2.1 AA contrast targets.
- Every state has a textual label or shape/icon distinction with an accessible name.
- Dark theme has no invisible dividers, icons, or system-bar content.

### A11Y-04: Touch, keyboard, switch access, and motion

Priority: P1  
Precondition: Hardware keyboard and Switch Access available. Remove animations enabled for a second pass.  
Steps:

1. Navigate and activate all primary flows without touch.
2. Inspect focus visibility and order.
3. Attempt controls near screen edges and repeated reminder buttons.

Expected:

- Interactive targets are at least 48 by 48 dp or have equivalent accessible hit areas.
- Keyboard and switch focus is visible and complete.
- Reduced-motion settings do not hide state changes or block progress.

### UX-01: Beginner comprehension test

Priority: P0  
Precondition: Recruit five participants who have never used Play closed testing. Do not coach them.  
Steps:

1. Ask each participant to create a listing, join a reciprocal test, open the partner app, interpret today's status, and find account deletion.
2. Ask them to explain what `install detected`, `activity detected`, and `days since joining` prove.
3. Record hesitation, wrong taps, requests for help, and interpretation errors.

Expected:

- At least 4 of 5 complete each primary task without assistance.
- Median time to identify the next required action is under 10 seconds per dashboard visit.
- All 5 understand that TestLoop does not verify Google enrollment or test quality.
- Any systematic misinterpretation of evidence is release-blocking until copy and hierarchy are corrected.

### UX-02: Error recovery and empty states

Priority: P0  
Precondition: Accounts representing no listings, no eligible reciprocal app, full listings, expired commitment, denied permissions, and offline mode.  
Steps:

1. Open every empty and error state.
2. Follow its primary action.
3. Press Back and retry after correcting the condition.

Expected:

- Every state says what happened, what it means, and the single best next action.
- No dead end requires reinstalling, clearing app data, or contacting support for a normal recoverable condition.
- Technical terms such as package visibility, API, token, and UsageStats are absent from beginner-facing copy.

## 9. Release-blocking acceptance criteria

A V1 release is blocked unless all criteria below are satisfied.

### Product truth

- No screen, notification, API field, analytics event, help page, or store copy claims Google verification, Play enrollment proof, genuine testing, or guaranteed eligibility.
- Install, uninstall, activity, reminder, and day-count states include accurate uncertainty and timestamp semantics.
- Google sign-in is never represented as the Google account used for Play opt-in.

### Security and integrity

- All P0 authorization, IDOR, concurrency, replay, stale-write, and deletion tests pass.
- Slot allocation and reciprocal transitions remain consistent under at least 100 concurrent join attempts in automated backend testing.
- Server-side constraints prevent self-join, capacity overflow, invalid package relationships, post-terminal evidence, and post-deletion writes.
- No high or critical findings remain from static analysis, dependency scanning, exported-component review, or an independent API authorization review.

### Privacy

- Network and storage inspection proves that unrelated package inventory and raw device-wide usage events are never uploaded.
- Usage collection is limited to active committed package IDs and stops immediately after revocation, leave, completion, sign-out, or deletion.
- Public APIs expose no email, OAuth identifier, push token, device identifier, raw usage event, or private relationship graph.
- Privacy policy, Data safety declaration, in-app disclosure, actual collection, retention, and deletion behavior agree exactly.
- In-app and external account-deletion paths pass end to end.

### Android reliability

- Core coordination remains usable when Usage Access and notifications are denied.
- Permission revocation, null usage results, package invisibility, missing launch activities, and external intent failure never crash the app or produce a false definitive status.
- Fresh install, upgrade, backup restore, force-stop, process death, and offline reconciliation pass on the device matrix.
- Crash-free sessions meet the team's launch target over a representative closed beta, with zero known data-loss defects.

### UX and accessibility

- All P0 TalkBack, large-text, contrast, keyboard, and beginner-comprehension scenarios pass.
- No primary flow has an unlabeled control, color-only state, focus trap, clipped critical copy, or inaccessible destructive action.
- Five-user beginner testing meets the completion and comprehension thresholds in UX-01.

### Scope and abuse control

- Candidate build contains no payments, donations, ratings, chat, ad SDK, `QUERY_ALL_PACKAGES`, or automatic broad app scanning.
- Reminders are authorized, rate-limited, generic, blockable, and unavailable after a commitment ends.
- Listing text and URLs are validated on the server and rendered safely on every surface.

## 10. Automation backlog

Manual scenarios establish behavior, but the following must become automated before public scale:

1. State-machine property tests for reciprocal commitments.
2. Database concurrency tests for final-slot allocation.
3. Authorization matrix tests for every API and table policy.
4. Idempotency and replay tests for all mutations.
5. Link parser fuzzing with Unicode, encodings, redirects, and duplicate parameters.
6. Date and time property tests across every IANA zone and daylight-saving transition.
7. Serialization tests proving only joined-package summaries can leave the device.
8. Compose accessibility checks plus screenshot tests at large font and dark theme.
9. Migration tests for upgrades, local database changes, and deletion tombstones.
10. Dependency and manifest diff checks that fail CI when a new permission, SDK, exported component, or network host appears.

## 11. External references used for risk assumptions

- [Android UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager): Usage Access is granted through Settings, queries require `PACKAGE_USAGE_STATS`, locked-device behavior can return no data, and detailed events are retained for only a few days.
- [Android package visibility](https://developer.android.com/training/package-visibility): Android 11 and later filter package visibility, and `QUERY_ALL_PACKAGES` is exceptional and subject to Play approval.
- [Android limited package visibility use cases](https://developer.android.com/training/package-visibility/use-cases): external activity and URL launches can fail and must handle `ActivityNotFoundException`.
- [Android notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission): Android 13 and later support allow, deny, and dismiss paths, with notifications off by default for new installs.
- [Google Play account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111): apps with account creation need an in-app deletion path and an external web resource for deletion requests.

## 12. Exit report format

For each test cycle, issue one report containing:

- Build, commit, environment, and backend schema version
- Devices and Android versions tested
- Scenario pass, fail, blocked, and not-applicable counts
- Open defects grouped by P0, P1, P2, and P3
- Privacy data-flow diff from the previous build
- Manifest, SDK, exported-component, and network-host diff
- Known limitations and exact user-facing copy
- Release recommendation: `BLOCK`, `LIMITED BETA`, or `RELEASE`

No release recommendation may be `RELEASE` while any P0 defect is open, any technical-truth claim is misleading, or deletion and authorization have not been verified end to end.
