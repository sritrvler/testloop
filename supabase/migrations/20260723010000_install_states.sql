-- Bounded MVP install signal. This table deliberately stores no usage duration,
-- activity events, device identifiers, or installed-app inventory.
create table public.commitment_install_states (
  commitment_id uuid not null references public.reciprocal_commitments (id) on delete cascade,
  reporter_id uuid not null references public.profiles (id) on delete cascade,
  target_owned_app_id uuid not null references public.owned_apps (id) on delete cascade,
  status public.install_signal not null default 'unavailable',
  observed_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  primary key (commitment_id, reporter_id, target_owned_app_id)
);

create index commitment_install_states_target_idx
  on public.commitment_install_states (target_owned_app_id, status, updated_at desc);

create trigger commitment_install_states_set_updated_at
before update on public.commitment_install_states
for each row execute function public.set_updated_at();

alter table public.commitment_install_states enable row level security;

create policy commitment_install_states_participant_read
on public.commitment_install_states for select to authenticated
using (public.is_commitment_participant(commitment_id));

create policy commitment_install_states_reporter_insert
on public.commitment_install_states for insert to authenticated
with check (
  reporter_id = auth.uid()
  and public.is_commitment_participant(commitment_id)
);

create policy commitment_install_states_reporter_update
on public.commitment_install_states for update to authenticated
using (reporter_id = auth.uid())
with check (reporter_id = auth.uid());

revoke all on table public.commitment_install_states from anon, authenticated;
grant select, insert, update on table public.commitment_install_states to authenticated;

comment on table public.commitment_install_states is
  'Latest install observation for an authorized commitment direction only. Not usage history or Google Play verification.';
