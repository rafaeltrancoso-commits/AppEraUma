import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { User } from '../types/api';
import { eraumaApi } from '../services/eraumaApi';
import { clearSession, getToken, getUserJson, saveSession } from '../services/tokenStorage';

type AuthContextValue = {
  user: User | null;
  loading: boolean;
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
    async signIn(email, password) {
      const response = await eraumaApi.login({ email, password });
      await saveSession(response.accessToken, JSON.stringify(response.user));
      setUser(response.user);
    },
    async signUp(name, email, password) {
      await eraumaApi.register({ name, email, password });
      const response = await eraumaApi.login({ email, password });
      await saveSession(response.accessToken, JSON.stringify(response.user));
      setUser(response.user);
    },
    async signOut() {
      await clearSession();
      setUser(null);
    },
  }), [loading, user]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}
