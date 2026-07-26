-- Allow either participant to leave a pending or active commitment. Leaving
-- the shared row releases its reserved campaign slot immediately.

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
  select rc.*
    into commitment_row
  from public.reciprocal_commitments rc
  where rc.id = target_commitment_id
  for update;

  if not found then
    raise exception 'Commitment not found';
  end if;

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
    return query
      select commitment_row.id, commitment_row.lifecycle, commitment_row.left_at;
    return;
  end if;

  update public.reciprocal_commitments
  set lifecycle = 'left'
  where id = commitment_row.id
    and public.reciprocal_commitments.lifecycle in ('pending_setup', 'active')
  returning * into commitment_row;

  if not found then
    raise exception 'Commitment changed before it could be left';
  end if;

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

  -- If this tester was part of the active overlap, leaving may reset the
  -- campaign clock. The function is security definer and remains server-only.
  perform public.refresh_campaign_testing_progress(commitment_row.campaign_id);

  return query
    select commitment_row.id, commitment_row.lifecycle, commitment_row.left_at;
end;
$$;

revoke all on function public.leave_commitment(uuid) from public, anon;
grant execute on function public.leave_commitment(uuid) to authenticated;
