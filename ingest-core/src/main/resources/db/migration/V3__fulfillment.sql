-- app_settings singleton row, same pattern as V2__ingestion_settings.sql.
-- max_concurrent_downloads=1 is deliberately the safe default: avoids HDD
-- seek-thrashing and keeps concurrency against Google Drive's API low.
insert into app_settings (id, max_concurrent_downloads, io_nice, updated_at)
values (1, 1, true, now());

-- Backoff scheduling for transient download failures (see
-- ExecuteDownloadUseCase) - an item can stay PENDING while waiting out its
-- next retry window instead of needing a separate "retrying" status.
alter table download_item add column next_attempt_at timestamp;
