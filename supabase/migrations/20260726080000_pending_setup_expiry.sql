-- Pending commitments reserve a slot for one hour. The deadline is set by
-- the database so clients cannot extend it by sending a later timestamp.
alter table public.reciprocal_commitments
  add column if not exists setup_expires_at timestamptz;

alter table public.reciprocal_commitments
  add column if not exists end_reason text;

update public.reciprocal_commitments
set setup_expires_at = joined_at + interval '1 hour'
where setup_expires_at is null;

update public.reciprocal_commitments
set end_reason = 'user_left'
where lifecycle = 'left'
  and end_reason is null;

alter table public.reciprocal_commitments
  alter column setup_expires_at set default timezone('utc', now()) + interval '1 hour';

alter table public.reciprocal_commitments
  alter column setup_expires_at set not null;

alter table public.reciprocal_commitments
  drop constraint if exists reciprocal_commitments_end_reason;

alter table public.reciprocal_commitments
  add constraint reciprocal_commitments_end_reason
  check (end_reason is null or end_reason in ('user_left', 'owner_release', 'install_timeout'));

create or replace function public.public_reserved_count(target_campaign_id uuid)
returns integer
language sql
stable
security definer
set search_path = public
as $$
  select count(*)::integer
  from public.reciprocal_commitments
  where campaign_id = target_campaign_id
    and (
      lifecycle = 'active'
      or (lifecycle = 'pending_setup' and setup_expires_at > timezone('utc', now()))
    );
$$;

create or replace function public.public_joined_count(target_campaign_id uuid)
returns integer
language sql
stable
security definer
set search_path = public
as $$
  select count(*)::integer
  from public.reciprocal_commitments
  where campaign_id = target_campaign_id
    and lifecycle = 'active';
$$;

create or replace function public.validate_commitment()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  campaign_owner uuid;
  campaign_status public.campaign_lifecycle;
  campaign_limit integer;
  active_count integer;
  selected_owner uuid;
  selected_app_changed boolean := tg_op = 'INSERT';
begin
  select oa.owner_id, c.lifecycle, c.requested_testers
    into campaign_owner, campaign_status, campaign_limit
  from public.campaigns c
  join public.owned_apps oa on oa.id = c.owned_app_id
  where c.id = new.campaign_id
  for update of c;

  if campaign_owner is null then
    raise exception 'Commitment must reference an existing campaign';
  end if;
  if new.tester_id = campaign_owner then
    raise exception 'An app owner cannot join their own campaign';
  end if;
  if new.lifecycle in ('pending_setup', 'active') and campaign_status <> 'active' then
    raise exception 'Only active campaigns accept testers';
  end if;
  if auth.uid() is not null
     and new.tester_id <> auth.uid()
     and not (new.lifecycle = 'left' and campaign_owner = auth.uid()) then
    raise exception 'A member may create or update a commitment only for themselves';
  end if;

  if tg_op = 'INSERT' then
    new.setup_expires_at = new.joined_at + interval '1 hour';
  else
    if (
      new.campaign_id <> old.campaign_id
      or new.tester_id <> old.tester_id
      or new.joined_at <> old.joined_at
      or new.target_days <> old.target_days
      or new.current_user_owned_app_id is distinct from old.current_user_owned_app_id
      or new.setup_expires_at <> old.setup_expires_at
    ) then
      raise exception 'Campaign, tester, reciprocal app, join date, target duration, and setup deadline are immutable';
    end if;
    selected_app_changed := false;
  end if;

  if new.current_user_owned_app_id is not null then
    select owner_id into selected_owner
    from public.owned_apps where id = new.current_user_owned_app_id;
    if selected_owner is distinct from new.tester_id then
      raise exception 'The selected reciprocal app must belong to the tester';
    end if;
    if selected_app_changed and exists (
      select 1 from public.owned_apps
      where id = new.current_user_owned_app_id and lifecycle = 'archived'
    ) then
      raise exception 'An archived app cannot be selected for reciprocity';
    end if;
  end if;

  if new.lifecycle in ('pending_setup', 'active') then
    select count(*) into active_count
    from public.reciprocal_commitments
    where campaign_id = new.campaign_id
      and (
        lifecycle = 'active'
        or (lifecycle = 'pending_setup' and setup_expires_at > timezone('utc', now()))
      )
      and (tg_op = 'INSERT' or id <> new.id);
    if active_count >= campaign_limit then
      raise exception 'This campaign has no testing slots left';
    end if;
  end if;

  if new.lifecycle = 'completed' then
    if tg_op = 'INSERT' then
      new.completed_at = timezone('utc', now());
    elsif old.lifecycle <> 'completed' then
      new.completed_at = timezone('utc', now());
    end if;
  end if;
  if new.lifecycle = 'left' then
    if new.end_reason is null then
      new.end_reason = 'owner_release';
    end if;
    if tg_op = 'INSERT' then
      new.left_at = timezone('utc', now());
    elsif old.lifecycle <> 'left' then
      new.left_at = timezone('utc', now());
    end if;
  end if;
  return new;
