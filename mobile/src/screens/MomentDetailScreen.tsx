import React, { useEffect, useState } from 'react';
import { Alert, Pressable, StyleSheet, Text, View } from 'react-native';
import { AppButton } from '../components/AppButton';
import { AuthenticatedMomentPhoto } from '../components/AuthenticatedMomentPhoto';
import { AuthenticatedStoryImage } from '../components/AuthenticatedStoryImage';
import { Screen } from '../components/Screen';
import { eraumaApi } from '../services/eraumaApi';
import { Moment, Story } from '../types/api';
import { theme } from '../theme/tokens';
import { formatLongDatePtBr as formatDate } from '../utils/dateFormat';

type Props = {
  moment: Moment;
  onBack: () => void;
  onEdit: (moment: Moment) => void;
  onCreateStory: (moment: Moment) => void;
  onOpenStory: (story: Story) => void;
  onChanged: (moment?: Moment) => void;
};

export function MomentDetailScreen({ moment: initialMoment, onBack, onEdit, onCreateStory, onOpenStory, onChanged }: Props) {
  const [moment, setMoment] = useState(initialMoment);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    eraumaApi.moment(initialMoment.id).then(setMoment).catch(() => undefined);
  }, [initialMoment.id]);

  async function favorite() {
    const previous = moment;
    const optimistic = { ...moment, favorite: !moment.favorite };
    setMoment(optimistic);
    try {
      const updated = await eraumaApi.favoriteMoment(moment.id, optimistic.favorite);
      setMoment(updated);
      onChanged(updated);
    } catch {
      setMoment(previous);
      Alert.alert('Não foi possível atualizar', 'Tente novamente em alguns instantes.');
    }
  }

  function confirmDelete() {
    Alert.alert('Excluir este momento?', 'Ele deixará de aparecer na sua linha do tempo.', [
      { text: 'Cancelar', style: 'cancel' },
      { text: 'Excluir', style: 'destructive', onPress: deleteMoment },
    ]);
  }

  async function deleteMoment() {
    setLoading(true);
    try {
      await eraumaApi.deleteMoment(moment.id);
      onChanged(undefined);
    } finally {
      setLoading(false);
    }
  }

  return (
    <Screen>
      <Pressable onPress={onBack}><Text style={styles.back}>← Momentos</Text></Pressable>
      <View style={styles.hero}><Text style={styles.heroIcon}>{moment.photos.length > 0 ? '📸' : '🌟'}</Text></View>
      <Text style={styles.title}>{moment.title}</Text>
      <Text style={styles.date}>{formatDate(moment.occurredAt)}</Text>
      {moment.locationName ? <Text style={styles.meta}>📍 {moment.locationName}</Text> : null}
      {moment.description ? <Text style={styles.description}>{moment.description}</Text> : null}
      {moment.photos.length > 0 ? <Text style={styles.section}>Fotos</Text> : null}
      {moment.photos.length > 0 ? (
        <View style={styles.photosGrid}>
          {moment.photos.map(photo => <AuthenticatedMomentPhoto key={photo.id} photo={photo} style={styles.photo} />)}
        </View>
      ) : null}
      <Text style={styles.section}>Crianças</Text>
      <Text style={styles.meta}>{moment.children.map(child => child.nickname || child.name).join(', ') || 'Toda a família'}</Text>
      <Text style={styles.section}>Participantes</Text>
      <Text style={styles.meta}>{moment.participants.map(participant => participant.name).join(', ') || 'Não informado'}</Text>
      {moment.stories?.length ? <Text style={styles.section}>Histórias relacionadas</Text> : null}
      {moment.stories?.map(story => {
        const cover = story.images?.find(image => image.type === 'COVER');
        return (
          <View key={story.id} style={styles.storyCard}>
            <AuthenticatedStoryImage image={cover} style={styles.cover} compact />
            <Text style={styles.storyTitle}>{story.title}</Text>
            <Text style={styles.meta}>{story.mainCharacterName || 'Personagem'} · {story.theme}</Text>
            <Text style={styles.meta}>{formatDate(story.createdAt)}</Text>
            <AppButton title="Ler história" onPress={() => onOpenStory(story as Story)} variant="secondary" />
            <AppButton title="Ouvir história" onPress={() => onOpenStory(story as Story)} variant="secondary" />
          </View>
        );
      })}
      <AppButton title="✨ Transformar em história" onPress={() => onCreateStory(moment)} />
      <AppButton title={moment.favorite ? '♥ Favorito' : '♡ Favoritar'} onPress={favorite} variant="secondary" />
      <AppButton title="✏️ Editar" onPress={() => onEdit(moment)} />
      <AppButton title="🗑️ Excluir" onPress={confirmDelete} loading={loading} variant="secondary" />
    </Screen>
  );
}

const styles = StyleSheet.create({
  back: { color: theme.colors.primary, fontWeight: '800' },
  hero: { minHeight: 150, borderRadius: theme.radius.lg, backgroundColor: theme.colors.surface, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: theme.colors.border },
  heroIcon: { fontSize: 54 },
  title: { fontSize: 30, fontWeight: '900', color: theme.colors.primary, textAlign: 'center' },
  date: { color: theme.colors.secondary, fontWeight: '900', textAlign: 'center' },
  meta: { color: theme.colors.muted, textAlign: 'center', fontSize: 16 },
  description: { color: theme.colors.text, fontSize: 17, lineHeight: 24, backgroundColor: theme.colors.surface, padding: theme.spacing.md, borderRadius: theme.radius.md },
  section: { color: theme.colors.primary, fontWeight: '900', textAlign: 'center', marginTop: theme.spacing.sm },
  photosGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: theme.spacing.sm },
  photo: { width: '48%', aspectRatio: 1, borderRadius: theme.radius.md, backgroundColor: theme.colors.surface },
  storyCard: { backgroundColor: theme.colors.surface, borderColor: theme.colors.border, borderWidth: 1, borderRadius: theme.radius.lg, padding: theme.spacing.md, gap: theme.spacing.sm },
  storyTitle: { color: theme.colors.primary, fontWeight: '900', fontSize: 18, textAlign: 'center' },
  cover: { width: '100%', aspectRatio: 16 / 9, borderRadius: theme.radius.md, backgroundColor: theme.colors.background },
});
