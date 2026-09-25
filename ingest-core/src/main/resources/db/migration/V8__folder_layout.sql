-- How FolderSyncJob reads a Drive folder source (see FolderLayout). MODELS
-- keeps the behavior every existing source had before.
alter table download_source add column folder_layout varchar not null default 'MODELS';

-- For COLLECTIONS sources: the top-level folder (usually a creator) a
-- collection item was found in. Null for every other item.
alter table download_item add column group_name varchar;
