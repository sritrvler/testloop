-- Approved Play release is a prerequisite for the closed-test clock.
create or replace function public.refresh_campaign_testing_progress(target_campaign_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  campaign_row public.campaigns%rowtype;
  release_approved_at timestamptz;
  active_count integer;
begin
  select c.*
    into campaign_row
  from public.campaigns c
  join public.owned_apps oa on oa.id = c.owned_app_id
  where c.id = target_campaign_id
  for update of c;

  if campaign_row.id is null then
    return;
  end if;

  select oa.play_store_published_at
    into release_approved_at
  from public.owned_apps oa
  where oa.id = campaign_row.owned_app_id;

  if release_approved_at is null then
    update public.campaigns
    set testing_started_at = null,
        testing_completed_at = null
    where id = target_campaign_id
      and (testing_started_at is not null or testing_completed_at is not null);
    return;
  end if;

  active_count := public.campaign_active_tester_count(target_campaign_id);

  if active_count < campaign_row.requested_testers then
    if campaign_row.testing_started_at is not null
       or campaign_row.testing_completed_at is not null then
      update public.campaigns
      set testing_started_at = null,
          testing_completed_at = null,
          progress_reset_at = timezone('utc', now())
      where id = target_campaign_id;
    end if;
    return;
  end if;

  if campaign_row.testing_started_at is null then
    update public.campaigns
    set testing_started_at = timezone('utc', now()),
        testing_completed_at = null
    where id = target_campaign_id;
  elsif campaign_row.testing_completed_at is null
        and timezone('utc', now()) >= campaign_row.testing_started_at + make_interval(days => campaign_row.target_days::integer) then
    update public.campaigns
    set testing_completed_at = timezone('utc', now())
    where id = target_campaign_id;
  end if;
end;
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
  oa.play_store_published_at
from public.owned_apps oa
join public.profiles p on p.id = oa.owner_id
join public.campaigns c on c.owned_app_id = oa.id
where oa.lifecycle = 'published' and c.lifecycle = 'active' and p.username is not null;

do $$
declare campaign_id uuid;
begin
  for campaign_id in select id from public.campaigns loop
    perform public.refresh_campaign_testing_progress(campaign_id);
  end loop;
end;
$$;
