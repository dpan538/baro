const valid = value => typeof value === 'number' && Number.isFinite(value);
export const WIND_UNITS = Object.freeze({ kmh: { label: 'km/h', divisor: 1 }, ms: { label: 'm/s', divisor: 3.6 }, mph: { label: 'mph', divisor: 1.609344 }, kts: { label: 'kts', divisor: 1.852 } });
export function convertTemperature(value, unit = 'c') { return valid(value) ? unit === 'f' ? value * 9 / 5 + 32 : value : null; }
export function convertWindSpeed(value, unit = 'kmh') { return valid(value) ? value / (WIND_UNITS[unit]?.divisor ?? 1) : null; }
export function formatUnitWeather(weather, preferences = {}) {
  const temperatureUnit = preferences.temperatureUnit === 'f' ? 'f' : 'c';
  const windUnit = Object.hasOwn(WIND_UNITS, preferences.windUnit) ? preferences.windUnit : 'kmh';
  const result = { ...weather, temperatureUnit: temperatureUnit.toUpperCase(), windUnit: WIND_UNITS[windUnit].label };
  for(const key of ['temperature','feelsLike','temperatureMax','temperatureMin']) result[key] = convertTemperature(weather[key], temperatureUnit);
  result.windSpeed = convertWindSpeed(weather.windSpeed, windUnit);
  return result;
}
