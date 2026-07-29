// @ts-check
import eslint from '@eslint/js';
import json from '@eslint/json';
import markdown from '@eslint/markdown';
import eslintPluginPrettierRecommended from 'eslint-plugin-prettier/recommended';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  {
    ignores: ['eslint.config.mjs', '.angular/**', '.vscode/**', 'coverage/**', 'dist/**'],
  },

  // ── TypeScript ─────────────────────────────────────────────────────────────
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      ...tseslint.configs.recommendedTypeChecked,
      eslintPluginPrettierRecommended,
    ],
    languageOptions: {
      globals: {
        ...globals.browser,
        ...globals.node,
        ...globals.vitest,
      },
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    rules: {
      '@typescript-eslint/consistent-type-imports': 'warn',
      '@typescript-eslint/no-floating-promises': 'error',
      '@typescript-eslint/no-misused-promises': 'error',
      'eol-last': ['error', 'always'],
      'linebreak-style': ['error', 'unix'],
      'max-len': ['error', 150],
      'newline-before-return': 'error',
      'no-multiple-empty-lines': ['error'],
      'prettier/prettier': ['error', { endOfLine: 'lf' }],
    },
  },

  // ── JSON ───────────────────────────────────────────────────────────────────
  {
    ...json.configs.recommended,
    files: ['**/*.json'],
    ignores: ['tsconfig*.json'],
    language: 'json/json',
  },

  // ── JSONC (tsconfig) ───────────────────────────────────────────────────────
  {
    ...json.configs.recommended,
    files: ['tsconfig*.json'],
    language: 'json/jsonc',
  },

  // ── Markdown ───────────────────────────────────────────────────────────────
  ...markdown.configs.recommended,
);
