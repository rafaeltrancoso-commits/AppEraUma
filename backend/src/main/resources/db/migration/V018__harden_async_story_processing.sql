-- Correções incrementais para ambientes onde a V017 já tenha sido aplicada.
alter table story add column generation_attempt_count integer not null default 0;

-- Histórias ilustradas anteriores à V017 receberam o default TEXT_ONLY.
update story
set generation_mode = 'ILLUSTRATED'
where exists (select 1 from story_image where story_image.story_id = story.id);

alter table story
  add constraint ck_story_generation_status
  check (generation_status in ('PENDENTE', 'PROCESSANDO_TEXTO', 'PROCESSANDO_IMAGENS', 'CONCLUIDA', 'CONCLUIDA_COM_FALHAS', 'ERRO'));

alter table story
  add constraint ck_story_generation_mode
  check (generation_mode in ('TEXT_ONLY', 'ILLUSTRATED'));

alter table story
  add constraint ck_story_generation_attempt_count
  check (generation_attempt_count >= 0);

alter table story_image
  add constraint ck_story_image_status
  check (status in ('PENDING', 'GENERATING', 'GENERATED', 'FAILED'));

alter table story_image
  add constraint ck_story_image_visual_format
  check (visual_format in ('SINGLE_SCENE', 'COMIC_THREE_PANELS'));

alter table story_image
  add constraint ck_story_image_attempt_count
  check (attempt_count >= 0);
