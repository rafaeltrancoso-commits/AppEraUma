create table story_audio (
  id uuid primary key,
  story_id uuid not null references story(id) on delete cascade,
  chapter_id uuid not null references story_chapter(id) on delete cascade,
  chunk_index integer not null,
  text_hash varchar(64) not null,
  voice varchar(120) not null,
  model varchar(120) not null,
  audio_format varchar(20) not null,
  storage_key varchar(500),
  size_bytes bigint,
  status varchar(30) not null,
  attempt_count integer not null default 0,
  error_message varchar(500),
  created_at timestamp with time zone not null default now(),
  updated_at timestamp with time zone not null default now(),
  constraint uk_story_audio_version unique (chapter_id, text_hash, voice, chunk_index),
  constraint ck_story_audio_status check (status in ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
  constraint ck_story_audio_attempt_count check (attempt_count >= 0),
  constraint ck_story_audio_chunk_index check (chunk_index >= 0),
  constraint ck_story_audio_size check (size_bytes is null or size_bytes > 0)
);

create index idx_story_audio_story on story_audio(story_id);
create index idx_story_audio_recovery on story_audio(status, updated_at);

alter table file_deletion_job drop constraint ck_file_deletion_job_storage_type;
alter table file_deletion_job
  add constraint ck_file_deletion_job_storage_type
  check (storage_type in ('MOMENT_PHOTO', 'STORY_IMAGE', 'STORY_AUDIO', 'STORY_DIRECTORY'));
