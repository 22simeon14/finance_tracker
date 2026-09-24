/**
 * Main Responsibility: Expenses list page — filtered list of saved expenses.
 *
 * Loads GET /expenses with optional from/to/categoryId/merchant (AND-combined).
 * The category filter lists top-level groups only (empty parentId).
 * Distinguishes true-empty (upload CTA) from filtered-empty (clear filters).
 * After unapprove, shows a short notice with a link to the pending inbox.
 * Login required.
 */
import { api } from '../api.js';
import { isLoggedIn } from '../auth.js';
import { navigate } from '../router.js';

/** sessionStorage key set by expense-detail after a successful unapprove. */
const UNAPPROVE_NOTICE_KEY = 'expenseUnapproveNotice';

export function renderExpensesPage(root) {
  if (!isLoggedIn()) {
    navigate('/login');
    return;
  }

  root.innerHTML = `
    <main class="page">
      <h1>Expenses</h1>
      <p class="subtitle">Your approved expenses</p>
      <p id="expenses-notice" class="expenses-notice" hidden></p>
      <p id="expenses-error" class="form-error" hidden></p>

      <form id="expenses-filters" class="expenses-filters auth-form">
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
        <div class="expenses-filters__row">
          <label>
            Category
            <select name="categoryId">
              <option value="">All categories</option>
            </select>
          </label>
          <label>
            Merchant
            <input type="text" name="merchant" placeholder="Contains…" autocomplete="off" />
          </label>
        </div>
        <div class="expenses-filters__actions">
          <button type="submit">Apply filters</button>
          <button type="button" id="clear-filters-btn">Clear filters</button>
        </div>
      </form>

      <p id="expenses-status" class="categories-status">Loading...</p>
      <ul id="expenses-list" class="expenses-list"></ul>
      <p id="expenses-empty" class="expenses-empty" hidden>
        No expenses yet.
        <a href="#/upload">Upload a document</a>
        to create one.
      </p>
      <p id="expenses-filtered-empty" class="expenses-empty" hidden>
        No expenses match these filters.
        <button type="button" id="clear-filters-empty-btn" class="link-button">Clear filters</button>
      </p>
      <p class="auth-switch">
        <a href="#/upload">Upload document</a>
        ·
        <a href="#/dashboard">Dashboard</a>
        ·
        <a href="#/documents">Pending inbox</a>
        ·
        <a href="#/">Back to home</a>
      </p>
    </main>
  `;

  const noticeEl = root.querySelector('#expenses-notice');
  const errorEl = root.querySelector('#expenses-error');
  const statusEl = root.querySelector('#expenses-status');
  const listEl = root.querySelector('#expenses-list');
  const emptyEl = root.querySelector('#expenses-empty');
  const filteredEmptyEl = root.querySelector('#expenses-filtered-empty');
  const formEl = root.querySelector('#expenses-filters');
  const categorySelectEl = formEl.querySelector('select[name="categoryId"]');
  const clearFiltersBtn = root.querySelector('#clear-filters-btn');
  const clearFiltersEmptyBtn = root.querySelector('#clear-filters-empty-btn');

  showUnapproveNoticeIfPresent();

  formEl.addEventListener('submit', (event) => {
    event.preventDefault();
    loadExpenses();
  });
  clearFiltersBtn.addEventListener('click', () => clearFiltersAndReload());
  clearFiltersEmptyBtn.addEventListener('click', () => clearFiltersAndReload());

  loadCategories().then(() => loadExpenses());

  /** One-shot banner after unapprove; cleared so refresh does not keep showing it. */
  function showUnapproveNoticeIfPresent() {
    if (sessionStorage.getItem(UNAPPROVE_NOTICE_KEY) !== '1') {
      return;
    }
    sessionStorage.removeItem(UNAPPROVE_NOTICE_KEY);
    noticeEl.innerHTML =
      'Expense removed from the list. The document is back in the ' +
      '<a href="#/documents">pending inbox</a> for review.';
    noticeEl.hidden = false;
  }

  async function loadCategories() {
    try {
      const categories = await api('/categories');
      // Expense header filter: groups only, not leaves such as Meat.
      const groups = categories.filter((category) => category.parentId == null);
      const options = groups
        .map((category) => `<option value="${category.id}">${escapeHtml(category.name)}</option>`)
        .join('');
      categorySelectEl.insertAdjacentHTML('beforeend', options);
    } catch (error) {
      if (error.status === 401) {
        navigate('/login');
      }
      // Filters still work without category options (date/merchant only).
    }
  }

  function readFilters() {
    return {
      from: formEl.querySelector('[name="from"]').value,
      to: formEl.querySelector('[name="to"]').value,
      categoryId: formEl.querySelector('[name="categoryId"]').value,
      merchant: formEl.querySelector('[name="merchant"]').value.trim(),
    };
  }

  function hasActiveFilters(filters) {
    return Boolean(filters.from || filters.to || filters.categoryId || filters.merchant);
  }

  /** Build GET /expenses?... only with non-empty filter params. */
  function buildExpensesPath(filters) {
    const params = new URLSearchParams();
    if (filters.from) {
      params.set('from', filters.from);
    }
    if (filters.to) {
      params.set('to', filters.to);
    }
    if (filters.categoryId) {
      params.set('categoryId', filters.categoryId);
    }
    if (filters.merchant) {
      params.set('merchant', filters.merchant);
    }
    const query = params.toString();
    return query ? `/expenses?${query}` : '/expenses';
  }

  function clearFiltersAndReload() {
    formEl.reset();
    loadExpenses();
  }

  async function loadExpenses() {
    errorEl.hidden = true;
    emptyEl.hidden = true;
    filteredEmptyEl.hidden = true;
    listEl.innerHTML = '';
    statusEl.textContent = 'Loading...';

    const filters = readFilters();

    try {
      const expenses = await api(buildExpensesPath(filters));
      statusEl.textContent = '';

      if (!expenses.length) {
        if (hasActiveFilters(filters)) {
          filteredEmptyEl.hidden = false;
        } else {
          emptyEl.hidden = false;
        }
        return;
      }

      listEl.innerHTML = expenses
        .map(
          (expense) => `
            <li>
              <a class="expense-row" href="#/expenses/${expense.id}">
                <span class="expense-row__date">${escapeHtml(expense.expenseDate ?? '')}</span>
                <span class="expense-row__merchant">${escapeHtml(expense.merchant || '—')}</span>
                <span class="expense-row__amount">${escapeHtml(formatAmount(expense))}</span>
                <span class="expense-row__category">${escapeHtml(expense.categoryName || '—')}</span>
              </a>
            </li>
          `,
        )
        .join('');
    } catch (error) {
      if (error.status === 401) {
        navigate('/login');
        return;
      }
      statusEl.textContent = '';
      errorEl.textContent = error.message || 'Could not load expenses';
      errorEl.hidden = false;
    }
  }
}

function formatAmount(expense) {
  const amount = expense.totalAmount != null ? String(expense.totalAmount) : '—';
  const currency = expense.currency || '';
  return currency ? `${amount} ${currency}` : amount;
}

/** Escape text before inserting into HTML attributes/content. */
function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
