create table story (
  id uuid primary key,
  family_id uuid not null references family(id),
  child_id uuid not null references child_profile(id),
  source_moment_id uuid references moment(id),
  title varchar(220) not null,
  summary text,
  content text not null,
  theme varchar(180) not null,
  place varchar(180),
  favorite_animal varchar(120),
  story_style varchar(30) not null,
  story_length varchar(20) not null,
  favorite boolean not null default false,
  generation_type varchar(20) not null,
  created_by_user_id uuid not null references app_user(id),
  created_at timestamp with time zone not null default now(),
  updated_at timestamp with time zone not null default now(),
  active boolean not null default true
);

create index idx_story_family_id on story(family_id);
create index idx_story_child_id on story(child_id);
create index idx_story_source_moment_id on story(source_moment_id);
create index idx_story_created_by_user_id on story(created_by_user_id);
create index idx_story_family_created_at on story(family_id, created_at desc);
create index idx_story_family_child on story(family_id, child_id);
