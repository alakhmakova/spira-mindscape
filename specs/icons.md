# Icons

The owner's icon collection — **the primary icon source for Spira** — mirrored from Linear so it
is readable inside the repo, greppable, and reviewable in a diff.

- **Source of truth:** the Linear document **Icons** — https://linear.app/grow-goals/document/icons-ae6f5a0fc7e0
- **Mirrored:** 2026-08-10 — **66 icons**, in the document's own order.
- **The rule that governs their use** is in `CLAUDE.md` -> Design -> Components and chrome -> "Icons & emoji":
  take the icon from this set **first**; if what you need is not here, **stop and ask the owner**.
  Lucide is a fallback that needs the owner's go-ahead each time, not a default.

Linear stays the source of truth: when an icon is added or changed there, re-mirror it here.
Nothing reads this file at build time — it is documentation, not an asset pipeline.

---

## How to use these

- **Copy the `d` attribute and the `viewBox`.** Everything else in the markup below is Linear's
  own rendering chrome: `class="gwb_..."`, `role`, `focusable`, `aria-hidden`, `nv_id`,
  `data-internal-name`, `width="1em"`. **Never copy those into Spira.**
- **Colour comes from the caller.** All but one icon are `fill="currentColor"`, so they take the
  surrounding text colour — do not bake a hex into the path.
- **Android** (`ui/icons/SpiraIcons.kt`): give **each `<path>` element its own argument** —
  merging them makes overlaps cancel under even-odd and hollows the glyph out — and **space out
  the arc flags**, because Compose's path parser rejects SVG's compact `a.75.75 0 011.06-1.06`
  form. **44 of the 66 icons below have more than one `<path>`**, so this is the common case, not
  the exception. A wrong path draws *nothing* while assertions stay green: re-render the icon in
  a `VisualCheck*` test and look at the PNG.

## Known quirks in the source

| Icon | What is different |
|---|---|
| **Lock** | `viewBox="0 0 995 995"`, and its paths sit inside two `<g>` wrappers — the only icon that does |
| **Download** | `viewBox="0 0 24 24"` |
| **Chevron down** | `viewBox="0 0 18 13"` |
| **Filled Sparcling** | **no `viewBox`** (`width="15" height="16"`), and the only icon with a hard-coded colour — `fill="#0A8080"`, which is Kale-500. Swap it for `currentColor` when porting |
| **Certificate** | listed **twice**, under the same name, with **two different glyphs**. Both are kept below, in source order, as *Certificate* and *Certificate (variant 2)* |
| **Chevron right** | carries a leftover `<title>Opens in current tab</title>` from the site it was lifted from — drop it; it would be read aloud by a screen reader |

The other **62** icons are a clean `viewBox="0 0 16 16"` set.

In three icons — **Lock**, **Chevron down** and **Filled Sparcling** — Linear had auto-linked the
namespace URL into `xmlns="<http://www.w3.org/2000/svg>"`, which is not valid SVG. That is the
**only** edit made while mirroring: the angle brackets are removed. Every path is verbatim.

---

## Index

