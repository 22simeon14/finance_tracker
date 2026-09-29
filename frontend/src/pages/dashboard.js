/**
 * Main Responsibility: Dashboard page — expense aggregates by currency,
 * line-item category breakdown (leaf / group / pie), and merchant.
 *
 * Loads GET /dashboard with optional from/to (inclusive expense_date).
 * The group pie lists only parents that have leaf or unallocated slices —
 * childless groups stay in By group only. Login required.
 */
import { api } from '../api.js';
import { isLoggedIn } from '../auth.js';
import { navigate } from '../router.js';

/** Distinct slice colors for the group pie (avoid purple-default bias). */
const PIE_COLORS = [
  '#0f766e',
  '#b45309',
  '#1d4ed8',
  '#be123c',
  '#365314',
  '#0e7490',
  '#9a3412',
  '#334155',
];

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

      <section class="dashboard-section" id="line-breakdown-section" hidden>
        <h2>Category breakdown (lines)</h2>
        <p class="dashboard-section__hint">Leaves from line items, plus positive unallocated remainder under the expense header group. Overrun is not drawn as a negative slice.</p>

        <h3 class="dashboard-subsection-title">By leaf</h3>
        <ul id="leaf-list" class="dashboard-list"></ul>
        <p id="leaf-empty" class="dashboard-empty" hidden>No leaf totals.</p>

        <h3 class="dashboard-subsection-title">By group</h3>
        <ul id="parent-list" class="dashboard-list"></ul>
        <p id="parent-empty" class="dashboard-empty" hidden>No group totals.</p>

        <h3 class="dashboard-subsection-title">Group pie</h3>
        <p class="dashboard-section__hint">Only groups that have subcategories (and line slices) appear here. Childless groups stay in By group above.</p>
        <label class="dashboard-pie-picker">
          Group
          <select id="pie-group-select"></select>
        </label>
        <div id="pie-panel" class="dashboard-pie-panel" hidden>
          <div id="pie-chart" class="dashboard-pie-chart" aria-hidden="true"></div>
          <ul id="pie-legend" class="dashboard-pie-legend"></ul>
        </div>
        <p id="pie-empty" class="dashboard-empty" hidden>No slices for this group.</p>
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

  const lineBreakdownSection = root.querySelector('#line-breakdown-section');
  const leafList = root.querySelector('#leaf-list');
  const leafEmpty = root.querySelector('#leaf-empty');
  const parentList = root.querySelector('#parent-list');
  const parentEmpty = root.querySelector('#parent-empty');
  const pieGroupSelect = root.querySelector('#pie-group-select');
  const piePanel = root.querySelector('#pie-panel');
  const pieChart = root.querySelector('#pie-chart');
  const pieLegend = root.querySelector('#pie-legend');
  const pieEmpty = root.querySelector('#pie-empty');

  const merchantSection = root.querySelector('#merchant-section');
  const merchantList = root.querySelector('#merchant-list');
  const merchantEmpty = root.querySelector('#merchant-empty');

  /** Latest byLeafCategory rows — pie re-filters when the group select changes. */
  let leafRows = [];

  formEl.addEventListener('submit', (event) => {
    event.preventDefault();
    loadDashboard();
  });
  clearPeriodBtn.addEventListener('click', () => {
    formEl.reset();
    loadDashboard();
  });
  pieGroupSelect.addEventListener('change', () => {
    renderGroupPie(leafRows, pieGroupSelect.value);
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
    lineBreakdownSection.hidden = true;
    merchantSection.hidden = true;
    totalsList.innerHTML = '';
    leafList.innerHTML = '';
    parentList.innerHTML = '';
    merchantList.innerHTML = '';
    pieGroupSelect.innerHTML = '';
    pieLegend.innerHTML = '';
    pieChart.style.background = '';
    piePanel.hidden = true;
    pieEmpty.hidden = true;

    try {
      const data = await api(buildDashboardPath(readPeriod()));
      statusEl.textContent = '';
      renderTotals(data.totalsByCurrency || []);
      renderLineBreakdown(data.byLeafCategory || [], data.byParentCategory || []);
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

  /**
   * Line-based report: leaf list, parent roll-up, and pie picker options.
   */
  function renderLineBreakdown(leaves, parents) {
    leafRows = leaves;
    lineBreakdownSection.hidden = false;

    if (!leaves.length) {
      leafEmpty.hidden = false;
    } else {
      leafEmpty.hidden = true;
      leafList.innerHTML = leaves
        .map(
          (row) => `
            <li class="dashboard-row">
              <span class="dashboard-row__label">${escapeHtml(row.categoryName || '—')}</span>
              <span class="dashboard-row__meta">${escapeHtml(leafMeta(row))}</span>
              <span class="dashboard-row__amount">${escapeHtml(formatMoney(row.totalAmount, row.currency))}</span>
            </li>
          `,
        )
        .join('');
    }

    if (!parents.length) {
      parentEmpty.hidden = false;
      pieGroupSelect.innerHTML = '';
      piePanel.hidden = true;
      pieEmpty.hidden = true;
      return;
    }

    parentEmpty.hidden = true;
    parentList.innerHTML = parents
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

    // One option per group+currency that actually has leaf/unallocated slices.
    // Childless groups stay in By group only — they are not pie options.
    const pieParents = parents.filter((parent) =>
      leaves.some(
        (leaf) =>
          Number(leaf.parentId) === Number(parent.categoryId) &&
          (leaf.currency || '') === (parent.currency || ''),
      ),
    );

    pieGroupSelect.innerHTML = pieParents
      .map(
        (row) =>
          `<option value="${escapeHtml(String(row.categoryId))}::${escapeHtml(row.currency || '')}">${escapeHtml(row.categoryName || '—')} (${escapeHtml(row.currency || '')})</option>`,
      )
      .join('');

    if (!pieParents.length) {
      piePanel.hidden = true;
      pieEmpty.hidden = true;
      return;
    }

    renderGroupPie(leaves, pieGroupSelect.value);
  }

  /** Pie slices = leaves (+ unallocated) for the selected group and currency. */
  function renderGroupPie(leaves, selectValue) {
    const [groupIdRaw, currency] = String(selectValue || '').split('::');
    const groupId = Number(groupIdRaw);
    const slices = leaves.filter(
      (row) => Number(row.parentId) === groupId && (row.currency || '') === (currency || ''),
    );

    if (!slices.length) {
      piePanel.hidden = true;
      pieEmpty.hidden = false;
      pieLegend.innerHTML = '';
      pieChart.style.background = '';
      return;
    }

    pieEmpty.hidden = true;
    piePanel.hidden = false;

    const groupTotal = slices.reduce((sum, row) => sum + Number(row.totalAmount || 0), 0);
    let angle = 0;
    const stops = [];
    pieLegend.innerHTML = slices
      .map((row, index) => {
        const amount = Number(row.totalAmount || 0);
        const share = groupTotal > 0 ? amount / groupTotal : 0;
        const start = angle;
        angle += share * 360;
        const color = PIE_COLORS[index % PIE_COLORS.length];
        stops.push(`${color} ${start}deg ${angle}deg`);
        const percent = groupTotal > 0 ? Math.round(share * 1000) / 10 : 0;
        return `
          <li class="dashboard-pie-legend__item">
            <span class="dashboard-pie-legend__swatch" style="background:${color}"></span>
            <span class="dashboard-pie-legend__label">${escapeHtml(row.categoryName || '—')}</span>
            <span class="dashboard-pie-legend__amount">${escapeHtml(formatMoney(row.totalAmount, row.currency))} (${percent}%)</span>
          </li>
        `;
      })
      .join('');

    pieChart.style.background = `conic-gradient(${stops.join(', ')})`;
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

/** Meta text under a leaf row: parent group name, or "remainder" for unallocated. */
function leafMeta(row) {
  const currency = row.currency || '';
  if (row.unallocated) {
    return [currency, row.parentName ? `under ${row.parentName}` : null, 'remainder']
      .filter(Boolean)
      .join(' · ');
  }
  if (row.parentName && row.parentId !== row.categoryId) {
    return [currency, row.parentName].filter(Boolean).join(' · ');
  }
  return currency;
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
