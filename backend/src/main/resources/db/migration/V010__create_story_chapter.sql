create table story_chapter (
  id uuid primary key,
  story_id uuid not null references story(id),
  chapter_number integer not null,
  title varchar(180) not null,
  content text not null,
  created_at timestamp with time zone not null default now(),
  constraint uk_story_chapter_number unique (story_id, chapter_number)
);

create index idx_story_chapter_story_id on story_chapter(story_id);
