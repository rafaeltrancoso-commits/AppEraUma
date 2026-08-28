import React from 'react';
import { StatusBar } from 'react-native';
import { AuthProvider } from './src/contexts/AuthContext';
import { AppNavigator } from './src/navigation/AppNavigator';

export default function App() {
  return (
    <AuthProvider>
      <StatusBar barStyle="dark-content" />
      <AppNavigator />
    </AuthProvider>
  );
}
