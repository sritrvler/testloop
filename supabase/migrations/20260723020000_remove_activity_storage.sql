-- The bounded MVP records only whether a committed package is installed.
-- Remove the earlier prototype's activity storage if the base migration was run.
drop table if exists public.daily_activity_summaries cascade;
