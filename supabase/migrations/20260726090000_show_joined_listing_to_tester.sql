-- A tester who already reserved a slot must be able to retrieve the listing
-- details needed to finish setup, even when the listing is no longer open to
-- additional testers. Discover still removes joined apps at the UI boundary.
create or replace view public.public_app_listings
with (security_invoker = true)
as
select
  oa.id,
  oa.title,
  oa.package_name,
  oa.owner_id,
  p.username as owner_username,
  oa.install_url,
  oa.opt_in_url,
  c.id as campaign_id,
  c.requested_testers,
  public.campaign_active_tester_count(c.id) as joined_tester_count,
  greatest(c.requested_testers - public.public_reserved_count(c.id), 0) as slots_remaining,
  c.instructions,
  oa.details,
  oa.category,
  c.started_on,
  oa.created_at,
  oa.updated_at,
  c.target_days,
  c.testing_started_at,
  c.testing_completed_at,
  c.progress_reset_at,
  oa.play_store_published_at,
  oa.group_url,
  oa.messaging_id,
  c.closed_test_confirmed_at,
  c.risk_decision,
  (c.replacement_open and public.public_reserved_count(c.id) < c.requested_testers) as replacement_open,
  c.replacement_joined_at,
  c.restart_confirmed_at
from public.owned_apps oa
join public.profiles p on p.id = oa.owner_id
join public.campaigns c on c.owned_app_id = oa.id
where oa.lifecycle = 'published'
  and c.lifecycle = 'active'
  and p.username is not null
  and (
    c.risk_decision is distinct from 'continue'
    or public.campaign_active_tester_count(c.id) >= c.requested_testers
    or exists (
      select 1
      from public.reciprocal_commitments rc
      where rc.campaign_id = c.id
        and rc.tester_id = auth.uid()
        and rc.lifecycle in ('pending_setup', 'active')
    )
  )
  and (
    (
      c.risk_decision is distinct from 'restart'
      and c.risk_decision is distinct from 'reopen'
    )
    or c.restart_confirmed_at is not null
    or (
      c.replacement_open
      and public.public_reserved_count(c.id) < c.requested_testers
    )
    or exists (
      select 1
      from public.reciprocal_commitments rc
      where rc.campaign_id = c.id
        and rc.tester_id = auth.uid()
        and rc.lifecycle in ('pending_setup', 'active')
    )
  );
