export function getConfidenceColor(value: number) {
  if (value <= 4) return "#EF7B6C";
  if (value <= 7) return "#F8D068";
  return "#7ECEC4";
}
