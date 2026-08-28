create table story_image (
  id uuid primary key,
  story_id uuid not null references story(id),
  chapter_id uuid references story_chapter(id),
  image_type varchar(40) not null,
  storage_key varchar(500),
  model varchar(120),
  size varchar(40),
  quality varchar(40),
  sort_order integer not null,
  status varchar(40) not null,
  created_at timestamp with time zone not null default now(),
  updated_at timestamp with time zone not null default now()
);

create index idx_story_image_story_order on story_image(story_id, sort_order);
create index idx_story_image_chapter on story_image(chapter_id);

create table ai_image_generation_log (
  id uuid primary key,
  user_id uuid not null references app_user(id),
  family_id uuid not null references family(id),
  story_id uuid references story(id),
  story_image_id uuid references story_image(id),
  provider varchar(40) not null,
  model varchar(120),
  quality varchar(40),
  size varchar(40),
  status varchar(40) not null,
  duration_ms bigint,
  estimated_cost_usd numeric(10, 4),
  created_at timestamp with time zone not null default now()
);

create index idx_ai_image_generation_log_user_created_at on ai_image_generation_log(user_id, created_at desc);
create index idx_ai_image_generation_log_story on ai_image_generation_log(story_id);
