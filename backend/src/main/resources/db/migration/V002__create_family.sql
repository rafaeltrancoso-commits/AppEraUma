create table family (
  id uuid primary key,
  name varchar(120) not null,
  created_by_user_id uuid not null references app_user(id),
  created_at timestamp with time zone not null default now(),
  updated_at timestamp with time zone not null default now(),
  active boolean not null default true
);

