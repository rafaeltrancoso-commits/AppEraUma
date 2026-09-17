import React, { useCallback, useEffect, useState } from 'react';
import { BackHandler, Linking, Platform, StyleSheet, Text, View } from 'react-native';
import { BottomTabBar, MainTab } from '../components/BottomTabBar';
import { Screen, ScreenBottomTabBarProvider } from '../components/Screen';
import { features } from '../config/features';
import { useAuth } from '../contexts/AuthContext';
import { eraumaApi } from '../services/eraumaApi';
import { listenForStoryNotifications, registerPushNotifications } from '../services/pushNotifications';
import { ChildProfile, Family, Moment, Story } from '../types/api';
import { ChildrenScreen } from '../screens/ChildrenScreen';
import { CreateChildScreen } from '../screens/CreateChildScreen';
import { CreateFamilyScreen } from '../screens/CreateFamilyScreen';
import { CreateStoryScreen } from '../screens/CreateStoryScreen';
import { HomeScreen } from '../screens/HomeScreen';
import { ForgotPasswordScreen } from '../screens/ForgotPasswordScreen';
import { LoginScreen } from '../screens/LoginScreen';
import { MomentDetailScreen } from '../screens/MomentDetailScreen';
import { MomentFormScreen } from '../screens/MomentFormScreen';
import { MomentsScreen } from '../screens/MomentsScreen';
import { ProfileScreen } from '../screens/ProfileScreen';
import { RegisterScreen } from '../screens/RegisterScreen';
import { ResetPasswordScreen } from '../screens/ResetPasswordScreen';
import { SplashScreen } from '../screens/SplashScreen';
import { StoryLibraryScreen } from '../screens/StoryLibraryScreen';
import { StoryReaderScreen } from '../screens/StoryReaderScreen';
import { theme } from '../theme/tokens';

type AuthScreen = 'login' | 'register' | 'forgotPassword' | 'resetPassword';
type AppScreen = 'home' | 'children' | 'createChild' | 'editChild' | 'moments' | 'createMoment' | 'momentDetail' | 'editMoment' | 'createStory' | 'storyLibrary' | 'storyReader' | 'profile';

type MainScreenProps = {
  activeTab: MainTab;
  children: React.ReactNode;
  momentsEnabled: boolean;
  onSelectTab: (tab: MainTab) => void;
};

function MainScreen({ activeTab, children, momentsEnabled, onSelectTab }: MainScreenProps) {
  return (
    <View style={styles.app}>
      <View style={styles.content}>
        <ScreenBottomTabBarProvider>{children}</ScreenBottomTabBarProvider>
      </View>
      <BottomTabBar activeTab={activeTab} momentsEnabled={momentsEnabled} onSelect={onSelectTab} />
    </View>
  );
}

