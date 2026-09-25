import { Ionicons } from '@expo/vector-icons';
import React, { useState } from 'react';
import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { AppButton } from '../components/AppButton';
import { AppTextInput } from '../components/AppTextInput';
import { Screen } from '../components/Screen';
import { useAuth } from '../contexts/AuthContext';
import { theme } from '../theme/tokens';

type Props = {
  onChildren: () => void;
};

export function ProfileScreen({ onChildren }: Props) {
  const { user, signOut, deleteAccount } = useAuth();
  const [signingOut, setSigningOut] = useState(false);
  const [deleteVisible, setDeleteVisible] = useState(false);
  const [password, setPassword] = useState('');
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState('');

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

  function closeDelete() {
    if (deleting) {
      return;
    }
    setDeleteVisible(false);
    setPassword('');
    setDeleteError('');
  }

  async function handleDeleteAccount() {
    if (deleting) {
      return;
    }
    if (!password.trim()) {
      setDeleteError('Informe sua senha atual.');
      return;
    }
    setDeleting(true);
    setDeleteError('');
    try {
      await deleteAccount(password);
    } catch (error) {
      setDeleteError(error instanceof Error ? error.message : 'Não foi possível excluir sua conta.');
      setDeleting(false);
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
      <View style={styles.divider} />
      <Pressable
        accessibilityRole="button"
        onPress={() => setDeleteVisible(true)}
        style={({ pressed }) => [styles.deleteAccount, pressed && styles.signOutPressed]}>
        <Ionicons name="trash-outline" size={22} color={theme.colors.error} />
        <Text style={styles.deleteAccountText}>Excluir minha conta</Text>
      </Pressable>
      <Modal animationType="fade" transparent visible={deleteVisible} onRequestClose={closeDelete}>
        <View style={styles.modalBackdrop}>
          <View style={styles.modalCard}>
            <Text style={styles.modalTitle}>Excluir sua conta?</Text>
            <Text style={styles.modalText}>
              Esta ação é permanente. Suas histórias, personagens, momentos, imagens e demais dados associados à sua conta serão excluídos.
            </Text>
            <AppTextInput
              label="Senha atual"
              value={password}
              onChangeText={value => { setPassword(value); setDeleteError(''); }}
              secureTextEntry
              autoCapitalize="none"
              autoCorrect={false}
              editable={!deleting}
              error={deleteError}
            />
            <AppButton
              title="Excluir permanentemente"
              variant="danger"
              loading={deleting}
              disabled={!password.trim()}
              onPress={() => { handleDeleteAccount().catch(() => undefined); }}
            />
            <AppButton title="Cancelar" variant="secondary" disabled={deleting} onPress={closeDelete} />
          </View>
        </View>
      </Modal>
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
  divider: { height: 1, backgroundColor: theme.colors.border, marginVertical: theme.spacing.lg },
  deleteAccount: {
    minHeight: 52,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: theme.spacing.sm,
  },
  deleteAccountText: { color: theme.colors.error, fontSize: 16, fontWeight: '800' },
  modalBackdrop: {
    flex: 1,
    justifyContent: 'center',
    padding: theme.spacing.lg,
    backgroundColor: 'rgba(0,0,0,0.45)',
  },
  modalCard: {
    gap: theme.spacing.md,
    padding: theme.spacing.lg,
    borderRadius: theme.radius.lg,
    backgroundColor: theme.colors.background,
  },
  modalTitle: { color: theme.colors.error, fontSize: 24, fontWeight: '900' },
  modalText: { color: theme.colors.text, fontSize: 16, lineHeight: 23 },
});
