# Purpose-driven observation panel

The second page is an independent instrument interface. Open it by swiping right on the weather page, using its observation button, or adding `page=sky` to the preview URL. Swipe left, use the small panorama return control, or press Escape to return. The first weather layout and artwork remain separate.

## Interaction and composition

The lower-right dial is the only information control. It chooses twelve user activities and concerns, rather than configuring widgets. Drag, tap a visible purpose, scroll, or use arrow keys; Home/End select endpoints and arrow movement wraps. There is no export flow, time slider, top toolbar, literal gear/chain or motivational headline. Advice remains optional, with at most three lines and twenty Unicode characters per line.

The page uses a new nested instrument chassis. A dedicated illustrated transect occupies approximately one third of the top width; the primary readout occupies approximately four ninths, reduced from the previous two-thirds card. The remaining top strip owns the location, sample date and **only** time window. A `+1` marker disambiguates an overnight interval without adding a duplicate clock.

Ten auxiliary modules sit within one cream assembly, beside the lower-right dial. Wind/gust/direction, pressure/three-hour change and dew spread/dew point/trend are stacked on the left. Smaller temperature, optical or particulate readouts fill short strips; humidity, visibility, cloud coverage, solar elevation and azimuth use different forms. The dew component has no decorative inner casing. Scalar components use compact tracks instead of large empty metric cards. Distant-view purposes shorten the scalar replacement and reassign the freed vertical track.

Fourteen modules remain, including primary, transect, window, ten auxiliary data families and dial. `instrument-content.js` assigns every metric field to exactly one owner. A primary humidity readout replaces the auxiliary humidity module with precipitation; cycling puts wind in the primary and replaces its auxiliary instance; camping combines primary temperature with a separate feels-like value. Cloud percentages appear only in the cloud family. Neither the primary nor the transect repeats the global time window or cloud summary.

The full user decision record and acceptance checklist are maintained in [user design requirements](USER-DESIGN-REQUIREMENTS.md). Earlier screenshots remain historical evidence, not the current design specification.

## Graphic-to-data contract

Generic oscillators, prism diagrams, flow rings and repeated hatched curves have been removed. Each visible graphic has a defined input and scale:

| Family | Quantitative encoding |
| --- | --- |
| Precipitation | Stepped probability envelope from the selected window's 25 illustrative samples, fixed 0–100% scale |
| Humidity | Segmented coverage proportional to relative humidity; shown in exactly one component |
| Wind | Direction vector rotates to the meteorological **from** direction; mean and gust remain in the same group |
| Pressure | Numeric hPa and signed three-hour change; no invented historical waveform |
| Dew spread | Sampled spread curve and a 2°C guide, sharing one component with the spread value and dew point; Fahrenheit differences use scale conversion without a temperature offset |
| Temperature | Selected-window low/high bounds with the initial reading marked within the range |
| Visibility | Calibrated distance gates at 1, 5, 10, 25 and 50 km activate only within the sample visibility; these are scale markers, not simulated optical ray paths |
| Cloud layers | Three independent fills on a common 0–100% coverage scale; height represents coverage, not cloud altitude or physical thickness |
| Solar elevation | Signed -90° to 90° semicircular scale, with zero at the top calibration tick and a needle computed from solar elevation |
| Solar azimuth | Numeric bearing, cardinal direction and moving angular ticks around the current bearing, including the nearest cardinal calibration |
| Lunar illumination | Approximate phase terminator from the mean lunation; no reused weather-scene Moon image |
| Reference CCT | Approximate palette value and marker over a 2000–10000 K colour reference; not a measured camera white balance |
| Glow | Uncalibrated 0–100 condition score encoded by diagonal segments; never labelled probability |
| PM2.5 / feels-like | Compact numerical readouts with units; no unrelated decorative graphic |

## Rendering and typography

`instrument-art.js` supplies a dedicated panoramic shader: layered terrain, cloud bands, water, mist and conditional precipitation. It does not draw the first page's weather artwork. The two programs share a single canvas/context per document to avoid doubling the eleven-scene gallery's WebGL context count. The weather renderer suspends drawing and resizing while the observation program owns presentation, then restores its own program, framebuffer and layout when returning. The panorama targets twelve updates per second, pauses outside the viewport and respects pause/reduced-motion preferences. Browser checks do not establish Android frame rate or battery use. A simpler independent Canvas fallback exists but has not been device-validated.

The page has separate scoped tokens: Space Grotesk for geometry and headings, Rajdhani for thin instrument digits, IBM Plex Mono for body/terminal metadata, and a renamed Noto Sans SC subset for Chinese. They interpret the reference typography; the original typefaces in the supplied pictures are not identified. Official original font bytes, OFL files, source URLs and hashes are retained. The optional fontTools script regenerates the renamed Chinese subset without modifying the upstream font.

## Data and scientific limits

All meteorological profiles and their time evolution are deterministic demonstration fixtures. No live forecast, radar, precise-location request or calibrated probability service is connected. A nearby future window is not a new weather forecast. The panel labels demo data and illustrative trends accordingly.

Solar position is an approximate geometric calculation from time and the sample city's coordinates. Local wall times use the scenario timezone, including DST. Sunrise/sunset uses a level-horizon -0.833° crossing; civil, nautical and astronomical twilight use -6°, -12° and -18°. Photographic windows use selected geometric boundaries: sunset 6° to -6°, dawn -6° to 6°, and evening blue hour -4° to -6°. Missing polar crossings remain unavailable. These are viewing conventions, not precise terrain-aware promises. See [USNO definitions](https://aa.usno.navy.mil/faq/RST_defs) and [solar approximation](https://aa.usno.navy.mil/faq/sun_approx).

Glow is an uncalibrated condition score out of 100, explicitly not a probability. CCT is a palette impression, not measured light temperature. Lunar illumination uses a mean 29.530588853-day lunation approximation, not an ephemeris or moonrise calculation; actual lunations vary, as explained in [NASA's phase notes](https://eclipse.gsfc.nasa.gov/phase/phases2001.html). Illumination alone does not establish moonlight interference at a site.

Cloud percentages describe separate sample layers; diagrams do not establish actual cloud thickness, height-resolved wind or beam optics. Future Open-Meteo integration must follow its [actual variable definitions](https://open-meteo.com/en/docs), including low clouds below 3 km, mid clouds 3–8 km and high clouds above 8 km. Its regional 15-minute products must not be labelled minute radar. Bortle class, PWV, DEM obstruction, historical rarity and actual camera exposure are unavailable and not invented. See [NPS light-pollution context](https://www.nps.gov/subjects/nightskies/lightpollution.htm).

## Verification

Pure checks cover equinox geometry, polar crossings, timezone/DST conversion, lunar-cycle limits, dew-point and wind rules, 66 deterministic sky samples, 264 planning combinations and another 264 information-ownership/graphic-mapping combinations. Browser inspection covers all eleven sample environments in Chinese and English at the gallery's 1440×960 frame size, real dial dragging and keyboard selection, dedicated WebGL2 rendering, and page handoff. Additional responsive and lifecycle results belong in implementation status only after they are actually observed.
