alter table story alter column child_id drop not null;

alter table story add column main_character_name varchar(120);
alter table story add column second_character_name varchar(120);