| # | Icon | | # | Icon |
|---|---|---|---|---|
| 1 | [Search](#search) | | 34 | [Document](#document) |
| 2 | [Filled sad](#filled-sad) | | 35 | [Heart beat](#heart-beat) |
| 3 | [Filled smile](#filled-smile) | | 36 | [Heart](#heart) |
| 4 | [Earth](#earth) | | 37 | [Heart filled](#heart-filled) |
| 5 | [Lock](#lock) | | 38 | [Settings](#settings) |
| 6 | [Certificate](#certificate) | | 39 | [Question](#question) |
| 7 | [Award](#award) | | 40 | [Folder](#folder) |
| 8 | [Info](#info) | | 41 | [More](#more) |
| 9 | [Money envelope](#money-envelope) | | 42 | [Hamburger](#hamburger) |
| 10 | [Help](#help) | | 43 | [Kebab](#kebab) |
| 11 | [Bank](#bank) | | 44 | [Business](#business) |
| 12 | [Open link](#open-link) | | 45 | [Filled check rounded](#filled-check-rounded) |
| 13 | [Pig](#pig) | | 46 | [Yes/Check](#yescheck) |
| 14 | [Calendar](#calendar) | | 47 | [Download](#download) |
| 15 | [Arrow down](#arrow-down) | | 48 | [Import](#import) |
| 16 | [Arrow left](#arrow-left) | | 49 | [Up and Down for the table](#up-and-down-for-the-table) |
| 17 | [People](#people) | | 50 | [Filled chevron down](#filled-chevron-down) |
| 18 | [Lamp](#lamp) | | 51 | [Chevron right](#chevron-right) |
| 19 | [User squared](#user-squared) | | 52 | [Chevron down](#chevron-down) |
| 20 | [Key](#key) | | 53 | [Chart](#chart) |
| 21 | [Calculator](#calculator) | | 54 | [Warning rounded](#warning-rounded) |
| 22 | [Certificate (variant 2)](#certificate-variant-2) | | 55 | [Warning triangle](#warning-triangle) |
| 23 | [Building](#building) | | 56 | [Notification](#notification) |
| 24 | [Pencil](#pencil) | | 57 | [Plus in a circle](#plus-in-a-circle) |
| 25 | [Trophy](#trophy) | | 58 | [Plus](#plus) |
| 26 | [Flash](#flash) | | 59 | [Cancel](#cancel) |
| 27 | [Play](#play) | | 60 | [Present](#present) |
| 28 | [Eye](#eye) | | 61 | [Persons badge](#persons-badge) |
| 29 | [Reports](#reports) | | 62 | [Time](#time) |
| 30 | [Dollar coin](#dollar-coin) | | 63 | [Alarm](#alarm) |
| 31 | [Message](#message) | | 64 | [Star](#star) |
| 32 | [Filter](#filter) | | 65 | [Home](#home) |
| 33 | [Resources](#resources) | | 66 | [Filled Sparcling](#filled-sparcling) |

---

## The icons

### Search

```svg
<svg aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path fill-rule="evenodd" d="M11.035 12.096a6.5 6.5 0 1 1 1.06-1.06l2.935 2.934a.75.75 0 0 1-1.06 1.06zM12 7A5 5 0 1 1 2 7a5 5 0 0 1 10 0" clip-rule="evenodd"></path></svg>
```

### Filled sad

```svg
<svg aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path fill-rule="evenodd" clip-rule="evenodd" d="M8 .5a7.5 7.5 0 100 15 7.5 7.5 0 000-15zm-2.5 4a1 1 0 100 2 1 1 0 000-2zm4 1a1 1 0 112 0 1 1 0 01-2 0zm.345 6.365a.75.75 0 101.31-.73C10.535 10.02 9.361 9.25 8 9.25c-1.361 0-2.534.77-3.155 1.885a.75.75 0 001.31.73c.377-.678 1.07-1.115 1.845-1.115.775 0 1.468.437 1.845 1.115z"></path></svg>
```

### Filled smile

```svg
<svg aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path fill-rule="evenodd" clip-rule="evenodd" d="M8 .5a7.5 7.5 0 100 15 7.5 7.5 0 000-15zm-2.5 4a1 1 0 100 2 1 1 0 000-2zm4 1a1 1 0 112 0 1 1 0 01-2 0zM6.155 9.635a.75.75 0 10-1.31.73C5.465 11.48 6.639 12.25 8 12.25c1.361 0 2.534-.77 3.155-1.885a.75.75 0 00-1.31-.73c-.377.678-1.07 1.115-1.845 1.115-.775 0-1.468-.437-1.845-1.115z"></path></svg>
```

### Earth

```svg
<svg aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1rem" height="1rem" fill="currentColor" viewBox="0 0 16 16"><path fill-rule="evenodd" d="M6.83 8.112a.75.75 0 0 1 .577-.217l1.397.09a.75.75 0 0 1 .37.125l2.12 1.425a.75.75 0 0 1 .253.958l-.719 1.438a.75.75 0 0 1-.67.415H8a.75.75 0 0 1-.624-.334L5.938 9.854a.75.75 0 0 1 .094-.947zm.698 1.423.121-.121.857.054 1.405.944-.217.434H8.4z" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M.806 8.65A7.218 7.218 0 0 1 8.65.806c3.44.3 6.244 3.104 6.543 6.544a7.217 7.217 0 0 1-7.843 7.843C3.91 14.894 1.105 12.09.806 8.65M8.519 2.3A5.718 5.718 0 0 0 2.3 8.52c.236 2.714 2.465 4.943 5.18 5.179a5.717 5.717 0 0 0 6.219-6.22c-.236-2.713-2.465-4.942-5.18-5.179" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M9.751.997a.75.75 0 0 1 .546.909l-.85 3.4a.75.75 0 0 1-.545.545l-2.54.639-.569 1.128a.75.75 0 0 1-1.12.263L1.762 5.695a.75.75 0 1 1 .9-1.2l2.195 1.648.32-.634a.75.75 0 0 1 .486-.39L8.1 4.506l.741-2.964a.75.75 0 0 1 .91-.545" clip-rule="evenodd"></path></svg>
```

### Lock

```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 995 995" aria-hidden="true" focusable="false">
	<g fill="currentColor" aria-hidden="true">
	 <path d="M502.28 463.784c70.076-.744 130.778-1.454 191.483-1.964 9.624-.08 19.257.73 28.885 1.149 6.63.288 10.666 3.719 11.787 10.243.92 4.537 1.495 9.137 1.719 13.761.883 49.632 1.371 99.272 2.472 148.9.9 40.34 2.276 80.672 3.845 120.991.749 19.256 3.218 38.467 3.429 57.715.147 13.459-1.552 27.05-3.664 40.387-1.623 10.243-3.348 11.066-13.3 11.872-39.172 3.174-78.334 4.662-117.666 2.156-20.622-1.314-41.4-.4-62.1-.225-32.838.273-65.679 1.325-98.508.984-24.957-.259-49.889-2.857-74.846-3.129-20.315-.221-40.649 1.521-60.979 2.258-4.985.261-9.982.141-14.949-.358-7.914-.927-10.019-3.226-9.884-11.051.149-8.558 1.126-17.1 1.4-25.659 1.973-62.805 6.382-125.558 4.707-188.444-.665-24.97-2.579-49.908-3.226-74.878s-.441-49.992-.567-74.99c-.038-7.5-.1-15.008.149-22.5.265-7.841 1.409-9.063 9.331-10.191 4.234-.644 8.512-.948 12.794-.908 65.681 1.297 131.363 2.697 187.688 3.881z"></path>
	</g>
	<g aria-hidden="true">
	 <path d="M714.693 627.3v189.667c0 23.89-2.029 25.959-25.409 25.921-81.419-.132-162.839-.067-244.256-.493-57.824-.3-115.644-1.274-173.466-1.948-1.071-.012-2.144 0-3.213-.059-12.077-.7-16.848-5.173-17.076-17.279-.337-17.853-.095-35.717-.2-53.576-.634-111.414-1.282-222.828-1.943-334.242-.021-3.571-.1-7.149.067-10.714.576-12.155 5.087-16.792 16.983-17.465 3.205-.181 6.426-.091 9.64-.092 23.569-.006 47.138.065 70.706-.034 11.139-.047 12.1-1 12.114-12.406.058-44.648-.8-89.315.2-133.94 1.192-53.328 24.711-94.689 71.322-121.432 43.4-24.9 96.364-16.894 132.287 18.266 17.95 17.569 29.095 38.659 34.923 62.762 3.847 15.614 5.749 31.643 5.663 47.724-.339 40.716-.054 81.438-.008 122.157 0 3.213.072 6.428.163 9.64.134 4.731 2.581 7.223 7.327 7.371 2.855.089 5.711.162 8.566.167 22.498.037 44.995.069 67.492.094 3.571-.088 7.143.033 10.7.363 11.605 1.4 16.356 6.5 17.055 18.1.194 3.2.106 6.427.106 9.642.005 63.937.005 127.873 0 191.81l.257-.004zM482.891 433.651c-24.034-.331-48.085-.534-72.104-.513-.297 0-.594.001-.892.001-6.076.004-12.152.007-18.228.007-30.323.003-60.647.007-90.97.012-3.942.001-7.884.001-11.826.002-12.8.007-13.69.824-13.626 13.129.611 118.492 1.26 236.984 1.949 355.476.068 11.782.963 12.569 13.034 12.689 62.43.62 124.86 1.4 187.292 1.751 64.955.367 129.912.324 194.869.436 16.058.028 16.1-.016 16.087-16.007-.075-117.45-.162-234.9-.26-352.351 0-2.854-.032-5.715-.226-8.56-.26-3.8-2.457-5.792-6.221-5.974-2.85-.138-5.708-.214-8.561-.2-27.83.181-55.661.484-83.492.562-29.803.083-59.601.092-89.402-.199-5.806-.094-11.614-.181-17.423-.261zm-1.981-26.508c3.509.012 7.017.025 10.526.04 24.333 0 48.664.091 72.999.018 11.472-.035 12.173-.851 12.36-12.082.036-2.141.024-4.284 0-6.426-.581-45.314-1.552-90.627-1.622-135.941-.05-31.84-11.059-58.463-34.391-80.045-19.645-18.17-42.815-24.871-69.127-21.129-26.222 3.728-46.542 17.863-62.643 37.858-14.073 17.478-24.451 37.345-24.37 60.474.172 48.885 1.216 97.767 1.913 146.65.02 1.426.091 2.858.252 4.274.425 3.737 2.582 5.775 6.307 6 2.847.175 5.706.22 8.56.222 26.411 0 52.824-.002 79.236.087z"></path>
	 <path d="M488.247 714.509c-14.579-.848-29.16-1.653-43.736-2.556-11.378-.7-15.711-6.941-12.15-18.155 3.777-11.893 7.688-23.771 12.218-35.392 5.719-14.669 4.607-28.385-2.855-42.2-3.749-7.288-6.828-14.902-9.2-22.747-9.448-29.221 11.507-57.107 38.166-64.2 23.209-6.179 48.792 12.286 50.682 36.615.53 7.804-.259 15.641-2.334 23.182-2.788 10.277-7.038 20.188-11.088 30.082-3.52 8.6-4.5 17.247-1.861 26.136 4.77 16.061 9.46 32.155 14.686 48.069 4.381 13.34.662 19.8-13.26 20.773-6.388.445-12.833.075-19.252.075l-.016.318z"></path>
	</g>
</svg>
```

### Certificate

```svg
<svg data-internal-name="academy-colorless-icon" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1.5rem" fill="currentColor" color="inherit" viewBox="0 0 16 16"><path fill-rule="evenodd" d="M12.112 8.223a1.888 1.888 0 1 0 0 3.775 1.888 1.888 0 0 0 0-3.775M8.724 10.11a3.388 3.388 0 1 1 6.776 0 3.388 3.388 0 0 1-6.776 0" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M10.605 11.525a.75.75 0 0 1 .75.75v1.982l.001.001.001.001h1.51m.002-.002v-1.981a.75.75 0 1 1 1.5 0v1.98c0 .83-.673 1.503-1.503 1.503h-1.507c-.83 0-1.504-.673-1.504-1.503v-1.98a.75.75 0 0 1 .75-.75" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M2.692 2.194a.757.757 0 0 0-.757.757v9.044c0 .418.339.757.757.757h5.275a.75.75 0 0 1 0 1.5H2.692a2.257 2.257 0 0 1-2.257-2.257V2.95A2.257 2.257 0 0 1 2.692.694h10.55A2.257 2.257 0 0 1 15.5 2.95v3.015a.75.75 0 1 1-1.5 0V2.95a.757.757 0 0 0-.757-.757z" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M3.449 4.835a.75.75 0 0 1 .75-.75h6.783a.75.75 0 0 1 0 1.5H4.199a.75.75 0 0 1-.75-.75m0 3.165a.75.75 0 0 1 .75-.75h3.015a.75.75 0 0 1 0 1.5H4.199a.75.75 0 0 1-.75-.75" clip-rule="evenodd"></path></svg>
```

### Award

```svg
<svg data-internal-name="professional-development-colorless-icon" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1.5rem" fill="currentColor" color="inherit" viewBox="0 0 16 16"><path fill-rule="evenodd" d="M4.119 8.813a.75.75 0 0 1 .012 1.061L2.45 11.595l1.06.637a.75.75 0 0 1 .258.257l.618 1.03 2.245-2.357a.75.75 0 1 1 1.086 1.034l-2.924 3.071a.75.75 0 0 1-1.186-.131l-1.029-1.714-1.714-1.029a.75.75 0 0 1-.15-1.167l2.344-2.4a.75.75 0 0 1 1.06-.013Zm7.762 0a.75.75 0 0 1 1.061.013l2.345 2.4a.75.75 0 0 1-.151 1.167l-1.714 1.029-1.029 1.714a.75.75 0 0 1-1.186.131l-2.924-3.071a.75.75 0 0 1 1.086-1.034l2.245 2.357.618-1.03a.75.75 0 0 1 .257-.257l1.061-.637-1.681-1.721a.75.75 0 0 1 .012-1.06Z" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M11.182 3.318a4.5 4.5 0 1 0-6.364 6.364 4.5 4.5 0 0 0 6.364-6.364m1.06-1.06a6 6 0 1 0-8.485 8.485 6 6 0 0 0 8.486-8.486Z" clip-rule="evenodd"></path><path fill-rule="evenodd" d="m8.994 3.68.499 1.011 1.116.162c.91.133 1.27 1.25.614 1.89l-.808.787.19 1.112A1.108 1.108 0 0 1 8.998 9.81l-.997-.524-.999.525a1.108 1.108 0 0 1-1.607-1.168l.19-1.112-.808-.787a1.108 1.108 0 0 1 .614-1.89l1.117-.163.499-1.012a1.107 1.107 0 0 1 1.987 0Zm-.636 4.1a.75.75 0 0 0-.747.017l-.604.317.123-.718a.75.75 0 0 0-.215-.664l-.522-.508.721-.105a.75.75 0 0 0 .565-.41L8 5.055l.322.652a.75.75 0 0 0 .565.41l.72.105-.522.508a.75.75 0 0 0-.215.664l.123.718-.635-.334Z" clip-rule="evenodd"></path></svg>
```

### Info

```svg
<svg aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="16px" height="16px" fill="currentColor" viewBox="0 0 16 16"><path fill-rule="evenodd" d="M8 2a6 6 0 1 0 0 12A6 6 0 1 0 8 2M.5 8A7.5 7.5 0 0 1 8 .5 7.5 7.5 0 0 1 15.5 8 7.5 7.5 0 0 1 8 15.5 7.5 7.5 0 0 1 .5 8" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M6.5 8a.75.75 0 0 1 .75-.75H8a.75.75 0 0 1 .75.75v3.75a.75.75 0 0 1-1.5 0v-3A.75.75 0 0 1 6.5 8m.375-2.81a.936.936 0 1 1 1.875-.003.938.938 0 0 1-1.875.003" clip-rule="evenodd"></path></svg>
```

### Money envelope

```svg
<svg data-internal-name="money-colorless-icon" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1.5rem" fill="currentColor" color="inherit" viewBox="0 0 16 16"><path fill-rule="evenodd" d="M3.032 8.575a.274.274 0 0 0-.44.217v4.434c0 .34.275.614.615.614h9.553c.34 0 .615-.275.615-.614V8.792a.274.274 0 0 0-.44-.217L9.27 11.382a2.115 2.115 0 0 1-2.571 0zm.912-1.191a1.774 1.774 0 0 0-2.852 1.408v4.434c0 1.167.947 2.114 2.115 2.114h9.553a2.115 2.115 0 0 0 2.115-2.114V8.792a1.774 1.774 0 0 0-2.852-1.408L8.357 10.19a.615.615 0 0 1-.747 0z" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M2.457 2.99c0-1.168.947-2.115 2.115-2.115h6.824c1.167 0 2.114.947 2.114 2.115v4.835a.75.75 0 0 1-1.5 0V2.99a.615.615 0 0 0-.614-.615H4.572a.615.615 0 0 0-.615.615v4.835a.75.75 0 1 1-1.5 0z" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M7.984 7.358a.75.75 0 0 1 .75.75v.682a.75.75 0 1 1-1.5 0v-.682a.75.75 0 0 1 .75-.75M6.619 4.287a.75.75 0 0 1 .75.75c0 .079.055.147.133.162l1.257.252c.779.155 1.34.839 1.34 1.633h-1.5a.166.166 0 0 0-.134-.162L7.208 6.67a1.666 1.666 0 0 1-1.34-1.633.75.75 0 0 1 .75-.75Z" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M9.348 6.334a.75.75 0 0 1 .75.75c0 .572-.31 1.032-.7 1.324a2.36 2.36 0 0 1-1.393.45 2.14 2.14 0 0 1-1.949-1.052.75.75 0 0 1 1.292-.762.64.64 0 0 0 .591.315l.045-.001a.87.87 0 0 0 .515-.15.3.3 0 0 0 .089-.094c.01-.019.01-.027.01-.03a.75.75 0 0 1 .75-.75M7.962 3.263A2.15 2.15 0 0 1 9.9 4.293a.75.75 0 0 1-1.281.78.65.65 0 0 0-.592-.31h-.043a.87.87 0 0 0-.515.15.3.3 0 0 0-.09.094c-.01.019-.01.027-.01.03a.75.75 0 1 1-1.5 0c0-.572.31-1.032.7-1.324a2.36 2.36 0 0 1 1.393-.45" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M7.984 2.58a.75.75 0 0 1 .75.75v.683a.75.75 0 0 1-1.5 0v-.682a.75.75 0 0 1 .75-.75Z" clip-rule="evenodd"></path></svg>
```

### Help

```svg
<svg data-internal-name="hr-colorless-icon" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1.5rem" fill="currentColor" color="inherit" viewBox="0 0 16 16"><path fill-rule="evenodd" d="M8 2.309a5.692 5.692 0 1 0 0 11.383A5.692 5.692 0 0 0 8 2.309M.808 8a7.192 7.192 0 1 1 14.384 0A7.192 7.192 0 0 1 .808 8" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M8 6.245a1.755 1.755 0 1 0 0 3.51 1.755 1.755 0 0 0 0-3.51M4.745 8a3.255 3.255 0 1 1 6.51 0 3.255 3.255 0 0 1-6.51 0" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M13.085 2.916a.75.75 0 0 1 0 1.06L10.302 6.76A.75.75 0 1 1 9.24 5.7l2.783-2.783a.75.75 0 0 1 1.06 0ZM6.759 9.241a.75.75 0 0 1 0 1.06l-2.783 2.784a.75.75 0 0 1-1.06-1.061l2.782-2.783a.75.75 0 0 1 1.061 0M2.915 2.916a.75.75 0 0 1 1.061 0L6.76 5.699a.75.75 0 1 1-1.06 1.06L2.914 3.976a.75.75 0 0 1 0-1.06Zm6.326 6.325a.75.75 0 0 1 1.06 0l2.784 2.783a.75.75 0 0 1-1.061 1.06L9.24 10.303a.75.75 0 0 1 0-1.06Z" clip-rule="evenodd"></path></svg>
```

### Bank

```svg
<svg nv_id="861" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="862" fill-rule="evenodd" clip-rule="evenodd" d="M5.193 5.154a.75.75 0 01.75.75v4.428a.75.75 0 01-1.5 0V5.904a.75.75 0 01.75-.75zm-2.979 0a.75.75 0 01.75.75v4.428a.75.75 0 01-1.5 0V5.904a.75.75 0 01.75-.75z"></path><path nv_id="863" fill-rule="evenodd" clip-rule="evenodd" d="M.818 10.099a.75.75 0 01.713-.517h4.785a.75.75 0 110 1.5H2.074l-.477 1.461v.176h4.72a.75.75 0 010 1.5H.846a.75.75 0 01-.75-.75v-1.045a.75.75 0 01.037-.233l.684-2.092zM6.7.476a.75.75 0 01.6 0l6.153 2.685c.273.12.45.39.45.688v2.055a.75.75 0 01-.75.75H.847a.75.75 0 01-.75-.75V3.849a.75.75 0 01.45-.688L6.7.476zM1.597 4.34v.814h10.806V4.34L7 1.98 1.597 4.34zm11.556 3.935a.75.75 0 01.75.75v4.103a.75.75 0 11-1.5 0V9.024a.75.75 0 01.75-.75zm-4.786 0a.75.75 0 01.75.75v4.103a.75.75 0 01-1.5 0V9.025a.75.75 0 01.75-.75z"></path><path nv_id="864" fill-rule="evenodd" clip-rule="evenodd" d="M8.773 7.611c.547-.234 1.25-.361 1.987-.361.737 0 1.44.126 1.987.36.272.117.54.275.752.49.214.216.404.53.404.926a.75.75 0 01-1.492.108 1.039 1.039 0 00-.254-.144c-.32-.137-.813-.24-1.397-.24-.583 0-1.077.103-1.396.24-.134.057-.214.11-.255.145a.75.75 0 01-1.492-.11c0-.395.191-.709.405-.926.211-.213.48-.371.751-.488zm.337 5.408a.75.75 0 00-1.493.109c0 .395.19.709.405.926.21.214.479.372.751.488.547.235 1.25.361 1.987.361.737 0 1.44-.127 1.987-.361.272-.117.54-.275.751-.488.214-.217.405-.53.405-.925a.75.75 0 00-1.492-.111 1.04 1.04 0 01-.255.145c-.319.137-.812.24-1.396.24-.584 0-1.077-.103-1.396-.24a1.034 1.034 0 01-.255-.144z"></path><path nv_id="865" fill-rule="evenodd" clip-rule="evenodd" d="M9.11 9.942a.75.75 0 00-1.493.11c0 .394.19.708.405.925.21.214.479.372.751.489.547.234 1.25.36 1.987.36.737 0 1.44-.126 1.987-.36.272-.117.54-.275.751-.489a1.32 1.32 0 00.405-.925.75.75 0 00-1.492-.11 1.04 1.04 0 01-.255.145c-.319.137-.812.24-1.396.24-.584 0-1.077-.103-1.396-.24a1.04 1.04 0 01-.255-.145z"></path></svg>
```

### Open link

```svg
<svg nv_id="1158" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="1159" fill-rule="evenodd" clip-rule="evenodd" d="M9.917 2a.75.75 0 01.75-.75H14a.75.75 0 01.75.75v3.333a.75.75 0 01-1.5 0V2.75h-2.583a.75.75 0 01-.75-.75z"></path><path nv_id="1160" fill-rule="evenodd" clip-rule="evenodd" d="M14.53 1.47a.75.75 0 010 1.06L9.864 7.197a.75.75 0 01-1.061-1.06L13.47 1.47a.75.75 0 011.06 0z"></path><path nv_id="1161" fill-rule="evenodd" clip-rule="evenodd" d="M3.333 4.083a.583.583 0 00-.583.584v8c0 .322.26.583.583.583h8c.323 0 .584-.26.584-.583V9.333a.75.75 0 011.5 0v3.334c0 1.15-.933 2.083-2.084 2.083h-8a2.083 2.083 0 01-2.083-2.083v-8c0-1.151.932-2.084 2.083-2.084h3.334a.75.75 0 010 1.5H3.333z"></path></svg>
```

### Pig

```svg
<svg data-internal-name="payroll-colorless-icon" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1.5rem" fill="currentColor" color="inherit" viewBox="0 0 16 16"><path fill-rule="evenodd" d="M11.27 7.781a.781.781 0 1 1 1.104 1.106.781.781 0 0 1-1.104-1.106M5.072 4a3.083 3.083 0 1 1 6.167 0c0 .476-.115.915-.296 1.303a.75.75 0 0 1-1.36-.635c.1-.213.155-.437.155-.668a1.583 1.583 0 1 0-3.166 0c0 .27.076.53.21.768a.75.75 0 0 1-1.305.738A3.05 3.05 0 0 1 5.072 4M2.34 6.184a.75.75 0 0 1-.29 1.02.53.53 0 0 0-.291.459c0 .269.238.544.604.544h.792a.75.75 0 1 1 0 1.5h-.792c-1.13 0-2.104-.884-2.104-2.044 0-.779.447-1.428 1.062-1.77a.75.75 0 0 1 1.02.29Z" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M11.415 5.333a1.23 1.23 0 0 1 .657-.247zm.657-.247v.95c0 .212.09.415.247.557.396.357.702.795.882 1.288a.75.75 0 0 0 .705.492h.5v1.877h-.792a.75.75 0 0 0-.649.375 3.3 3.3 0 0 1-1.187 1.187.75.75 0 0 0-.373.649v1.122H10.24V13a.75.75 0 0 0-.75-.75H7.154a.75.75 0 0 0-.75.75v.495H5.238v-1.52a.75.75 0 0 0-.249-.558A3.24 3.24 0 0 1 3.905 9a3.25 3.25 0 0 1 3.25-3.25h3.017c.451 0 .887-.148 1.243-.417m-.902-1.198a2.72 2.72 0 0 1 1.642-.552h.667a.75.75 0 0 1 .75.75V5.72c.327.338.607.725.824 1.154h.092c.783 0 1.417.635 1.417 1.417v2.043c0 .783-.634 1.417-1.416 1.417h-.466c-.308.433-.687.81-1.118 1.118v.799c0 .782-.634 1.416-1.416 1.416h-1.334c-.754 0-1.371-.59-1.414-1.333h-.846a1.417 1.417 0 0 1-1.407 1.245H5.155a1.417 1.417 0 0 1-1.417-1.416v-1.286A4.73 4.73 0 0 1 2.405 9a4.75 4.75 0 0 1 4.75-4.75h3.017c.122 0 .241-.04.34-.115Z" clip-rule="evenodd"></path></svg>
```

### Calendar

```svg
<svg nv_id="746" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1.25rem" fill="currentColor" viewBox="0 0 16 16"><path nv_id="747" fill-rule="evenodd" clip-rule="evenodd" d="M4.62.495a.75.75 0 01.75.75v2.25a.75.75 0 01-1.5 0v-2.25a.75.75 0 01.75-.75zm6.75 0a.75.75 0 01.75.75v2.25a.75.75 0 01-1.5 0v-2.25a.75.75 0 01.75-.75zM.495 6.12a.75.75 0 01.75-.75h13.5a.75.75 0 010 1.5h-13.5a.75.75 0 01-.75-.75z"></path><path nv_id="748" fill-rule="evenodd" clip-rule="evenodd" d="M.495 4.37a2.75 2.75 0 012.75-2.75h9.5a2.75 2.75 0 012.75 2.75v8.375a2.75 2.75 0 01-2.75 2.75h-9.5a2.75 2.75 0 01-2.75-2.75V4.37zm2.75-1.25c-.69 0-1.25.56-1.25 1.25v8.375c0 .69.56 1.25 1.25 1.25h9.5c.69 0 1.25-.56 1.25-1.25V4.37c0-.69-.56-1.25-1.25-1.25h-9.5z"></path></svg>
```

### Arrow down

```svg
<svg aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="24px" height="24px" fill="currentColor" color="#0a8080" viewBox="0 0 16 16"><path fill-rule="evenodd" d="M8 2.583a.75.75 0 0 1 .75.75v9.334a.75.75 0 0 1-1.5 0V3.333a.75.75 0 0 1 .75-.75" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M4.136 8.802a.75.75 0 0 1 1.06 0L8 11.606l2.804-2.804a.75.75 0 1 1 1.06 1.06L8.53 13.198a.75.75 0 0 1-1.06 0L4.136 9.863a.75.75 0 0 1 0-1.06Z" clip-rule="evenodd"></path></svg>
```

### Arrow left

```svg
<svg nv_id="1014" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="1015" fill-rule="evenodd" clip-rule="evenodd" d="M2.583 8a.75.75 0 01.75-.75h9.334a.75.75 0 010 1.5H3.333a.75.75 0 01-.75-.75z"></path><path nv_id="1016" fill-rule="evenodd" clip-rule="evenodd" d="M7.197 4.136a.75.75 0 010 1.061L3.864 8.53a.75.75 0 01-1.061-1.06l3.333-3.334a.75.75 0 011.061 0z"></path><path nv_id="1017" fill-rule="evenodd" clip-rule="evenodd" d="M2.803 7.47a.75.75 0 011.06 0l3.334 3.333a.75.75 0 01-1.06 1.06L2.802 8.53a.75.75 0 010-1.06z"></path></svg>
```

### People

```svg
<svg nv_id="606" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW NavSide-module__chevron___wfgXn" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="607" fill-rule="evenodd" clip-rule="evenodd" d="M4 10.75a1.925 1.925 0 00-1.917 1.917.75.75 0 01-1.5 0A3.425 3.425 0 014 9.25h2.667a3.424 3.424 0 013.416 3.417.75.75 0 11-1.5 0 1.925 1.925 0 00-1.916-1.917H4zm2.48-6.21c-.684-.625-1.73-.605-2.264-.03-.573.618-.56 1.572.067 2.246.537.579 1.523.596 2.207-.039.6-.557.631-1.525-.01-2.177zm1.027-1.093c-1.184-1.095-3.195-1.244-4.39.043-1.16 1.249-1.04 3.095.067 4.287 1.196 1.288 3.144 1.137 4.326.04 1.263-1.173 1.235-3.128.024-4.343a.84.84 0 00-.027-.027zm2.41 5.886a.75.75 0 01.75-.75h2a2.714 2.714 0 012.75 2.75.75.75 0 11-1.5 0c0-.719-.531-1.25-1.25-1.25h-2a.75.75 0 01-.75-.75zm2.433-4.322c-.397-.344-.997-.303-1.286-.014a.74.74 0 01-.029.027c-.286.257-.338.837.056 1.274.257.286.837.338 1.274-.055.315-.284.36-.842-.015-1.232zm1.013-1.107c-.932-.833-2.439-.873-3.346.018-1.031.945-.844 2.488-.041 3.38.943 1.047 2.496.862 3.392.056 1.013-.911.972-2.473.034-3.416a.914.914 0 00-.04-.038z"></path></svg>
```

### Lamp

```svg
<svg nv_id="758" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="759" d="M9.86 13.73h-4c-.41 0-.75.34-.75.75s.34.75.75.75h4c.41 0 .75-.34.75-.75s-.34-.75-.75-.75zm1.82-11.22C10.67 1.52 9.36.96 7.92 1c-1.39.02-2.75.63-3.73 1.68a5.294 5.294 0 00-1.44 3.83c.06 1.3.62 2.53 1.54 3.45.04.04.08.08.12.11.08.07.15.15.24.22.38.31.6.76.6 1.23v.71c0 .41.34.75.75.75h4c.41 0 .75-.34.75-.75v-.71c0-.47.21-.92.58-1.22 1.22-1 1.92-2.48 1.92-4.05 0-1.42-.56-2.75-1.57-3.74zm-4.93 8.97c0-.44-.11-.86-.29-1.25h3.08c-.18.39-.28.81-.29 1.25h-2.5zM5.22 8.73a3.69 3.69 0 01-.96-2.29c-.05-.99.33-1.99 1.03-2.74.7-.75 1.67-1.19 2.66-1.2.99-.02 1.97.37 2.69 1.08.72.71 1.12 1.66 1.12 2.67 0 .93-.36 1.8-.97 2.48H5.22z"></path></svg>
```

### User squared

```svg
<svg nv_id="766" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="767" fill-rule="evenodd" clip-rule="evenodd" d="M8.663 5.273A.938.938 0 107.337 6.6a.938.938 0 001.326-1.326zm1.061-1.06a2.438 2.438 0 10-3.448 3.446 2.438 2.438 0 003.448-3.446zM4.017 11.29a2.929 2.929 0 012.89-2.473h2.185a2.93 2.93 0 012.89 2.472.75.75 0 11-1.48.236 1.43 1.43 0 00-1.41-1.208H6.908c-.71 0-1.3.52-1.41 1.208a.75.75 0 01-1.48-.236z"></path><path nv_id="768" fill-rule="evenodd" clip-rule="evenodd" d="M4.248 1.997a2.251 2.251 0 00-2.25 2.251v7.504a2.251 2.251 0 002.25 2.25h7.504a2.251 2.251 0 002.25-2.25V4.248a2.251 2.251 0 00-2.25-2.25H4.248zM.498 4.248a3.751 3.751 0 013.75-3.75h7.504a3.751 3.751 0 013.75 3.75v7.504a3.751 3.751 0 01-3.75 3.75H4.248a3.751 3.751 0 01-3.75-3.75V4.248z"></path></svg>
```

### Key

```svg
<svg nv_id="783" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="784" fill-rule="evenodd" clip-rule="evenodd" d="M8.989 5.347c.158-.16.41-.31.726-.31s.568.15.727.31c.159.158.309.411.309.726 0 .316-.15.568-.309.727a1.035 1.035 0 01-.727.309c-.315 0-.568-.15-.726-.309a1.035 1.035 0 01-.31-.727c0-.315.15-.568.31-.726z"></path><path nv_id="785" fill-rule="evenodd" clip-rule="evenodd" d="M9.295 2.771c-.344-.124-.584-.06-.763.12L6.46 4.96a.804.804 0 00-.192.83L6.9 7.194a.75.75 0 01-.158.842l-4.06 3.993v1.221h1.224l4.065-4.065a.75.75 0 01.826-.159l1.482.635c.344.125.584.06.763-.119l2.043-2.044c.189-.239.244-.516.146-.79l-1.063-2.482-.007-.016a.665.665 0 00-.368-.368l-.017-.007L9.295 2.77zM7.47 1.83c.675-.676 1.574-.755 2.365-.458l.032.013 2.492 1.068c.54.219.97.65 1.19 1.19l1.068 2.492.013.032c.32.855.095 1.706-.412 2.315a.755.755 0 01-.046.05l-2.07 2.07c-.676.676-1.574.755-2.365.458a.895.895 0 01-.033-.013l-1.032-.442-3.926 3.925a.75.75 0 01-.53.22H1.93a.75.75 0 01-.75-.75v-2.285a.75.75 0 01.225-.535l3.912-3.847-.429-.952a2.304 2.304 0 01.51-2.48L7.472 1.83z"></path></svg>
```

### Calculator

```svg
<svg nv_id="665" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="666" fill-rule="evenodd" clip-rule="evenodd" d="M4.004 1.5a.754.754 0 00-.754.754v10.992c0 .416.338.754.754.754h7.881a.755.755 0 00.755-.755V2.255a.755.755 0 00-.756-.755h-7.88zm7.881 14h-7.88a2.254 2.254 0 01-2.255-2.254V2.254A2.254 2.254 0 014.004 0h7.88a2.255 2.255 0 012.255 2.254v10.99a2.255 2.255 0 01-2.254 2.256z"></path><path nv_id="667" fill-rule="evenodd" clip-rule="evenodd" d="M1.75 4.638a.75.75 0 01.75-.75h10.89a.75.75 0 010 1.5H2.5a.75.75 0 01-.75-.75zm3.085 4.656a.789.789 0 100 1.578.789.789 0 000-1.578zm3.035.005a.788.788 0 10.151 1.57.788.788 0 00-.152-1.57zm3.188-.005a.789.789 0 100 1.578.789.789 0 000-1.578zM4.835 6.96a.789.789 0 100 1.578.789.789 0 000-1.578zm3.035.006a.789.789 0 10.151 1.57.789.789 0 00-.152-1.57zm3.188-.006a.789.789 0 100 1.578.789.789 0 000-1.578zm-6.223 4.667a.789.789 0 100 1.578.789.789 0 000-1.578zm3.035.005a.789.789 0 10.152 1.57.789.789 0 00-.153-1.57zm3.188-.005a.789.789 0 100 1.578.789.789 0 000-1.578z"></path></svg>
```

### Certificate (variant 2)

Filed under the same name as the icon above in the source document; kept separate here so
both glyphs survive.

```svg
<svg nv_id="1078" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="1079" d="M8 7.5S9.5 5.828 9.5 5a1.5 1.5 0 10-3 0C6.5 5.828 8 7.5 8 7.5z"></path><path nv_id="1080" fill-rule="evenodd" clip-rule="evenodd" d="M10.982 1.675a4.457 4.457 0 00-6.548 5.987l.667.89-3.03 5.194a1 1 0 00.864 1.504H5.86a1 1 0 00.852-.476L8 12.681l1.288 2.093a1 1 0 00.852.476h2.925a1 1 0 00.864-1.504l-3.03-5.194.667-.89a4.457 4.457 0 00-.584-5.987zM6.022 2.79a2.957 2.957 0 014.344 3.972L9.1 8.448l3.093 5.302H10.42L8 9.819 5.58 13.75H3.807L6.9 8.448 5.635 6.762a2.957 2.957 0 01.388-3.972z"></path></svg>
```

### Building

```svg
<svg nv_id="1065" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="1066" fill-rule="evenodd" clip-rule="evenodd" d="M.25 14.65A.75.75 0 011 13.9h14a.75.75 0 010 1.5H1a.75.75 0 01-.75-.75zm2.886-6.49a4.944 4.944 0 019.728 0l.074.406a.75.75 0 11-1.476.268l-.074-.406a3.444 3.444 0 00-6.776 0l-.074.406a.75.75 0 11-1.476-.268l.074-.407z"></path><path nv_id="1067" fill-rule="evenodd" clip-rule="evenodd" d="M8 2a.75.75 0 01.75.75v2.1a.75.75 0 11-1.5 0v-2.1A.75.75 0 018 2z"></path><path nv_id="1068" fill-rule="evenodd" clip-rule="evenodd" d="M7.25 1.5C7.25.81 7.81.25 8.5.25h1.8c.69 0 1.25.56 1.25 1.25v1.1c0 .69-.56 1.25-1.25 1.25H8.5c-.69 0-1.25-.56-1.25-1.25V1.5zm1.5.25v.6h1.3v-.6h-1.3zM6.2 7.95a.75.75 0 01.75.75v5.95a.75.75 0 01-1.5 0V8.7a.75.75 0 01.75-.75zm3.6 0a.75.75 0 01.75.75v5.95a.75.75 0 01-1.5 0V8.7a.75.75 0 01.75-.75z"></path><path nv_id="1069" fill-rule="evenodd" clip-rule="evenodd" d="M1.65 9.4c0-.8.65-1.45 1.45-1.45h9.8c.8 0 1.45.65 1.45 1.45v5.25a.75.75 0 01-1.5 0v-5.2h-9.7v5.2a.75.75 0 01-1.5 0V9.4z"></path></svg>
```

### Pencil

```svg
<svg nv_id="1122" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="1123" fill-rule="evenodd" clip-rule="evenodd" d="M8.88 3.315a.75.75 0 011.06 0l2.745 2.745a.75.75 0 01-1.06 1.06L8.88 4.375a.75.75 0 010-1.06z"></path><path nv_id="1124" fill-rule="evenodd" clip-rule="evenodd" d="M12.001 2.314L2.01 12.3a.053.053 0 00-.009.012V14h1.581a.748.748 0 01.107-.008h.004L13.686 4 12 2.314zM3.835 15.485c.34-.032.663-.178.915-.43l9.998-9.997a1.497 1.497 0 000-2.118L13.06 1.252a1.497 1.497 0 00-2.118 0L.937 11.25l-.014.015c-.255.27-.423.638-.423 1.049v2.437c0 .414.336.75.75.75h2.438c.05 0 .1-.005.147-.014z"></path></svg>
```

### Trophy

```svg
<svg data-internal-name="talent-colorless-icon" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1.5rem" fill="currentColor" color="inherit" viewBox="0 0 16 16"><path fill-rule="evenodd" d="M5.417 3.083v5.25c0 1.059.858 1.917 1.916 1.917h1.334a1.917 1.917 0 0 0 1.916-1.917v-5.25zM3.917 3c0-.782.634-1.417 1.416-1.417h5.334c.782 0 1.416.635 1.416 1.417v5.333a3.417 3.417 0 0 1-3.416 3.417H7.333a3.417 3.417 0 0 1-3.416-3.417z" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M7.25 13.667V11h1.5v2.667z" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M5.25 13.667a.75.75 0 0 1 .75-.75h4a.75.75 0 0 1 0 1.5H6a.75.75 0 0 1-.75-.75m5.333-9.334a.75.75 0 0 1 .75-.75h2c.783 0 1.417.635 1.417 1.417v1.333A2.75 2.75 0 0 1 12 9.083h-.667a.75.75 0 0 1 0-1.5H12c.69 0 1.25-.56 1.25-1.25v-1.25h-1.917a.75.75 0 0 1-.75-.75M1.25 5c0-.782.634-1.417 1.417-1.417h2a.75.75 0 0 1 0 1.5H2.75v1.25c0 .69.56 1.25 1.25 1.25h.667a.75.75 0 0 1 0 1.5H4a2.75 2.75 0 0 1-2.75-2.75z" clip-rule="evenodd"></path></svg>
```

### Flash

```svg
<svg nv_id="1073" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="1074" fill-rule="evenodd" clip-rule="evenodd" d="M9.098.312a.75.75 0 01.444.797l-.674 4.585H14a.75.75 0 01.587 1.217l-6.8 8.556a.75.75 0 01-1.33-.576l.675-4.585H2a.75.75 0 01-.587-1.217l6.8-8.556a.75.75 0 01.885-.221zM3.554 8.806H8a.75.75 0 01.742.859l-.395 2.686 4.099-5.157H8a.75.75 0 01-.742-.859l.395-2.686-4.099 5.157z"></path></svg>
```

### Play

```svg
<svg data-internal-name="Video" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1rem" fill="currentColor" color="inherit" viewBox="0 0 16 16"><path fill-rule="evenodd" d="M7.067 6.505v.004l-.003-.002.001-.002M9.552 8 7.067 6.509V9.49l-.003.002h.002l.001.002V9.49zm.003.002L9.552 8l.003-.002v.003Zm0 0L9.556 8zm-3.228-2.8a1.5 1.5 0 0 1 1.509.019l2.491 1.494a1.498 1.498 0 0 1 0 2.57l-2.491 1.494a1.498 1.498 0 0 1-2.269-1.284v-2.99c0-.54.29-1.037.76-1.303" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M.518 5.268a4.75 4.75 0 0 1 4.75-4.75h5.464a4.75 4.75 0 0 1 4.75 4.75v5.464a4.75 4.75 0 0 1-4.75 4.75H5.268a4.75 4.75 0 0 1-4.75-4.75zm4.75-3.25a3.25 3.25 0 0 0-3.25 3.25v5.464a3.25 3.25 0 0 0 3.25 3.25h5.464a3.25 3.25 0 0 0 3.25-3.25V5.268a3.25 3.25 0 0 0-3.25-3.25z" clip-rule="evenodd"></path></svg>
```

### Eye

```svg
<svg aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW LinkColumn_actionIcons__KIXif" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path fill-rule="evenodd" d="M2.128 8c.65 1.199 1.573 2.313 2.619 3.121C5.8 11.935 6.93 12.403 8 12.403s2.2-.468 3.253-1.282c1.046-.808 1.968-1.922 2.619-3.12-.65-1.2-1.573-2.314-2.619-3.122C10.2 4.065 9.071 3.597 8 3.597c-1.07 0-2.2.468-3.253 1.282C3.7 5.687 2.779 6.8 2.128 7.999ZM3.83 3.692C5.054 2.746 6.498 2.097 8 2.097s2.946.649 4.17 1.595c1.229.95 2.286 2.235 3.028 3.608.236.436.236.964 0 1.4-.742 1.373-1.8 2.659-3.028 3.608-1.224.946-2.668 1.595-4.17 1.595s-2.946-.649-4.17-1.595C2.6 11.358 1.544 10.073.802 8.7a1.48 1.48 0 0 1 0-1.4c.742-1.373 1.8-2.659 3.028-3.608" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M9.056 6.943a1.494 1.494 0 1 0-2.112 2.113 1.494 1.494 0 0 0 2.112-2.113m1.01-1.01a2.923 2.923 0 1 0-4.133 4.134 2.923 2.923 0 0 0 4.134-4.134Z" clip-rule="evenodd"></path></svg>
```

### Reports

```svg
<svg nv_id="853" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="854" fill-rule="evenodd" clip-rule="evenodd" d="M4.25 7.25A.75.75 0 015 8v3.75a.75.75 0 01-1.5 0V8a.75.75 0 01.75-.75zm3-4.5A.75.75 0 018 3.5v8.25a.75.75 0 01-1.5 0V3.5a.75.75 0 01.75-.75zm3 3.675a.75.75 0 01.75.75v4.575a.75.75 0 01-1.5 0V7.175a.75.75 0 01.75-.75zm3-2.925a.75.75 0 01.75.75v7.5a.75.75 0 01-1.5 0v-7.5a.75.75 0 01.75-.75z"></path><path nv_id="855" fill-rule="evenodd" clip-rule="evenodd" d="M1.25.5a.75.75 0 01.75.75v12c0 .415.335.75.75.75h12a.75.75 0 010 1.5h-12A2.25 2.25 0 01.5 13.25v-12A.75.75 0 011.25.5z"></path></svg>
```

### Dollar coin

```svg
<svg aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1rem" height="1rem" fill="currentColor" viewBox="0 0 16 16"><path fill-rule="evenodd" d="M7.187 1.125a.75.75 0 0 1 .75-.75c4.23 0 7.688 3.458 7.688 7.688s-3.458 7.687-7.688 7.687c-2.508 0-4.734-1.221-6.136-3.083a.75.75 0 0 1 1.198-.902c1.136 1.508 2.929 2.485 4.938 2.485 3.402 0 6.188-2.786 6.188-6.188 0-3.4-2.786-6.187-6.188-6.187a.75.75 0 0 1-.75-.75" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M7.937 1.875c-3.4 0-6.187 2.786-6.187 6.188 0 1.384.467 2.663 1.249 3.702a.75.75 0 0 1-1.198.902A7.64 7.64 0 0 1 .25 8.063C.25 3.832 3.708.375 7.937.375a.75.75 0 1 1 0 1.5" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M7.745 3.458a.75.75 0 0 1 .75.75v.964a.75.75 0 1 1-1.5 0v-.964a.75.75 0 0 1 .75-.75m0 6.745a.75.75 0 0 1 .75.75v.964a.75.75 0 0 1-1.5 0v-.964a.75.75 0 0 1 .75-.75" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M5.068 6.525c0-1.161.94-2.103 2.103-2.103h1.056A2.19 2.19 0 0 1 10.41 6.47a.75.75 0 1 1-1.496.1.69.69 0 0 0-.687-.65H7.17a.603.603 0 0 0-.147 1.189l1.806.453a2.103 2.103 0 0 1-.512 4.143H7.262a2.19 2.19 0 0 1-2.184-2.053.75.75 0 0 1 1.497-.098.69.69 0 0 0 .687.651h1.056a.603.603 0 0 0 .147-1.188l-1.806-.453a2.104 2.104 0 0 1-1.591-2.04Z" clip-rule="evenodd"></path></svg>
```

### Message

```svg
<svg aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1rem" height="1rem" fill="currentColor" viewBox="0 0 16 16"><path fill-rule="evenodd" d="M10.085 11.264a.75.75 0 0 1-.115 1.055l-3.75 3.015a.75.75 0 0 1-.94-1.168l3.75-3.016a.75.75 0 0 1 1.055.114" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M5.75 11a.75.75 0 0 1 .75.75v3a.75.75 0 0 1-1.5 0v-3a.75.75 0 0 1 .75-.75" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M5 2a3 3 0 0 0-3 3v3a3 3 0 0 0 3 3h.75a.75.75 0 0 1 0 1.5H5A4.5 4.5 0 0 1 .5 8V5A4.5 4.5 0 0 1 5 .5h6A4.5 4.5 0 0 1 15.5 5v3a4.5 4.5 0 0 1-4.5 4.5H9.5a.75.75 0 0 1 0-1.5H11a3 3 0 0 0 3-3V5a3 3 0 0 0-3-3z" clip-rule="evenodd"></path></svg>
```

### Filter

```svg
<svg nv_id="1050" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="1051" d="M.5 4.75A.75.75 0 011.25 4h13.5a.75.75 0 010 1.5H1.25a.75.75 0 01-.75-.75zM3 8.25a.75.75 0 01.75-.75h8.5a.75.75 0 010 1.5h-8.5A.75.75 0 013 8.25zM6.5 11a.75.75 0 000 1.5h3a.75.75 0 000-1.5h-3z"></path></svg>
```

### Resources

```svg
<svg nv_id="949" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="950" fill-rule="evenodd" clip-rule="evenodd" d="M5.745 12.777a.75.75 0 011.061 0l1.021 1.021a.75.75 0 01-1.06 1.061l-1.022-1.021a.75.75 0 010-1.06z"></path><path nv_id="951" fill-rule="evenodd" clip-rule="evenodd" d="M4.66 10.156a1.535 1.535 0 100 3.071 1.535 1.535 0 000-3.07zm-3.035 1.536a3.035 3.035 0 116.07 0 3.035 3.035 0 01-6.07 0zM9.758.921a.75.75 0 01.75.75v2.462c0 .362.294.656.656.656h2.462a.75.75 0 010 1.5h-2.462a2.156 2.156 0 01-2.156-2.156V1.67a.75.75 0 01.75-.75z"></path><path nv_id="952" fill-rule="evenodd" clip-rule="evenodd" d="M1.625 3.78A2.86 2.86 0 014.485.922h5.454a2.86 2.86 0 012.022.838l1.577 1.577a2.86 2.86 0 01.838 2.022v6.861a2.86 2.86 0 01-2.86 2.86H10.11a.75.75 0 110-1.5h1.406c.75 0 1.36-.609 1.36-1.36V5.358c0-.36-.144-.706-.399-.961L10.9 2.82a1.36 1.36 0 00-.961-.399H4.484c-.75 0-1.36.61-1.36 1.36v3.516a.75.75 0 11-1.5 0V3.78z"></path></svg>
```

### Document

```svg
<svg aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1rem" height="1rem" fill="currentColor" viewBox="0 0 16 16"><path fill-rule="evenodd" d="M1.917 4a2.75 2.75 0 0 1 2.75-2.75h5.085c.73 0 1.429.29 1.945.805l1.58 1.582a2.75 2.75 0 0 1 .806 1.944V12a2.75 2.75 0 0 1-2.75 2.75H4.667A2.75 2.75 0 0 1 1.917 12zm2.75-1.25c-.69 0-1.25.56-1.25 1.25v8c0 .69.56 1.25 1.25 1.25h6.666c.69 0 1.25-.56 1.25-1.25V5.581c0-.331-.131-.65-.366-.884l-1.58-1.58a1.25 1.25 0 0 0-.885-.367z" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M9.667 1.25a.75.75 0 0 1 .75.75v2.333c0 .322.26.584.583.584h2.333a.75.75 0 0 1 0 1.5H11a2.083 2.083 0 0 1-2.083-2.084V2a.75.75 0 0 1 .75-.75" clip-rule="evenodd"></path></svg>
```

### Heart beat

```svg
<svg nv_id="936" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="937" fill-rule="evenodd" clip-rule="evenodd" d="M5.228 2.75C3.37 2.75 2 4.53 2 6.316c0 1.865 1.172 3.6 2.628 4.918.717.648 1.47 1.167 2.115 1.52.322.176.607.305.84.389.246.09.38.107.417.107.037 0 .171-.018.418-.107a6.18 6.18 0 00.84-.39 11.382 11.382 0 002.114-1.52C12.828 9.916 14 8.182 14 6.317c0-1.785-1.37-3.566-3.228-3.566-1.08 0-1.782.53-2.202 1.02a.75.75 0 01-1.14 0c-.42-.49-1.121-1.02-2.202-1.02zM.5 6.316C.5 3.938 2.324 1.25 5.228 1.25c1.219 0 2.131.456 2.772.985a4.264 4.264 0 012.772-.985c2.904 0 4.728 2.688 4.728 5.066 0 2.486-1.536 4.593-3.122 6.03a12.873 12.873 0 01-2.4 1.723c-.374.204-.73.37-1.05.485-.305.11-.632.196-.928.196-.296 0-.623-.086-.928-.196a7.645 7.645 0 01-1.05-.485 12.873 12.873 0 01-2.4-1.724C2.036 10.91.5 8.802.5 6.316z"></path><path nv_id="938" fill-rule="evenodd" clip-rule="evenodd" d="M7.323 5.253a.75.75 0 01.622.442l.928 2.089.355-.533a.75.75 0 01.624-.334h1.481a.75.75 0 010 1.5h-1.08l-.888 1.332a.75.75 0 01-1.31-.111L7.127 7.55l-.355.533a.75.75 0 01-.624.334H4.667a.75.75 0 110-1.5h1.08l.888-1.333a.75.75 0 01.688-.331z"></path></svg>
```

### Heart

```svg
<svg nv_id="697" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="698" fill-rule="evenodd" clip-rule="evenodd" d="M4.813 3.25c-.58.01-1.2.256-1.77.792-.914.86-1.007 2.095-.442 3.512.566 1.42 1.745 2.842 3.023 3.764a16.31 16.31 0 001.726 1.091 7.32 7.32 0 00.647.315 7.518 7.518 0 00.658-.317c.486-.261 1.11-.64 1.747-1.097 1.279-.916 2.449-2.334 3.007-3.753.556-1.414.457-2.652-.46-3.515-.571-.537-1.188-.782-1.767-.792-.574-.01-1.18.212-1.74.738L9.4 4.03l-.111.109-.766.752-.526-.535-.528.532-.438-.433-.314-.311-.112-.11-.035-.035-.011-.01C6 3.463 5.392 3.241 4.814 3.25zm3.183 1.106l-.528.532a.75.75 0 001.054.003l-.526-.535zm.003-1.053c-.19-.189-.378-.374-.414-.407-.813-.766-1.79-1.163-2.797-1.145-1.004.017-1.97.443-2.772 1.198C.476 4.4.52 6.385 1.207 8.11c.686 1.72 2.062 3.36 3.539 4.425a17.97 17.97 0 001.89 1.195c.268.145.51.263.71.349.099.042.197.08.288.109.07.022.21.063.362.063s.291-.041.362-.063c.091-.029.19-.067.29-.11.201-.085.446-.204.716-.349a17.81 17.81 0 001.912-1.199c1.483-1.063 2.852-2.702 3.529-4.423.678-1.726.708-3.71-.83-5.157-.801-.754-1.764-1.181-2.767-1.198-1.006-.018-1.98.38-2.793 1.145-.028.025-.22.214-.416.407z"></path></svg>
```

### Heart filled

```svg
<svg aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW IntegrationsDetail_connectionsIcon__cujww" role="img" focusable="false" width="1rem" height="1rem" fill="currentColor" viewBox="0 0 16 16"><path d="M6.929 2.85c-1.584-1.508-3.657-1.445-5.24.064-2.833 2.7-.116 7.53 3.063 9.854 1.518 1.11 2.965 1.84 3.243 1.84.28 0 1.747-.734 3.28-1.848 3.187-2.314 5.86-7.147 3.027-9.846-1.583-1.51-3.647-1.572-5.23-.063-.054.051-1.077 1.069-1.077 1.069s-.992-.998-1.066-1.07"></path></svg>
```

### Settings

```svg
<svg nv_id="1002" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="1003" d="M8 10.55c-1.41 0-2.55-1.14-2.55-2.55S6.59 5.45 8 5.45 10.55 6.59 10.55 8 9.41 10.55 8 10.55zm0-3.6a1.05 1.05 0 100 2.1 1.05 1.05 0 000-2.1z"></path><path nv_id="1004" d="M8.94 14.75H7.06c-.67 0-1.24-.5-1.33-1.16l-.13-.93c-.15-.08-.29-.16-.42-.25l-.87.35c-.62.25-1.34 0-1.67-.58l-.94-1.63c-.33-.58-.19-1.32.34-1.74l.74-.58v-.5l-.74-.58a1.36 1.36 0 01-.34-1.74l.94-1.63a1.35 1.35 0 011.67-.58l.87.35c.14-.09.28-.17.43-.25l.13-.93c.09-.66.67-1.16 1.33-1.16h1.88c.67 0 1.24.5 1.33 1.16l.13.93c.15.08.29.16.43.25l.87-.35c.62-.25 1.34 0 1.67.58l.94 1.63c.33.58.19 1.33-.34 1.74l-.74.57v.5l.74.58c.53.41.67 1.16.34 1.74l-.94 1.63c-.33.58-1.05.82-1.67.58l-.87-.35c-.14.09-.28.17-.43.25l-.13.93c-.1.66-.67 1.16-1.33 1.16l-.01.01zm-1.75-1.5h1.62l.13-.88c.06-.43.34-.81.75-1.02.13-.07.25-.14.38-.22.38-.25.85-.3 1.26-.14l.82.33.81-1.4-.95-.75a.742.742 0 01-.28-.68c.02-.16.04-.33.04-.5 0-.17-.02-.33-.04-.5-.03-.26.07-.52.28-.68l.95-.75-.81-1.4-.82.33c-.41.16-.88.11-1.26-.14-.12-.08-.25-.15-.38-.22-.41-.2-.68-.58-.75-1.02l-.13-.88H7.19l-.13.88c-.06.43-.34.81-.74 1.02-.13.07-.26.14-.38.22-.38.25-.85.3-1.26.14l-.83-.33-.81 1.4.95.75c.21.16.31.42.28.68-.02.16-.04.33-.04.5 0 .17.02.33.04.5.03.26-.07.52-.28.68l-.95.75.81 1.4.83-.33c.41-.17.88-.11 1.26.14.12.08.25.16.38.22.4.21.68.59.74 1.02l.13.88z"></path></svg>
```

### Question

```svg
<svg aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW LinkColumn_actionIcons__KIXif" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path fill-rule="evenodd" d="M8.683 6.687a.674.674 0 0 0-.708-.605h-.073a.704.704 0 0 0-.714.545.75.75 0 0 1-1.462-.34 2.204 2.204 0 0 1 2.202-1.704 2.174 2.174 0 0 1 2.255 2.084c0 .894-.655 1.433-.993 1.708l-.138.11a3 3 0 0 0-.307.27.75.75 0 0 1-1.495-.088c0-.455.249-.78.432-.972a5 5 0 0 1 .476-.416l.085-.068c.356-.29.432-.42.44-.524" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M11.728 4.272a5.26 5.26 0 0 0-8.026 6.736.75.75 0 0 1 .118.594l-.166.743.744-.165a.75.75 0 0 1 .594.118 5.26 5.26 0 0 0 6.736-8.026M3.664 2.814a6.76 6.76 0 1 1 .748 10.9l-1.583.351a.75.75 0 0 1-.894-.894l.351-1.583a6.76 6.76 0 0 1 1.378-8.774" clip-rule="evenodd"></path><path d="M8.76 10.781a.75.75 0 1 1-1.501 0 .75.75 0 0 1 1.5 0Z"></path></svg>
```

### Folder

```svg
<svg aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW LinkColumn_actionIcons__KIXif" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path fill-rule="evenodd" d="M6.103 1.25a1.5 1.5 0 0 1 1.242.656l1.051 1.549h4.854a2.25 2.25 0 0 1 2.25 2.25V12.5a2.25 2.25 0 0 1-2.25 2.25H2.75A2.25 2.25 0 0 1 .5 12.5v-9a2.25 2.25 0 0 1 2.25-2.25zm.002 1.5H2.75A.75.75 0 0 0 2 3.5v9c0 .414.336.75.75.75h10.5a.75.75 0 0 0 .75-.75V5.705a.75.75 0 0 0-.75-.75H8.397a1.5 1.5 0 0 1-1.243-.66z" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M7.152 4.112a1.5 1.5 0 0 1 1.24-.657.75.75 0 0 1 .001 1.5l-1.05 1.548v.002a1.5 1.5 0 0 1-1.24.655H1.25a.75.75 0 0 1 0-1.5h4.853z" clip-rule="evenodd"></path></svg>
```

### More

```svg
<svg aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW LinkColumn_actionIcons__KIXif" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path fill-rule="evenodd" d="M3.333 2.75a.583.583 0 0 0-.583.583v2c0 .323.26.584.583.584h2a.583.583 0 0 0 .584-.584v-2a.583.583 0 0 0-.584-.583zm-2.083.583c0-1.15.932-2.083 2.083-2.083h2c1.151 0 2.084.932 2.084 2.083v2a2.084 2.084 0 0 1-2.084 2.084h-2A2.083 2.083 0 0 1 1.25 5.333zm9.417-.583a.583.583 0 0 0-.584.583v2c0 .323.261.584.584.584h2a.583.583 0 0 0 .583-.584v-2a.583.583 0 0 0-.583-.583zm-2.084.583c0-1.15.933-2.083 2.084-2.083h2c1.15 0 2.083.932 2.083 2.083v2a2.083 2.083 0 0 1-2.083 2.084h-2a2.083 2.083 0 0 1-2.084-2.084zm-5.25 6.75a.583.583 0 0 0-.583.584v2c0 .322.26.583.583.583h2c.323 0 .584-.26.584-.583v-2a.583.583 0 0 0-.584-.584zm-2.083.584c0-1.151.932-2.084 2.083-2.084h2c1.151 0 2.084.933 2.084 2.084v2c0 1.15-.933 2.083-2.084 2.083h-2a2.083 2.083 0 0 1-2.083-2.083zM11.667 9.25a.75.75 0 0 1 .75.75v3.333a.75.75 0 0 1-1.5 0V10a.75.75 0 0 1 .75-.75" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M9.25 11.667a.75.75 0 0 1 .75-.75h3.333a.75.75 0 0 1 0 1.5H10a.75.75 0 0 1-.75-.75" clip-rule="evenodd"></path></svg>
```

### Hamburger

```svg
<svg nv_id="722" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="723" d="M2 4.5a.75.75 0 01.75-.75h10.5a.75.75 0 010 1.5H2.75A.75.75 0 012 4.5zM2 8a.75.75 0 01.75-.75h10.5a.75.75 0 010 1.5H2.75A.75.75 0 012 8zm.75 2.75a.75.75 0 000 1.5h10.5a.75.75 0 000-1.5H2.75z"></path></svg>
```

### Kebab

```svg
<svg nv_id="1146" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="1147" d="M9.5 2.5a1.5 1.5 0 11-3 0 1.5 1.5 0 013 0zm0 5.5a1.5 1.5 0 11-3 0 1.5 1.5 0 013 0zM8 15a1.5 1.5 0 100-3 1.5 1.5 0 000 3z"></path></svg>
```

### Business

```svg
<svg aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW LinkColumn_actionIcons__KIXif" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path fill-rule="evenodd" d="M2.167 7.757a.75.75 0 0 1 .75.75v4.938c0 .444.36.805.805.805h8.556c.445 0 .805-.36.805-.806V8.508a.75.75 0 0 1 1.5 0v4.938a2.305 2.305 0 0 1-2.305 2.305H3.722a2.306 2.306 0 0 1-2.305-2.306V8.508a.75.75 0 0 1 .75-.75Z" clip-rule="evenodd"></path><path fill-rule="evenodd" d="m2.552 2.324-.768 2.562a.8.8 0 0 0-.034.231v1.327a1.583 1.583 0 1 0 3.167 0V4.89a.75.75 0 1 1 1.5 0v1.555a3.083 3.083 0 0 1-6.167 0V5.117q0-.338.097-.662l.769-2.562A2.31 2.31 0 0 1 3.324.25H9.25a.75.75 0 0 1 0 1.5H3.324a.81.81 0 0 0-.772.574" clip-rule="evenodd"></path><path fill-rule="evenodd" d="m13.447 2.324.769 2.562a.8.8 0 0 1 .034.231v1.327a1.583 1.583 0 1 1-3.167 0V4.89a.75.75 0 0 0-1.5 0v1.555a3.083 3.083 0 0 0 6.167 0V5.117q0-.338-.097-.662l-.769-2.562A2.31 2.31 0 0 0 12.676.25H6.75a.75.75 0 0 0 0 1.5h5.926c.356 0 .67.233.771.574" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M.267 4.889a.75.75 0 0 1 .75-.75h13.966a.75.75 0 0 1 0 1.5H1.017a.75.75 0 0 1-.75-.75" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M5.667 4.139a.75.75 0 0 1 .75.75v1.555a1.583 1.583 0 1 0 3.166 0V4.89a.75.75 0 1 1 1.5 0v1.555a3.083 3.083 0 1 1-6.166 0V4.89a.75.75 0 0 1 .75-.75Z" clip-rule="evenodd"></path></svg>
```

### Filled check rounded

```svg
<svg aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW AvailabilityCell_checkmark__ADLSP" role="img" focusable="false" width="1.25rem" height="1.25rem" fill="currentColor" viewBox="0 0 16 16"><path fill-rule="evenodd" d="M8 .5a7.5 7.5 0 1 0 0 15 7.5 7.5 0 0 0 0-15M5.492 7.893l1.546 1.546 3.97-3.97a.75.75 0 0 1 1.06 1.061l-4.322 4.324a1 1 0 0 1-1.415 0l-1.9-1.9a.75.75 0 1 1 1.06-1.061Z" clip-rule="evenodd"></path></svg>
```

### Yes/Check

```svg
<svg aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW CellContent_checkIcon__6KYIh" role="img" focusable="false" width="1.25rem" height="1.25rem" fill="currentColor" viewBox="0 0 16 16"><path fill-rule="evenodd" d="M6.5 10.44 4.03 7.97a.75.75 0 0 0-1.06 1.06l2.823 2.824a1 1 0 0 0 1.414 0L13.53 5.53a.75.75 0 0 0-1.06-1.06z" clip-rule="evenodd"></path></svg>
```

### Download

```svg
<svg aria-hidden="true" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v12"></path><path d="m7 10 5 5 5-5"></path><path d="M5 21h14"></path></svg>
```

### Import

```svg
<svg nv_id="1150" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="1151" fill-rule="evenodd" clip-rule="evenodd" d="M8 14.75a.75.75 0 01-.75-.75V4.667a.75.75 0 111.5 0V14a.75.75 0 01-.75.75zM14.083 2a.75.75 0 01-.75.75H2.667a.75.75 0 110-1.5h10.666a.75.75 0 01.75.75z"></path><path nv_id="1152" fill-rule="evenodd" clip-rule="evenodd" d="M11.864 8.53a.75.75 0 01-1.06 0L8 5.727 5.197 8.53a.75.75 0 01-1.06-1.06L7.47 4.136a.748.748 0 011.061 0l3.333 3.334a.75.75 0 010 1.06z"></path></svg>
```

### Up and Down for the table

```svg
<svg nv_id="1102" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW gwb_e2Oj9" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="1103" d="M7.375.81a.871.871 0 011.25 0l4.114 4.198c.557.568.163 1.539-.625 1.539H3.885c-.787 0-1.181-.971-.625-1.539L7.375.811zm1.25 14.38a.871.871 0 01-1.25 0l-4.114-4.198c-.557-.568-.163-1.539.625-1.539h8.229c.787 0 1.181.971.625 1.539l-4.115 4.197z"></path></svg>
```

### Filled chevron down

```svg
<svg nv_id="1028" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="1029" d="M8.53 10.77a.75.75 0 01-1.06 0L3.977 7.277a.75.75 0 01.53-1.28h6.986a.75.75 0 01.53 1.28L8.53 10.77z"></path></svg>
```

### Chevron right

```svg
<svg nv_id="728" aria-hidden="true" class="_root_6sgrz_1 _icon_81wep_1" role="img" focusable="false" aria-labelledby=":rik:" width="1em" fill="currentColor" viewBox="0 0 16 16"><title nv_id="729" id=":rik:">Opens in current tab</title><path nv_id="731" fill-rule="evenodd" d="M5.47 3.47a.75.75 0 0 1 1.06 0l4 4a.75.75 0 0 1 0 1.06l-4 4a.75.75 0 0 1-1.06-1.06L8.94 8 5.47 4.53a.75.75 0 0 1 0-1.06" clip-rule="evenodd"></path></svg>
```

### Chevron down

```svg
<svg width="18" height="13" viewBox="0 0 18 13" fill="none" xmlns="http://www.w3.org/2000/svg" class="ArticleBody_tableOfContentsToggleIcon__CNJ0u"><path d="M2.3252 3L9.07724 10L15.4321 3" stroke="black" stroke-width="3" stroke-linecap="square"></path></svg>
```

### Chart

```svg
<svg data-internal-name="insights-colorless-icon" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1.5rem" fill="currentColor" color="inherit" viewBox="0 0 16 16"><path fill-rule="evenodd" d="M2.898 2.19a.707.707 0 0 0-.708.708v10.204c0 .392.316.708.708.708h10.204a.707.707 0 0 0 .708-.708V2.898a.707.707 0 0 0-.708-.708zM.69 2.898C.69 1.678 1.678.69 2.898.69h10.204c1.22 0 2.208.988 2.208 2.208v10.204c0 1.22-.988 2.208-2.208 2.208H2.898A2.207 2.207 0 0 1 .69 13.102z" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M7.25 4.356a.75.75 0 0 1 .75-.75A4.395 4.395 0 0 1 12.395 8a.75.75 0 0 1-.75.75H8A.75.75 0 0 1 7.25 8zm1.5.848V7.25h2.046A2.9 2.9 0 0 0 8.75 5.204" clip-rule="evenodd"></path><path fill-rule="evenodd" d="M6.454 5.652a.75.75 0 0 1-.251 1.03 2.25 2.25 0 0 0-1.097 1.925 2.29 2.29 0 0 0 2.287 2.287c.814 0 1.521-.435 1.924-1.097a.75.75 0 0 1 1.282.78 3.75 3.75 0 0 1-3.206 1.817 3.79 3.79 0 0 1-3.787-3.787c0-1.374.74-2.55 1.818-3.206a.75.75 0 0 1 1.03.251" clip-rule="evenodd"></path></svg>
```

### Warning rounded

```svg
<svg aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW gwb_s7iPy" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path fill-rule="evenodd" d="M.5 8a7.5 7.5 0 1 1 15 0 7.5 7.5 0 0 1-15 0m6.75.25v-3a.75.75 0 0 1 1.5 0v3a.75.75 0 0 1-1.5 0M8 11.5A.75.75 0 1 0 8 10a.75.75 0 0 0 0 1.5" clip-rule="evenodd"></path></svg>
```

### Warning triangle

```svg
<svg nv_id="1053" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW gwb_s7iPy" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="1054" d="M8 9.75A.75.75 0 008.75 9V6a.75.75 0 00-1.5 0v3c0 .414.336.75.75.75zm0 2.5a.75.75 0 100-1.5.75.75 0 000 1.5z"></path><path nv_id="1055" fill-rule="evenodd" clip-rule="evenodd" d="M15.361 11.25l-5.196-9c-.962-1.667-3.368-1.667-4.33 0l-5.196 9C-.323 12.917.879 15 2.804 15h10.392c1.925 0 3.127-2.083 2.165-3.75zM7.134 3a1 1 0 011.732 0l5.196 9a1 1 0 01-.866 1.5H2.804a1 1 0 01-.866-1.5l5.196-9z"></path></svg>
```

### Notification

```svg
<svg nv_id="328" aria-hidden="true" class="_root_6sgrz_1 _icon_81wep_1" role="img" focusable="false" width="1.25rem" fill="currentColor" viewBox="0 0 16 16"><path nv_id="329" fill-rule="evenodd" clip-rule="evenodd" d="M5.783 14.833a.75.75 0 0 1 .75-.75h2.934a.75.75 0 0 1 0 1.5H6.533a.75.75 0 0 1-.75-.75M2.667 5.667a5.334 5.334 0 0 1 10.666 0v2.295q0 .019.02.032l.459.228a2.45 2.45 0 0 1-1.095 4.64H3.283a2.45 2.45 0 0 1-1.096-4.64l.46-.23a.03.03 0 0 0 .02-.03zM8 1.833a3.834 3.834 0 0 0-3.833 3.834v2.295c0 .582-.33 1.111-.847 1.37l-.46.23a.95.95 0 0 0 .424 1.8h9.433a.95.95 0 0 0 .425-1.798l-.46-.23-.004-.001a1.54 1.54 0 0 1-.845-1.371V5.667A3.834 3.834 0 0 0 8 1.833"></path></svg>
```

### Plus in a circle

```svg
<svg nv_id="976" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW icon-module__icon___go7vC" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="977" fill-rule="evenodd" clip-rule="evenodd" d="M12.42 3.58a6.25 6.25 0 10-8.84 8.84 6.25 6.25 0 008.84-8.84zm1.06-1.06A7.75 7.75 0 102.52 13.48 7.75 7.75 0 0013.48 2.52z"></path><path nv_id="978" fill-rule="evenodd" clip-rule="evenodd" d="M8 4.92a.75.75 0 01.75.75v4.66a.75.75 0 01-1.5 0V5.67A.75.75 0 018 4.92z"></path><path nv_id="979" fill-rule="evenodd" clip-rule="evenodd" d="M4.92 8a.75.75 0 01.75-.75h4.66a.75.75 0 110 1.5H5.67A.75.75 0 014.92 8z"></path></svg>
```

### Plus

```svg
<svg nv_id="685" aria-hidden="true" class="_root_6sgrz_1 _icon_81wep_1" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="686" fill-rule="evenodd" d="M2.5 7.98a.75.75 0 0 1 .75-.75h9.5a.75.75 0 0 1 0 1.5h-9.5a.75.75 0 0 1-.75-.75" clip-rule="evenodd"></path><path nv_id="687" fill-rule="evenodd" d="M7.946 2.5a.75.75 0 0 1 .75.75v9.5a.75.75 0 0 1-1.5 0v-9.5a.75.75 0 0 1 .75-.75" clip-rule="evenodd"></path></svg>
```

### Cancel

```svg
<svg aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW CellContent_xIcon__7j1Rq" role="img" focusable="false" width="1.25rem" height="1.25rem" fill="currentColor" viewBox="0 0 16 16"><path fill-rule="evenodd" d="M3.97 3.97a.75.75 0 0 1 1.06 0l7 7a.75.75 0 1 1-1.06 1.06l-7-7a.75.75 0 0 1 0-1.06" clip-rule="evenodd"></path><path fill-rule="evenodd" d="m3.97 10.97 7-7a.75.75 0 1 1 1.06 1.06l-7 7a.75.75 0 0 1-1.06-1.06" clip-rule="evenodd"></path></svg>
```

### Present

```svg
<svg nv_id="752" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1.25rem" fill="currentColor" viewBox="0 0 16 16"><path nv_id="753" fill-rule="evenodd" clip-rule="evenodd" d="M8.163 2.716a2.845 2.845 0 013.083-1.685A2.147 2.147 0 0113.01 3.5l-.04.246a2.4 2.4 0 01-.142.503h.672c.966 0 1.75.784 1.75 1.75v1a1.75 1.75 0 01-1.5 1.732V13.5A1.75 1.75 0 0112 15.25H4a1.75 1.75 0 01-1.75-1.75V8.732A1.75 1.75 0 01.75 7V6c0-.966.784-1.75 1.75-1.75h.668a2.38 2.38 0 01-.134-.485l-.019-.11A2.266 2.266 0 014.93 1.037a3.025 3.025 0 013.194 1.77l.039-.092zm2.45 1.534H9.136l.404-.943c.245-.57.846-.899 1.458-.797.352.059.59.392.532.744l-.042.247a.897.897 0 01-.877.749zm-1.695 1.5H8.75v1.5h4.75a.25.25 0 00.25-.25V6a.25.25 0 00-.25-.25H8.918zm-3.54 0H2.5a.25.25 0 00-.25.25v1c0 .138.112.25.25.25h4.75v-1.5H5.377zm1.446-2.137l.182.637H5.369a.876.876 0 01-.856-.732l-.018-.11a.766.766 0 01.647-.885 1.524 1.524 0 011.682 1.09zM8.75 8.75h3.5v4.75a.25.25 0 01-.25.25H8.75v-5zm-1.5 0v5H4a.25.25 0 01-.25-.25V8.75h3.5z"></path></svg>
```

### Persons badge

```svg
<svg nv_id="374" aria-hidden="true" class="_root_6sgrz_1 _icon_81wep_1" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="375" fill-rule="evenodd" clip-rule="evenodd" d="M6.197 5.97a.75.75 0 1 0-1.06 1.06.75.75 0 0 0 1.06-1.06m1.06-1.061a2.25 2.25 0 1 0-3.181 3.182 2.25 2.25 0 0 0 3.182-3.182Z"></path><path nv_id="376" fill-rule="evenodd" clip-rule="evenodd" d="M2.694 3.083a.61.61 0 0 0-.61.61v8.64c0 .323.26.584.583.584h10.666a.583.583 0 0 0 .584-.584V3.667a.583.583 0 0 0-.584-.584zm-2.11.61a2.11 2.11 0 0 1 2.11-2.11h10.64c1.15 0 2.083.933 2.083 2.084v8.666a2.084 2.084 0 0 1-2.084 2.084H2.667a2.083 2.083 0 0 1-2.084-2.084v-8.64Z"></path><path nv_id="377" fill-rule="evenodd" clip-rule="evenodd" d="M9.25 6.333a.75.75 0 0 1 .75-.75h2.667a.75.75 0 0 1 0 1.5H10a.75.75 0 0 1-.75-.75M9.25 9a.75.75 0 0 1 .75-.75h1.6a.75.75 0 0 1 0 1.5H10A.75.75 0 0 1 9.25 9m-5.962.68a2.87 2.87 0 0 1 1.948-.763h.861a2.865 2.865 0 0 1 2.663 1.805.75.75 0 0 1-1.393.556 1.36 1.36 0 0 0-1.27-.861h-.861a1.365 1.365 0 0 0-1.27.86.75.75 0 1 1-1.393-.555c.158-.396.402-.751.715-1.042"></path></svg>
```

### Time

```svg
<svg nv_id="672" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW NavSide-module__chevron___wfgXn" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="673" fill-rule="evenodd" clip-rule="evenodd" d="M1.5 7.5a6 6 0 1012 0 6 6 0 00-12 0zm6-7.5a7.5 7.5 0 100 15 7.5 7.5 0 000-15z"></path><path nv_id="674" fill-rule="evenodd" clip-rule="evenodd" d="M7 3.239a.75.75 0 01.75.75v3.067l2.382 1.452a.75.75 0 11-.78 1.281L6.61 8.117a.75.75 0 01-.36-.64V3.989a.75.75 0 01.75-.75z"></path></svg>
```

### Alarm

```svg
<svg nv_id="493" aria-hidden="true" class="_root_6sgrz_1 _icon_81wep_1" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="494" fill-rule="evenodd" clip-rule="evenodd" d="M5.988 3.894a5.258 5.258 0 1 0 4.025 9.716 5.258 5.258 0 0 0-4.025-9.716M5.414 2.51a6.758 6.758 0 1 0 5.173 12.487A6.758 6.758 0 0 0 5.414 2.509ZM4.406.976a.75.75 0 0 1-.092 1.057L2.061 3.925a.75.75 0 1 1-.965-1.148L3.349.884a.75.75 0 0 1 1.057.092m7.196.007a.75.75 0 0 1 1.056-.092l2.254 1.893a.75.75 0 1 1-.965 1.149L11.694 2.04a.75.75 0 0 1-.092-1.057"></path><path nv_id="495" fill-rule="evenodd" clip-rule="evenodd" d="M7.812 5.321a.75.75 0 0 1 .75.75v2.685l2.082 1.27a.75.75 0 1 1-.781 1.28L7.422 9.817a.75.75 0 0 1-.36-.64V6.07a.75.75 0 0 1 .75-.75Z"></path></svg>
```

### Star

```svg
<svg nv_id="775" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="776" fill-rule="evenodd" clip-rule="evenodd" d="M6.347 1.971c.545-1.567 2.761-1.567 3.306 0l.943 2.713a.25.25 0 00.231.168l2.871.058c1.66.034 2.344 2.142 1.022 3.145L12.431 9.79a.25.25 0 00-.088.271l.832 2.75c.48 1.588-1.313 2.89-2.675 1.943l-2.357-1.64a.25.25 0 00-.286 0L5.5 14.753c-1.362.947-3.155-.355-2.675-1.944l.832-2.749a.25.25 0 00-.088-.271l-2.29-1.735C-.041 7.052.644 4.945 2.302 4.91l2.872-.058a.25.25 0 00.231-.168l.943-2.713zm1.89.493a.25.25 0 00-.473 0L6.82 5.177a1.75 1.75 0 01-1.617 1.175l-2.872.058a.25.25 0 00-.146.45l2.289 1.734c.583.443.83 1.201.618 1.902l-.832 2.749a.25.25 0 00.382.277L7 11.882a1.75 1.75 0 012 0l2.357 1.64a.25.25 0 00.382-.277l-.832-2.75a1.75 1.75 0 01.618-1.9l2.289-1.736a.25.25 0 00-.146-.449l-2.872-.058A1.75 1.75 0 019.18 5.177l-.943-2.713z"></path></svg>
```

### Home

```svg
<svg nv_id="777" aria-hidden="true" class="gwb_Wd4Oi gwb_rvhBW" role="img" focusable="false" width="1em" fill="currentColor" viewBox="0 0 16 16"><path nv_id="778" fill-rule="evenodd" clip-rule="evenodd" d="M2.667 4.983a.75.75 0 01.75.75v7.517h9.166V5.733a.75.75 0 011.5 0V14a.75.75 0 01-.75.75H2.667a.75.75 0 01-.75-.75V5.733a.75.75 0 01.75-.75z"></path><path nv_id="779" fill-rule="evenodd" clip-rule="evenodd" d="M7.57 1.386a.75.75 0 01.86 0l6.667 4.666a.75.75 0 01-.86 1.23L8 2.914 1.763 7.281a.75.75 0 11-.86-1.229L7.57 1.386zM5.25 10c0-1.15.932-2.083 2.083-2.083h1.334c1.15 0 2.083.932 2.083 2.083v4a.75.75 0 01-1.5 0v-4a.583.583 0 00-.583-.583H7.333A.583.583 0 006.75 10v4a.75.75 0 01-1.5 0v-4z"></path></svg>
```

### Filled Sparcling

```svg
<svg xmlns="http://www.w3.org/2000/svg" width="15" height="16" fill="none"><path fill="#0A8080" d="M5.737 1.166c.226-.688 1.2-.688 1.425 0l1.15 3.494a.75.75 0 0 0 .477.478l3.495 1.15c.687.225.687 1.198 0 1.424l-3.495 1.15a.75.75 0 0 0-.478.477l-1.149 3.495c-.226.687-1.199.687-1.425 0L4.588 9.338a.75.75 0 0 0-.478-.478L.616 7.712c-.687-.226-.687-1.199 0-1.425L4.11 5.138a.75.75 0 0 0 .478-.478l1.15-3.494Zm6.238 9.778a.5.5 0 0 1 .95 0l.312.95a.5.5 0 0 0 .319.319l.95.312a.5.5 0 0 1 0 .95l-.95.312a.5.5 0 0 0-.319.319l-.312.95a.5.5 0 0 1-.95 0l-.313-.95a.5.5 0 0 0-.318-.319l-.95-.312a.5.5 0 0 1 0-.95l.95-.313a.5.5 0 0 0 .319-.318l.312-.95Z"/></svg>
```

