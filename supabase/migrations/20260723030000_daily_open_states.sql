-- Daily yes/no signal only. No usage duration, raw events, or app inventory.
create table public.commitment_daily_open_states (
  commitment_id uuid not null references public.reciprocal_commitments (id) on delete cascade,
  reporter_id uuid not null references public.profiles (id) on delete cascade,
  target_owned_app_id uuid not null references public.owned_apps (id) on delete cascade,
  activity_date date not null,
  opened boolean not null default false,
  observed_at timestamptz not null default timezone('utc', now()),
  primary key (commitment_id, reporter_id, target_owned_app_id, activity_date)
);

alter table public.commitment_daily_open_states enable row level security;
create policy daily_open_participant_read on public.commitment_daily_open_states for select to authenticated using (public.is_commitment_participant(commitment_id));
create policy daily_open_self_insert on public.commitment_daily_open_states for insert to authenticated with check (reporter_id = auth.uid() and public.is_commitment_participant(commitment_id));
create policy daily_open_self_update on public.commitment_daily_open_states for update to authenticated using (reporter_id = auth.uid()) with check (reporter_id = auth.uid());
revoke all on public.commitment_daily_open_states from anon, authenticated;
grant select, insert, update on public.commitment_daily_open_states to authenticated;
