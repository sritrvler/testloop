-- TestLoop foundation
--
-- This migration intentionally stores only daily, coarse activity summaries.
-- It does not store raw Usage Access events, device inventories, Google email
-- addresses, or any claim that an app passed Google Play testing.

create extension if not exists "pgcrypto";

create type public.app_lifecycle as enum ('draft', 'published', 'paused', 'archived');
create type public.campaign_lifecycle as enum ('active', 'paused', 'closed');
create type public.commitment_lifecycle as enum ('pending_setup', 'active', 'completed', 'left');
create type public.install_signal as enum ('installed', 'not_installed', 'unavailable');
create type public.activity_signal as enum ('opened_today', 'not_detected', 'unavailable');
create type public.notification_kind as enum ('install', 'reciprocal', 'reminder', 'account');

-- Public identity is deliberately limited to a chosen username. Email belongs
-- to auth.users and is never copied into a public application table.
create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  username text,
  tracking_setup_complete boolean not null default false,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint profiles_username_format check (
    username is null or username ~ '^[A-Za-z0-9_]{3,24}$'
  )
);

create unique index profiles_username_lower_key
  on public.profiles (lower(username))
  where username is not null;

-- An owned app is the durable portfolio record. A package name is globally
-- unique because Android package IDs identify one Play app unambiguously.
create table public.owned_apps (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references public.profiles (id) on delete cascade,
  title text not null,
  package_name text not null,
  install_url text not null,
  opt_in_url text not null,
  icon_seed text not null default '',
  details text not null default '',
  category text not null default 'Productivity',
  lifecycle public.app_lifecycle not null default 'draft',
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint owned_apps_title_length check (char_length(trim(title)) between 1 and 80),
  constraint owned_apps_package_format check (package_name ~ '^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$'),
  constraint owned_apps_install_url check (
    install_url ~ '^https://play\.google\.com/store/apps/details\?id=[A-Za-z0-9_.]+([^#@:]*)$'
  ),
  constraint owned_apps_opt_in_url check (
    opt_in_url ~ '^https://play\.google\.com/apps/testing/[A-Za-z0-9_.]+([^#@:]*)$'
  )
);

create unique index owned_apps_package_name_key on public.owned_apps (lower(package_name));
create index owned_apps_owner_id_idx on public.owned_apps (owner_id, lifecycle, created_at desc);

-- V1 supports one active campaign per owned app. History can be added later
-- with a separate campaign revision model if product needs it.
create table public.campaigns (
  id uuid primary key default gen_random_uuid(),
  owned_app_id uuid not null unique references public.owned_apps (id) on delete cascade,
  requested_testers integer not null,
  instructions text not null default '',
  lifecycle public.campaign_lifecycle not null default 'active',
  started_on date not null default (timezone('utc', now())::date),
  closed_at timestamptz,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint campaigns_requested_testers check (requested_testers between 1 and 100),
  constraint campaigns_instructions_length check (char_length(instructions) between 0 and 4000),
  constraint campaigns_closed_at_consistency check (
    (lifecycle = 'closed') = (closed_at is not null)
  )
);

create index campaigns_public_idx on public.campaigns (lifecycle, started_on desc);
create index campaigns_owned_app_id_idx on public.campaigns (owned_app_id);

-- A commitment names both sides of a reciprocal exchange:
-- campaign.owned_app_id is the app the tester owes the publisher, and
-- current_user_owned_app_id is the exact app the publisher owes the tester.
-- The selected app may be null only for an intentionally one-way commitment.
create table public.reciprocal_commitments (
  id uuid primary key default gen_random_uuid(),
  campaign_id uuid not null references public.campaigns (id) on delete cascade,
  tester_id uuid not null references public.profiles (id) on delete cascade,
  current_user_owned_app_id uuid references public.owned_apps (id) on delete set null,
  target_days smallint not null default 14,
  lifecycle public.commitment_lifecycle not null default 'pending_setup',
  joined_at timestamptz not null default timezone('utc', now()),
  completed_at timestamptz,
  left_at timestamptz,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint reciprocal_commitments_target_days check (target_days between 1 and 30),
  constraint reciprocal_commitments_tester_once unique (campaign_id, tester_id),
  constraint reciprocal_commitments_terminal_timestamps check (
    (lifecycle = 'completed') = (completed_at is not null)
    and (lifecycle = 'left') = (left_at is not null)
  )
);

