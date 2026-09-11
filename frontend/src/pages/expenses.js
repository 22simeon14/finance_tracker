/**
 * Main Responsibility: Expenses list page — show the current user's saved expenses.
 *
 * Loads GET /expenses (JWT). Each row links to the detail page. Empty accounts
 * see a short message and a link to upload. Login required.
 */
import { api } from '../api.js';
import { isLoggedIn } from '../auth.js';
import { navigate } from '../router.js';

export function renderExpensesPage(root) {
  if (!isLoggedIn()) {
    navigate('/login');
    return;
  }

  root.innerHTML = `
    <main class="page">
      <h1>Expenses</h1>
      <p class="subtitle">Your approved expenses</p>
      <p id="expenses-error" class="form-error" hidden></p>
      <p id="expenses-status" class="categories-status">Loading...</p>
      <ul id="expenses-list" class="expenses-list"></ul>
      <p id="expenses-empty" class="expenses-empty" hidden>
        No expenses yet.
        <a href="#/upload">Upload a document</a>
        to create one.
      </p>
      <p class="auth-switch">
        <a href="#/upload">Upload document</a>
        ·
        <a href="#/">Back to home</a>
      </p>
    </main>
  `;

  const errorEl = root.querySelector('#expenses-error');
  const statusEl = root.querySelector('#expenses-status');
  const listEl = root.querySelector('#expenses-list');
  const emptyEl = root.querySelector('#expenses-empty');

  loadExpenses();

  async function loadExpenses() {
    errorEl.hidden = true;
    emptyEl.hidden = true;
    listEl.innerHTML = '';
    statusEl.textContent = 'Loading...';

    try {
      const expenses = await api('/expenses');
      statusEl.textContent = '';

      if (!expenses.length) {
        emptyEl.hidden = false;
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
