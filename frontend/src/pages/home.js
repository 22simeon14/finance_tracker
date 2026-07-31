/**
 * Main Responsibility: Home page UI — account, categories proof, and health check.
 *
 * Shows login/register links when logged out, or email + upload + logout when logged in.
 * After a successful /auth/me, loads GET /categories (JWT-protected) and lists names.
 * Health uses plain fetch("/health") (proxied by Vite) instead of api(), because
 * it is public and does not need a Bearer token.
 */
import { api } from '../api.js';
import { clearToken, isLoggedIn } from '../auth.js';
import { navigate } from '../router.js';

export function renderHomePage(root) {
  root.innerHTML = `
    <main class="page">
      <h1>Finance Tracker</h1>
      <p class="subtitle">MVP skeleton</p>

      <section class="auth-card">
        <h2>Account</h2>
        <p id="account-status" class="account-status">Loading...</p>
        <div id="account-actions" class="account-actions"></div>
      </section>

      <section id="categories-section" class="categories-card" hidden>
        <h2>Categories</h2>
        <ul id="categories-list" class="categories-list"></ul>
        <p id="categories-status" class="categories-status"></p>
      </section>

      <section class="health-card">
        <h2>Backend status</h2>
        <p id="health-status" class="health-status">Checking...</p>
        <button type="button" id="refresh-health">Refresh</button>
      </section>
    </main>
  `;

  const accountStatusEl = root.querySelector('#account-status');
  const accountActionsEl = root.querySelector('#account-actions');
  const categoriesSectionEl = root.querySelector('#categories-section');
  const categoriesListEl = root.querySelector('#categories-list');
  const categoriesStatusEl = root.querySelector('#categories-status');
  const statusEl = root.querySelector('#health-status');
  const refreshBtn = root.querySelector('#refresh-health');

  function hideCategories() {
    categoriesSectionEl.hidden = true;
    categoriesListEl.innerHTML = '';
    categoriesStatusEl.textContent = '';
  }

  async function loadCategories() {
    categoriesSectionEl.hidden = false;
    categoriesListEl.innerHTML = '';
    categoriesStatusEl.textContent = 'Loading...';

    try {
      const categories = await api('/categories');
      categoriesStatusEl.textContent = '';
      // Simple name list — proof that the JWT-protected endpoint works from the UI.
      categoriesListEl.innerHTML = categories
        .map((category) => `<li>${category.name}</li>`)
        .join('');
    } catch (error) {
      categoriesListEl.innerHTML = '';
      categoriesStatusEl.textContent = 'Could not load categories';
    }
  }

  async function loadAccount() {
    if (!isLoggedIn()) {
      accountStatusEl.textContent = 'Not logged in';
      accountActionsEl.innerHTML = `
        <a href="#/login">Log in</a>
        <a href="#/register">Register</a>
      `;
      hideCategories();
      return;
    }

    try {
      const me = await api('/auth/me');
      accountStatusEl.textContent = `Logged in as ${me.email}`;
      accountActionsEl.innerHTML = `
        <a href="#/upload">Upload document</a>
        <button type="button" id="logout-btn">Log out</button>
      `;
      root.querySelector('#logout-btn').addEventListener('click', () => {
        clearToken();
        navigate('/login');
      });
      await loadCategories();
    } catch (error) {
      // api() already cleared an invalid token on 401.
      accountStatusEl.textContent = 'Session expired — please log in again';
      accountActionsEl.innerHTML = `
        <a href="#/login">Log in</a>
        <a href="#/register">Register</a>
      `;
      hideCategories();
    }
  }

  async function loadHealth() {
    statusEl.textContent = 'Checking...';
    statusEl.className = 'health-status';

    try {
      const response = await fetch('/health');
      const data = await response.json();

      if (response.ok && data.status === 'ok' && data.database === 'up') {
        statusEl.textContent = 'OK — database is up';
        statusEl.className = 'health-status health-status--ok';
      } else {
        statusEl.textContent = `Degraded — database: ${data.database ?? 'unknown'}`;
        statusEl.className = 'health-status health-status--error';
      }
    } catch (error) {
      statusEl.textContent = 'Cannot reach backend';
      statusEl.className = 'health-status health-status--error';
    }
  }

  refreshBtn.addEventListener('click', loadHealth);
  loadAccount();
  loadHealth();
}
