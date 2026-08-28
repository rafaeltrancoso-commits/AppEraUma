import React, { useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { AppButton } from '../components/AppButton';
import { AppTextInput } from '../components/AppTextInput';
import { Screen } from '../components/Screen';
import { ApiError } from '../services/api';
import { eraumaApi } from '../services/eraumaApi';
import { ChildProfile, Family, Moment, Story, StoryLength, StoryStyle } from '../types/api';
import { theme } from '../theme/tokens';

const MAX_CHARACTER_NAME_LENGTH = 120;

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
  onCancel: () => void;
  onCreated: (story: Story) => void;
};

function firstName(name?: string) {
  return name?.trim().split(/\s+/)[0] ?? '';
}

export function CreateStoryScreen({ family, childrenProfiles, sourceMoment, onCancel, onCreated }: Props) {
  const initialChildId = useMemo(() => sourceMoment?.children.length === 1 ? sourceMoment.children[0].id : childrenProfiles[0]?.id, [childrenProfiles, sourceMoment]);
  const [childId, setChildId] = useState(initialChildId ?? '');
  const selectedChild = childrenProfiles.find(child => child.id === childId);
  const [mainCharacterName, setMainCharacterName] = useState(firstName(selectedChild?.name));
  const [secondCharacterName, setSecondCharacterName] = useState('');
  const [mainCharacterEdited, setMainCharacterEdited] = useState(false);
  const [themeValue, setThemeValue] = useState(sourceMoment ? `${sourceMoment.title}${sourceMoment.description ? ` — ${sourceMoment.description}` : ''}` : '');
  const [place, setPlace] = useState(sourceMoment?.locationName ?? '');
  const [favoriteAnimal, setFavoriteAnimal] = useState('');
  const [style, setStyle] = useState<StoryStyle>('ADVENTURE');
  const [length, setLength] = useState<StoryLength>('MEDIUM');
  const [generationMode, setGenerationMode] = useState<'TEXT_ONLY' | 'ILLUSTRATED'>('TEXT_ONLY');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  function selectChild(nextChildId: string) {
    setChildId(nextChildId);
    if (!mainCharacterEdited) {
      const child = childrenProfiles.find(item => item.id === nextChildId);
      setMainCharacterName(firstName(child?.name));
    }
  }

  function clearChild() {
    setChildId('');
  }

  function changeMainCharacterName(value: string) {
    setMainCharacterEdited(true);
    setMainCharacterName(value);
  }

  async function submit() {
    if (loading) {
      return;
    }
    setError('');
    const resolvedMainCharacterName = mainCharacterName.trim() || firstName(selectedChild?.name);
    const resolvedSecondCharacterName = secondCharacterName.trim();
    if (!resolvedMainCharacterName) {
      setError('Informe o personagem principal ou escolha uma criança.');
      return;
    }
    if (resolvedMainCharacterName.length > MAX_CHARACTER_NAME_LENGTH || resolvedSecondCharacterName.length > MAX_CHARACTER_NAME_LENGTH) {
      setError('Nome do personagem deve ter no máximo 120 caracteres.');
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
        childId: childId || undefined,
        sourceMomentId: sourceMoment?.id,
        mainCharacterName: resolvedMainCharacterName,
        secondCharacterName: resolvedSecondCharacterName || undefined,
        theme: themeValue.trim(),
        place: place.trim() || undefined,
        favoriteAnimal: favoriteAnimal.trim() || undefined,
        style,
        length,
        generationMode,
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
        setError('A história está demorando mais que o esperado. Tente novamente.');
        return;
      }
      setError(exception instanceof Error ? exception.message : 'Não conseguimos criar a história agora. Tente novamente em alguns instantes.');
    } finally {
      setLoading(false);
    }
  }

  const loadingCharacter = mainCharacterName.trim() || selectedChild?.nickname || selectedChild?.name || 'seu personagem';

  return (
    <Screen>
      <Pressable onPress={onCancel}><Text style={styles.back}>← Voltar</Text></Pressable>
      <Text style={styles.eyebrow}>✨ Criar História</Text>
      <Text style={styles.title}>Uma aventura feita para a sua família</Text>
      {sourceMoment ? <Text style={styles.source}>A partir do momento: {sourceMoment.title}</Text> : null}

      <Text style={styles.section}>Personagens</Text>
      <Text style={styles.hint}>Escolha uma criança para personalizar a história ou informe um protagonista manualmente.</Text>
      <View style={styles.chips}>
        <Pressable style={[styles.chip, !childId && styles.chipSelected]} onPress={clearChild} disabled={loading}>
          <Text style={styles.chipText}>Sem criança {!childId ? '✓' : ''}</Text>
        </Pressable>
        {childrenProfiles.map(child => (
          <Pressable key={child.id} style={[styles.chip, childId === child.id && styles.chipSelected]} onPress={() => selectChild(child.id)} disabled={loading}>
            <Text style={styles.chipText}>{child.nickname || child.name} {childId === child.id ? '✓' : ''}</Text>
          </Pressable>
        ))}
      </View>
      <AppTextInput label="Personagem principal" value={mainCharacterName} onChangeText={changeMainCharacterName} placeholder="Nando, Super Nando, Capitão Theo..." />
      <AppTextInput label="Segundo personagem (opcional)" value={secondCharacterName} onChangeText={setSecondCharacterName} placeholder="Luna, Bolota, Vovó Ana..." />

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
      {loading ? <Text style={styles.loading}>{generationMode === 'ILLUSTRATED' ? '🎨 Preparando as ilustrações...' : `✨ Criando sua história para ${loadingCharacter}...`}</Text> : null}
      {error ? <Text style={styles.error}>{error}</Text> : null}
      {error ? <AppButton title="Tentar novamente" onPress={submit} variant="secondary" disabled={loading} /> : null}
      <AppButton title="✨ Criar minha história" onPress={submit} loading={loading} disabled={loading} />
      <AppButton title="Cancelar" onPress={onCancel} variant="secondary" disabled={loading} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  back: { color: theme.colors.primary, fontWeight: '800' },
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
});
