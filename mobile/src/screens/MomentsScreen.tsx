import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { AppButton } from '../components/AppButton';
import { Screen } from '../components/Screen';
import { eraumaApi } from '../services/eraumaApi';
import { ChildProfile, Family, Moment, MomentCalendarDay } from '../types/api';
import { theme } from '../theme/tokens';
import { dateOnlyFromLocalDate, formatDateOnlyPtBr, formatLongDatePtBr } from '../utils/dateFormat';

type Props = { family: Family; childrenProfiles: ChildProfile[]; onBack: () => void; onCreate: () => void; onOpen: (moment: Moment) => void };
type ViewMode = 'timeline' | 'calendar';

export function MomentsScreen({ family, childrenProfiles, onBack, onCreate, onOpen }: Props) {
  const [moments, setMoments] = useState<Moment[]>([]);
  const [calendarDays, setCalendarDays] = useState<MomentCalendarDay[]>([]);
  const [page, setPage] = useState(0);
  const [last, setLast] = useState(true);
  const [childId, setChildId] = useState<string | undefined>();
  const [mode, setMode] = useState<ViewMode>('timeline');
  const [visibleMonth, setVisibleMonth] = useState(new Date());
  const [selectedDate, setSelectedDate] = useState<string>();
  const [selectedMoments, setSelectedMoments] = useState<Moment[]>([]);
  const [selectedLoading, setSelectedLoading] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(async (nextPage = 0, append = false) => {
    setLoading(true); setError('');
    try { const response = await eraumaApi.moments(family.id, nextPage, childId); setMoments(current => append ? [...current, ...response.content] : response.content); setPage(response.page); setLast(response.last); }
    catch (exception) { setError(exception instanceof Error ? exception.message : 'Não foi possível carregar seus momentos.'); }
    finally { setLoading(false); }
  }, [childId, family.id]);

  const loadCalendar = useCallback(async () => {
    try { setCalendarDays(await eraumaApi.momentCalendar(family.id, visibleMonth.getFullYear(), visibleMonth.getMonth() + 1, childId)); }
    catch { setCalendarDays([]); }
  }, [childId, family.id, visibleMonth]);

  const loadSelectedDay = useCallback(async (date: string) => {
    setSelectedLoading(true);
    setError('');
    try {
      const response = await eraumaApi.moments(family.id, 0, childId, date, date);
      setSelectedMoments(response.content);
    } catch (exception) {
      setSelectedMoments([]);
      setError(exception instanceof Error ? exception.message : 'Não foi possível carregar os momentos deste dia.');
    } finally {
      setSelectedLoading(false);
    }
  }, [childId, family.id]);

  useEffect(() => { load(); }, [load]);
  useEffect(() => { loadCalendar(); }, [loadCalendar]);
  useEffect(() => {
    if (selectedDate) {
      loadSelectedDay(selectedDate).catch(() => undefined);
    } else {
      setSelectedMoments([]);
    }
  }, [loadSelectedDay, selectedDate]);

  const momentsByMonth = useMemo(() => groupByMonth(moments), [moments]);

  function filterByChild(id?: string) { setChildId(id); setSelectedDate(undefined); setSelectedMoments([]); }
  function shiftMonth(delta: number) { setVisibleMonth(current => new Date(current.getFullYear(), current.getMonth() + delta, 1)); setSelectedDate(undefined); setSelectedMoments([]); }

  return (
    <Screen>
      <Pressable onPress={onBack}><Text style={styles.back}>← Home</Text></Pressable>
      <Text style={styles.title}>❤ Momentos</Text>
      <AppButton title="Novo momento" onPress={onCreate} />
      <Text style={styles.subtitle}>Memória visual da infância da sua família.</Text>
      <View style={styles.tabs}><Tab label="Linha do tempo" selected={mode === 'timeline'} onPress={() => setMode('timeline')} /><Tab label="Calendário" selected={mode === 'calendar'} onPress={() => setMode('calendar')} /></View>
      <View style={styles.chips}><Chip label="Todas" selected={!childId} onPress={() => filterByChild(undefined)} />{childrenProfiles.map(child => <Chip key={child.id} label={child.nickname || child.name} selected={childId === child.id} onPress={() => filterByChild(child.id)} />)}</View>
      {mode === 'timeline' ? <Timeline groups={momentsByMonth} onOpen={onOpen} /> : <CalendarMonth month={visibleMonth} days={calendarDays} selectedDate={selectedDate} onPrevious={() => shiftMonth(-1)} onNext={() => shiftMonth(1)} onSelect={setSelectedDate} moments={selectedMoments} loading={selectedLoading} onOpen={onOpen} />}
      {moments.length === 0 && !loading ? <View style={styles.empty}><Text style={styles.emptyTitle}>✨ Sua história começa aqui</Text><Text style={styles.emptyText}>Guarde aqueles pequenos momentos que você nunca quer esquecer.</Text></View> : null}
      {error ? <Text style={styles.error}>{error}</Text> : null}
      {mode === 'timeline' && !last ? <AppButton title="Carregar mais" onPress={() => load(page + 1, true)} loading={loading} variant="secondary" /> : null}
    </Screen>
  );
}