end;
$$;

revoke execute on function public.validate_commitment() from public, anon, authenticated;

create or replace function public.notify_pending_commitment()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  app_title text;
begin
  if new.lifecycle <> 'pending_setup' then
    return new;
  end if;

  select oa.title into app_title
  from public.campaigns c
  join public.owned_apps oa on oa.id = c.owned_app_id
  where c.id = new.campaign_id;

  insert into public.notifications (recipient_id, kind, title, message, commitment_id, owned_app_id)
  values (
    new.tester_id,
    'install',
    format('Install %s within 1 hour', coalesce(app_title, 'the app')),
    format('Your tester slot for %s is reserved for 1 hour. Install the app and return to TestLoop before the setup window expires.', coalesce(app_title, 'this test')),
    new.id,
    (select owned_app_id from public.campaigns where id = new.campaign_id)
  );
  return new;
end;
$$;

drop trigger if exists commitments_notify_pending_setup on public.reciprocal_commitments;
create trigger commitments_notify_pending_setup
after insert on public.reciprocal_commitments
for each row execute function public.notify_pending_commitment();

create or replace function public.activate_commitment_after_install()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  expected_app_id uuid;
  expected_tester_id uuid;
  setup_deadline timestamptz;
begin
  if new.status <> 'installed' then
    return new;
  end if;

  select c.owned_app_id, rc.tester_id, rc.setup_expires_at
    into expected_app_id, expected_tester_id, setup_deadline
  from public.reciprocal_commitments rc
  join public.campaigns c on c.id = rc.campaign_id
  where rc.id = new.commitment_id
  for update of rc;

  if new.reporter_id = expected_tester_id
     and new.target_owned_app_id = expected_app_id
     and setup_deadline > timezone('utc', now()) then
    update public.reciprocal_commitments
    set lifecycle = 'active'
    where id = new.commitment_id
      and lifecycle = 'pending_setup'
      and setup_expires_at > timezone('utc', now());
  end if;

  return new;
end;
$$;

revoke execute on function public.activate_commitment_after_install() from public, anon, authenticated;

create or replace function public.expire_pending_commitments()
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  commitment_row record;
  expired_count integer := 0;
begin
  for commitment_row in
    select rc.id, rc.campaign_id, rc.tester_id, c.owned_app_id, oa.title, oa.owner_id
    from public.reciprocal_commitments rc
    join public.campaigns c on c.id = rc.campaign_id
    join public.owned_apps oa on oa.id = c.owned_app_id
    where rc.lifecycle = 'pending_setup'
      and rc.setup_expires_at <= timezone('utc', now())
    for update of rc skip locked
  loop
    update public.reciprocal_commitments
    set lifecycle = 'left',
        end_reason = 'install_timeout',
        left_at = timezone('utc', now())
    where id = commitment_row.id
      and lifecycle = 'pending_setup';

    if found then
      expired_count := expired_count + 1;
      insert into public.notifications (recipient_id, kind, title, message, commitment_id, owned_app_id)
      values (
        commitment_row.tester_id,
        'install',
        'Tester slot released',
        format('The 1-hour install window for %s ended, so your reserved tester slot was released. You can join again if a slot is available.', commitment_row.title),
        commitment_row.id,
        commitment_row.owned_app_id
      );
      insert into public.notifications (recipient_id, kind, title, message, commitment_id, owned_app_id)
      values (
        commitment_row.owner_id,
        'reciprocal',
        'A tester slot reopened',
        format('The tester did not install %s within 1 hour, so the reserved slot is available again.', commitment_row.title),
        commitment_row.id,
        commitment_row.owned_app_id
      );
    end if;
  end loop;
  return expired_count;
