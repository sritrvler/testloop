# TestLoop UX redesign specification

## 1. Design outcome

TestLoop should feel like a focused daily coordination tool, not a generic admin dashboard. A beginner must be able to answer these questions within five seconds:

1. Which apps do I need to test today?
2. Who is testing each of my apps?
3. What do I owe another member?
4. What does another member owe me?
5. What is the next safe action?

The visual personality is confident, bright, and precise. It combines the trust of deep blue with the momentum of electric indigo and a restrained mint success colour. Status is communicated with plain text, icons, and position, never by colour alone.

## 2. Current experience audit

The current implementation has sound privacy language, but the interface does not yet represent the product model.

### Structural misses

- `MyListingScreen` selects the first listing owned by `"you"`. It assumes one user has one app and cannot display a portfolio.
- The current own-listing view does not render testers even though `TesterCard` exists.
- Reciprocal status appears as low-priority text at the bottom of a commitment card. Users cannot quickly distinguish their obligation from the other person's obligation.
- The Today screen shows only apps the user is testing. It does not surface people who need attention across the user's own apps.
- Joining a listing does not ask which of the user's own apps should be tested in return. This makes reciprocity ambiguous when a user owns multiple apps.
- The create action exists only as a floating button in Discover, so adding a second own app is not discoverable from My Apps.
- Generic elevated cards, repeated rounded rectangles, and large gaps flatten the hierarchy. Important actions and informational blocks look equally important.
- Raw technical phrases such as "package not detected on this device" are too long for repeated status UI.
- The listing form is one long technical form. It does not separate identity, Play links, and test instructions into beginner-friendly steps.

### Visual misses

- Warm off-white, muted teal, low elevation, and repeated surface-coloured cards create a dull, low-energy page.
- There is no strong brand moment, progress visual, app artwork, or daily completion feedback.
- Typography relies on similar weights and sizes, so pages read as undifferentiated blocks.
- Status pills contain long sentences and compete for space.
- The screen has no distinctive component language beyond generic Material cards and buttons.

## 3. Product model required by the interface

The design must not model reciprocity at user level. It must model it between two specific app listings.

Example:

- Priya owns Focus Flow and Pantry Pal.
- Priya joins Alex's Budget Bee listing.
- Priya selects Focus Flow as the app Alex will test in return.
- The commitment pair is Budget Bee for Focus Flow.
- A later commitment can pair a different member's app with Pantry Pal.

Each reciprocal commitment needs:

- `commitmentId`
- `appBeingTestedByCurrentUserId`
- `currentUserOwnAppId`
- `otherUserId`
- `joinedAt`
- `targetDays`
- install and activity signals for both directions
- reminder availability for both directions
- lifecycle state such as pending setup, active, completed, or left

Never find an own app by username, owner label, list position, or package display name. Use the listing ID. The UI must always include both app names when a commitment is viewed outside a specific app detail screen.

## 4. Information architecture

Use four bottom navigation destinations:

1. **Today**: the fastest path to complete current obligations.
2. **My apps**: all apps published by the current user and the people testing each one.
3. **Discover**: find an app and create a reciprocal pair.
4. **Profile**: identity, notifications, tracking, privacy, and account controls.

Notifications are opened from a bell in the top app bar. They do not need a fifth bottom destination.

### Navigation rules

- Today is the default destination.
- My apps owns the primary `Add app` action.
- Discover has no floating add button. Its only primary goal is finding an app to test.
- A selected bottom destination preserves scroll and filter state.
- Tapping the selected destination again scrolls to the top.
- Detail pages use a standard back action and do not show bottom navigation.
- Deep-linked notifications open the exact app, tester, or reciprocal commitment in question.

## 5. Core language and status system

### Direction labels

Use these exact headings:

- **You owe them**: the current user's responsibility to test the other person's app.
- **They owe you**: the other person's responsibility to test the current user's selected app.

These headings must always be followed by an app name. Never show them as accusations without context.

Example:

```text
You owe them
Budget Bee
Opened today

They owe you
Focus Flow
No activity detected today
```

### Precise signal labels

Use:

