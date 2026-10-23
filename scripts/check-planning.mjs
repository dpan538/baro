import assert from 'node:assert/strict';
import {WEATHER_PRESETS} from '../dist/weather-art.js';
import {makeSkyDay,skyAt} from '../dist/sky-model.js';
import {SKY_PURPOSES,makePlanningView,planningWindow,planningAdvice} from '../dist/planning-model.js';
assert.equal(new Set(SKY_PURPOSES.map(p=>p[0])).size,12);
let count=0;
for(const [preset,weather] of Object.entries(WEATHER_PRESETS)){
 const day=makeSkyDay(weather,preset);
 for(const [purpose] of SKY_PURPOSES)for(const lang of ['zh','en']){
  const view=makePlanningView(day,purpose,lang);
  assert.equal(view.purpose,purpose);assert(view.m.available);
  assert(view.end.epoch>=view.m.epoch);assert.equal(view.samples.length,25);
  assert(view.trend.every(Number.isFinite));assert(view.barValue>=0&&view.barValue<=100);
  const advice=planningAdvice(view,lang);
  assert([...advice].length<=(lang==='en'?180:84),`${preset}/${purpose}/${lang}: interpretation exceeds its reading budget`);
  assert(advice.length>0&&!advice.includes('\n'),'A connected interpretation, not an advice list');
  if(['gear','drying','stars','sunset','dawn','blue','horizon'].includes(purpose))assert(!/体感|汗液|闷热|muggy|sweat|feel cooler/i.test(advice),`${purpose}: interpretation must match its activity rather than personal comfort`);
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
assert.match(planningAdvice(makePlanningView(day,'sunset')),/傍晚/,'The photographic window must use its selected time period');
assert.match(planningAdvice(makePlanningView(day,'dawn')),/清晨/);
assert.equal(makePlanningView(day,'stars').primary.label,'月面照明 ≈');
for(const purpose of ['sunset','dawn','blue']){
 const view=makePlanningView(day,purpose);
 const targetEnd=purpose==='dawn'?6:-6;
 assert(Math.abs(view.end.sun.elevation-targetEnd)<.3,'Photographic window must end at its geometric boundary, not an arbitrary duration');
}
assert.equal(makePlanningView(day,'invalid').purpose,'commute');
const polar={...day,coordinates:{latitude:89,longitude:0},date:'2026-06-21',thresholds:{'-18':[]}};
assert.equal(planningWindow(polar,'stars').available,false,'No astronomical night must not produce a fabricated crossing');
const afterDusk=makeSkyDay(WEATHER_PRESETS.storm,'storm');
const tomorrow=makePlanningView(afterDusk,'sunset');
assert(tomorrow.window.nextDay);
assert(tomorrow.window.day.thresholds[-.833].every(event=>event.at>afterDusk.thresholds[-.833].at(-1).at),'Tomorrow must use tomorrow’s solar events in every module');
const missingEnd={...day,thresholds:{...day.thresholds,'-6':[]},initialMinute:0,weather:{...day.weather,validAt:day.thresholds[6].find(x=>!x.rising).at-3600}};
assert.equal(planningWindow(missingEnd,'sunset').available,false,'A missing end crossing must not become an arbitrary two-hour photographic window');
const adviceView={preset:'clear',purpose:'commute',m:{temperature:30,humidity:85,dewSpread:5,precipitation:0,rainRate:0,windGust:10,sun:{elevation:30}},end:{temperature:30}};
assert.match(planningAdvice(adviceView),/潮湿闷热/);
assert.match(planningAdvice(adviceView),/汗液不易蒸发/,'Wet heat should be explained in everyday terms');
assert.match(planningAdvice({...adviceView,m:{...adviceView.m,temperature:20,humidity:50,windGust:45}}),/晃动/,'Translate gust conditions without adding a new forecast');
assert.match(planningAdvice({...adviceView,m:{...adviceView.m,sun:{elevation:-20}}}),/夜间/);
assert.match(planningAdvice({...adviceView,m:{...adviceView.m,temperature:20,humidity:90,dewSpread:1,sun:{elevation:-20}}}),/冷表面可能结露/);
assert.match(planningAdvice({...adviceView,m:{...adviceView.m,temperature:5}}),/气温偏低/);
const warmCooling=planningAdvice({...adviceView,m:{...adviceView.m,temperature:30,humidity:50},end:{temperature:25}});
assert.match(warmCooling,/降温/);assert(!warmCooling.includes('气温偏低'),'Cooling from warm air must not be described as a cold temperature');
assert.match(planningAdvice({...adviceView,preset:'storm'}),/雷雨/);
assert.match(planningAdvice({...adviceView,preset:'snow'}),/降雪/,'Snow must not be interpreted as rain');
assert.match(planningAdvice({...adviceView,m:{...adviceView.m,temperature:20,humidity:50,sun:{elevation:4}}}),/晨昏低光/);
assert.match(planningAdvice({...adviceView,m:{...adviceView.m,temperature:20,humidity:50,windGust:50,visibility:3}}),/阵风较强、远景模糊/);
const gearView=makePlanningView(makeSkyDay(WEATHER_PRESETS.storm,'storm'),'gear');
assert.match(planningAdvice(gearView),/物品|金属/);
assert.match(planningAdvice({...gearView,purpose:'stars',window:{...gearView.window,available:false}}),/没有对应的暗夜窗口/);
assert.match(planningAdvice(gearView),/结露|水汽/);
const dryGear={...gearView,m:{...gearView.m,humidity:40,dewSpread:12,rainRate:0},end:{...gearView.end,humidity:40,dewSpread:12}};
assert.match(planningAdvice(dryGear),/明显冷却/,'Dry storage conditions must not imply that condensation is easy');
console.log(`Verified ${count} purpose/environment/language combinations, objective metric selection, midnight/polar windows and bounded plain-language environment interpretations.`);
