import { Platform } from 'react-native';
import {
  AuthResponse,
  ChildPayload,
  ChildProfile,
  Family,
  Moment,
  MomentCalendarDay,
  MomentPayload,
  MomentPhoto,
  PageResponse,
  PasswordResetResponse,
  ResetPasswordResponse,
  Story,
  StoryImage,
  StoryGenerationRequest,
  StoryGenerationMode,
  StoryStyle,
  StoryUpdatePayload,
  User,
} from '../types/api';
import { apiRequest } from './api';

type MomentUploadPhoto = {
  uri: string;
  name?: string;
  type?: string;
  size?: number;
};

type ReactNativeFormDataFile = {
  uri: string;
  name: string;
  type: string;
};

type WebFormDataFile = {
  uri: string;
  name: string;
  type: string;
  blob: Blob;
};

export const eraumaApi = {
  register: (data: { name: string; email: string; password: string }) =>
    apiRequest<User>('/auth/register', { method: 'POST', body: data, auth: false }),
  login: (data: { email: string; password: string }) =>
    apiRequest<AuthResponse>('/auth/login', { method: 'POST', body: data, auth: false }),
  forgotPassword: (email: string) =>
    apiRequest<PasswordResetResponse>('/auth/forgot-password', { method: 'POST', body: { email }, auth: false }),
  resetPassword: (data: { token: string; newPassword: string; confirmPassword: string }) =>
    apiRequest<ResetPasswordResponse>('/auth/reset-password', { method: 'POST', body: data, auth: false }),
  families: () => apiRequest<Family[]>('/families/me'),
  createFamily: (name: string) => apiRequest<Family>('/families', { method: 'POST', body: { name } }),
  children: (familyId: string) => apiRequest<ChildProfile[]>(`/families/${familyId}/children`),
  createChild: (familyId: string, data: ChildPayload) =>
    apiRequest<ChildProfile>(`/families/${familyId}/children`, { method: 'POST', body: data }),
  updateChild: (childId: string, data: ChildPayload) =>
    apiRequest<ChildProfile>(`/children/${childId}`, { method: 'PUT', body: data }),
  moments: (familyId: string, page = 0, childId?: string, from?: string, to?: string) => {
    const params = new URLSearchParams({ page: String(page), size: '30' });
    if (childId) {params.append('childId', childId);}
    if (from) {params.append('from', from);}
    if (to) {params.append('to', to);}
    return apiRequest<PageResponse<Moment>>(`/families/${familyId}/moments?${params.toString()}`);
  },
  momentCalendar: (familyId: string, year: number, month: number, childId?: string) => {
    const params = new URLSearchParams({ year: String(year), month: String(month) });
    if (childId) {params.append('childId', childId);}
    return apiRequest<MomentCalendarDay[]>(`/families/${familyId}/moments/calendar?${params.toString()}`);
  },
  moment: (momentId: string) => apiRequest<Moment>(`/moments/${momentId}`),
  createMoment: (familyId: string, data: MomentPayload) =>
    apiRequest<Moment>(`/families/${familyId}/moments`, { method: 'POST', body: data }),
  updateMoment: (momentId: string, data: MomentPayload) =>
    apiRequest<Moment>(`/moments/${momentId}`, { method: 'PUT', body: data }),
  favoriteMoment: (momentId: string, favorite: boolean) =>
    apiRequest<Moment>(`/moments/${momentId}/favorite`, { method: 'PATCH', body: { favorite } }),
  deleteMoment: (momentId: string) => apiRequest<void>(`/moments/${momentId}`, { method: 'DELETE' }),
  uploadMomentPhotos: async (momentId: string, photos: MomentUploadPhoto[]) => {
    const form = new FormData();
    for (const [index, photo] of photos.entries()) {
      if (Platform.OS === 'web') {
        const uploadPhoto = await normalizeWebMomentUploadPhoto(photo, index);
        logMomentPhotoUploadStart(momentId, uploadPhoto, photo.size ?? uploadPhoto.blob.size);
        form.append('files', new File([uploadPhoto.blob], uploadPhoto.name, { type: uploadPhoto.type }));
      } else {
        const uploadPhoto = normalizeNativeMomentUploadPhoto(photo, index);
        logMomentPhotoUploadStart(momentId, uploadPhoto, photo.size);
        appendReactNativeFile(form, 'files', uploadPhoto);
      }
    }
    const uploaded = await apiRequest<MomentPhoto[]>(`/moments/${momentId}/photos`, { method: 'POST', body: form, multipart: true, timeoutMs: 60000 });
    if (__DEV__) {
      console.info('moment_photo_upload_result', { status: 201, photoCount: uploaded.length });
    }
    return uploaded;
  },
  generateStory: (familyId: string, data: StoryGenerationRequest) =>
    apiRequest<Story>(`/families/${familyId}/stories/generate`, { method: 'POST', body: data, timeoutMs: data.generationMode === 'ILLUSTRATED' ? 210000 : 90000 }),
  stories: (familyId: string, page = 0, childId?: string, favorite?: boolean, style?: StoryStyle, generationMode?: StoryGenerationMode, from?: string, to?: string) => {
    const params = new URLSearchParams({ page: String(page), size: '20' });
    if (childId) {
      params.append('childId', childId);
    }
    if (favorite !== undefined) {
      params.append('favorite', String(favorite));
    }
    if (style) {
      params.append('style', style);
    }
    if (generationMode) {
      params.append('generationMode', generationMode);
    }
    if (from) {
      params.append('from', from);
    }
    if (to) {
      params.append('to', to);
    }
    return apiRequest<PageResponse<Story>>(`/families/${familyId}/stories?${params.toString()}`);
  },
  story: (storyId: string) => apiRequest<Story>(`/stories/${storyId}`),
  updateStory: (storyId: string, data: StoryUpdatePayload) =>
    apiRequest<Story>(`/stories/${storyId}`, { method: 'PUT', body: data }),
  favoriteStory: (storyId: string, favorite: boolean) =>
    apiRequest<Story>(`/stories/${storyId}/favorite`, { method: 'PATCH', body: { favorite } }),
  retryStoryImage: (imageId: string) =>
    apiRequest<StoryImage>(`/story-images/${imageId}/retry`, { method: 'POST' }),
  deleteStory: (storyId: string) => apiRequest<void>(`/stories/${storyId}`, { method: 'DELETE' }),
};