- **Installed**: the target package was detected on the relevant device.
- **Not installed**: the target package was not detected.
- **Install status unavailable**: the signal could not be read.
- **Opened today**: foreground activity was detected today.
- **No activity detected today**: no foreground activity signal has been observed yet.
- **Activity unavailable**: Usage Access or signal data is unavailable.
- **Day 6 of 14**: elapsed campaign day based on the server join date.
- **8 days remaining**: target duration minus elapsed days, with a floor of zero.
- **Reminder sent today**: the daily reminder has already been used.

Use a short footer where needed:

> Activity is a device signal, not proof of Google Play testing or test quality.

Do not use:

- Failed tester
- Bad tester
- Inactive tester
- Did not test
- Verified tester
- Google verified
- Cheating

### Priority rules

Order states from most actionable to least actionable:

1. Not installed
2. Activity unavailable
3. No activity detected today
4. Opened today
5. Completed

A missing signal is not proof of bad behaviour. Reminder copy must stay neutral.

## 6. Visual system

### Colour

The recommended brand colour is energetic indigo. Blue communicates trust and reliability. A controlled violet undertone makes the product feel current and gives progress actions more energy than the existing muted teal.

Light theme:

| Token | Hex | Use |
| --- | --- | --- |
| Brand 700 | `#2639C6` | pressed primary actions, selected navigation text |
| Brand 600 | `#3F51E8` | primary buttons, links, active controls |
| Brand 100 | `#E7EAFF` | selected rows, progress backgrounds |
| Ink 950 | `#121526` | primary text |
| Ink 700 | `#3C4257` | secondary text |
| Ink 500 | `#687086` | metadata |
| Canvas | `#F7F8FC` | app background |
| Surface | `#FFFFFF` | focused surfaces and sheets |
| Divider | `#E5E8F0` | row separation |
| Success 700 | `#087A5B` | completed status text |
| Success 100 | `#DDF7ED` | completed status background |
| Attention 700 | `#9A5B00` | pending status text |
| Attention 100 | `#FFF0D5` | pending status background |
| Danger 700 | `#B4233C` | destructive actions and genuine errors |
| Danger 100 | `#FFE5EA` | destructive warning background |

Dark theme:

- Canvas `#0F1120`
- Surface `#181B2D`
- Brand primary `#AEB7FF`
- Primary text `#F5F6FF`
- Secondary text `#BCC2D6`
- Divider `#2A2F45`
- Success `#5BE1B5`
- Attention `#FFC56B`
- Danger `#FF8799`

Colour behaviour:

- Reserve brand indigo for navigation, primary actions, selected controls, and progress.
- Use green only for a positive signal detected today or a completed commitment.
- Use amber for something the user can address today.
- Use red only for broken links, destructive account actions, or a confirmed system error.
- Do not tint an entire page according to a person's status.
- Never rely on red versus green alone. Pair every state with an icon and label.
- Disable dynamic colour for the first branded release. It can be added later as an opt-in setting after the core identity is established.

### Typography

Use Roboto Flex if bundled without a network dependency. Otherwise use the Android system sans family.

| Style | Size / line height | Weight | Use |
| --- | --- | --- | --- |
| Display | 36 / 42 sp | 750 | Welcome brand line only |
| Screen title | 28 / 34 sp | 700 | Today, My apps, Discover |
| Hero number | 32 / 36 sp | 750 | Daily completed count, slot count |
| Section title | 18 / 24 sp | 700 | You owe them, Needs attention |
| App title | 17 / 22 sp | 650 | App names |
| Body | 15 / 21 sp | 450 | Instructions and descriptions |
| Label | 13 / 18 sp | 600 | Status and buttons |
| Metadata | 12 / 16 sp | 500 | Day count, timestamps |

Use sentence case. Do not use all caps. Use tabular figures for day counts, durations, and tester counts.

### Spacing and shape

- Base spacing unit: 4 dp.
- Screen horizontal gutter: 20 dp on phones.
- Top app bar content inset: 20 dp.
- Section gap: 28 dp.
- Related item gap: 8 dp.
- Standard row vertical padding: 14 dp.
- Minimum touch target: 48 by 48 dp.
- Primary button height: 52 dp.
- Standard corner radius: 16 dp.
- Small status background radius: 10 dp.
- Bottom sheet top radius: 28 dp.

