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

      <section class="health-card">
        <h2>Backend status</h2>
        <p id="health-status" class="health-status">Checking...</p>
        <button type="button" id="refresh-health">Refresh</button>
      </section>
    </main>
  `;

  const accountStatusEl = root.querySelector('#account-status');
  const accountActionsEl = root.querySelector('#account-actions');
  const statusEl = root.querySelector('#health-status');
  const refreshBtn = root.querySelector('#refresh-health');

  async function loadAccount() {
    if (!isLoggedIn()) {
      accountStatusEl.textContent = 'Not logged in';
      accountActionsEl.innerHTML = `
        <a href="#/login">Log in</a>
        <a href="#/register">Register</a>
      `;
      return;
    }

    try {
      const me = await api('/auth/me');
      accountStatusEl.textContent = `Logged in as ${me.email}`;
      accountActionsEl.innerHTML = `
        <button type="button" id="logout-btn">Log out</button>
      `;
      root.querySelector('#logout-btn').addEventListener('click', () => {
        clearToken();
        navigate('/login');
      });
    } catch (error) {
      accountStatusEl.textContent = 'Session expired — please log in again';
      accountActionsEl.innerHTML = `
        <a href="#/login">Log in</a>
        <a href="#/register">Register</a>
      `;
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
