-- Restore the activity signal path. Usage Access and daily open state are core
-- TestLoop product behavior, so this compensates for the cleanup migration.

do $$
begin
  if not exists (
    select 1 from pg_type
    where typnamespace = 'public'::regnamespace
      and typname = 'activity_signal'
  ) then
    create type public.activity_signal as enum ('opened_today', 'not_detected', 'unavailable');
  end if;
end;
$$;

alter table public.profiles
  add column if not exists tracking_setup_complete boolean not null default false,
  add column if not exists usage_access_last_verified_at timestamptz,
  add column if not exists usage_access_revoked_at timestamptz;

create table if not exists public.commitment_daily_open_states (
  commitment_id uuid not null references public.reciprocal_commitments (id) on delete cascade,
  reporter_id uuid not null references public.profiles (id) on delete cascade,
  target_owned_app_id uuid not null references public.owned_apps (id) on delete cascade,
  activity_date date not null,
  opened boolean not null default false,
  observed_at timestamptz not null default timezone('utc', now()),
  primary key (commitment_id, reporter_id, target_owned_app_id, activity_date)
);

alter table public.commitment_daily_open_states enable row level security;

drop policy if exists daily_open_participant_read on public.commitment_daily_open_states;
create policy daily_open_participant_read
on public.commitment_daily_open_states for select to authenticated
using (public.is_commitment_participant(commitment_id));

drop policy if exists daily_open_self_insert on public.commitment_daily_open_states;
create policy daily_open_self_insert
on public.commitment_daily_open_states for insert to authenticated
with check (reporter_id = auth.uid() and public.is_commitment_participant(commitment_id));

drop policy if exists daily_open_self_update on public.commitment_daily_open_states;
create policy daily_open_self_update
on public.commitment_daily_open_states for update to authenticated
using (reporter_id = auth.uid())
with check (reporter_id = auth.uid());

revoke all on public.commitment_daily_open_states from anon, authenticated;
grant select, insert, update on public.commitment_daily_open_states to authenticated;

create or replace function public.record_usage_access_status(has_access boolean)
returns table (
  usage_access_revoked_at timestamptz,
  usage_access_deadline timestamptz,
  listing_deactivated boolean
)
language plpgsql
security definer
set search_path = public
as $$
declare
  current_revoked_at timestamptz;
begin
  if auth.uid() is null then
    raise exception 'Authentication required';
  end if;

  if has_access then
    update public.profiles
    set usage_access_last_verified_at = timezone('utc', now()),
        usage_access_revoked_at = null
    where id = auth.uid();
  else
    update public.profiles
    set usage_access_revoked_at = coalesce(usage_access_revoked_at, timezone('utc', now()))
    where id = auth.uid();
  end if;

  select p.usage_access_revoked_at into current_revoked_at
  from public.profiles p where p.id = auth.uid();

  if current_revoked_at is not null
     and current_revoked_at <= timezone('utc', now()) - interval '6 hours' then
    update public.campaigns c
    set lifecycle = 'paused'
    where c.lifecycle = 'active'
      and exists (
        select 1 from public.owned_apps oa
        where oa.id = c.owned_app_id and oa.owner_id = auth.uid()
      );
  end if;

  return query
  select current_revoked_at,
         case when current_revoked_at is null then null else current_revoked_at + interval '6 hours' end,
         exists (
           select 1 from public.campaigns c
           join public.owned_apps oa on oa.id = c.owned_app_id
           where oa.owner_id = auth.uid() and c.lifecycle = 'paused'
         );
end;
$$;

revoke all on function public.record_usage_access_status(boolean) from public, anon;
grant execute on function public.record_usage_access_status(boolean) to authenticated;
