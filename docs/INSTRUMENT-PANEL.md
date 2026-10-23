# Purpose-driven observation panel

The second page is an independent instrument interface. Open it by swiping right on the weather page, using its observation button, or adding `page=sky` to the preview URL. Swipe left, use the small panorama return control, or press Escape to return. The first weather layout and artwork remain separate.

## Interaction and composition

The lower-right dial is the only control that changes the information set. Drag vertically, scroll, tap a visible purpose or use arrow keys; Home/End select the first/last purpose. Twelve purposes cover commuting, walking, cycling, outdoor stays, sunset/dawn/blue-hour photography, stargazing, distant views, camping, ventilation/drying and equipment moisture.

Selecting a purpose computes the relevant local-time window and replaces related information. For example, commuting emphasizes precipitation and gusts; equipment care shows humidity and dew-point trends; stargazing replaces the cloud-section graphic with lunar optics. The dial does not select or configure widgets. There is no time slider, purpose button strip, export flow, top toolbar, literal gear or chain.

Fourteen modules form the panel: the main vertically divided readout, nested temperature/clock/solar capsule, panoramic atmospheric window, radial scale, hatched orange trend, barcode percentage, double arc, waveform/capsule filter, interchangeable cloud/lunar/dew section, flow schematic, air-clarity readout, monospace wind/pressure ticket, paired environmental readouts and purpose dial. Cards use different physical forms instead of repeating one metric template. On narrow screens the data scrolls while the purpose dial stays at the lower right.

Data is primary. Optional advice is limited to three lines per environment/purpose, each at most twenty Unicode characters in either interface language; most combinations have zero or one line. These limits are covered by the source tests. No motivational headlines or long preparation lists are shown.

## Rendering and typography

`instrument-art.js` supplies a dedicated panoramic shader: layered terrain, cloud bands, water, mist and conditional precipitation. It does not draw the first page's weather artwork. The two programs share a single canvas/context per document to avoid doubling the eleven-scene gallery's WebGL context count. The weather renderer suspends drawing and resizing while the observation program owns presentation, then restores its own program, framebuffer and layout when returning. The panorama targets twelve updates per second, pauses outside the viewport and respects pause/reduced-motion preferences. Browser checks do not establish Android frame rate or battery use. A simpler independent Canvas fallback exists but has not been device-validated.

The page has separate scoped tokens: Space Grotesk for geometry and headings, Rajdhani for thin instrument digits, IBM Plex Mono for body/terminal metadata, and a renamed Noto Sans SC subset for Chinese. They interpret the reference typography; the original typefaces in the supplied pictures are not identified. Official original font bytes, OFL files, source URLs and hashes are retained. The optional fontTools script regenerates the renamed Chinese subset without modifying the upstream font.

## Data and scientific limits

All meteorological profiles and their time evolution are deterministic demonstration fixtures. No live forecast, radar, precise-location request or calibrated probability service is connected. A nearby future window is not a new weather forecast. The panel labels demo data and illustrative trends accordingly.

Solar position is an approximate geometric calculation from time and the sample city's coordinates. Local wall times use the scenario timezone, including DST. Sunrise/sunset uses a level-horizon -0.833° crossing; civil, nautical and astronomical twilight use -6°, -12° and -18°. Photographic windows use selected geometric boundaries: sunset 6° to -6°, dawn -6° to 6°, and evening blue hour -4° to -6°. Missing polar crossings remain unavailable. These are viewing conventions, not precise terrain-aware promises. See [USNO definitions](https://aa.usno.navy.mil/faq/RST_defs) and [solar approximation](https://aa.usno.navy.mil/faq/sun_approx).

Glow is an uncalibrated condition score out of 100, explicitly not a probability. CCT is a palette impression, not measured light temperature. Lunar illumination uses a mean 29.530588853-day lunation approximation, not an ephemeris or moonrise calculation; actual lunations vary, as explained in [NASA's phase notes](https://eclipse.gsfc.nasa.gov/phase/phases2001.html). Illumination alone does not establish moonlight interference at a site.

Cloud percentages describe separate sample layers; diagrams do not establish actual cloud thickness, height-resolved wind or beam optics. Future Open-Meteo integration must follow its [actual variable definitions](https://open-meteo.com/en/docs), including low clouds below 3 km, mid clouds 3–8 km and high clouds above 8 km. Its regional 15-minute products must not be labelled minute radar. Bortle class, PWV, DEM obstruction, historical rarity and actual camera exposure are unavailable and not invented. See [NPS light-pollution context](https://www.nps.gov/subjects/nightskies/lightpollution.htm).

## Verification

Pure checks cover equinox geometry, polar crossings, timezone/DST conversion, lunar-cycle limits, dew-point and wind rules, 66 deterministic sky samples, and 264 purpose/environment/language combinations. Browser inspection covers all eleven sample environments in Chinese and English at the gallery's 1440×960 frame size, real dial dragging and keyboard selection, dedicated WebGL2 rendering, and page handoff. Additional responsive and lifecycle results belong in implementation status only after they are actually observed.
