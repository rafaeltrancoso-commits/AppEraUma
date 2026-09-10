import React, { createContext, useContext } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet } from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { theme } from '../theme/tokens';

const BottomTabBarVisibleContext = createContext(false);

export function ScreenBottomTabBarProvider({ children }: { children: React.ReactNode }) {
  return <BottomTabBarVisibleContext.Provider value>{children}</BottomTabBarVisibleContext.Provider>;
}

export function Screen({ children }: { children: React.ReactNode }) {
  const insets = useSafeAreaInsets();
  const bottomTabBarVisible = useContext(BottomTabBarVisibleContext);
  const bottomPadding = theme.spacing.lg + (bottomTabBarVisible ? 64 : 0) + insets.bottom;

  return (
    <SafeAreaView edges={['top', 'left', 'right']} style={styles.safeArea}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={styles.root}>
        <ScrollView
          contentContainerStyle={[styles.content, { paddingBottom: bottomPadding }]}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}>
          {children}
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: theme.colors.background },
  root: { flex: 1, backgroundColor: theme.colors.background },
  content: {
    flexGrow: 1,
    paddingHorizontal: theme.spacing.lg,
    paddingTop: theme.spacing.md,
    justifyContent: 'center',
    gap: theme.spacing.md,
  },
});
