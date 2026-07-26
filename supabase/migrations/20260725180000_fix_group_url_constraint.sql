-- The original group URL constraint over-escaped the dots in the hostname.
-- PostgreSQL therefore rejected valid URLs such as
-- https://groups.google.com/g/clearpath-testing.
alter table public.owned_apps
  drop constraint if exists owned_apps_group_url;

alter table public.owned_apps
  add constraint owned_apps_group_url check (
    group_url is null
    or group_url ~ '^https://groups\.google\.com/g/[A-Za-z0-9][A-Za-z0-9_-]*$'
  );
