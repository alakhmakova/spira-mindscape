import js from "@eslint/js";
import eslintPluginPrettier from "eslint-plugin-prettier/recommended";
import globals from "globals";
import jsxA11y from "eslint-plugin-jsx-a11y";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import tseslint from "typescript-eslint";

export default tseslint.config(
  { ignores: ["dist", ".output", ".vinxi", "backend", "android"] },
  {
    extends: [
      js.configs.recommended,
      ...tseslint.configs.recommended,
      // The static half of the accessibility work (BUG-023). It reads JSX and never runs it, so
      // it sees every screen — including the ones no E2E spec opens — but only what is decidable
      // from the markup: a handler on a `<div>`, an `<img>` with no `alt`, an `aria-*` that is not
      // a real attribute, a label with nothing bound to it. `e2e/accessibility.spec.ts` is the
      // other half and needs a rendered page; neither replaces the other.
      jsxA11y.flatConfigs.recommended,
    ],
    files: ["**/*.{ts,tsx}"],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
    },
    plugins: {
      "react-hooks": reactHooks,
      "react-refresh": reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      "react-refresh/only-export-components": [
        "warn",
        { allowConstantExport: true },
      ],
      "@typescript-eslint/no-unused-vars": "off",
    },
  },
  {
    files: ["src/components/ui/**/*.{ts,tsx}", "src/router.tsx"],
    rules: {
      "react-refresh/only-export-components": "off",
    },
  },
  {
    // The shadcn primitives are wrappers: `AlertTitle` renders `<h5 {...props} />` and its content
    // arrives from whoever uses it. A rule that reads the markup cannot see through that, so it
    // reports every one of them as an empty heading. The call sites are linted normally, which is
    // where an actually empty heading would be written.
    files: ["src/components/ui/**/*.{ts,tsx}"],
    rules: {
      "jsx-a11y/heading-has-content": "off",
    },
  },
  eslintPluginPrettier,
);
