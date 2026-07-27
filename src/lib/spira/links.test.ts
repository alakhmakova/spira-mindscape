import { describe, it, expect } from "vitest";
import { splitUrls, isSafeHttpUrl } from "./links";

describe("splitUrls", () => {
  it("splits text and a URL", () => {
    expect(splitUrls("see https://example.com/x now")).toEqual([
      { type: "text", value: "see " },
      { type: "url", url: "https://example.com/x" },
      { type: "text", value: " now" },
    ]);
  });

  it("returns a single text segment when there is no URL", () => {
    expect(splitUrls("plain words")).toEqual([
      { type: "text", value: "plain words" },
    ]);
  });

  it("keeps trailing sentence punctuation out of the link", () => {
    expect(splitUrls("go to https://example.com.")).toEqual([
      { type: "text", value: "go to " },
      { type: "url", url: "https://example.com" },
      { type: "text", value: "." },
    ]);
  });

  it("handles multiple URLs", () => {
    const urls = splitUrls("https://a.com and https://b.com").filter(
      (s) => s.type === "url",
    );
    expect(urls).toHaveLength(2);
  });
});

describe("isSafeHttpUrl", () => {
  it("allows http and https", () => {
    expect(isSafeHttpUrl("https://example.com")).toBe(true);
    expect(isSafeHttpUrl("http://example.com")).toBe(true);
  });

  it("blocks javascript: and data: (XSS boundary)", () => {
    expect(isSafeHttpUrl("javascript:alert(1)")).toBe(false);
    expect(isSafeHttpUrl("data:text/html,<script>")).toBe(false);
    expect(isSafeHttpUrl("not a url")).toBe(false);
  });
});
