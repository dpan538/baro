# Purpose-driven observation panel

The second page is an independent instrument interface. Open it by swiping right on the weather page, using its observation button, or adding `page=sky` to the preview URL. Swipe left, use the small panorama return control, or press Escape to return. The first weather layout and artwork remain separate.

## Interaction and composition

The lower-right dial is the only information control. It chooses twelve user activities and concerns, rather than configuring widgets. Drag, tap a visible purpose, scroll, or use arrow keys; Home/End select endpoints and arrow movement wraps. There is no export flow, time slider, top toolbar, literal gear/chain or motivational headline. Advice remains optional, with at most three lines and twenty Unicode characters per line.

The page is a nested instrument chassis with **four parallel data columns**. The lower-right dial now occupies roughly 2/9 of the screen, one third narrower than the previous 1/3. The primary readout occupies 2/9, half its previous 4/9. Independent artwork occupies approximately 5/18. The remaining top area contains the single observation interval, a shared two-part CIN/LCL and CAPE stack, and a short-term rainfall-rate histogram.

Twenty-three auxiliary families plus primary, artwork, context and dial make **27 modules**, thirteen more than the earlier fourteen-module design. The cloud component is now one compact track with three horizontal coverage bars; the freed row holds purpose-dependent wet bulb, VPD or temperature tendency. The single time window is retained, while the requested countdown and distinct lunar crossing times have separate meanings. An overnight interval has a `+1` marker.

The four columns form shared groups: surface wind/pressure/dew, moisture/cloud/soil, solar and optical information, and light/lunar/upper-air context. Related neighbours share surfaces and end corners; internal divisions stay square. Only the primary gets a muted coral surface. Core and secondary auxiliary modules use thin coral/ochre accents over neutral group surfaces. Their priority changes with activity and environment. Wind gust/direction metadata is at least 20% larger than the preceding version, with additional title headroom. The upper-wind vectors are enlarged and use relative vector length; direct/diffuse radiation has two full-width tracks. At night those tracks are empty and explicitly labelled zero solar flux rather than inventing a shape.

`instrument-content.js` gives every metric field one owner. A primary humidity reading replaces auxiliary humidity with precipitation; cycling replaces the auxiliary wind family; a lunar primary replaces the lunar phase card with lunar crossing and unmeasured sky-brightness context. Dew spread, dew point and trend remain one group. Visibility has no decorative chart. Primary and artwork never repeat the cloud summary or global time interval.

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
| Visibility | Name, value and km only; distance-gate visualization removed |
| Cloud layers | Three short horizontal coverage fills on a common 0–100% scale; neither height nor thickness is inferred |
| Solar elevation | Signed -90° to 90° semicircular scale, with zero at the top calibration tick and a needle computed from solar elevation |
| Solar azimuth | Numeric bearing, cardinal direction and moving angular ticks around the current bearing, including the nearest cardinal calibration |
| Lunar illumination | Approximate phase terminator from the mean lunation; no reused weather-scene Moon image |
| Reference CCT | Approximate palette value and marker over a 2000–10000 K colour reference; not a measured camera white balance |
| Glow | Uncalibrated 0–100 condition score encoded by diagonal segments; never labelled probability |
| PM2.5 / feels-like | Compact numerical readouts with units; no unrelated decorative graphic |

## Additional physics and source boundaries

`instrument-physics.js` keeps the new illustrative soundings independent from surface relative humidity. All PWV, upper wind, freezing altitude, CAPE/CIN, soil moisture, surface temperature and rainfall-rate values are **demonstration fixtures**, not downloaded forecasts. Their shapes and units are ready for later adapter work; no live integration is claimed.

| New family | Meaning and encoding |
| --- | --- |
| Light countdown | Selected photographic crossing or next golden/blue crossing, relative to the fixture clock; ticks while this view is open. A later date counts from the original scenario time, not from itself. Missing crossings stay unavailable. |
| Lunar context | Mean-lunation phase remains approximate; rise/set use the low-precision SunCalc lunar position formula and a numerical crossing search within the local civil day. Both times are marked approximate. Bortle remains unmeasured. |
| Upper wind | Illustrative 300-hPa wind, meteorological from direction, and vector difference from the surface wind. Arrows point **toward** drift; their lengths compare wind magnitudes. This is a bulk vector difference, not a vertical derivative or aviation hazard classification. |
| PWV | Independent full-column water in mm on a fixed 0–70 mm reference. Never inferred from VPD, never labelled a 0–10 km measurement or a calibrated transparency percentage. |
| Freezing level | Illustrative altitude in metres above sea level. It is not a snow line, cloud base or local icing prediction. |
| CAPE / CIN | Illustrative available/inhibiting energy in J/kg. No thunderstorm probability or severity label is inferred from one parameter. |
| Soil water | Illustrative volumetric water fraction in the **0–1 cm layer**, not two metres. It is not a site-specific dew/ground-slip prediction. |
| Direct / diffuse radiation | Direct-horizontal = DNI × max(0, sin solar elevation), then compare with diffuse-horizontal radiation. The common-plane ratio is absent when diffuse radiation is zero. Full-width tracks have a fixed 1100 W/m² scale. |
| Surface contrast | Surface minus 2-m air temperature in °C, a thermal proxy only. It is not a refractive index, Cn², seeing forecast or a measured heat-haze severity category. |
| Short rain | Illustrative rate in mm/h at nine 15-minute steps over two hours; not minute radar, not a separate thunderstorm probability. |
| VPD | Near-surface saturation vapour pressure deficit in kPa, using the FAO saturation relation and RH. It stays separate from PWV. |
| LCL | Approximate 125 × (T − Td) metres above ground, not a cloud-base observation. |
| Wet bulb | Stull approximation, bounded by dew point and air temperature; outside −20…50°C / 5…99% RH it is unavailable, with saturated air handled as Tw=T. Not WBGT or a health-risk classification. |
| Temperature tendency | The selected illustrative window's average temperature change per hour. Fahrenheit changes use scaling without the absolute-temperature offset. |

