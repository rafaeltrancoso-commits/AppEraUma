import { Platform } from 'react-native';
import * as SecureStore from 'expo-secure-store';

const TOKEN_KEY = 'erauma_access_token';
const USER_KEY = 'erauma_user';

function isWebStorageAvailable() {
  return Platform.OS === 'web' && typeof localStorage !== 'undefined';
}

async function setItem(key: string, value: string) {
  if (isWebStorageAvailable()) {
    localStorage.setItem(key, value);
    return;
  }

  if (Platform.OS === 'web') {
    throw new Error('Armazenamento local indisponível no navegador.');
  }

  await SecureStore.setItemAsync(key, value);
}

async function getItem(key: string) {
  if (isWebStorageAvailable()) {
    return localStorage.getItem(key);
  }

  if (Platform.OS === 'web') {
    return null;
  }

  return SecureStore.getItemAsync(key);
}

async function deleteItem(key: string) {
  if (isWebStorageAvailable()) {
    localStorage.removeItem(key);
    return;
  }

  if (Platform.OS === 'web') {
    return;
  }

  await SecureStore.deleteItemAsync(key);
}

export async function saveSession(token: string, userJson: string) {
  await setItem(TOKEN_KEY, token);
  await setItem(USER_KEY, userJson);
}

export async function getToken() {
  return getItem(TOKEN_KEY);
}

export async function getUserJson() {
  return getItem(USER_KEY);
}

export async function clearSession() {
  await deleteItem(TOKEN_KEY);
  await deleteItem(USER_KEY);
}
