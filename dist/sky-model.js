// An illustrative local sky notebook. Meteorological profiles below are fixtures,
// solar geometry is calculated, and the glow score is an uncalibrated heuristic.
const SKY_RAD=Math.PI/180;
const skyClamp=(value,min=0,max=100)=>Math.max(min,Math.min(max,value));
const skyWrap=value=>(value%360+360)%360;
export const SKY_LOCATIONS=Object.freeze({
  BRISBANE:{latitude:-27.4698,longitude:153.0251},SHANGHAI:{latitude:31.2304,longitude:121.4737},
  LONDON:{latitude:51.5074,longitude:-.1278},TOKYO:{latitude:35.6762,longitude:139.6503},
  SINGAPORE:{latitude:1.3521,longitude:103.8198},OSLO:{latitude:59.9139,longitude:10.7522},
  AMSTERDAM:{latitude:52.3676,longitude:4.9041},WELLINGTON:{latitude:-41.2866,longitude:174.7756},
  REYKJAVIK:{latitude:64.1466,longitude:-21.9426}
});
// high / mid / low / gust km/h / wind FROM degrees / PM2.5 µg/m³ / hPa / 3h hPa.
const SKY_PROFILES=Object.freeze({
  dawn:[42,16,4,12,75,7,1018.3,.4],partly:[52,20,12,22,135,28,1014.2,-.7],
  clear:[12,4,2,21,95,6,1017.4,.3],overcast:[74,82,91,31,235,13,1008.6,-1.8],
  rain:[72,85,94,35,210,9,1005.8,-2.1],storm:[88,93,97,52,160,16,1001.7,-3.2],
  snow:[64,78,92,38,25,5,1008.2,-1.2],fog:[18,23,96,8,190,21,1021.4,.2],
  wind:[48,24,15,68,285,6,1009.1,-1.7],night:[9,5,3,16,50,4,1020.2,.6],
  sunset:[52,20,5,14,245,8,1015.6,-.4]
});
const SKY_FORMATTERS=new Map();
export function skyLocalParts(epoch,timezone){
  if(!SKY_FORMATTERS.has(timezone))SKY_FORMATTERS.set(timezone,new Intl.DateTimeFormat('en-GB',{timeZone:timezone,year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}));
  return Object.fromEntries(SKY_FORMATTERS.get(timezone).formatToParts(new Date(epoch*1000)).map(p=>[p.type,p.value]));
}
export function skyLocalEpoch(date,minute,timezone){
  const [year,month,day]=date.split('-').map(Number);
  const target=Date.UTC(year,month-1,day,0,minute)/1000;
  let epoch=target;
  for(let i=0;i<3;i++){
    const p=skyLocalParts(epoch,timezone);
    const local=Date.UTC(+p.year,+p.month-1,+p.day,+p.hour,+p.minute)/1000;
    epoch+=target-local;
  }
  return epoch;
}
export function skyClock(epoch,timezone){
  if(!Number.isFinite(epoch))return '—';
  const p=skyLocalParts(epoch,timezone);return p.hour+':'+p.minute;
}
export function solarPosition(epoch,latitude,longitude){
  if(![epoch,latitude,longitude].every(Number.isFinite))return null;
  const n=epoch/86400+2440587.5-2451545;
  const mean=skyWrap(280.46+.9856474*n)*SKY_RAD,anomaly=skyWrap(357.528+.9856003*n)*SKY_RAD;
  const ecliptic=mean+(1.915*Math.sin(anomaly)+.020*Math.sin(2*anomaly))*SKY_RAD;
  const obliquity=(23.439-.0000004*n)*SKY_RAD;
  const ascension=Math.atan2(Math.cos(obliquity)*Math.sin(ecliptic),Math.cos(ecliptic))/SKY_RAD;
  const declination=Math.asin(Math.sin(obliquity)*Math.sin(ecliptic));
  const hour=skyWrap(280.46061837+360.98564736629*n+longitude-ascension)*SKY_RAD;
  const lat=latitude*SKY_RAD;
  const elevation=Math.asin(Math.sin(lat)*Math.sin(declination)+Math.cos(lat)*Math.cos(declination)*Math.cos(hour))/SKY_RAD;
  const azimuth=skyWrap(Math.atan2(Math.sin(hour),Math.cos(hour)*Math.sin(lat)-Math.tan(declination)*Math.cos(lat))/SKY_RAD+180);
  return {elevation,azimuth};
}
export function solarCrossings(date,timezone,coordinates,altitude){
  if(!coordinates)return [];
  const start=skyLocalEpoch(date,0,timezone),end=skyLocalEpoch(date,1440,timezone),events=[];
  const height=at=>solarPosition(at,coordinates.latitude,coordinates.longitude).elevation-altitude;
  let previous=start,a=height(start);
  for(let time=start+600;time<=end;time+=600){
    const b=height(time);
    if((a<0&&b>=0)||(a>=0&&b<0)){
      let lo=previous,hi=time;
      for(let i=0;i<15;i++){const mid=(lo+hi)/2;if((height(lo)<0)===(height(mid)<0))lo=mid;else hi=mid;}
      events.push({at:(lo+hi)/2,rising:b>a});
    }
    previous=time;a=b;
  }
  return events;
}
export function skyDewPoint(temperature,humidity){
  if(![temperature,humidity].every(Number.isFinite)||humidity<=0||humidity>100)return null;
  const alpha=Math.log(humidity/100)+17.625*temperature/(243.04+temperature);
  return 243.04*alpha/(17.625-alpha);
}
export function skyMoonPhase(epoch){
  const cycle=29.530588853,age=((epoch/86400-Date.UTC(2000,0,6,18,14)/86400000)%cycle+cycle)%cycle;
  return {age,fraction:age/cycle,illumination:(1-Math.cos(age/cycle*2*Math.PI))/2};
}
export function skyDirection(degrees,language='en'){
  const en=['N','NNE','NE','ENE','E','ESE','SE','SSE','S','SSW','SW','WSW','W','WNW','NW','NNW'],zh=['北','北北东','东北','东东北','东','东东南','东南','南南东','南','南南西','西南','西西南','西','西西北','西北','北北西'];
  return (language==='en'?en:zh)[Math.round(skyWrap(degrees)/22.5)%16];
}
export function glowConditionScore({high,mid,low,visibility,humidity}){
  if(![high,mid,low,visibility,humidity].every(Number.isFinite))return null;
  const canvas=skyClamp(1-Math.abs(high-50)/55,0,1),depth=skyClamp(mid/30,0,1);
  return Math.round(skyClamp(20+canvas*29+depth*10+skyClamp(visibility/25,0,1)*25-low*.48-Math.max(0,humidity-85)*.35));
}
export function makeSkyDay(weather,preset){
  const coordinates=SKY_LOCATIONS[weather.cityEn]??null;
  const profile=SKY_PROFILES[preset]??null;
  const p=skyLocalParts(weather.validAt,weather.timezone),date=p.year+'-'+p.month+'-'+p.day;
  const thresholds={};
  for(const altitude of [6,-.833,-4,-6,-12,-18])thresholds[altitude]=solarCrossings(date,weather.timezone,coordinates,altitude);
  const morning=+p.hour<12;
  const event=(altitude,rising)=>thresholds[altitude].find(e=>e.rising===rising)?.at??null;
  const windows=morning?[
    {key:'blue',start:event(-6,true),end:event(-4,true)},
    {key:'civil',start:event(-6,true),end:event(-.833,true)},
    {key:'golden',start:event(-.833,true),end:event(6,true)},
    {key:'night',start:skyLocalEpoch(date,0,weather.timezone),end:event(-18,true)}
  ]:[
    {key:'golden',start:event(6,false),end:event(-.833,false)},
    {key:'civil',start:event(-.833,false),end:event(-6,false)},
    {key:'blue',start:event(-4,false),end:event(-6,false)},
    {key:'night',start:event(-18,false),end:skyLocalEpoch(date,1440,weather.timezone)}
  ];
  return {weather,preset,coordinates,profile,date,thresholds,windows,morning,cache:new Map(),initialMinute:+p.hour*60+ +p.minute};
}
export function skyAt(day,minute){
  minute=skyClamp(Math.round(minute),0,1560);
  if(day.cache.has(minute))return day.cache.get(minute);
  const {weather,profile,coordinates}=day,epoch=skyLocalEpoch(day.date,minute,weather.timezone);
  const sun=coordinates?solarPosition(epoch,coordinates.latitude,coordinates.longitude):null;
  if(!profile)return {minute,epoch,sun,available:false};
  const [baseHigh,baseMid,baseLow,gust,from,pm25,pressure,pressureTrend]=profile;
  const t=minute/60,delta=(minute-day.initialMinute)/60;
  const wave=(rate,phase)=>Math.sin(t*rate+phase)-Math.sin(day.initialMinute/60*rate+phase);
  const high=skyClamp(baseHigh+wave(.35,1)*8),mid=skyClamp(baseMid+wave(.47,3)*6);
  const low=skyClamp(baseLow+wave(.29,2)*7-(day.preset==='fog'?Math.max(0,t-8)*3:0));
  const humidity=skyClamp(weather.humidity+wave(.18,2)*5,15,100);
  const temperature=weather.temperature+wave(.20,-1)*2;
  const visibility=Math.max(.1,weather.visibility*(1+wave(.17,.5)*.12)+(day.preset==='fog'?Math.max(0,t-8)*.12:0));
  const dewPoint=skyDewPoint(temperature,humidity),dewSpread=dewPoint===null?null:Math.max(0,temperature-dewPoint);
  const windSpeed=Math.max(0,weather.windSpeed+wave(.48,.2)*3),windFrom=skyWrap(from+wave(.23,2)*13);
  const precipitation=skyClamp(weather.precipitationProbability+wave(.5,.6)*8);
  const glow=glowConditionScore({high,mid,low,humidity,visibility});
  const moon=skyMoonPhase(epoch),elevation=sun?.elevation??0;
  const phase=elevation>6?'day':elevation>-.833?'golden':elevation>-4?'civil':elevation>-6?'blue':elevation>-18?'twilight':'night';
  const context=['rain','storm','snow'].includes(day.preset)?'precipitation':day.preset==='fog'?'fog':phase==='night'?'night':'glow';
  const starScore=Math.round(skyClamp(95-low*.65-mid*.18-high*.12-moon.illumination*28-Math.max(0,25-visibility)*.7));
  // This is a palette impression for the illustration, not a measured sky CCT.
  const colorTemperature=phase==='day'?5800:phase==='golden'?3400:phase==='civil'?2600:phase==='blue'?9000:null;
  const windGust=Math.max(windSpeed,gust+wave(.4,2)*4);
  const model={available:true,minute,epoch,sun,phase,context,high,mid,low,humidity,temperature,dewPoint,dewSpread,visibility,
    windSpeed,windGust,windFrom,windToward:skyWrap(windFrom+180),pm25,pressure:pressure+delta*pressureTrend/3,
    pressureTrend,precipitation,glow,moon,starScore,colorTemperature};
  day.cache.set(minute,model);return model;
}
