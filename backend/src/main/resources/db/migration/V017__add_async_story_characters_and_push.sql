alter table story add column generation_status varchar(40) not null default 'CONCLUIDA';
alter table story add column generation_mode varchar(30) not null default 'TEXT_ONLY';
alter table story add column generation_error varchar(500);
alter table story add column idempotency_key varchar(120);
alter table story add column other_characters varchar(500);

alter table story add constraint uk_story_user_idempotency unique (created_by_user_id, idempotency_key);
create index idx_story_generation_status on story(generation_status, created_at);

create table story_character (
  id uuid primary key,
  story_id uuid not null references story(id) on delete cascade,
  child_profile_id uuid not null references child_profile(id),
  selection_order integer not null,
  role varchar(20) not null,
  visual_description text,
  created_at timestamp with time zone not null default now(),
  constraint ck_story_character_order check (selection_order between 1 and 3),
  constraint ck_story_character_role check (role in ('PROTAGONIST', 'SECONDARY')),
  constraint uk_story_character_profile unique (story_id, child_profile_id),
  constraint uk_story_character_order unique (story_id, selection_order)
);

create index idx_story_character_profile on story_character(child_profile_id);

-- Compatibilidade: histórias antigas passam a expor seu child_id como protagonista.
insert into story_character (id, story_id, child_profile_id, selection_order, role)
select gen_random_uuid(), id, child_id, 1, 'PROTAGONIST'
from story
where child_id is not null;

alter table story_image add column visual_format varchar(40) not null default 'SINGLE_SCENE';
alter table story_image add column attempt_count integer not null default 0;

create table push_device_token (
  id uuid primary key,
  user_id uuid not null references app_user(id) on delete cascade,
  device_id varchar(180) not null,
  expo_push_token varchar(300) not null,
  platform varchar(20) not null,
  active boolean not null default true,
  last_error varchar(500),
  created_at timestamp with time zone not null default now(),
  updated_at timestamp with time zone not null default now(),
  constraint uk_push_device_user_device unique (user_id, device_id),
  constraint uk_push_device_token unique (expo_push_token),
  constraint ck_push_device_platform check (platform in ('ANDROID', 'IOS'))
);

create index idx_push_device_user_active on push_device_token(user_id, active);
