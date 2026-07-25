import { api } from '../api.js';
import { setToken } from '../auth.js';
import { navigate } from '../router.js';

export function renderLoginPage(root) {
  root.innerHTML = `
    <main class="page">
      <h1>Log in</h1>
      <p class="subtitle">Sign in to your Finance Tracker account</p>
      <form id="login-form" class="auth-form">
        <label>
          Email
          <input type="email" name="email" required autocomplete="email" />
        </label>
        <label>
          Password
          <input type="password" name="password" required autocomplete="current-password" />
        </label>
        <p id="login-error" class="form-error" hidden></p>
        <button type="submit">Log in</button>
      </form>
      <p class="auth-switch">
        No account?
        <a href="#/register">Register</a>
      </p>
    </main>
  `;

  const form = root.querySelector('#login-form');
  const errorEl = root.querySelector('#login-error');

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    errorEl.hidden = true;

    const formData = new FormData(form);
    const email = String(formData.get('email') || '').trim();
    const password = String(formData.get('password') || '');

    try {
      const data = await api('/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      });
      setToken(data.token);
      navigate('/');
    } catch (error) {
      errorEl.textContent = error.message || 'Login failed';
      errorEl.hidden = false;
    }
  });
}
