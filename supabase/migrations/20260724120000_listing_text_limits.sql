-- Keep listing text limits aligned with the publish form.
alter table public.campaigns
    drop constraint if exists campaigns_instructions_length;

alter table public.campaigns
    add constraint campaigns_instructions_length
    check (char_length(instructions) between 8 and 256);

alter table public.owned_apps
    add constraint owned_apps_details_length
    check (char_length(details) <= 256);
