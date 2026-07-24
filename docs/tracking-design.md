# Battery-conscious tracking design

## Scope

Tracking is opt-in. TestLoop must only process package IDs from active reciprocal commitments selected by the member. It must not request `QUERY_ALL_PACKAGES`, scan a device inventory, or upload raw usage events.

## Collection design

- Refresh a selected package after the user returns to TestLoop from an explicit Open action.
- Refresh while TestLoop is in the foreground when the user asks to refresh.
- Schedule at most one deferred daily sync through WorkManager with battery-aware constraints.
- Batch selected package summaries into one network request per refresh.
- Derive daily foreground-minute estimates on the device, then upload only the daily aggregate with collection time and reporting date.
- Do not run polling loops, alarms, live timers, or a foreground service.
- Immediately cancel scheduled work and stop sharing evidence when a commitment ends, is left, or the account is deleted.

## Truth and privacy

Usage Access is broad Android system access even though TestLoop filters to selected packages locally. Empty or delayed data must be represented as unavailable or delayed, never as a failed test. The backend must treat client evidence as untrusted, cap invalid durations, use server time for campaign age, and retain only the disclosed daily summary.

## Interfaces

`ScopedActivityTracker` reads one explicit package and date. `TrackingSyncScheduler` schedules a unique daily WorkManager request only after opt-in. Neither interface implements real Usage Access in this foundation.
