-- Campaign recovery is owner-controlled. Install observations remain an input,
-- but they never reset or move the testing clock on their own.
alter table public.campaigns
  add column if not exists closed_test_confirmed_at timestamptz,
  add column if not exists risk_decision text,
  add column if not exists risk_decision_at timestamptz,
  add column if not exists replacement_open boolean not null default false,
  add column if not exists replacement_requested_at timestamptz,
  add column if not exists replacement_joined_at timestamptz,
  add column if not exists restart_confirmed_at timestamptz;

alter table public.campaigns
  drop constraint if exists campaigns_risk_decision;

alter table public.campaigns
  add constraint campaigns_risk_decision
  check (risk_decision is null or risk_decision in ('continue', 'restart', 'defer'));

create table if not exists public.campaign_replacement_events (
  id uuid primary key default gen_random_uuid(),
  campaign_id uuid not null references public.campaigns (id) on delete cascade,
  released_commitment_id uuid not null references public.reciprocal_commitments (id) on delete restrict,
  released_tester_id uuid not null references public.profiles (id) on delete restrict,
  replacement_commitment_id uuid references public.reciprocal_commitments (id) on delete set null,
  requested_at timestamptz not null default timezone('utc', now()),
  replacement_joined_at timestamptz,
  created_at timestamptz not null default timezone('utc', now()),
  constraint campaign_replacement_events_one_open unique (campaign_id, released_commitment_id)
);

create index if not exists campaign_replacement_events_campaign_idx
  on public.campaign_replacement_events (campaign_id, created_at desc);

alter table public.campaign_replacement_events enable row level security;
create policy campaign_replacement_events_participant_read
on public.campaign_replacement_events for select to authenticated
using (
  exists (
    select 1
    from public.campaigns c
    join public.owned_apps oa on oa.id = c.owned_app_id
    where c.id = campaign_replacement_events.campaign_id
      and oa.owner_id = auth.uid()
  )
  or released_tester_id = auth.uid()
  or exists (
    select 1 from public.reciprocal_commitments rc
    where rc.id = campaign_replacement_events.replacement_commitment_id
      and rc.tester_id = auth.uid()
  )
);
revoke all on public.campaign_replacement_events from anon, authenticated;
grant select on public.campaign_replacement_events to authenticated;

-- Keep automatic safety refreshes, but do not clear testing_started_at when the
-- observed installed count falls below the target. Completion is evaluated
-- only while the full target is currently observed.
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
  select c.* into campaign_row
  from public.campaigns c
  where c.id = target_campaign_id
  for update;

  if campaign_row.id is null then
    return;
  end if;

  active_count := public.campaign_active_tester_count(target_campaign_id);

  if active_count < campaign_row.requested_testers then
    return;
  end if;

  if campaign_row.replacement_open then
    update public.campaigns
    set replacement_open = false
    where id = target_campaign_id;
  end if;

  -- Only the owner confirmation RPC may create the first testing_started_at.
  if campaign_row.closed_test_confirmed_at is null
     or campaign_row.testing_started_at is null then
    return;
  end if;

  if campaign_row.testing_completed_at is null
     and timezone('utc', now()) >= campaign_row.testing_started_at
       + make_interval(days => campaign_row.target_days::integer) then
    update public.campaigns
    set testing_completed_at = timezone('utc', now())
    where id = target_campaign_id;
  end if;
end;
$$;

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

  if campaign_row.risk_decision = 'restart'
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

drop trigger if exists commitments_mark_replacement_joined on public.reciprocal_commitments;
create trigger commitments_mark_replacement_joined
after insert on public.reciprocal_commitments
for each row execute function public.mark_replacement_commitment_joined();

create or replace function public.confirm_closed_testing(target_campaign_id uuid)
returns setof public.campaigns
language plpgsql
security definer
set search_path = public
as $$
declare
  campaign_row public.campaigns%rowtype;
  owner_id uuid;
  active_count integer;
  confirmed_now boolean := false;
begin
  if auth.uid() is null then
    raise exception 'Authentication required';
  end if;

  select c.* into campaign_row
  from public.campaigns c
  join public.owned_apps oa on oa.id = c.owned_app_id
  where c.id = target_campaign_id
  for update of c;

  select oa.owner_id into owner_id
  from public.owned_apps oa
  where oa.id = campaign_row.owned_app_id;

  if campaign_row.id is null then raise exception 'Campaign not found'; end if;
  if owner_id <> auth.uid() then raise exception 'Only the app owner can confirm closed testing'; end if;

  active_count := public.campaign_active_tester_count(target_campaign_id);
  if active_count < campaign_row.requested_testers then
    raise exception 'All required tester installs must be detected before confirmation';
  end if;

  if campaign_row.closed_test_confirmed_at is null then
    confirmed_now := true;
    update public.campaigns
    set closed_test_confirmed_at = timezone('utc', now()),
        testing_started_at = coalesce(testing_started_at, timezone('utc', now())),
        testing_completed_at = null
    where id = target_campaign_id
    returning * into campaign_row;
  end if;

  if confirmed_now then
    insert into public.notifications (recipient_id, kind, title, message, commitment_id, owned_app_id)
    select rc.tester_id, 'reciprocal', 'Closed testing is live',
           'The owner confirmed that Google Play closed testing is live. Daily open tasks are now available; TestLoop does not verify Google approval.',
           rc.id, campaign_row.owned_app_id
    from public.reciprocal_commitments rc
    where rc.campaign_id = target_campaign_id and rc.lifecycle = 'active';
  end if;

  return next campaign_row;
