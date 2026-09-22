-- Optional post-download normalization: recursively replace spaces with
-- underscores in downloaded file/folder names. Off by default - existing
-- downloads/tooling shouldn't change behavior until an operator opts in.
alter table app_settings
    add column rename_spaces_to_underscores boolean not null default false;
