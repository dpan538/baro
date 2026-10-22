import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import { adaptOpenMeteo, buildForecastUrl, formatScreenWeather } from '../dist/open-meteo.js';
import { resolveWeatherScene } from '../dist/weather-art.js';

const response = JSON.parse(await fs.readFile(new URL('../verification/open-meteo-response.json', import.meta.url), 'utf8'));
const request = new URL(buildForecastUrl({ latitude: -27.47, longitude: 153.03 }));
assert.equal(request.hostname, 'api.open-meteo.com');
assert.equal(request.searchParams.get('timeformat'), 'unixtime');
assert.equal(request.searchParams.get('timezone'), 'auto');
assert.equal(request.searchParams.get('daily'), 'sunrise,sunset,temperature_2m_max,temperature_2m_min');
assert.equal(request.searchParams.get('past_days'), '1');
assert.throws(() => buildForecastUrl({ latitude: 91, longitude: 0 }), RangeError);

const weather = adaptOpenMeteo(response);
assert.equal(weather.temperature, response.current.temperature_2m);
assert.equal(weather.visibility, response.current.visibility / 1000);
assert.equal(weather.uvIndex, response.current.uv_index);
assert.equal(weather.localDate, '2026-10-22');
assert.equal(weather.timezone, 'Australia/Brisbane');
assert.equal(weather.precipitationProbability, 90);
assert.equal(weather.precipitationWindow, '00–01h');
assert.equal(weather.sunset, '5:59');
assert.equal(weather.sunsetPeriod, 'pm');
assert.equal(resolveWeatherScene(weather), 'rain');
for (const [code, word] of [[66, 'Freezing'], [96, 'hail'], [99, 'hail']]) {
  const specialised = structuredClone(response);
  specialised.current.weather_code = code;
  assert.match(adaptOpenMeteo(specialised).condition, new RegExp(word));
}

// Move the clock into the last quarter of the day, then verify that the next
// hourly interval and the current location's date do not come from array index 0.
const midnight = structuredClone(response);
midnight.current.time = response.hourly.time[23] + 45 * 60;
const crossing = adaptOpenMeteo(midnight);
assert.equal(crossing.precipitationWindow, '23–00h');
assert.equal(crossing.precipitationProbability, response.hourly.precipitation_probability[24]);
assert.equal(crossing.localDate, '2026-10-22');
midnight.current.time = response.hourly.time[24] + 15 * 60;
assert.equal(adaptOpenMeteo(midnight).localDate, '2026-10-23');
assert.equal(adaptOpenMeteo(midnight).sunsetAt, response.daily.sunset[1]);

const golden = structuredClone(response);
golden.current.time = response.daily.sunset[0] - 900;
golden.current.is_day = 1;
golden.current.weather_code = 1;
golden.current.cloud_cover = 24;
assert.equal(adaptOpenMeteo(golden).period, 'sunset');
assert.equal(resolveWeatherScene(adaptOpenMeteo(golden)), 'sunset');

// Preserve nulls and zeros separately; units and missing polar sunset data must
// never produce fabricated normal readings or an event from another day.
const missing = structuredClone(response);
Object.assign(missing.current, { temperature_2m: null, uv_index: null, visibility: null, weather_code: null, relative_humidity_2m: 0 });
missing.current_units.wind_speed_10m = 'mph';
missing.daily.sunset[0] = null;
missing.hourly.precipitation_probability.fill(null);
const unavailable = adaptOpenMeteo(missing);
const screen = formatScreenWeather(unavailable);
assert.equal(screen.temperature, '—');
assert.equal(screen.humidity, '0');
assert.equal(screen.uvIndex, '—');
assert.equal(unavailable.windSpeed, null);
assert.equal(screen.visibility, '—');
assert.equal(screen.sunset, '—');
assert.equal(screen.sunsetPeriod, '');
assert.equal(screen.precipitationProbability, '—');
assert.equal(screen.precipitationWindow, '');
assert.equal(resolveWeatherScene(unavailable), 'unknown');
assert.throws(() => adaptOpenMeteo({ error: true, reason: 'Invalid variable' }), TypeError);

// Unix instants and IANA formatting preserve local clocks across a DST jump.
const dst = structuredClone(response);
dst.timezone = 'America/New_York';
dst.current.time = Date.parse('2026-03-08T06:15:00Z') / 1000;
dst.hourly = { time: [Date.parse('2026-03-08T07:00:00Z') / 1000], precipitation_probability: [0] };
dst.daily = { time: [], sunset: [] };
assert.equal(adaptOpenMeteo(dst).precipitationWindow, '01–03h');
assert.equal(adaptOpenMeteo(dst).precipitationProbability, 0);
console.log('Verified Open-Meteo response, units, WMO mapping, hourly intervals, location dates, DST, nulls and sunset selection.');
