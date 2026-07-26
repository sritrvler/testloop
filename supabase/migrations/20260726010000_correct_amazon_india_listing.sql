-- Correct the active Amazon listing to the India Play package and expose its
-- optional closed-test opt-in link.
update public.owned_apps
set title = 'Amazon India',
    package_name = 'in.amazon.mShop.android.shopping',
    install_url = 'https://play.google.com/store/apps/details?id=in.amazon.mShop.android.shopping',
    opt_in_url = 'https://play.google.com/apps/testing/in.amazon.mShop.android.shopping',
    group_url = 'https://groups.google.com/g/clearpath-testing'
where id = 'fae3dd54-73c8-475d-8283-86eb7eddb442'
  and lifecycle = 'published';
