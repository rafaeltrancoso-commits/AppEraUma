import React, { useState } from 'react';
import { Text, Pressable, StyleSheet } from 'react-native';
import { AppButton } from '../components/AppButton';
import { AppTextInput } from '../components/AppTextInput';
import { Screen } from '../components/Screen';
import { features } from '../config/features';
import { useAuth } from '../contexts/AuthContext';
import { theme } from '../theme/tokens';

export function LoginScreen({ onCreateAccount, onForgotPassword }: { onCreateAccount: () => void; onForgotPassword: () => void }) {
  const { signIn } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function submit() {
    setError('');
    if (!email.includes('@') || !password) {
      setError('Informe email e senha válidos.');
      return;
    }
    setLoading(true);
    try {
      await signIn(email, password);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'Não foi possível entrar.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Screen>
      <Text style={styles.title}>EraUma</Text>
      <Text style={styles.subtitle}>{features.moments ? 'Momentos que viram histórias' : 'Histórias infantis personalizadas'}</Text>
      <AppTextInput label="Email" value={email} onChangeText={setEmail} autoCapitalize="none" keyboardType="email-address" />
      <AppTextInput label="Senha" value={password} onChangeText={setPassword} secureTextEntry />
      <Pressable onPress={onForgotPassword} disabled={loading}>
        <Text style={styles.forgot}>Esqueci minha senha</Text>
      </Pressable>
      {error ? <Text style={styles.error}>{error}</Text> : null}
      <AppButton title="Entrar" onPress={submit} loading={loading} />
      <Pressable onPress={onCreateAccount} disabled={loading}>
        <Text style={styles.link}>Criar conta</Text>
      </Pressable>
    </Screen>
  );
}

const styles = StyleSheet.create({
  title: { fontSize: 38, fontWeight: '800', color: theme.colors.primary, textAlign: 'center' },
  subtitle: { fontSize: 16, color: theme.colors.text, textAlign: 'center', marginBottom: theme.spacing.lg },
  error: { color: theme.colors.error, textAlign: 'center' },
  forgot: { color: theme.colors.primary, fontWeight: '700', textAlign: 'right' },
  link: { color: theme.colors.primary, fontWeight: '700', textAlign: 'center', padding: theme.spacing.md },
});
