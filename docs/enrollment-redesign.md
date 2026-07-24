# Enrollment redesign

## Product rule

TestLoop should count a person as a tester only after the app they committed to is observed as installed on that person's phone. Joining a listing creates a provisional commitment and reserves capacity; it does not make the person a joined tester yet.

The minimum useful post-join experience is therefore:

1. Join the publisher's closed test group.
2. Install the committed app.
3. Return to TestLoop; the install observation activates the commitment and increments the publisher's tester count.

Everything else is secondary and should not block those actions.

## State model

```text
Discover listing
      |
      v
pending_setup  -- install observed -->  active  --> completed
      |
      +-------------------------------> left
```

- `pending_setup`: the user selected the exact app pairing. A slot is reserved, but the user is not included in the joined tester count.
- `active`: the committed app is installed on the tester's device. The user is included in the joined tester count.
- `completed`: the target duration is complete.
- `left`: the commitment was intentionally ended.

Install detection is a bounded device observation for one explicitly committed package. It is not Google Play verification, test-quality verification, or proof that the group link was accepted.

## Screen flow

- Discover detail shows the instructions once.
- Tapping Join opens the app-selection step directly; instructions are not repeated in a second popup.
- The user selects which of their own apps is offered in return and confirms the exact pairing.
- Success opens a focused action screen with `Join test group` and `Install app` links.
- The user is told plainly that they become countable after the install is detected.
- Returning to TestLoop refreshes the single committed package and promotes the commitment if installed.
- Foreground refresh keeps the other phone's changes visible without requiring a manual close/reopen cycle.
- A user can bookmark a listing after requesting group access; bookmarks persist, show their age, and can be removed.
- Discover keeps full campaigns out of the open-slots view and exposes them in a separate Full tab.

## Acceptance criteria

- A newly published app appears in My apps only after a successful save and refresh; failed saves show an error and do not navigate away.
- A published app is not shown in Discover for its owner, and joined listings remain available to commitment details for their app name and links.
- A pending commitment reserves capacity but does not increase the public joined tester count.
- An installed committed app promotes the commitment to active exactly once.
- A not-installed observation never demotes an active commitment.
- App restart does not flash the sign-up screen while an existing session is being restored.
- Usage Access is not a prerequisite for sign-in, discovery, joining, or install activation.
- Bookmark count reflects waiting requests, and an installed/activated app is removed from bookmarks automatically.
