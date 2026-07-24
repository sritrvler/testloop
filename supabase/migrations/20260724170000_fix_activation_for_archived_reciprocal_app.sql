-- An app may be delisted after a reciprocal commitment is created. That
-- historical app must remain valid for the existing commitment so the
-- tester's install observation can still activate the commitment. A new
-- commitment, or an attempted change to its selected app, must still reject
-- archived apps.
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
  if auth.uid() is not null and new.tester_id <> auth.uid() then
    raise exception 'A member may create a commitment only for themselves';
  end if;

  if tg_op = 'UPDATE' then
    if (
      new.campaign_id <> old.campaign_id
      or new.tester_id <> old.tester_id
      or new.joined_at <> old.joined_at
      or new.target_days <> old.target_days
      or new.current_user_owned_app_id is distinct from old.current_user_owned_app_id
    ) then
      raise exception 'Campaign, tester, reciprocal app, join date, and target duration are immutable';
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

revoke execute on function public.validate_commitment() from public, anon, authenticated;
