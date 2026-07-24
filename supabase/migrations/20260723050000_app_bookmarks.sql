-- A bookmark is a member-owned reminder that they requested access to a
-- listing and are waiting. It is not proof of Play-group membership.
create table public.app_bookmarks (
  member_id uuid not null references public.profiles (id) on delete cascade,
  owned_app_id uuid not null references public.owned_apps (id) on delete cascade,
  created_at timestamptz not null default timezone('utc', now()),
  primary key (member_id, owned_app_id)
);

create index app_bookmarks_member_created_idx
  on public.app_bookmarks (member_id, created_at desc);

alter table public.app_bookmarks enable row level security;

create policy app_bookmarks_self_read
on public.app_bookmarks for select to authenticated
using (member_id = auth.uid());

create policy app_bookmarks_self_insert
on public.app_bookmarks for insert to authenticated
with check (member_id = auth.uid() and public.is_published_app(owned_app_id));

create policy app_bookmarks_self_delete
on public.app_bookmarks for delete to authenticated
using (member_id = auth.uid());

revoke all on table public.app_bookmarks from anon, authenticated;
grant select, insert, delete on table public.app_bookmarks to authenticated;