Avoid cards as default containers. Prefer:

- clear section headings
- flat white sections
- grouped rows with subtle dividers
- one tinted momentum panel per screen at most
- bottom sheets for focused choices
- tonal backgrounds only for actionable or explanatory states

Use elevation only for the top app bar while scrolling, bottom sheets, floating snackbars, and the primary floating action on My apps.

### Icon and app artwork

- Use the actual app icon when available.
- Fallback icon: a two-letter monogram on a deterministic soft brand tint.
- Use outlined icons for inactive navigation and filled icons for the selected destination.
- Status icons: check circle for detected, clock for pending, phone-off or download for not installed, eye-off for unavailable, bell for reminder.
- Decorative illustration is allowed only on Welcome and meaningful empty states.

### Motion

- On completing today's final task, animate the progress ring to 100 percent and show a restrained check sparkle for 500 ms.
- Status updates crossfade over 180 ms.
- Expanding a tester row uses a 220 ms size transition.
- Respect Android's reduced-motion setting.
- Do not use looping animation.

## 7. Screen specifications

### 7.1 Welcome

Current screen needs a full redesign.

Content hierarchy:

1. Compact TestLoop wordmark and loop-check symbol.
2. Headline: **Keep every test commitment on track**
3. Supporting line: **See what to test today, who is testing your apps, and where reciprocity needs attention.**
4. A visual two-way commitment preview with two app icons connected by a loop.
5. Three short benefits:
   - Open today's test apps in one tap
   - See install and activity signals
   - Send one respectful reminder when needed
6. Primary button: **Continue with Google**
7. Privacy link: **How activity signals work**

Remove demo integration language from the production UI. Privacy detail opens a bottom sheet rather than occupying the main visual hierarchy.

### 7.2 Username setup

Current screen needs visual refinement and better completion context.

Content hierarchy:

1. Progress label: **Step 1 of 2**
2. Title: **Choose your public username**
3. Explanation: **Other members see this name. Your Google email stays private.**
4. Live avatar preview.
5. Username field with availability state.
6. Primary button: **Use this username**

Validation:

- Validate after 400 ms of typing pause.
- Keep the button disabled until the local format and remote availability checks pass.
- Explain a conflict inline and suggest two alternatives.
- Preserve the field if the network request fails.

### 7.3 Tracking setup

Add this screen after username setup, but do not open a system settings screen without a user tap.

Content:

1. Progress label: **Step 2 of 2**
2. Title: **Let TestLoop detect your progress**
3. Diagram showing only joined test apps flowing into TestLoop.
4. Plain explanation:
   - TestLoop checks only apps linked to your active commitments.
   - Members see install and daily activity signals for the app involved.
   - TestLoop does not show your full app history.
5. Primary button: **Allow Usage Access**
6. Secondary action: **Learn more**

After returning from system settings:

- If enabled, show a success state and continue.
- If not enabled, explain that activity tracking cannot work and offer **Try again**.
- Notification permission is requested later, after the first reminder-related value moment. It is not bundled with Usage Access.

### 7.4 Today

Current screen needs a structural and visual redesign.

The goal is to finish daily work, not inspect a dashboard.

Structure:

```text
[Good morning, Priya]                         [Bell]
[Thursday, 23 July]

[Momentum panel]
2 of 4 opened today              [progress ring]
Two quick tests left

YOU OWE THEM                                  [2 left]
[App task row]
[App task row]

THEY OWE YOU                                  [3 need attention]
[Compact attention row grouped by your app]

[Activity signal note]
```

Momentum panel:

- Use a brand gradient from `#3F51E8` to `#6B4DE6`.
- Keep it 124 to 148 dp high, not a giant hero.
- Show progress based only on current user's active test obligations.
- If there are no obligations, replace it with an action: **Find an app to test**.

You owe them rows:

- App icon and app name.
- Owner username.
- `Day 6 of 14` and `8 days remaining`.
- One status line: **Opened today**, **Not installed**, **No activity detected today**, or **Activity unavailable**.
- Primary inline action:
  - **Open** when installed.
  - **Install** when not installed.
  - **Fix access** when tracking is unavailable.
- Tapping the row opens Reciprocal commitment detail.

