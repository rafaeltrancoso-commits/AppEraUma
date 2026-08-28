create table family_member (
  id uuid primary key,
  family_id uuid not null references family(id),
  user_id uuid not null references app_user(id),
  role varchar(20) not null,
  created_at timestamp with time zone not null default now(),
  active boolean not null default true,
  constraint uk_family_member_user unique (family_id, user_id)
);

