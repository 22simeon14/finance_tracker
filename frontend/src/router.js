/**
 * Main Responsibility: Simple hash-based client router (no full page reloads).
 *
 * Routes look like #/login or #/register. getRoute normalizes the hash to a path
 * starting with "/". startRouter runs the callback once, then on every hashchange.
 */

/** Read the current path from window.location.hash (default "/"). */
export function getRoute() {
  const hash = window.location.hash.replace(/^#/, '') || '/';
  return hash.startsWith('/') ? hash : `/${hash}`;
}

export function navigate(path) {
  window.location.hash = path.startsWith('#') ? path : `#${path}`;
}

/**
 * Subscribe to route changes. Returns an unsubscribe function.
 * Calls onRouteChange immediately with the current route.
 */
export function startRouter(onRouteChange) {
  const handle = () => onRouteChange(getRoute());
  window.addEventListener('hashchange', handle);
  handle();
  return () => window.removeEventListener('hashchange', handle);
}
