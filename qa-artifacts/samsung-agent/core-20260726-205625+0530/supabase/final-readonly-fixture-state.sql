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
