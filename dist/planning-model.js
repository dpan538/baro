import { skyAt, skyClock, skyLocalParts, skyLocalEpoch, solarCrossings } from './sky-model.js';

// A purpose selects an information set, never a widget or a weather condition.
export const SKY_PURPOSES=Object.freeze([
  ['commute','通勤','COMMUTE'],['walking','步行','WALKING'],['cycling','骑行','CYCLING'],
  ['outing','户外停留','OUTDOORS'],['sunset','晚霞拍摄','SUNSET PHOTO'],['dawn','晨光拍摄','DAWN PHOTO'],
  ['blue','蓝调拍摄','BLUE HOUR'],['stars','夜空观测','STARGAZING'],['horizon','远眺','DISTANT VIEW'],
  ['camp','露营','CAMPING'],['drying','晾晒通风','AIR & DRYING'],['gear','器材防潮','EQUIPMENT']
]);
const planningClamp=(v,a,b)=>Math.max(a,Math.min(b,v));
export function planningWindow(day,purpose){
  const now=day.initialMinute,tz=day.weather.timezone;
  const requested={sunset:[6,false],dawn:[-6,true],blue:[-4,false],stars:[-18,false]}[purpose];
  let start=now,end=Math.min(1560,now+120),nextDay=false;
  if(purpose==='stars'&&(skyAt(day,now).sun?.elevation??0)<-18)return {day,start,end,nextDay:false,available:true};
  if(requested&&day.coordinates){
    const [height,rising]=requested;
    let date=day.date,event=day.thresholds[height]?.find(e=>e.rising===rising&&e.at>=day.weather.validAt-1800);
    if(!event){
      const p=skyLocalParts(skyLocalEpoch(day.date,1440,tz),tz);date=`${p.year}-${p.month}-${p.day}`;
      event=solarCrossings(date,tz,day.coordinates,height).find(e=>e.rising===rising);nextDay=true;
    }
    if(event){
      const p=skyLocalParts(event.at,tz);start=+p.hour*60+ +p.minute;
      const endHeight={sunset:-6,dawn:6,blue:-6}[purpose];
      const endEvent=endHeight!==undefined?(date===day.date?day.thresholds[endHeight]:solarCrossings(date,tz,day.coordinates,endHeight)).find(e=>e.rising===rising&&e.at>event.at):null;
      if(endEvent){const q=skyLocalParts(endEvent.at,tz);end=+q.hour*60+ +q.minute;if(end<start)end+=1440;}else end=start+120;
      return {day:date===day.date?day:{...day,date,cache:new Map()},start,end:Math.min(1560,end),nextDay,available:true};
    }
    return {day,start,end,nextDay:false,available:false};
  }
  return {day,start,end,nextDay,available:true};
}
export function makePlanningView(day,purpose,language='zh'){
  if(!SKY_PURPOSES.some(p=>p[0]===purpose))purpose='commute';
  const en=language==='en',photo=['sunset','dawn','blue'].includes(purpose),night=['stars','camp'].includes(purpose),air=['drying','gear'].includes(purpose);
  const window=planningWindow(day,purpose),m=skyAt(window.day,window.start),end=skyAt(window.day,window.end),samples=Array.from({length:25},(_,i)=>skyAt(window.day,window.start+(window.end-window.start)*i/24));
  const c=(zh,english)=>en?english:zh;
  const time=at=>skyClock(at,day.weather.timezone),round=(v,n=0)=>Number.isFinite(v)?v.toFixed(n):'—';
  let primary,secondary;
  if(photo){primary={label:c('霞光条件指数','GLOW CONDITION SCORE'),value:m.glow,unit:'/100',note:c('规则估算 · 非概率','HEURISTIC · NOT PROBABILITY')};secondary=[c('太阳高度角','SOLAR ELEVATION'),round(m.sun?.elevation,1)+'°'];}
  else if(purpose==='stars'){primary={label:c('月面照明 ≈','MOON ILLUMINATION ≈'),value:round(m.moon.illumination*100),unit:'%',note:c('均匀朔望月近似 · 无月亮高度','MEAN LUNATION · NO LUNAR ALTITUDE')};secondary=[c('低云覆盖','LOW CLOUD COVER'),round(m.low)+'%'];}
  else if(purpose==='horizon'){primary={label:c('能见度','VISIBILITY'),value:round(m.visibility,1),unit:'km',note:c('地形遮挡数据未接入','TERRAIN OCCLUSION UNAVAILABLE')};secondary=[c('低云','LOW CLOUD'),round(m.low)+'%'];}
  else if(air){primary={label:c('相对湿度','RELATIVE HUMIDITY'),value:round(m.humidity),unit:'%',note:c('地面环境 · 示例','SURFACE CONDITIONS · DEMO')};secondary=[c('露点差','DEW-POINT SPREAD'),round(m.dewSpread,1)+'°C'];}
  else if(purpose==='cycling'){primary={label:c('阵风','WIND GUST'),value:round(m.windGust),unit:'km/h',note:c('地面风场 · 示例','SURFACE WIND · DEMO')};secondary=[c('降水概率','PRECIPITATION CHANCE'),round(m.precipitation)+'%'];}
  else if(purpose==='camp'||purpose==='outing'){primary={label:c('气温','AIR TEMPERATURE'),value:round(m.temperature,1),unit:'°C',note:c('时段起点','AT WINDOW START')};secondary=[c('时段末气温','AT WINDOW END'),round(end.temperature,1)+'°C'];}
  else{primary={label:c('降水概率','PRECIPITATION CHANCE'),value:round(m.precipitation),unit:'%',note:c('两小时示意趋势 · 非雷达','2-HOUR ILLUSTRATION · NOT RADAR')};secondary=[c('阵风','WIND GUST'),round(m.windGust)+' km/h'];}
  const suggestions=[];
  // Advice is deliberately sparse: at most three short lines, beside the data.
  if(day.preset==='storm')suggestions.push(c('雷暴时避开空旷高处','Lightning: shelter'));
  else if(m.precipitation>60)suggestions.push(c('随身带伞','Carry an umbrella'));
  if(purpose==='gear'&&m.dewSpread<3)suggestions.push(c('镜头留意结露','Lens dew possible'));
  if((purpose==='camp'||purpose==='outing')&&end.temperature<m.temperature-1.5)suggestions.push(c('入夜备一件外套','Pack a warm layer'));
  const metrics={photo,night,air};
  const trendKey=photo?'glow':purpose==='horizon'?'visibility':air?'humidity':purpose==='camp'||purpose==='outing'?'temperature':'precipitation';
  const barKey=photo?'low':purpose==='stars'?'high':air?'humidity':'precipitation';
  return {purpose,m,end,samples,window,primary,secondary,suggestions:suggestions.slice(0,3),metrics,trendKey,barKey,
    interval:time(m.epoch)+'–'+time(end.epoch),dayLabel:window.nextDay?c('明日','TOMORROW'):c('今日','TODAY'),
    trend:samples.map(v=>v[trendKey]),barValue:planningClamp(m[barKey],0,100),
    preset:day.preset,location:en?day.weather.cityEn:day.weather.cityZh};
}