create index reciprocal_commitments_tester_idx
  on public.reciprocal_commitments (tester_id, lifecycle, joined_at desc);
create index reciprocal_commitments_campaign_idx
  on public.reciprocal_commitments (campaign_id, lifecycle, joined_at desc);
create index reciprocal_commitments_selected_app_idx
  on public.reciprocal_commitments (current_user_owned_app_id, lifecycle);

-- Only one aggregate is retained for a target app, reporter, and UTC date.
-- foreground_minutes is an estimate and must never be interpreted as proof of
-- useful testing or Google Play enrollment.
create table public.daily_activity_summaries (
  id uuid primary key default gen_random_uuid(),
  commitment_id uuid not null references public.reciprocal_commitments (id) on delete cascade,
  reported_by uuid not null references public.profiles (id) on delete cascade,
  target_owned_app_id uuid not null references public.owned_apps (id) on delete cascade,
  activity_date date not null,
  install_status public.install_signal not null default 'unavailable',
  activity_status public.activity_signal not null default 'unavailable',
  foreground_minutes integer not null default 0,
  collected_at timestamptz not null default timezone('utc', now()),
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint daily_activity_minutes check (foreground_minutes between 0 and 1440),
  constraint daily_activity_unique_report unique (
    commitment_id, reported_by, target_owned_app_id, activity_date
  ),
  constraint daily_activity_status_consistency check (
    (activity_status = 'opened_today' and foreground_minutes > 0)
    or activity_status <> 'opened_today'
  )
);

create index daily_activity_commitment_date_idx
  on public.daily_activity_summaries (commitment_id, activity_date desc);
create index daily_activity_reporter_date_idx
  on public.daily_activity_summaries (reported_by, activity_date desc);
create index daily_activity_target_date_idx
  on public.daily_activity_summaries (target_owned_app_id, activity_date desc);

-- A reminder is directional. The sender must own target_owned_app_id and the
-- recipient must be the tester responsible for that app, or vice versa for the
-- selected reciprocal app. One reminder per direction per UTC day is allowed.
create table public.reminders (
  id uuid primary key default gen_random_uuid(),
  commitment_id uuid not null references public.reciprocal_commitments (id) on delete cascade,
  sender_id uuid not null references public.profiles (id) on delete cascade,
  recipient_id uuid not null references public.profiles (id) on delete cascade,
  target_owned_app_id uuid not null references public.owned_apps (id) on delete cascade,
  reminder_date date not null default (timezone('utc', now())::date),
  message text,
  created_at timestamptz not null default timezone('utc', now()),
  constraint reminders_sender_recipient_different check (sender_id <> recipient_id),
  constraint reminders_message_length check (message is null or char_length(message) <= 280),
  constraint reminders_unique_per_direction_day unique (
    commitment_id, sender_id, recipient_id, target_owned_app_id, reminder_date
  )
);

create index reminders_recipient_idx on public.reminders (recipient_id, created_at desc);
create index reminders_commitment_idx on public.reminders (commitment_id, created_at desc);

create table public.notifications (
  id uuid primary key default gen_random_uuid(),
  recipient_id uuid not null references public.profiles (id) on delete cascade,
  kind public.notification_kind not null,
  title text not null,
  message text not null,
  commitment_id uuid references public.reciprocal_commitments (id) on delete cascade,
  owned_app_id uuid references public.owned_apps (id) on delete set null,
  read_at timestamptz,
  created_at timestamptz not null default timezone('utc', now()),
  constraint notifications_title_length check (char_length(title) between 1 and 160),
  constraint notifications_message_length check (char_length(message) between 1 and 1000)
);

create index notifications_recipient_unread_idx
  on public.notifications (recipient_id, read_at, created_at desc);
