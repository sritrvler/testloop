
with fixture_apps(owner_id,title,package_name,install_url,opt_in_url,group_url,icon_seed,details,category,lifecycle,requested_testers,instructions) as (
  values
  ('12650c42-0c83-45d3-a1ee-1f4ec636ccf8'::uuid,'QA Core Amazon India','in.amazon.mShop.android.shopping','https://play.google.com/store/apps/details?id=in.amazon.mShop.android.shopping',null,'https://groups.google.com/g/testloop-core-amazon-india','QA Core Amazon India','Fixture listing for Amazon India both-device installed flow.','Shopping','published'::public.app_lifecycle,1,'Open Amazon India and verify install/open detection for TestLoop core QA.'),
  ('12650c42-0c83-45d3-a1ee-1f4ec636ccf8'::uuid,'QA Core WhatsApp','com.whatsapp','https://play.google.com/store/apps/details?id=com.whatsapp',null,'https://groups.google.com/g/testloop-core-whatsapp','QA Core WhatsApp','Fixture listing for WhatsApp both-device installed flow; chat behavior out of scope.','Communication','published'::public.app_lifecycle,1,'Open WhatsApp only to verify package launch/install detection. Do not test messaging.'),
  ('f1ec135f-ddfb-4794-8c2e-3b02d584f81c'::uuid,'QA Core Agoda Pixel Only','com.agoda.mobile.consumer','https://play.google.com/store/apps/details?id=com.agoda.mobile.consumer',null,'https://groups.google.com/g/testloop-core-agoda','QA Core Agoda Pixel Only','Pixel-owned listing for Samsung missing-app handoff; Agoda installed on Pixel only.','Travel','published'::public.app_lifecycle,1,'Open Agoda and verify Pixel-only dependency behavior for TestLoop QA.'),
  ('f1ec135f-ddfb-4794-8c2e-3b02d584f81c'::uuid,'QA Core Zomato Pixel Only','com.application.zomato','https://play.google.com/store/apps/details?id=com.application.zomato',null,'https://groups.google.com/g/testloop-core-zomato','QA Core Zomato Pixel Only','Pixel-owned listing for Samsung missing-app handoff; Zomato installed on Pixel only.','Food','published'::public.app_lifecycle,1,'Open Zomato and verify Pixel-only dependency behavior for TestLoop QA.'),
  ('12650c42-0c83-45d3-a1ee-1f4ec636ccf8'::uuid,'QA Core Missing Pixel','com.testloop.qa.absentpixel','https://play.google.com/store/apps/details?id=com.testloop.qa.absentpixel',null,'https://groups.google.com/g/testloop-core-missing-pixel','QA Core Missing Pixel','Synthetic absent package fixture for Pixel not-installed detection.','Tools','published'::public.app_lifecycle,1,'Attempt install/open detection for a deliberately absent package fixture.')
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
       c.id as campaign_id, c.requested_testers, c.lifecycle as campaign_lifecycle,
       oa.group_url
from public.owned_apps oa
join public.profiles p on p.id=oa.owner_id
left join public.campaigns c on c.owned_app_id=oa.id
where oa.package_name in ('in.amazon.mShop.android.shopping','com.whatsapp','com.agoda.mobile.consumer','com.application.zomato','com.testloop.qa.absentpixel')
order by oa.created_at;
