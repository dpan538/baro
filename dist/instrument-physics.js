import {skyAt,skyLocalEpoch,skyLocalParts,solarCrossings} from './sky-model.js';

// Deliberately independent DEMONSTRATION soundings, not inferred from surface RH.
// PWV mm / 300-hPa wind km/h, from° / freezing ASL m / CAPE J/kg /
// 0–1 cm volumetric soil water / daytime surface excess °C / rain rate mm/h.
const INSTRUMENT_SOUNDINGS=Object.freeze({
 dawn:[22,32,265,4000,80,.22,4,0],partly:[28,42,260,3800,350,.29,7,.1],
 clear:[18,36,285,4100,120,.18,12,0],overcast:[24,48,270,2300,180,.31,2,.2],
 rain:[37,62,245,3200,620,.39,1,3.6],storm:[61,75,245,5000,2100,.43,.7,7.8],
 snow:[8,54,290,0,15,.32,.5,.8],fog:[19,20,220,2100,30,.36,1,0],
 wind:[18,87,300,2500,160,.24,5,.1],night:[6,38,280,900,10,.20,0,0],
 sunset:[20,42,260,4200,180,.21,8,0]
});
const physicsRad=Math.PI/180,physicsClamp=(v,a,b)=>Math.max(a,Math.min(b,v));
export function surfaceOptics(temperature,humidity,dewPoint){
 const vpd=.6108*Math.exp(17.27*temperature/(temperature+237.3))*(1-humidity/100);
 const wet=temperature*Math.atan(.151977*Math.sqrt(humidity+8.313659))+Math.atan(temperature+humidity)-Math.atan(humidity-1.676331)+.00391838*humidity**1.5*Math.atan(.023101*humidity)-4.686035;
 return {vpd:Math.max(0,vpd),lcl:Math.max(0,(temperature-dewPoint)*125),wetBulb:humidity===100?temperature:temperature>=-20&&temperature<=50&&humidity>=5&&humidity<=99?Math.min(temperature,Math.max(dewPoint,wet)):null};
}
export function horizontalRadiation(dni,diffuse,elevation){
 const direct=Math.max(0,dni)*Math.max(0,Math.sin(elevation*physicsRad)),dhi=Math.max(0,diffuse);
 return {direct,diffuse:dhi,total:direct+dhi,ratio:dhi>0?direct/dhi:null};
}
export function vectorWindDifference(speedA,fromA,speedB,fromB){
 const u=(speed,from)=>[-speed*Math.sin(from*physicsRad),-speed*Math.cos(from*physicsRad)];
 const a=u(speedA,fromA),b=u(speedB,fromB);return Math.hypot(b[0]-a[0],b[1]-a[1]);
}
// Low-precision lunar coordinate formula adapted from SunCalc 1.9.0,
// Vladimir Agafonkin; BSD-2-Clause notice in licenses/SunCalc-BSD-2-Clause.txt.
export function instrumentMoonAltitude(epoch,latitude,longitude){
 const d=epoch/86400+2440587.5-2451545,e=23.4397*physicsRad;
 const L=(218.316+13.176396*d)*physicsRad,M=(134.963+13.064993*d)*physicsRad,F=(93.272+13.229350*d)*physicsRad;
 const l=L+6.289*physicsRad*Math.sin(M),b=5.128*physicsRad*Math.sin(F);
 const ra=Math.atan2(Math.sin(l)*Math.cos(e)-Math.tan(b)*Math.sin(e),Math.cos(l));
 const dec=Math.asin(Math.sin(b)*Math.cos(e)+Math.cos(b)*Math.sin(e)*Math.sin(l));
 const H=(280.16+360.9856235*d+longitude)*physicsRad-ra,p=latitude*physicsRad;
 let h=Math.asin(Math.sin(p)*Math.sin(dec)+Math.cos(p)*Math.cos(dec)*Math.cos(H));
 const positive=Math.max(0,h);h+=.0002967/Math.tan(positive+.00312536/(positive+.08901179));
 return h/physicsRad;
}
export function instrumentMoonEvents(day){
 if(day.instrumentMoonEvents?.date===day.date)return day.instrumentMoonEvents;
 if(!day.coordinates)return {rise:null,set:null};
 const {latitude,longitude}=day.coordinates,start=skyLocalEpoch(day.date,0,day.weather.timezone),end=skyLocalEpoch(day.date,1440,day.weather.timezone);
 const height=t=>instrumentMoonAltitude(t,latitude,longitude)-.133,result={date:day.date,rise:null,set:null};
 let previous=start,a=height(start);
 for(let time=start+600;time<=end;time+=600){const b=height(time);
  if((a<0&&b>=0)||(a>=0&&b<0)){let lo=previous,hi=time;for(let n=0;n<16;n++){const mid=(lo+hi)/2;if((height(lo)<0)===(height(mid)<0))lo=mid;else hi=mid;}result[b>a?'rise':'set']=(lo+hi)/2;}
  previous=time;a=b;
 }
 day.instrumentMoonEvents=result;return result;
}
export function instrumentEnvironment(day,minute){
 const m=skyAt(day,minute),p=INSTRUMENT_SOUNDINGS[day.preset];if(!p)return {};
 const [pwv,upperSpeed,upperFrom,freezing,cape,soil,heat,rain]=p,exposure=Math.max(0,Math.sin((m.sun?.elevation??0)*physicsRad));
 const change=Math.sin((minute-day.initialMinute)/180)*.08;
 const dni=exposure>0?850*(1-m.low/115)*(1-m.high/300):0,dhi=exposure*180*(.35+m.low/100);
 const radiation=horizontalRadiation(dni,dhi,m.sun?.elevation??0),surfaceDelta=heat*exposure-1.5*(1-exposure)*(1-m.low/100);
 const upper={speed:upperSpeed*(1+change),from:(upperFrom+change*100+360)%360};
 return {pwv:pwv*(1+change*.2),upper,windDifference:vectorWindDifference(m.windSpeed,m.windFrom,upper.speed,upper.from),
  freezing:Math.max(0,freezing+change*300),cape:Math.max(0,cape*(1+change)),soil:physicsClamp(soil+change*.015,0,1),
  surfaceTemperature:m.temperature+surfaceDelta,surfaceDelta,radiation,rainRate:Math.max(0,rain*(1+change*3)),
  ...surfaceOptics(m.temperature,m.humidity,m.dewPoint),cin:({storm:-35,rain:-70,snow:-180,fog:-240,night:-210,dawn:-160,sunset:-90,clear:-130,partly:-85,overcast:-110,wind:-100})[day.preset],
  moonEvents:instrumentMoonEvents(day),bortle:null};
}
export function instrumentLightStage(day,purpose,window){
 const reference=day.weather.validAt;
 if(['sunset','dawn','blue'].includes(purpose)&&window.available){
  const start=skyLocalEpoch(window.day.date,window.start,day.weather.timezone),end=skyLocalEpoch(window.day.date,window.end,day.weather.timezone),active=start<=reference&&reference<end;
  return {key:purpose,at:active?end:start,seconds:Math.max(0,Math.round((active?end:start)-reference)),active};
 }
 if(['sunset','dawn','blue'].includes(purpose))return {key:null,at:null,seconds:null,active:false};
 const candidates=[];
 for(let offset=0;offset<=1;offset++){
  const parts=skyLocalParts(skyLocalEpoch(day.date,offset*1440,day.weather.timezone),day.weather.timezone),date=parts.year+'-'+parts.month+'-'+parts.day;
  for(const [height,rising,key] of [[-.833,true,'golden'],[6,false,'golden'],[-6,true,'blue'],[-4,false,'blue']]){
   const events=offset?solarCrossings(date,day.weather.timezone,day.coordinates,height):day.thresholds[height]??[];
   for(const event of events)if(event.rising===rising&&event.at>reference)candidates.push({key,at:event.at,seconds:Math.round(event.at-reference),active:false});
  }
 }
 return candidates.sort((a,b)=>a.at-b.at)[0]??{key:null,at:null,seconds:null,active:false};
}
