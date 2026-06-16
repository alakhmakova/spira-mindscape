import { describe, expect, it } from "vitest";
import {
  columnLabel,
  emptyRow,
  emptyValue,
  formatCell,
  inputColumns,
} from "./tool-logic";
import type { ToolSchema } from "@/lib/spira/tools-api";

const schema: ToolSchema = {
  layout: "table",
  columns: [
    { key: "company", label: "Company", primitive: "text" },
    { key: "count", primitive: "number" },
    { key: "done", primitive: "checkbox" },
    { key: "tasks", primitive: "checklist" },
    { key: "trend", primitive: "chart" },
  ],
};

describe("emptyValue / emptyRow", () => {
  it("gives a sensible blank per primitive", () => {
    expect(emptyValue("number")).toBeNull();
    expect(emptyValue("rating")).toBeNull();
    expect(emptyValue("checkbox")).toBe(false);
    expect(emptyValue("checklist")).toEqual([]);
    expect(emptyValue("tags")).toEqual([]);
    expect(emptyValue("text")).toBe("");
    expect(emptyValue("textarea")).toBe("");
  });

  it("builds a blank row keyed by every column", () => {
    const row = emptyRow(schema);
    expect(Object.keys(row)).toEqual([
      "company",
      "count",
      "done",
      "tasks",
      "trend",
    ]);
    expect(row.done).toBe(false);
    expect(row.count).toBeNull();
  });
});

describe("formatCell", () => {
  it("renders em-dash for empty values", () => {
    expect(formatCell("text", "")).toBe("—");
    expect(formatCell("number", null)).toBe("—");
  });
  it("renders checkbox, checklist progress, and percentages", () => {
    expect(formatCell("checkbox", true)).toBe("✓");
    expect(
      formatCell("checklist", [
        { label: "a", done: true },
        { label: "b", done: false },
      ]),
    ).toBe("1/2 done");
    expect(formatCell("progress", 42)).toBe("42%");
    expect(formatCell("progress", 250)).toBe("100%"); // clamped
  });
  it("renders rating as filled/empty stars", () => {
    expect(formatCell("rating", 3)).toBe("★★★☆☆");
    expect(formatCell("rating", 9)).toBe("★★★★★"); // clamped to 5
  });
  it("joins tags, em-dash when none", () => {
    expect(formatCell("tags", ["remote", "urgent"])).toBe("remote, urgent");
    expect(formatCell("tags", [])).toBe("—");
  });
  it("stringifies plain values", () => {
    expect(formatCell("text", "Acme")).toBe("Acme");
    expect(formatCell("number", 7)).toBe("7");
  });
});

describe("inputColumns", () => {
  it("excludes the derived chart primitive", () => {
    const keys = inputColumns(schema).map((c) => c.key);
    expect(keys).toContain("company");
    expect(keys).not.toContain("trend");
  });
});

describe("columnLabel", () => {
  it("uses the label when present, else humanizes the key", () => {
    expect(
      columnLabel({ key: "company", label: "Company", primitive: "text" }),
    ).toBe("Company");
    expect(columnLabel({ key: "applied_date", primitive: "date" })).toBe(
      "Applied Date",
    );
  });
});