export function AppNavigator() {
  const { user, loading } = useAuth();
  const [authScreen, setAuthScreen] = useState<AuthScreen>('login');
  const [appScreen, setAppScreen] = useState<AppScreen>('home');
  const [booting, setBooting] = useState(false);
  const [family, setFamily] = useState<Family | null>(null);
  const [childrenProfiles, setChildrenProfiles] = useState<ChildProfile[]>([]);
  const [selectedMoment, setSelectedMoment] = useState<Moment | null>(null);
  const [selectedChild, setSelectedChild] = useState<ChildProfile | null>(null);
  const [sourceMoment, setSourceMoment] = useState<Moment | undefined>();
  const [selectedStory, setSelectedStory] = useState<Story | null>(null);
  const [storyCharacterOrder, setStoryCharacterOrder] = useState<string[]>([]);
  const [error, setError] = useState('');
  const [resetToken, setResetToken] = useState<string | null | undefined>();
  const momentsEnabled = features.moments;

  useEffect(() => {
    if (!user) {
      return undefined;
    }
    registerPushNotifications().catch(pushError => {
      if (__DEV__) {
        console.warn('push_registration_failed', { message: pushError instanceof Error ? pushError.message : 'unknown' });
      }
    });
    return listenForStoryNotifications(storyId => {
      eraumaApi.story(storyId).then(story => {
        setSelectedStory(story);
        setAppScreen('storyReader');
      }).catch(pushError => {
        if (__DEV__) {
          console.warn('push_story_open_failed', { storyId, status: pushError instanceof Error && 'status' in pushError ? pushError.status : undefined });
        }
      });
    });
  }, [user]);

  const handleTabSelect = useCallback((tab: MainTab) => {
    if (tab === 'moments' && !momentsEnabled) {
      return;
    }
    if (tab === 'createStory') {
      setSourceMoment(undefined);
    }
    setAppScreen(tab);
  }, [momentsEnabled]);

  const handleAuthBack = useCallback(() => {
    if (authScreen === 'register' || authScreen === 'forgotPassword') {
      setAuthScreen('login');
      return true;
    }
    if (authScreen === 'resetPassword') {
      setAuthScreen('forgotPassword');
      return true;
    }
    return false;
  }, [authScreen]);

  const handleAppBack = useCallback(() => {
    if (appScreen === 'children') {
      setAppScreen('home');
      return true;
    }
    if (appScreen === 'createChild') {
      setAppScreen('children');
      return true;
    }
    if (appScreen === 'editChild') {
      setSelectedChild(null);
      setAppScreen('children');
      return true;
    }
    if (momentsEnabled && appScreen === 'moments') {
      setAppScreen('home');
      return true;
    }
    if (momentsEnabled && appScreen === 'createMoment') {
      setAppScreen('moments');
      return true;
    }
    if (momentsEnabled && appScreen === 'editMoment') {
      setAppScreen('momentDetail');
      return true;
    }
    if (momentsEnabled && appScreen === 'momentDetail') {
      setAppScreen('moments');
      return true;
    }
    if (appScreen === 'createStory') {
      const activeSourceMoment = momentsEnabled ? sourceMoment : undefined;
      setSourceMoment(undefined);
      setAppScreen(activeSourceMoment ? 'momentDetail' : 'home');
      return true;
    }
    if (appScreen === 'storyLibrary') {
      setAppScreen('home');
      return true;
    }
    if (appScreen === 'storyReader') {
      setAppScreen('storyLibrary');
      return true;
    }
    if (appScreen === 'profile') {
      setAppScreen('home');
      return true;
    }
    return false;
  }, [appScreen, momentsEnabled, sourceMoment]);

  useEffect(() => {
    async function loadProfile() {
      if (!user) {
        return;
      }
      setBooting(true);
      setError('');
      try {
        const families = await eraumaApi.families();
        const currentFamily = families[0] ?? null;
        setFamily(currentFamily);
        if (currentFamily) {
          setChildrenProfiles(await eraumaApi.children(currentFamily.id));
        } else {
          setChildrenProfiles([]);
        }
      } catch (exception) {
        setError(exception instanceof Error ? exception.message : 'Erro ao carregar dados.');
      } finally {
        setBooting(false);
      }
    }
    loadProfile();
  }, [user]);

  useEffect(() => {
    if (Platform.OS !== 'android') {
      return undefined;
    }
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      if (!user) {
        return handleAuthBack();
      }
      if (error || !family || childrenProfiles.length === 0) {
        return false;
      }
      return handleAppBack();
    });
    return () => subscription.remove();
  }, [childrenProfiles.length, error, family, handleAppBack, handleAuthBack, user]);

  useEffect(() => {
    function openResetPassword(url: string | null) {
      if (!url || user) {
        return;
      }
      const match = url.match(/[?&]token=([^&#]+)/);
      if (!match?.[1]) {
        return;
      }
      setResetToken(decodeURIComponent(match[1].replace(/\+/g, '%2B')));
      setAuthScreen('resetPassword');
    }

    Linking.getInitialURL().then(openResetPassword).catch(() => undefined);
    const subscription = Linking.addEventListener('url', event => openResetPassword(event.url));
    return () => subscription.remove();
  }, [user]);

  if (loading || booting) {
    return <SplashScreen />;
  }
  if (!user) {
    if (authScreen === 'register') {
      return <RegisterScreen onBack={() => setAuthScreen('login')} />;
    }
    if (authScreen === 'forgotPassword') {
      return <ForgotPasswordScreen onBack={() => setAuthScreen('login')} onTokenReady={token => { setResetToken(token); setAuthScreen('resetPassword'); }} />;
    }
    if (authScreen === 'resetPassword') {
      return <ResetPasswordScreen initialToken={resetToken} onBack={() => setAuthScreen('forgotPassword')} onDone={() => setAuthScreen('login')} />;
    }
    return <LoginScreen onCreateAccount={() => setAuthScreen('register')} onForgotPassword={() => setAuthScreen('forgotPassword')} />;
  }
  if (error) {
    return <Screen><Text>{error}</Text></Screen>;
  }
  if (!family) {
    return <CreateFamilyScreen onCreated={setFamily} />;
  }
  if (childrenProfiles.length === 0) {
    return <CreateChildScreen family={family} onCreated={child => setChildrenProfiles([child])} />;
  }
  if (appScreen === 'children') {
    return <ChildrenScreen childrenProfiles={childrenProfiles} onBack={() => setAppScreen('home')} onAdd={() => setAppScreen('createChild')} onEdit={child => { setSelectedChild(child); setAppScreen('editChild'); }} />;
  }
  if (appScreen === 'createChild') {
    return <CreateChildScreen family={family} onCancel={() => setAppScreen('children')} onCreated={child => { setChildrenProfiles(current => [...current, child]); setAppScreen('children'); }} />;
  }
  if (appScreen === 'editChild' && selectedChild) {
    return <CreateChildScreen family={family} child={selectedChild} onCancel={() => setAppScreen('children')} onSaved={child => { setChildrenProfiles(current => current.map(item => item.id === child.id ? child : item)); setSelectedChild(null); setAppScreen('children'); }} />;
  }
  if (momentsEnabled && appScreen === 'moments') {
    return <MainScreen activeTab="moments" momentsEnabled={momentsEnabled} onSelectTab={handleTabSelect}><MomentsScreen family={family} childrenProfiles={childrenProfiles} onBack={() => setAppScreen('home')} onCreate={() => setAppScreen('createMoment')} onOpen={moment => { setSelectedMoment(moment); setAppScreen('momentDetail'); }} /></MainScreen>;
  }
  if (momentsEnabled && appScreen === 'createMoment') {
    return <MomentFormScreen family={family} childrenProfiles={childrenProfiles} onCancel={() => setAppScreen('moments')} onSaved={moment => { setSelectedMoment(moment); setAppScreen('momentDetail'); }} />;
  }
  if (momentsEnabled && appScreen === 'editMoment' && selectedMoment) {
    return <MomentFormScreen family={family} childrenProfiles={childrenProfiles} moment={selectedMoment} onCancel={() => setAppScreen('momentDetail')} onSaved={moment => { setSelectedMoment(moment); setAppScreen('momentDetail'); }} />;
  }
  if (momentsEnabled && appScreen === 'momentDetail' && selectedMoment) {
    return <MomentDetailScreen moment={selectedMoment} onBack={() => setAppScreen('moments')} onEdit={moment => { setSelectedMoment(moment); setAppScreen('editMoment'); }} onCreateStory={moment => { setSourceMoment(moment); setAppScreen('createStory'); }} onOpenStory={story => { eraumaApi.story(story.id).then(fullStory => { setSelectedStory(fullStory); setAppScreen('storyReader'); }).catch(() => { setSelectedStory(story); setAppScreen('storyReader'); }); }} onChanged={moment => { setSelectedMoment(moment ?? null); setAppScreen(moment ? 'momentDetail' : 'moments'); }} />;
  }
  if (appScreen === 'createStory') {
    const activeSourceMoment = momentsEnabled ? sourceMoment : undefined;
    const createStoryScreen = <CreateStoryScreen family={family} childrenProfiles={childrenProfiles} sourceMoment={activeSourceMoment} initialCharacterIds={storyCharacterOrder} onCharacterOrderChange={setStoryCharacterOrder} onCancel={() => { setSourceMoment(undefined); setAppScreen(activeSourceMoment ? 'momentDetail' : 'home'); }} onCreated={story => { setStoryCharacterOrder([]); setSourceMoment(undefined); setSelectedStory(story); setAppScreen('storyReader'); }} />;
    return activeSourceMoment
      ? createStoryScreen
      : <MainScreen activeTab="createStory" momentsEnabled={momentsEnabled} onSelectTab={handleTabSelect}>{createStoryScreen}</MainScreen>;
  }
  if (appScreen === 'storyLibrary') {
    return <MainScreen activeTab="storyLibrary" momentsEnabled={momentsEnabled} onSelectTab={handleTabSelect}><StoryLibraryScreen family={family} childrenProfiles={childrenProfiles} onBack={() => setAppScreen('home')} onCreate={() => { setSourceMoment(undefined); setAppScreen('createStory'); }} onOpen={story => { eraumaApi.story(story.id).then(fullStory => { setSelectedStory(fullStory); setAppScreen('storyReader'); }).catch(() => { setSelectedStory(story); setAppScreen('storyReader'); }); }} /></MainScreen>;
  }
  if (appScreen === 'storyReader' && selectedStory) {
    return <StoryReaderScreen story={selectedStory} onBack={() => setAppScreen('storyLibrary')} onCreateAnother={() => { setSourceMoment(undefined); setAppScreen('createStory'); }} onLibrary={() => setAppScreen('storyLibrary')} onChanged={story => { setSelectedStory(story ?? null); setAppScreen(story ? 'storyReader' : 'storyLibrary'); }} />;
  }
  if (appScreen === 'profile') {
    return <MainScreen activeTab="profile" momentsEnabled={momentsEnabled} onSelectTab={handleTabSelect}><ProfileScreen onChildren={() => setAppScreen('children')} /></MainScreen>;
  }
  return <MainScreen activeTab="home" momentsEnabled={momentsEnabled} onSelectTab={handleTabSelect}><HomeScreen childrenProfiles={childrenProfiles} onChildren={() => setAppScreen('children')} onMoments={momentsEnabled ? () => setAppScreen('moments') : undefined} onCreateStory={() => { setSourceMoment(undefined); setAppScreen('createStory'); }} onLibrary={() => setAppScreen('storyLibrary')} /></MainScreen>;
}

const styles = StyleSheet.create({
  app: { flex: 1, backgroundColor: theme.colors.background },
  content: { flex: 1 },
});
