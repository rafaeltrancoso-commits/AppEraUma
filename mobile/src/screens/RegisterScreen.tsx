import React, { useState } from 'react';
import { Text, Pressable, StyleSheet } from 'react-native';
import { AppButton } from '../components/AppButton';
import { AppTextInput } from '../components/AppTextInput';
import { Screen } from '../components/Screen';
import { useAuth } from '../contexts/AuthContext';
import { theme } from '../theme/tokens';

export function RegisterScreen({ onBack }: { onBack: () => void }) {
  const { signUp } = useAuth();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordTouched, setPasswordTouched] = useState(false);
  const [confirmPasswordTouched, setConfirmPasswordTouched] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const passwordMismatch = passwordTouched && confirmPasswordTouched && password.length > 0 && confirmPassword.length > 0 && password !== confirmPassword;
  const visibleError = passwordMismatch ? 'As senhas não conferem.' : error;

  function updatePassword(value: string) {
    setPasswordTouched(true);
    setPassword(value);
  }

  function updateConfirmPassword(value: string) {
    setConfirmPasswordTouched(true);
    setConfirmPassword(value);
  }

  async function submit() {
    setError('');
    if (!name.trim() || !email.includes('@') || password.length < 6) {
      setError('Preencha nome, email e senha com pelo menos 6 caracteres.');
      return;
    }
    if (password !== confirmPassword) {
      setPasswordTouched(true);
      setConfirmPasswordTouched(true);
      return;
    }
    setLoading(true);
    try {
      await signUp(name, email, password);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'Não foi possível criar a conta.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Screen>
      <Text style={styles.title}>Criar conta</Text>
      <AppTextInput label="Nome" value={name} onChangeText={setName} />
      <AppTextInput label="Email" value={email} onChangeText={setEmail} autoCapitalize="none" keyboardType="email-address" />
      <AppTextInput label="Senha" value={password} onChangeText={updatePassword} secureTextEntry />
      <AppTextInput label="Confirmar senha" value={confirmPassword} onChangeText={updateConfirmPassword} secureTextEntry />
      {visibleError ? <Text style={styles.error}>{visibleError}</Text> : null}
      <AppButton title="Criar conta" onPress={submit} loading={loading} />
      <Pressable onPress={onBack} disabled={loading}>
        <Text style={styles.link}>Já tenho conta</Text>
      </Pressable>
    </Screen>
  );
}

const styles = StyleSheet.create({
  title: { fontSize: 30, fontWeight: '800', color: theme.colors.primary, textAlign: 'center', marginBottom: theme.spacing.md },
  error: { color: theme.colors.error, textAlign: 'center' },
  link: { color: theme.colors.primary, fontWeight: '700', textAlign: 'center', padding: theme.spacing.md },
});
