# TestLoop product design

## Purpose

TestLoop helps people coordinate reciprocal Android closed-test commitments. It does not verify Google Play testing completion. It presents clear, limited signals: install detected, activity detected, and days since joining.

## Colour and visual direction

The interface uses deep teal as its primary colour, warm off-white surfaces, and blue for information. Teal communicates calm, reliability, and forward progress without the urgency of red or the sales feel of saturated green. Amber is reserved for attention states, and red appears only for destructive or genuinely failed actions. Cards are spacious, labels use plain language, and every primary action is a full-width, high-contrast button.

## Personas

| Persona | Goal | Need | Fear | Design response |
| --- | --- | --- | --- | --- |
| New publisher, Priya | Fill a closed test quickly | Clear steps and reliable people | Being ignored after sharing links | Listing checklist, slot count, reciprocal status, simple reminders |
| Reciprocal tester, Arjun | Keep promises without losing track | One place for every app | Forgetting an app or being accused unfairly | Today screen, Open buttons, precise activity wording, commitment history |
| Careful tester, Mia | Protect her privacy | Understand exactly what is shared | Phone-wide surveillance or spam | Scoped-tracking explanation, explicit consent, no raw history, controls and account deletion |

## User flow and stage needs

| Stage | Need and pain point | Fear | Product decision |
| --- | --- | --- | --- |
| Welcome | Understand the app in seconds | Hidden tracking | State the purpose and privacy boundary before sign-in |
| Google sign-in | Fast, low-friction entry | Email becoming public | Google sign-in is an integration seam; a separate public username is required |
| Username | A simple public identity | Accidentally sharing a real name | Explain what other members see and validate availability later |
| Discover | Find a suitable reciprocal test | Dead or misleading listings | Search, filters, requested slots, plain listing details and reporting route |
| Join | Know what the commitment means | Joining by mistake | Confirmation dialog lists the exact app and the two conditions |
| Today | Finish daily work fast | Forgetting an app | Big Open button, date-based activity state, remaining days |
| My listing | See who is reciprocating | Unfair blame | Separate install detected from activity detected; never claim Play verification |
| Reminder | Prompt once, respectfully | Harassment | One reminder per app each day, quiet-hours intent and a block/report extension point |
| Privacy and exit | Leave with confidence | Data remains forever | Explain scoped data, retention, archive process, and immediate account deletion path |

## Product boundaries

- Only package IDs explicitly included in an accepted commitment may be tracked in a future integration.
- The app must not scan the device app inventory, silently request Usage Access, or imply Google verification.
- Membership, install detection, activity detection, and self-reported completion are distinct states.
- Payments, donations, public chat, ads, and ratings are intentionally outside this foundation.

## Future integration seams

- `AuthRepository` is the seam for Credential Manager and Google identity.
- `TestLoopRepository` maps to Supabase authentication, listings, commitments, notifications, and row-level security.
- `ScopedActivityTracker` accepts only an explicit target package and date range. It must be backed by a clear, opt-in Usage Access flow before it can read device data.
- `ExternalAppLauncher` is the seam for launching a tester-selected installed app.

## V1 safety decisions from adversarial QA

- Play links are accepted only when they are direct HTTPS `play.google.com` URLs with no user info, port, fragment, duplicate query keys, unsafe controls, redirects, or package mismatch.
- Package detection is labelled as a device observation, not proof of Google Play install or enrollment. Activity is an estimate and never proof of useful testing.
- A server implementation must use server timestamps for joined-day counts, atomic slot allocation, idempotency keys, row-level authorization, and immediate revocation of evidence sharing after leave or deletion.
- Reminder delivery is best effort. V1 uses a one-per-pair-per-day rule, and notification permission remains optional.
