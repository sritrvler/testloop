-- This product models Android closed testing, whose minimum overlap target is
-- twelve testers. Do not allow a campaign that can never satisfy the rule.
alter table public.campaigns
  drop constraint if exists campaigns_requested_testers;

alter table public.campaigns
  add constraint campaigns_requested_testers check (requested_testers between 12 and 100);
