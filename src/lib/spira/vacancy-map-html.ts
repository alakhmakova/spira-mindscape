import {
  companyLabel,
  requirementLabel,
  type MapAdditionalItem,
  type MapCheckItem,
  type VacancyMap,
} from "@/lib/spira/vacancy-map";

/**
 * The map as one HTML file a person can keep, print or send on (owner, 2026-09-24).
 *
 * **It is a document, not a copy of the page.** Nothing in it is editable, nothing is a control,
 * and there is no script: what the page draws as a pencil, a checkbox or a comment glyph is an
 * invitation to write, and an invitation makes no sense in a file. So the empty fields are simply
 * left out — a map printed with eight blank headings reads as a form nobody filled in, when in
 * fact those questions were never asked of this vacancy.
 *
 * Everything is inline (the styles included) and self-contained, because the file will be opened
 * from a downloads folder with no network and no app behind it.
 */
export function vacancyMapHtml(title: string, map: VacancyMap): string {
  const facts = map.facts;
  const sections: string[] = [];

  const factRows: string[] = [
    factRow("Job title", facts.jobTitle),
    factRow("Location", facts.location),
    factRow("Link", facts.link, true),
    factRow("Education", facts.education, false, facts.educationNote),
    factRow(
      "Years of experience",
      facts.experience,
      false,
      facts.experienceNote,
    ),
    factRow("Languages", facts.language, false, facts.languageNote),
    facts.deadline ? factRow("Apply by", facts.deadline) : "",
    ...facts.custom.map((item) =>
      factRow(item.label || "Field", item.value, false, item.note),
    ),
  ].filter(Boolean);
  if (factRows.length) {
    sections.push(
      section("The vacancy", `<dl class="facts">${factRows.join("")}</dl>`),
    );
  }

  const checkList = (items: MapCheckItem[]) =>
    `<ul class="checks">${items
      .map(
        (item) =>
          `<li><span class="mark">${item.checked ? "Yes" : "&mdash;"}</span>` +
          `<span>${escape(item.text)}${comments(item)}</span></li>`,
      )
      .join("")}</ul>`;

  const skills = map.skills.filter((item) => item.text.trim());
  if (skills.length) sections.push(section("Skills", checkList(skills)));

  const qualities = map.qualities.filter((item) => item.text.trim());
  if (qualities.length)
    sections.push(section("Personal qualities", checkList(qualities)));

  const requirements = map.requirements.filter(
    (item) =>
      item.text.trim() || item.companies.some((company) => company.text.trim()),
  );
  if (requirements.length) {
    const rows = requirements.map((item) => {
      // Numbered by the row's position in the WHOLE list, so the file agrees with the app even
      // when a blank requirement above it has been left out of the file.
      const number = map.requirements.indexOf(item);
      const answers = item.companies.filter((company) => company.text.trim());
      return (
        `<article class="req${item.unmet ? " unmet" : ""}">` +
        `<h3>${
          item.important ? "Very important!" : escape(requirementLabel(number))
        }${item.unmet ? ' <span class="tag">Cannot meet</span>' : ""}</h3>` +
        `<p>${escape(item.text)}</p>` +
        (answers.length
          ? `<dl class="answers">${answers
              .map(
                (company) =>
                  `<dt>${escape(
                    companyLabel(company, item.companies.indexOf(company)),
                  )}</dt><dd>${escape(company.text)}</dd>`,
              )
              .join("")}</dl>`
          : "") +
        `</article>`
      );
    });
    sections.push(section("Requirements", rows.join("")));
  }

  const additional = map.additional.filter((item) => item.text.trim());
  if (additional.length) {
    sections.push(
      section(
        "Additional information",
        `<ul class="extra">${additional
          .map(
            (item) =>
              `<li><span>${escape(item.text)}</span>${
                item.tag !== "none"
                  ? ` <span class="tag">${escape(tagLabel(item))}</span>`
                  : ""
              }${item.detail.trim() ? `<p class="note">${escape(item.detail)}</p>` : ""}</li>`,
          )
          .join("")}</ul>`,
      ),
    );
  }

  const company = map.company;
  const companyParts: string[] = [];
  if (company.name.trim())
    companyParts.push(`<p class="lead">${escape(company.name)}</p>`);
  if (company.link.trim()) companyParts.push(`<p>${link(company.link)}</p>`);
  if (company.about.trim())
    companyParts.push(`<p>${escape(company.about)}</p>`);
  const notes = company.comments.filter((note) => note.text.trim());
  if (notes.length) {
    companyParts.push(
      `<ul class="extra">${notes
        .map((note) => `<li><span>${escape(note.text)}</span></li>`)
        .join("")}</ul>`,
    );
  }
  if (companyParts.length) {
    sections.push(section("The company", companyParts.join("")));
  }

  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escape(title)}</title>