create index notifications_commitment_idx on public.notifications (commitment_id, created_at desc);

-- Shared timestamp trigger. The function is not callable by client roles.
create or replace function public.set_updated_at()
returns trigger
language plpgsql
security invoker
set search_path = public
as $$
begin
  new.updated_at = timezone('utc', now());
  return new;
end;
$$;

create trigger profiles_set_updated_at
before update on public.profiles
for each row execute function public.set_updated_at();
create trigger owned_apps_set_updated_at
before update on public.owned_apps
for each row execute function public.set_updated_at();
create trigger campaigns_set_updated_at
before update on public.campaigns
for each row execute function public.set_updated_at();
create trigger commitments_set_updated_at
before update on public.reciprocal_commitments
for each row execute function public.set_updated_at();
create trigger activity_set_updated_at
before update on public.daily_activity_summaries
for each row execute function public.set_updated_at();

-- Create the profile shell at Google sign-in time. Username is completed in
-- the onboarding screen and remains the only public identity field here.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (id)
  values (new.id)
  on conflict (id) do nothing;
  return new;
end;
$$;

create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_user();

-- Cross-row authorization and integrity checks belong in triggers because a
-- CHECK constraint cannot verify ownership in another table.
create or replace function public.validate_campaign()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  app_owner uuid;
  app_lifecycle public.app_lifecycle;
begin
  select owner_id, lifecycle into app_owner, app_lifecycle
  from public.owned_apps where id = new.owned_app_id;

  if app_owner is null then
    raise exception 'Campaign must reference an existing owned app';
  end if;
  if new.lifecycle = 'active' and app_lifecycle <> 'published' then
    raise exception 'An active campaign requires a published app';
  end if;
  if new.lifecycle <> 'closed' and new.closed_at is not null then
    raise exception 'Only closed campaigns may have closed_at';
  end if;
  if new.lifecycle = 'closed' and new.closed_at is null then
    new.closed_at = timezone('utc', now());
  end if;
  return new;
end;
$$;

create trigger campaigns_validate
before insert or update on public.campaigns
for each row execute function public.validate_campaign();

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
  if auth.uid() is not null and new.tester_id <> auth.uid() then
    raise exception 'A member may create a commitment only for themselves';
  end if;

  if tg_op = 'UPDATE' then
    if (
      new.campaign_id <> old.campaign_id
      or new.tester_id <> old.tester_id
      or new.joined_at <> old.joined_at
      or new.target_days <> old.target_days
    ) then
      raise exception 'Campaign, tester, join date, and target duration are immutable';
    end if;
  end if;

  if new.current_user_owned_app_id is not null then
    select owner_id into selected_owner
    from public.owned_apps where id = new.current_user_owned_app_id;
    if selected_owner is distinct from new.tester_id then
      raise exception 'The selected reciprocal app must belong to the tester';
    end if;
    if exists (
      select 1 from public.owned_apps
      where id = new.current_user_owned_app_id and lifecycle = 'archived'
    ) then
      raise exception 'An archived app cannot be selected for reciprocity';
    end if;
  end if;

  -- Locking the campaign row makes the slot check safe for concurrent joins.
  if new.lifecycle in ('pending_setup', 'active') then
    select count(*) into active_count
    from public.reciprocal_commitments
    where campaign_id = new.campaign_id
      and lifecycle in ('pending_setup', 'active')
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
    if tg_op = 'INSERT' then
      new.left_at = timezone('utc', now());
    elsif old.lifecycle <> 'left' then
      new.left_at = timezone('utc', now());
    end if;
  end if;
  return new;
end;
$$;

create trigger commitments_validate
before insert or update on public.reciprocal_commitments
for each row execute function public.validate_commitment();

create or replace function public.validate_activity_summary()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  campaign_owner uuid;
  campaign_app uuid;
  reciprocal_app uuid;
  tester uuid;
