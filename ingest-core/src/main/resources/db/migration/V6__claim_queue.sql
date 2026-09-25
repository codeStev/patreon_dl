-- Background claim queue (ClaimQueueJob) for port-backed claims like
-- Gumroad: retry bookkeeping, the reason a claim needs manual action, and
-- the receipt URL a successful claim produced (Gumroad's /d/<id> page).
-- claim_status gains NEEDS_MANUAL as a value - it's a plain varchar, no
-- constraint to alter.
alter table download_source add column claim_receipt_url varchar;
alter table download_source add column claim_note varchar;
alter table download_source add column claim_attempts int not null default 0;
alter table download_source add column next_claim_attempt_at timestamp;
