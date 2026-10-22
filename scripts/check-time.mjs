import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import { WEATHER_PRESETS, resolveTimeContext } from '../dist/weather-art.js';
import { resolveWeatherIcon } from '../dist/weather-icons.js';
import { airQualityStatus, adaptAirQuality, adaptOpenMeteo, buildAirQualityUrl } from '../dist/open-meteo.js';

const w=WEATHER_PRESETS.partly;
for(const [now,event,progress] of [[w.sunriseAt-1,'sunrise',null],[w.sunriseAt,'sunset',0],[w.sunsetAt-1,'sunset',null],[w.sunsetAt,'sunrise',0],[w.sunsetAt+1,'sunrise',null]]){
  const state=resolveTimeContext({...w,validAt:now,isDay:now>=w.sunriseAt&&now<w.sunsetAt});
  assert.equal(state.event,event);assert(state.eventAt>now);
  if(progress!==null)assert.equal(state.progress,progress);else assert(state.progress>=0&&state.progress<=1);
}
const midnight=resolveTimeContext(WEATHER_PRESETS.night);
assert.equal(midnight.eventAt,WEATHER_PRESETS.night.nextSunriseAt);
assert.equal(midnight.clock24,'23:10');
assert.equal(resolveTimeContext(WEATHER_PRESETS.dawn).event,'sunset','after sunrise must show sunset, even during dawn artwork');
const fog=resolveTimeContext(WEATHER_PRESETS.fog);
assert.equal(fog.eventAt,WEATHER_PRESETS.fog.sunriseAt);
assert.equal(fog.clock24,'07:10');assert(fog.progress>.9);
const polar=resolveTimeContext({validAt:w.validAt,timezone:w.timezone,isDay:true});
assert.equal(polar.event,null);assert.equal(polar.progress,null);
const all=Object.values(WEATHER_PRESETS);
assert.equal(new Set(all.map(v=>v.at)).size,11);
assert(new Set(all.map(resolveWeatherIcon)).size>=9);
for(const sample of all){assert(sample.temperatureMin<=sample.temperature);assert(sample.temperatureMax>=sample.temperature);assert(resolveTimeContext(sample).eventAt>sample.validAt);}
for(const [value,tier] of [[0,0],[50,0],[51,1],[100,1],[101,2],[150,2],[151,3],[200,3],[201,4],[300,4],[301,5],[600,5]]){
  const status=airQualityStatus({standard:'US AQI',value});assert.equal(status.tier,tier);assert.equal(status.warning,tier>=2);
}
assert.equal(airQualityStatus({standard:'EU AQI',value:90}),null);
assert.equal(airQualityStatus(null),null);assert.equal(airQualityStatus({standard:'US AQI',value:-1}),null);
const aqPayload={timezone:w.timezone,current_units:{time:'unixtime',us_aqi:'USAQI'},current:{time:w.validAt,us_aqi:154}};
assert.equal(adaptAirQuality(aqPayload,w).value,154);
assert.equal(adaptAirQuality({...aqPayload,current:{time:w.validAt-3601,us_aqi:154}},w),null);
assert.equal(adaptAirQuality({...aqPayload,timezone:'Europe/London'},w),null);
assert.equal(new URL(buildAirQualityUrl({latitude:31.23,longitude:121.47})).searchParams.get('current'),'us_aqi');
// A deliberate three-day API fixture verifies date indexing rather than array position.
const previous=w.sunriseAt-86400, current=w.sunriseAt, next=w.nextSunriseAt;
const payload={timezone:w.timezone,current_units:{time:'unixtime'},current:{time:w.validAt},daily_units:{time:'unixtime',sunrise:'unixtime',sunset:'unixtime',temperature_2m_max:'°C',temperature_2m_min:'°C'},daily:{time:[previous,current,next],sunrise:[previous,current,next],sunset:[w.previousSunsetAt,w.sunsetAt,w.sunsetAt+86400],temperature_2m_max:[25,26,27],temperature_2m_min:[17,18,19]}};
const adapted=adaptOpenMeteo(payload);assert.equal(adapted.sunriseAt,w.sunriseAt);assert.equal(adapted.nextSunriseAt,w.nextSunriseAt);assert.equal(adapted.previousSunsetAt,w.previousSunsetAt);assert.equal(adapted.temperatureMax,26);assert.equal(adapted.temperatureMin,18);
console.log('Verified solar boundaries, next-day sunrise, DST dawn, polar nulls, 11 distinct scenarios, temperature ranges and all US AQI thresholds/staleness.');

const realForecast=JSON.parse(await fs.readFile(new URL('../verification/open-meteo-v6-forecast.json',import.meta.url),'utf8'));
const realAir=JSON.parse(await fs.readFile(new URL('../verification/open-meteo-v6-air.json',import.meta.url),'utf8'));
const realWeather=adaptOpenMeteo(realForecast);
for(const field of ['sunriseAt','sunsetAt','previousSunsetAt','nextSunriseAt','temperatureMin','temperatureMax'])assert(Number.isFinite(realWeather[field]),field+' absent in recorded official response');
assert(adaptAirQuality(realAir,realWeather));
console.log('Verified recorded v6 official Forecast and Air Quality responses through the new adapters.');
