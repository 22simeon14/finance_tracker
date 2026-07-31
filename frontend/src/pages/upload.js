/**
 * Main Responsibility: Upload page — send a receipt file to POST /documents.
 *
 * Logged-in only. Checks MIME and size in the browser before upload, then
 * shows the created document metadata. Optional verify calls GET /documents/{id}.
 */
import { api } from '../api.js';
import { isLoggedIn } from '../auth.js';
import { navigate } from '../router.js';

const ALLOWED_MIME_TYPES = new Set(['image/jpeg', 'image/png', 'application/pdf']);
const MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;

export function renderUploadPage(root) {
  if (!isLoggedIn()) {
    navigate('/login');
    return;
  }

  root.innerHTML = `
    <main class="page">
      <h1>Upload document</h1>
      <p class="subtitle">JPEG, PNG, or PDF — max 5 MB</p>

      <form id="upload-form" class="auth-form">
        <label>
          File
          <input
            type="file"
            name="file"
            accept="image/jpeg,image/png,application/pdf,.jpg,.jpeg,.png,.pdf"
            required
          />
        </label>
        <p id="upload-error" class="form-error" hidden></p>
        <button type="submit">Upload</button>
      </form>

      <section id="upload-result" class="health-card" hidden>
        <h2>Uploaded</h2>
        <p id="upload-result-summary" class="account-status"></p>
        <button type="button" id="verify-btn">Verify with GET /documents/{id}</button>
        <p id="verify-status" class="categories-status"></p>
      </section>

      <p class="auth-switch">
        <a href="#/">Back to home</a>
      </p>
    </main>
  `;

  const form = root.querySelector('#upload-form');
  const errorEl = root.querySelector('#upload-error');
  const resultSection = root.querySelector('#upload-result');
  const resultSummaryEl = root.querySelector('#upload-result-summary');
  const verifyBtn = root.querySelector('#verify-btn');
  const verifyStatusEl = root.querySelector('#verify-status');

  let lastDocumentId = null;

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    errorEl.hidden = true;
    resultSection.hidden = true;
    verifyStatusEl.textContent = '';
    lastDocumentId = null;

    const fileInput = form.querySelector('input[name="file"]');
    const file = fileInput.files?.[0];

    const clientError = validateFileClientSide(file);
    if (clientError) {
      errorEl.textContent = clientError;
      errorEl.hidden = false;
      return;
    }

    const body = new FormData();
    body.append('file', file);

    try {
      const document = await api('/documents', {
        method: 'POST',
        body,
      });

      lastDocumentId = document.id;
      resultSummaryEl.textContent =
        `id=${document.id}, status=${document.status}, ` +
        `file=${document.originalFilename}, type=${document.mimeType}, ` +
        `size=${document.fileSizeBytes} bytes`;
      resultSection.hidden = false;
    } catch (error) {
      // Token may already be cleared by api() on 401 — send user to login.
      if (error.status === 401) {
        navigate('/login');
        return;
      }
      errorEl.textContent = error.message || 'Upload failed';
      errorEl.hidden = false;
    }
  });

  verifyBtn.addEventListener('click', async () => {
    if (lastDocumentId == null) {
      return;
    }

    verifyStatusEl.textContent = 'Loading...';

    try {
      const document = await api(`/documents/${lastDocumentId}`);
      verifyStatusEl.textContent =
        `Verified: id=${document.id}, status=${document.status}, ` +
        `file=${document.originalFilename}`;
    } catch (error) {
      if (error.status === 401) {
        navigate('/login');
        return;
      }
      verifyStatusEl.textContent = error.message || 'Verify failed';
    }
  });
}

/** Match server rules so bad files fail before the request leaves the browser. */
function validateFileClientSide(file) {
  if (!file) {
    return 'File is required';
  }
  if (file.size > MAX_FILE_SIZE_BYTES) {
    return 'File must be 5 MB or smaller';
  }
  if (!ALLOWED_MIME_TYPES.has(file.type)) {
    return 'Unsupported file type (use JPEG, PNG, or PDF)';
  }
  return null;
}
