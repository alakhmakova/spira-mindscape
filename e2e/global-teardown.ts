import { existsSync, readFileSync, rmSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/**
 * Deletes every goal `createGoal` (in `helpers.ts`) made during this run.
 *
 * BUG-073: 13 of 14 goals in the local dev DB were leftover E2E fixtures — "E2E img
 * 1788185140243", "Filters A 1788185040841", and so on — because nothing ever cleaned them up
 * between runs. Each `createGoal` call appends its real goal id to `.created-goals.ndjson`; this
 * runs once after the whole suite and deletes exactly those ids, nothing guessed from a title.
 *
 * CSRF is disabled under both the `local` and `e2e` backend profiles (see
 * `SecurityConfig.java` — "Either way the login is stateless per request, so CSRF is disabled"),
 * so a plain `fetch` mutation needs no token here, only the same auth this suite already uses:
 * the `local` profile auto-authenticates every request as `dev@local`, and CI's `e2e` profile
 * reads `X-E2E-Auth` the same way `playwright.config.ts` sets it for the browser.
 */
const CREATED_GOALS_LOG = path.join(__dirname, ".created-goals.ndjson");

export default async function globalTeardown() {
  if (!existsSync(CREATED_GOALS_LOG)) return;

  const lines = readFileSync(CREATED_GOALS_LOG, "utf-8")
    .split("\n")
    .map((l) => l.trim())
    .filter(Boolean);
  const ids = [
    ...new Set(
      lines
        .map((l) => {
          try {
            return (JSON.parse(l) as { id?: string }).id;
          } catch {
            return undefined;
          }
        })
        .filter((id): id is string => !!id),
    ),
  ];
  // The file is removed regardless of what follows — a delete that fails against a backend that
  // isn't up any more must not leave stale entries for the *next* run to trip over.
  rmSync(CREATED_GOALS_LOG, { force: true });
  if (ids.length === 0) return;

  const e2eAuthEmail = process.env.SPIRA_E2E_AUTH;
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };
  if (e2eAuthEmail) headers["X-E2E-Auth"] = e2eAuthEmail;

  const url = `${process.env.SPIRA_BASE_URL ?? "http://localhost:5173"}/graphql`;
  const mutation = "mutation($id: ID!) { deleteGoal(id: $id) }";

  // **A delete that came back 401 or with a GraphQL error is a delete that did not happen.**
  // `allSettled` only calls the network itself rejecting a "failure", so a systematically broken
  // teardown — wrong auth header, CSRF on, wrong port — reported perfect success while leaving
  // every fixture behind, which is BUG-073 quietly un-fixed.
  const results = await Promise.allSettled(
    ids.map(async (id) => {
      const res = await fetch(url, {
        method: "POST",
        headers,
        body: JSON.stringify({ query: mutation, variables: { id } }),
      });
      if (!res.ok) throw new Error(`${id}: HTTP ${res.status}`);
      const body = (await res.json()) as {
        errors?: Array<{ message?: string }>;
      };
      if (body.errors?.length) {
        throw new Error(`${id}: ${body.errors[0]?.message ?? "GraphQL error"}`);
      }
      return id;
    }),
  );
  const failures = results.flatMap((r) =>
    r.status === "rejected" ? [String(r.reason)] : [],
  );
  if (failures.length > 0) {
    // Best-effort cleanup: a failure here must never fail the whole E2E run. Worst case, a
    // stray fixture goal is left behind — but say WHICH, and why, so it can be chased.
    console.warn(
      `[global-teardown] failed to delete ${failures.length}/${ids.length} E2E fixture goal(s):`,
      failures,
    );
  }
}
