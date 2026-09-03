import React, { useEffect, useState } from 'react';
import { Alert, Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import { AppButton } from '../components/AppButton';
import { AuthenticatedStoryImage } from '../components/AuthenticatedStoryImage';
import { Screen } from '../components/Screen';
import { eraumaApi } from '../services/eraumaApi';
import { speakStoryChapters, stopStoryNarration } from '../services/storyNarration';
import { Story } from '../types/api';
import { theme } from '../theme/tokens';
import { normalizeStoryText, storyParagraphs } from '../utils/storyText';
import { formatLongDatePtBr as formatDate } from '../utils/dateFormat';

type Props = {
  story: Story;
  onBack: () => void;
  onCreateAnother: () => void;
  onLibrary: () => void;
  onChanged: (story?: Story) => void;
};

export function StoryReaderScreen({ story: initialStory, onBack, onCreateAnother, onLibrary, onChanged }: Props) {
  const [story, setStory] = useState(initialStory);
  const [loading, setLoading] = useState(false);
  const [narrating, setNarrating] = useState(false);
  const [narratingChapter, setNarratingChapter] = useState<number | null>(null);
  useEffect(() => () => { stopStoryNarration().catch(() => undefined); }, []);
  useEffect(() => {
    setStory(initialStory);
  }, [initialStory]);
  useEffect(() => {
    const hasProcessingImages = story.images?.some(image => image.status === 'PENDING' || image.status === 'GENERATING');
    if (!hasProcessingImages) {
      return undefined;
    }
    let cancelled = false;
    const interval = setInterval(() => {
      eraumaApi.story(story.id).then(updated => {
        if (!cancelled) {
          setStory(updated);
          onChanged(updated);
        }
      }).catch(() => undefined);
    }, 5000);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [onChanged, story.id, story.images]);

  async function favorite() {
    const previous = story;
    const optimistic = { ...story, favorite: !story.favorite };
    setStory(optimistic);
    try {
      const updated = await eraumaApi.favoriteStory(story.id, optimistic.favorite);
      setStory(updated);
      onChanged(updated);
    } catch {
      setStory(previous);
      Alert.alert('Não foi possível atualizar', 'Tente novamente em alguns instantes.');
    }
  }

  function confirmDelete() {
    if (loading) {
      return;
    }
    if (Platform.OS === 'web') {
      // eslint-disable-next-line no-alert
      if (window.confirm('Excluir história?\n\nEssa história será removida da sua biblioteca.')) {
        deleteStory().catch(() => undefined);
      }
      return;
    }
    Alert.alert('Excluir história?', 'Essa história será removida da sua biblioteca.', [
      { text: 'Cancelar', style: 'cancel' },
      { text: 'Excluir', style: 'destructive', onPress: deleteStory },
    ]);
  }

  async function deleteStory() {
    if (loading) {
      return;
    }
    setLoading(true);
    try {
      await stopNarration();
      await eraumaApi.deleteStory(story.id);
      onChanged(undefined);
    } catch (exception) {
      if (__DEV__) {
        console.warn('story_delete_failed', { storyId: story.id, status: exception instanceof Error && 'status' in exception ? exception.status : undefined });
      }
      Alert.alert('Não foi possível excluir a história. Tente novamente.');
    } finally {
      setLoading(false);
    }
  }

  async function startNarration() {
    if (narrating || story.chapters.length === 0) {
      return;
    }
    setNarrating(true);
    setNarratingChapter(1);
    await speakStoryChapters(story.chapters, {
      onChapterStart: chapterIndex => setNarratingChapter(chapterIndex + 1),
      onDone: () => {
        setNarrating(false);
        setNarratingChapter(null);
      },
      onStopped: () => {
        setNarrating(false);
        setNarratingChapter(null);
      },
      onError: () => {
        setNarrating(false);
        setNarratingChapter(null);
        Alert.alert('Não foi possível narrar', 'Verifique o Text-to-Speech do dispositivo e tente novamente.');
      },
    });
  }

  async function stopNarration() {
    await stopStoryNarration();
    setNarrating(false);
    setNarratingChapter(null);
  }

  async function leave(action: () => void) {
    await stopNarration();
    action();
  }

  async function retryImage(imageId: string) {
    try {
      await eraumaApi.retryStoryImage(imageId);
      const updated = await eraumaApi.story(story.id);
      setStory(updated);
      onChanged(updated);
    } catch {
      Alert.alert('Não foi possível tentar novamente', 'A história continua disponível para leitura.');
    }
  }

  const characterName = story.mainCharacterName || story.child?.name || 'Personagem';
  const cover = story.images?.find(image => image.type === 'COVER');
  const scenes = story.images?.filter(image => image.type === 'SCENE') ?? [];
  const illustrationInProgress = story.images?.some(image => image.status === 'PENDING' || image.status === 'GENERATING');
  const failedImages = story.images?.filter(image => image.status === 'FAILED') ?? [];

  function sceneForChapter(chapterNumber: number) {
    return scenes.find(image => image.chapterEnd === chapterNumber) ?? scenes.find((_, index) => index === chapterNumber - 1);
  }

  return (
    <Screen>
      <Pressable onPress={() => { leave(onBack).catch(() => undefined); }}><Text style={styles.back}>← Voltar</Text></Pressable>
      <Text style={styles.eyebrow}>📖 História de {characterName}</Text>
      <Text style={styles.title}>{story.title}</Text>
      {illustrationInProgress ? <Text style={styles.ready}>Sua história está pronta!{'\n'}Estamos preparando as ilustrações.</Text> : null}
      <AuthenticatedStoryImage image={cover} style={styles.cover} resizeMode="contain" />
      {cover ? <Text style={styles.imageHint}>Toque na ilustração para ampliar.</Text> : null}
      <Text style={styles.date}>{formatDate(story.createdAt)}</Text>
      {story.secondCharacterName ? <Text style={styles.date}>Com {story.secondCharacterName}</Text> : null}
      <Text style={styles.summary}>{normalizeStoryText(story.summary)}</Text>
      {narrating ? <Text style={styles.narration}>Narrando capítulo {narratingChapter ?? 1} de {story.chapters.length}</Text> : null}
      <AppButton title={narrating ? '⏹ Parar narração' : '🔊 Ouvir história'} onPress={narrating ? stopNarration : startNarration} variant="secondary" />
      {story.chapters.map(chapter => (
        <View key={chapter.number} style={styles.chapter}>
          <Text style={styles.chapterNumber}>Capítulo {chapter.number}</Text>
          <Text style={styles.chapterTitle}>{chapter.title}</Text>
          {storyParagraphs(chapter.content).map((paragraph, paragraphIndex) => (
            <Text key={`${chapter.number}-${paragraphIndex}`} style={styles.content}>{paragraph}</Text>
          ))}
          <AuthenticatedStoryImage image={sceneForChapter(chapter.number)} style={styles.scene} resizeMode="contain" />
        </View>
      ))}
      {failedImages.length > 0 ? (
        <View style={styles.retryBox}>
          <Text style={styles.retryText}>Algumas ilustrações não ficaram prontas.</Text>
          {failedImages.map(image => (
            <AppButton key={image.id} title="Tentar ilustração novamente" onPress={() => retryImage(image.id)} variant="secondary" />
          ))}
        </View>
      ) : null}
      <AppButton title={story.favorite ? '♥ Favorita' : '♡ Favoritar'} onPress={favorite} variant="secondary" />
      <AppButton title="📚 Biblioteca" onPress={() => { leave(onLibrary).catch(() => undefined); }} />
      <AppButton title="✨ Criar outra" onPress={() => { leave(onCreateAnother).catch(() => undefined); }} variant="secondary" />
      <AppButton title="🗑️ Excluir" onPress={confirmDelete} loading={loading} disabled={loading} variant="secondary" />
    </Screen>
  );
}

const styles = StyleSheet.create({
  back: { color: theme.colors.primary, fontWeight: '800' },
  eyebrow: { color: theme.colors.secondary, fontWeight: '900', textAlign: 'center' },
  title: { fontSize: 30, fontWeight: '900', color: theme.colors.primary, textAlign: 'center' },
  date: { color: theme.colors.muted, textAlign: 'center' },
  imageHint: { color: theme.colors.muted, fontSize: 12, textAlign: 'center', marginTop: -theme.spacing.sm },
  ready: { color: theme.colors.primary, textAlign: 'center', fontWeight: '900', backgroundColor: theme.colors.surface, padding: theme.spacing.md, borderRadius: theme.radius.md },
  narration: { color: theme.colors.primary, textAlign: 'center', fontWeight: '900' },
  summary: { color: theme.colors.text, fontSize: 17, lineHeight: 24, backgroundColor: theme.colors.surface, padding: theme.spacing.md, borderRadius: theme.radius.md },
  cover: { width: '100%', aspectRatio: 16 / 9, borderRadius: theme.radius.lg, backgroundColor: theme.colors.surface },
  chapter: { backgroundColor: theme.colors.surface, borderRadius: theme.radius.lg, padding: theme.spacing.lg, borderWidth: 1, borderColor: theme.colors.border, gap: theme.spacing.sm },
  chapterNumber: { color: theme.colors.secondary, fontWeight: '900' },
  chapterTitle: { color: theme.colors.primary, fontWeight: '900', fontSize: 20 },
  content: { color: theme.colors.text, fontSize: 17, lineHeight: 26 },
  scene: { width: '100%', aspectRatio: 16 / 9, borderRadius: theme.radius.md, backgroundColor: theme.colors.background },
  retryBox: { gap: theme.spacing.sm, backgroundColor: theme.colors.surface, borderRadius: theme.radius.md, padding: theme.spacing.md, borderWidth: 1, borderColor: theme.colors.border },
  retryText: { color: theme.colors.muted, textAlign: 'center', fontWeight: '700' },
});
