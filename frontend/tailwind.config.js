/**
 * Main Responsibility: Tailwind content paths and app color tokens.
 *
 * content tells Tailwind which JS templates to scan. theme.extend keeps the
 * existing Finance Tracker palette so pages can keep their current class names.
 */
/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.js'],
  theme: {
    extend: {
      colors: {
        'app-bg': '#f4f6f8',
        'app-ink': '#1a1a1a',
        'app-muted': '#555555',
        'app-card': '#ffffff',
        'app-border': '#dddddd',
        'app-ok': '#0a7a2f',
        'app-danger': '#b00020',
        'app-link': '#0b5fff',
        'app-notice': '#eef6ff',
        'app-notice-border': '#b6d4fe',
        'app-notice-ink': '#1a3a5c',
        'app-input': '#cccccc',
        'app-preview': '#f9fafb',
        'app-divider': '#eeeeee',
      },
      fontFamily: {
        sans: ['system-ui', '-apple-system', 'sans-serif'],
      },
    },
  },
  plugins: [],
};
