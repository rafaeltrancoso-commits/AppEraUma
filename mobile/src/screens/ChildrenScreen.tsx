import React from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { AppButton } from '../components/AppButton';
import { Screen } from '../components/Screen';
import { ChildProfile } from '../types/api';
import { theme } from '../theme/tokens';

function ageLabel(birthDate?: string) {
  if (!birthDate) {
    return 'Idade não informada';
  }
  const birth = new Date(`${birthDate}T00:00:00`);
  const now = new Date();
  let years = now.getFullYear() - birth.getFullYear();
  const monthDiff = now.getMonth() - birth.getMonth();
  if (monthDiff < 0 || (monthDiff === 0 && now.getDate() < birth.getDate())) {
    years--;
  }
  return years >= 0 ? `${years} anos` : 'Idade não informada';
}

type Props = {
  childrenProfiles: ChildProfile[];
  onBack: () => void;
  onAdd: () => void;
  onEdit: (child: ChildProfile) => void;
};

export function ChildrenScreen({ childrenProfiles, onBack, onAdd, onEdit }: Props) {
  return (
    <Screen>
      <Pressable onPress={onBack}><Text style={styles.back}>← Home</Text></Pressable>
      <View style={styles.headerPanel}>
        <Text style={styles.headerIcon}>👧👦</Text>
        <Text style={styles.title}>Minhas crianças</Text>
        <Text style={styles.subtitle}>Cadastre quantas crianças fizerem parte das histórias da família.</Text>
        <AppButton title="+ Adicionar outra criança" onPress={onAdd} />
      </View>
      {childrenProfiles.map(child => (
        <View key={child.id} style={styles.card}>
          <View style={styles.childInfo}>
            <Text style={styles.name}>{child.nickname || child.name}</Text>
            <Text style={styles.meta}>{child.name}</Text>
            <Text style={styles.meta}>{ageLabel(child.birthDate)}</Text>
          </View>
          <Pressable style={styles.editButton} onPress={() => onEdit(child)} accessibilityRole="button">
            <Text style={styles.edit}>Editar</Text>
          </Pressable>
        </View>
      ))}
    </Screen>
  );
}

const styles = StyleSheet.create({
  back: { color: theme.colors.primary, fontWeight: '800' },
  headerPanel: { backgroundColor: theme.colors.surface, borderColor: theme.colors.secondary, borderWidth: 2, borderRadius: theme.radius.md, padding: theme.spacing.lg, gap: theme.spacing.sm, alignItems: 'center' },
  headerIcon: { fontSize: 34 },
  title: { fontSize: 30, fontWeight: '900', color: theme.colors.primary, textAlign: 'center' },
  subtitle: { color: theme.colors.muted, textAlign: 'center', marginBottom: theme.spacing.sm },
  card: { backgroundColor: theme.colors.surface, borderColor: theme.colors.border, borderWidth: 1, borderRadius: theme.radius.md, padding: theme.spacing.lg, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: theme.spacing.md },
  childInfo: { flex: 1 },
  name: { color: theme.colors.primary, fontWeight: '900', fontSize: 20 },
  meta: { color: theme.colors.muted },
  editButton: { minHeight: 44, justifyContent: 'center', paddingHorizontal: theme.spacing.md },
  edit: { color: theme.colors.primary, fontWeight: '900' },
});
