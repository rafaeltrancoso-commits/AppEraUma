import { Ionicons } from '@expo/vector-icons';
import React, { ComponentProps } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Screen } from '../components/Screen';
import { features } from '../config/features';
import { useAuth } from '../contexts/AuthContext';
import { ChildProfile } from '../types/api';
import { theme } from '../theme/tokens';

type Props = {
  childrenProfiles: ChildProfile[];
  onMoments?: () => void;
  onCreateStory: () => void;
  onLibrary: () => void;
  onChildren: () => void;
};

type IconName = ComponentProps<typeof Ionicons>['name'];

export function HomeScreen({ childrenProfiles, onMoments, onCreateStory, onLibrary, onChildren }: Props) {
  const { user } = useAuth();
  const momentsEnabled = features.moments && Boolean(onMoments);

  return (
    <Screen>
      <Text style={styles.greeting}>Olá, {user?.name} 👋</Text>
      <Text style={styles.title}>Quem vai viver uma aventura hoje?</Text>
      <View style={styles.childList}>
        {childrenProfiles.map(child => <Text key={child.id} style={styles.childCard}>{child.nickname || child.name}</Text>)}
      </View>
      <Pressable style={styles.childrenPanel} onPress={onChildren} accessibilityRole="button">
        <View style={styles.childrenPanelHeader}>
          <Ionicons name="people" size={28} color={theme.colors.primary} />
          <View style={styles.childrenPanelCopy}>
            <Text style={styles.childrenPanelTitle}>Meus personagens</Text>
            {childrenProfiles.length === 0 ? <Text style={styles.childrenPanelMeta}>Nenhum personagem cadastrado</Text> : null}
          </View>
        </View>
        <Text style={styles.addChildAction}>+ Adicionar outro personagem</Text>
      </Pressable>
      <View style={styles.cards}>
        <HomeCard icon="sparkles" primary text="Criar História" hint="Inventar uma aventura personalizada" onPress={onCreateStory} />
        {momentsEnabled ? <HomeCard icon="heart" text="Momentos" hint="Guardar lembranças da família" onPress={onMoments} /> : null}
        <HomeCard icon="book" primary={!momentsEnabled} text="Minhas Histórias" hint="Reler histórias guardadas" onPress={onLibrary} />
      </View>
      <Text style={styles.footer}>{momentsEnabled ? 'Suas melhores memórias viram histórias no EraUma.' : 'Histórias personalizadas para ler e reler em família.'}</Text>
    </Screen>
  );
}

function HomeCard({ icon, text, hint, primary = false, onPress }: { icon: IconName; text: string; hint: string; primary?: boolean; onPress?: () => void }) {
  return (
    <Pressable style={[styles.card, primary && styles.primaryCard]} onPress={onPress}>
      <View style={styles.cardHeader}>
        <Ionicons name={icon} size={20} color={theme.colors.primary} />
        <Text style={styles.cardText}>{text}</Text>
      </View>
      <Text style={styles.cardHint}>{hint}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  greeting: { fontSize: 22, color: theme.colors.text, fontWeight: '700' },
  title: { fontSize: 28, color: theme.colors.primary, fontWeight: '800' },
  childList: { flexDirection: 'row', flexWrap: 'wrap', gap: theme.spacing.sm },
  childCard: { backgroundColor: theme.colors.secondary, color: theme.colors.primary, padding: theme.spacing.md, borderRadius: theme.radius.md, fontWeight: '800' },
  childrenPanel: { backgroundColor: theme.colors.surface, borderColor: theme.colors.secondary, borderWidth: 2, borderRadius: theme.radius.md, padding: theme.spacing.md, gap: theme.spacing.md },
  childrenPanelHeader: { flexDirection: 'row', alignItems: 'center', gap: theme.spacing.md },
  childrenPanelCopy: { flex: 1 },
  childrenPanelTitle: { color: theme.colors.primary, fontSize: 20, fontWeight: '900' },
  childrenPanelMeta: { color: theme.colors.muted, marginTop: theme.spacing.xs },
  addChildAction: { alignSelf: 'flex-start', minHeight: 44, color: theme.colors.primary, backgroundColor: theme.colors.secondary, borderRadius: theme.radius.md, paddingHorizontal: theme.spacing.md, paddingVertical: theme.spacing.sm, fontWeight: '900', textAlignVertical: 'center' },
  cards: { gap: theme.spacing.md, marginVertical: theme.spacing.lg },
  card: { padding: theme.spacing.lg, borderRadius: theme.radius.lg, backgroundColor: theme.colors.surface, borderWidth: 1, borderColor: theme.colors.border },
  primaryCard: { borderColor: theme.colors.primary, borderWidth: 2 },
  cardHeader: { flexDirection: 'row', alignItems: 'center', gap: theme.spacing.sm },
  cardText: { fontSize: 18, color: theme.colors.primary, fontWeight: '800' },
  cardHint: { color: theme.colors.muted, marginTop: theme.spacing.xs },
  footer: { textAlign: 'center', color: theme.colors.muted, fontSize: 16 },
});
