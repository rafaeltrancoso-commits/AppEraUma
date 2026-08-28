import * as ImagePicker from 'expo-image-picker';
import React, { useState } from 'react';
import { Alert, Pressable, StyleSheet, Text, View } from 'react-native';
import { AppButton } from '../components/AppButton';
import { AppTextInput } from '../components/AppTextInput';
import { Screen } from '../components/Screen';
import { eraumaApi } from '../services/eraumaApi';
import { ChildProfile, Family, Moment, MomentPayload } from '../types/api';
import { theme } from '../theme/tokens';
import { applyBrazilianDateMask, dateOnlyFromLocalDate, formatDateForApi, formatDateForDisplay } from '../utils/dateFormat';

const MAX_MOMENT_PHOTOS = 10;

type SelectedPhoto = { uri: string; name?: string; type?: string; size?: number };

type Props = {
  family: Family;
  childrenProfiles: ChildProfile[];
  moment?: Moment;
  onCancel: () => void;
  onSaved: (moment: Moment) => void;
};

export function MomentFormScreen({ family, childrenProfiles, moment, onCancel, onSaved }: Props) {
  const now = new Date();
  const [title, setTitle] = useState(moment?.title ?? '');
  const [description, setDescription] = useState(moment?.description ?? '');
  const [date, setDate] = useState(formatDateForDisplay(moment?.occurredAt?.slice(0, 10) ?? dateOnlyFromLocalDate(now)));
  const [time, setTime] = useState(moment?.occurredAt?.slice(11, 16) ?? `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`);
  const [locationName, setLocationName] = useState(moment?.locationName ?? '');
  const [selectedChildren, setSelectedChildren] = useState<string[]>(moment?.children.map(child => child.id) ?? []);
  const [participants, setParticipants] = useState<string[]>(moment?.participants.map(participant => participant.name) ?? []);
  const [participantName, setParticipantName] = useState('');
  const [photos, setPhotos] = useState<SelectedPhoto[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  function toggleChild(childId: string) {
    setSelectedChildren(current => current.includes(childId) ? current.filter(id => id !== childId) : [...current, childId]);
  }

  function updateDate(value: string) {
    setDate(applyBrazilianDateMask(value));
  }

  function addParticipant() {
    if (participantName.trim()) {
      setParticipants(current => [...current, participantName.trim()]);
      setParticipantName('');
    }
  }

  async function pickPhotos() {
    if (loading) {
      return;
    }
    const existingPhotosCount = moment?.photos.length ?? 0;
    const maxSelectablePhotos = MAX_MOMENT_PHOTOS - existingPhotosCount;
    const remainingSlots = maxSelectablePhotos - photos.length;
    if (remainingSlots <= 0) {
      Alert.alert('Limite de fotos', `Este momento pode ter até ${MAX_MOMENT_PHOTOS} fotos.`);
      return;
    }
    const result = await ImagePicker.launchImageLibraryAsync({ allowsMultipleSelection: true, mediaTypes: ['images'], quality: 0.85 });
    if (result.canceled) {
      return;
    }
    const picked = result.assets.map((asset, index) => ({
      uri: asset.uri,
      name: asset.fileName ?? `momento-${Date.now()}-${index}.${extensionForMimeType(asset.mimeType)}`,
      type: asset.mimeType,
      size: asset.fileSize,
    }));
    setPhotos(current => [...current, ...picked].slice(0, maxSelectablePhotos));
  }

  async function submit() {
    if (loading) {
      return;
    }
    setError('');
    if (!title.trim()) {
      setError('Dê um título para esse momento.');
      return;
    }
    if (!date.trim()) {
      setError('Informe a data do momento.');
      return;
    }
    const dateForApi = formatDateForApi(date);
    if (!dateForApi) {
      setError('Informe uma data válida.');
      return;
    }
    setLoading(true);
    try {
      const payload: MomentPayload = {
        title,
        description: description || undefined,
        occurredAt: `${dateForApi}T${time || '00:00'}:00`,
        locationName: locationName || undefined,
        childIds: selectedChildren,
        participants: participants.map(name => ({ name, participantType: 'ADULT' })),
      };
      const saved = moment ? await eraumaApi.updateMoment(moment.id, payload) : await eraumaApi.createMoment(family.id, payload);
      if (photos.length > 0) {
        try {
          await eraumaApi.uploadMomentPhotos(saved.id, photos);
        } catch (uploadError) {
          if (__DEV__) {
            console.warn('moment_photo_upload_failed', {
              path: `/moments/${saved.id}/photos`,
              status: uploadError instanceof Error && 'status' in uploadError ? uploadError.status : undefined,
              message: uploadError instanceof Error ? uploadError.message : 'upload failed',
            });
          }
          Alert.alert('Momento salvo', 'Momento salvo, mas não foi possível enviar uma das fotos.');
        }
      }
      onSaved(await eraumaApi.moment(saved.id));
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'Não foi possível salvar o momento.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Screen>
      <Text style={styles.eyebrow}>❤️ Momentos</Text>
      <Text style={styles.title}>{moment ? 'Editar lembrança' : 'Guardar um momento'}</Text>
      <AppTextInput label="Título *" value={title} onChangeText={setTitle} placeholder="Meu primeiro passeio de bicicleta" />
      <AppTextInput label="Descrição" value={description} onChangeText={setDescription} multiline placeholder="O que aconteceu?" />
      <View style={styles.row}>
        <View style={styles.flex}><AppTextInput label="Data *" value={date} onChangeText={updateDate} placeholder="DD/MM/AAAA" keyboardType="number-pad" /></View>
        <View style={styles.flex}><AppTextInput label="Hora" value={time} onChangeText={setTime} placeholder="15:00" /></View>
      </View>
      <AppTextInput label="Local" value={locationName} onChangeText={setLocationName} placeholder="Parque" />
      <Text style={styles.section}>Quem viveu esse momento?</Text>
      <View style={styles.chips}>
        {childrenProfiles.map(child => (
          <Pressable key={child.id} style={[styles.chip, selectedChildren.includes(child.id) && styles.chipSelected]} onPress={() => toggleChild(child.id)}>
            <Text style={styles.chipText}>{child.nickname || child.name} {selectedChildren.includes(child.id) ? '✓' : ''}</Text>
          </Pressable>
        ))}
      </View>
      <Text style={styles.section}>Quem estava junto?</Text>
      {participants.map(name => (
        <Pressable key={name} onPress={() => setParticipants(current => current.filter(item => item !== name))}>
          <Text style={styles.person}>{name} ×</Text>
        </Pressable>
      ))}
      <View style={styles.row}>
        <View style={styles.flex}><AppTextInput label="Participante" value={participantName} onChangeText={setParticipantName} placeholder="Papai" /></View>
        <AppButton title="+" onPress={addParticipant} variant="secondary" />
      </View>
      <AppButton title={`Adicionar fotos (${photos.length})`} onPress={pickPhotos} variant="secondary" disabled={loading || (moment?.photos.length ?? 0) + photos.length >= MAX_MOMENT_PHOTOS} />
      {photos.length > 0 ? <Text style={styles.photoHint}>{photos.length} foto{photos.length > 1 ? 's' : ''} selecionada{photos.length > 1 ? 's' : ''} para envio.</Text> : null}
      {error ? <Text style={styles.error}>{error}</Text> : null}
      <AppButton title={moment ? 'Salvar alterações' : 'Salvar momento'} onPress={submit} loading={loading} />
      <AppButton title="Cancelar" onPress={onCancel} variant="secondary" disabled={loading} />
    </Screen>
  );
}

function extensionForMimeType(mimeType?: string | null) {
  if (mimeType === 'image/png') {
    return 'png';
  }
  if (mimeType === 'image/webp') {
    return 'webp';
  }
  return 'jpg';
}

const styles = StyleSheet.create({
  eyebrow: { color: theme.colors.secondary, fontWeight: '900', textAlign: 'center' },
  title: { fontSize: 28, fontWeight: '900', color: theme.colors.primary, textAlign: 'center' },
  row: { flexDirection: 'row', gap: theme.spacing.sm, alignItems: 'flex-end' },
  flex: { flex: 1 },
  section: { color: theme.colors.primary, fontWeight: '800', fontSize: 17, marginTop: theme.spacing.sm },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: theme.spacing.sm },
  chip: { backgroundColor: theme.colors.surface, borderColor: theme.colors.border, borderWidth: 1, borderRadius: theme.radius.md, padding: theme.spacing.md },
  chipSelected: { backgroundColor: theme.colors.secondary },
  chipText: { color: theme.colors.primary, fontWeight: '800' },
  person: { backgroundColor: theme.colors.surface, padding: theme.spacing.md, borderRadius: theme.radius.md, color: theme.colors.text },
  photoHint: { color: theme.colors.muted, textAlign: 'center' },
  error: { color: theme.colors.error, textAlign: 'center' },
});
