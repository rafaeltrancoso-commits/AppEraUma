alter table app_user add column deletion_requested_at timestamp with time zone;

create index idx_app_user_deletion_requested_at
  on app_user(deletion_requested_at);

create table file_deletion_job (
  id uuid primary key,
  storage_type varchar(30) not null,
  storage_key varchar(500) not null,
  attempt_count integer not null default 0,
  last_error varchar(500),
  created_at timestamp with time zone not null default now(),
  updated_at timestamp with time zone not null default now(),
  completed_at timestamp with time zone,
  constraint ck_file_deletion_job_storage_type
    check (storage_type in ('MOMENT_PHOTO', 'STORY_IMAGE', 'STORY_DIRECTORY')),
  constraint ck_file_deletion_job_attempt_count check (attempt_count >= 0),
  constraint uk_file_deletion_job_target unique (storage_type, storage_key)
);

create index idx_file_deletion_job_pending
  on file_deletion_job(completed_at, created_at);
