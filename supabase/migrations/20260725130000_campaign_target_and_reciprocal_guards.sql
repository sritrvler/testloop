-- Live publishers may adjust their tester target as offline testers join.
-- Keep the progress aggregate in sync when the threshold changes.
create or replace function public.refresh_campaign_progress_from_target_change()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.requested_testers is distinct from old.requested_testers then
    perform public.refresh_campaign_testing_progress(new.id);
  end if;
  return new;
end;
$$;

drop trigger if exists campaigns_refresh_progress_on_target_change on public.campaigns;
create trigger campaigns_refresh_progress_on_target_change
after update of requested_testers on public.campaigns
for each row execute function public.refresh_campaign_progress_from_target_change();

create or replace function public.prevent_campaign_target_below_reserved()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.requested_testers < public.public_reserved_count(new.id) then
    raise exception 'The tester target cannot be lower than already reserved testers';
  end if;
  return new;
end;
$$;

drop trigger if exists campaigns_prevent_target_below_reserved on public.campaigns;
create trigger campaigns_prevent_target_below_reserved
before update of requested_testers on public.campaigns
for each row execute function public.prevent_campaign_target_below_reserved();

-- A member can offer a given owned app to multiple people, but not twice from
-- their own account at the same time. The client explains the choice and can
-- offer a volunteer path; this trigger protects the rule for all clients.
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
        and existing.lifecycle in ('pending_setup', 'active')
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
