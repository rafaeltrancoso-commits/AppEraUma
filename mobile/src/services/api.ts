import { Platform } from 'react-native';
import { clearSession, getToken } from './tokenStorage';

const API_URL = process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:8080/api';

if (__DEV__) {
  console.info(`API Base URL: ${API_URL}`);
}

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

function getHttpErrorMessage(status: number, fallback?: string) {
  if (fallback) {
    return fallback;
  }

  if (status === 400) {
    return 'Revise os dados informados.';
  }
  if (status === 401) {
    return 'SessÃ£o expirada. Entre novamente.';
  }
  if (status === 403) {
    return 'VocÃª nÃ£o tem permissÃ£o para acessar este recurso.';
  }
  if (status === 404) {
    return 'Recurso nÃ£o encontrado.';
  }
  if (status >= 500) {
    return 'Servidor indisponÃ­vel no momento. Tente novamente.';
  }

  return 'Erro ao comunicar com o servidor.';
}

function getNetworkErrorMessage(error: unknown) {
  if (error instanceof Error && error.name === 'AbortError') {
    return 'Tempo esgotado ao conectar com o servidor.';
  }

  if (error instanceof TypeError) {
    return 'Falha de rede. Verifique se a API está acessível.';
  }

  return 'NÃ£o foi possÃ­vel conectar ao servidor.';
}

type Options = {
  method?: string;
  body?: unknown;
  auth?: boolean;
  multipart?: boolean;
  timeoutMs?: number;
};

export async function apiRequest<T>(path: string, options: Options = {}): Promise<T> {
  const isFormData = typeof FormData !== 'undefined' && options.body instanceof FormData;
  const isMultipart = options.multipart || isFormData;
  const headers: Record<string, string> = isMultipart ? {} : { 'Content-Type': 'application/json' };
  if (options.auth !== false) {
    const token = await getToken();
    if (token) {
      headers.Authorization = `Bearer ${token}`;
    }
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), options.timeoutMs ?? 10000);
  try {
    const response = await fetch(`${API_URL}${path}`, {
      method: options.method ?? 'GET',
      headers,
      body: options.body ? (isMultipart ? options.body as BodyInit : JSON.stringify(options.body)) : undefined,
      signal: controller.signal,
    });
    const text = await response.text();
    const data = text ? JSON.parse(text) : null;
    if (!response.ok) {
      if (__DEV__) {
        console.warn('api_request_failed', { path, status: response.status, code: data?.code, message: data?.message });
      }
      if (response.status === 401) {
        await clearSession();
      }
      throw new ApiError(response.status, getHttpErrorMessage(response.status, data?.message));
    }
    return data as T;
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }

    throw new ApiError(0, getNetworkErrorMessage(error));
  } finally {
    clearTimeout(timeout);
  }
}

export const apiBaseUrl = API_URL;

export function apiContentUrl(path?: string | null) {
  if (!path) {
    return undefined;
  }
  if (/^https?:\/\//i.test(path)) {
    return path;
  }
  const base = API_URL.replace(/\/+$/, '');
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  if (base.endsWith('/api') && normalizedPath.startsWith('/api/')) {
    return `${base.slice(0, -4)}${normalizedPath}`;
  }
  if (!base.endsWith('/api') && !normalizedPath.startsWith('/api/')) {
    return `${base}/api${normalizedPath}`;
  }
  return `${base}${normalizedPath}`;
}

export function apiContentUrlHost(path?: string | null) {
  const url = apiContentUrl(path);
  if (!url) {
    return undefined;
  }
  try {
    return new URL(url).host;
  } catch {
    return undefined;
  }
}

if (__DEV__ && Platform.OS !== 'web') {
  const host = apiContentUrlHost('/api/health');
  if (host?.startsWith('localhost') || host?.startsWith('127.0.0.1')) {
    console.warn('api_url_native_localhost', { urlHost: host });
  }
}
