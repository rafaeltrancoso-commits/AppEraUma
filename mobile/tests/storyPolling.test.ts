import { StoryPollingController, isStoryProcessing, STORY_POLL_DELAYED_AFTER, STORY_POLL_INTERVAL_MS, STORY_POLL_SLOW_INTERVAL_MS } from '../src/services/storyPolling';
import { Story } from '../src/types/api';

function story(status: Story['generationStatus'], imageStatus: Story['images'][number]['status'] = 'GENERATED'): Story {
  return {
    id: 'story-1', title: 'História', summary: '', theme: '', style: 'ADVENTURE', length: 'SHORT',
    favorite: false, generationType: 'AI', chapters: [], createdAt: '2026-01-01T00:00:00Z',
    generationStatus: status, images: [{ id: 'image-1', type: 'COVER', status: imageStatus, sortOrder: 0 }],
  };
}

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) {throw new Error(message);}
}

async function flush(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
  await Promise.resolve();
}

async function run(): Promise<void> {
  assert(isStoryProcessing(story('PENDENTE')), 'PENDENTE deve ser acompanhado');
  assert(isStoryProcessing(story('PROCESSANDO_TEXTO')), 'PROCESSANDO_TEXTO deve ser acompanhado');
  assert(isStoryProcessing(story('PROCESSANDO_IMAGENS')), 'PROCESSANDO_IMAGENS deve ser acompanhado');
  assert(isStoryProcessing(story('CONCLUIDA', 'PENDING')), 'imagem PENDING deve ser acompanhada');
  assert(isStoryProcessing(story('CONCLUIDA', 'GENERATING')), 'imagem GENERATING deve ser acompanhada');
  assert(!isStoryProcessing(story('CONCLUIDA')), 'CONCLUIDA deve encerrar');
  assert(!isStoryProcessing(story('CONCLUIDA_COM_FALHAS', 'FAILED')), 'falha terminal deve encerrar');
  assert(!isStoryProcessing(story('ERRO', 'FAILED')), 'ERRO deve encerrar');

  const scheduled: Array<{ callback: () => void; delay: number; cancelled: boolean }> = [];
  const updates: Story[] = [];
  let loads = 0;
  let resolveLoad: ((value: Story) => void) | undefined;
  const controller = new StoryPollingController(story('PROCESSANDO_IMAGENS', 'PENDING'), {
    load: () => {
      loads += 1;
      return new Promise(resolve => { resolveLoad = resolve; });
    },
    onUpdate: updated => updates.push(updated),
    schedule: (callback, delay) => {
      const item = { callback, delay, cancelled: false };
      scheduled.push(item);
      return item as unknown as ReturnType<typeof setTimeout>;
    },
    cancel: timer => { (timer as unknown as { cancelled: boolean }).cancelled = true; },
  });
  controller.start();
  assert(loads === 1, 'start deve atualizar imediatamente');
  controller.refreshNow();
  assert(loads === 1, 'não deve sobrepor requisições');
  resolveLoad?.(story('PROCESSANDO_IMAGENS', 'GENERATING'));
  await flush();
  assert(updates.length === 1, 'deve publicar resposta válida');
  assert(scheduled[0]?.delay === STORY_POLL_INTERVAL_MS, 'deve usar intervalo normal');
  scheduled[0].callback();
  assert(Number(loads) === 2, 'timer deve iniciar nova consulta');
  resolveLoad?.(story('CONCLUIDA', 'GENERATED'));
  await flush();
  assert(scheduled.length === 1, 'estado terminal não agenda nova consulta');
  controller.foreground();
  assert(Number(loads) === 3, 'retorno ao foreground deve revalidar estado terminal');
  controller.stop();
  resolveLoad?.(story('CONCLUIDA'));
  await flush();
  assert(Number(updates.length) === 2, 'resposta posterior ao unmount deve ser ignorada');

  let transientErrors = 0;
  const retries: Array<{ callback: () => void; delay: number }> = [];
  const retrying = new StoryPollingController(story('PROCESSANDO_TEXTO', 'PENDING'), {
    load: async () => { throw new Error('rede'); },
    onUpdate: () => { throw new Error('não deveria atualizar'); },
    onTransientError: () => { transientErrors += 1; },
    schedule: (callback, delay) => {
      retries.push({ callback, delay });
      return retries[retries.length - 1] as unknown as ReturnType<typeof setTimeout>;
    },
  });
  retrying.start();
  await flush();
  assert(transientErrors === 1, 'erro transitório deve ser informado');
  assert(retries.length === 1, 'erro transitório deve manter o polling');
  retrying.stop();

  let delayed = 0;
  const slowSchedules: number[] = [];
  const slowCallbacks: Array<() => void> = [];
  const alwaysProcessing = new StoryPollingController(story('PROCESSANDO_IMAGENS', 'PENDING'), {
    load: async () => story('PROCESSANDO_IMAGENS', 'PENDING'),
    onUpdate: () => undefined,
    onDelayed: () => { delayed += 1; },
    schedule: (callback, delay) => {
      slowSchedules.push(delay);
      slowCallbacks.push(callback);
      return { delay } as unknown as ReturnType<typeof setTimeout>;
    },
    cancel: () => undefined,
  });
  alwaysProcessing.start();
  await flush();
  for (let index = 1; index < STORY_POLL_DELAYED_AFTER + 2; index += 1) {
    const callback = slowCallbacks.shift();
    assert(callback, 'consulta em processamento deve agendar o próximo ciclo');
    callback();
    await flush();
  }
  alwaysProcessing.stop();
  assert(delayed === 1, 'aviso de demora deve ocorrer uma única vez');
  assert(slowSchedules.includes(STORY_POLL_SLOW_INTERVAL_MS), 'polling prolongado deve reduzir frequência');

  console.info('storyPolling: todos os cenários passaram');
}

run().catch(error => {
  console.error(error);
  throw error;
});
