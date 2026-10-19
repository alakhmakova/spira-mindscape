import { beforeEach, describe, expect, it } from "vitest";

import {
  markNotificationsHintSeen,
  shouldOfferNotificationsHint,
} from "./notifications-hint";

describe("shouldOfferNotificationsHint", () => {
  beforeEach(() => window.localStorage.clear());

  it("offers it to a browser that has not allowed notifications, once", () => {
    expect(shouldOfferNotificationsHint("ask")).toBe(true);
    expect(shouldOfferNotificationsHint("denied")).toBe(true);
    markNotificationsHintSeen();
    expect(shouldOfferNotificationsHint("ask")).toBe(false);
    expect(shouldOfferNotificationsHint("denied")).toBe(false);
  });

  it("never offers it where Settings could not turn notifications on, or they are on", () => {
    for (const state of ["granted", "off", "insecure", "unsupported"] as const)
      expect(shouldOfferNotificationsHint(state)).toBe(false);
    // Not known yet: the click must not wait for it, so it goes straight to the form.
    expect(shouldOfferNotificationsHint(null)).toBe(false);
  });
});
