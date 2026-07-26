-- A tester whose commitment was released may reclaim the reopened replacement
-- slot. Keep historical LEFT rows, but enforce one current commitment per
-- tester and campaign.
alter table public.reciprocal_commitments
  drop constraint if exists reciprocal_commitments_tester_once;

create unique index if not exists reciprocal_commitments_current_tester_key
  on public.reciprocal_commitments (campaign_id, tester_id)
  where lifecycle in ('pending_setup', 'active');
