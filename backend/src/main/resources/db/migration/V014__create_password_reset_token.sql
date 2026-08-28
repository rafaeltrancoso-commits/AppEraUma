create table password_reset_token (
  id uuid primary key,
  user_id uuid not null references app_user(id),
  token_hash varchar(128) not null unique,
  expires_at timestamp with time zone not null,
  used_at timestamp with time zone,
  created_at timestamp with time zone not null default now()
);

create index idx_password_reset_token_user_created_at on password_reset_token(user_id, created_at desc);
create index idx_password_reset_token_hash on password_reset_token(token_hash);
