/**
 * Main Responsibility: Expense detail page — show one expense and its document file.
 *
 * Loads GET /expenses/{id}. Document preview uses authenticated blob fetch via
 * documentFileUrl (img/iframe cannot send Bearer). No edit/delete actions.
 */
import { api, apiBlob } from '../api.js';
import { isLoggedIn } from '../auth.js';
import { navigate } from '../router.js';

/** Revoke the last preview blob URL so repeated visits do not leak memory. */
let activePreviewUrl = null;

export function renderExpenseDetailPage(root, expenseId) {
  if (!isLoggedIn()) {
    navigate('/login');
    return;
  }

  revokeActivePreviewUrl();

  root.innerHTML = `
    <main class="page page--wide">
      <h1>Expense details</h1>
      <p id="expense-detail-status" class="subtitle">Loading...</p>
      <p id="expense-detail-error" class="form-error" hidden></p>

      <div id="expense-detail-content" class="review-layout" hidden>
        <section class="review-preview">
          <h2>Document</h2>
          <div id="expense-preview-container" class="preview-container">
            <p class="preview-placeholder">Loading preview...</p>
          </div>
          <p id="expense-file-meta" class="review-meta"></p>
        </section>

        <section class="expense-detail-fields">
          <h2>Details</h2>
          <dl class="expense-dl">
            <dt>Date</dt>
            <dd id="field-date"></dd>
            <dt>Merchant</dt>
            <dd id="field-merchant"></dd>
            <dt>Amount</dt>
            <dd id="field-amount"></dd>
            <dt>Currency</dt>
            <dd id="field-currency"></dd>
            <dt>Category</dt>
            <dd id="field-category"></dd>
            <dt>Created</dt>
            <dd id="field-created"></dd>
            <dt>Filename</dt>
            <dd id="field-filename"></dd>
          </dl>
        </section>
      </div>

      <p class="auth-switch">
        <a href="#/expenses">Back to expenses</a>
        ·
        <a href="#/">Back to home</a>
      </p>
    </main>
  `;

  const statusEl = root.querySelector('#expense-detail-status');
  const errorEl = root.querySelector('#expense-detail-error');
  const contentEl = root.querySelector('#expense-detail-content');
  const previewContainerEl = root.querySelector('#expense-preview-container');
  const fileMetaEl = root.querySelector('#expense-file-meta');

  loadExpense();

  async function loadExpense() {
    errorEl.hidden = true;
    contentEl.hidden = true;
    statusEl.textContent = 'Loading...';

    try {
      const expense = await api(`/expenses/${expenseId}`);
      statusEl.textContent = expense.merchant || `Expense #${expense.id}`;
      root.querySelector('#field-date').textContent = expense.expenseDate ?? '—';
      root.querySelector('#field-merchant').textContent = expense.merchant || '—';
      root.querySelector('#field-amount').textContent =
        expense.totalAmount != null ? String(expense.totalAmount) : '—';
      root.querySelector('#field-currency').textContent = expense.currency || '—';
      root.querySelector('#field-category').textContent = expense.categoryName || '—';
      root.querySelector('#field-created').textContent = formatDateTime(expense.createdAt);
      root.querySelector('#field-filename').textContent = expense.originalFilename || '—';
      fileMetaEl.textContent = expense.originalFilename
        ? `File: ${expense.originalFilename}`
        : '';
      contentEl.hidden = false;
      await loadPreview(expense);
    } catch (error) {
      if (error.status === 401) {
        navigate('/login');
        return;
      }
      statusEl.textContent = '';
      errorEl.textContent =
        error.status === 404
          ? 'Expense not found or you do not have access.'
          : error.message || 'Could not load expense';
      errorEl.hidden = false;
    }
  }

  async function loadPreview(expense) {
    if (!expense.documentFileUrl) {
      previewContainerEl.innerHTML =
        '<p class="preview-placeholder">No document linked to this expense.</p>';
      return;
    }

    previewContainerEl.innerHTML = '<p class="preview-placeholder">Loading preview...</p>';
    revokeActivePreviewUrl();

    try {
      const blob = await apiBlob(expense.documentFileUrl);
      activePreviewUrl = URL.createObjectURL(blob);
      const mimeType = blob.type || mimeFromFilename(expense.originalFilename);
      previewContainerEl.innerHTML = buildPreviewElement(mimeType, activePreviewUrl);
    } catch (error) {
      if (error.status === 401) {
        navigate('/login');
        return;
      }
      previewContainerEl.innerHTML =
        `<p class="preview-placeholder preview-placeholder--error">${error.message || 'Preview unavailable'}</p>`;
    }
  }
}

function buildPreviewElement(mimeType, objectUrl) {
  if (mimeType === 'application/pdf') {
    return `<iframe class="preview-frame" src="${objectUrl}" title="Document preview"></iframe>`;
  }
  if (mimeType === 'image/jpeg' || mimeType === 'image/png') {
    return `<img class="preview-image" src="${objectUrl}" alt="Document preview" />`;
  }
  return `<p class="preview-placeholder">Preview not supported for ${mimeType || 'unknown type'}. <a href="${objectUrl}" download>Download file</a></p>`;
}

/** Fallback when the blob response has no Content-Type. */
function mimeFromFilename(filename) {
  if (!filename) {
    return '';
  }
  const lower = filename.toLowerCase();
  if (lower.endsWith('.pdf')) {
    return 'application/pdf';
  }
  if (lower.endsWith('.png')) {
    return 'image/png';
  }
  if (lower.endsWith('.jpg') || lower.endsWith('.jpeg')) {
    return 'image/jpeg';
  }
  return '';
}

function formatDateTime(value) {
  if (!value) {
    return '—';
  }
  return String(value).replace('T', ' ').slice(0, 19);
}

function revokeActivePreviewUrl() {
  if (activePreviewUrl) {
    URL.revokeObjectURL(activePreviewUrl);
    activePreviewUrl = null;
  }
}
