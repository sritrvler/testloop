-- Basic one-to-one encrypted chat for active TestLoop pairs.
-- Message bodies are never stored as plaintext. The app writes two HPKE
-- ciphertexts: one for the sender and one for the recipient.

create table public.chat_key_bundles (
  user_id uuid primary key references public.profiles (id) on delete cascade,
  algorithm text not null default 'HPKE_X25519_HKDF_SHA256_AES256_GCM',
  key_version integer not null default 1,
  public_key text not null,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint chat_key_bundles_algorithm check (algorithm = 'HPKE_X25519_HKDF_SHA256_AES256_GCM'),
  constraint chat_key_bundles_public_key_length check (char_length(public_key) between 1 and 20000)
);

create table public.chat_conversations (
  id uuid primary key default gen_random_uuid(),
  participant_a uuid not null references public.profiles (id) on delete cascade,
  participant_b uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default timezone('utc', now()),
  updated_at timestamptz not null default timezone('utc', now()),
  constraint chat_conversations_distinct_participants check (participant_a <> participant_b),
  constraint chat_conversations_canonical_participants check (participant_a < participant_b),
  constraint chat_conversations_unique_pair unique (participant_a, participant_b)
);

create table public.chat_messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid not null references public.chat_conversations (id) on delete cascade,
  sender_id uuid not null references public.profiles (id) on delete cascade,
  recipient_id uuid not null references public.profiles (id) on delete cascade,
  key_version integer not null default 1,
  ciphertext_for_sender text not null,
  ciphertext_for_recipient text not null,
  created_at timestamptz not null default timezone('utc', now()),
  constraint chat_messages_distinct_participants check (sender_id <> recipient_id),
  constraint chat_messages_ciphertext_length check (
    char_length(ciphertext_for_sender) between 1 and 200000
    and char_length(ciphertext_for_recipient) between 1 and 200000
  )
);

create index chat_messages_conversation_created_idx
  on public.chat_messages (conversation_id, created_at asc);
create index chat_messages_recipient_idx
  on public.chat_messages (recipient_id, created_at desc);

alter publication supabase_realtime add table public.chat_messages;

create trigger chat_key_bundles_set_updated_at
before update on public.chat_key_bundles
for each row execute function public.set_updated_at();

create trigger chat_conversations_set_updated_at
before update on public.chat_conversations
for each row execute function public.set_updated_at();

create or replace function public.get_or_create_chat_conversation(target_user_id uuid)
returns public.chat_conversations
language plpgsql
security definer
set search_path = public
as $$
declare
  first_participant uuid;
  second_participant uuid;
  conversation public.chat_conversations;
begin
  if auth.uid() is null or target_user_id is null or target_user_id = auth.uid() then
    raise exception 'Chat requires two different signed-in members';
  end if;

  if not exists (
    select 1
    from public.reciprocal_commitments rc
    join public.campaigns c on c.id = rc.campaign_id
    join public.owned_apps oa on oa.id = c.owned_app_id
    where rc.lifecycle in ('pending_setup', 'active')
      and (
        (rc.tester_id = auth.uid() and oa.owner_id = target_user_id)
        or (rc.tester_id = target_user_id and oa.owner_id = auth.uid())
      )
  ) then
    raise exception 'Chat is available only between active TestLoop pair members';
  end if;

  first_participant := least(auth.uid(), target_user_id);
  second_participant := greatest(auth.uid(), target_user_id);

  insert into public.chat_conversations (participant_a, participant_b)
  values (first_participant, second_participant)
  on conflict (participant_a, participant_b) do nothing;

  select * into conversation
  from public.chat_conversations
  where participant_a = first_participant
    and participant_b = second_participant;
  return conversation;
end;
$$;

alter table public.chat_key_bundles enable row level security;
alter table public.chat_conversations enable row level security;
alter table public.chat_messages enable row level security;

create policy chat_key_bundles_authenticated_read
on public.chat_key_bundles for select to authenticated
using (auth.uid() is not null);

create policy chat_key_bundles_self_insert
on public.chat_key_bundles for insert to authenticated
with check (user_id = auth.uid());

create policy chat_key_bundles_self_update
on public.chat_key_bundles for update to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());

create policy chat_conversations_participant_read
on public.chat_conversations for select to authenticated
using (auth.uid() = participant_a or auth.uid() = participant_b);

create policy chat_messages_participant_read
on public.chat_messages for select to authenticated
using (
  exists (
    select 1 from public.chat_conversations c
    where c.id = conversation_id
      and (auth.uid() = c.participant_a or auth.uid() = c.participant_b)
  )
);

create policy chat_messages_sender_insert
on public.chat_messages for insert to authenticated
with check (
  sender_id = auth.uid()
  and exists (
    select 1 from public.chat_conversations c
    where c.id = conversation_id
      and ((c.participant_a = sender_id and c.participant_b = recipient_id)
        or (c.participant_b = sender_id and c.participant_a = recipient_id))
  )
);

revoke all on table public.chat_key_bundles from anon, authenticated;
revoke all on table public.chat_conversations from anon, authenticated;
revoke all on table public.chat_messages from anon, authenticated;
grant select, insert, update on table public.chat_key_bundles to authenticated;
grant select on table public.chat_conversations to authenticated;
grant select, insert on table public.chat_messages to authenticated;
grant execute on function public.get_or_create_chat_conversation(uuid) to authenticated;

comment on table public.chat_messages is 'Encrypted chat envelopes. Plaintext must never be added to this table.';
