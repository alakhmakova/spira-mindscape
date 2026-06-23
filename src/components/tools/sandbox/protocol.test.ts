import { describe, expect, it } from "vitest";
import { parseFrameMessage, FRAME_SOURCE } from "./protocol";

const f = (m: object) => ({ source: FRAME_SOURCE, ...m });

describe("parseFrameMessage", () => {
  it("accepts well-formed frame messages", () => {
    expect(parseFrameMessage(f({ type: "ready" }))?.type).toBe("ready");
    expect(parseFrameMessage(f({ type: "resize", height: 200 }))).toEqual({
      source: FRAME_SOURCE,
      type: "resize",
      height: 200,
    });
    expect(parseFrameMessage(f({ type: "error", message: "boom" }))).toEqual({
      source: FRAME_SOURCE,
      type: "error",
      message: "boom",
    });
    expect(
      parseFrameMessage(f({ type: "mutate", op: "add", data: { a: 1 } })),
    ).toEqual({
      source: FRAME_SOURCE,
      type: "mutate",
      op: "add",
      data: { a: 1 },
    });
    expect(
      parseFrameMessage(
        f({ type: "mutate", op: "edit", recordId: 5, data: {} }),
      ),
    ).toMatchObject({ op: "edit", recordId: 5 });
    expect(
      parseFrameMessage(f({ type: "mutate", op: "delete", recordId: 7 })),
    ).toMatchObject({ op: "delete", recordId: 7 });
  });

  it("rejects foreign, malformed, or incomplete messages", () => {
    expect(parseFrameMessage(null)).toBeNull();
    expect(parseFrameMessage("nope")).toBeNull();
    // wrong source (e.g. a stray message from another script)
    expect(parseFrameMessage({ source: "evil", type: "ready" })).toBeNull();
    // unknown type
    expect(parseFrameMessage(f({ type: "exfiltrate" }))).toBeNull();
    // resize without a numeric height
    expect(parseFrameMessage(f({ type: "resize" }))).toBeNull();
    // add without a data object
    expect(parseFrameMessage(f({ type: "mutate", op: "add" }))).toBeNull();
    // edit without a recordId
    expect(
      parseFrameMessage(f({ type: "mutate", op: "edit", data: {} })),
    ).toBeNull();
    // delete without a recordId
    expect(parseFrameMessage(f({ type: "mutate", op: "delete" }))).toBeNull();
    // array data is not a record object
    expect(
      parseFrameMessage(f({ type: "mutate", op: "add", data: [] })),
    ).toBeNull();
  });
});
