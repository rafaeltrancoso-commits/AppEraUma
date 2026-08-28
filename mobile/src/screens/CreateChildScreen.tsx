import React, { useState } from 'react';
import { Pressable, Text, StyleSheet, View } from 'react-native';
import { AppButton } from '../components/AppButton';
import { AppTextInput } from '../components/AppTextInput';
import { Screen } from '../components/Screen';
import { eraumaApi } from '../services/eraumaApi';
import { ChildPayload, ChildProfile, Family } from '../types/api';
import { theme } from '../theme/tokens';
import { applyBrazilianDateMask, formatDateForApi } from '../utils/dateFormat';

type Props = {
  family: Family;
  child?: ChildProfile;
  onCreated?: (child: ChildProfile) => void;
  onSaved?: (child: ChildProfile) => void;
  onCancel?: () => void;
};

function formatDateForInput(value?: string) {
  if (!value) {
    return '';
  }
  const [year, month, day] = value.split('-');
  return day && month && year ? `${day}/${month}/${year}` : '';
}

export function CreateChildScreen({ family, child, onCreated, onSaved, onCancel }: Props) {
  const [name, setName] = useState(child?.name ?? '');
  const [nickname, setNickname] = useState(child?.nickname ?? '');
  const [birthDate, setBirthDate] = useState(formatDateForInput(child?.birthDate));
  const [visualPresentation, setVisualPresentation] = useState<ChildPayload['visualPresentation']>(child?.visualPresentation ?? 'UNSPECIFIED');
  const [skinTone, setSkinTone] = useState<ChildPayload['skinTone']>(child?.skinTone ?? 'UNSPECIFIED');
  const [hairColor, setHairColor] = useState(child?.hairColor ?? '');
  const [hairLength, setHairLength] = useState(child?.hairLength ?? '');
  const [hairTexture, setHairTexture] = useState<ChildPayload['hairTexture']>(child?.hairTexture ?? 'OTHER_OR_UNSPECIFIED');
  const [eyeColor, setEyeColor] = useState(child?.eyeColor ?? '');
  const [specialFeatures, setSpecialFeatures] = useState(child?.specialFeatures ?? '');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  function updateBirthDate(value: string) {
    setBirthDate(applyBrazilianDateMask(value));
    setError('');
  }

  async function submit() {
    setError('');
    if (!name.trim()) {
      setError('Informe o nome da criança.');
      return;
    }
    const parsedBirthDate = birthDate ? formatDateForApi(birthDate) : null;
    if (birthDate && !parsedBirthDate) {
      setError('Informe uma data de nascimento válida.');
      return;
    }
    setLoading(true);
    try {
      const payload: ChildPayload = {
        name: name.trim(),
        nickname: nickname.trim() || undefined,
        birthDate: parsedBirthDate ?? undefined,
        visualPresentation,
        skinTone,
        hairColor: hairColor.trim() || undefined,
        hairLength: hairLength.trim() || undefined,
        hairTexture,
        eyeColor: eyeColor.trim() || undefined,
        specialFeatures: specialFeatures.trim() || undefined,
      };
      const saved = child ? await eraumaApi.updateChild(child.id, payload) : await eraumaApi.createChild(family.id, payload);
      child ? onSaved?.(saved) : onCreated?.(saved);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'Não foi possível salvar a criança.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Screen>
      {onCancel ? <Pressable onPress={onCancel} disabled={loading}><Text style={styles.back}>← Voltar</Text></Pressable> : null}
      <Text style={styles.title}>{child ? 'Editar criança' : 'Quem vai viver essas histórias?'}</Text>
      <AppTextInput label="Nome" value={name} onChangeText={value => { setName(value); setError(''); }} />
      <AppTextInput label="Apelido" value={nickname} onChangeText={setNickname} />
      <AppTextInput label="Data de nascimento" placeholder="DD/MM/AAAA" value={birthDate} onChangeText={updateBirthDate} keyboardType="number-pad" />
      <View style={styles.visualSection}>
        <Text style={styles.sectionTitle}>Como vamos imaginar {name.trim() || 'a criança'} nas histórias? ✨</Text>
        <Text style={styles.helper}>Conte só o que quiser. Essas informações ajudam a deixar as ilustrações mais parecidas com a criança.</Text>
        <Text style={styles.label}>Como gostaria que aparecesse nas histórias?</Text>
        <View style={styles.options}>
          <Option label="Menino" selected={visualPresentation === 'BOY'} onPress={() => setVisualPresentation('BOY')} />
          <Option label="Menina" selected={visualPresentation === 'GIRL'} onPress={() => setVisualPresentation('GIRL')} />
          <Option label="Prefiro não informar" selected={visualPresentation === 'UNSPECIFIED'} onPress={() => setVisualPresentation('UNSPECIFIED')} />
        </View>
        <Text style={styles.label}>Como é o tom de pele?</Text>
        <View style={styles.options}>
          <Option label="Muito claro" selected={skinTone === 'VERY_LIGHT'} onPress={() => setSkinTone('VERY_LIGHT')} />
          <Option label="Claro" selected={skinTone === 'LIGHT'} onPress={() => setSkinTone('LIGHT')} />
          <Option label="Médio" selected={skinTone === 'MEDIUM'} onPress={() => setSkinTone('MEDIUM')} />
          <Option label="Moreno" selected={skinTone === 'BROWN'} onPress={() => setSkinTone('BROWN')} />
          <Option label="Escuro" selected={skinTone === 'DARK'} onPress={() => setSkinTone('DARK')} />
          <Option label="Prefiro não informar" selected={skinTone === 'UNSPECIFIED'} onPress={() => setSkinTone('UNSPECIFIED')} />
        </View>
        <Text style={styles.label}>Como é o cabelo?</Text>
        <AppTextInput label="Cor" value={hairColor} onChangeText={setHairColor} />
        <AppTextInput label="Comprimento" value={hairLength} onChangeText={setHairLength} />
        <View style={styles.options}>
          <Option label="Liso" selected={hairTexture === 'STRAIGHT'} onPress={() => setHairTexture('STRAIGHT')} />
          <Option label="Ondulado" selected={hairTexture === 'WAVY'} onPress={() => setHairTexture('WAVY')} />
          <Option label="Cacheado" selected={hairTexture === 'CURLY'} onPress={() => setHairTexture('CURLY')} />
          <Option label="Crespo" selected={hairTexture === 'COILY'} onPress={() => setHairTexture('COILY')} />
          <Option label="Outro / Prefiro não informar" selected={hairTexture === 'OTHER_OR_UNSPECIFIED'} onPress={() => setHairTexture('OTHER_OR_UNSPECIFIED')} />
        </View>
        <AppTextInput label="Qual é a cor dos olhos?" value={eyeColor} onChangeText={setEyeColor} />
        <AppTextInput
          label="Tem algum detalhe especial que devemos lembrar?"
          placeholder="Óculos, aparelho auditivo, cadeira de rodas, sardinhas, penteado, acessório favorito..."
          value={specialFeatures}
          onChangeText={setSpecialFeatures}
          multiline
        />
      </View>
      {error ? <Text style={styles.error}>{error}</Text> : null}
      <AppButton title={child ? 'Salvar' : 'Cadastrar'} onPress={submit} loading={loading} />
    </Screen>
  );
}

function Option({ label, selected, onPress }: { label: string; selected: boolean; onPress: () => void }) {
  return (
    <Pressable style={[styles.option, selected && styles.optionSelected]} onPress={onPress}>
      <Text style={[styles.optionText, selected && styles.optionTextSelected]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  back: { color: theme.colors.primary, fontWeight: '800' },
  title: { fontSize: 28, fontWeight: '800', color: theme.colors.primary, textAlign: 'center', marginBottom: theme.spacing.md },
  visualSection: { gap: theme.spacing.sm, marginTop: theme.spacing.md },
  sectionTitle: { color: theme.colors.primary, fontSize: 20, fontWeight: '900' },
  helper: { color: theme.colors.muted, lineHeight: 20 },
  label: { color: theme.colors.text, fontWeight: '800', marginTop: theme.spacing.sm },
  options: { flexDirection: 'row', flexWrap: 'wrap', gap: theme.spacing.sm },
  option: { borderWidth: 1, borderColor: theme.colors.border, borderRadius: theme.radius.md, paddingHorizontal: theme.spacing.md, paddingVertical: theme.spacing.sm, backgroundColor: theme.colors.surface },
  optionSelected: { borderColor: theme.colors.primary, backgroundColor: theme.colors.background },
  optionText: { color: theme.colors.text, fontWeight: '700' },
  optionTextSelected: { color: theme.colors.primary },
  error: { color: theme.colors.error, textAlign: 'center' },
});
