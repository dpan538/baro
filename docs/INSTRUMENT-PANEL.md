# Purpose-driven observation panel

The second page is an independent instrument interface. Open it by swiping right on the weather page, using its observation button, or adding `page=sky` to the preview URL. Swipe left, use the small panorama return control, or press Escape to return. The first weather layout and artwork remain separate.

## Interaction and composition

The lower-right dial is the only information control. It chooses twelve user activities and concerns, rather than configuring widgets. Drag, tap a visible purpose, scroll, or use arrow keys; Home/End select endpoints and arrow movement wraps. There is no export flow, time slider, top toolbar, literal gear/chain or motivational headline. A single plain-language interpretation replaces imperative advice. It explains the selected conditions in a larger, rounded paragraph; the latest request supersedes the earlier two-line / 30-character limit.

The page is a nested instrument chassis with **four data groups**. Landscape uses four parallel lanes; 2:3 portrait packs the same groups into two columns, keeping the dial at the lower right and the artwork independent. The lower-right dial occupies roughly 2/9 of the screen, one third narrower than the previous 1/3. The primary readout occupies 2/9, half its previous 4/9, with the single observation interval directly below. Independent artwork occupies approximately 5/18. Three short scalar strips, a two-part CIN/LCL and CAPE stack, and a compact rain histogram with an accumulation strip complete the top band.

Twenty-four auxiliary families plus primary, artwork, context, dial and one environment interpretation make **29 modules**. The interpretation shares a 132px landscape / 156px tablet-portrait footer with the wet-bulb/VPD/temperature-trend strip and extends across the other three lanes. Related scalars use 46–69px strips in the 1600×900 reference viewport, while data-rich diagrams receive more height. The cloud component uses three horizontal coverage bars; wet bulb, VPD or temperature tendency occupy a separate compact purpose-dependent strip. The single time window is retained, while the countdown and distinct lunar crossing times have separate meanings. An overnight interval has a `+1` marker.

The four columns form shared groups: surface wind/pressure/dew, moisture/cloud/soil, solar and optical information, and light/lunar/upper-air context. Related neighbours share surfaces and end corners; internal divisions stay square. Only the primary gets a muted coral surface. There are **no accent sidebars**: core and secondary priority is expressed through restrained coral/ochre heading ink over neutral surfaces. Wind metadata remains enlarged by 20%. Fine radial ticks, a pressure semicircle, a graduated humidity filament field, a dew area, a signed solar-angle diagram with a window trajectory, a soil matrix and a direct/diffuse ray field use distinct quantitative forms. Parallel rays represent the direct component, a scattered fan represents the diffuse component, and a split rail shows their shares on the same horizontal plane. Night replaces the energy paths with a Sun below the horizon and a zero-flux caption.

`instrument-content.js` gives every metric field one owner. A primary humidity reading replaces auxiliary humidity with precipitation; cycling replaces the auxiliary wind family; a lunar primary replaces the lunar phase card with lunar crossing and unmeasured sky-brightness context. Dew spread, dew point and trend remain one group. Visibility has no decorative chart. Primary and artwork never repeat the cloud summary or global time interval.

The full user decision record is maintained in [user design requirements](USER-DESIGN-REQUIREMENTS.md). The [component visual audit](COMPONENT-VISUAL-AUDIT.md) records each module's height, treatment and actual checks. Earlier screenshots remain historical evidence, not the current design specification.

## Graphic-to-data contract

Generic oscillators, prism diagrams, flow rings and repeated hatched curves have been removed. Each visible graphic has a defined input and scale:

| Family | Quantitative encoding |
| --- | --- |
| Precipitation | Twenty-five probability stems with an inactive 0–100% reference and relative window-minute labels; zero/low probability is not inflated into a decorative waveform |
| Humidity | A 42-filament saturation comb with a 0–100% scale, active threshold and exact RH marker; shown in exactly one component |
| Wind | Direction vector rotates to the meteorological **from** direction within 72 fine radial ticks; mean and gust remain in the same group |
| Pressure | Numeric hPa and signed three-hour change inside a 960–1040 hPa semicircle; marker uses the actual pressure, without an invented history |
| Dew spread | Sampled spread curve with a hatched area and 2°C guide, sharing one component with spread and dew point; Fahrenheit differences use scale conversion without an offset |
| Temperature | Selected-window low/high bounds with the initial reading marked within the range |
| Visibility | Name, value and km only; distance-gate visualization removed |
| Cloud layers | Three short horizontal coverage fills on a common 0–100% scale; neither height nor thickness is inferred |
| Solar elevation | A signed elevation ray relative to the horizon, −90/0/+90 calibration, and a selected-window trajectory labelled with start/end angles |
| Solar azimuth | Numeric bearing, cardinal direction and moving angular ticks around the current bearing, including the nearest cardinal calibration |
| Lunar illumination | Exact circular SVG limb, elliptical phase terminator and native radial shadows; lunar age appears on the roomy tablet card. Shading is illustrative, with no generated image or surface map |
| Reference CCT | Approximate palette value and marker over a 2000–10000 K colour reference; not a measured camera white balance |
| Glow | Uncalibrated 0–100 condition score encoded by diagonal segments; never labelled probability |
| PM2.5 / feels-like | Short name/value/unit strips. PM2.5 additionally has a 0–100 µg/m³ fine barcode reference; this is raw concentration, not AQI or a health classification. Feels-like remains scalar. |

## Additional physics and source boundaries

`instrument-physics.js` keeps the new illustrative soundings independent from surface relative humidity. All PWV, upper wind, freezing altitude, CAPE/CIN, soil moisture, surface temperature and rainfall-rate values are **demonstration fixtures**, not downloaded forecasts. Their shapes and units are ready for later adapter work; no live integration is claimed.

| New family | Meaning and encoding |
| --- | --- |
| Light countdown | Selected photographic crossing or next golden/blue crossing, relative to the fixture clock; ticks while this view is open. A later date counts from the original scenario time, not from itself. Missing crossings stay unavailable. |
| Lunar context | Mean-lunation phase remains approximate; rise/set use the low-precision SunCalc lunar position formula and a numerical crossing search within the local civil day. Both times are marked approximate. Bortle remains unmeasured. |
| Upper wind | Illustrative 300-hPa wind, meteorological from direction, and vector difference from the surface wind. Arrows point **toward** drift; their lengths compare wind magnitudes. This is a bulk vector difference, not a vertical derivative or aviation hazard classification. |
| PWV | Independent full-column water in mm in a vertical graduated 0–70 mm column. Never inferred from VPD, never labelled a 0–10 km measurement or calibrated transparency percentage. |
| Freezing level | Illustrative altitude in metres above sea level. It is not a snow line, cloud base or local icing prediction. |
| CAPE / CIN | Illustrative available/inhibiting energy in J/kg. No thunderstorm probability or severity label is inferred from one parameter. |
| Soil water | A 40-cell density matrix represents the illustrative volumetric fraction in the **0–1 cm layer**, not two metres. It is not a site-specific dew/ground-slip prediction. |
| Direct / diffuse radiation | Direct-horizontal = DNI × max(0, sin solar elevation), compared with diffuse-horizontal. Numeric values retain W/m². Direct and diffuse ray weights and the split rail encode their relative contributions, not absolute flux. The common-plane ratio is absent when diffuse radiation is zero; at night there are no incoming energy paths or filled share rail. |
| Surface contrast | Surface minus 2-m air temperature in °C, a thermal proxy only. It is not a refractive index, Cn², seeing forecast or a measured heat-haze severity category. |
| Short rain | Illustrative rate in mm/h at nine 15-minute steps over two hours; not minute radar, not a separate thunderstorm probability. |
| Two-hour accumulation | Trapezoidal integration of the nine rainfall-rate samples across eight 0.25-hour intervals, displayed in a separate 48px strip below the histogram. Rate and integrated amount are distinct quantities. |
| VPD | Near-surface saturation vapour pressure deficit in kPa, using the FAO saturation relation and RH. It stays separate from PWV. |
| LCL | Approximate 125 × (T − Td) metres above ground, not a cloud-base observation. |
| Wet bulb | Stull approximation, bounded by dew point and air temperature; outside −20…50°C / 5…99% RH it is unavailable, with saturated air handled as Tw=T. Not WBGT or a health-risk classification. |
| Temperature tendency | The selected illustrative window's average temperature change per hour. Fahrenheit changes use scaling without the absolute-temperature offset. |

