import React from 'react';
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

export function HomeScreen({ childrenProfiles, onMoments, onCreateStory, onLibrary, onChildren }: Props) {
  const { user, signOut } = useAuth();
  const childrenNames = childrenProfiles.map(child => child.nickname || child.name).join(' · ');
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
          <Text style={styles.childrenPanelIcon}>👧👦</Text>
          <View style={styles.childrenPanelCopy}>
            <Text style={styles.childrenPanelTitle}>Minhas crianças</Text>
            <Text style={styles.childrenPanelMeta}>{childrenNames || 'Nenhuma criança cadastrada'}</Text>
          </View>
        </View>
        <Text style={styles.addChildAction}>+ Adicionar outra criança</Text>
      </Pressable>
      <View style={styles.cards}>
        <Pressable style={[styles.card, styles.primaryCard]} onPress={onCreateStory}>
          <Text style={styles.cardText}>✨ Criar História</Text>
          <Text style={styles.cardHint}>Inventar uma aventura personalizada</Text>
        </Pressable>
        {momentsEnabled ? (
          <Pressable style={styles.card} onPress={onMoments}>
            <Text style={styles.cardText}>❤ Momentos</Text>
            <Text style={styles.cardHint}>Guardar lembranças da família</Text>
          </Pressable>
        ) : null}
        <Pressable style={[styles.card, !momentsEnabled && styles.primaryCard]} onPress={onLibrary}>
          <Text style={styles.cardText}>📚 Minhas Histórias</Text>
          <Text style={styles.cardHint}>Reler histórias guardadas</Text>
        </Pressable>
      </View>
      <Text style={styles.footer}>{momentsEnabled ? 'Suas melhores memórias viram histórias no EraUma.' : 'Histórias personalizadas para ler e reler em família.'}</Text>
      <Pressable onPress={signOut}><Text style={styles.signOut}>Sair</Text></Pressable>
    </Screen>
  );
}

const styles = StyleSheet.create({
  greeting: { fontSize: 22, color: theme.colors.text, fontWeight: '700' },
  title: { fontSize: 28, color: theme.colors.primary, fontWeight: '800' },
  childList: { flexDirection: 'row', flexWrap: 'wrap', gap: theme.spacing.sm },
  childCard: { backgroundColor: theme.colors.secondary, color: theme.colors.primary, padding: theme.spacing.md, borderRadius: theme.radius.md, fontWeight: '800' },
  childrenPanel: { backgroundColor: theme.colors.surface, borderColor: theme.colors.secondary, borderWidth: 2, borderRadius: theme.radius.md, padding: theme.spacing.md, gap: theme.spacing.md },
  childrenPanelHeader: { flexDirection: 'row', alignItems: 'center', gap: theme.spacing.md },
  childrenPanelIcon: { fontSize: 28 },
  childrenPanelCopy: { flex: 1 },
  childrenPanelTitle: { color: theme.colors.primary, fontSize: 20, fontWeight: '900' },
  childrenPanelMeta: { color: theme.colors.muted, marginTop: theme.spacing.xs },
  addChildAction: { alignSelf: 'flex-start', minHeight: 44, color: theme.colors.primary, backgroundColor: theme.colors.secondary, borderRadius: theme.radius.md, paddingHorizontal: theme.spacing.md, paddingVertical: theme.spacing.sm, fontWeight: '900', textAlignVertical: 'center' },
  cards: { gap: theme.spacing.md, marginVertical: theme.spacing.lg },
  card: { padding: theme.spacing.lg, borderRadius: theme.radius.lg, backgroundColor: theme.colors.surface, borderWidth: 1, borderColor: theme.colors.border },
  primaryCard: { borderColor: theme.colors.primary, borderWidth: 2 },
  cardText: { fontSize: 18, color: theme.colors.primary, fontWeight: '800' },
  cardHint: { color: theme.colors.muted, marginTop: theme.spacing.xs },
  footer: { textAlign: 'center', color: theme.colors.muted, fontSize: 16 },
  signOut: { textAlign: 'center', color: theme.colors.error, padding: theme.spacing.md, fontWeight: '700' },
});