begin
  select oa.owner_id, c.owned_app_id, rc.current_user_owned_app_id, rc.tester_id
    into campaign_owner, campaign_app, reciprocal_app, tester
  from public.reciprocal_commitments rc
  join public.campaigns c on c.id = rc.campaign_id
  join public.owned_apps oa on oa.id = c.owned_app_id
  where rc.id = new.commitment_id;

  if campaign_owner is null then
    raise exception 'Activity must reference an existing commitment';
  end if;
  if new.activity_date > timezone('utc', now())::date then
    raise exception 'Activity date cannot be in the future';
  end if;
  if auth.uid() is not null and new.reported_by <> auth.uid() then
    raise exception 'Activity can only be reported by the device owner';
  end if;

  if not (
    (new.reported_by = tester and new.target_owned_app_id = campaign_app)
    or (new.reported_by = campaign_owner and reciprocal_app is not null
        and new.target_owned_app_id = reciprocal_app)
  ) then
    raise exception 'Activity target is not a valid direction of this commitment';
  end if;
  return new;
end;
$$;

create trigger activity_validate
before insert or update on public.daily_activity_summaries
for each row execute function public.validate_activity_summary();

create or replace function public.validate_reminder()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  campaign_owner uuid;
  campaign_app uuid;
  reciprocal_app uuid;
  tester uuid;
  app_owner uuid;
begin
  select oa.owner_id, c.owned_app_id, rc.current_user_owned_app_id, rc.tester_id
    into campaign_owner, campaign_app, reciprocal_app, tester
  from public.reciprocal_commitments rc
  join public.campaigns c on c.id = rc.campaign_id
  join public.owned_apps oa on oa.id = c.owned_app_id
  where rc.id = new.commitment_id;

  select owner_id into app_owner from public.owned_apps where id = new.target_owned_app_id;
  new.reminder_date = timezone('utc', now())::date;
  if auth.uid() is not null and new.sender_id <> auth.uid() then
    raise exception 'A member may send reminders only as themselves';
  end if;
  if app_owner is null then
    raise exception 'Reminder target must be an existing owned app';
  end if;
  if not (
    (new.target_owned_app_id = campaign_app and new.sender_id = campaign_owner and new.recipient_id = tester)
    or (reciprocal_app is not null and new.target_owned_app_id = reciprocal_app
        and new.sender_id = tester and new.recipient_id = campaign_owner)
  ) then
    raise exception 'Reminder direction is not valid for this commitment';
  end if;
  return new;
end;
$$;

create trigger reminders_validate
before insert on public.reminders
for each row execute function public.validate_reminder();

-- Notifications are generated server-side from valid actions. There is no
-- INSERT policy for client roles on notifications.
create or replace function public.notify_new_commitment()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  owner_id uuid;
begin
  select oa.owner_id into owner_id
  from public.campaigns c
  join public.owned_apps oa on oa.id = c.owned_app_id
  where c.id = new.campaign_id;
  insert into public.notifications (recipient_id, kind, title, message, commitment_id, owned_app_id)
  values (
    owner_id,
    'reciprocal',
    'A new tester joined',
    'Someone joined your app campaign. Open the commitment to review the exact app pairing.',
    new.id,
    (select owned_app_id from public.campaigns where id = new.campaign_id)
  );
  return new;
end;
$$;

create trigger commitments_notify_owner
after insert on public.reciprocal_commitments
for each row execute function public.notify_new_commitment();

create or replace function public.notify_new_reminder()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.notifications (recipient_id, kind, title, message, commitment_id, owned_app_id)
  values (
    new.recipient_id,
    'reminder',
    'A test reminder from a member',
    coalesce(new.message, 'They asked you to open the app for today''s test commitment.'),
    new.commitment_id,
    new.target_owned_app_id
  );
  return new;
end;
$$;

create trigger reminders_notify_recipient
after insert on public.reminders
for each row execute function public.notify_new_reminder();

-- Public listing view exposes only listing metadata and a username. It never
-- selects auth.users or any email-like identity field. The count is obtained
-- through a narrow aggregate helper so this view can keep security_invoker and
-- cannot accidentally become a bypass for commitment rows.
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
    and lifecycle in ('pending_setup', 'active');
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
  public.public_joined_count(c.id) as joined_tester_count,
  greatest(c.requested_testers - public.public_joined_count(c.id), 0) as slots_remaining,
  c.instructions,
  oa.details,
  oa.category,
  c.started_on,
  oa.created_at,
  oa.updated_at
