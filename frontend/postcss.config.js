/**
 * Main Responsibility: PostCSS pipeline that compiles Tailwind for Vite.
 *
 * Vite loads this file automatically. autoprefixer adds vendor prefixes
 * after Tailwind emits the final CSS.
 */
export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
};
