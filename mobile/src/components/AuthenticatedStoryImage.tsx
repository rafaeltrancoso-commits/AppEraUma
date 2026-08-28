import React, { useEffect, useState } from 'react';
import { Image, ImageStyle, Modal, Platform, Pressable, StyleProp, StyleSheet, Text, View } from 'react-native';
import { apiContentUrl, apiContentUrlHost } from '../services/api';
import { getToken } from '../services/tokenStorage';
import { StoryImage } from '../types/api';
import { theme } from '../theme/tokens';

type Props = {
  image?: StoryImage | null;
  style: StyleProp<ImageStyle>;
  resizeMode?: 'cover' | 'contain' | 'stretch' | 'repeat' | 'center';
  compact?: boolean;
  fullScreenEnabled?: boolean;
};

export function AuthenticatedStoryImage({ image, style, resizeMode = 'cover', compact = false, fullScreenEnabled = true }: Props) {
  const imageId = image?.id;
  const imageStatus = image?.status;
  const imageContentUrl = image?.contentUrl;
  const [token, setToken] = useState<string>();
  const [objectUrl, setObjectUrl] = useState<string>();
  const [loading, setLoading] = useState(imageStatus === 'GENERATED');
  const [error, setError] = useState('');
  const [viewerVisible, setViewerVisible] = useState(false);

  useEffect(() => {
    let cancelled = false;
    let localObjectUrl: string | undefined;
    setError('');
    setObjectUrl(undefined);
    setLoading(imageStatus === 'GENERATED');

    async function load() {
      if (__DEV__ && image?.model?.startsWith('mock')) {
        console.info('story_image_mock_debug', { imageId, model: image.model, status: imageStatus });
      }
      if (imageStatus !== 'GENERATED' || !imageContentUrl) {
        setLoading(false);
        return;
      }
      const authToken = await getToken();
      if (cancelled) {
        return;
      }
      setToken(authToken ?? undefined);
      const uri = apiContentUrl(imageContentUrl);
      const urlHost = apiContentUrlHost(imageContentUrl);
      if (!uri) {
        setLoading(false);
        setError('Não foi possível carregar esta ilustração.');
        return;
      }
      if (Platform.OS !== 'web') {
        if (__DEV__) {
          console.info('story_image_android_request', { imageId, urlHost, status: imageStatus });
        }
        setLoading(false);
        return;
      }
      let httpStatus = 0;
      let contentType = '';
      let bytes = 0;
      try {
        const response = await fetch(uri, { headers: authToken ? { Authorization: `Bearer ${authToken}` } : undefined });
        httpStatus = response.status;
        contentType = response.headers.get('Content-Type') ?? '';
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }
        const blob = await response.blob();
        bytes = blob.size;
        if (bytes <= 0 || !blob.type.startsWith('image/')) {
          throw new Error('Invalid image content');
        }
        localObjectUrl = URL.createObjectURL(blob);
        if (!cancelled) {
          setObjectUrl(localObjectUrl);
        }
      } catch {
        if (!cancelled) {
          setError('Não foi possível carregar esta ilustração.');
        }
      } finally {
        if (__DEV__) {
          console.info('story_image_load', { imageId, httpStatus, contentType, bytes });
        }
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    load().catch(() => {
      if (!cancelled) {
        setLoading(false);
        setError('Não foi possível carregar esta ilustração.');
      }
    });

    return () => {
      cancelled = true;
      if (localObjectUrl) {
        URL.revokeObjectURL(localObjectUrl);
      }
    };
  }, [imageId, imageStatus, imageContentUrl, image?.model]);

  if (!image) {
    return null;
  }
  if (image.status === 'PENDING') {
    return <ImageMessage style={style} compact={compact} text="Preparando ilustração..." />;
  }
  if (image.status === 'FAILED') {
    return <ImageMessage style={style} compact={compact} text="Não foi possível gerar esta ilustração." />;
  }
  if (error) {
    return <ImageMessage style={style} compact={compact} text="Não foi possível carregar esta ilustração." />;
  }
  if (loading) {
    return <ImageMessage style={style} compact={compact} text="Carregando ilustração..." />;
  }

  const uri = Platform.OS === 'web' ? objectUrl : apiContentUrl(image.contentUrl);
  if (!uri) {
    return <ImageMessage style={style} compact={compact} text="Não foi possível carregar esta ilustração." />;
  }

  const source = Platform.OS === 'web' ? { uri } : { uri, headers: token ? { Authorization: `Bearer ${token}` } : undefined };
  const imageElement = <Image source={source} style={style} resizeMode={resizeMode} onError={() => {
    if (__DEV__ && Platform.OS !== 'web') {
      console.warn('story_image_android_request', { imageId, urlHost: apiContentUrlHost(image.contentUrl), status: 'LOAD_FAILED' });
    }
    setError('Não foi possível carregar esta ilustração.');
  }} />;

  if (compact || !fullScreenEnabled) {
    return imageElement;
  }

  return (
    <>
      <Pressable onPress={() => setViewerVisible(true)} accessibilityRole="imagebutton">
        {imageElement}
      </Pressable>
      <Modal visible={viewerVisible} transparent animationType="fade" onRequestClose={() => setViewerVisible(false)}>
        <View style={styles.viewer}>
          <Pressable style={styles.viewerBackdrop} onPress={() => setViewerVisible(false)}>
            <Image source={source} style={styles.viewerImage} resizeMode="contain" />
          </Pressable>
          <Pressable style={styles.closeButton} onPress={() => setViewerVisible(false)}>
            <Text style={styles.closeText}>×</Text>
          </Pressable>
        </View>
      </Modal>
    </>
  );
}

function ImageMessage({ style, text, compact }: { style: StyleProp<ImageStyle>; text: string; compact: boolean }) {
  return (
    <View style={[styles.placeholder, style]}>
      <Text style={[styles.placeholderText, compact && styles.compactText]}>{text}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  placeholder: { alignItems: 'center', justifyContent: 'center', borderWidth: 1, borderColor: theme.colors.border },
  placeholderText: { color: theme.colors.muted, textAlign: 'center', fontWeight: '700', padding: theme.spacing.md },
  compactText: { fontSize: 12, padding: theme.spacing.sm },
  viewer: { flex: 1, backgroundColor: 'rgba(0, 0, 0, 0.88)' },
  viewerBackdrop: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: theme.spacing.md },
  viewerImage: { width: '100%', height: '100%' },
  closeButton: { position: 'absolute', top: 40, right: 24, width: 44, height: 44, borderRadius: 22, alignItems: 'center', justifyContent: 'center', backgroundColor: 'rgba(0, 0, 0, 0.45)' },
  closeText: { color: '#FFFFFF', fontSize: 34, lineHeight: 38, fontWeight: '600' },
});

