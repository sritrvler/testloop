-- TestLoop now uses only explicit, package-scoped install observations.
-- Remove the unused Usage Access and daily-open storage left by the prototype.

drop function if exists public.record_usage_access_status(boolean);
drop function if exists public.validate_activity_summary();

drop table if exists public.commitment_daily_open_states cascade;
drop table if exists public.daily_activity_summaries cascade;

alter table public.profiles
  drop column if exists tracking_setup_complete,
  drop column if exists usage_access_last_verified_at,
  drop column if exists usage_access_revoked_at;

drop type if exists public.activity_signal;
