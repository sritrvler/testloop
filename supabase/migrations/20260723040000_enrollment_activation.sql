-- Enrollment is provisional until the tester's device observes the committed
-- app. Provisional commitments reserve capacity but are not counted as joined
-- testers in the publisher's campaign.

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
  greatest(c.requested_testers - public.public_reserved_count(c.id), 0) as slots_remaining,
  c.instructions,
  oa.details,
  oa.category,
  c.started_on,
  oa.created_at,
  oa.updated_at
from public.owned_apps oa
join public.profiles p on p.id = oa.owner_id
join public.campaigns c on c.owned_app_id = oa.id
where oa.lifecycle = 'published' and c.lifecycle = 'active' and p.username is not null;

-- A valid install observation promotes the provisional commitment. The
-- transition is monotonic: a later "not installed" observation never removes
-- a tester who has already been counted.
create or replace function public.activate_commitment_after_install()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  expected_app_id uuid;
  expected_tester_id uuid;
begin
  if new.status <> 'installed' then
    return new;
  end if;

  select c.owned_app_id, rc.tester_id
    into expected_app_id, expected_tester_id
  from public.reciprocal_commitments rc
  join public.campaigns c on c.id = rc.campaign_id
  where rc.id = new.commitment_id;

  if new.reporter_id = expected_tester_id
     and new.target_owned_app_id = expected_app_id then
    update public.reciprocal_commitments
    set lifecycle = 'active'
    where id = new.commitment_id
      and lifecycle = 'pending_setup';
  end if;

  return new;
end;
$$;

drop trigger if exists commitment_install_states_activate on public.commitment_install_states;
create trigger commitment_install_states_activate
after insert or update of status on public.commitment_install_states
for each row execute function public.activate_commitment_after_install();

-- Bring already-observed installs forward when this migration is applied.
update public.reciprocal_commitments rc
set lifecycle = 'active'
where rc.lifecycle = 'pending_setup'
  and exists (
    select 1
    from public.commitment_install_states cis
    join public.campaigns c on c.id = rc.campaign_id
    where cis.commitment_id = rc.id
      and cis.reporter_id = rc.tester_id
      and cis.target_owned_app_id = c.owned_app_id
      and cis.status = 'installed'
  );

-- Do not notify the publisher when somebody merely reserves a slot. Notify
-- only when the install observation makes the tester countable.
create or replace function public.notify_new_commitment()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  owner_id uuid;
begin
  if new.lifecycle <> 'active'
     or (tg_op = 'UPDATE' and old.lifecycle = 'active') then
    return new;
  end if;

  select oa.owner_id into owner_id
  from public.campaigns c
  join public.owned_apps oa on oa.id = c.owned_app_id
  where c.id = new.campaign_id;

  insert into public.notifications (recipient_id, kind, title, message, commitment_id, owned_app_id)
  values (
    owner_id,
    'reciprocal',
    'A tester installed your app',
    'The tester is now counted in your app campaign.',
    new.id,
    (select owned_app_id from public.campaigns where id = new.campaign_id)
  );
  return new;
end;
$$;

drop trigger if exists commitments_notify_owner_active on public.reciprocal_commitments;
create trigger commitments_notify_owner_active
after update of lifecycle on public.reciprocal_commitments
for each row execute function public.notify_new_commitment();

revoke execute on function public.public_reserved_count(uuid) from public, anon, authenticated;
revoke execute on function public.activate_commitment_after_install() from public, anon, authenticated;
