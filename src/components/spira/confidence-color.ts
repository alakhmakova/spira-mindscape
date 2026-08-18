export function getConfidenceColor(value: number) {
  if (value <= 4) return "#FF725D";
  if (value <= 7) return "#EBAF00";
  return "#7EC5C4";
}
