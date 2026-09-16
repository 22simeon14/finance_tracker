/**
 * Main Responsibility: Dashboard page — expense aggregates by currency, category, merchant.
 *
 * Loads GET /dashboard with optional from/to (inclusive expense_date).
 * Shows only confirmed expenses; no recent-list widgets. Login required.
 */
import { api } from '../api.js';
import { isLoggedIn } from '../auth.js';
import { navigate } from '../router.js';

export function renderDashboardPage(root) {
  if (!isLoggedIn()) {
    navigate('/login');
    return;
  }

  root.innerHTML = `
    <main class="page">
      <h1>Dashboard</h1>
      <p class="subtitle">Totals from approved expenses</p>
      <p id="dashboard-error" class="form-error" hidden></p>

      <form id="dashboard-filters" class="expenses-filters auth-form">
        <div class="expenses-filters__row">
          <label>
            From
            <input type="date" name="from" />
          </label>
          <label>
            To
            <input type="date" name="to" />
          </label>
        </div>
        <div class="expenses-filters__actions">
          <button type="submit">Apply period</button>
          <button type="button" id="clear-period-btn">Clear period</button>
        </div>
      </form>

      <p id="dashboard-status" class="categories-status">Loading...</p>

      <section class="dashboard-section" id="totals-section" hidden>
        <h2>Totals by currency</h2>
        <ul id="totals-list" class="dashboard-list"></ul>
        <p id="totals-empty" class="dashboard-empty" hidden>No expenses in this period.</p>
      </section>

      <section class="dashboard-section" id="category-section" hidden>
        <h2>By category</h2>
        <ul id="category-list" class="dashboard-list"></ul>
        <p id="category-empty" class="dashboard-empty" hidden>No category totals.</p>
      </section>

      <section class="dashboard-section" id="merchant-section" hidden>
        <h2>By merchant</h2>
        <ul id="merchant-list" class="dashboard-list"></ul>
        <p id="merchant-empty" class="dashboard-empty" hidden>No merchant totals.</p>
      </section>

      <p class="auth-switch">
        <a href="#/expenses">Expenses</a>
        ·
        <a href="#/documents">Pending inbox</a>
        ·
        <a href="#/">Back to home</a>
      </p>
    </main>
  `;

  const errorEl = root.querySelector('#dashboard-error');
  const statusEl = root.querySelector('#dashboard-status');
  const formEl = root.querySelector('#dashboard-filters');
  const clearPeriodBtn = root.querySelector('#clear-period-btn');

  const totalsSection = root.querySelector('#totals-section');
  const totalsList = root.querySelector('#totals-list');
  const totalsEmpty = root.querySelector('#totals-empty');

  const categorySection = root.querySelector('#category-section');
  const categoryList = root.querySelector('#category-list');
  const categoryEmpty = root.querySelector('#category-empty');

  const merchantSection = root.querySelector('#merchant-section');
  const merchantList = root.querySelector('#merchant-list');
  const merchantEmpty = root.querySelector('#merchant-empty');

  formEl.addEventListener('submit', (event) => {
    event.preventDefault();
    loadDashboard();
  });
  clearPeriodBtn.addEventListener('click', () => {
    formEl.reset();
    loadDashboard();
  });

  loadDashboard();

  function readPeriod() {
    return {
      from: formEl.querySelector('[name="from"]').value,
      to: formEl.querySelector('[name="to"]').value,
    };
  }

  /** Build GET /dashboard?... only with non-empty date params. */
  function buildDashboardPath(period) {
    const params = new URLSearchParams();
    if (period.from) {
      params.set('from', period.from);
    }
    if (period.to) {
      params.set('to', period.to);
    }
    const query = params.toString();
    return query ? `/dashboard?${query}` : '/dashboard';
  }

  async function loadDashboard() {
    errorEl.hidden = true;
    statusEl.textContent = 'Loading...';
    totalsSection.hidden = true;
    categorySection.hidden = true;
    merchantSection.hidden = true;
    totalsList.innerHTML = '';
    categoryList.innerHTML = '';
    merchantList.innerHTML = '';

    try {
      const data = await api(buildDashboardPath(readPeriod()));
      statusEl.textContent = '';
      renderTotals(data.totalsByCurrency || []);
      renderByCategory(data.byCategory || []);
      renderByMerchant(data.byMerchant || []);
    } catch (error) {
      if (error.status === 401) {
        navigate('/login');
        return;
      }
      statusEl.textContent = '';
      errorEl.textContent = error.message || 'Could not load dashboard';
      errorEl.hidden = false;
    }
  }

  function renderTotals(rows) {
    totalsSection.hidden = false;
    if (!rows.length) {
      totalsEmpty.hidden = false;
      return;
    }
    totalsEmpty.hidden = true;
    totalsList.innerHTML = rows
      .map(
        (row) => `
          <li class="dashboard-row">
            <span class="dashboard-row__label">${escapeHtml(row.currency || '—')}</span>
            <span class="dashboard-row__meta"></span>
            <span class="dashboard-row__amount">${escapeHtml(formatMoney(row.totalAmount, row.currency))}</span>
          </li>
        `,
      )
      .join('');
  }

  function renderByCategory(rows) {
    categorySection.hidden = false;
    if (!rows.length) {
      categoryEmpty.hidden = false;
      return;
    }
    categoryEmpty.hidden = true;
    categoryList.innerHTML = rows
      .map(
        (row) => `
          <li class="dashboard-row">
            <span class="dashboard-row__label">${escapeHtml(row.categoryName || '—')}</span>
            <span class="dashboard-row__meta">${escapeHtml(row.currency || '')}</span>
            <span class="dashboard-row__amount">${escapeHtml(formatMoney(row.totalAmount, row.currency))}</span>
          </li>
        `,
      )
      .join('');
  }

  function renderByMerchant(rows) {
    merchantSection.hidden = false;
    if (!rows.length) {
      merchantEmpty.hidden = false;
      return;
    }
    merchantEmpty.hidden = true;
    merchantList.innerHTML = rows
      .map(
        (row) => `
          <li class="dashboard-row">
            <span class="dashboard-row__label">${escapeHtml(row.merchant || '(none)')}</span>
            <span class="dashboard-row__meta">${escapeHtml(row.currency || '')}</span>
            <span class="dashboard-row__amount">${escapeHtml(formatMoney(row.totalAmount, row.currency))}</span>
          </li>
        `,
      )
      .join('');
  }
}

function formatMoney(amount, currency) {
  const value = amount != null ? String(amount) : '—';
  return currency ? `${value} ${currency}` : value;
}

/** Escape text before inserting into HTML. */
function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
