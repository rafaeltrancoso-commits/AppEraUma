import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { User } from '../types/api';
import { eraumaApi } from '../services/eraumaApi';
import { markSessionAuthenticated, setSessionExpiredHandler } from '../services/api';
import { clearSession, getToken, getUserJson, saveSession } from '../services/tokenStorage';
import { unregisterPushNotifications } from '../services/pushNotifications';

type AuthContextValue = {
  user: User | null;
  loading: boolean;
  sessionExpiredMessage: string;
  signIn: (email: string, password: string) => Promise<void>;
  signUp: (name: string, email: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

function parseStoredUser(userJson: string): User | null {
  try {
    const storedUser = JSON.parse(userJson) as Partial<User>;
    if (
      typeof storedUser.id === 'string'
      && typeof storedUser.name === 'string'
      && typeof storedUser.email === 'string'
    ) {
      return storedUser as User;
    }
  } catch {
    return null;
  }

  return null;
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [sessionExpiredMessage, setSessionExpiredMessage] = useState('');

  useEffect(() => setSessionExpiredHandler(() => {
    setSessionExpiredMessage('Sua sessão expirou. Entre novamente para continuar.');
    setUser(null);
  }), []);

  useEffect(() => {
    let mounted = true;

    async function restore() {
      try {
        const [token, userJson] = await Promise.all([getToken(), getUserJson()]);
        if (!token || !userJson) {
          await clearSession();
          if (mounted) {
            setUser(null);
          }
          return;
        }

        const storedUser = parseStoredUser(userJson);
        if (!storedUser) {
          await clearSession();
          if (mounted) {
            setUser(null);
          }
          return;
        }

        if (mounted) {
          markSessionAuthenticated();
          setUser(storedUser);
        }
      } catch {
        await clearSession();
        if (mounted) {
          setUser(null);
        }
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    }
    restore();

    return () => {
      mounted = false;
    };
  }, []);

  const value = useMemo<AuthContextValue>(() => ({
    user,
    loading,
    sessionExpiredMessage,
    async signIn(email, password) {
      setSessionExpiredMessage('');
      const response = await eraumaApi.login({ email, password });
      await saveSession(response.accessToken, JSON.stringify(response.user));
      markSessionAuthenticated();
      setUser(response.user);
    },
    async signUp(name, email, password) {
      setSessionExpiredMessage('');
      await eraumaApi.register({ name, email, password });
      const response = await eraumaApi.login({ email, password });
      await saveSession(response.accessToken, JSON.stringify(response.user));
      markSessionAuthenticated();
      setUser(response.user);
    },
    async signOut() {
      try {
        await unregisterPushNotifications();
      } catch {
        // O logout local não pode depender da disponibilidade da rede.
      }
      await clearSession();
      setSessionExpiredMessage('');
      setUser(null);
    },
  }), [loading, sessionExpiredMessage, user]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}
