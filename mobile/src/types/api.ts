export type User = {
  id: string;
  name: string;
  email: string;
};

export type AuthResponse = {
  accessToken: string;
  tokenType: 'Bearer';
  user: User;
};

export type PasswordResetResponse = {
  message: string;
  resetToken?: string | null;
};

export type ResetPasswordResponse = {
  message: string;
};

export type Family = {
  id: string;
  name: string;
};

export type ChildProfile = {
  id: string;
  familyId: string;
  name: string;
  birthDate?: string;
  nickname?: string;
  favoriteAnimal?: string;
  avatarUrl?: string;
  visualPresentation?: 'BOY' | 'GIRL' | 'UNSPECIFIED';
  skinTone?: 'VERY_LIGHT' | 'LIGHT' | 'MEDIUM' | 'BROWN' | 'DARK' | 'UNSPECIFIED';
  hairColor?: string;
  hairLength?: string;
  hairTexture?: 'STRAIGHT' | 'WAVY' | 'CURLY' | 'COILY' | 'OTHER_OR_UNSPECIFIED';
  eyeColor?: string;
  specialFeatures?: string;
};

export type ChildPayload = {
  name: string;
  nickname?: string;
  birthDate?: string;
  favoriteAnimal?: string;
  visualPresentation?: ChildProfile['visualPresentation'];
  skinTone?: ChildProfile['skinTone'];
  hairColor?: string;
  hairLength?: string;
  hairTexture?: ChildProfile['hairTexture'];
  eyeColor?: string;
  specialFeatures?: string;
};

export type MomentParticipant = {
  id?: string;
  name: string;
  participantType: 'ADULT' | 'OTHER';
};

export type MomentPhoto = {
  id: string;
  originalFilename?: string;
  contentType: string;
  sizeBytes: number;
  sortOrder: number;
  createdAt: string;
};

export type MomentStory = {
  id: string;
  title: string;
  summary?: string;
  mainCharacterName?: string;
  secondCharacterName?: string;
  theme?: string;
  createdAt: string;
  images: StoryImage[];
};

export type Moment = {
  id: string;
  familyId: string;
  title: string;
  description?: string;
  occurredAt: string;
  locationName?: string;
  favorite: boolean;
  children: Pick<ChildProfile, 'id' | 'name' | 'nickname'>[];
  participants: MomentParticipant[];
  photos: MomentPhoto[];
  stories: MomentStory[];
  createdAt: string;
  updatedAt: string;
};

export type MomentCalendarDay = {
  date: string;
  count: number;
};

export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
};

export type MomentPayload = {
  title: string;
  description?: string;
  occurredAt: string;
  locationName?: string;
  childIds?: string[];
  participants?: { name: string; participantType: 'ADULT' | 'OTHER' }[];
};

export type StoryStyle = 'ADVENTURE' | 'FUNNY' | 'EDUCATIONAL' | 'FANTASY' | 'BEDTIME';
export type StoryGenerationMode = 'TEXT_ONLY' | 'ILLUSTRATED';

export type StoryLength = 'SHORT' | 'MEDIUM' | 'LONG';

export type StoryChapter = {
  id?: string;
  number: number;
  title: string;
  content: string;
};

export type StoryImage = {
  id: string;
  type: 'COVER' | 'SCENE';
  chapterId?: string | null;
  status: 'PENDING' | 'GENERATED' | 'FAILED';
  contentUrl?: string | null;
  model?: string;
  size?: string;
  quality?: string;
  sortOrder: number;
};

export type Story = {
  id: string;
  title: string;
  summary: string;
  theme: string;
  place?: string;
  favoriteAnimal?: string;
  style: StoryStyle;
  length: StoryLength;
  favorite: boolean;
  generationType: 'MOCK' | 'AI';
  mainCharacterName?: string;
  secondCharacterName?: string;
  child?: Pick<ChildProfile, 'id' | 'name'> | null;
  sourceMoment?: Pick<Moment, 'id' | 'title'>;
  chapters: StoryChapter[];
  images: StoryImage[];
  createdAt: string;
};

export type StoryGenerationRequest = {
  childId?: string;
  sourceMomentId?: string;
  mainCharacterName?: string;
  secondCharacterName?: string;
  theme: string;
  place?: string;
  favoriteAnimal?: string;
  style: StoryStyle;
  length: StoryLength;
  generationMode?: StoryGenerationMode;
};

export type StoryUpdatePayload = {
  title?: string;
  favorite?: boolean;
};
