import Constants from 'expo-constants';
import * as Device from 'expo-device';
import * as Notifications from 'expo-notifications';
import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';
import { eraumaApi } from './eraumaApi';

const DEVICE_ID_KEY = 'erauma.push.deviceId';

Notifications.setNotificationHandler({
  handleNotification: async () => ({ shouldShowBanner: true, shouldShowList: true, shouldPlaySound: true, shouldSetBadge: false }),
});

async function deviceId() {
  const current = await SecureStore.getItemAsync(DEVICE_ID_KEY);
  if (current) {
    return current;
  }
  const created = Date.now() + '-' + Math.random().toString(36).slice(2);
  await SecureStore.setItemAsync(DEVICE_ID_KEY, created);
  return created;
}

export async function registerPushNotifications() {
  if (Platform.OS === 'web' || !Device.isDevice) {
    return false;
  }
  if (Platform.OS === 'android') {
    await Notifications.setNotificationChannelAsync('default', {
      name: 'Histórias',
      importance: Notifications.AndroidImportance.DEFAULT,
      sound: 'default',
    });
  }
  const current = await Notifications.getPermissionsAsync();
  const permission = current.status === 'granted' ? current : await Notifications.requestPermissionsAsync();
  if (permission.status !== 'granted') {
    return false;
  }
  const projectId = Constants.expoConfig?.extra?.eas?.projectId ?? Constants.easConfig?.projectId;
  if (!projectId) {
    throw new Error('EAS projectId não configurado');
  }
  const token = (await Notifications.getExpoPushTokenAsync({ projectId })).data;
  await eraumaApi.registerPushToken({
    deviceId: await deviceId(),
    expoPushToken: token,
    platform: Platform.OS === 'ios' ? 'IOS' : 'ANDROID',
  });
  return true;
}

export async function unregisterPushNotifications() {
  const currentDeviceId = await SecureStore.getItemAsync(DEVICE_ID_KEY);
  if (currentDeviceId) {
    await eraumaApi.removePushToken(currentDeviceId);
  }
}

export function listenForStoryNotifications(onStory: (storyId: string) => void) {
  const open = async (response: Notifications.NotificationResponse | null) => {
    const storyId = response?.notification.request.content.data?.storyId;
    if (typeof storyId === 'string') {
      onStory(storyId);
      await Notifications.clearLastNotificationResponseAsync();
    }
  };
  Notifications.getLastNotificationResponseAsync().then(open).catch(() => undefined);
  const subscription = Notifications.addNotificationResponseReceivedListener(open);
  return () => subscription.remove();
}
