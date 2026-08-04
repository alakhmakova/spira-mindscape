/**
 * A short confetti burst, fired when a target is completed.
 *
 * Deliberately dependency-free: a few dozen absolutely-positioned dots animated with the Web
 * Animations API and removed when they finish, so nothing lingers in the DOM and nothing is added
 * to the bundle. Colours come from the brand palette (Guava, Kale, Ginger).
 */
const CONFETTI_COLOURS = [
  "#F45D48", // Guava-500
  "#EF523C", // Guava-600
  "#0A8080", // Kale-500
  "#8DD3D4", // Kale-300
  "#FFF2DF", // Ginger-200
];

const PIECES = 44;
const DURATION_MS = 1800;

export function celebrate(): void {
  if (typeof document === "undefined") return;
  // Respect the user's motion preference — a burst of moving dots is exactly what it covers.
  if (window.matchMedia?.("(prefers-reduced-motion: reduce)").matches) return;
  if (typeof document.body.animate !== "function") return; // jsdom / very old browsers

  const layer = document.createElement("div");
  layer.setAttribute("aria-hidden", "true");
  layer.style.cssText =
    "position:fixed;inset:0;pointer-events:none;z-index:9999;overflow:hidden";
  document.body.appendChild(layer);

  const originX = window.innerWidth / 2;
  const originY = window.innerHeight * 0.35;

  for (let i = 0; i < PIECES; i++) {
    const piece = document.createElement("span");
    const size = 6 + Math.random() * 6;
    piece.style.cssText = [
      "position:absolute",
      `left:${originX}px`,
      `top:${originY}px`,
      `width:${size}px`,
      `height:${size * (Math.random() < 0.5 ? 1 : 0.5)}px`,
      `background:${CONFETTI_COLOURS[i % CONFETTI_COLOURS.length]}`,
      Math.random() < 0.5 ? "border-radius:2px" : "border-radius:50%",
    ].join(";");
    layer.appendChild(piece);

    const angle = Math.random() * Math.PI * 2;
    const distance = 120 + Math.random() * 260;
    piece.animate(
      [
        { transform: "translate(0, 0) rotate(0deg)", opacity: 1 },
        {
          transform: `translate(${Math.cos(angle) * distance}px, ${
            Math.sin(angle) * distance + 220
          }px) rotate(${(Math.random() * 720 - 360).toFixed(0)}deg)`,
          opacity: 0,
        },
      ],
      {
        duration: DURATION_MS * (0.6 + Math.random() * 0.4),
        easing: "cubic-bezier(.16,.68,.4,1)",
        fill: "forwards",
      },
    );
  }

  window.setTimeout(() => layer.remove(), DURATION_MS + 200);
}
