import { Ionicons } from '@expo/vector-icons';
import React, { ComponentProps } from 'react';
import { ActivityIndicator, Pressable, Text, View, StyleSheet } from 'react-native';
import { theme } from '../theme/tokens';

type Props = {
  title: string;
  onPress: () => void;
  loading?: boolean;
  disabled?: boolean;
  variant?: 'primary' | 'secondary' | 'danger';
  icon?: ComponentProps<typeof Ionicons>['name'];
};

export function AppButton({ title, onPress, loading = false, disabled = false, variant = 'primary', icon }: Props) {
  const isDisabled = disabled || loading;
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      disabled={isDisabled}
      style={[styles.button, variant === 'secondary' && styles.secondary, variant === 'danger' && styles.danger, isDisabled && styles.disabled]}>
      {loading ? (
        <ActivityIndicator color={theme.colors.surface} />
      ) : (
        <View style={styles.content}>
          {icon ? <Ionicons name={icon} size={18} color={theme.colors.surface} /> : null}
          <Text style={styles.text}>{title}</Text>
        </View>
      )}
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
  danger: {
    backgroundColor: theme.colors.error,
  },
  disabled: {
    opacity: 0.6,
  },
  content: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: theme.spacing.xs,
  },
  text: {
    color: theme.colors.surface,
    fontSize: 16,
    fontWeight: '700',
  },
});

