/**
 * Main Responsibility: Expense detail page — preview, edit fields/lines, unapprove.
 *
 * Loads GET /expenses/{id} and GET /categories. The header category dropdown
 * lists top-level groups only. Line items reuse review-items.js (same CSS).
 * Save uses PUT /expenses/{id} with header fields plus the form line list.
 * Unapprove uses DELETE /expenses/{id} (expense gone; document → pending inbox),
 * then navigates to #/expenses with a short notice. Preview uses authenticated blob fetch.
 */
import { api, apiBlob } from '../api.js';
import { isLoggedIn } from '../auth.js';
import { navigate } from '../router.js';
import { createReviewItemsEditor } from './review-items.js';

/** Product is EUR-only; column stays, UI no longer offers USD/GBP. */
const CURRENCY = 'EUR';

/** Must match expenses.js so the list can show the post-unapprove notice. */
const UNAPPROVE_NOTICE_KEY = 'expenseUnapproveNotice';

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
          <form id="expense-edit-form" class="auth-form">
            <label>
              Merchant
              <input type="text" name="merchant" autocomplete="off" />
            </label>
            <label>
              Date
              <input type="date" name="expenseDate" required />
            </label>
            <label>
              Amount
              <input type="number" name="totalAmount" step="0.01" min="0" required />
            </label>
            <label>
              Currency
              <input type="text" name="currency" value="${CURRENCY}" readonly />
            </label>
            <label>
              Category
              <select name="categoryId" required>
                <option value="">— Select category —</option>
              </select>
            </label>
            <dl class="expense-dl expense-dl--meta">
              <dt>Created</dt>
              <dd id="field-created"></dd>
              <dt>Filename</dt>
              <dd id="field-filename"></dd>
            </dl>
            <div class="review-actions">
              <button type="submit" id="save-btn" class="button-primary">Save changes</button>
              <button type="button" id="unapprove-btn" class="button-danger">Unapprove</button>
            </div>
            <p id="expense-action-status" class="categories-status"></p>
          </form>
        </section>

        <!-- Full-width under preview+form; same Items card as review. -->
        <section class="review-items" aria-labelledby="expense-items-heading">
          <h2 id="expense-items-heading">Items</h2>
          <div id="expense-items-list"></div>
          <div class="review-items-footer">
            <button type="button" id="expense-add-item-btn">Add item</button>
            <div class="review-items-totals">
              <p id="expense-items-sum" class="review-items-sum"></p>
              <p id="expense-items-mismatch" class="review-items-mismatch" hidden></p>
            </div>
          </div>
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
  const formEl = root.querySelector('#expense-edit-form');
  const categorySelectEl = formEl.querySelector('select[name="categoryId"]');
  const amountInputEl = formEl.querySelector('input[name="totalAmount"]');
  const saveBtn = root.querySelector('#save-btn');
  const unapproveBtn = root.querySelector('#unapprove-btn');
  const actionStatusEl = root.querySelector('#expense-action-status');

  const itemsEditor = createReviewItemsEditor({
    listEl: root.querySelector('#expense-items-list'),
    sumEl: root.querySelector('#expense-items-sum'),
    mismatchEl: root.querySelector('#expense-items-mismatch'),
    addBtn: root.querySelector('#expense-add-item-btn'),
    amountInputEl,
    currency: CURRENCY,
    mismatchHint:
      'Line items and receipt total differ. That can be a discount, deposit, or tip — Save is still allowed.',
  });

  formEl.addEventListener('submit', (event) => {
    event.preventDefault();
    runSave();
  });
  unapproveBtn.addEventListener('click', () => runUnapprove());

  loadExpense();

  async function loadExpense() {
    errorEl.hidden = true;
    contentEl.hidden = true;
    actionStatusEl.textContent = '';
    statusEl.textContent = 'Loading...';

    try {
      const [expense, categories] = await Promise.all([
        api(`/expenses/${expenseId}`),
        api('/categories'),
      ]);

      itemsEditor.setCategories(categories);
      populateCategoryOptions(categories, expense);
      applyExpenseToForm(expense);
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

  function populateCategoryOptions(categories, expense) {
    // Whole-expense category is a group. Leaves are chosen on a line below.
    const groups = categories.filter((category) => category.parentId == null);
    const options = groups
      .map((category) => `<option value="${category.id}">${escapeHtml(category.name)}</option>`)
      .join('');
    categorySelectEl.insertAdjacentHTML('beforeend', options);

    // If the saved category is inactive it won't be in GET /categories — keep it selectable for display.
    const ids = new Set(groups.map((category) => String(category.id)));
    if (expense.categoryId != null && !ids.has(String(expense.categoryId))) {
      const label = expense.categoryName
        ? `${expense.categoryName} (inactive)`
        : `Category #${expense.categoryId} (inactive)`;
      categorySelectEl.insertAdjacentHTML(
        'beforeend',
        `<option value="${expense.categoryId}">${escapeHtml(label)}</option>`,
      );
    }
  }

  function applyExpenseToForm(expense) {
    statusEl.textContent = expense.merchant || `Expense #${expense.id}`;
    formEl.querySelector('[name="merchant"]').value = expense.merchant || '';
    formEl.querySelector('[name="expenseDate"]').value = expense.expenseDate ?? '';
    formEl.querySelector('[name="totalAmount"]').value =
      expense.totalAmount != null ? String(expense.totalAmount) : '';
    formEl.querySelector('[name="currency"]').value = CURRENCY;
    categorySelectEl.value =
      expense.categoryId != null ? String(expense.categoryId) : '';
    root.querySelector('#field-created').textContent = formatDateTime(expense.createdAt);
    root.querySelector('#field-filename').textContent = expense.originalFilename || '—';
    fileMetaEl.textContent = expense.originalFilename
      ? `File: ${expense.originalFilename}`
      : '';
    itemsEditor.loadFromExtraction(expense.lineItems);
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

  /**
   * PUT header fields and the Items list. Empty list is allowed.
   * A qty×price mismatch does not block Save.
   */
  async function runSave() {
    errorEl.hidden = true;
    actionStatusEl.textContent = '';

    const merchant = formEl.querySelector('[name="merchant"]').value.trim();
    const expenseDate = formEl.querySelector('[name="expenseDate"]').value;
    const amountRaw = formEl.querySelector('[name="totalAmount"]').value;
    const categoryIdRaw = formEl.querySelector('[name="categoryId"]').value;

    if (!expenseDate || !amountRaw || !categoryIdRaw) {
      errorEl.textContent = 'Date, amount, and category are required.';
      errorEl.hidden = false;
      return;
    }

    const totalAmount = Number(amountRaw);
    if (!(totalAmount > 0)) {
      errorEl.textContent = 'Amount must be greater than zero.';
      errorEl.hidden = false;
      return;
    }

    const lines = itemsEditor.linesForApprove();
    if (lines.error) {
      errorEl.textContent = lines.error;
      errorEl.hidden = false;
      return;
    }

    actionStatusEl.textContent = 'Saving...';
    saveBtn.disabled = true;
    unapproveBtn.disabled = true;

    try {
      const updated = await api(`/expenses/${expenseId}`, {
        method: 'PUT',
        body: JSON.stringify({
          expenseDate,
          totalAmount,
          currency: CURRENCY,
          categoryId: Number(categoryIdRaw),
          merchant: merchant || null,
          lineItems: lines.lineItems,
        }),
      });
      applyExpenseToForm(updated);
      actionStatusEl.textContent = 'Saved.';
    } catch (error) {
      if (error.status === 401) {
        navigate('/login');
        return;
      }
      actionStatusEl.textContent = '';
      errorEl.textContent = error.message || 'Save failed';
      errorEl.hidden = false;
    } finally {
      saveBtn.disabled = false;
      unapproveBtn.disabled = false;
    }
  }

  /**
   * Unapprove is not a forever wipe: expense row is removed, document returns
   * to REVIEW_REQUIRED, and the file stays on disk for the pending inbox.
   */
  async function runUnapprove() {
    errorEl.hidden = true;
    actionStatusEl.textContent = '';

    const confirmed = window.confirm(
      'Remove this expense from your list? The linked document will go back to the pending inbox for review. The file is kept (this is not a permanent delete).',
    );
    if (!confirmed) {
      return;
    }

    actionStatusEl.textContent = 'Unapproving...';
    saveBtn.disabled = true;
    unapproveBtn.disabled = true;

    try {
      await api(`/expenses/${expenseId}`, { method: 'DELETE' });
      sessionStorage.setItem(UNAPPROVE_NOTICE_KEY, '1');
      navigate('/expenses');
    } catch (error) {
      if (error.status === 401) {
        navigate('/login');
        return;
      }
      actionStatusEl.textContent = '';
      errorEl.textContent = error.message || 'Unapprove failed';
      errorEl.hidden = false;
      saveBtn.disabled = false;
      unapproveBtn.disabled = false;
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

/** Escape text before inserting into HTML (category option labels). */
function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
