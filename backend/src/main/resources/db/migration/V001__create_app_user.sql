create table app_user (
  id uuid primary key,
  name varchar(120) not null,
  email varchar(180) not null unique,
  password_hash varchar(255) not null,
  created_at timestamp with time zone not null default now(),
  updated_at timestamp with time zone not null default now(),
  active boolean not null default true
);

