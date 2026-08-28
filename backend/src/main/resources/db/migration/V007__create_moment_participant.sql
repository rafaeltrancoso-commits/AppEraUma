create table moment_participant (
  id uuid primary key,
  moment_id uuid not null references moment(id),
  name varchar(120) not null,
  participant_type varchar(20) not null,
  user_id uuid references app_user(id),
  created_at timestamp with time zone not null default now()
);

create index idx_moment_participant_moment_id on moment_participant(moment_id);
create index idx_moment_participant_user_id on moment_participant(user_id);
