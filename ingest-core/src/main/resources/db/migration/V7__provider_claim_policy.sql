-- Redeeming and downloading are configured separately per provider (see
-- ClaimPolicy). AUTO keeps the behavior every existing provider had before.
alter table provider_settings add column claim_policy varchar not null default 'AUTO';
