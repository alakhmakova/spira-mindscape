/**
 * Point git at the hooks the repository ships (BUG-095).
 *
 * Git does not clone hooks — `.git/hooks` is local to each machine — so a hook is worth nothing
 * until every clone is told where the tracked ones live. `core.hooksPath` is that instruction,
 * and this sets it.
 *
 * It runs from `npm install` (the `prepare` script) and **never fails the install**: a machine
 * without git, a tarball install, a CI checkout with no hooks needed — none of those is a reason
 * to stop someone installing dependencies. It prints what it did and exits 0 regardless.
 *
 * The one failure it cannot catch itself is not existing. The production image copies
 * `package.json` and the lockfile **before** the sources, so `npm ci` runs `prepare` in a layer
 * where this file is absent and node exits 1 — which broke the Cloud Run build until `prepare`
 * was made `... || exit 0` (2026-09-30).
 */
import { execFileSync } from "node:child_process";
import { existsSync } from "node:fs";

const HOOKS = ".githooks";

function git(...args) {
  return execFileSync("git", args, {
    encoding: "utf8",
    stdio: ["ignore", "pipe", "ignore"],
  }).trim();
}

try {
  if (!existsSync(HOOKS)) process.exit(0);
  if (git("rev-parse", "--is-inside-work-tree") !== "true") process.exit(0);

  let current = "";
  try {
    current = git("config", "--get", "core.hooksPath");
  } catch {
    // Unset — which is the normal state before this has ever run.
  }
  if (current === HOOKS) process.exit(0);
  if (current) {
    // Somebody has pointed git somewhere else on purpose; say so rather than overruling them.
    console.log(
      `[hooks] core.hooksPath is already "${current}" — leaving it. ` +
        `The secret-scanning hook lives in ${HOOKS}.`,
    );
    process.exit(0);
  }

  git("config", "core.hooksPath", HOOKS);
  console.log(
    `[hooks] core.hooksPath -> ${HOOKS} (pre-commit scans staged changes for secrets)`,
  );
} catch {
  // No git, no repository, no permission — all fine. CI scans every push either way.
}
