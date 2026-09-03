import React, { useState } from 'react';
import { Text, StyleSheet } from 'react-native';
import { AppButton } from '../components/AppButton';
import { AppTextInput } from '../components/AppTextInput';
import { Screen } from '../components/Screen';
import { eraumaApi } from '../services/eraumaApi';
import { Family } from '../types/api';
import { theme } from '../theme/tokens';

export function CreateFamilyScreen({ onCreated }: { onCreated: (family: Family) => void }) {
  const [name, setName] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  async function submit() {
    setError('');
    if (!name.trim()) {
      setError('Informe o nome da família.');
      return;
    }
    setLoading(true);
    try {
      onCreated(await eraumaApi.createFamily(name));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'Não foi possível criar a família.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Screen>
      <Text style={styles.title}>Vamos começar sua história ✨</Text>
      <Text style={styles.subtitle}>Como podemos chamar sua família?</Text>
      <AppTextInput label="Família" placeholder="Família Feliz" value={name} onChangeText={setName} />
      {error ? <Text style={styles.error}>{error}</Text> : null}
      <AppButton title="Continuar" onPress={submit} loading={loading} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  title: { fontSize: 28, fontWeight: '800', color: theme.colors.primary, textAlign: 'center' },
  subtitle: { fontSize: 16, color: theme.colors.text, textAlign: 'center', marginBottom: theme.spacing.lg },
  error: { color: theme.colors.error, textAlign: 'center' },
});
