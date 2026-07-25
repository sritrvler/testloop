-- Keep reminders useful and low-pressure. The database trigger supplies the
-- notification title, while the client supplies the app-specific message.
create or replace function public.notify_new_reminder()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.notifications (recipient_id, kind, title, message, commitment_id, owned_app_id)
  values (
    new.recipient_id,
    'reminder',
    'A quick TestLoop check-in',
    coalesce(new.message, 'Open TestLoop when you have a moment so we can update today''s testing status.'),
    new.commitment_id,
    new.target_owned_app_id
  );
  return new;
end;
$$;