Definitions were checked against [Open-Meteo documentation](https://open-meteo.com/en/docs) and its [official variable schema](https://github.com/open-meteo/open-meteo/blob/main/openapi/forecast.yml). VPD is not precipitable water; the schema lists `total_column_integrated_water_vapour`. Radiation must compare the same plane. Regional `minutely_15` fields are not global minute radar. The [NWS CAPE glossary](https://www.weather.gov/spotterguide/glossary_c) explains why instability alone does not ensure thunderstorm initiation. [NPS sky-brightness metrics](https://www.nps.gov/subjects/nightskies/NSQmetrics.html) require site observations for Bortle. Surface approximations use [Stull's water-vapour chapter](https://www.eoas.ubc.ca/books/Practical_Meteorology/prmet102/Ch04-watervapor-v102b.pdf) and [FAO-56 meteorological definitions](https://www.fao.org/4/x0490e/x0490e07.htm). The lunar formula's [SunCalc 1.9.0 source](https://github.com/mourner/suncalc/tree/v1.9.0) is attributed with its BSD notice in `dist/licenses/`; the self-contained gallery embeds that notice.

## Rendering and typography

`instrument-art.js` draws eleven independent native pixel scenes in WebGL: an island station, rain greenhouse, coastal storm bunker, alpine cabin, radio telescope, open sunset pavilion, valley shelter, solar field, overcast turbines, forest lookout and highland windsock. Architecture uses distinct frontal elevations, material shading, framing and ground contact. Low-mode clouds retain their early analytic silhouette and soft vertical lighting; fine mode uses the weather page's lobe/volume principles with more material detail. There are no generated-image assets, texture samplers or image-loading paths.

The same `resolvePixelGrid` utility serves both pages. Observation requests 192 basic columns; the weather page's 96-column default is unchanged. Fine observation mode samples at twice the native density on each axis, capped at 1536 columns and both GPU dimensions. Basic observation mode and both weather-page modes keep their existing grids. Basic mode widens equipment strokes and simplifies small window/vent details. Ordered pixel ramps, depth layers and independent wind/rain/snow/star/water timing are shader-generated. The two programs share one canvas/context per document; the original weather program and framebuffer are restored on return. Observation targets twelve updates per second and respects pause/reduced-motion. Browser inspection does not establish Android performance. The simpler Canvas fallback remains less detailed and was not visually requalified as equivalent to the GPU scenes.

The page has separate scoped tokens: Space Grotesk for geometry and headings, Rajdhani for thin instrument digits, IBM Plex Mono for body/terminal metadata, and a renamed Noto Sans SC subset for Chinese. They interpret the reference typography; the original typefaces in the supplied pictures are not identified. Official original font bytes, OFL files, source URLs and hashes are retained. The optional fontTools script regenerates the renamed Chinese subset without modifying the upstream font.

## Data and scientific limits

All meteorological profiles and their time evolution are deterministic demonstration fixtures. No live forecast, radar, precise-location request or calibrated probability service is connected. A nearby future window is not a new weather forecast. The panel labels demo data and illustrative trends accordingly.

Solar position is an approximate geometric calculation from time and the sample city's coordinates. Local wall times use the scenario timezone, including DST. Sunrise/sunset uses a level-horizon -0.833° crossing; civil, nautical and astronomical twilight use -6°, -12° and -18°. Photographic windows use selected geometric boundaries: sunset 6° to -6°, dawn -6° to 6°, and evening blue hour -4° to -6°. Missing polar crossings remain unavailable. These are viewing conventions, not precise terrain-aware promises. See [USNO definitions](https://aa.usno.navy.mil/faq/RST_defs) and [solar approximation](https://aa.usno.navy.mil/faq/sun_approx).

Glow is an uncalibrated condition score out of 100, explicitly not a probability. CCT is a palette impression, not measured light temperature. Lunar illumination uses a mean 29.530588853-day lunation approximation, not a precise lunar ephemeris; actual lunations vary, as explained in [NASA's phase notes](https://eclipse.gsfc.nasa.gov/phase/phases2001.html). Illumination alone does not establish moonlight interference at a site.

Cloud percentages describe separate sample layers; diagrams do not establish actual cloud thickness, height-resolved wind or beam optics. Future Open-Meteo integration must follow its [actual variable definitions](https://open-meteo.com/en/docs), including low clouds below 3 km, mid clouds 3–8 km and high clouds above 8 km. Its regional 15-minute products must not be labelled minute radar. Bortle class, DEM obstruction, historical rarity and actual camera exposure remain unavailable. PWV is now shown only as an explicitly illustrative sounding. See [NPS light-pollution context](https://www.nps.gov/subjects/nightskies/lightpollution.htm).

## Verification

Pure checks cover equinox geometry, polar crossings, timezone/DST conversion, lunar-cycle limits, dew-point and wind rules, 66 deterministic sky samples, 264 planning combinations and another 264 information-ownership/graphic-mapping combinations. Browser inspection covers all eleven sample environments in Chinese and English at the gallery's 1440×960 frame size, real dial dragging and keyboard selection, dedicated WebGL2 rendering, and page handoff. Additional responsive and lifecycle results belong in implementation status only after they are actually observed.

## Compact footer and environment interpretation — 2026-10-23

PWV and soil no longer absorb the full tall-window surplus. During the initial compaction, the 1629×1193 reference changed from 276+311px to 137+156px, a 50% combined reduction; the later text-footer allocation tightened them further. In shorter viewports the minimum readable rows take precedence over the exact percentage. Radiation is 150px instead of the previous 346px tall-window card. Landscape bounds chart growth; the dedicated 2:3 portrait layout uses the full screen instead of centring a desktop-height chassis in a tall viewport.

The footer places the purpose-dependent wet-bulb/VPD/trend value in the first lane and one interpretation across lanes 2–4, at the same height. The text now contains a short condition summary plus relevant explanation, with an 84-character Chinese / 180-character English reading budget, flowing into 2–4 lines as space requires. The latest request explicitly calls for more content and larger type. Narrow phone fallback may flow onto additional lines. It interprets the selected time window's surface comfort, rain/snow, gusts, visibility and moisture conditions. It does not repeat imperative advice or invent a CAPE-derived storm probability. Source checks cover all 264 environment/purpose/language combinations and explicit day/night, heat, cold, snow, dew and wind/visibility cases.

Place/date and the time window share one font-size token. Light-stage labels and start/end hints are larger. CIN/CAPE show small signed/positive energy scales on wide screens; compact screens retain the readable values and method labels. Lunar content remains 128px wide on compact layouts and 150px in the roomy 2:3 tablet layout, with times grouped beside it; the upper-wind area is shorter.

## 3.4K tablet proportion

The geometric target is 2304×3456 (2:3), with 3456×2304 landscape retained. Browser checks use 1152×1728, 1024×1536 and 922×1383 CSS frames to cover effective density differences; they do not assert a measured Xiaomi device DPR. Portrait has a panorama/primary/energy row, a compact scalar/rain row and two columns of precisely allocated instruments. Pure values such as visibility use short strips. Primary temperature places its calibrated low/high range below the value; constant ranges use a centred marker. Humidity uses the full available field instead of an empty circle.


## Grid and vector Moon follow-up — 2026-10-23

The equal-height footer uses a shared title row and 12px inline inset, with the microenvironment method beneath its value. Its interpretation has an explicit 18px radius unaffected by the generic cell rule. The existing component is expanded rather than adding another card that repeats a metric. Landscape pressure is 176–204px while the dew panel contracts; the 2:3 grid uses 48 fractional tracks to transfer half-row increments without offsets. The 1152×1728 reference allocates approximately 194px to pressure and 150px to dew, compared with 133px and 226px previously.

The Moon is a native SVG circle with an exact elliptical phase terminator and radial light/shadow gradients. Etched meridians and cycle ticks were removed. The phase geometry is still an approximate mean lunation, and the shading is illustrative. The Bortle placeholder remains unmeasured in a separate header row; measured visible separation from the disc is at least 16px in the responsive checks. Compact portrait pressure separates left readings and right gauge, and the solar trajectory has its own space below the angle reading.
