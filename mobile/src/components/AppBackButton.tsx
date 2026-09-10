import { Ionicons } from '@expo/vector-icons';
import React from 'react';
import { Pressable, StyleSheet, Text } from 'react-native';
import { theme } from '../theme/tokens';

type Props = {
  label?: string;
  onPress: () => void;
  disabled?: boolean;
};

export function AppBackButton({ label = 'Voltar', onPress, disabled = false }: Props) {
  return (
    <Pressable
      accessibilityLabel={label}
      accessibilityRole="button"
      disabled={disabled}
      hitSlop={8}
      onPress={onPress}
      style={({ pressed }) => [styles.button, pressed && styles.pressed, disabled && styles.disabled]}>
      <Ionicons name="arrow-back" size={22} color={theme.colors.primary} />
      <Text style={styles.label}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    alignSelf: 'flex-start',
    minWidth: 44,
    minHeight: 44,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: theme.spacing.xs,
    paddingHorizontal: theme.spacing.sm,
    borderRadius: theme.radius.md,
  },
  label: { color: theme.colors.primary, fontWeight: '800' },
  pressed: { backgroundColor: theme.colors.secondary },
  disabled: { opacity: 0.5 },
});
