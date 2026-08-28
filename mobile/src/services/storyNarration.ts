import * as Speech from 'expo-speech';
import { StoryChapter } from '../types/api';
import { normalizeStoryText } from '../utils/storyText';

const NARRATION_LANGUAGE = 'pt-BR';
const NARRATION_RATE = 0.9;

type NarrationCallbacks = {
  onChapterStart?: (chapterIndex: number, totalChapters: number) => void;
  onDone?: () => void;
  onStopped?: () => void;
  onError?: (error: Error) => void;
};

let stopped = true;

function chapterText(chapter: StoryChapter) {
  return ['Capitulo ' + chapter.number, chapter.title, normalizeStoryText(chapter.content)].filter(Boolean).join('. ').trim();
}

function safeSpeechChunks(text: string) {
  const maxLength = Math.max(1, Speech.maxSpeechInputLength || 3500);
  if (text.length <= maxLength) {
    return [text];
  }

  const chunks: string[] = [];
  let remaining = text.trim();
  while (remaining.length > maxLength) {
    const slice = remaining.slice(0, maxLength);
    const splitAt = Math.max(slice.lastIndexOf('. '), slice.lastIndexOf('! '), slice.lastIndexOf('? '), slice.lastIndexOf(' '));
    const index = splitAt > 0 ? splitAt + 1 : maxLength;
    chunks.push(remaining.slice(0, index).trim());
    remaining = remaining.slice(index).trim();
  }
  if (remaining) {
    chunks.push(remaining);
  }
  return chunks;
}

function speakChunk(chunks: string[], chunkIndex: number, onDone: () => void, callbacks: NarrationCallbacks) {
  if (stopped) {
    callbacks.onStopped?.();
    return;
  }
  const text = chunks[chunkIndex];
  if (!text) {
    onDone();
    return;
  }

  Speech.speak(text, {
    language: NARRATION_LANGUAGE,
    rate: NARRATION_RATE,
    onDone: () => speakChunk(chunks, chunkIndex + 1, onDone, callbacks),
    onStopped: () => callbacks.onStopped?.(),
    onError: error => callbacks.onError?.(error instanceof Error ? error : new Error('Falha na narracao')),
  });
}

function speakChapter(chapters: StoryChapter[], chapterIndex: number, callbacks: NarrationCallbacks) {
  if (stopped) {
    callbacks.onStopped?.();
    return;
  }
  const chapter = chapters[chapterIndex];
  if (!chapter) {
    stopped = true;
    callbacks.onDone?.();
    return;
  }

  callbacks.onChapterStart?.(chapterIndex, chapters.length);
  speakChunk(safeSpeechChunks(chapterText(chapter)), 0, () => speakChapter(chapters, chapterIndex + 1, callbacks), callbacks);
}

export async function speakStoryChapters(chapters: StoryChapter[], callbacks: NarrationCallbacks = {}) {
  await stopStoryNarration();
  stopped = false;
  speakChapter(chapters, 0, callbacks);
}

export async function stopStoryNarration() {
  stopped = true;
  await Speech.stop();
}

export function isStoryNarrationSpeaking() {
  return Speech.isSpeakingAsync();
}

export const storyNarrationSettings = {
  language: NARRATION_LANGUAGE,
  rate: NARRATION_RATE,
  pauseResumeSupportedOnAndroid: false,
};
