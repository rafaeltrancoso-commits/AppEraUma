import React from 'react';
import { Text, TextInput, TextInputProps, StyleSheet, View } from 'react-native';
import { theme } from '../theme/tokens';

type Props = TextInputProps & {
  label: string;
  error?: string;
};

export function AppTextInput({ label, error, ...props }: Props) {
  return (
    <View style={styles.wrapper}>
      <Text style={styles.label}>{label}</Text>
      <TextInput placeholderTextColor={theme.colors.muted} style={[styles.input, error && styles.inputError]} {...props} />
      {error ? <Text style={styles.error}>{error}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: { gap: theme.spacing.xs },
  label: { color: theme.colors.text, fontWeight: '700' },
  input: {
    minHeight: 50,
    borderRadius: theme.radius.md,
    borderWidth: 1,
    borderColor: theme.colors.border,
    backgroundColor: theme.colors.surface,
    paddingHorizontal: theme.spacing.md,
    color: theme.colors.text,
  },
  inputError: { borderColor: theme.colors.error },
  error: { color: theme.colors.error },
});

