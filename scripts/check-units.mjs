import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import { convertTemperature, convertWindSpeed, formatUnitWeather, WIND_UNITS } from '../dist/weather-units.js';
import { formatScreenWeather } from '../dist/open-meteo.js';
import { WEATHER_PRESETS } from '../dist/weather-art.js';
import { APP_META } from '../dist/app-meta.js';
import { settingText, formatBytes } from '../dist/settings.js';
import { deviceServices } from '../dist/device-services.js';
assert.equal(convertTemperature(0,'f'),32);assert.equal(convertTemperature(-40,'f'),-40);assert.equal(convertTemperature(100,'f'),212);
assert.equal(convertWindSpeed(36,'ms'),10);assert.equal(convertWindSpeed(1.852,'kts'),1);assert.equal(convertWindSpeed(1.609344,'mph'),1);
for(const invalid of [null,undefined,NaN,Infinity,'0']){assert.equal(convertTemperature(invalid,'f'),null);assert.equal(convertWindSpeed(invalid,'ms'),null);}
for(const [key,weather] of Object.entries(WEATHER_PRESETS))for(const temperatureUnit of ['c','f'])for(const windUnit of Object.keys(WIND_UNITS)){
 const before=JSON.stringify(weather),converted=formatUnitWeather(weather,{temperatureUnit,windUnit});
 assert.equal(JSON.stringify(weather),before,'Canonical weather mutated: '+key);
 for(const field of ['temperature','feelsLike','temperatureMax','temperatureMin'])assert.equal(converted[field],convertTemperature(weather[field],temperatureUnit));
 assert.equal(converted.windSpeed,convertWindSpeed(weather.windSpeed,windUnit));assert.equal(converted.windUnit,WIND_UNITS[windUnit].label);
 const display=formatScreenWeather(converted);assert(!display.temperature.includes('NaN'));assert(!display.windSpeed.includes('NaN'));
}
assert.equal(formatScreenWeather(formatUnitWeather(WEATHER_PRESETS.storm,{temperatureUnit:'f',windUnit:'ms'})).temperature,'81');
assert.equal(formatScreenWeather(formatUnitWeather(WEATHER_PRESETS.storm,{windUnit:'ms'})).windSpeed,'9.2');
assert.equal(formatUnitWeather({temperature:null,windSpeed:null},{temperatureUnit:'f'}).temperature,null);
assert.equal(formatBytes(0),'0 B');assert.equal(formatBytes(1536),'1.5 KB');
const html=await fs.readFile(new URL('../dist/index.html',import.meta.url),'utf8');
for(const key of [...html.matchAll(/data-copy="([^"]+)"/g)].map(match=>match[1]))for(const language of ['zh','en'])assert(settingText(key,language));
const gradle=await fs.readFile(new URL('../android/app/build.gradle.kts',import.meta.url),'utf8');assert(gradle.includes(`versionCode = ${APP_META.versionCode}`));assert(gradle.includes(`versionName = "${APP_META.version}"`));
assert.equal(APP_META.name,'BARO');
assert.equal(APP_META.nameZh,'晴雨');
assert.equal(APP_META.updateUrl,'https://github.com/dpan538/baro/releases');
assert.equal(APP_META.feedbackUrl,'https://github.com/dpan538/baro/issues/new/choose');
// Cache operations only touch app-owned data, preserve another application's cache.
const storage=new Map([['sundown-weather-v1',new Map([['forecast',new Response('weather')]])],['sundown-maps-v1',new Map([['tile',new Response('map')]])],['unrelated-cache',new Map([['keep',new Response('keep')]])]]);
globalThis.window={caches:true};globalThis.caches={keys:async()=>[...storage.keys()],open:async name=>({keys:async()=>[...storage.get(name).keys()],match:async key=>storage.get(name).get(key).clone()}),delete:async name=>storage.delete(name)};
assert.deepEqual(await deviceServices.cache(),{weather:7,maps:3,total:10});assert.deepEqual(await deviceServices.clearCache(),{weather:0,maps:0,total:0});assert(storage.has('unrelated-cache'));assert.deepEqual(deviceServices.setLocation(true),{enabled:false,status:'preview'});
let openedUrl=null;window.open=url=>{openedUrl=url;return null;};
assert.equal(deviceServices.external(APP_META.updateUrl),true);assert.equal(openedUrl,APP_META.updateUrl);assert.equal(deviceServices.external('javascript:alert(1)'),false);
console.log('Verified 88 fixture/unit combinations, temperature extremes, null data, exact wind factors, settings translations, version consistency, owned-cache accounting/clearing and browser location fallback.');
