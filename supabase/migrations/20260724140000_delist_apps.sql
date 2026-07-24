-- Delisted apps no longer occupy the package ID, allowing the owner to
-- publish that same Play package again later.
drop index if exists public.owned_apps_package_name_key;

create unique index owned_apps_package_name_key
on public.owned_apps (lower(package_name))
where lifecycle <> 'archived';
