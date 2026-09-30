import { describe, expect, it } from "vitest";

import { vacancyMapFileName, vacancyMapHtml } from "./vacancy-map-html";
import {
  emptyVacancyMap,
  newCheckItem,
  newComment,
  newCustomFact,
  type VacancyMap,
} from "./vacancy-map";

/** A map with something written in every part of it. */
function filled(): VacancyMap {
  const map = emptyVacancyMap();
  map.facts.jobTitle = "Systemutvecklare";
  map.facts.location = "Malmö";
  map.facts.link = "https://example.se/jobb/1";
  map.facts.education = "Eftergymnasial utbildning";
  map.facts.educationNote = "YH, 400 poäng";
  map.facts.custom.push({ ...newCustomFact(), label: "Salary", value: "45k" });

  const skill = newCheckItem("Java");
  skill.checked = true;
  skill.comments.push(newComment("Five years of it", "Squidler"));
  map.skills.push(skill);
  map.qualities.push(newCheckItem("Noggrann"));

  map.requirements[0].text = "Bygga interna system";
  map.requirements[1].text = "Arkitektur";
  map.requirements[1].unmet = true;
  map.requirements[1].companies[0].text = "Byggde API:et själv";
  map.additional.push({
    id: "a1",
    text: "Litet team",
    detail: "Det passar mig",
    tag: "cover_letter",
    checked: false,
  });
  map.company.name = "Prima Vård";
  map.company.link = "https://example.se";
  map.company.comments.push({ id: "n1", text: "Växer snabbt" });
  return map;
}

describe("the map as a file", () => {
  it("is one self-contained document — no script, no fetch, styles inline", () => {
    const html = vacancyMapHtml("Systemutvecklare", filled());

    expect(html.startsWith("<!doctype html>")).toBe(true);
    expect(html).toContain("<style>");
    // It is opened from a downloads folder, with no app and often no network behind it.
    expect(html).not.toMatch(/<script/i);
    expect(html).not.toMatch(/src=|@import|<link/i);
  });

  it("carries everything that was written in the map", () => {
    const html = vacancyMapHtml("Systemutvecklare", filled());

    for (const written of [
      "Systemutvecklare",
      "Malmö",
      "Eftergymnasial utbildning",
      "YH, 400 poäng",
      "Salary",
      "45k",
      "Java",
      "Five years of it",
      "Squidler",
      "Noggrann",
      "Bygga interna system",
      "Byggde API:et själv",
      "Litet team",
      "Det passar mig",
      "Cover letter",
      "Prima Vård",
      "Växer snabbt",
    ]) {
      expect(html).toContain(written);
    }
  });

  it("leaves out the questions this vacancy never asked", () => {
    const map = emptyVacancyMap();
    map.facts.jobTitle = "Systemutvecklare";

    const html = vacancyMapHtml("A vacancy", map);

    // A file is not a form: an empty heading in a document reads as something nobody filled in.
    expect(html).toContain("Job title");
    expect(html).not.toContain("Years of experience");
    // The section headings, not the words: "Requirements map" is the document's own kicker and
    // is there whatever the map holds.
    expect(html).not.toContain("<h2>Personal qualities</h2>");
    expect(html).not.toContain("<h2>Requirements</h2>");
    expect(html).not.toContain("<h2>The company</h2>");
  });

  it("keeps the app's numbering, and says which requirement the job turns on", () => {
    const map = emptyVacancyMap();
    map.requirements[0].text = "The one it turns on";
    map.requirements[2].text = "Another";

    const html = vacancyMapHtml("A vacancy", map);

    expect(html).toContain("Very important!");
    // Position 2 is "Requirement 2" in the app, and stays so here although the blank row
    // between them is not in the file.
    expect(html).toContain("Requirement 2");
    expect(html).not.toContain("Requirement 1");
  });

  it("marks a requirement she cannot meet", () => {
    const map = emptyVacancyMap();
    map.requirements[1].text = "Ten years of Kotlin";
    map.requirements[1].unmet = true;

    expect(vacancyMapHtml("A vacancy", map)).toContain("Cannot meet");
  });

  it("escapes the text, because all of it is somebody's own writing", () => {
    const map = emptyVacancyMap();
    map.facts.jobTitle = '<img src=x onerror="alert(1)">';
    map.requirements[0].text = "5 > 3 && 2 < 4";

    const html = vacancyMapHtml('"><script>alert(1)</script>', map);

    expect(html).not.toContain("<img src=x");
    expect(html).not.toContain("<script>");
    expect(html).toContain("&lt;img src=x");
    expect(html).toContain("5 &gt; 3 &amp;&amp; 2 &lt; 4");
  });

  it("makes a link a link only when it is safe to follow", () => {
    const map = emptyVacancyMap();
    map.facts.link = "javascript:alert(1)";

    const html = vacancyMapHtml("A vacancy", map);

    expect(html).not.toContain('<a href="javascript:');
    expect(html).toContain("javascript:alert(1)");
  });
});

describe("the file it is saved as", () => {
  it("is named after the vacancy", () => {
    expect(vacancyMapFileName("Systemutvecklare hos Prima Vård")).toBe(
      "Systemutvecklare hos Prima Vård.html",
    );
  });

  it("drops what a filesystem will not take", () => {
    expect(vacancyMapFileName('Java/Kotlin: "senior" <dev>?')).toBe(
      "Java Kotlin senior dev.html",
    );
  });

  it("names an untitled map rather than producing a bare extension", () => {
    expect(vacancyMapFileName("   ")).toBe("Requirements map.html");
  });
});
