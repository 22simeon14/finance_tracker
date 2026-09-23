/**
 * Main Responsibility: Pending-documents inbox — list non-SAVED docs and forever-delete.
 *
 * Loads GET /documents?status=pending. Row opens review; Delete uses existing
 * DELETE /documents/{id} (removes row + file). Empty state when nothing pending.
 * Does not replace re-upload. Login required.
 */
import { api } from '../api.js';
import { isLoggedIn } from '../auth.js';
import { navigate } from '../router.js';

export function renderDocumentsPage(root) {
  if (!isLoggedIn()) {
    navigate('/login');
    return;
  }

  root.innerHTML = `
    <main class="page">
      <h1>Pending inbox</h1>
      <p class="subtitle">Documents waiting for review (not yet saved as expenses)</p>
      <p id="documents-error" class="form-error" hidden></p>
      <p id="documents-status" class="categories-status">Loading...</p>
      <ul id="documents-list" class="documents-list"></ul>
      <p id="documents-empty" class="expenses-empty" hidden>No pending documents.</p>
      <p class="auth-switch">
        <a href="#/upload">Upload document</a>
        ·
        <a href="#/expenses">Expenses</a>
        ·
        <a href="#/dashboard">Dashboard</a>
        ·
        <a href="#/">Back to home</a>
      </p>
    </main>
  `;

  const errorEl = root.querySelector('#documents-error');
  const statusEl = root.querySelector('#documents-status');
  const listEl = root.querySelector('#documents-list');
  const emptyEl = root.querySelector('#documents-empty');

  loadDocuments();

  async function loadDocuments() {
    errorEl.hidden = true;
    emptyEl.hidden = true;
    listEl.innerHTML = '';
    statusEl.textContent = 'Loading...';

    try {
      const documents = await api('/documents?status=pending');
      statusEl.textContent = '';

      if (!documents.length) {
        emptyEl.hidden = false;
        return;
      }

      listEl.innerHTML = documents
        .map(
          (doc) => `
            <li class="document-row">
              <a class="document-row__main" href="#/review/${doc.id}">
                <span class="document-row__name">${escapeHtml(doc.originalFilename || 'Untitled')}</span>
                <span class="document-row__status">${escapeHtml(formatStatus(doc.status))}</span>
                <span class="document-row__date">${escapeHtml(formatCreatedAt(doc.createdAt))}</span>
              </a>
              <button
                type="button"
                class="button-danger document-row__delete"
                data-id="${escapeHtml(String(doc.id))}"
                data-name="${escapeHtml(doc.originalFilename || 'this document')}"
              >
                Delete forever
              </button>
            </li>
          `,
        )
        .join('');

      listEl.querySelectorAll('.document-row__delete').forEach((button) => {
        button.addEventListener('click', () => {
          const id = button.getAttribute('data-id');
          const name = button.getAttribute('data-name') || 'this document';
          deleteForever(id, name, button);
        });
      });
    } catch (error) {
      if (error.status === 401) {
        navigate('/login');
        return;
      }
      statusEl.textContent = '';
      errorEl.textContent = error.message || 'Could not load pending documents';
      errorEl.hidden = false;
    }
  }

  /**
   * Permanent wipe via existing DELETE /documents/{id}.
   * Confirm copy is honest — file and row are removed, not recoverable.
   */
  async function deleteForever(documentId, displayName, button) {
    const confirmed = window.confirm(
      `Delete "${displayName}" forever? The file will be removed and this cannot be undone.`,
    );
    if (!confirmed) {
      return;
    }

    errorEl.hidden = true;
    button.disabled = true;

    try {
      await api(`/documents/${documentId}`, { method: 'DELETE' });
      await loadDocuments();
    } catch (error) {
      if (error.status === 401) {
        navigate('/login');
        return;
      }
      button.disabled = false;
      errorEl.textContent = error.message || 'Delete failed';
      errorEl.hidden = false;
    }
  }
}

/** Show status codes in a short readable form. */
function formatStatus(status) {
  if (!status) {
    return '—';
  }
  return String(status).replace(/_/g, ' ');
}

/** Prefer date+time from ISO string; fall back to raw value. */
function formatCreatedAt(value) {
  if (!value) {
    return '—';
  }
  const text = String(value);
  // "2026-09-15T14:30:00" → "2026-09-15 14:30"
  if (text.length >= 16 && text.includes('T')) {
    return `${text.slice(0, 10)} ${text.slice(11, 16)}`;
  }
  return text;
}

/** Escape text before inserting into HTML attributes/content. */
function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}
