import React, { useEffect, useState } from 'react';
import { Image, ImageStyle, Platform, StyleProp, StyleSheet, Text, View } from 'react-native';
import { apiContentUrl } from '../services/api';
import { getToken } from '../services/tokenStorage';
import { MomentPhoto } from '../types/api';
import { theme } from '../theme/tokens';

type Props = {
  photo: MomentPhoto;
  style: StyleProp<ImageStyle>;
};

export function AuthenticatedMomentPhoto({ photo, style }: Props) {
  const [token, setToken] = useState<string>();
  const [objectUrl, setObjectUrl] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    let localObjectUrl: string | undefined;
    const contentUrl = apiContentUrl(`/api/moment-photos/${photo.id}/content`);
    setLoading(true);
    setError('');
    setObjectUrl(undefined);

    async function load() {
      const authToken = await getToken();
      if (cancelled) {
        return;
      }
      setToken(authToken ?? undefined);
      if (!contentUrl) {
        setError('Não foi possível carregar esta foto.');
        return;
      }
      if (Platform.OS !== 'web') {
        return;
      }
      const response = await fetch(contentUrl, { headers: authToken ? { Authorization: `Bearer ${authToken}` } : undefined });
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const blob = await response.blob();
      localObjectUrl = URL.createObjectURL(blob);
      if (!cancelled) {
        setObjectUrl(localObjectUrl);
      }
    }

    load().catch(() => {
      if (!cancelled) {
        setError('Não foi possível carregar esta foto.');
      }
    }).finally(() => {
      if (!cancelled) {
        setLoading(false);
      }
    });

    return () => {
      cancelled = true;
      if (localObjectUrl) {
        URL.revokeObjectURL(localObjectUrl);
      }
    };
  }, [photo.id]);

  if (loading) {
    return <PhotoMessage style={style} text="Carregando foto..." />;
  }
  if (error) {
    return <PhotoMessage style={style} text={error} />;
  }

  const uri = Platform.OS === 'web' ? objectUrl : apiContentUrl(`/api/moment-photos/${photo.id}/content`);
  if (!uri) {
    return <PhotoMessage style={style} text="Não foi possível carregar esta foto." />;
  }

  return <Image source={{ uri, headers: token ? { Authorization: `Bearer ${token}` } : undefined }} style={style} resizeMode="cover" />;
}

function PhotoMessage({ style, text }: { style: StyleProp<ImageStyle>; text: string }) {
  return (
    <View style={[styles.placeholder, style]}>
      <Text style={styles.placeholderText}>{text}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  placeholder: { alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: theme.colors.border },
  placeholderText: { color: theme.colors.muted, textAlign: 'center', fontWeight: '700', padding: theme.spacing.md },
});
