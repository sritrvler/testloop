-- App install and closed-testing links are security-sensitive input. Keep the
-- database boundary as strict as the Android client: exact HTTPS Play URLs,
-- no extra query parameters, and both URLs must identify package_name.

alter table public.owned_apps
  drop constraint if exists owned_apps_install_url,
  drop constraint if exists owned_apps_opt_in_url;

alter table public.owned_apps
  add constraint owned_apps_install_url check (
    install_url = 'https://play.google.com/store/apps/details?id=' || package_name
  ),
  add constraint owned_apps_opt_in_url check (
    opt_in_url = 'https://play.google.com/apps/testing/' || package_name
  );
