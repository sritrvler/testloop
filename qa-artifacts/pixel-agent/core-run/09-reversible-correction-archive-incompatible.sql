-- Reversible correction for mistaken Pixel-primary fixture setup.
-- No users/chat rows are touched. Rows can be restored by setting owned_apps.lifecycle='published'
-- and campaigns.lifecycle='active', closed_at=null for the same IDs.
with target_owned_apps(id) as (
  values
    ('21cd7628-91b0-4d3f-b311-45eb271efb0a'::uuid), -- Pixel-owned CRED, should be Samsung UI-created
    ('4a8f6f0e-97f5-454b-b070-3276b6574e0b'::uuid), -- Pixel-owned GPay, should be Samsung UI-created
    ('c5f2aecb-924b-43f1-9ea6-9a7729819aef'::uuid), -- Pixel-owned Agoda, should be Samsung-owned SQL fixture
    ('f54dddd9-6f87-4d4f-8314-651170ca13b5'::uuid), -- Pixel-owned Zomato, should be Samsung-owned SQL fixture
    ('006762fa-97ab-4b7c-add0-66d7c246b420'::uuid)  -- extra synthetic absent package no longer in corrected matrix
), closed_campaigns as (
  update public.campaigns c
  set lifecycle = 'closed', closed_at = coalesce(c.closed_at, timezone('utc', now()))
  from target_owned_apps t
  where c.owned_app_id = t.id
    and c.lifecycle <> 'closed'
  returning c.id as campaign_id, c.owned_app_id, c.lifecycle, c.closed_at
), archived_apps as (
  update public.owned_apps oa
  set lifecycle = 'archived'
  from target_owned_apps t
  where oa.id = t.id
    and oa.lifecycle <> 'archived'
  returning oa.id as owned_app_id, oa.owner_id, oa.title, oa.package_name, oa.lifecycle
)
select 'closed_campaign' as action, campaign_id::text as id, owned_app_id::text, lifecycle::text, closed_at::text as detail from closed_campaigns
union all
select 'archived_app' as action, owned_app_id::text as id, owner_id::text as owned_app_id, lifecycle::text, package_name as detail from archived_apps
order by action, detail;
