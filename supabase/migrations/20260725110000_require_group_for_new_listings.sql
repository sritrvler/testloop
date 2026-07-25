-- Preserve legacy listings created before group_url existed, but require a
-- valid Google Group whenever a new app becomes published.

create or replace function public.validate_owned_app_testing_links()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if tg_op = 'INSERT' then
    if new.lifecycle = 'published' and new.group_url is null then
      raise exception 'A published app must include a Google Group testing link';
    end if;
  elsif new.lifecycle = 'published'
        and new.group_url is null
        and (old.lifecycle <> 'published' or old.group_url is not null) then
    raise exception 'A published app must include a Google Group testing link';
  end if;
  return new;
end;
$$;

drop trigger if exists owned_apps_validate_testing_links on public.owned_apps;
create trigger owned_apps_validate_testing_links
before insert or update of group_url, install_url, opt_in_url, package_name, lifecycle
on public.owned_apps
for each row execute function public.validate_owned_app_testing_links();

revoke execute on function public.validate_owned_app_testing_links() from public, anon, authenticated;
