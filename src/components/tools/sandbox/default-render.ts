// A DEFAULT render module (a function body that receives `ctx`) used until a
// tool has its own AI-written code. It proves the sandbox loop end to end:
// renders a table from the schema + records, and adds / deletes rows through
// the parent-validated callbacks. The AI will later emit code in this exact
// shape (uses ctx.root, ctx.schema, ctx.records, ctx.addRow/editRow/deleteRow).
export const DEFAULT_RENDER_CODE = `
var cols = (ctx.schema && ctx.schema.columns ? ctx.schema.columns : [])
  .filter(function (c) { return c.primitive !== "chart"; });
var root = ctx.root;

function cell(v) { return v == null ? "" : (typeof v === "boolean" ? (v ? "\\u2713" : "") : String(v)); }
function label(c) { return c.label || c.key; }

// ── Existing rows (newest first) ─────────────────────────────────────────────
var table = document.createElement("table");
table.style.width = "100%";
table.style.borderCollapse = "collapse";
table.style.fontSize = "13px";

var thead = document.createElement("tr");
cols.forEach(function (c) {
  var th = document.createElement("th");
  th.textContent = label(c);
  th.style.textAlign = "left";
  th.style.padding = "6px 8px";
  th.style.borderBottom = "1px solid var(--border)";
  th.style.color = "var(--muted)";
  thead.appendChild(th);
});
var thAct = document.createElement("th");
thAct.style.borderBottom = "1px solid var(--border)";
thead.appendChild(thAct);
table.appendChild(thead);

ctx.records.slice().reverse().forEach(function (rec) {
  var tr = document.createElement("tr");
  cols.forEach(function (c) {
    var td = document.createElement("td");
    td.textContent = cell(rec.data ? rec.data[c.key] : "");
    td.style.padding = "6px 8px";
    td.style.borderBottom = "1px solid var(--border)";
    tr.appendChild(td);
  });
  var tdDel = document.createElement("td");
  tdDel.style.borderBottom = "1px solid var(--border)";
  tdDel.style.textAlign = "right";
  var del = document.createElement("button");
  del.textContent = "Delete";
  del.style.cssText = "border:0;background:none;color:#a33;cursor:pointer;font:inherit;";
  del.onclick = function () { ctx.deleteRow(rec.id); };
  tdDel.appendChild(del);
  tr.appendChild(tdDel);
  table.appendChild(tr);
});
root.appendChild(table);

if (!ctx.records.length) {
  var empty = document.createElement("p");
  empty.textContent = "No entries yet — add one below.";
  empty.style.cssText = "color:var(--muted);font-style:italic;margin:8px;";
  root.appendChild(empty);
}

// ── Add a row ────────────────────────────────────────────────────────────────
var form = document.createElement("div");
form.style.cssText = "display:flex;flex-wrap:wrap;gap:6px;align-items:flex-end;margin-top:10px;";
var inputs = {};
cols.forEach(function (c) {
  var wrap = document.createElement("label");
  wrap.style.cssText = "display:flex;flex-direction:column;font-size:11px;color:var(--muted);gap:2px;";
  wrap.textContent = label(c);
  var field;
  if (c.primitive === "select") {
    field = document.createElement("select");
    (c.options || []).forEach(function (o) {
      var opt = document.createElement("option");
      opt.value = o; opt.textContent = o; field.appendChild(opt);
    });
  } else if (c.primitive === "checkbox") {
    field = document.createElement("input"); field.type = "checkbox";
  } else {
    field = document.createElement("input");
    field.type = c.primitive === "number" ? "number" : (c.primitive === "date" ? "date" : "text");
  }
  field.style.cssText = "padding:6px 8px;border:1px solid var(--border);border-radius:6px;font:inherit;";
  inputs[c.key] = { field: field, primitive: c.primitive };
  wrap.appendChild(field);
  form.appendChild(wrap);
});
var add = document.createElement("button");
add.textContent = "Add entry";
add.style.cssText = "padding:7px 12px;border:0;border-radius:6px;background:var(--accent);color:#fff;font:inherit;font-weight:600;cursor:pointer;";
add.onclick = function () {
  var data = {};
  Object.keys(inputs).forEach(function (k) {
    var i = inputs[k];
    if (i.primitive === "checkbox") data[k] = i.field.checked;
    else if (i.primitive === "number") data[k] = i.field.value === "" ? null : Number(i.field.value);
    else if (i.field.value !== "") data[k] = i.field.value;
  });
  ctx.addRow(data);
};
form.appendChild(add);
root.appendChild(form);
`;
