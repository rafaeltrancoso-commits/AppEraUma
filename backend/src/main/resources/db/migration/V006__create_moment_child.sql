create table moment_child (
  moment_id uuid not null references moment(id),
  child_id uuid not null references child_profile(id),
  created_at timestamp with time zone not null default now(),
  primary key (moment_id, child_id)
);

create index idx_moment_child_child_id on moment_child(child_id);
