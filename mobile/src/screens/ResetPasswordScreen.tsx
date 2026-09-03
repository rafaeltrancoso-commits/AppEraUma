import React, { useState } from 'react';
import { Pressable, StyleSheet, Text } from 'react-native';
import { AppButton } from '../components/AppButton';
import { AppTextInput } from '../components/AppTextInput';
import { Screen } from '../components/Screen';
import { eraumaApi } from '../services/eraumaApi';
import { theme } from '../theme/tokens';

type Props = {
  initialToken?: string | null;
  onBack: () => void;
  onDone: () => void;
};

export function ResetPasswordScreen({ initialToken, onBack, onDone }: Props) {
  const [token, setToken] = useState(initialToken ?? '');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState('');
  const [error, setError] = useState('');

  function updatePassword(value: string) {
    setNewPassword(value);
    setError('');
    setSuccess('');
  }

  function updateConfirmPassword(value: string) {
    setConfirmPassword(value);
    setError('');
    setSuccess('');
  }

  async function submit() {
    setError('');
    setSuccess('');
    const recoveryToken = normalizeRecoveryToken(token);
    if (!recoveryToken) {
      setError('Informe o link ou código de recuperação.');
      return;
    }
    if (newPassword.length < 6) {
      setError('A senha deve ter pelo menos 6 caracteres.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setError('As senhas não coincidem.');
      return;
    }
    setLoading(true);
    try {
      const response = await eraumaApi.resetPassword({ token: recoveryToken, newPassword, confirmPassword });
      setSuccess(response.message || 'Senha alterada com sucesso.');
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'Não foi possível redefinir a senha.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Screen>
      <Pressable onPress={onBack} disabled={loading}><Text style={styles.back}>← Voltar</Text></Pressable>
      <Text style={styles.title}>Nova senha</Text>
      <Text style={styles.subtitle}>Use o link recebido ou digite o código de recuperação e escolha uma nova senha.</Text>
      <AppTextInput label="Link ou código de recuperação" value={token} onChangeText={value => { setToken(value); setError(''); }} autoCapitalize="none" />
      <AppTextInput label="Nova senha" value={newPassword} onChangeText={updatePassword} secureTextEntry />
      <AppTextInput label="Confirmar nova senha" value={confirmPassword} onChangeText={updateConfirmPassword} secureTextEntry />
      {success ? <Text style={styles.success}>{success}</Text> : null}
      {error ? <Text style={styles.error}>{error}</Text> : null}
      {success ? <AppButton title="Entrar" onPress={onDone} /> : <AppButton title="Redefinir senha" onPress={submit} loading={loading} disabled={loading} />}
    </Screen>
  );
}

function normalizeRecoveryToken(value: string) {
  const trimmed = value.trim();
  if (!trimmed) {
    return '';
  }
  const match = trimmed.match(/[?&]token=([^&#]+)/);
  if (match?.[1]) {
    return decodeURIComponent(match[1].replace(/\+/g, '%2B'));
  }
  return trimmed;
}

const styles = StyleSheet.create({
  back: { color: theme.colors.primary, fontWeight: '800' },
  title: { fontSize: 30, fontWeight: '900', color: theme.colors.primary, textAlign: 'center' },
  subtitle: { color: theme.colors.text, textAlign: 'center', marginBottom: theme.spacing.lg },
  success: { color: theme.colors.primary, textAlign: 'center', fontWeight: '700' },
  error: { color: theme.colors.error, textAlign: 'center' },
});
