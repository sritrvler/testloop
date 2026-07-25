-- A live publisher may lower a target below the old 12-tester default when
-- fewer testers are enrolled, but never below the enrolled/reserved count.
alter table public.campaigns
  drop constraint if exists campaigns_requested_testers;

alter table public.campaigns
  add constraint campaigns_requested_testers check (requested_testers between 1 and 100);

create or replace function public.prevent_campaign_target_below_reserved()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.requested_testers < public.public_reserved_count(new.id) then
    raise exception 'The tester target cannot be lower than the testers already enrolled';
  end if;
  return new;
end;
$$;