Rows are flat within one white section and separated by 1 dp dividers. Do not wrap every row in a card.

They owe you summary:

- Group by each own app.
- Example: **Focus Flow: 2 people need attention**
- Secondary line: **1 not installed, 1 not opened today**
- Tap opens that own app's tester list with the `Needs attention` filter applied.
- Do not put reminder buttons in this compact summary. Reminders require context and belong in the tester row or commitment detail.

Caught-up state:

- Headline: **You are caught up today**
- Supporting line: **All 4 test apps have activity detected.**
- Keep upcoming day counts visible below.
- Do not hide the They owe you section.

### 7.5 My apps portfolio

This is a new top-level screen and is required for multiple own apps.

Structure:

```text
[My apps]                                  [+ Add app]
[2 active apps]

[Focus Flow icon] Focus Flow
8 of 12 testers       3 need attention
[thin tester progress bar]

[Pantry Pal icon] Pantry Pal
12 of 12 testers      All active today
[thin tester progress bar]

[Archived apps]
```

Each portfolio row contains:

- app icon and title
- active tester count versus requested tester count
- slots remaining
- attention summary
- campaign age or end date
- 4 dp progress rail
- overflow menu with Edit listing, Pause listing, Archive, and Copy links

Use one grouped white surface with dividers, not independent cards.

Primary `Add app` action:

- Use a text button in the top bar on compact phones.
- A small extended floating action may appear while the list is long and the top action has scrolled away.
- Never hide add-app access under Profile.

Empty state:

- Title: **Add the app you need tested**
- Body: **Create a listing, choose how many testers you need, and share the Play links in one place.**
- Primary action: **Add your first app**
- Secondary action: **See how it works**

### 7.6 Own app detail

Replace the current single `My listing` screen with an app-specific detail screen.

Header:

- app icon, app name, and overflow menu
- `8 of 12 testers joined`
- progress rail
- `4 slots left`
- secondary action: **Share listing**

Below the header, use a sticky segmented control:

- **All 8**
- **Needs attention 3**
- **On track 5**

Tester row structure:

```text
[Avatar] @alex_dev                       Day 6 of 14
They owe you: Focus Flow
[Installed] [No activity detected today]

You owe them: Budget Bee
[Installed] [Opened today]                    [Open]

[Send reminder]
```

Collapsed row:

- avatar, username, day count
- a single two-line reciprocity summary
- attention marker when action is available

Expanded row:

- full two-direction details
- today's foreground duration when available, such as **12 min detected today**
- join date and target days remaining
- **Open their app**
- **Send reminder** only when their direction has no activity detected or is not installed
- **Message** can be added later, but should not be shown as a disabled placeholder
- overflow actions: Report issue, End commitment

Reminder behaviour:

- Button copy: **Send reminder**
- Confirmation sheet: **Remind @alex_dev about Focus Flow?**
- Body: **We will send one respectful reminder to open Focus Flow today.**
- Confirm: **Send reminder**
- After sending, replace the button with a quiet label: **Reminder sent today**
- Limit one reminder per person, per app, per local calendar day using a server-enforced rule.

Empty states:

- No testers: **No one has joined yet** with actions **Share listing** and **View your public listing**.
- Filter empty: **Everyone is on track** with body **No tester currently needs attention for this app.**
- Signal outage: **Activity signals are temporarily unavailable** with body **Do not send reminders until signals return.**

### 7.7 Reciprocal commitment detail

Add a dedicated screen reached from Today, Own app detail, and notifications.

Structure:

1. Pair header with both app icons and usernames.
2. Progress: **Day 6 of 14**, **8 days remaining**.
3. Two equal direction blocks:
   - **You owe them**
   - **They owe you**
4. Each block shows app name, install signal, activity signal, and today's duration if available.
5. Primary action follows the most urgent current-user task:
   - **Install Budget Bee**
   - **Open Budget Bee**
   - **Fix activity access**
6. Secondary action appears only when the other direction needs attention: **Send reminder**
7. Footer actions: Report issue, Leave commitment.

The two direction blocks may use a split white panel with a central loop connector. This is one of the few places where a distinctive branded container is justified.

### 7.8 Discover

Current screen needs improved hierarchy and trust cues.

Structure:

