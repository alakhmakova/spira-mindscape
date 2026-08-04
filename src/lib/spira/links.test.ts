import { describe, it, expect } from "vitest";
import {
  splitInline,
  isSafeHttpUrl,
  hasResourceToken,
  resourceIdsIn,
  replaceResourceToken,
  resourceToken,
  stripResourceTokens,
} from "./links";

describe("splitInline", () => {
  it("splits text and a URL", () => {
    expect(splitInline("see https://example.com/x now")).toEqual([
      { type: "text", value: "see " },
      { type: "url", url: "https://example.com/x" },
      { type: "text", value: " now" },
    ]);
  });

  it("returns a single text segment when there is no URL", () => {
    expect(splitInline("plain words")).toEqual([
      { type: "text", value: "plain words" },
    ]);
  });

  it("keeps trailing sentence punctuation out of the link", () => {
    expect(splitInline("go to https://example.com.")).toEqual([
      { type: "text", value: "go to " },
      { type: "url", url: "https://example.com" },
      { type: "text", value: "." },
    ]);
  });

  it("handles multiple URLs", () => {
    const urls = splitInline("https://a.com and https://b.com").filter(
      (s) => s.type === "url",
    );
    expect(urls).toHaveLength(2);
  });

  it("emits a resource segment for an attachment token", () => {
    expect(splitInline("Read {{res:42}} first")).toEqual([
      { type: "text", value: "Read " },
      { type: "resource", id: "42" },
      { type: "text", value: " first" },
    ]);
  });

  it("handles an optimistic local id and a URL in the same value", () => {
    expect(splitInline("{{res:local-ab12}} https://a.com")).toEqual([
      { type: "resource", id: "local-ab12" },
      { type: "text", value: " " },
      { type: "url", url: "https://a.com" },
    ]);
  });

  it("leaves a malformed token as plain text", () => {
    expect(splitInline("{{res:}} and {{ res:1 }}")).toEqual([
      { type: "text", value: "{{res:}} and {{ res:1 }}" },
    ]);
  });
});

describe("resource tokens", () => {
  it("detects and lists referenced ids without duplicates", () => {
    const text = `a ${resourceToken("7")} b ${resourceToken("9")} c ${resourceToken("7")}`;
    expect(hasResourceToken(text)).toBe(true);
    expect(resourceIdsIn(text)).toEqual(["7", "9"]);
  });

  it("reports no token for plain text", () => {
    expect(hasResourceToken("nothing here")).toBe(false);
    expect(resourceIdsIn("nothing here")).toEqual([]);
  });

  it("replaces every reference to one resource, leaving others alone", () => {
    const text = "see {{res:7}} and {{res:9}} and {{res:7}}";
    expect(replaceResourceToken(text, "7", "CV")).toBe(
      "see CV and {{res:9}} and CV",
    );
  });
});

describe("stripResourceTokens", () => {
  it("reads a tagged value as prose", () => {
    expect(
      stripResourceTokens("Call them {{res:7}} today", (inner) =>
        inner === "7" ? "the recruiter" : "",
      ),
    ).toBe("Call them the recruiter today");
  });

  it("drops a tag with no replacement without leaving double spaces", () => {
    expect(stripResourceTokens("Call them {{res:7}} today", () => "")).toBe(
      "Call them today",
    );
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
