-- Optional public contact handle for app publishers.
-- Encrypted chat remains in the database for possible future reactivation;
-- this migration only adds the contact field used while chat is disabled.

alter table public.owned_apps
  add column if not exists messaging_id text;

alter table public.owned_apps
  drop constraint if exists owned_apps_messaging_id_length;

alter table public.owned_apps
  add constraint owned_apps_messaging_id_length check (
    messaging_id is null
    or char_length(trim(messaging_id)) between 1 and 160
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
  oa.group_url,
  oa.messaging_id
from public.owned_apps oa
join public.profiles p on p.id = oa.owner_id
join public.campaigns c on c.owned_app_id = oa.id
where oa.lifecycle = 'published'
  and c.lifecycle = 'active'
  and p.username is not null;
