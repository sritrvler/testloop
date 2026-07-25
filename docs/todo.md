# TestLoop TODO

This is the prioritized product checklist for taking the current install-only MVP to a credible beta release.

## P0: Must complete before beta

- [ ] Run a complete two-account end-to-end test with two real Google accounts.
  - [ ] User A creates two owned apps.
  - [ ] User B joins one of User A's listings.
  - [ ] Both users install each other's committed apps.
  - [ ] Verify installed, not installed, unavailable, reciprocal, and not-testing-back states.
  - [ ] Verify tester slot counts when users join, leave, change commitments, and when a publisher edits the live tester target.
  - [ ] Verify enrolling with an app already offered in another commitment shows the warning and supports choosing another app or volunteering.
  - [ ] Verify legacy duplicate reciprocal rows collapse to one visible task per tester/app direction.
- [x] Apply and verify the campaign-target and duplicate-offer Supabase migration in the shared database.
- [x] Complete real Google OAuth testing on the emulator/device and confirm the session survives app restarts.
- [x] Test Supabase Row Level Security with multiple users.
  - [x] Users can read only data needed for their own listings and commitments.
  - [x] Users cannot modify another user's install state.
  - [x] Users cannot spoof the reporter or target app identifiers.
  - [x] Owners can see the install state for their own committed apps.
- [ ] Add basic report, block, and moderation controls before opening the beta to strangers.
- [ ] Define the install-status refresh behavior clearly in the UI so users know when the signal was last checked.

## P1: Required for Play Store launch

- [ ] Write and publish a privacy policy covering Google sign-in, profile data, listing data, commitment data, and install-status signals.
- [ ] Complete the Google Play Data safety form accurately.
- [ ] Add a working account deletion flow and document what data is removed.
- [ ] Add consent and disclosure copy for install-status reporting.
- [ ] Prepare store screenshots, app description, feature graphic, and support contact.
- [ ] Verify the app against Google Play closed-testing requirements and current policy requirements.
- [ ] Test on multiple Android versions and screen sizes.
- [ ] Test offline, slow-network, expired-session, empty-state, and Supabase-error flows.
- [ ] Add crash reporting and basic analytics that do not collect unnecessary personal data.
- [ ] Review ads for accidental interference with testing links, reminders, or navigation.
- [ ] Establish a support and abuse-response process.

## P2: Beta polish

- [ ] Improve empty states and error messages based on the two-account test.
- [ ] Add a clear leave-commitment flow with confirmation and immediate slot updates.
- [ ] Add rate limits for reminders and listing actions.
- [ ] Add server-side notification inbox behavior for important commitment changes.
- [ ] Add FCM push notifications only after the in-app notification model is reliable.
- [ ] Measure battery, network, and database usage on a real device over several days.
- [ ] Add automated repository and RLS tests for the core commitment flows.

## V2 backlog

- [ ] In-app chat.
- [ ] Tester ratings.
- [ ] Tester reputation and trust signals.
- [ ] Reputation-based discovery and tester matching.
- [ ] Donations or tester rewards.
- [ ] Account expiry and the 30-day prolongation workflow.
- [ ] More advanced tester marketplace features.

## Already completed

- [x] Install-only tracking model implemented.
- [x] Live publishers can edit the required tester target, with the change reflected in progress, slots, Discover, and tester views.
- [x] Duplicate reciprocal app offers are guarded, with a volunteer enrollment path when no unused app is available.
- [x] Duplicate tester rows are deduplicated in home, obligations, and app-management views.
- [x] Home task section renamed to “Your tasks today” and tester/app detail layouts reflowed for narrow screens.
- [x] Legacy daily activity table removed from the deployed Supabase schema.
- [x] Battery-friendly explicit refresh approach selected.
- [x] Google OAuth deep-link duplicate-activity issue fixed.
- [x] Basic emulator smoke test completed.
- [x] Dummy installed and absent package detection boundaries tested.
- [x] Modern visual redesign completed, including removal of the unexplained “BU FO” element.
