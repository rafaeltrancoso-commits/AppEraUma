import { createAudioPlayer, setAudioModeAsync } from 'expo-audio';
import { StoryAudioPart } from '../types/api';
import { apiContentUrl } from './api';
import { getToken } from './tokenStorage';

type PlaybackCallbacks = {
  onPlaying?: () => void;
  onPaused?: () => void;
  onDone?: () => void;
  onStopped?: () => void;
  onError?: (error: Error) => void;
};

let currentPlayer: ReturnType<typeof createAudioPlayer> | undefined;
let currentSubscription: { remove: () => void } | undefined;
let currentParts: StoryAudioPart[] = [];
let currentIndex = 0;
let callbacks: PlaybackCallbacks = {};
let session = 0;
let advancing = false;

function releasePlayer() {
  currentSubscription?.remove();
  currentSubscription = undefined;
  currentPlayer?.remove();
  currentPlayer = undefined;
}

async function playPart(playbackSession: number) {
  if (playbackSession !== session) { return; }
  const part = currentParts[currentIndex];
  if (!part?.contentUrl) {
    releasePlayer();
    callbacks.onDone?.();
    return;
  }
  const token = await getToken();
  if (!token) { throw new Error('Sessão indisponível para carregar a narração.'); }
  releasePlayer();
  currentPlayer = createAudioPlayer({
    uri: apiContentUrl(part.contentUrl),
    headers: { Authorization: `Bearer ${token}` },
  });
  advancing = false;
  currentSubscription = currentPlayer.addListener('playbackStatusUpdate', status => {
    if (playbackSession !== session) { return; }
    if (status.didJustFinish && !advancing) {
      advancing = true;
      currentIndex += 1;
      playPart(playbackSession).catch(handleError);
    }
  });
  currentPlayer.play();
  callbacks.onPlaying?.();
}

function handleError(error: unknown) {
  releasePlayer();
  callbacks.onError?.(error instanceof Error ? error : new Error('Falha ao reproduzir a narração.'));
}

export async function startAiStoryNarration(parts: StoryAudioPart[], nextCallbacks: PlaybackCallbacks = {}) {
  await stopAiStoryNarration(false);
  const playable = parts
    .filter(part => part.status === 'COMPLETED' && Boolean(part.contentUrl))
    .slice()
    .sort((left, right) => left.chapterNumber - right.chapterNumber || left.chunkIndex - right.chunkIndex);
  if (playable.length === 0) { throw new Error('A narração ainda não está pronta.'); }
  await setAudioModeAsync({ playsInSilentMode: true, shouldPlayInBackground: false });
  currentParts = playable;
  currentIndex = 0;
  advancing = false;
  callbacks = nextCallbacks;
  const playbackSession = ++session;
  try { await playPart(playbackSession); } catch (error) { handleError(error); }
}

export function pauseAiStoryNarration() {
  if (!currentPlayer) { return; }
  currentPlayer.pause();
  callbacks.onPaused?.();
}

export function resumeAiStoryNarration() {
  if (!currentPlayer) { return; }
  currentPlayer.play();
  callbacks.onPlaying?.();
}

export async function stopAiStoryNarration(notify = true) {
  session += 1;
  releasePlayer();
  currentParts = [];
  currentIndex = 0;
  const previous = callbacks;
  callbacks = {};
  if (notify) { previous.onStopped?.(); }
}