async function normalizeWebMomentUploadPhoto(photo: MomentUploadPhoto, index: number): Promise<WebFormDataFile> {
  const response = await fetch(photo.uri);
  const blob = await response.blob();
  const name = resolveFilename(photo.name, photo.type || blob.type, index);
  return {
    uri: photo.uri,
    name,
    type: resolveMimeType(name, photo.type || blob.type),
    blob,
  };
}

function normalizeNativeMomentUploadPhoto(photo: MomentUploadPhoto, index: number): ReactNativeFormDataFile {
  const name = resolveFilename(photo.name, photo.type, index);
  return {
    uri: photo.uri,
    name,
    type: resolveMimeType(name, photo.type),
  };
}

function resolveFilename(name: string | undefined, mimeType: string | undefined, index: number) {
  if (name?.trim()) {
    return name.trim();
  }
  const extension = extensionForMimeType(mimeType) ?? 'jpg';
  return `moment-photo-${Date.now()}-${index}.${extension}`;
}

function resolveMimeType(filename: string, mimeType: string | undefined) {
  if (mimeType?.startsWith('image/') && mimeType !== 'application/octet-stream') {
    return mimeType;
  }
  const extension = filename.split('.').pop()?.toLowerCase();
  if (extension === 'png') {
    return 'image/png';
  }
  if (extension === 'webp') {
    return 'image/webp';
  }
  return 'image/jpeg';
}

function extensionForMimeType(mimeType: string | undefined) {
  if (mimeType === 'image/png') {
    return 'png';
  }
  if (mimeType === 'image/webp') {
    return 'webp';
  }
  return 'jpg';
}

function appendReactNativeFile(form: FormData, fieldName: string, file: ReactNativeFormDataFile) {
  (form as unknown as { append: (name: string, value: ReactNativeFormDataFile) => void }).append(fieldName, file);
}

function logMomentPhotoUploadStart(momentId: string, photo: ReactNativeFormDataFile | WebFormDataFile, fileSize?: number) {
  if (!__DEV__) {
    return;
  }
  console.info('moment_photo_upload_start', {
    platform: Platform.OS,
    momentId,
    uriScheme: photo.uri.split(':', 1)[0] || 'unknown',
    fileName: photo.name,
    mimeType: photo.type,
    fileSize,
  });
}
