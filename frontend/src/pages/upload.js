/**
 * Main Responsibility: Upload page — send a receipt file to POST /documents.
 *
 * Logged-in only. Validates MIME and size in the browser, then redirects to
 * the review page for the created document id.
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
        <button type="submit" id="upload-submit">Upload</button>
      </form>

      <p class="auth-switch">
        <a href="#/documents">Pending inbox</a>
        ·
        <a href="#/expenses">Expenses</a>
        ·
        <a href="#/">Back to home</a>
      </p>
    </main>
  `;

  const form = root.querySelector('#upload-form');
  const errorEl = root.querySelector('#upload-error');
  const submitBtn = root.querySelector('#upload-submit');

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    errorEl.hidden = true;

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

    submitBtn.disabled = true;
    submitBtn.textContent = 'Uploading...';

    try {
      const document = await api('/documents', {
        method: 'POST',
        body,
      });

      navigate(`/review/${document.id}`);
    } catch (error) {
      if (error.status === 401) {
        navigate('/login');
        return;
      }
      errorEl.textContent = error.message || 'Upload failed';
      errorEl.hidden = false;
    } finally {
      submitBtn.disabled = false;
      submitBtn.textContent = 'Upload';
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
