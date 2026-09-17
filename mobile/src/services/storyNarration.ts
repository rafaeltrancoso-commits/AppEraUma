import * as Speech from 'expo-speech';
import { setAudioModeAsync } from 'expo-audio';
import { StoryChapter } from '../types/api';
import { normalizeStoryText } from '../utils/storyText';

const NARRATION_LANGUAGE = 'pt-BR';
const NARRATION_RATE = 0.92;
const NARRATION_PITCH = 1.08;

type NarrationCallbacks = {
  onStart?: () => void;
  onDone?: () => void;
  onStopped?: () => void;
  onError?: (error: Error) => void;
};

let stopped = true;
let narrationSession = 0;
let preferredVoicePromise: Promise<string | undefined> | undefined;

function isHigherQualityVoice(voice: Speech.Voice) {
  if (voice.quality === Speech.VoiceQuality.Enhanced) {
    return true;
  }
  const haystack = `${voice.identifier} ${voice.name}`.toLowerCase();
  return haystack.includes('enhanced') || haystack.includes('premium') || haystack.includes('neural');
}

async function resolvePreferredVoiceIdentifier(): Promise<string | undefined> {
  try {
    const voices = await Speech.getAvailableVoicesAsync();
    const ptBrVoices = voices.filter(voice => voice.language?.toLowerCase().startsWith('pt-br'));
    const candidates = ptBrVoices.length > 0
      ? ptBrVoices
      : voices.filter(voice => voice.language?.toLowerCase().startsWith('pt'));
    if (candidates.length === 0) {
      return undefined;
    }
    const bestVoice = candidates.find(isHigherQualityVoice) ?? candidates[0];
    return bestVoice.identifier;
  } catch {
    return undefined;
  }
}

function getPreferredVoiceIdentifier(): Promise<string | undefined> {
  if (!preferredVoicePromise) {
    preferredVoicePromise = resolvePreferredVoiceIdentifier();
  }
  return preferredVoicePromise;
}

function storyText(chapters: StoryChapter[]) {
  return chapters
    .slice()
    .sort((left, right) => left.number - right.number)
    .map(chapter => normalizeStoryText(chapter.content))
    .filter(Boolean)
    .join('\n\n')
    .trim();
}

const SENTENCE_END_REGEX = /(?:\.\.\.|[.!?]|…)(?=\s|$)/g;

function lastSentenceBoundary(text: string, maxLength: number) {
  SENTENCE_END_REGEX.lastIndex = 0;
  let boundary = -1;
  let match: RegExpExecArray | null;
  while ((match = SENTENCE_END_REGEX.exec(text))) {
    const end = match.index + match[0].length;
    if (end > maxLength) {
      break;
    }
    boundary = end;
  }
  return boundary;
}

function safeSpeechChunks(text: string) {
  const maxLength = Math.max(1, Speech.maxSpeechInputLength || 3500);
  if (text.length <= maxLength) {
    return [text];
  }

  const chunks: string[] = [];
  let remaining = text.trim();
  while (remaining.length > maxLength) {
    const sentenceBoundary = lastSentenceBoundary(remaining, maxLength);
    let index: number;
    if (sentenceBoundary > 0) {
      index = sentenceBoundary;
    } else {
      const spaceIndex = remaining.slice(0, maxLength).lastIndexOf(' ');
      index = spaceIndex > 0 ? spaceIndex + 1 : maxLength;
    }
    chunks.push(remaining.slice(0, index).trim());
    remaining = remaining.slice(index).trim();
  }
  if (remaining) {
    chunks.push(remaining);
  }
  return chunks;
}

function speakChunk(session: number, chunks: string[], chunkIndex: number, callbacks: NarrationCallbacks, voiceIdentifier: string | undefined) {
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
    pitch: NARRATION_PITCH,
    voice: voiceIdentifier,
    onDone: () => {
      if (stopped || session !== narrationSession) {
        callbacks.onStopped?.();
        return;
      }
      setTimeout(() => speakChunk(session, chunks, chunkIndex + 1, callbacks, voiceIdentifier), 120);
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
  const voiceIdentifier = await getPreferredVoiceIdentifier();
  stopped = false;
  const session = ++narrationSession;
  speakChunk(session, safeSpeechChunks(text), 0, callbacks, voiceIdentifier);
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
  pitch: NARRATION_PITCH,
  pauseResumeSupportedOnAndroid: false,
};
