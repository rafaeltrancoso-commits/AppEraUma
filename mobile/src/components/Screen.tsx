import React, { createContext, useContext } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet } from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { theme } from '../theme/tokens';

const BottomTabBarVisibleContext = createContext(false);

export function ScreenBottomTabBarProvider({ children }: { children: React.ReactNode }) {
  return <BottomTabBarVisibleContext.Provider value>{children}</BottomTabBarVisibleContext.Provider>;
}

type Props = {
  children: React.ReactNode;
  /** When false, renders a plain container instead of a ScrollView, so a screen can host its own scrollable list (e.g. FlatList). Defaults to true. */
  scrollable?: boolean;
  /** When true (default), short content is centered vertically. List-like screens should pass false so content stays anchored to the top. */
  center?: boolean;
};

export function Screen({ children, scrollable = true, center = true }: Props) {
  const insets = useSafeAreaInsets();
  const bottomTabBarVisible = useContext(BottomTabBarVisibleContext);
  const bottomPadding = theme.spacing.lg + (bottomTabBarVisible ? 64 : 0) + insets.bottom;

  if (!scrollable) {
    return (
      <SafeAreaView edges={['top', 'left', 'right']} style={styles.safeArea}>
        <KeyboardAvoidingView
          behavior={Platform.OS === 'ios' ? 'padding' : undefined}
          style={[styles.root, { paddingBottom: bottomPadding }]}>
          {children}
        </KeyboardAvoidingView>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView edges={['top', 'left', 'right']} style={styles.safeArea}>
      <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined} style={styles.root}>
        <ScrollView
          contentContainerStyle={[styles.content, { paddingBottom: bottomPadding, justifyContent: center ? 'center' : 'flex-start' }]}
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
    gap: theme.spacing.md,
  },
});
