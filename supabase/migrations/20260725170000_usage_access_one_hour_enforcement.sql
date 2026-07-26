-- The permission-loss grace period is one hour. Keep this as a forward
-- migration because the original six-hour function may already be deployed.
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

  select p.usage_access_revoked_at
    into current_revoked_at
  from public.profiles p
  where p.id = auth.uid();

  if current_revoked_at is not null
     and current_revoked_at <= timezone('utc', now()) - interval '1 hour' then
    update public.campaigns c
    set lifecycle = 'paused'
    where c.lifecycle = 'active'
      and exists (
        select 1
        from public.owned_apps oa
        where oa.id = c.owned_app_id
          and oa.owner_id = auth.uid()
      );
  end if;

  return query
  select current_revoked_at,
         case when current_revoked_at is null then null else current_revoked_at + interval '1 hour' end,
         exists (
           select 1
           from public.campaigns c
           join public.owned_apps oa on oa.id = c.owned_app_id
           where oa.owner_id = auth.uid()
             and c.lifecycle = 'paused'
         );
end;
$$;

revoke all on function public.record_usage_access_status(boolean) from public, anon;
grant execute on function public.record_usage_access_status(boolean) to authenticated;
