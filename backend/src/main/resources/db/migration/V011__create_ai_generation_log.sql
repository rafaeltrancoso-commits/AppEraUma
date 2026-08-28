create table ai_generation_log (
  id uuid primary key,
  user_id uuid not null references app_user(id),
  family_id uuid not null references family(id),
  story_id uuid references story(id),
  provider varchar(40) not null,
  model varchar(120),
  status varchar(40) not null,
  input_tokens integer,
  output_tokens integer,
  duration_ms bigint,
  created_at timestamp with time zone not null default now()
);

create index idx_ai_generation_log_user_created_at on ai_generation_log(user_id, created_at desc);
create index idx_ai_generation_log_family_created_at on ai_generation_log(family_id, created_at desc);