function Timeline({ groups, onOpen }: { groups: [string, Moment[]][]; onOpen: (moment: Moment) => void }) { return <>{groups.map(([month, items]) => <View key={month} style={styles.group}><Text style={styles.groupTitle}>{month}</Text>{items.map(moment => <MomentCard key={moment.id} moment={moment} onOpen={onOpen} />)}</View>)}</>; }
function MomentCard({ moment, onOpen }: { moment: Moment; onOpen: (moment: Moment) => void }) { return <Pressable style={styles.card} onPress={() => onOpen(moment)}><Text style={styles.day}>{formatDateOnlyPtBr(moment.occurredAt)}</Text><Text style={styles.cardTitle}>● {moment.title}</Text><Text style={styles.meta}>{moment.children.map(child => child.nickname || child.name).join(', ') || 'Toda a família'}</Text>{moment.description ? <Text style={styles.description} numberOfLines={2}>{moment.description}</Text> : null}<Text style={styles.meta}>{moment.stories?.length ? '📖 História relacionada' : '📖 Sem história relacionada'} · 📷 {moment.photos.length} registros</Text></Pressable>; }
function CalendarMonth({ month, days, selectedDate, onPrevious, onNext, onSelect, moments, loading, onOpen }: { month: Date; days: MomentCalendarDay[]; selectedDate?: string; onPrevious: () => void; onNext: () => void; onSelect: (date: string) => void; moments: Moment[]; loading: boolean; onOpen: (moment: Moment) => void }) { const counts = new Map(days.map(day => [day.date, day.count])); const cells = calendarCells(month); return <View style={styles.calendar}><View style={styles.monthNav}><Pressable onPress={onPrevious}><Text style={styles.nav}>‹</Text></Pressable><Text style={styles.monthTitle}>{new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric' }).format(month)}</Text><Pressable onPress={onNext}><Text style={styles.nav}>›</Text></Pressable></View><View style={styles.week}>{['D','S','T','Q','Q','S','S'].map(day => <Text key={day} style={styles.weekDay}>{day}</Text>)}</View><View style={styles.grid}>{cells.map((date, index) => { const key = date ? dateOnlyFromLocalDate(date) : `empty-${index}`; const count = date ? counts.get(key) : 0; return <Pressable key={key} disabled={!date || !count} style={[styles.dateCell, selectedDate === key && styles.dateSelected]} onPress={() => date && onSelect(key)}><Text style={styles.dateText}>{date?.getDate() ?? ''}</Text>{count ? <Text style={styles.dot}>{count > 1 ? count : '●'}</Text> : null}</Pressable>; })}</View>{selectedDate ? <View style={styles.dayPanel}><Text style={styles.groupTitle}>{formatDate(selectedDate)}</Text>{loading ? <Text style={styles.meta}>Carregando momentos...</Text> : null}{!loading && moments.map(moment => <MomentCard key={moment.id} moment={moment} onOpen={onOpen} />)}{!loading && moments.length === 0 ? <Text style={styles.meta}>Nenhum momento registrado neste dia.</Text> : null}</View> : null}</View>; }
function Tab({ label, selected, onPress }: { label: string; selected: boolean; onPress: () => void }) { return <Pressable style={[styles.tab, selected && styles.tabSelected]} onPress={onPress}><Text style={styles.tabText}>{label}</Text></Pressable>; }
function Chip({ label, selected, onPress }: { label: string; selected: boolean; onPress: () => void }) { return <Pressable style={[styles.chip, selected && styles.chipSelected]} onPress={onPress}><Text>{label}</Text></Pressable>; }
function calendarCells(month: Date) { const first = new Date(month.getFullYear(), month.getMonth(), 1); const cells: (Date | null)[] = Array(first.getDay()).fill(null); for (let day = 1; day <= new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate(); day++) {cells.push(new Date(month.getFullYear(), month.getMonth(), day));} return cells; }
function groupByMonth(moments: Moment[]): [string, Moment[]][] { const groups = new Map<string, Moment[]>(); moments.forEach(moment => { const label = formatMonthLabel(moment.occurredAt); groups.set(label, [...(groups.get(label) ?? []), moment]); }); return Array.from(groups.entries()); }
function formatMonthLabel(value: string) { const dateOnly = value.slice(0, 10); const [year, month] = dateOnly.split('-').map(Number); return new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric' }).format(new Date(year, month - 1, 1)).toUpperCase(); }
export function formatDate(value: string) { return formatLongDatePtBr(value); }

const styles = StyleSheet.create({
  back: { color: theme.colors.primary, fontWeight: '800' }, title: { fontSize: 30, fontWeight: '900', color: theme.colors.primary, textAlign: 'center' }, subtitle: { color: theme.colors.muted, textAlign: 'center', fontSize: 16 }, tabs: { flexDirection: 'row', backgroundColor: theme.colors.surface, borderRadius: theme.radius.md, padding: theme.spacing.xs }, tab: { flex: 1, padding: theme.spacing.md, alignItems: 'center', borderRadius: theme.radius.md }, tabSelected: { backgroundColor: theme.colors.secondary }, tabText: { color: theme.colors.primary, fontWeight: '900' }, chips: { flexDirection: 'row', flexWrap: 'wrap', gap: theme.spacing.sm }, chip: { backgroundColor: theme.colors.surface, padding: theme.spacing.sm, borderRadius: theme.radius.md, borderWidth: 1, borderColor: theme.colors.border }, chipSelected: { backgroundColor: theme.colors.secondary }, group: { gap: theme.spacing.sm }, groupTitle: { color: theme.colors.primary, fontWeight: '900', fontSize: 18 }, card: { backgroundColor: theme.colors.surface, borderRadius: theme.radius.lg, padding: theme.spacing.lg, borderWidth: 1, borderColor: theme.colors.border, gap: theme.spacing.xs }, day: { color: theme.colors.secondary, fontWeight: '900' }, cardTitle: { fontSize: 19, fontWeight: '900', color: theme.colors.primary }, meta: { color: theme.colors.muted }, description: { color: theme.colors.text }, calendar: { gap: theme.spacing.md }, monthNav: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }, monthTitle: { color: theme.colors.primary, fontSize: 20, fontWeight: '900', textTransform: 'capitalize' }, nav: { color: theme.colors.primary, fontSize: 34, fontWeight: '900', paddingHorizontal: theme.spacing.lg }, week: { flexDirection: 'row' }, weekDay: { flex: 1, textAlign: 'center', color: theme.colors.muted, fontWeight: '900' }, grid: { flexDirection: 'row', flexWrap: 'wrap' }, dateCell: { width: '14.28%', minHeight: 52, alignItems: 'center', justifyContent: 'center', borderRadius: theme.radius.md }, dateSelected: { backgroundColor: theme.colors.secondary }, dateText: { color: theme.colors.text, fontWeight: '700' }, dot: { color: theme.colors.primary, fontWeight: '900' }, dayPanel: { gap: theme.spacing.sm }, empty: { backgroundColor: theme.colors.surface, borderRadius: theme.radius.lg, padding: theme.spacing.lg, gap: theme.spacing.md, alignItems: 'center' }, emptyTitle: { fontSize: 22, fontWeight: '900', color: theme.colors.primary, textAlign: 'center' }, emptyText: { color: theme.colors.muted, textAlign: 'center', fontSize: 16 }, error: { color: theme.colors.error, textAlign: 'center' },
});