1. Title: **Discover**
2. Search field: **Search apps or usernames**
3. Horizontal filters:
   - Open slots
   - New
   - Fewest slots
   - Category
4. Result rows.

Listing result row:

- app icon and app name
- owner username and future rating placeholder only after ratings exist
- one-line test task
- `4 slots left`
- target duration
- `Posted today`
- chevron

Do not display a fake rating or disabled rating area before the feature exists.

Result states:

- Full listing: replace Join with **Waitlist unavailable** or hide it.
- User's own listing: label **Your app** and open management detail.
- Already joined: label **Joined** and open the existing commitment.
- Same package ID already owned by current user: prevent joining and explain why.

Empty search:

- Title: **No matching apps**
- Body: **Try the app name, package ID, or member username.**
- Action: **Clear filters**

### 7.9 Public listing detail

Current screen needs a task-first redesign.

Hierarchy:

1. App icon, app title, owner username.
2. Slot status and posted date.
3. Strong section: **What you will test**
4. One simple task in a numbered or checked format.
5. Device or version requirements.
6. Compact trust note about links and signals.
7. Sticky bottom action: **Join and choose my app**

Technical package ID belongs in an expandable **App details** row. It should not compete with the test task.

### 7.10 Join flow for users with multiple apps

Replace the current confirmation dialog with a bottom-sheet flow.

Step 1, confirm the app to test:

- Title: **Test Budget Bee**
- Show task, duration target, and owner.
- Primary action: **Continue**

Step 2, choose what they test back:

- Title: **Which of your apps should Alex test?**
- List every eligible active own app with icon, title, open slots, and active tester count.
- Single selection is required.
- Primary action: **Pair with Focus Flow**
- Secondary action: **Add another app**

Step 3, commitment summary:

```text
You test: Budget Bee
Alex tests: Focus Flow
Target: 14 days
```

- Consent checkbox: **I understand that activity signals do not verify test quality or Google Play completion.**
- Primary action: **Join commitment**

After success, show a focused setup checklist:

1. Open the Play opt-in page
2. Install Budget Bee
3. Return to TestLoop

Detect each state when the app resumes and advance the checklist. Do not reserve a slot permanently until the server confirms the reciprocal pair atomically.

If the user owns no app, explain:

> Reciprocal commitments need an app from each member.

Primary action: **Add my app**

Do not let the user create an ambiguous user-to-user commitment.

### 7.11 Add or edit app

Replace the long form with a three-step full-screen flow. Preserve entered values between steps.

Step 1, app identity:

- App display name
- Play install link
- Extract and preview package ID from the link
- App icon preview if safely retrievable

Step 2, closed-test access:

- Play testing opt-in link
- Number of testers needed
- Inline link validation with clear correction copy

Step 3, test brief:

- **What should testers try?**
- Optional extra details
- Preview listing

Use a sticky bottom action and progress label. Do not make a beginner type the package ID if it can be extracted from the install link. Allow manual correction only under **Advanced**.

On adding a second app:

- Start from My apps.
- Keep all first-app commitments untouched.
- After publishing, return to the My apps portfolio with the new app highlighted.
- Offer **Find testers for this app**.

### 7.12 Notifications

Current screen needs action grouping and destination-aware behaviour.

Group by:

- **Needs action**
- **Updates**
- **Earlier**

Notification rows are flat with dividers. Each row includes a distinct type icon, title, body, relative time, and unread dot.

Recommended notification copy:

1. Tester left or uninstall signal:
   - Title: **Install no longer detected**
   - Body: **TestLoop could not detect Focus Flow on @alex_dev's device. Open the commitment to review.**
2. Current user owes an install:
   - Title: **Install Budget Bee to start**
   - Body: **You joined Alex's commitment, but Budget Bee is not installed yet.**
3. Respectful reminder:
   - Title: **Alex sent a test reminder**
   - Body: **Open Budget Bee when you can to keep today's commitment on track.**
4. Account expiry:
   - Title: **Keep your TestLoop account active**
   - Body: **Your inactive account is scheduled for deletion in 24 hours. Open TestLoop to keep it.**

Tap behaviour:

- Uninstall signal opens the exact Own app detail tester row.
- Owed install opens Reciprocal commitment detail with **Install** as primary.
- Reminder opens Reciprocal commitment detail with **Open** as primary.
- Account expiry opens account retention settings.

