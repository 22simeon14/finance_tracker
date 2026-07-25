import { api } from '../api.js';
import { setToken } from '../auth.js';
import { navigate } from '../router.js';

export function renderRegisterPage(root) {
  root.innerHTML = `
    <main class="page">
      <h1>Register</h1>
      <p class="subtitle">Create a Finance Tracker account</p>
      <form id="register-form" class="auth-form">
        <label>
          Email
          <input type="email" name="email" required autocomplete="email" />
        </label>
        <label>
          Password
          <input type="password" name="password" required minlength="8" autocomplete="new-password" />
        </label>
        <p id="register-error" class="form-error" hidden></p>
        <button type="submit">Create account</button>
      </form>
      <p class="auth-switch">
        Already have an account?
        <a href="#/login">Log in</a>
      </p>
    </main>
  `;

  const form = root.querySelector('#register-form');
  const errorEl = root.querySelector('#register-error');

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    errorEl.hidden = true;

    const formData = new FormData(form);
    const email = String(formData.get('email') || '').trim();
    const password = String(formData.get('password') || '');

    try {
      const data = await api('/auth/register', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      });
      setToken(data.token);
      navigate('/');
    } catch (error) {
      errorEl.textContent = error.message || 'Registration failed';
      errorEl.hidden = false;
    }
  });
}