from public.owned_apps oa
join public.profiles p on p.id = oa.owner_id
join public.campaigns c on c.owned_app_id = oa.id
where oa.lifecycle = 'published' and c.lifecycle = 'active' and p.username is not null
;

-- These narrowly scoped security-definer predicates avoid recursive RLS when a
-- policy needs to reason about ownership across two protected tables. They
-- return booleans only and expose no row data.
create or replace function public.is_app_owner(target_app_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.owned_apps
    where id = target_app_id and owner_id = auth.uid()
  );
$$;

create or replace function public.is_published_app(target_app_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.owned_apps
    where id = target_app_id and lifecycle = 'published'
  );
$$;

create or replace function public.is_campaign_owner(target_campaign_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.campaigns c
    join public.owned_apps oa on oa.id = c.owned_app_id
    where c.id = target_campaign_id and oa.owner_id = auth.uid()
  );
$$;

create or replace function public.is_commitment_participant(target_commitment_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.reciprocal_commitments rc
    where rc.id = target_commitment_id
      and rc.lifecycle in ('pending_setup', 'active')
      and (
        rc.tester_id = auth.uid()
        or public.is_campaign_owner(rc.campaign_id)
      )
  );
$$;

-- Row-level security is deny-by-default. Each policy below is intentionally
-- scoped to authenticated members and the smallest useful participant set.
alter table public.profiles enable row level security;
alter table public.owned_apps enable row level security;
alter table public.campaigns enable row level security;
alter table public.reciprocal_commitments enable row level security;
alter table public.daily_activity_summaries enable row level security;
alter table public.reminders enable row level security;
alter table public.notifications enable row level security;

create policy profiles_authenticated_read
on public.profiles for select to authenticated
using (auth.uid() is not null);

create policy profiles_self_update
on public.profiles for update to authenticated
using (id = auth.uid())
with check (id = auth.uid());

create policy owned_apps_public_or_owner_read
on public.owned_apps for select to authenticated
using (
  owner_id = auth.uid()
  or lifecycle = 'published'
);

create policy owned_apps_owner_insert
on public.owned_apps for insert to authenticated
with check (owner_id = auth.uid());

create policy owned_apps_owner_update
on public.owned_apps for update to authenticated
using (owner_id = auth.uid())
with check (owner_id = auth.uid());

create policy owned_apps_owner_delete
on public.owned_apps for delete to authenticated
using (owner_id = auth.uid());

create policy campaigns_public_or_owner_read
on public.campaigns for select to authenticated
using (
  public.is_campaign_owner(id)
  or (lifecycle = 'active' and public.is_published_app(owned_app_id))
);

create policy campaigns_owner_insert
on public.campaigns for insert to authenticated
with check (
  public.is_app_owner(owned_app_id)
);

create policy campaigns_owner_update
on public.campaigns for update to authenticated
using (public.is_campaign_owner(id))
with check (public.is_campaign_owner(id));

create policy campaigns_owner_delete
on public.campaigns for delete to authenticated
using (public.is_campaign_owner(id));

create policy commitments_participant_read
on public.reciprocal_commitments for select to authenticated
using (tester_id = auth.uid() or public.is_campaign_owner(campaign_id));

create policy commitments_self_insert
on public.reciprocal_commitments for insert to authenticated
with check (tester_id = auth.uid());

create policy commitments_tester_update
on public.reciprocal_commitments for update to authenticated
using (tester_id = auth.uid())
with check (tester_id = auth.uid());

create policy commitments_tester_delete
on public.reciprocal_commitments for delete to authenticated
using (tester_id = auth.uid());

create policy activity_participant_read
on public.daily_activity_summaries for select to authenticated
using (reported_by = auth.uid() or public.is_commitment_participant(commitment_id));

