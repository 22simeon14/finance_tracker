/**
 * Main Responsibility: Shared HTTP helper for backend API calls.
 *
 * Adds JSON Content-Type for string/object bodies, but not for FormData
 * (the browser must set multipart boundary). Adds Bearer token when logged in.
 * On 401 (except login), clears the stored token so the UI can show logged-out state.
 * Failed responses throw an Error with status and optional JSON body attached.
 */
import { clearToken, getToken } from './auth.js';

export async function api(path, options = {}) {
  // FormData needs its own Content-Type with boundary — do not force application/json.
  const isFormData = typeof FormData !== 'undefined' && options.body instanceof FormData;
  const headers = {
    ...(options.body && !isFormData ? { 'Content-Type': 'application/json' } : {}),
    ...options.headers,
  };

  const token = getToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(path, {
    ...options,
    headers,
  });

  // Do not clear token on failed login — the user may still have an old session token.
  if (response.status === 401 && !path.startsWith('/auth/login')) {
    clearToken();
  }

  let data = null;
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    data = await response.json();
  }

  if (!response.ok) {
    const error = new Error(data?.error || `Request failed (${response.status})`);
    error.status = response.status;
    error.data = data;
    throw error;
  }

  return data;
}
