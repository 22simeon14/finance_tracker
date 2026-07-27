/**
 * Main Responsibility: Store and read the JWT in browser localStorage.
 *
 * Key "ft_token" holds the access token after login/register.
 * isLoggedIn only checks that a token string exists — it does not validate expiry.
 */
const TOKEN_KEY = 'ft_token';

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

export function isLoggedIn() {
  return Boolean(getToken());
}
