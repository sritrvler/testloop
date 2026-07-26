-- Corrected matrix: Samsung profile `selva` owns Agoda/Zomato listings via CLI.
-- Pixel has Agoda/Zomato installed; Samsung read-only matrix shows absent/no activity.
with fixture_apps(owner_id,title,package_name,install_url,opt_in_url,group_url,icon_seed,details,category,lifecycle,requested_testers,instructions) as (
  values
  ('12650c42-0c83-45d3-a1ee-1f4ec636ccf8'::uuid,'QA Samsung Agoda Pixel Installed','com.agoda.mobile.consumer','https://play.google.com/store/apps/details?id=com.agoda.mobile.consumer',null,'https://groups.google.com/g/testloop-samsung-agoda','QA Samsung Agoda Pixel Installed','Samsung-owned fixture; Agoda is missing on Samsung and installed on Pixel for comparison.','Travel','published'::public.app_lifecycle,1,'Open Agoda on the comparison Pixel and verify installed/open behavior; Samsung should report missing.'),
  ('12650c42-0c83-45d3-a1ee-1f4ec636ccf8'::uuid,'QA Samsung Zomato Pixel Installed','com.application.zomato','https://play.google.com/store/apps/details?id=com.application.zomato',null,'https://groups.google.com/g/testloop-samsung-zomato','QA Samsung Zomato Pixel Installed','Samsung-owned fixture; Zomato is missing on Samsung and installed on Pixel for comparison.','Food','published'::public.app_lifecycle,1,'Open Zomato on the comparison Pixel and verify installed/open behavior; Samsung should report missing.')
), inserted_apps as (
  insert into public.owned_apps(owner_id,title,package_name,install_url,opt_in_url,group_url,icon_seed,details,category,lifecycle)
  select owner_id,title,package_name,install_url,opt_in_url,group_url,icon_seed,details,category,lifecycle
  from fixture_apps fa
  where not exists (
    select 1 from public.owned_apps existing
    where lower(existing.package_name)=lower(fa.package_name)
      and existing.lifecycle <> 'archived'
  )
  returning id, owner_id, title, package_name
), all_fixture_apps as (
  select oa.id, fa.requested_testers, fa.instructions
  from fixture_apps fa
  join public.owned_apps oa on lower(oa.package_name)=lower(fa.package_name) and oa.lifecycle <> 'archived'
), inserted_campaigns as (
  insert into public.campaigns(owned_app_id, requested_testers, instructions, lifecycle)
  select id, requested_testers, instructions, 'active'::public.campaign_lifecycle
  from all_fixture_apps afa
  where not exists (select 1 from public.campaigns c where c.owned_app_id=afa.id)
  returning id, owned_app_id
)
select oa.id as owned_app_id, oa.owner_id, p.username, oa.title, oa.package_name, oa.lifecycle,
       c.id as campaign_id, c.requested_testers, c.lifecycle as campaign_lifecycle, oa.group_url
from public.owned_apps oa
join public.profiles p on p.id=oa.owner_id
join public.campaigns c on c.owned_app_id=oa.id
where oa.package_name in ('com.agoda.mobile.consumer','com.application.zomato')
  and oa.lifecycle <> 'archived'
order by oa.created_at;
