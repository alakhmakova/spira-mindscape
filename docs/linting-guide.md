# Linting Guide

Linting is an automated code quality check.

A linter reads source files and reports problems before the code is run in the browser or shipped to users. It catches issues such as formatting mistakes, unsafe React hook usage, unused variables, inconsistent imports, and project-specific code style violations.

In this project, linting is handled by ESLint with TypeScript, React Hooks, React Refresh, and Prettier rules.

## Why Linting Matters

Linting helps keep the codebase:

- consistent, so files are easier to read and review;
- safer, because common React and TypeScript mistakes are caught early;
- easier to maintain, because style decisions are automated instead of debated manually;
- CI-ready, because the same command can be run locally and in automated checks.

Linting is especially useful in a React app because small mistakes in hooks, exports, or formatting can create confusing runtime behavior later.

## How To Run It

From the repository root:

```powershell
npm.cmd run lint
```

On Windows PowerShell, `npm.cmd` is often safer than `npm` because local script execution policies can block `npm.ps1`.

## How To Read Lint Output

Lint output usually shows:

- the file path;
- the line and column number;
- whether the issue is an `error` or a `warning`;
- the rule that reported it.

Example:

```text
src/components/example.tsx
  12:5  error  Replace formatting  prettier/prettier
```

An `error` should be fixed before considering the code ready.

A `warning` may not fail the command, but it still points to something worth reviewing.

## Linting vs Tests

Linting and tests do different jobs.

Linting checks code structure, formatting, and common static mistakes.

Tests check behavior: whether functions, components, APIs, and integrations do what they are supposed to do.

Both are needed. A file can pass lint and still have a behavioral bug. A feature can pass tests but still have messy or risky code.

## Formatting And Prettier

Prettier is the formatter used by the lint setup.

When ESLint reports `prettier/prettier`, the code usually has formatting or line-ending differences. These can often be fixed by running:

```powershell
npm.cmd run format
```

For a smaller change, format only the affected file:

```powershell
node_modules\.bin\prettier.cmd --write path\to\file.tsx
```

## Current Project Rules

The lint setup lives in:

```text
eslint.config.js
```

The formatter configuration lives in:

```text
.prettierrc
```

The current lint setup includes:

- TypeScript ESLint rules;
- React Hooks rules;
- React Fast Refresh rules;
- Prettier formatting rules.

Some generated or build output folders are ignored because they should not be linted.
