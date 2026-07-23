export function renderHomePage(root) {
  root.innerHTML = `
    <main class="page">
      <h1>Finance Tracker</h1>
      <p class="subtitle">MVP skeleton</p>
      <section class="health-card">
        <h2>Backend status</h2>
        <p id="health-status" class="health-status">Checking...</p>
        <button type="button" id="refresh-health">Refresh</button>
      </section>
    </main>
  `;

  const statusEl = root.querySelector('#health-status');
  const refreshBtn = root.querySelector('#refresh-health');

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
  loadHealth();
}
