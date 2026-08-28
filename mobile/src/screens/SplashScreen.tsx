import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { features } from '../config/features';
import { theme } from '../theme/tokens';

export function SplashScreen() {
  return (
    <View style={styles.root}>
      <View style={styles.placeholder}><Text style={styles.star}>✨</Text></View>
      <Text style={styles.title}>EraUma</Text>
      <Text style={styles.subtitle}>{features.moments ? 'Momentos que viram histórias' : 'Histórias infantis personalizadas'}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: theme.colors.background, gap: theme.spacing.sm },
  placeholder: { width: 120, height: 120, borderRadius: 60, backgroundColor: theme.colors.surface, alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: theme.colors.border },
  star: { fontSize: 42 },
  title: { fontSize: 40, fontWeight: '800', color: theme.colors.primary },
  subtitle: { fontSize: 16, color: theme.colors.text },
});
