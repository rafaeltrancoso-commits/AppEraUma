create table moment (
  id uuid primary key,
  family_id uuid not null references family(id),
  title varchar(180) not null,
  description text,
  occurred_at timestamp not null,
  location_name varchar(180),
  favorite boolean not null default false,
  created_by_user_id uuid not null references app_user(id),
  created_at timestamp with time zone not null default now(),
  updated_at timestamp with time zone not null default now(),
  active boolean not null default true
);

create index idx_moment_family_id on moment(family_id);
create index idx_moment_occurred_at on moment(occurred_at);
create index idx_moment_created_by_user_id on moment(created_by_user_id);
create index idx_moment_family_occurred_at on moment(family_id, occurred_at desc);