Notification controls:

- Per-type toggles in Settings.
- Quiet hours default to 10:00 PM through 8:00 AM local time.
- In-app inbox remains available if Android notification permission is denied.

### 7.13 Profile, privacy, and account

Current Profile and Settings need consolidation and clearer priority.

Profile structure:

- avatar, username, future reputation area hidden until ratings ship
- Activity access: On or Needs setup
- Notifications
- Privacy and data
- Blocked members
- Help and report a problem
- Delete account

Do not place My apps inside Profile.

Delete flow:

1. Explain active app listings and commitments that will end.
2. Show the number of affected relationships.
3. Require confirmation through an explicit checkbox.
4. Final destructive button: **Delete my account**
5. Do not ask the user to log out before deletion.

## 8. Required user journeys

### Journey A: first onboarding

1. User understands TestLoop from Welcome.
2. User signs in with Google.
3. User chooses a public username and sees that email is private.
4. User enables Usage Access after reading the scoped explanation.
5. User lands on a purposeful empty Today screen.
6. Two primary paths are shown: **Add my app** and **Find an app to test**.

Success condition: the user understands reciprocity, tracking scope, and the next action without reading policy-heavy paragraphs.

### Journey B: adding a second own app

1. User opens My apps.
2. User taps **Add app**.
3. User completes the three-step listing flow.
4. The second app appears as its own portfolio row.
5. User can open it and see a separate tester count and empty tester list.
6. Existing commitments remain mapped to the first app.

Success condition: there is no singular "My listing" concept anywhere in the UI or data access.

### Journey C: joining and testing another member's app

1. User opens Discover and selects Budget Bee.
2. User reads the exact test task.
3. User taps **Join and choose my app**.
4. User selects Focus Flow from their own apps.
5. User confirms the pair and target duration.
6. User completes opt-in and install steps.
7. Budget Bee appears under You owe them on Today.
8. User taps **Open**, tests it, and later sees **Opened today**.

Success condition: both directions and both app names are explicit before the commitment is created.

### Journey D: catching up today

1. Today shows **2 of 4 opened today**.
2. Pending apps appear before completed apps.
3. User taps **Open** on each pending installed app.
4. On returning to TestLoop, the relevant row refreshes.
5. When all are detected, the momentum panel shows **You are caught up today**.
6. They owe you remains visible and is not mixed into the user's personal completion score.

Success condition: daily testing takes no more than one tap per installed app from the Today list.

### Journey E: resolving missed reciprocity

1. Today shows **Focus Flow: 2 people need attention** under They owe you.
2. User opens Focus Flow with the attention filter applied.
3. User expands @alex_dev.
4. The screen says **No activity detected today**, not that Alex failed to test.
5. The paired direction shows whether the current user has opened Alex's app.
6. If the current user also owes work, **Open their app** is visually presented before **Send reminder**.
7. If the current user is caught up, they can send one reminder.
8. The action becomes **Reminder sent today**.

Success condition: the product encourages fairness and action without public shaming or unsupported accusations.

## 9. Component inventory and behaviour

Build a small, distinctive Compose component set:

- `TestLoopTopBar`: screen title, optional subtitle, bell, and contextual text action.
- `MomentumPanel`: daily completion count, progress ring, and one sentence next action.
- `AppIdentity`: app icon, app name, optional owner, and compact metadata.
- `SignalLabel`: icon, short status text, semantic colour, and accessibility description.
- `DayProgress`: `Day x of y`, remaining days, and thin progress rail.
- `DailyTaskRow`: app identity, day progress, signal, and Open or Install action.
- `OwnAppRow`: app identity, tester fill, attention summary, and overflow menu.
- `ReciprocitySummary`: two named directions with app names and short status.
- `TesterRow`: collapsed and expanded states for one person and one app pair.
- `SectionHeader`: title, count, and optional text action.
- `InlineNotice`: neutral, attention, success, or error guidance without card elevation.
- `EmptyState`: compact illustration, direct title, one sentence, primary action.
- `StickyActionBar`: bottom action area used in forms and join flow.

Component rules:

- `SignalLabel` is display-only and must not use a clickable chip API.
- Rows expose one dominant action. Put secondary actions in expansion or overflow.
- Every tappable row has a visible chevron or action affordance.
- Snackbar copy names what changed, such as **Reminder sent to @alex_dev**.
- Loading keeps the existing structure through skeleton rows. Do not replace a whole screen with a spinner.
- Error states preserve useful cached content and show retry inline.

## 10. Accessibility and beginner usability

- Support font scaling through 200 percent without clipped status text or overlapping actions.
- At large font sizes, move inline actions below row content.
- Provide TalkBack descriptions that include direction, app, person, and state. Example: **They owe you, Focus Flow, no activity detected today, Day 6 of 14.**
- Do not announce decorative icons.
- Keep all targets at least 48 dp.
- Maintain WCAG AA contrast for text and essential controls.
- Use icons plus text for every status.
- Do not communicate reciprocity only through left versus right position, because screen readers read linearly.
- Respect system bold text, dark theme, reduced motion, and high-contrast preferences.
- Use plain verbs: Open, Install, Send reminder, Add app, Join.
- Explain technical terms at the point of use. Keep package ID and Usage Access out of primary task language.
- Confirmation dialogs must name the exact app and person affected.
- Preserve scroll position and form state through rotation and process recreation.

## 11. Battery-conscious interaction requirements

The UI should set accurate expectations and avoid encouraging continuous monitoring.

- Refresh activity signals when TestLoop enters the foreground, after returning from an external test app, and through one deferred daily background refresh.
- Do not show live timers or imply second-by-second monitoring.
- Show **Last checked 2 min ago** when a signal may be stale.
- Offer pull to refresh with a short cooldown.
- Batch network updates and status reads.
- Do not run a foreground service for routine usage checks.
- If battery restrictions delay data, show **Signal update delayed** rather than blaming a tester.

## 12. Screen redesign priority

### Release blocker

1. Replace singular My listing with My apps portfolio and Own app detail.
2. Add a reciprocal commitment model that links two specific app listings.
3. Redesign Today around You owe them and They owe you.
4. Add own-app selection to Join.
5. Make install, activity, elapsed days, remaining days, and reminder state visible per direction.
6. Replace the dull colour and typography system.

### High priority

7. Convert create listing into a three-step flow.
8. Redesign Discover and public listing detail.
9. Add Reciprocal commitment detail.
10. Redesign notification grouping and deep-link destinations.

### Polish

11. Add app icons and deterministic monogram fallbacks.
12. Add completion motion and polished loading states.
13. Refine Profile, privacy, and deletion.

## 13. Acceptance criteria for design implementation

- A user with two own apps sees both on My apps with independent tester and attention counts.
- Joining another app always requires selecting one eligible own app.
- Every commitment view names both apps and clearly separates You owe them from They owe you.
- An own-app detail screen shows testers joined, install signal, today's activity signal, reciprocal install signal, elapsed days, remaining days, duration when available, and reminder state.
- Today lets a user open every installed app they owe in one tap each.
- Today shows who needs attention across the user's own apps without mixing that status into personal daily completion.
- No screen calls missing activity a failed test.
- No screen implies Google Play verification.
- There is no public shaming, public inactivity leaderboard, or negative badge on a username.
- Adding a second app is visible from My apps and does not alter the first app's commitments.
- The UI does not rely on generic cards for every block.
- Light and dark themes meet contrast requirements.
- TalkBack can identify direction, app, person, status, and action.
- The interface never uses an em dash character.

## 14. Design review checklist

Before approving each implemented screen:

1. Blur test: the primary action and most urgent state remain visually obvious.
2. Five-second test: a new user can say what they owe and what is owed to them.
3. Multi-app test: every relationship still makes sense when both people own two or more apps.
4. Fairness test: reminder language stays neutral and both sides of reciprocity are visible.
5. Signal test: unavailable data is not presented as negative behaviour.
6. Large-text test: 200 percent font scaling remains usable.
7. Battery test: no screen depends on continuous polling or live duration.
8. Empty-state test: each empty screen gives one clear next action.
9. Offline test: cached status remains visible with a stale-data label.
10. Visual rhythm test: no page becomes a stack of identical rounded cards.
