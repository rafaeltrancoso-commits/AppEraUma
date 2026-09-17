import React, { useEffect, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { AppBackButton } from '../components/AppBackButton';
import { AppButton } from '../components/AppButton';
import { AppTextInput } from '../components/AppTextInput';
import { Screen } from '../components/Screen';
import { ApiError } from '../services/api';
import { eraumaApi } from '../services/eraumaApi';
import { ChildProfile, Family, Moment, Story, StoryLength, StoryStyle } from '../types/api';
import { theme } from '../theme/tokens';

const MAX_OTHER_CHARACTERS_LENGTH = 500;

const storyStyles: { value: StoryStyle; label: string }[] = [
  { value: 'ADVENTURE', label: '🗺️ Aventura' },
  { value: 'FUNNY', label: '😂 Engraçada' },
  { value: 'EDUCATIONAL', label: '🧠 Educativa' },
  { value: 'FANTASY', label: '✨ Fantasia' },
  { value: 'BEDTIME', label: '🌙 Para dormir' },
];

const storyLengths: { value: StoryLength; label: string; hint: string }[] = [
  { value: 'SHORT', label: 'Curta', hint: 'leitura rápida' },
  { value: 'MEDIUM', label: 'Média', hint: 'alguns minutos' },
  { value: 'LONG', label: 'Longa', hint: 'história maior' },
];

type Props = {
  family: Family;
  childrenProfiles: ChildProfile[];
  sourceMoment?: Moment;
  initialCharacterIds?: string[];
  onCharacterOrderChange?: (ids: string[]) => void;
  onCancel: () => void;
  onCreated: (story: Story) => void;
};

function firstName(name?: string) {
  return name?.trim().split(/\s+/)[0] ?? '';
}

export function CreateStoryScreen({ family, childrenProfiles, sourceMoment, initialCharacterIds = [], onCharacterOrderChange, onCancel, onCreated }: Props) {
  const initialChildId = useMemo(() => sourceMoment?.children.length === 1 ? sourceMoment.children[0].id : childrenProfiles[0]?.id, [childrenProfiles, sourceMoment]);
  const [selectedCharacterIds, setSelectedCharacterIds] = useState<string[]>(() => {
    const availableIds = new Set(childrenProfiles.map(character => character.id));
    const restored = initialCharacterIds.filter(id => availableIds.has(id)).slice(0, 3);
    if (!sourceMoment && restored.length > 0) {
      return restored;
    }
    return initialChildId ? [initialChildId] : [];
  });
  const [otherCharacters, setOtherCharacters] = useState('');
  const [idempotencyKey] = useState(() => Date.now() + '-' + Math.random().toString(36).slice(2));
  const [themeValue, setThemeValue] = useState(sourceMoment ? `${sourceMoment.title}${sourceMoment.description ? ` — ${sourceMoment.description}` : ''}` : '');
  const [place, setPlace] = useState(sourceMoment?.locationName ?? '');
  const [favoriteAnimal, setFavoriteAnimal] = useState('');
  const [style, setStyle] = useState<StoryStyle>('ADVENTURE');
  const [length, setLength] = useState<StoryLength>('MEDIUM');
  const [generationMode, setGenerationMode] = useState<'TEXT_ONLY' | 'ILLUSTRATED'>('TEXT_ONLY');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    onCharacterOrderChange?.(selectedCharacterIds);
  }, [onCharacterOrderChange, selectedCharacterIds]);

  function toggleCharacter(characterId: string) {
    if (loading) {
      return;
    }
    setError('');
    setSelectedCharacterIds(current => {
      if (current.includes(characterId)) {
        return current.filter(id => id !== characterId);
      }
      if (current.length >= 3) {
        setError('Você já escolheu três personagens. Remova um para trocar.');
        return current;
      }
      return [...current, characterId];
    });
  }

  function moveCharacter(index: number, direction: -1 | 1) {
    setSelectedCharacterIds(current => {
      const destination = index + direction;
      if (destination < 0 || destination >= current.length) {
        return current;
      }
      const next = [...current];
      [next[index], next[destination]] = [next[destination], next[index]];
      return next;
    });
  }

  async function submit() {
    if (loading) {
      return;
    }
    setError('');
    if (selectedCharacterIds.length === 0) {
      setError('Escolha ao menos um personagem.');
      return;
    }
    const normalizedOthers = otherCharacters.trim().replace(/\s+/g, ' ');
    if (normalizedOthers.length > MAX_OTHER_CHARACTERS_LENGTH) {
      setError('Outros personagens deve ter no máximo 500 caracteres.');
      return;
    }
    if (!themeValue.trim()) {
      setError('Conte sobre o que será a história.');
      return;
    }
    setLoading(true);
    try {
      if (__DEV__) {
        console.info('story_create_request', { generationMode });
      }
      const story = await eraumaApi.generateStory(family.id, {
        childId: selectedCharacterIds[0],
        characterIds: selectedCharacterIds,
        sourceMomentId: sourceMoment?.id,
        otherCharacters: normalizedOthers || undefined,
        theme: themeValue.trim(),
        place: place.trim() || undefined,
        favoriteAnimal: favoriteAnimal.trim() || undefined,
        style,
        length,
        generationMode,
        idempotencyKey,
      });
      if (__DEV__) {
        console.info('story_created_debug', {
          storyId: story.id,
          requestedGenerationMode: generationMode,
          generationType: story.generationType,
          imageCount: story.images.length,
          imageModels: story.images.map(image => image.model),
          imageStatuses: story.images.map(image => image.status),
        });
      }
      onCreated(story);
    } catch (exception) {
      if (exception instanceof ApiError && exception.status === 0 && exception.message.includes('Tempo esgotado')) {
        setError('A solicitação pode continuar sendo processada. Confira a biblioteca; tentar novamente reutiliza a mesma solicitação com segurança.');
        return;
      }
      setError(exception instanceof Error ? exception.message : 'Não conseguimos criar a história agora. Tente novamente em alguns instantes.');
    } finally {
      setLoading(false);
    }
  }

  const selectedCharacters = selectedCharacterIds.map(id => childrenProfiles.find(child => child.id === id)).filter((child): child is ChildProfile => Boolean(child));
  const loadingCharacter = selectedCharacters[0]?.nickname || firstName(selectedCharacters[0]?.name) || 'seu personagem';

  return (
    <Screen>
      <AppBackButton onPress={onCancel} />
      <Text style={styles.eyebrow}>✨ Criar História</Text>
      <Text style={styles.title}>Uma aventura feita para a sua família</Text>
      {sourceMoment ? <Text style={styles.source}>A partir do momento: {sourceMoment.title}</Text> : null}

      <Text style={styles.section}>Personagens</Text>
      <Text style={styles.hint}>Escolha de um a três personagens. O primeiro será o protagonista; use as setas para mudar a ordem.</Text>
      <View style={styles.chips}>
        {childrenProfiles.map(child => (
          <Pressable key={child.id} style={[styles.chip, selectedCharacterIds.includes(child.id) && styles.chipSelected]} onPress={() => toggleCharacter(child.id)} disabled={loading}>
            <Text style={styles.chipText}>{child.nickname || child.name} {selectedCharacterIds.includes(child.id) ? '✓' : ''}</Text>
          </Pressable>
        ))}
      </View>
      {selectedCharacters.map((character, index) => (
        <View key={character.id} style={styles.selectedCharacter}>
          <View style={styles.selectedCharacterText}>
            <Text style={styles.lengthTitle}>{index + 1}. {character.nickname || character.name}</Text>
            <Text style={styles.lengthHint}>{index === 0 ? 'Protagonista' : 'Personagem secundário'}</Text>
          </View>
          <Pressable onPress={() => moveCharacter(index, -1)} disabled={index === 0 || loading}><Text style={styles.orderAction}>↑</Text></Pressable>
          <Pressable onPress={() => moveCharacter(index, 1)} disabled={index === selectedCharacters.length - 1 || loading}><Text style={styles.orderAction}>↓</Text></Pressable>
          <Pressable onPress={() => toggleCharacter(character.id)} disabled={loading}><Text style={styles.removeAction}>Remover</Text></Pressable>
        </View>
      ))}
      <AppTextInput label="Outros personagens (opcional)" value={otherCharacters} onChangeText={setOtherCharacters} placeholder="Vovó Ana, Bolota..." />

      <AppTextInput label="Sobre o que será a história? *" value={themeValue} onChangeText={setThemeValue} multiline placeholder="Medo do escuro, uma viagem ao espaço..." />
      <AppTextInput label="Onde acontece?" value={place} onChangeText={setPlace} placeholder="Floresta, praia, castelo..." />
      <AppTextInput label="Animal da história (opcional)" value={favoriteAnimal} onChangeText={setFavoriteAnimal} placeholder="Dinossauro, cachorro, gato, unicórnio..." />

      <Text style={styles.section}>Que tipo de história?</Text>
      <View style={styles.chips}>
        {storyStyles.map(item => (
          <Pressable key={item.value} style={[styles.chip, style === item.value && styles.chipSelected]} onPress={() => setStyle(item.value)} disabled={loading}>
            <Text style={styles.chipText}>{item.label}</Text>
          </Pressable>
        ))}
      </View>
      <Text style={styles.section}>Qual o tamanho?</Text>
      {storyLengths.map(item => (
        <Pressable key={item.value} style={[styles.lengthCard, length === item.value && styles.chipSelected]} onPress={() => setLength(item.value)} disabled={loading}>
          <Text style={styles.lengthTitle}>{item.label}</Text>
          <Text style={styles.lengthHint}>{item.hint}</Text>
        </Pressable>
      ))}
      <Text style={styles.section}>Ilustrações</Text>
      <Pressable style={[styles.lengthCard, generationMode === 'TEXT_ONLY' && styles.chipSelected]} onPress={() => setGenerationMode('TEXT_ONLY')} disabled={loading}>
        <Text style={styles.lengthTitle}>Somente história</Text>
        <Text style={styles.lengthHint}>Mais rápida</Text>
      </Pressable>
      <Pressable style={[styles.lengthCard, generationMode === 'ILLUSTRATED' && styles.chipSelected]} onPress={() => setGenerationMode('ILLUSTRATED')} disabled={loading}>
        <Text style={styles.lengthTitle}>História ilustrada</Text>
        <Text style={styles.lengthHint}>Inclui capa e ilustrações</Text>
      </Pressable>
      {loading ? <Text style={styles.loading}>{`✨ Preparando sua história para ${loadingCharacter}...\nEla continuará sendo preparada mesmo se você sair desta tela.`}</Text> : null}
      {error ? <Text style={styles.error}>{error}</Text> : null}
      {error ? <AppButton title="Tentar novamente" onPress={submit} variant="secondary" disabled={loading} /> : null}
      <AppButton title="✨ Criar minha história" onPress={submit} loading={loading} disabled={loading} />
      <AppButton title="Cancelar" onPress={onCancel} variant="secondary" disabled={loading} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  eyebrow: { color: theme.colors.secondary, fontWeight: '900', textAlign: 'center' },
  title: { fontSize: 28, fontWeight: '900', color: theme.colors.primary, textAlign: 'center' },
  source: { color: theme.colors.muted, textAlign: 'center', backgroundColor: theme.colors.surface, padding: theme.spacing.md, borderRadius: theme.radius.md },
  section: { color: theme.colors.primary, fontWeight: '900', fontSize: 17, marginTop: theme.spacing.sm },
  hint: { color: theme.colors.muted, fontSize: 14 },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: theme.spacing.sm },
  chip: { backgroundColor: theme.colors.surface, borderColor: theme.colors.border, borderWidth: 1, borderRadius: theme.radius.md, padding: theme.spacing.md },
  chipSelected: { backgroundColor: theme.colors.secondary },
  chipText: { color: theme.colors.primary, fontWeight: '800' },
  lengthCard: { backgroundColor: theme.colors.surface, borderColor: theme.colors.border, borderWidth: 1, borderRadius: theme.radius.md, padding: theme.spacing.md },
  lengthTitle: { color: theme.colors.primary, fontWeight: '900', fontSize: 16 },
  lengthHint: { color: theme.colors.muted },
  loading: { color: theme.colors.primary, textAlign: 'center', fontWeight: '800' },
  error: { color: theme.colors.error, textAlign: 'center' },
  selectedCharacter: { flexDirection: 'row', alignItems: 'center', gap: theme.spacing.sm, backgroundColor: theme.colors.surface, borderRadius: theme.radius.md, padding: theme.spacing.md },
  selectedCharacterText: { flex: 1 },
  orderAction: { color: theme.colors.primary, fontSize: 22, fontWeight: '900', padding: 4 },
  removeAction: { color: theme.colors.error, fontWeight: '800' },
});