end;
$$;

revoke all on function public.expire_pending_commitments() from public, anon;
grant execute on function public.expire_pending_commitments() to authenticated;

create or replace function public.hide_campaign_after_commitment_release()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if old.lifecycle in ('pending_setup', 'active')
     and new.lifecycle = 'left'
     and new.end_reason is distinct from 'install_timeout' then
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

create or replace function public.leave_commitment(target_commitment_id uuid)
returns table (
  commitment_id uuid,
  lifecycle public.commitment_lifecycle,
  left_at timestamptz
)
language plpgsql
security definer
set search_path = public
as $$
declare
  commitment_row public.reciprocal_commitments%rowtype;
  campaign_owner uuid;
  owner_app_title text;
  other_party uuid;
begin
  select rc.* into commitment_row
  from public.reciprocal_commitments rc
  where rc.id = target_commitment_id
  for update;

  if not found then raise exception 'Commitment not found'; end if;

  select oa.owner_id, oa.title
    into campaign_owner, owner_app_title
  from public.campaigns c
  join public.owned_apps oa on oa.id = c.owned_app_id
  where c.id = commitment_row.campaign_id;

  if auth.uid() is null
     or (auth.uid() <> commitment_row.tester_id and auth.uid() <> campaign_owner) then
    raise exception 'Only a commitment participant can leave it';
  end if;
  if commitment_row.lifecycle = 'completed' then
    raise exception 'A completed commitment cannot be left';
  end if;
  if commitment_row.lifecycle = 'left' then
    return query select commitment_row.id, commitment_row.lifecycle, commitment_row.left_at;
    return;
  end if;

  update public.reciprocal_commitments
  set lifecycle = 'left', end_reason = 'user_left'
  where public.reciprocal_commitments.id = commitment_row.id
    and public.reciprocal_commitments.lifecycle in ('pending_setup', 'active')
  returning * into commitment_row;

  if not found then raise exception 'Commitment changed before it could be left'; end if;

  other_party := case
    when auth.uid() = commitment_row.tester_id then campaign_owner
    else commitment_row.tester_id
  end;

  insert into public.notifications (recipient_id, kind, title, message, commitment_id, owned_app_id)
  values (
    other_party,
    'reciprocal',
    'A testing commitment ended',
    format('The %s testing commitment ended because the other member left. The reserved tester slot is available again.', owner_app_title),
    commitment_row.id,
    (select owned_app_id from public.campaigns where id = commitment_row.campaign_id)
  );

  perform public.refresh_campaign_testing_progress(commitment_row.campaign_id);
  return query select commitment_row.id, commitment_row.lifecycle, commitment_row.left_at;
end;
$$;

revoke all on function public.leave_commitment(uuid) from public, anon;
grant execute on function public.leave_commitment(uuid) to authenticated;

create or replace function public.prevent_duplicate_reciprocal_app_offer()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.current_user_owned_app_id is not null
     and new.lifecycle in ('pending_setup', 'active') then
    perform pg_advisory_xact_lock(
      hashtextextended(new.tester_id::text || ':' || new.current_user_owned_app_id::text, 0)
    );
    if exists (
      select 1
      from public.reciprocal_commitments existing
      where existing.tester_id = new.tester_id
        and existing.current_user_owned_app_id = new.current_user_owned_app_id
        and (
          existing.lifecycle = 'active'
          or (existing.lifecycle = 'pending_setup' and existing.setup_expires_at > timezone('utc', now()))
        )
        and existing.id <> new.id
    ) then
      raise exception 'This app is already offered in another active commitment';
    end if;
  end if;
  return new;
end;
$$;

drop trigger if exists commitments_prevent_duplicate_reciprocal_app on public.reciprocal_commitments;
create trigger commitments_prevent_duplicate_reciprocal_app
before insert or update of current_user_owned_app_id, lifecycle on public.reciprocal_commitments
for each row execute function public.prevent_duplicate_reciprocal_app_offer();

create extension if not exists pg_cron;
select cron.unschedule('expire-pending-testloop-commitments')
where exists (
  select 1 from cron.job where jobname = 'expire-pending-testloop-commitments'
);
select cron.schedule(
  'expire-pending-testloop-commitments',
  '* * * * *',
  $$select public.expire_pending_commitments();$$
);
