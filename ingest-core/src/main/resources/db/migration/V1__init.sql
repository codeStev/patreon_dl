-- Schema per patreon-ingest-bot-design.md "Data model (consolidated)".

create table provider_settings (
    provider_id varchar primary key,   -- 'nomnom', 'bulkamancer', 'wicked'
    download_policy varchar not null,
    updated_at timestamp
);

create table app_settings (
    id bigint primary key default 1,
    max_concurrent_downloads int not null default 1,
    bandwidth_limit_kbps int,
    io_nice boolean not null default true,
    allowed_hours_start time,
    allowed_hours_end time,
    updated_at timestamp
);

create table download_source (
    id uuid primary key,
    creator varchar not null,
    category varchar,             -- regular, loyalty, lootbox, keycap, extra, term
    month_label varchar,
    source_type varchar not null, -- DRIVE, MMF, GUMROAD
    source_url varchar not null,
    claim_type varchar,           -- NONE, GUMROAD
    claim_status varchar not null,-- DISCOVERED, CLAIMED
    first_seen timestamp,
    last_synced timestamp,        -- last time this source was diffed
    claimed_at timestamp,
    quiet_since timestamp,        -- set once no new files appear for 40 days
    link_dead boolean not null default false,
    unique (creator, source_url)
);

create table download_item (
    id uuid primary key,
    source_id uuid not null references download_source(id),
    model_name varchar not null,
    remote_file_id varchar,       -- rclone/Drive file id — dedup key for re-diffing
    status varchar not null,      -- PENDING, DOWNLOADED, FAILED
    local_path varchar,
    file_size_bytes bigint,
    retry_count int not null default 0,
    last_error varchar,
    discovered_at timestamp not null,
    downloaded_at timestamp,
    unique (source_id, remote_file_id)
);

-- storage visibility becomes a derived view, not a stored counter — avoids
-- write contention and drift on the hot path
create view source_bytes as
select source_id, sum(file_size_bytes) as bytes_downloaded
from download_item
where status = 'DOWNLOADED'
group by source_id;

create table processed_email (
    id uuid primary key,
    mailbox varchar not null,
    uid bigint not null,          -- IMAP UID: restart-safe idempotency key
    message_id varchar,
    from_address varchar,
    subject varchar,
    received_at timestamp,
    parser_matched varchar,       -- null if no parser's supports() matched
    parse_status varchar not null,-- PARSED, NO_PARSER_MATCH, PARSE_ERROR
    error_message varchar,
    processed_at timestamp not null,
    unique (mailbox, uid)
);
