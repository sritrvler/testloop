select p.username, oa.owner_id, oa.id as owned_app_id, c.id as campaign_id, oa.title, oa.package_name, oa.lifecycle,
       c.lifecycle as campaign_lifecycle, c.requested_testers, c.target_days, c.testing_started_at, c.closed_test_confirmed_at,
       c.replacement_open, oa.group_url, oa.install_url
from public.owned_apps oa
left join public.profiles p on p.id = oa.owner_id
left join public.campaigns c on c.owned_app_id = oa.id
where oa.title ilike '%QA Core%' or oa.title ilike '%QA%20Core%' or oa.package_name in (
  'com.dreamplug.androidapp','com.google.android.apps.nbu.paisa.user','in.amazon.mShop.android.shopping','com.whatsapp','com.agoda.mobile.consumer','com.application.zomato','com.testloop.qa.absentpixel'
)
order by p.username, oa.title;
