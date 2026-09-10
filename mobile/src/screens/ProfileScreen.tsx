import { Ionicons } from '@expo/vector-icons';
import React, { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { AppButton } from '../components/AppButton';
import { Screen } from '../components/Screen';
import { useAuth } from '../contexts/AuthContext';
import { theme } from '../theme/tokens';

type Props = {
  onChildren: () => void;
};

export function ProfileScreen({ onChildren }: Props) {
  const { user, signOut } = useAuth();
  const [signingOut, setSigningOut] = useState(false);

  async function handleSignOut() {
    if (signingOut) {
      return;
    }
    setSigningOut(true);
    try {
      await signOut();
    } finally {
      setSigningOut(false);
    }
  }

  return (
    <Screen>
      <View style={styles.header}>
        <View style={styles.avatar}>
          <Ionicons name="person" size={34} color={theme.colors.primary} />
        </View>
        <Text style={styles.title}>Perfil</Text>
        <Text style={styles.name}>{user?.name}</Text>
        <Text style={styles.email}>{user?.email}</Text>
      </View>
      <AppButton title="Meus personagens" onPress={onChildren} variant="secondary" />
      <Pressable
        accessibilityRole="button"
        disabled={signingOut}
        onPress={() => { handleSignOut().catch(() => undefined); }}
        style={({ pressed }) => [styles.signOut, pressed && styles.signOutPressed, signingOut && styles.disabled]}>
        <Ionicons name="log-out-outline" size={22} color={theme.colors.error} />
        <Text style={styles.signOutText}>{signingOut ? 'Saindo...' : 'Sair'}</Text>
      </Pressable>
    </Screen>
  );
}

const styles = StyleSheet.create({
  header: { alignItems: 'center', gap: theme.spacing.xs, marginBottom: theme.spacing.md },
  avatar: {
    width: 72,
    height: 72,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 36,
    backgroundColor: theme.colors.secondary,
    marginBottom: theme.spacing.sm,
  },
  title: { color: theme.colors.primary, fontSize: 30, fontWeight: '900' },
  name: { color: theme.colors.text, fontSize: 20, fontWeight: '800' },
  email: { color: theme.colors.muted, fontSize: 15 },
  signOut: {
    minHeight: 52,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: theme.spacing.sm,
    borderWidth: 1,
    borderColor: theme.colors.error,
    borderRadius: theme.radius.md,
  },
  signOutText: { color: theme.colors.error, fontSize: 16, fontWeight: '800' },
  signOutPressed: { backgroundColor: theme.colors.surface },
  disabled: { opacity: 0.6 },
});
