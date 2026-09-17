import { useEffect, useRef } from 'react';
import { AppState } from 'react-native';
import { StoryPollingController } from '../services/storyPolling';
import { Story } from '../types/api';

type Options = {
  story: Story;
  load: (storyId: string) => Promise<Story>;
  onUpdate: (story: Story) => void;
  onDelayed: () => void;
  onTransientError: () => void;
};

export function useStoryPolling({ story, load, onUpdate, onDelayed, onTransientError }: Options): void {
  const callbacks = useRef({ load, onUpdate, onDelayed, onTransientError });
  const controller = useRef<StoryPollingController | undefined>(undefined);
  const storyRef = useRef(story);
  const storyId = story.id;
  callbacks.current = { load, onUpdate, onDelayed, onTransientError };
  storyRef.current = story;

  useEffect(() => {
    const polling = new StoryPollingController(storyRef.current, {
      load: id => callbacks.current.load(id),
      onUpdate: updated => callbacks.current.onUpdate(updated),
      onDelayed: () => callbacks.current.onDelayed(),
      onTransientError: () => callbacks.current.onTransientError(),
    });
    controller.current = polling;
    polling.start();
    const subscription = AppState.addEventListener('change', state => {
      if (state === 'active') {polling.foreground();}
    });
    return () => {
      subscription.remove();
      polling.stop();
      if (controller.current === polling) {controller.current = undefined;}
    };
  }, [storyId]);

  useEffect(() => {
    controller.current?.observe(story);
  }, [story]);

}
