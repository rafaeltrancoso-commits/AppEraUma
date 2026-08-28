import React, { useCallback, useEffect, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { AppButton } from '../components/AppButton';
import { AppTextInput } from '../components/AppTextInput';
import { AuthenticatedStoryImage } from '../components/AuthenticatedStoryImage';
import { Screen } from '../components/Screen';
import { eraumaApi } from '../services/eraumaApi';
import { ChildProfile, Family, Story, StoryGenerationMode, StoryStyle } from '../types/api';
import { theme } from '../theme/tokens';
import { formatLongDatePtBr as formatDate } from '../utils/dateFormat';

const styleLabels: Record<StoryStyle, string> = { ADVENTURE: 'Aventura', FUNNY: 'Engraçada', EDUCATIONAL: 'Educativa', FANTASY: 'Fantasia', BEDTIME: 'Para dormir' };
const styleIcons: Record<StoryStyle, string> = { ADVENTURE: '🗺️', FUNNY: '😂', EDUCATIONAL: '🧠', FANTASY: '✨', BEDTIME: '🌙' };
const dateLabels = { all: 'Todas', today: 'Hoje', seven: 'Últimos 7 dias', thirty: 'Últimos 30 dias', month: 'Este mês' } as const;
type DateFilter = keyof typeof dateLabels;

type Props = { family: Family; childrenProfiles: ChildProfile[]; onBack: () => void; onCreate: () => void; onOpen: (story: Story) => void };

function isoDate(date: Date) { return date.toISOString().slice(0, 10); }
function dateRange(filter: DateFilter) {
  const now = new Date();
  if (filter === 'all') {return {};}
  if (filter === 'today') {return { from: isoDate(now), to: isoDate(now) };}
  if (filter === 'seven') { const start = new Date(now); start.setDate(start.getDate() - 6); return { from: isoDate(start), to: isoDate(now) }; }
  if (filter === 'thirty') { const start = new Date(now); start.setDate(start.getDate() - 29); return { from: isoDate(start), to: isoDate(now) }; }
  return { from: isoDate(new Date(now.getFullYear(), now.getMonth(), 1)), to: isoDate(now) };
}

export function StoryLibraryScreen({ family, childrenProfiles, onBack, onCreate, onOpen }: Props) {
  const [stories, setStories] = useState<Story[]>([]);
  const [page, setPage] = useState(0);
  const [last, setLast] = useState(true);
  const [childId, setChildId] = useState<string | undefined>();
  const [favoriteOnly, setFavoriteOnly] = useState(false);
  const [style, setStyle] = useState<StoryStyle | undefined>();
  const [generationMode, setGenerationMode] = useState<StoryGenerationMode | undefined>();
  const [dateFilter, setDateFilter] = useState<DateFilter>('all');
  const [search, setSearch] = useState('');
  const [showFilters, setShowFilters] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(async (nextPage = 0, append = false) => {
    setLoading(true); setError('');
    try {
      const range = dateRange(dateFilter);
      const response = await eraumaApi.stories(family.id, nextPage, childId, favoriteOnly ? true : undefined, style, generationMode, range.from, range.to);
      setStories(current => append ? [...current, ...response.content] : response.content);
      setPage(response.page); setLast(response.last);
    } catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível carregar sua biblioteca.'); }
    finally { setLoading(false); }
  }, [childId, dateFilter, family.id, favoriteOnly, generationMode, style]);

  useEffect(() => { load(); }, [load]);

  const filteredStories = stories.filter(story => !search.trim() || `${story.title} ${story.summary} ${story.mainCharacterName ?? ''} ${story.theme}`.toLowerCase().includes(search.trim().toLowerCase()));
  const activeChips = [childId ? childrenProfiles.find(child => child.id === childId)?.name : undefined, favoriteOnly ? 'Favoritas' : undefined, style ? styleLabels[style] : undefined, generationMode === 'TEXT_ONLY' ? 'Texto' : generationMode === 'ILLUSTRATED' ? 'Ilustrada' : undefined, dateFilter !== 'all' ? dateLabels[dateFilter] : undefined].filter(Boolean);
  function clearFilters() { setChildId(undefined); setFavoriteOnly(false); setStyle(undefined); setGenerationMode(undefined); setDateFilter('all'); setSearch(''); }

  return (
    <Screen>
      <Pressable onPress={onBack}><Text style={styles.back}>← Home</Text></Pressable>
      <Text style={styles.title}>📚 Biblioteca</Text>
      <View style={styles.searchRow}>
        <View style={styles.searchBox}><AppTextInput label="Buscar" value={search} onChangeText={setSearch} placeholder="Título, tema, personagem..." /></View>
        <Pressable style={styles.filterButton} onPress={() => setShowFilters(value => !value)}><Text style={styles.filterText}>Filtros</Text></Pressable>
      </View>
      {activeChips.length ? <View style={styles.chips}>{activeChips.map(chip => <Text key={chip} style={styles.activeChip}>{chip} ×</Text>)}<Pressable onPress={clearFilters}><Text style={styles.clear}>Limpar filtros</Text></Pressable></View> : null}
      {showFilters ? <View style={styles.filterPanel}>
        <Text style={styles.section}>Criança</Text><View style={styles.chips}><Chip label="Todas" selected={!childId} onPress={() => setChildId(undefined)} />{childrenProfiles.map(child => <Chip key={child.id} label={child.nickname || child.name} selected={childId === child.id} onPress={() => setChildId(child.id)} />)}</View>
        <Text style={styles.section}>Data</Text><View style={styles.chips}>{Object.entries(dateLabels).map(([value, label]) => <Chip key={value} label={label} selected={dateFilter === value} onPress={() => setDateFilter(value as DateFilter)} />)}</View>
        <Text style={styles.section}>Formato</Text><View style={styles.chips}><Chip label="Todas" selected={!generationMode} onPress={() => setGenerationMode(undefined)} /><Chip label="Texto" selected={generationMode === 'TEXT_ONLY'} onPress={() => setGenerationMode('TEXT_ONLY')} /><Chip label="Ilustrada" selected={generationMode === 'ILLUSTRATED'} onPress={() => setGenerationMode('ILLUSTRATED')} /></View>
        <Text style={styles.section}>Tipo</Text><View style={styles.chips}><Chip label="Todos" selected={!style} onPress={() => setStyle(undefined)} />{Object.entries(styleLabels).map(([value, label]) => <Chip key={value} label={label} selected={style === value} onPress={() => setStyle(value as StoryStyle)} />)}</View>
        <Chip label="Somente favoritas" selected={favoriteOnly} onPress={() => setFavoriteOnly(value => !value)} />
      </View> : null}
      {filteredStories.map(story => { const cover = story.images?.find(image => image.type === 'COVER'); return <Pressable key={story.id} style={styles.card} onPress={() => onOpen(story)}>{cover ? <AuthenticatedStoryImage image={cover} style={styles.coverThumb} compact /> : <Text style={styles.icon}>{styleIcons[story.style]}</Text>}<Text style={styles.cardTitle}>{story.title}</Text><Text style={styles.meta}>{story.mainCharacterName || story.child?.name || 'Personagem não informado'}</Text><Text style={styles.meta}>{styleLabels[story.style]} · {formatDate(story.createdAt)}</Text><Text style={styles.heart}>{story.favorite ? '♥' : '♡'}</Text></Pressable>; })}
      {filteredStories.length === 0 && !loading ? <Text style={styles.emptyText}>Nenhuma história encontrada.</Text> : null}
      {error ? <Text style={styles.error}>{error}</Text> : null}
      <AppButton title="✨ Criar História" onPress={onCreate} />
      {!last ? <AppButton title="Carregar mais" onPress={() => load(page + 1, true)} loading={loading} variant="secondary" /> : null}
    </Screen>
  );
}

function Chip({ label, selected, onPress }: { label: string; selected: boolean; onPress: () => void }) { return <Pressable style={[styles.chip, selected && styles.chipSelected]} onPress={onPress}><Text>{label}</Text></Pressable>; }

const styles = StyleSheet.create({
  back: { color: theme.colors.primary, fontWeight: '800' }, title: { fontSize: 30, fontWeight: '900', color: theme.colors.primary, textAlign: 'center' },
  searchRow: { flexDirection: 'row', gap: theme.spacing.sm, alignItems: 'flex-end' }, searchBox: { flex: 1 }, filterButton: { backgroundColor: theme.colors.secondary, padding: theme.spacing.md, borderRadius: theme.radius.md }, filterText: { color: theme.colors.primary, fontWeight: '900' },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: theme.spacing.sm }, chip: { backgroundColor: theme.colors.surface, padding: theme.spacing.sm, borderRadius: theme.radius.md, borderWidth: 1, borderColor: theme.colors.border }, chipSelected: { backgroundColor: theme.colors.secondary }, activeChip: { backgroundColor: theme.colors.secondary, color: theme.colors.primary, padding: theme.spacing.sm, borderRadius: theme.radius.md, fontWeight: '800' }, clear: { color: theme.colors.error, fontWeight: '800', padding: theme.spacing.sm }, filterPanel: { backgroundColor: theme.colors.surface, padding: theme.spacing.md, borderRadius: theme.radius.lg, gap: theme.spacing.sm }, section: { color: theme.colors.primary, fontWeight: '900' },
  card: { backgroundColor: theme.colors.surface, borderRadius: theme.radius.lg, padding: theme.spacing.lg, borderWidth: 1, borderColor: theme.colors.border, gap: theme.spacing.xs }, icon: { fontSize: 34 }, coverThumb: { width: '100%', aspectRatio: 16 / 9, borderRadius: theme.radius.md, backgroundColor: theme.colors.background }, cardTitle: { fontSize: 20, fontWeight: '900', color: theme.colors.primary }, meta: { color: theme.colors.muted }, heart: { position: 'absolute', right: theme.spacing.lg, top: theme.spacing.lg, color: theme.colors.error, fontSize: 24 }, emptyText: { color: theme.colors.muted, textAlign: 'center' }, error: { color: theme.colors.error, textAlign: 'center' },
});
