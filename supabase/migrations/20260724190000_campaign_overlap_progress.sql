-- Closed-test progress is campaign-level, not commitment-level.
-- The clock starts only when every requested tester is simultaneously
-- device-observed as installed, and resets when that overlap falls below the
-- campaign threshold.

alter table public.campaigns
  add column if not exists target_days smallint not null default 14,
  add column if not exists testing_started_at timestamptz,
  add column if not exists testing_completed_at timestamptz,
  add column if not exists progress_reset_at timestamptz;

alter table public.campaigns
  drop constraint if exists campaigns_target_days;

alter table public.campaigns
  add constraint campaigns_target_days check (target_days between 1 and 30);

create or replace function public.campaign_active_tester_count(target_campaign_id uuid)
returns integer
language sql
stable
security definer
set search_path = public
as $$
  select count(distinct rc.tester_id)::integer
  from public.reciprocal_commitments rc
  join public.campaigns c on c.id = rc.campaign_id
  where rc.campaign_id = target_campaign_id
    and rc.lifecycle = 'active'
    and exists (
      select 1
      from public.commitment_install_states cis
      where cis.commitment_id = rc.id
        and cis.reporter_id = rc.tester_id
        and cis.target_owned_app_id = c.owned_app_id
        and cis.status = 'installed'
    );
$$;

create or replace function public.refresh_campaign_testing_progress(target_campaign_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  campaign_row public.campaigns%rowtype;
  active_count integer;
begin
  select * into campaign_row
  from public.campaigns
  where id = target_campaign_id
  for update;

  if campaign_row.id is null then
    return;
  end if;

  active_count := public.campaign_active_tester_count(target_campaign_id);

  if active_count < campaign_row.requested_testers then
    if campaign_row.testing_started_at is not null
       or campaign_row.testing_completed_at is not null then
      update public.campaigns
      set testing_started_at = null,
          testing_completed_at = null,
          progress_reset_at = timezone('utc', now())
      where id = target_campaign_id;
    end if;
    return;
  end if;

  if campaign_row.testing_started_at is null then
    update public.campaigns
    set testing_started_at = timezone('utc', now()),
        testing_completed_at = null
    where id = target_campaign_id;
  elsif campaign_row.testing_completed_at is null
        and timezone('utc', now()) >= campaign_row.testing_started_at + make_interval(days => campaign_row.target_days::integer) then
    update public.campaigns
    set testing_completed_at = timezone('utc', now())
    where id = target_campaign_id;
  end if;
end;
$$;

create or replace function public.refresh_campaign_progress_from_install()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  target_campaign_id uuid;
begin
  select rc.campaign_id into target_campaign_id
  from public.reciprocal_commitments rc
  where rc.id = new.commitment_id;

  perform public.refresh_campaign_testing_progress(target_campaign_id);
  return new;
end;
$$;

drop trigger if exists commitment_install_states_refresh_campaign on public.commitment_install_states;
create trigger commitment_install_states_refresh_campaign
after insert or update of status on public.commitment_install_states
for each row execute function public.refresh_campaign_progress_from_install();

create or replace function public.public_joined_count(target_campaign_id uuid)
returns integer
language sql
stable
security definer
set search_path = public
as $$
  select public.campaign_active_tester_count(target_campaign_id);
$$;

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
  c.progress_reset_at
from public.owned_apps oa
join public.profiles p on p.id = oa.owner_id
join public.campaigns c on c.owned_app_id = oa.id
where oa.lifecycle = 'published' and c.lifecycle = 'active' and p.username is not null;

-- Bring existing campaigns to the correct derived state after deployment.
do $$
declare campaign_id uuid;
begin
  for campaign_id in select id from public.campaigns loop
    perform public.refresh_campaign_testing_progress(campaign_id);
  end loop;
end;
$$;

revoke execute on function public.campaign_active_tester_count(uuid) from public, anon, authenticated;
revoke execute on function public.refresh_campaign_testing_progress(uuid) from public, anon, authenticated;
revoke execute on function public.refresh_campaign_progress_from_install() from public, anon, authenticated;
