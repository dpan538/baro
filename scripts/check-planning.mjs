import assert from 'node:assert/strict';
import {WEATHER_PRESETS} from '../dist/weather-art.js';
import {makeSkyDay,skyAt} from '../dist/sky-model.js';
import {SKY_PURPOSES,makePlanningView,planningWindow} from '../dist/planning-model.js';
assert.equal(new Set(SKY_PURPOSES.map(p=>p[0])).size,12);
let count=0;
for(const [preset,weather] of Object.entries(WEATHER_PRESETS)){
 const day=makeSkyDay(weather,preset);
 for(const [purpose] of SKY_PURPOSES)for(const lang of ['zh','en']){
  const view=makePlanningView(day,purpose,lang);
  assert.equal(view.purpose,purpose);assert(view.m.available);
  assert(view.end.epoch>=view.m.epoch);assert.equal(view.samples.length,25);
  assert(view.trend.every(Number.isFinite));assert(view.barValue>=0&&view.barValue<=100);
  assert(view.suggestions.length<=3);
  for(const text of view.suggestions)assert([...text].length<=20,`${preset}/${purpose}/${lang}: ${text}`);
  assert(view.window.end<=1560);assert.equal(view.dayLabel,view.window.nextDay?(lang==='en'?'TOMORROW':'明日'):(lang==='en'?'TODAY':'今日'));
  count++;
 }
}
const night=makeSkyDay(WEATHER_PRESETS.night,'night');
if(skyAt(night,night.initialMinute).sun.elevation<-18)assert.equal(planningWindow(night,'stars').start,night.initialMinute,'Already dark: observe this window, not tomorrow');
const day=makeSkyDay(WEATHER_PRESETS.clear,'clear');
assert.equal(makePlanningView(day,'commute').trendKey,'precipitation');
assert.equal(makePlanningView(day,'gear').trendKey,'humidity');
assert.equal(makePlanningView(day,'sunset').trendKey,'glow');
assert.equal(makePlanningView(day,'horizon').trendKey,'visibility');
assert.equal(makePlanningView(day,'stars').primary.label,'月面照明 ≈');
for(const purpose of ['sunset','dawn','blue']){
 const view=makePlanningView(day,purpose);
 const targetEnd=purpose==='dawn'?6:-6;
 assert(Math.abs(view.end.sun.elevation-targetEnd)<.3,'Photographic window must end at its geometric boundary, not an arbitrary duration');
}
assert.equal(makePlanningView(day,'invalid').purpose,'commute');
const polar={...day,coordinates:{latitude:89,longitude:0},date:'2026-06-21',thresholds:{'-18':[]}};
assert.equal(planningWindow(polar,'stars').available,false,'No astronomical night must not produce a fabricated crossing');
assert.equal(makePlanningView(makeSkyDay(WEATHER_PRESETS.storm,'storm'),'gear').suggestions.length,2);
const afterDusk=makeSkyDay(WEATHER_PRESETS.storm,'storm');
const tomorrow=makePlanningView(afterDusk,'sunset');
assert(tomorrow.window.nextDay);
assert(tomorrow.window.day.thresholds[-.833].every(event=>event.at>afterDusk.thresholds[-.833].at(-1).at),'Tomorrow must use tomorrow’s solar events in every module');
const missingEnd={...day,thresholds:{...day.thresholds,'-6':[]},initialMinute:0,weather:{...day.weather,validAt:day.thresholds[6].find(x=>!x.rising).at-3600}};
assert.equal(planningWindow(missingEnd,'sunset').available,false,'A missing end crossing must not become an arbitrary two-hour photographic window');
console.log(`Verified ${count} purpose/environment/language combinations, objective metric selection, midnight/polar windows and the three-line/20-character advice limit.`);
