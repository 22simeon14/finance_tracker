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
