# UI system

The browser uses Inter for numeric values and UI text, MiSans for Chinese glyphs, and Roboto Mono for English labels and metadata. These are semantic roles, not separate choices for individual weather scenarios.

| Role | Tracking | Numerals |
| --- | --- | --- |
| Labels | `--tracking-label: -.02em` | ordinary text |
| UI / descriptions | `--tracking-text: -.015em` | ordinary text |
| Values and units | `--tracking-number: -.02em` | tabular Inter |
| Large temperature | `--tracking-display: -.04em` | tabular Inter |
| Solar time | `--tracking-time: .01em` | tabular Inter |

Weather and control icons share a 32-unit canvas, flat line ends, lightly rounded joins and a bolder `--icon-stroke` (0.34% of the panel width, clamped to 2–3.4 CSS pixels). Geometry uses `vector-effect: non-scaling-stroke` so different display sizes do not accidentally create different line weights. The larger solar diagram uses the same stroke token and retains its established straight radial rays. Weather forms are deliberately geometric: eight sun rays, one crescent, a single three-arc cloud, three rain strokes, two snowflakes, two wind lines and an open lightning zigzag. Avoid miniature decorative stars, layered rain/moon/thunder combinations and filled emblems.

The weather hero uses the same equal-width columns as the metric grid. Temperature and the solar diagram share a baseline track, range and solar label share the next row, and the weather description shares its text baseline with the event time. The weather symbol is centered beside the description but does not determine the text baseline. All support rows use `--hero-row-gap: 1.6cqw`; a hidden next-day caption removes its row entirely. The temperature is sized to stay inside its half of the grid, with a separate smaller tier for three-character readings. Its unit shares the number's baseline with a compact 0.025em gap. The cloud-cover reading is centered as one baseline-aligned value/percent group inside its track.

The gear's drawing canvas is 4.62cqw, with a minimum 44px hit area and no surrounding circle; keyboard focus uses a short underline. Its SVG box and the header time share the same right alignment line. Header date and time both use 2.65cqw Roboto Mono: enlarge the date to the original clock size. Changes must be checked in Chinese and English, at the target tablet proportion and the affected viewport.
