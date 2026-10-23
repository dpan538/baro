# Native field inventory and truth boundaries

Checked against official Open-Meteo Forecast/Air Quality/Geocoding documentation on 2026-10-23. `backend/baro.py` fixes allowed upstream variables; `WeatherJson` checks units and finite/range-valid numbers. The native app uses the same canonical `Weather` for all readouts, derived values, graphs, interpretation and scene selection. The recorded live Brisbane response is a dated test fixture, not a live feed at test execution time.

| Page / field | Actual endpoint / field or derivation | Canonical unit / time / absence |
| --- | --- | --- |
| Place | `/v1/places` → Open-Meteo geocoding (GeoNames); native foreground location | Coordinates are request identity. Location denial/off/timeout retains the selected place; default Brisbane is a labelled place, never a fabricated GPS fix. |
| Date and clock | Forecast `timezone`, `current.time` | Unix seconds → IANA zone. Device zone is not used to interpret provider hours. |
| Current air temperature | Forecast `current.temperature_2m` | °C, model estimate; `—` for null/wrong units. |
| Feels like | `current.apparent_temperature` | °C; no substitution from air temperature. |
| High / low | `daily.temperature_2m_max/min` for the local date | °C. Missing date or value stays absent. |
| Wind / gust / from | `current.wind_speed_10m`, `wind_gusts_10m`, `wind_direction_10m`; hourly at selected window | km/h, degrees meteorological FROM. Drift arrows use +180°. |
| UV | `current.uv_index` | Current model UV, unitless; not the daily maximum. |
| RH | `current.relative_humidity_2m`, hourly RH for windows | %. One observation owner; humidity primary replaces auxiliary humidity with precipitation. |
| Precipitation chance | `hourly.precipitation_probability`, next hourly ending timestamp | %. Explicit preceding-hour interval. Probability does not determine current rainfall or animation. |
| Visibility | `current.visibility`, hourly for a window | m → displayed km. No 10-km cap or fake range chart. |
| Cloud cover | `current.cloud_cover` | %. Meter fill uses actual amount; colour is illustrative. |
| Solar event | `daily.sunrise/sunset` on matching local days | First future crossing, including next day; no fixed 12-hour denominator. Polar absence stays `—`. |
| US AQI | Air Quality `current.us_aqi`, unit `USAQI` | Separate model feed. Requires same timezone and timestamp within one hour; stale/failed air is absent. |
| Weather text / pixel scene | `current.weather_code`, `is_day`, cloud/wind and local solar events | WMO weather groups, with documented wind and dawn/sunset visual thresholds. Unknown codes use the neutral weather scene. Never a radar image. |
| Pressure and 3h change | `pressure_msl`; actual hourly sample difference | hPa, and hPa/3h. Absent past sample → no trend. |
| Dew / spread | `hourly.dew_point_2m`; if unavailable, Magnus approximation from T/RH | °C, spread = T−Td. F differences scale by 1.8 without +32. Sampled graphs use only actual hourly T/Td, without interpolation across missing points. |
| Cloud layers | `hourly.cloud_cover_low/mid/high` | %, provider height bands; no inferred cloud-base height. |
| PWV | `hourly.total_column_integrated_water_vapour` | kg/m², water-equivalent mm; full atmospheric column, never inferred from RH or VPD. |
| Surface soil water | `hourly.soil_moisture_0_to_1cm` | m³/m³ in 0–1 cm. Not a site-specific moisture measurement. |
| Freezing level | `hourly.freezing_level_height` | m above sea level. Not a snow line or an icing hazard prediction. |
| CAPE / CIN | `hourly.cape`, `convective_inhibition` | J/kg. No thunderstorm probability is inferred from these scalars. |
| LCL ≈ | max(0, 125 × (T−Td)) | m above ground; surface approximation, not observed cloud base. |
| Upper wind / bulk difference | `hourly.wind_speed_300hPa`, `wind_direction_300hPa`; vector difference from surface wind | km/h at 300 hPa, no invented sounding. Δv is a vector magnitude, not a vertical derivative or aviation classification. |
| Solar elevation / bearing ≈ | Ported existing geometric solar calculation from epoch/coordinates | degrees. No DEM, terrain obstruction or measured irradiance implied. |
| Direct / diffuse | `hourly.direct_radiation_instant`, `diffuse_radiation_instant` | W/m², both horizontal instantaneous model values. No extra DNI conversion. Zero flux has no incoming rays or fabricated ratio. |
| Surface contrast | `soil_temperature_0cm` minus `temperature_2m` | °C; model soil-surface thermal proxy. Not a measured equipment/skin temperature, refractive index or seeing severity. |
| Short precipitation | `hourly.precipitation` | Preceding-hour amount divided by one hour, displayed mm/h. **Hourly model, not 15-minute radar**; the native app does not invent nine subhour samples. |
| 2h precipitation | Sum of two complete available future hourly-ending amounts | mm, distinct from rate. Requires both actual samples. Labels describe model windows, not a rolling radar integral. |
| Moon illumination / age ≈ | Existing mean 29.530588853-day lunation model | % / days. Flat 2D terminator, illustrative, not precise ephemeris. |
| Moon rise/set ≈ | SunCalc low-precision lunar altitude, numerical local-day crossing search | IANA civil day with 23/24/25-hour DST boundaries; missing polar crossing stays `—`. BSD notice bundled. |
| Bortle | No measurement/source | Always `—`; no inferred class from weather or city. |
| Photographic window / countdown ≈ | Geometric solar elevation crossings, up to two following days | Dawn −6°→+6°, sunset +6°→−6°, evening blue −4°→−6°. Cross-day `+1`; no crossing stays unavailable. Forecast samples outside coverage stay `—`. |
| Glow score | Existing bounded cloud/visibility/RH heuristic | 0–100, explicitly not probability or a measured light product. Missing input → `—`. |
| CCT | No spectral measurement / supported provider field | Always `—`, with an explicit absence hint. No phase-to-kelvin lookup table is presented as data. |
| Wet bulb ≈ | Stull approximation, bounded between dew and air temperature | °C; valid −20…50°C / 5…99% RH, saturated air handled separately. Not WBGT or medical risk. |
| VPD | FAO saturation relation × (1−RH/100) | kPa, near-surface approximation; distinct from PWV. |
| Temperature tendency | Actual hourly endpoint temperature difference / duration | °C/h or scaled °F/h; no synthetic oscillator. |
| Interpretation | Purpose-specific rules over the same selected samples | Missing data is explicit. Storage discusses item surfaces and moisture; home discusses drying, without pretending indoor humidity/equipment temperatures were measured. |

All upstream weather and air fields are **model estimates**, not on-site observations. Current and hourly semantics remain distinct. UI values do not overwrite canonical units. Surface/optical approximations use the existing documented formulas. The shipping app has no demo payload, scene picker or sample-data mode. Frozen prototype examples remain historical design references only; production layout assets contain geometry/styles and no sample text. Native product screenshots require a successful live API response.

Client cache is keyed by coordinates, capped at 12 places, fresh for 30 minutes and recoverable up to 24 hours with original timestamps. Each request has a generation guard; cancellation on place/lifecycle change prevents old responses from updating another place or repopulating a cleared cache. Foreground refresh uses bounded retry and timeouts. No background polling or background location is requested.

Official sources: [Forecast definitions](https://open-meteo.com/en/docs), [Air Quality](https://open-meteo.com/en/docs/air-quality-api), [Geocoding](https://open-meteo.com/en/docs/geocoding-api), [pricing/licensing](https://open-meteo.com/en/pricing), [SunCalc source](https://github.com/mourner/suncalc/tree/v1.9.0), [USNO solar definitions](https://aa.usno.navy.mil/faq/RST_defs). Original source notices and font licenses are bundled in the APK.
