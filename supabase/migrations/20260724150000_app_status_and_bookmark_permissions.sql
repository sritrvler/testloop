-- Bookmarks are written with an idempotent upsert, so the authenticated role
-- also needs UPDATE permission and a self-only update policy.
create policy app_bookmarks_self_update
on public.app_bookmarks for update to authenticated
using (member_id = auth.uid())
with check (member_id = auth.uid() and public.is_published_app(owned_app_id));

grant update on table public.app_bookmarks to authenticated;

-- This records the owner's explicit confirmation that Google Play has
-- accepted the app. It is intentionally separate from TestLoop's tester
-- and install evidence.
alter table public.owned_apps
  add column if not exists play_store_published_at timestamptz;

comment on column public.owned_apps.play_store_published_at is
  'Owner-confirmed timestamp that the app was successfully published on Google Play.';
