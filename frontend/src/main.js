/**
 * Main Responsibility: App entry point — wire CSS and hash routes to page renderers.
 *
 * Unknown routes fall through to the home page.
 */
import './style.css';
import { startRouter } from './router.js';
import { renderHomePage } from './pages/home.js';
import { renderLoginPage } from './pages/login.js';
import { renderRegisterPage } from './pages/register.js';

const app = document.getElementById('app');

startRouter((route) => {
  if (route === '/login') {
    renderLoginPage(app);
    return;
  }
  if (route === '/register') {
    renderRegisterPage(app);
    return;
  }
  renderHomePage(app);
});