create policy activity_self_insert
on public.daily_activity_summaries for insert to authenticated
with check (reported_by = auth.uid());

create policy activity_self_update
on public.daily_activity_summaries for update to authenticated
using (reported_by = auth.uid())
with check (reported_by = auth.uid());

create policy activity_self_delete
on public.daily_activity_summaries for delete to authenticated
using (reported_by = auth.uid());

create policy reminders_participant_read
on public.reminders for select to authenticated
using (sender_id = auth.uid() or recipient_id = auth.uid() or public.is_commitment_participant(commitment_id));

create policy reminders_self_insert
on public.reminders for insert to authenticated
with check (sender_id = auth.uid());

create policy notifications_recipient_read
on public.notifications for select to authenticated
using (recipient_id = auth.uid());

create policy notifications_recipient_update
on public.notifications for update to authenticated
using (recipient_id = auth.uid())
with check (recipient_id = auth.uid());

create policy notifications_recipient_delete
on public.notifications for delete to authenticated
using (recipient_id = auth.uid());

-- PostgREST needs table/view grants in addition to RLS. No client role can
-- insert notifications or profiles, and no client role can access auth.users.
revoke all on table public.profiles from anon, authenticated;
revoke all on table public.owned_apps from anon, authenticated;
revoke all on table public.campaigns from anon, authenticated;
revoke all on table public.reciprocal_commitments from anon, authenticated;
revoke all on table public.daily_activity_summaries from anon, authenticated;
revoke all on table public.reminders from anon, authenticated;
revoke all on table public.notifications from anon, authenticated;
revoke all on public.public_app_listings from anon, authenticated;

grant select, update on table public.profiles to authenticated;
grant select, insert, update, delete on table public.owned_apps to authenticated;
grant select, insert, update, delete on table public.campaigns to authenticated;
grant select, insert, update, delete on table public.reciprocal_commitments to authenticated;
grant select, insert, update, delete on table public.daily_activity_summaries to authenticated;
grant select, insert on table public.reminders to authenticated;
grant select, update, delete on table public.notifications to authenticated;
grant select on public.public_app_listings to authenticated;

revoke execute on function public.set_updated_at() from public, anon, authenticated;
revoke execute on function public.handle_new_user() from public, anon, authenticated;
revoke execute on function public.validate_campaign() from public, anon, authenticated;
revoke execute on function public.validate_commitment() from public, anon, authenticated;
revoke execute on function public.validate_activity_summary() from public, anon, authenticated;
revoke execute on function public.validate_reminder() from public, anon, authenticated;
revoke execute on function public.notify_new_commitment() from public, anon, authenticated;
revoke execute on function public.notify_new_reminder() from public, anon, authenticated;
revoke execute on function public.is_app_owner(uuid) from public, anon, authenticated;
revoke execute on function public.is_published_app(uuid) from public, anon, authenticated;
revoke execute on function public.is_campaign_owner(uuid) from public, anon, authenticated;
revoke execute on function public.is_commitment_participant(uuid) from public, anon, authenticated;
revoke execute on function public.public_joined_count(uuid) from public, anon, authenticated;
grant execute on function public.is_app_owner(uuid) to authenticated;
grant execute on function public.is_published_app(uuid) to authenticated;
grant execute on function public.is_campaign_owner(uuid) to authenticated;
grant execute on function public.is_commitment_participant(uuid) to authenticated;
grant execute on function public.public_joined_count(uuid) to authenticated;

comment on table public.profiles is 'Public username and privacy settings only. Never copy auth email here.';
comment on table public.owned_apps is 'Apps owned by a member. Package IDs and Play links are validated at the database boundary.';
comment on table public.campaigns is 'One public closed-test listing per owned app in V1.';
comment on table public.reciprocal_commitments is 'A directional app-to-app testing promise between two members.';
comment on table public.daily_activity_summaries is 'Daily aggregate device signals only. Not Google Play verification and not raw usage history.';
comment on table public.reminders is 'One respectful reminder per commitment direction per UTC day.';
comment on table public.notifications is 'Private inbox rows addressed to one member.';
