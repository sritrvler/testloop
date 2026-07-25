-- Closed-test listings carry three distinct links:
--   1) Google Group membership (required for new listings in the client)
--   2) Play closed-test opt-in (optional because some tracks expose it later)
--   3) Play install (required)
-- Existing listings may have no group link yet; new listings are validated by
-- the client and all supplied links are validated here at the database edge.

alter table public.owned_apps
  add column if not exists group_url text;

alter table public.owned_apps
  alter column opt_in_url drop not null;

alter table public.owned_apps
  drop constraint if exists owned_apps_install_url,
  drop constraint if exists owned_apps_opt_in_url,
  drop constraint if exists owned_apps_group_url;

alter table public.owned_apps
  add constraint owned_apps_install_url check (
    install_url = 'https://play.google.com/store/apps/details?id=' || package_name
  ),
  add constraint owned_apps_opt_in_url check (
    opt_in_url is null
    or opt_in_url = 'https://play.google.com/apps/testing/' || package_name
  ),
  add constraint owned_apps_group_url check (
    group_url is null
    or group_url ~ '^https://groups\\.google\\.com/g/[A-Za-z0-9][A-Za-z0-9_-]*$'
  );

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
  oa.group_url
from public.owned_apps oa
join public.profiles p on p.id = oa.owner_id
join public.campaigns c on c.owned_app_id = oa.id
where oa.lifecycle = 'published' and c.lifecycle = 'active' and p.username is not null;
