import { Story } from '../types/api';

export const STORY_POLL_INTERVAL_MS = 5_000;
export const STORY_POLL_SLOW_INTERVAL_MS = 10_000;
export const STORY_POLL_DELAYED_AFTER = 24;

export function isStoryProcessing(story: Story): boolean {
  const imageProcessing = story.images?.some(image => image.status === 'PENDING' || image.status === 'GENERATING') ?? false;
  return imageProcessing || story.generationStatus === 'PENDENTE'
    || story.generationStatus === 'PROCESSANDO_TEXTO'
    || story.generationStatus === 'PROCESSANDO_IMAGENS';
}

type Timer = ReturnType<typeof setTimeout>;

export type StoryPollingOptions = {
  load: (storyId: string) => Promise<Story>;
  onUpdate: (story: Story) => void;
  onDelayed?: () => void;
  onTransientError?: () => void;
  schedule?: (callback: () => void, delayMs: number) => Timer;
  cancel?: (timer: Timer) => void;
};

export class StoryPollingController {
  private story: Story;
  private readonly options: StoryPollingOptions;
  private timer?: Timer;
  private running = false;
  private stopped = true;
  private pollCount = 0;
  private delayedReported = false;

  constructor(story: Story, options: StoryPollingOptions) {
    this.story = story;
    this.options = options;
  }

  start(): void {
    if (!this.stopped) {return;}
    this.stopped = false;
    this.refreshNow();
  }

  stop(): void {
    this.stopped = true;
    this.clearTimer();
  }

  observe(story: Story): void {
    this.story = story;
    if (this.stopped) {return;}
    this.clearTimer();
    if (isStoryProcessing(story)) {this.scheduleNext();}
  }

  foreground(): void {
    if (this.stopped) {return;}
    this.clearTimer();
    this.refreshNow();
  }

  refreshNow(): void {
    if (this.stopped || this.running) {return;}
    this.clearTimer();
    this.running = true;
    const requestedId = this.story.id;
    this.options.load(requestedId).then(updated => {
      if (this.stopped || updated.id !== requestedId) {return;}
      this.story = updated;
      this.pollCount += 1;
      this.options.onUpdate(updated);
      if (isStoryProcessing(updated)) {
        if (this.pollCount >= STORY_POLL_DELAYED_AFTER && !this.delayedReported) {
          this.delayedReported = true;
          this.options.onDelayed?.();
        }
      }
    }).catch(() => {
      if (this.stopped) {return;}
      this.options.onTransientError?.();
    }).finally(() => {
      this.running = false;
      if (!this.stopped && isStoryProcessing(this.story)) {this.scheduleNext();}
    });
  }

  private scheduleNext(): void {
    if (this.stopped || this.timer || this.running) {return;}
    const schedule = this.options.schedule ?? ((callback, delayMs) => setTimeout(callback, delayMs));
    const delay = this.pollCount >= STORY_POLL_DELAYED_AFTER ? STORY_POLL_SLOW_INTERVAL_MS : STORY_POLL_INTERVAL_MS;
    this.timer = schedule(() => {
      this.timer = undefined;
      this.refreshNow();
    }, delay);
  }

  private clearTimer(): void {
    if (!this.timer) {return;}
    const cancel = this.options.cancel ?? clearTimeout;
    cancel(this.timer);
    this.timer = undefined;
  }
}
