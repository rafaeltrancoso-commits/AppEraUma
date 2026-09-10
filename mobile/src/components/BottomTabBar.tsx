import { Ionicons } from '@expo/vector-icons';
import React, { ComponentProps } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { theme } from '../theme/tokens';

export type MainTab = 'home' | 'createStory' | 'storyLibrary' | 'moments' | 'profile';

type TabItem = {
  key: MainTab;
  label: string;
  icon: ComponentProps<typeof Ionicons>['name'];
  selectedIcon: ComponentProps<typeof Ionicons>['name'];
};

const mainTabs: TabItem[] = [
  { key: 'home', label: 'Início', icon: 'home-outline', selectedIcon: 'home' },
  { key: 'createStory', label: 'Criar', icon: 'sparkles-outline', selectedIcon: 'sparkles' },
  { key: 'storyLibrary', label: 'Histórias', icon: 'book-outline', selectedIcon: 'book' },
  { key: 'moments', label: 'Momentos', icon: 'heart-outline', selectedIcon: 'heart' },
  { key: 'profile', label: 'Perfil', icon: 'person-outline', selectedIcon: 'person' },
];

type Props = {
  activeTab: MainTab;
  momentsEnabled: boolean;
  onSelect: (tab: MainTab) => void;
};

export function BottomTabBar({ activeTab, momentsEnabled, onSelect }: Props) {
  const insets = useSafeAreaInsets();
  const bottomInset = Math.max(insets.bottom, 8);
  const visibleTabs = momentsEnabled ? mainTabs : mainTabs.filter(tab => tab.key !== 'moments');

  return (
    <View
      accessibilityRole="tablist"
      style={[styles.bar, { height: 64 + bottomInset, paddingBottom: bottomInset }]}>
      {visibleTabs.map(tab => {
        const selected = tab.key === activeTab;
        return (
          <Pressable
            key={tab.key}
            accessibilityRole="tab"
            accessibilityState={{ selected }}
            accessibilityLabel={tab.label}
            hitSlop={4}
            onPress={() => onSelect(tab.key)}
            style={({ pressed }) => [styles.tab, selected && styles.selectedTab, pressed && styles.pressedTab]}>
            <Ionicons
              name={selected ? tab.selectedIcon : tab.icon}
              color={selected ? theme.colors.primary : theme.colors.muted}
              size={23}
            />
            <Text style={[styles.label, selected && styles.selectedLabel]} numberOfLines={1}>{tab.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  bar: {
    flexShrink: 0,
    flexDirection: 'row',
    alignItems: 'stretch',
    paddingHorizontal: theme.spacing.xs,
    paddingTop: 8,
    backgroundColor: '#FFFFFF',
    borderTopWidth: 1,
    borderTopColor: '#E8E0CF',
  },
  tab: {
    flex: 1,
    minWidth: 44,
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 2,
    borderRadius: theme.radius.md,
  },
  selectedTab: { backgroundColor: theme.colors.background },
  pressedTab: { opacity: 0.7 },
  label: { color: theme.colors.muted, fontSize: 11, fontWeight: '700' },
  selectedLabel: { color: theme.colors.primary, fontWeight: '900' },
});
