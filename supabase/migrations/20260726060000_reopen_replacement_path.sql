-- A released commitment is hidden by default. The owner must explicitly
-- choose whether to keep the campaign hidden or reopen one replacement slot.
alter table public.campaigns
  drop constraint if exists campaigns_risk_decision;

alter table public.campaigns
  add constraint campaigns_risk_decision
  check (risk_decision is null or risk_decision in ('continue', 'restart', 'reopen', 'defer'));

create or replace function public.hide_campaign_after_commitment_release()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if old.lifecycle in ('pending_setup', 'active') and new.lifecycle = 'left' then
    update public.campaigns
    set risk_decision = 'continue',
        risk_decision_at = timezone('utc', now()),
        replacement_open = false
    where id = new.campaign_id
      and risk_decision is distinct from 'restart';
  end if;
  return new;
end;
$$;

drop trigger if exists commitments_hide_campaign_after_release on public.reciprocal_commitments;
create trigger commitments_hide_campaign_after_release
after update of lifecycle on public.reciprocal_commitments
for each row execute function public.hide_campaign_after_commitment_release();

-- Reopen is intentionally separate from restart: it only records the
-- already-left commitment as replacement history and opens Discover. It does
-- not release another active tester and does not change the testing clock.
create or replace function public.reopen_campaign_replacement(target_campaign_id uuid)
returns setof public.campaigns
language plpgsql
security definer
set search_path = public
as $$
declare
  campaign_row public.campaigns%rowtype;
  owner_id uuid;
  released_commitment public.reciprocal_commitments%rowtype;
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;

  select c.* into campaign_row
  from public.campaigns c
  join public.owned_apps oa on oa.id = c.owned_app_id
  where c.id = target_campaign_id
  for update of c;
  select oa.owner_id into owner_id
  from public.owned_apps oa
  where oa.id = campaign_row.owned_app_id;
  if campaign_row.id is null then raise exception 'Campaign not found'; end if;
  if owner_id <> auth.uid() then raise exception 'Only the app owner can reopen a replacement slot'; end if;
  if campaign_row.replacement_open then
    return next campaign_row;
    return;
  end if;
  if public.public_reserved_count(target_campaign_id) >= campaign_row.requested_testers then
    raise exception 'No replacement slot is available';
  end if;

  select rc.* into released_commitment
  from public.reciprocal_commitments rc
  where rc.campaign_id = target_campaign_id
    and rc.lifecycle = 'left'
    and not exists (
      select 1 from public.campaign_replacement_events cre
      where cre.released_commitment_id = rc.id
    )
  order by rc.left_at desc nulls last, rc.updated_at desc
  limit 1
  for update;
  if released_commitment.id is null then
    raise exception 'No released commitment is available for replacement';
  end if;

  insert into public.campaign_replacement_events (campaign_id, released_commitment_id, released_tester_id)
  values (target_campaign_id, released_commitment.id, released_commitment.tester_id);

  update public.campaigns
  set risk_decision = 'reopen',
      risk_decision_at = timezone('utc', now()),
      replacement_open = true,
      replacement_requested_at = timezone('utc', now()),
      replacement_joined_at = null,
      restart_confirmed_at = null
  where id = target_campaign_id
  returning * into campaign_row;

  return next campaign_row;
end;
$$;

-- Replacement joins and final restart are shared by the explicit restart and
-- reopen paths. The existing active-commitment restart RPC is unchanged.
create or replace function public.mark_replacement_commitment_joined()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  campaign_row public.campaigns%rowtype;
  replacement_event_id uuid;
begin
  select c.* into campaign_row
  from public.campaigns c
  where c.id = new.campaign_id
  for update;

  if campaign_row.risk_decision in ('restart', 'reopen')
     and campaign_row.restart_confirmed_at is null
     and campaign_row.replacement_joined_at is null
     and new.lifecycle in ('pending_setup', 'active') then
    select id into replacement_event_id
    from public.campaign_replacement_events
    where campaign_id = new.campaign_id
      and replacement_commitment_id is null
    order by created_at desc
    limit 1
    for update;

    if replacement_event_id is not null then
      update public.campaign_replacement_events
      set replacement_commitment_id = new.id,
          replacement_joined_at = timezone('utc', now())
      where id = replacement_event_id;
      update public.campaigns
      set replacement_joined_at = timezone('utc', now())
      where id = new.campaign_id;
    end if;
  end if;
  return new;
end;
$$;

create or replace function public.finalize_campaign_restart(target_campaign_id uuid)
returns setof public.campaigns
language plpgsql
security definer
set search_path = public
as $$
declare
  campaign_row public.campaigns%rowtype;
  owner_id uuid;
  active_count integer;
  new_start timestamptz := timezone('utc', now());
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;

  select c.* into campaign_row
  from public.campaigns c
  join public.owned_apps oa on oa.id = c.owned_app_id
  where c.id = target_campaign_id
  for update of c;
  select oa.owner_id into owner_id
  from public.owned_apps oa
  where oa.id = campaign_row.owned_app_id;
  if campaign_row.id is null then raise exception 'Campaign not found'; end if;
  if owner_id <> auth.uid() then raise exception 'Only the app owner can restart this campaign'; end if;
  if campaign_row.risk_decision not in ('restart', 'reopen') or campaign_row.restart_confirmed_at is not null then
    return next campaign_row;
    return;
  end if;
  if campaign_row.replacement_joined_at is null then raise exception 'A replacement tester must join before restart'; end if;

  active_count := public.campaign_active_tester_count(target_campaign_id);
  if active_count < campaign_row.requested_testers then raise exception 'All required tester installs must be restored before restart'; end if;

  update public.campaigns
  set testing_started_at = new_start,
      testing_completed_at = null,
      progress_reset_at = new_start,
      restart_confirmed_at = new_start,
      replacement_open = false
  where id = target_campaign_id
  returning * into campaign_row;

  insert into public.notifications (recipient_id, kind, title, message, owned_app_id)
  select rc.tester_id, 'reciprocal', 'Testing restarted at Day 1',
         format('The owner confirmed a restart for %s. Your new Day 1 is today and the new end date is %s. TestLoop still cannot verify Google approval.', oa.title, (new_start::date + (campaign_row.target_days - 1))),
         campaign_row.owned_app_id
  from public.reciprocal_commitments rc
  join public.owned_apps oa on oa.id = campaign_row.owned_app_id
  where rc.campaign_id = target_campaign_id and rc.lifecycle = 'active';

  return next campaign_row;
end;
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
  )
  and (
    c.risk_decision is distinct from 'restart'
    and c.risk_decision is distinct from 'reopen'
    or c.restart_confirmed_at is not null
    or (
      c.replacement_open
      and public.public_reserved_count(c.id) < c.requested_testers
    )
  );

revoke all on function public.reopen_campaign_replacement(uuid) from public, anon;
grant execute on function public.reopen_campaign_replacement(uuid) to authenticated;
