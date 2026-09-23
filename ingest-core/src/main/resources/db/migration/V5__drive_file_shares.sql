-- Tracks whether a FolderSyncJob-populated download_item's remote_file_id
-- refers to a Drive folder or a plain file - determines which rclone
-- command RcloneDriveDownloadAdapter uses to fetch it. Null for items
-- created before this migration (all folders at the time) and for
-- directly-named registrations (Bulkamancer/Wicked), which derive this
-- from the source URL shape instead.
alter table download_item add column remote_is_directory boolean;
