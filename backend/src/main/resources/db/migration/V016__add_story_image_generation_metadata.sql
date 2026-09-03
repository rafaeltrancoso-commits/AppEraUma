alter table story_image add column chapter_start integer;
alter table story_image add column chapter_end integer;
alter table story_image add column prompt_text text;
alter table story_image add column error_message varchar(500);

alter table story_image
  add constraint uk_story_image_plan unique (story_id, image_type, sort_order);

alter table ai_image_generation_log add column represented_chapters varchar(40);
alter table ai_image_generation_log add column prompt_text text;
alter table ai_image_generation_log add column error_message varchar(500);
