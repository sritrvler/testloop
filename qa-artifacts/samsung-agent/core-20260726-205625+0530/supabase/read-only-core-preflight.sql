select 'profiles' as table_name, count(*)::text as count from public.profiles
union all select 'owned_apps', count(*)::text from public.owned_apps
union all select 'campaigns', count(*)::text from public.campaigns
union all select 'public_app_listings', count(*)::text from public.public_app_listings
union all select 'reciprocal_commitments', count(*)::text from public.reciprocal_commitments
union all select 'commitment_install_states', count(*)::text from public.commitment_install_states
union all select 'commitment_daily_open_states', count(*)::text from public.commitment_daily_open_states
union all select 'reminders', count(*)::text from public.reminders
union all select 'notifications', count(*)::text from public.notifications
union all select 'app_bookmarks', count(*)::text from public.app_bookmarks
order by table_name;

select oa.id as owned_app_id, oa.owner_id, oa.title, oa.package_name, oa.install_url, oa.group_url, oa.lifecycle,
       c.id as campaign_id, c.lifecycle as campaign_lifecycle, c.requested_testers, c.target_days,
       c.testing_started_at, c.closed_test_confirmed_at, c.replacement_open, c.replacement_joined_at
from public.owned_apps oa
join public.campaigns c on c.owned_app_id = oa.id
where oa.title ilike '%QA%Core%' or oa.package_name in ('com.dreamplug.androidapp','com.google.android.apps.nbu.paisa.user','in.amazon.mShop.android.shopping','com.whatsapp','com.agoda.mobile.consumer','com.application.zomato')
order by oa.created_at desc;
