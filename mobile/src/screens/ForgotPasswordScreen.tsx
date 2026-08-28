import React, { useState } from 'react';
import { Pressable, StyleSheet, Text } from 'react-native';
import { AppButton } from '../components/AppButton';
import { AppTextInput } from '../components/AppTextInput';
import { Screen } from '../components/Screen';
import { eraumaApi } from '../services/eraumaApi';
import { theme } from '../theme/tokens';

const NEUTRAL_MESSAGE = 'Se este e-mail estiver cadastrado, enviaremos as instruções para redefinir sua senha.';

type Props = {
  onBack: () => void;
  onTokenReady: (token?: string | null) => void;
};

export function ForgotPasswordScreen({ onBack, onTokenReady }: Props) {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  async function submit() {
    setError('');
    setMessage('');
    if (!email.includes('@')) {
      setError('Informe um e-mail válido.');
      return;
    }
    setLoading(true);
    try {
      const response = await eraumaApi.forgotPassword(email.trim());
      setMessage(response.message || NEUTRAL_MESSAGE);
      if (response.resetToken) {
        onTokenReady(response.resetToken);
      }
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'Não foi possível solicitar a recuperação.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Screen>
      <Pressable onPress={onBack} disabled={loading}><Text style={styles.back}>← Voltar</Text></Pressable>
      <Text style={styles.title}>Recuperar senha</Text>
      <Text style={styles.subtitle}>Informe seu e-mail para receber as instruções.</Text>
      <AppTextInput label="E-mail" value={email} onChangeText={value => { setEmail(value); setError(''); }} autoCapitalize="none" keyboardType="email-address" />
      {message ? <Text style={styles.success}>{message}</Text> : null}
      {error ? <Text style={styles.error}>{error}</Text> : null}
      <AppButton title="Enviar instruções" onPress={submit} loading={loading} disabled={loading} />
      {message ? <AppButton title="Já tenho o código" onPress={() => onTokenReady(undefined)} variant="secondary" disabled={loading} /> : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  back: { color: theme.colors.primary, fontWeight: '800' },
  title: { fontSize: 30, fontWeight: '900', color: theme.colors.primary, textAlign: 'center' },
  subtitle: { color: theme.colors.text, textAlign: 'center', marginBottom: theme.spacing.lg },
  success: { color: theme.colors.primary, textAlign: 'center', fontWeight: '700' },
  error: { color: theme.colors.error, textAlign: 'center' },
});