end;
$$;

create or replace function public.choose_campaign_risk_decision(
  target_campaign_id uuid,
  decision text,
  affected_commitment_id uuid default null
)
returns setof public.campaigns
language plpgsql
security definer
set search_path = public
as $$
declare
  campaign_row public.campaigns%rowtype;
  owner_id uuid;
  commitment_row public.reciprocal_commitments%rowtype;
  active_count integer;
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;
  if decision not in ('continue', 'restart', 'defer') then raise exception 'Unknown campaign risk decision'; end if;

  select c.* into campaign_row
  from public.campaigns c
  join public.owned_apps oa on oa.id = c.owned_app_id
  where c.id = target_campaign_id
  for update of c;
  select oa.owner_id into owner_id
  from public.owned_apps oa
  where oa.id = campaign_row.owned_app_id;
  if campaign_row.id is null then raise exception 'Campaign not found'; end if;
  if owner_id <> auth.uid() then raise exception 'Only the app owner can choose a campaign risk decision'; end if;
  if campaign_row.testing_started_at is null then raise exception 'The campaign has not started'; end if;

  active_count := public.campaign_active_tester_count(target_campaign_id);
  if active_count >= campaign_row.requested_testers then raise exception 'This campaign is not currently below its required threshold'; end if;

  if decision = 'restart' then
    if affected_commitment_id is null then raise exception 'Choose the tester commitment to replace'; end if;
    if campaign_row.risk_decision = 'restart' and campaign_row.replacement_requested_at is not null then
      return next campaign_row;
      return;
    end if;

    select rc.* into commitment_row
    from public.reciprocal_commitments rc
    where rc.id = affected_commitment_id
      and rc.campaign_id = target_campaign_id
      and rc.lifecycle = 'active'
    for update;
    if commitment_row.id is null then raise exception 'Choose an active commitment to replace'; end if;

    update public.reciprocal_commitments
    set lifecycle = 'left', left_at = timezone('utc', now())
    where id = commitment_row.id;

    insert into public.campaign_replacement_events (campaign_id, released_commitment_id, released_tester_id)
    values (target_campaign_id, commitment_row.id, commitment_row.tester_id);

    update public.campaigns
    set risk_decision = 'restart',
        risk_decision_at = timezone('utc', now()),
        replacement_open = true,
        replacement_requested_at = timezone('utc', now()),
        replacement_joined_at = null,
        restart_confirmed_at = null
    where id = target_campaign_id
    returning * into campaign_row;

    insert into public.notifications (recipient_id, kind, title, message, commitment_id, owned_app_id)
    values (
      commitment_row.tester_id, 'reciprocal', 'Your tester commitment was released',
      'The owner chose the replacement path after an install drop. This commitment was released so a replacement tester can join; it is not a judgment about your test quality.',
      commitment_row.id, campaign_row.owned_app_id
    );
  else
    update public.campaigns
    set risk_decision = decision,
        risk_decision_at = timezone('utc', now()),
        replacement_open = false
    where id = target_campaign_id
    returning * into campaign_row;
  end if;

  return next campaign_row;
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
  if campaign_row.risk_decision <> 'restart' or campaign_row.restart_confirmed_at is not null then
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

revoke all on function public.confirm_closed_testing(uuid) from public, anon;
revoke all on function public.choose_campaign_risk_decision(uuid, text, uuid) from public, anon;
revoke all on function public.finalize_campaign_restart(uuid) from public, anon;
grant execute on function public.confirm_closed_testing(uuid) to authenticated;
grant execute on function public.choose_campaign_risk_decision(uuid, text, uuid) to authenticated;
grant execute on function public.finalize_campaign_restart(uuid) to authenticated;

-- Preserve the existing listing shape and old play_store_published_at meaning,
-- while exposing replacement state separately.
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
  and not (
    c.risk_decision = 'continue'
    and public.campaign_active_tester_count(c.id) < c.requested_testers
  )
  and not (
    c.risk_decision = 'restart'
    and c.restart_confirmed_at is null
    and (not c.replacement_open or public.public_reserved_count(c.id) >= c.requested_testers)
  );

revoke all on public.campaign_replacement_events from anon, authenticated;
grant select on public.campaign_replacement_events to authenticated;