Definitions were checked against [Open-Meteo documentation](https://open-meteo.com/en/docs) and its [official variable schema](https://github.com/open-meteo/open-meteo/blob/main/openapi/forecast.yml). VPD is not precipitable water; the schema lists `total_column_integrated_water_vapour`. Radiation must compare the same plane. Regional `minutely_15` fields are not global minute radar. The [NWS CAPE glossary](https://www.weather.gov/spotterguide/glossary_c) explains why instability alone does not ensure thunderstorm initiation. [NPS sky-brightness metrics](https://www.nps.gov/subjects/nightskies/NSQmetrics.html) require site observations for Bortle. Surface approximations use [Stull's water-vapour chapter](https://www.eoas.ubc.ca/books/Practical_Meteorology/prmet102/Ch04-watervapor-v102b.pdf) and [FAO-56 meteorological definitions](https://www.fao.org/4/x0490e/x0490e07.htm). The lunar formula's [SunCalc 1.9.0 source](https://github.com/mourner/suncalc/tree/v1.9.0) is attributed with its BSD notice in `dist/licenses/`; the self-contained gallery embeds that notice.

## Rendering and typography

`instrument-art.js` supplies a dedicated panoramic shader: layered terrain, cloud bands, water, mist and conditional precipitation. It does not draw the first page's weather artwork. The two programs share a single canvas/context per document to avoid doubling the eleven-scene gallery's WebGL context count. The weather renderer suspends drawing and resizing while the observation program owns presentation, then restores its own program, framebuffer and layout when returning. The panorama targets twelve updates per second, pauses outside the viewport and respects pause/reduced-motion preferences. Browser checks do not establish Android frame rate or battery use. A simpler independent Canvas fallback exists but has not been device-validated.

The page has separate scoped tokens: Space Grotesk for geometry and headings, Rajdhani for thin instrument digits, IBM Plex Mono for body/terminal metadata, and a renamed Noto Sans SC subset for Chinese. They interpret the reference typography; the original typefaces in the supplied pictures are not identified. Official original font bytes, OFL files, source URLs and hashes are retained. The optional fontTools script regenerates the renamed Chinese subset without modifying the upstream font.

## Data and scientific limits

All meteorological profiles and their time evolution are deterministic demonstration fixtures. No live forecast, radar, precise-location request or calibrated probability service is connected. A nearby future window is not a new weather forecast. The panel labels demo data and illustrative trends accordingly.

Solar position is an approximate geometric calculation from time and the sample city's coordinates. Local wall times use the scenario timezone, including DST. Sunrise/sunset uses a level-horizon -0.833° crossing; civil, nautical and astronomical twilight use -6°, -12° and -18°. Photographic windows use selected geometric boundaries: sunset 6° to -6°, dawn -6° to 6°, and evening blue hour -4° to -6°. Missing polar crossings remain unavailable. These are viewing conventions, not precise terrain-aware promises. See [USNO definitions](https://aa.usno.navy.mil/faq/RST_defs) and [solar approximation](https://aa.usno.navy.mil/faq/sun_approx).

Glow is an uncalibrated condition score out of 100, explicitly not a probability. CCT is a palette impression, not measured light temperature. Lunar illumination uses a mean 29.530588853-day lunation approximation, not a precise lunar ephemeris; actual lunations vary, as explained in [NASA's phase notes](https://eclipse.gsfc.nasa.gov/phase/phases2001.html). Illumination alone does not establish moonlight interference at a site.

Cloud percentages describe separate sample layers; diagrams do not establish actual cloud thickness, height-resolved wind or beam optics. Future Open-Meteo integration must follow its [actual variable definitions](https://open-meteo.com/en/docs), including low clouds below 3 km, mid clouds 3–8 km and high clouds above 8 km. Its regional 15-minute products must not be labelled minute radar. Bortle class, DEM obstruction, historical rarity and actual camera exposure remain unavailable. PWV is now shown only as an explicitly illustrative sounding. See [NPS light-pollution context](https://www.nps.gov/subjects/nightskies/lightpollution.htm).

## Verification

Pure checks cover equinox geometry, polar crossings, timezone/DST conversion, lunar-cycle limits, dew-point and wind rules, 66 deterministic sky samples, 264 planning combinations and another 264 information-ownership/graphic-mapping combinations. Browser inspection covers all eleven sample environments in Chinese and English at the gallery's 1440×960 frame size, real dial dragging and keyboard selection, dedicated WebGL2 rendering, and page handoff. Additional responsive and lifecycle results belong in implementation status only after they are actually observed.
