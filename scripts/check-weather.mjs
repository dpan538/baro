import assert from 'node:assert/strict';
import { createWeatherFrame, resolveWeatherScene, WEATHER_PRESETS, WEATHER_CODE_GROUPS, MOTION } from '../dist/weather-art.js';

const names=Object.keys(WEATHER_PRESETS);
for(const name of names) {
  const weather=WEATHER_PRESETS[name];
  assert.equal(resolveWeatherScene(weather),name,`${name}: weather and illustration must agree`);
  for(const rows of [18,24,32]) {
    const first=createWeatherFrame(weather,rows,0);
    assert.equal(first.pixels.length,Math.ceil(rows)*MOTION.columns);
    assert(first.pixels.every(color=>/^#[0-9a-f]{6}$/i.test(color)),`${name}: invalid pixel colour`);
    assert.deepEqual(first.pixels,createWeatherFrame(weather,rows,MOTION.frames).pixels,`${name}: loop is not periodic`);
    assert.notDeepEqual(first.pixels,createWeatherFrame(weather,rows,24).pixels,`${name}: scene does not animate`);
  }
}
assert.equal(resolveWeatherScene({...WEATHER_PRESETS.rain,isDay:false}),'rain','precipitation must take priority over the clear-night scene');
assert.equal(resolveWeatherScene({...WEATHER_PRESETS.snow,windSpeed:40}),'snow','strong wind must not replace snow with a clear-sky illustration');
assert.equal(resolveWeatherScene({...WEATHER_PRESETS.partly,isDay:false}),'night');
assert.equal(resolveWeatherScene({...WEATHER_PRESETS.overcast,isDay:false}),'overcast','night-time overcast must keep its cloud scene');
assert.notDeepEqual(createWeatherFrame(WEATHER_PRESETS.overcast,32,0).pixels,createWeatherFrame({...WEATHER_PRESETS.overcast,isDay:false},32,0).pixels,'cloud scenes must also use the night palette');
for (const code of [null,undefined,NaN,4,98,100,'0']) {
  assert.equal(resolveWeatherScene({...WEATHER_PRESETS.clear,code}),'unknown','missing or invalid weather must not become clear skies');
}
for (const [group,codes] of Object.entries(WEATHER_CODE_GROUPS)) {
  for (const code of codes) assert.equal(resolveWeatherScene({code,isDay:true,cloudCover:0,windSpeed:0}),group);
}
assert.equal(resolveWeatherScene({...WEATHER_PRESETS.storm,period:'sunset',isDay:false}),'storm');
assert.equal(resolveWeatherScene({...WEATHER_PRESETS.overcast,windSpeed:40,period:'sunset'}),'overcast');
console.log(`Verified ${names.length} weather states at three panel shapes: weather mapping, valid pixels, motion and eight-second loops.`);
