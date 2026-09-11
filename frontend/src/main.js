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
import { renderUploadPage } from './pages/upload.js';
import { renderReviewPage } from './pages/review.js';
import { renderExpensesPage } from './pages/expenses.js';
import { renderExpenseDetailPage } from './pages/expense-detail.js';

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
  if (route === '/upload') {
    renderUploadPage(app);
    return;
  }
  const reviewMatch = route.match(/^\/review\/(\d+)$/);
  if (reviewMatch) {
    renderReviewPage(app, reviewMatch[1]);
    return;
  }
  if (route === '/expenses') {
    renderExpensesPage(app);
    return;
  }
  const expenseMatch = route.match(/^\/expenses\/(\d+)$/);
  if (expenseMatch) {
    renderExpenseDetailPage(app, expenseMatch[1]);
    return;
  }
  renderHomePage(app);
});
