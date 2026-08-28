create table moment_photo (
  id uuid primary key,
  moment_id uuid not null references moment(id),
  storage_key varchar(120) not null unique,
  original_filename varchar(255),
  content_type varchar(80) not null,
  size_bytes bigint not null,
  sort_order integer not null default 0,
  created_at timestamp with time zone not null default now(),
  active boolean not null default true
);

create index idx_moment_photo_moment_id on moment_photo(moment_id);
create index idx_moment_photo_moment_sort on moment_photo(moment_id, sort_order);
