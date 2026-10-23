import assert from 'node:assert/strict';
import { createWeatherFrame, resolveWeatherScene, WEATHER_PRESETS, WEATHER_CODE_GROUPS, MOTION } from '../dist/weather-art.js';
import { resolvePixelGrid, FINE_MOTION } from '../dist/weather-shader.js';

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
assert.deepEqual(resolvePixelGrid(1728,2304),{columns:1536,rows:2048},'3.4K split panel uses the denser fine grid');
assert.deepEqual(resolvePixelGrid(800,1200),{columns:800,rows:1200},'smaller panels render one logical cell per native pixel');
assert.deepEqual(resolvePixelGrid(1728,2304,'block'),{columns:96,rows:128},'block mode keeps its established grid');
assert.deepEqual(resolvePixelGrid(800,400,'block',4096,192),{columns:192,rows:96},'observation can refine its basic grid without changing weather defaults');
assert.deepEqual(resolvePixelGrid(800,400,'block',128,192),{columns:128,rows:64},'observation grid still respects GPU limits');
for(const [width,height,limit] of [[320,480,4096],[1440,960,4096],[3456,2304,4096],[3840,2160,4096],[300,6000,1024],[6000,300,1024],[1,1,4096]]){
  for(const style of ['fine','block']){
    const {columns,rows}=resolvePixelGrid(width,height,style,limit);
    assert(Number.isInteger(columns)&&Number.isInteger(rows)&&columns>0&&rows>0);
    assert(columns<=limit&&rows<=limit,'both texture dimensions must fit the GPU limit');
    assert(Math.abs(columns*height-rows*width)<=Math.max(width,height),'grid aspect must stay within one texel of the panel');
    if(style==='fine')assert(columns<=Math.min(width,FINE_MOTION.columns)&&rows<=height,'fine mode must not oversample the native canvas');
  }
}
console.log(`Verified ${names.length} Canvas fallback states at three panel shapes: weather mapping, valid pixels, motion and eight-second fallback loops.`);
console.log('Verified native-density fine grids, the 3.4K tablet split, block-mode sizing, aspect preservation and GPU texture limits.');
