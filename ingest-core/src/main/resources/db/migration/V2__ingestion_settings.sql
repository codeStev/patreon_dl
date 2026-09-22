-- Single-row, runtime-configurable mailbox poll interval - same pattern as
-- app_settings (Fulfillment), but owned by Ingestion since it's this
-- context's own cadence, not a download-queue policy.
create table ingestion_settings (
    id bigint primary key default 1,
    poll_interval_seconds int not null,
    updated_at timestamp
);

insert into ingestion_settings (id, poll_interval_seconds, updated_at)
values (1, 300, now());
