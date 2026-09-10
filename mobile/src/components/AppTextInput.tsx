import { Ionicons } from '@expo/vector-icons';
import React, { useRef, useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, TextInputProps, TextInputSelectionChangeEventData, NativeSyntheticEvent, View } from 'react-native';
import { theme } from '../theme/tokens';

type Props = TextInputProps & {
  label: string;
  error?: string;
};

export function AppTextInput({ label, error, secureTextEntry, onSelectionChange, style, ...props }: Props) {
  const inputRef = useRef<TextInput>(null);
  const [showPassword, setShowPassword] = useState(false);
  const [selection, setSelection] = useState<{ start: number; end: number } | null>(null);
  const hasPasswordToggle = Boolean(secureTextEntry);

  function handleSelectionChange(event: NativeSyntheticEvent<TextInputSelectionChangeEventData>) {
    setSelection(event.nativeEvent.selection);
    onSelectionChange?.(event);
  }

  function togglePasswordVisibility() {
    setShowPassword(current => !current);
    requestAnimationFrame(() => {
      inputRef.current?.focus();
      if (selection) {
        inputRef.current?.setNativeProps({ selection });
      }
    });
  }

  return (
    <View style={styles.wrapper}>
      <Text style={styles.label}>{label}</Text>
      <View style={hasPasswordToggle && styles.passwordInputWrapper}>
        <TextInput
          ref={inputRef}
          placeholderTextColor={theme.colors.muted}
          style={[styles.input, hasPasswordToggle && styles.passwordInput, error && styles.inputError, style]}
          secureTextEntry={hasPasswordToggle ? !showPassword : secureTextEntry}
          onSelectionChange={handleSelectionChange}
          {...props}
        />
        {hasPasswordToggle ? (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={showPassword ? 'Ocultar senha' : 'Mostrar senha'}
            hitSlop={8}
            onPress={togglePasswordVisibility}
            style={styles.passwordToggle}
          >
            <Ionicons name={showPassword ? 'eye-off' : 'eye'} size={22} color={theme.colors.muted} />
          </Pressable>
        ) : null}
      </View>
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
  passwordInputWrapper: { position: 'relative' },
  passwordInput: { paddingRight: 56 },
  passwordToggle: {
    position: 'absolute',
    right: 0,
    top: 0,
    width: 50,
    minHeight: 50,
    alignItems: 'center',
    justifyContent: 'center',
  },
  inputError: { borderColor: theme.colors.error },
  error: { color: theme.colors.error },
});