<style>${STYLE}</style>
</head>
<body>
<main>
<header>
<p class="kicker">Requirements map</p>
<h1>${escape(title)}</h1>
</header>
${sections.join("\n")}
</main>
</body>
</html>
`;
}

/** The file it is downloaded as: the vacancy's own name, and nothing a filesystem will refuse. */
export function vacancyMapFileName(title: string): string {
  const stem =
    title
      .replace(/[\\/:*?"<>|]+/g, " ")
      .replace(/\s+/g, " ")
      .trim()
      .slice(0, 80) || "Requirements map";
  return `${stem}.html`;
}

function section(heading: string, body: string): string {
  return `<section><h2>${escape(heading)}</h2>${body}</section>`;
}

function factRow(
  label: string,
  value: string,
  asLink = false,
  note = "",
): string {
  if (!value.trim()) return "";
  return (
    `<dt>${escape(label)}</dt>` +
    `<dd>${asLink ? link(value) : escape(value)}` +
    `${note.trim() ? `<span class="note">${escape(note)}</span>` : ""}</dd>`
  );
}

function comments(item: MapCheckItem): string {
  const written = item.comments.filter((comment) => comment.text.trim());
  if (!written.length) return "";
  return `<ul class="thread">${written
    .map(
      (comment) =>
        `<li>${escape(comment.text)}${
          comment.company.trim()
            ? ` <span class="at">${escape(comment.company)}</span>`
            : ""
        }</li>`,
    )
    .join("")}</ul>`;
}

function tagLabel(item: MapAdditionalItem): string {
  return item.tag === "cover_letter" ? "Cover letter" : "Profile";
}

/** A link is only ever rendered as one when it is safe to follow. */
function link(url: string): string {
  const trimmed = url.trim();
  if (!/^https?:\/\//i.test(trimmed)) return escape(trimmed);
  return `<a href="${escape(trimmed)}" rel="noopener noreferrer">${escape(trimmed)}</a>`;
}

/**
 * The map is the user's own text and the advert's, and this file is opened in a browser — so
 * every value is escaped, including inside an attribute.
 */
function escape(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

// Kale, Guava and the Salt ramp, as everywhere else — the file should look like it came from
// Spira. Serif headings, 4px corners, a hairline instead of a fill (CLAUDE.md -> Design).
const STYLE = `
:root { color-scheme: light; }
* { box-sizing: border-box; }
body {
  margin: 0; padding: 32px 16px; background: #fff; color: #222525;
  font: 15px/1.45 "Montserrat", "Segoe UI", system-ui, -apple-system, sans-serif;
}
main { max-width: 760px; margin: 0 auto; }
header { margin-bottom: 28px; }
.kicker {
  margin: 0 0 6px; font-size: 11px; font-weight: 600; letter-spacing: .08em;
  text-transform: uppercase; color: #0A8080;
}
h1 { margin: 0; font-family: "Playfair Display", Georgia, serif; font-size: 27px; line-height: 1.1; }
h2 {
  margin: 0 0 12px; font-family: "Playfair Display", Georgia, serif; font-size: 19px;
  line-height: 1.1;
}
h3 { margin: 0 0 4px; font-size: 14px; }
section { border: 1px solid #E5E5E5; border-radius: 4px; padding: 20px; margin-bottom: 16px; }
dl.facts { margin: 0; display: grid; gap: 10px; }
dl.facts dt { font-size: 14px; font-weight: 600; }
dl.facts dt::after { content: ":"; }
dl.facts dd { margin: 2px 0 0; overflow-wrap: anywhere; }
.note { display: block; margin-top: 2px; color: #6C6C72; }
ul { margin: 0; padding: 0; list-style: none; }
ul.checks li, ul.extra li { display: flex; gap: 8px; padding: 7px 0; border-top: 1px solid #F3F3F3; }
ul.checks li:first-child, ul.extra li:first-child { border-top: 0; }
ul.extra li { flex-direction: column; gap: 4px; }
.mark { flex: 0 0 34px; font-size: 12px; font-weight: 600; color: #0A8080; }
ul.thread { margin: 4px 0 0; padding-left: 12px; border-left: 2px solid #E5F4F3; }
ul.thread li { padding: 2px 0; font-size: 13px; color: #525257; }
.at { color: #0A8080; font-weight: 600; }
.req { padding: 12px 0; border-top: 1px solid #F3F3F3; }
.req:first-of-type { border-top: 0; padding-top: 0; }
.req h3 { color: #0A8080; }
.req.unmet h3 { color: #C53336; }
.req p { margin: 0; }
dl.answers { margin: 8px 0 0; padding-left: 12px; border-left: 2px solid #E5F4F3; }
dl.answers dt { font-size: 12px; font-weight: 600; color: #6C6C72; }
dl.answers dd { margin: 0 0 8px; }
dl.answers dd:last-child { margin-bottom: 0; }
.tag {
  display: inline-block; border: 1px solid #0A8080; border-radius: 999px;
  padding: 1px 9px; font-size: 11px; font-weight: 600; color: #222525;
}
.lead { margin: 0 0 4px; font-weight: 600; }
p { margin: 0 0 8px; overflow-wrap: anywhere; }
p:last-child { margin-bottom: 0; }
a { color: #0A8080; }
@media print {
  body { padding: 0; }
  section { break-inside: avoid; border-color: #DCDCDC; }
}
`;
