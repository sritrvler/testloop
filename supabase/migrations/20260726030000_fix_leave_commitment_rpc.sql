-- Qualify the lifecycle predicate so PL/pgSQL does not resolve it against
-- the output column of leave_commitment().

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
  where public.reciprocal_commitments.id = commitment_row.id
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

  perform public.refresh_campaign_testing_progress(commitment_row.campaign_id);

  return query
    select commitment_row.id, commitment_row.lifecycle, commitment_row.left_at;
end;
$$;

revoke all on function public.leave_commitment(uuid) from public, anon;
grant execute on function public.leave_commitment(uuid) to authenticated;
