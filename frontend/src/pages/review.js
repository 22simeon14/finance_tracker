/**
 * Main Responsibility: Document review page — preview uploaded file and edit proposed fields.
 *
 * Loads GET /documents/{id} and GET /categories. The receipt category dropdown
 * lists top-level groups only. File preview uses authenticated
 * blob fetch (img/iframe cannot send Bearer). Actions: approve (REVIEW_REQUIRED
 * only → expenses list), delete pending, retry processing, continue manually after failure.
 */
import { api, apiBlob } from '../api.js';
import { isLoggedIn } from '../auth.js';
import { navigate } from '../router.js';

/** Product is EUR-only; column stays, UI no longer offers USD/GBP. */
const CURRENCY = 'EUR';

/** Revoke the last preview blob URL so repeated visits do not leak memory. */
let activePreviewUrl = null;

export function renderReviewPage(root, documentId) {
  if (!isLoggedIn()) {
    navigate('/login');
    return;
  }

  revokeActivePreviewUrl();

  root.innerHTML = `
    <main class="page page--wide">
      <h1>Review document</h1>
      <p id="review-status-line" class="subtitle">Loading...</p>
      <p id="review-error" class="form-error" hidden></p>

      <div id="review-content" class="review-layout" hidden>
        <section class="review-preview">
          <h2>Preview</h2>
          <div id="preview-container" class="preview-container">
            <p class="preview-placeholder">Loading preview...</p>
          </div>
          <p id="review-file-meta" class="review-meta"></p>
        </section>

        <section class="review-form-section">
          <h2>Proposed details</h2>
          <p id="review-form-note" class="review-note" hidden></p>
          <form id="review-form" class="auth-form">
            <label>
              Merchant
              <input type="text" name="merchant" autocomplete="off" />
            </label>
            <label>
              Date
              <input type="date" name="date" />
            </label>
            <label>
              Amount
              <input type="number" name="amount" step="0.01" min="0" />
            </label>
            <label>
              Currency
              <input type="text" name="currency" value="${CURRENCY}" readonly />
            </label>
            <label>
              Category
              <select name="categoryId">
                <option value="">— Select category —</option>
              </select>
            </label>
          </form>

          <div id="review-failed-actions" class="review-actions" hidden>
            <button type="button" id="retry-btn">Retry processing</button>
            <button type="button" id="continue-manual-btn">Continue manually</button>
          </div>

          <div class="review-actions">
            <button type="button" id="approve-btn" hidden>Approve</button>
            <button type="button" id="delete-btn" class="button-danger">Delete pending document</button>
          </div>
          <p id="review-action-status" class="categories-status"></p>
        </section>
      </div>

      <p class="auth-switch">
        <a href="#/upload">Upload another</a>
        ·
        <a href="#/">Back to home</a>
      </p>
    </main>
  `;

  const statusLineEl = root.querySelector('#review-status-line');
  const errorEl = root.querySelector('#review-error');
  const contentEl = root.querySelector('#review-content');
  const previewContainerEl = root.querySelector('#preview-container');
  const fileMetaEl = root.querySelector('#review-file-meta');
  const formNoteEl = root.querySelector('#review-form-note');
  const formEl = root.querySelector('#review-form');
  const categorySelectEl = formEl.querySelector('select[name="categoryId"]');
  const failedActionsEl = root.querySelector('#review-failed-actions');
  const retryBtn = root.querySelector('#retry-btn');
  const continueManualBtn = root.querySelector('#continue-manual-btn');
  const approveBtn = root.querySelector('#approve-btn');
  const deleteBtn = root.querySelector('#delete-btn');
  const actionStatusEl = root.querySelector('#review-action-status');

  let currentDocument = null;

  retryBtn.addEventListener('click', () => runAction('retry'));
  continueManualBtn.addEventListener('click', () => runAction('continue-manual'));
  approveBtn.addEventListener('click', () => runApprove());
  deleteBtn.addEventListener('click', () => runAction('delete'));

  loadPage();

  async function loadPage() {
    errorEl.hidden = true;
    actionStatusEl.textContent = '';
    contentEl.hidden = true;
    statusLineEl.textContent = 'Loading...';

    try {
      const [document, categories] = await Promise.all([
        api(`/documents/${documentId}`),
        api('/categories'),
      ]);

      currentDocument = document;
      populateCategoryOptions(categories);
      applyDocumentToUi(document);
      await loadPreview(document);
      contentEl.hidden = false;
    } catch (error) {
      if (error.status === 401) {
        navigate('/login');
        return;
      }
      statusLineEl.textContent = '';
      errorEl.textContent =
        error.status === 404
          ? 'Document not found or you do not have access.'
          : error.message || 'Could not load document';
      errorEl.hidden = false;
    }
  }

  function populateCategoryOptions(categories) {
    // Whole-receipt category is a group. Leaves are chosen on a line later.
    const groups = categories.filter((category) => category.parentId == null);
    const options = groups
      .map((category) => `<option value="${category.id}">${category.name}</option>`)
      .join('');
    categorySelectEl.insertAdjacentHTML('beforeend', options);
  }

  function applyDocumentToUi(document) {
    statusLineEl.textContent = `${document.originalFilename} — ${document.status}`;
    fileMetaEl.textContent =
      `${document.mimeType}, ${formatBytes(document.fileSizeBytes)}`;

    const extraction = document.extraction;
    formEl.querySelector('[name="merchant"]').value = extraction?.proposedMerchant ?? '';
    formEl.querySelector('[name="date"]').value = extraction?.proposedDate ?? '';
    formEl.querySelector('[name="amount"]').value =
      extraction?.proposedAmount != null ? String(extraction.proposedAmount) : '';
    formEl.querySelector('[name="currency"]').value = CURRENCY;
    categorySelectEl.value =
      extraction?.proposedCategoryId != null ? String(extraction.proposedCategoryId) : '';

    const isFailed = document.status === 'PROCESSING_FAILED';
    const canApprove = document.status === 'REVIEW_REQUIRED';
    failedActionsEl.hidden = !isFailed;
    // Approve only when the document is ready for review (hidden for PROCESSING_FAILED / SAVED).
    approveBtn.hidden = !canApprove;

    if (isFailed) {
      formNoteEl.textContent =
        'Processing failed. Retry to run extraction again, or continue manually to fill the form yourself.';
      formNoteEl.hidden = false;
    } else if (canApprove) {
      formNoteEl.textContent =
        'Review and edit the proposed fields, then Approve to save as an expense.';
      formNoteEl.hidden = false;
    } else {
      formNoteEl.hidden = true;
    }
  }

  async function loadPreview(document) {
    previewContainerEl.innerHTML = '<p class="preview-placeholder">Loading preview...</p>';
    revokeActivePreviewUrl();

    try {
      const blob = await apiBlob(`/documents/${document.id}/file`);
      activePreviewUrl = URL.createObjectURL(blob);
      previewContainerEl.innerHTML = buildPreviewElement(document.mimeType, activePreviewUrl);
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
   * POST confirmed form fields to approve. Client checks required fields first;
   * backend remains the authority for validation and status rules.
   */
  async function runApprove() {
    errorEl.hidden = true;
    actionStatusEl.textContent = '';

    const merchant = formEl.querySelector('[name="merchant"]').value.trim();
    const expenseDate = formEl.querySelector('[name="date"]').value;
    const amountRaw = formEl.querySelector('[name="amount"]').value;
    const categoryIdRaw = formEl.querySelector('[name="categoryId"]').value;

    if (!expenseDate || !amountRaw || !categoryIdRaw) {
      errorEl.textContent = 'Date, amount, and category are required before approve.';
      errorEl.hidden = false;
      return;
    }

    const totalAmount = Number(amountRaw);
    if (!(totalAmount > 0)) {
      errorEl.textContent = 'Amount must be greater than zero.';
      errorEl.hidden = false;
      return;
    }

    actionStatusEl.textContent = 'Approving...';
    approveBtn.disabled = true;

    try {
      await api(`/documents/${documentId}/approve`, {
        method: 'POST',
        body: JSON.stringify({
          expenseDate,
          totalAmount,
          currency: CURRENCY,
          categoryId: Number(categoryIdRaw),
          merchant: merchant || null,
        }),
      });
      navigate('/expenses');
    } catch (error) {
      if (error.status === 401) {
        navigate('/login');
        return;
      }
      actionStatusEl.textContent = '';
      errorEl.textContent = error.message || 'Approve failed';
      errorEl.hidden = false;
      approveBtn.disabled = false;
    }
  }

  async function runAction(action) {
    actionStatusEl.textContent = 'Working...';
    errorEl.hidden = true;

    try {
      if (action === 'delete') {
        const confirmed = window.confirm(
          'Delete this document permanently? This cannot be undone.',
        );
        if (!confirmed) {
          actionStatusEl.textContent = '';
          return;
        }
        await api(`/documents/${documentId}`, { method: 'DELETE' });
        navigate('/upload');
        return;
      }

      const path =
        action === 'retry'
          ? `/documents/${documentId}/process`
          : `/documents/${documentId}/continue-manual`;

      const document = await api(path, { method: 'POST' });
      currentDocument = document;
      applyDocumentToUi(document);
      actionStatusEl.textContent =
        action === 'retry' ? 'Processing retried.' : 'Ready for manual entry.';
    } catch (error) {
      if (error.status === 401) {
        navigate('/login');
        return;
      }
      actionStatusEl.textContent = '';
      errorEl.textContent = error.message || 'Action failed';
      errorEl.hidden = false;
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
  return `<p class="preview-placeholder">Preview not supported for ${mimeType}</p>`;
}

function formatBytes(bytes) {
  if (bytes == null) {
    return 'unknown size';
  }
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  return `${(bytes / 1024).toFixed(1)} KB`;
}

function revokeActivePreviewUrl() {
  if (activePreviewUrl) {
    URL.revokeObjectURL(activePreviewUrl);
    activePreviewUrl = null;
  }
}
