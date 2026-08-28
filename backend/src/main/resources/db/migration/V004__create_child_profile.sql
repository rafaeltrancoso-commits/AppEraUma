create table child_profile (
  id uuid primary key,
  family_id uuid not null references family(id),
  name varchar(120) not null,
  birth_date date,
  nickname varchar(120),
  favorite_animal varchar(120),
  avatar_url varchar(500),
  created_at timestamp with time zone not null default now(),
  updated_at timestamp with time zone not null default now(),
  active boolean not null default true
);

