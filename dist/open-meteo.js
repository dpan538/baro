import { WEATHER_CODE_GROUPS } from './weather-art.js';

// A verified data contract; this module does not fetch or enable network access.
export const CURRENT_FIELDS = Object.freeze([
  'temperature_2m', 'apparent_temperature', 'relative_humidity_2m',
  'wind_speed_10m', 'uv_index', 'visibility', 'cloud_cover', 'weather_code', 'is_day'
]);

export function buildForecastUrl({ latitude, longitude }) {
  if (!Number.isFinite(latitude) || Math.abs(latitude) > 90 ||
      !Number.isFinite(longitude) || Math.abs(longitude) > 180) {
    throw new RangeError('Invalid weather coordinates');
  }
  const url = new URL('https://api.open-meteo.com/v1/forecast');
  url.search = new URLSearchParams({
    latitude, longitude, current: CURRENT_FIELDS.join(','),
    hourly: 'precipitation_probability', daily: 'sunrise,sunset,temperature_2m_max,temperature_2m_min', forecast_days: 2, past_days: 1,
    timezone: 'auto', timeformat: 'unixtime',
    temperature_unit: 'celsius', wind_speed_unit: 'kmh'
  });
  return url.href;
}

const finite = value => typeof value === 'number' && Number.isFinite(value);
const bounded = (value, low, high = Infinity) => finite(value) && value >= low && value <= high ? value : null;
const percent = value => bounded(value, 0, 100);
const parts = (seconds, timezone, options) => Object.fromEntries(
  new Intl.DateTimeFormat('en-US', { timeZone: timezone, ...options })
    .formatToParts(new Date(seconds * 1000)).map(part => [part.type, part.value])
);
const dateAt = (seconds, timezone) => {
  const { year, month, day } = parts(seconds, timezone, { year: 'numeric', month: '2-digit', day: '2-digit' });
  return `${year}-${month}-${day}`;
};
const hourAt = (seconds, timezone) => parts(seconds, timezone, { hour: '2-digit', hourCycle: 'h23' }).hour;

export function uvCategory(value) {
  if (bounded(value, 0) === null) return null;
  const displayed = Math.round(value);
  return displayed < 3 ? 'Low' : displayed < 6 ? 'Moderate' : displayed < 8 ? 'High' : displayed < 11 ? 'Very high' : 'Extreme';
}

function conditionFor(code, isDay) {
  if (code === 0 && isDay === false) return 'Clear night';
  return {
    0: 'Clear skies', 1: 'Mainly clear', 2: 'Partly cloudy', 3: 'Overcast',
    45: 'Fog', 48: 'Rime fog',
    51: 'Light drizzle', 53: 'Drizzle', 55: 'Dense drizzle',
    56: 'Freezing drizzle', 57: 'Dense freezing drizzle',
    61: 'Light rain', 63: 'Rain', 65: 'Heavy rain', 66: 'Freezing rain', 67: 'Heavy freezing rain',
    71: 'Light snow', 73: 'Snow', 75: 'Heavy snow', 77: 'Snow grains',
    80: 'Light showers', 81: 'Showers', 82: 'Heavy showers', 85: 'Snow showers', 86: 'Heavy snow showers',
    95: 'Thunderstorm', 96: 'Storm / slight hail', 97: 'Heavy storm', 99: 'Storm / heavy hail'
  }[code] ?? 'Weather unavailable';
}

