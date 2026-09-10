import * as Speech from 'expo-speech';
import { setAudioModeAsync } from 'expo-audio';
import { StoryChapter } from '../types/api';
import { normalizeStoryText } from '../utils/storyText';

const NARRATION_LANGUAGE = 'pt-BR';
const NARRATION_RATE = 0.9;

type NarrationCallbacks = {
  onStart?: () => void;
  onDone?: () => void;
  onStopped?: () => void;
  onError?: (error: Error) => void;
};

let stopped = true;
let narrationSession = 0;

function storyText(chapters: StoryChapter[]) {
  return chapters
    .slice()
    .sort((left, right) => left.number - right.number)
    .map(chapter => normalizeStoryText(chapter.content))
    .filter(Boolean)
    .join('\n\n')
    .trim();
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

function speakChunk(session: number, chunks: string[], chunkIndex: number, callbacks: NarrationCallbacks) {
  if (stopped || session !== narrationSession) {
    callbacks.onStopped?.();
    return;
  }
  const text = chunks[chunkIndex];
  if (!text) {
    stopped = true;
    callbacks.onDone?.();
    return;
  }

  if (chunkIndex === 0) {
    callbacks.onStart?.();
  }

  Speech.speak(text, {
    language: NARRATION_LANGUAGE,
    rate: NARRATION_RATE,
    onDone: () => {
      if (stopped || session !== narrationSession) {
        callbacks.onStopped?.();
        return;
      }
      setTimeout(() => speakChunk(session, chunks, chunkIndex + 1, callbacks), 120);
    },
    onStopped: () => callbacks.onStopped?.(),
    onError: error => callbacks.onError?.(error instanceof Error ? error : new Error('Falha na narracao')),
  });
}

export async function speakStoryChapters(chapters: StoryChapter[], callbacks: NarrationCallbacks = {}) {
  await stopStoryNarration();
  const text = storyText(chapters);
  if (!text) {
    callbacks.onDone?.();
    return;
  }
  try {
    await setAudioModeAsync({ playsInSilentMode: true, shouldPlayInBackground: false });
  } catch (exception) {
    callbacks.onError?.(exception instanceof Error ? exception : new Error('Falha ao configurar o áudio'));
    return;
  }
  stopped = false;
  const session = ++narrationSession;
  speakChunk(session, safeSpeechChunks(text), 0, callbacks);
}

export async function stopStoryNarration() {
  stopped = true;
  narrationSession += 1;
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
