import assert from 'node:assert/strict';
import { WEATHER_PRESETS } from '../dist/weather-art.js';
import { solarPosition, solarCrossings, skyLocalEpoch, skyClock, skyMoonPhase, skyDewPoint, skyDirection, glowConditionScore, makeSkyDay, skyAt } from '../dist/sky-model.js';

const epoch=iso=>Date.parse(iso)/1000;
// Equinox geometry provides an independent analytic sanity check.
assert(solarPosition(epoch('2026-03-20T12:08:00Z'),0,0).elevation>89);
assert(solarPosition(epoch('2026-03-20T00:08:00Z'),0,0).elevation< -89);
assert(Math.abs(solarPosition(epoch('2026-03-20T06:08:00Z'),0,0).azimuth-90)<1);
assert(Math.abs(solarPosition(epoch('2026-03-20T18:08:00Z'),0,0).azimuth-270)<1);
assert.equal(solarPosition(null,0,0),null);
const crosses=solarCrossings('2026-03-20','UTC',{latitude:0,longitude:0},-.833);
assert.equal(crosses.length,2);assert(crosses[0].rising);assert(!crosses[1].rising);
assert(Math.abs((crosses[1].at-crosses[0].at)/3600-12)<.2);
assert.equal(solarCrossings('2026-12-21','UTC',{latitude:89,longitude:0},-.833).length,0);
assert.equal(solarCrossings('2026-06-21','UTC',{latitude:89,longitude:0},-18).length,0);
// DST conversion must preserve local wall-clock intent, including 25-hour days.
const tz='Europe/Amsterdam';
assert.equal(skyClock(skyLocalEpoch('2026-10-25',240,tz),tz),'04:00');
assert.equal(skyLocalEpoch('2026-10-25',240,tz)-skyLocalEpoch('2026-10-25',0,tz),5*3600);
assert.equal(skyClock(skyLocalEpoch('2026-10-22',1490,'Australia/Brisbane'),'Australia/Brisbane'),'00:50');
assert(Math.abs(skyDewPoint(20,100)-20)<1e-9);assert(skyDewPoint(20,50)<11);assert.equal(skyDewPoint(20,0),null);
assert.equal(skyDirection(248),'WSW');assert.equal(skyDirection(0),'N');assert.equal(skyDirection(359),'N');assert.equal(skyDirection(180+245),'ENE');
const newMoon=epoch('2000-01-06T18:14:00Z'),cycle=29.530588853*86400;
assert(skyMoonPhase(newMoon).illumination<.001);assert(skyMoonPhase(newMoon+cycle/2).illumination>.999);
assert.equal(glowConditionScore({}),null);
const clear={high:50,mid:20,low:5,visibility:25,humidity:60};
assert(glowConditionScore(clear)>glowConditionScore({...clear,low:80}));
assert(glowConditionScore(clear)>glowConditionScore({...clear,visibility:2}));
let samples=0;
for(const [preset,weather] of Object.entries(WEATHER_PRESETS)){
  const day=makeSkyDay(weather,preset);
  for(const minute of [0,300,720,1073,1435,1555]){
    const model=skyAt(day,minute);assert(model.available);assert.equal(model.minute,minute);
    for(const key of ['high','mid','low','glow','starScore','precipitation'])assert(model[key]>=0&&model[key]<=100,preset+' '+key);
    assert(model.dewSpread>=0);assert(model.windGust>=model.windSpeed);
    assert(model.sun.elevation>=-90&&model.sun.elevation<=90);assert(model.sun.azimuth>=0&&model.sun.azimuth<360);
    assert.equal(skyAt(day,minute),model,'Repeated samples should be stable and cached');
    samples++;
  }
}
const day=makeSkyDay(WEATHER_PRESETS.clear,'clear');
assert(skyAt(day,0).sun.elevation<0);
assert(skyAt(day,720).sun.elevation>0);
assert.equal(skyAt(makeSkyDay(WEATHER_PRESETS.clear,'missing-profile'),720).available,false);
assert.equal(skyAt(makeSkyDay(WEATHER_PRESETS.rain,'rain'),1080).context,'precipitation');
assert.equal(skyAt(makeSkyDay(WEATHER_PRESETS.fog,'fog'),1080).context,'fog');
assert.equal(skyAt(makeSkyDay(WEATHER_PRESETS.night,'night'),1380).context,'night');
console.log(`Verified solar geometry, polar/DST boundaries, lunar-cycle limits, wind directions, score missing-data behavior and ${samples} deterministic sky samples across 11 environments.`);
