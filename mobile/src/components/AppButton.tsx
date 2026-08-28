import React from 'react';
import { ActivityIndicator, Pressable, Text, StyleSheet } from 'react-native';
import { theme } from '../theme/tokens';

type Props = {
  title: string;
  onPress: () => void;
  loading?: boolean;
  disabled?: boolean;
  variant?: 'primary' | 'secondary';
};

export function AppButton({ title, onPress, loading = false, disabled = false, variant = 'primary' }: Props) {
  const isDisabled = disabled || loading;
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      disabled={isDisabled}
      style={[styles.button, variant === 'secondary' && styles.secondary, isDisabled && styles.disabled]}>
      {loading ? <ActivityIndicator color={theme.colors.surface} /> : <Text style={styles.text}>{title}</Text>}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    minHeight: 52,
    borderRadius: theme.radius.md,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: theme.colors.primary,
    paddingHorizontal: theme.spacing.md,
  },
  secondary: {
    backgroundColor: theme.colors.secondary,
  },
  disabled: {
    opacity: 0.6,
  },
  text: {
    color: theme.colors.surface,
    fontSize: 16,
    fontWeight: '700',
  },
});