export function adaptOpenMeteo(payload) {
  if (payload?.error || !finite(payload?.current?.time) || payload?.current_units?.time !== 'unixtime') {
    throw new TypeError('Open-Meteo response needs current data and Unix timestamps');
  }
  const timezone = payload.timezone;
  // An invalid or absent location timezone must not silently become the device timezone.
  if (typeof timezone !== 'string') throw new TypeError('Missing location timezone');
  const current = payload.current;
  const validAt = current.time;
  const localDate = dateAt(validAt, timezone);
  const read = (key, unit) => payload.current_units[key] === unit && finite(current[key]) ? current[key] : null;
  const temperature = read('temperature_2m', '°C');
  const feelsLike = read('apparent_temperature', '°C');
  const windSpeed = bounded(read('wind_speed_10m', 'km/h'), 0);
  const humidity = percent(read('relative_humidity_2m', '%'));
  const cloudCover = percent(read('cloud_cover', '%'));
  const uvIndex = bounded(read('uv_index', ''), 0);
  const visibilityMetres = bounded(read('visibility', 'm'), 0);
  const rawCode = read('weather_code', 'wmo code');
  const code = Object.values(WEATHER_CODE_GROUPS).some(codes => codes.includes(rawCode)) ? rawCode : null;
  const rawDay = read('is_day', '');
  const isDay = rawDay === 1 ? true : rawDay === 0 ? false : null;

  // Each probability describes the hour ENDING at its timestamp. Display that
  // explicit interval, not a daily chance or a claimed rolling next-60-minutes value.
  const hourly = payload.hourly;
  const hourlyIndex = payload.hourly_units?.time === 'unixtime' && Array.isArray(hourly?.time)
    ? hourly.time.findIndex(end => finite(end) && end > validAt && end <= validAt + 3600) : -1;
  const precipitationProbability = hourlyIndex >= 0 && payload.hourly_units.precipitation_probability === '%'
    ? percent(hourly.precipitation_probability?.[hourlyIndex]) : null;
  const precipitationEnd = precipitationProbability === null ? null : hourly.time[hourlyIndex];
  const precipitationStart = precipitationEnd === null ? null : precipitationEnd - 3600;
  const precipitationWindow = precipitationEnd === null ? null
    : `${hourAt(precipitationStart, timezone)}–${hourAt(precipitationEnd, timezone)}h`;

  const daily = payload.daily;
  const dailyIndex = payload.daily_units?.time === 'unixtime' && Array.isArray(daily?.time)
    ? daily.time.findIndex(time => finite(time) && dateAt(time, timezone) === localDate) : -1;
  const rawSunset = dailyIndex >= 0 && payload.daily_units.sunset === 'unixtime' ? daily.sunset?.[dailyIndex] : null;
  const sunsetAt = finite(rawSunset) && dateAt(rawSunset, timezone) === localDate ? rawSunset : null;
  const solar = (key, index) => {
    const value = index >= 0 && payload.daily_units[key] === 'unixtime' ? daily[key]?.[index] : null;
    return finite(value) && finite(daily.time[index]) && dateAt(value, timezone) === dateAt(daily.time[index], timezone) ? value : null;
  };
  const sunriseAt = solar('sunrise', dailyIndex);
  const nextSunriseAt = solar('sunrise', dailyIndex >= 0 ? dailyIndex + 1 : -1);
  const previousSunsetAt = solar('sunset', dailyIndex - 1);
  const dailyTemperature = key => dailyIndex >= 0 && payload.daily_units[key] === '°C' && finite(daily[key]?.[dailyIndex]) ? daily[key][dailyIndex] : null;
  const temperatureMax = dailyTemperature('temperature_2m_max'), temperatureMin = dailyTemperature('temperature_2m_min');
  const sunsetParts = sunsetAt === null ? null : parts(sunsetAt, timezone, { hour: 'numeric', minute: '2-digit', hour12: true });
  // Golden-hour artwork is a product rule, not an Open-Meteo weather code.
  const period = isDay === true && sunsetAt !== null && validAt >= sunsetAt - 1800 && validAt < sunsetAt ? 'sunset' : null;

  return {
    temperature, temperatureMax, temperatureMin, sunriseAt, nextSunriseAt, previousSunsetAt, feelsLike, windSpeed, humidity, uvIndex, uvLabel: uvCategory(uvIndex),
    cloudCover, visibility: visibilityMetres === null ? null : visibilityMetres / 1000,
    precipitationProbability, precipitationWindow, precipitationStart, precipitationEnd,
    sunset: sunsetParts ? `${sunsetParts.hour}:${sunsetParts.minute}` : null,
    sunsetPeriod: sunsetParts?.dayPeriod.toLowerCase() ?? null, sunsetAt,
    code, isDay, period, condition: conditionFor(code, isDay),
    validAt, interval: bounded(current.interval, 1), timezone, localDate
  };
}

export function formatScreenWeather(weather) {
  const number = (value, decimals = 0) => finite(value) ? Number(value.toFixed(decimals)).toString() : '—';
  return {
    ...weather,
    temperature: number(weather.temperature), temperatureMax: number(weather.temperatureMax), temperatureMin: number(weather.temperatureMin), feelsLike: number(weather.feelsLike),
    windSpeed: number(weather.windSpeed, weather.windUnit && weather.windUnit !== 'km/h' ? 1 : 0), humidity: number(weather.humidity),
    uvIndex: number(weather.uvIndex), uvLabel: weather.uvLabel ?? '',
    cloudCover: number(weather.cloudCover), visibility: number(weather.visibility, 1),
    precipitationProbability: number(weather.precipitationProbability),
    precipitationWindow: weather.precipitationWindow ?? '',
    sunset: weather.sunset ?? '—', sunsetPeriod: weather.sunsetPeriod ?? ''
  };
}

// Separate modelled air-quality endpoint. Missing, stale or differently zoned data
// never inherits a reassuring green status from a previous location.
export function buildAirQualityUrl(coordinates) {
  const forecast = new URL(buildForecastUrl(coordinates));
  const url = new URL('https://air-quality-api.open-meteo.com/v1/air-quality');
  url.search = new URLSearchParams({ latitude: forecast.searchParams.get('latitude'), longitude: forecast.searchParams.get('longitude'), current: 'us_aqi', timezone: 'auto', timeformat: 'unixtime', forecast_days: 1 });
  return url.href;
}
export function adaptAirQuality(payload, weather) {
  const value = payload?.current?.us_aqi, time = payload?.current?.time;
  if (payload?.error || payload?.current_units?.time !== 'unixtime' || payload?.current_units?.us_aqi !== 'USAQI' ||
      bounded(value, 0) === null || !finite(time) || !finite(weather?.validAt) ||
      payload.timezone !== weather.timezone || Math.abs(time - weather.validAt) > 3600) return null;
  return { value, validAt: time, timezone: payload.timezone, standard: 'US AQI' };
}
export function airQualityStatus(airQuality) {
  if (airQuality?.standard !== 'US AQI' || bounded(airQuality.value, 0) === null) return null;
  const value = Math.round(airQuality.value);
  const tier = value <= 50 ? 0 : value <= 100 ? 1 : value <= 150 ? 2 : value <= 200 ? 3 : value <= 300 ? 4 : 5;
  return { value, tier, warning: tier >= 2,
    zh: ['优', '中等', '敏感人群需注意', '不健康', '非常不健康', '危险'][tier],
    en: ['Good', 'Moderate', 'Sensitive groups', 'Unhealthy', 'Very unhealthy', 'Hazardous'][tier],
    color: ['#6e8b64','#b59d57','#bf853b','#bc624f','#896887','#8d4c62'][tier] };
}
