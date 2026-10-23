import { skyAt, skyClock, skyLocalParts, skyLocalEpoch, solarCrossings } from './sky-model.js';
import {instrumentEnvironment,instrumentLightStage} from './instrument-physics.js';

// A purpose selects an information set, never a widget or a weather condition.
export const SKY_PURPOSES=Object.freeze([
  ['commute','通勤','COMMUTE'],['walking','步行','WALKING'],['cycling','骑行','CYCLING'],
  ['outing','户外停留','OUTDOORS'],['sunset','晚霞拍摄','SUNSET PHOTO'],['dawn','晨光拍摄','DAWN PHOTO'],
  ['blue','蓝调拍摄','BLUE HOUR'],['stars','夜空观测','STARGAZING'],['horizon','远眺','DISTANT VIEW'],
  ['camp','露营','CAMPING'],['drying','居家建议','HOME TIPS'],['gear','储存建议','STORAGE TIPS']
]);
const planningClamp=(v,a,b)=>Math.max(a,Math.min(b,v));
// Plain-language interpretation of the selected window, not a second forecast.
// Translate selected surface comfort, moisture and optical conditions into plain language.
function planningInterpretationLead({preset,purpose,window,m,end},language='zh'){
 const elevation=m.sun?.elevation??0,night=elevation<-6;
 const wetHeat=m.wetBulb>=25||(m.temperature>=28&&m.humidity>=70);
 const rainy=m.rainRate>.2||m.precipitation>60,gusty=m.windGust>=35;
 const poorView=m.visibility<8,dew=m.dewSpread<3&&m.humidity>=80;
 const cold=m.temperature<=8,cooling=end.temperature<m.temperature-3;
 if(language==='en'){
  if(preset==='snow')return 'Snowy, cold; muted views.';
  if(preset==='storm'){const conditions=[wetHeat?'muggy':cold?'cold':m.humidity>=80?'humid':null,gusty?'gusty':null,poorView?'hazy':null].filter(Boolean);return 'Storms'+(conditions.length?'; '+conditions.join(', '):' in this window')+'.';}
  if(rainy)return night?'Rain during the night.':'Rain in the selected window.';
  if(wetHeat)return night?'Warm, humid night.':'Hot, humid air near ground.';
  if(cold)return night?'Cold air after dark.':'Cool air, low temperatures.';
  if(cooling)return 'Cooling across this window.';
  if(poorView)return dew?'Hazy, humid near the ground.':'Hazy, distant views muted.';
  if(dew)return 'Humid air near the surface.';
  if(gusty)return 'Strong gusts at the surface.';
  if(window?.available!==false&&['sunset','dawn','blue'].includes(purpose))return {sunset:'Low evening light.',dawn:'Dawn light near the horizon.',blue:'Blue hour, low natural light.'}[purpose];
  if(elevation<6)return night?'Dark, mild conditions.':'Low sun, changing light.';
  return m.temperature>=26?'Warm air near the ground.':m.humidity<35?'Dry air in daytime.':m.radiation?.total>0&&m.radiation.diffuse/m.radiation.total>.7?'Mild air, soft daylight.':'Mild air, daytime light.';
 }
 const photoTime=window?.available!==false?{sunset:'傍晚',dawn:'清晨',blue:'蓝调时段'}[purpose]:null;
 const time=photoTime??(night?'夜间':elevation<6?'晨昏':'白天');
 const sky=preset==='storm'?'雷雨':preset==='snow'?'降雪':rainy?'有雨':m.visibility<1?'浓雾':m.low>85?'云厚':elevation<6&&!night?'低光':'';
 const comfort=wetHeat?'潮湿闷热':cold?'气温偏低':cooling?'稍后降温':m.temperature>=26?'气温偏暖':m.humidity>=80?'空气潮湿':m.humidity<35?'空气干燥':'体感温和';
 const details=[comfort];
 if(gusty)details.push('阵风较强');
 if(poorView)details.push('远景模糊');
 if(details.length<3&&m.pwv>40&&!poorView)details.push('远景通透度偏低');
 if(details.length<3&&m.radiation?.total>0&&m.radiation.diffuse/m.radiation.total>.7)details.push('日光柔和');
 return `${time}${sky}，${details.slice(0,3).join('、')}。`;
}
// Each purpose chooses what the same measured/demo conditions mean for that activity.
function planningPurposeInterpretation({purpose,preset,window,m,end},language){
 const en=language==='en',c=(zh,english)=>en?english:zh,dew=m.dewSpread<3&&m.humidity>=80;
 const rain=m.rainRate>.2,gust=m.windGust>=35,night=(m.sun?.elevation??0)<-6;
 const lowCloud=m.low>80,cloud=Math.max(m.low??0,m.mid??0,m.high??0),details=[];
 let lead;
 if(purpose==='gear'){
  lead=dew?c('空气接近饱和，冷金属和玻璃表面容易结露。','Near-saturated air can leave dew on cool metal and glass.'):
   m.humidity>=80?c('空气偏湿，纸张、织物和金属物品需留意防潮。','Humid weather calls for moisture protection for paper, fabric and metal.'):
   m.dewSpread>=8?c('空气离饱和较远，物品表面需明显冷却才会结露。','Air is farther from saturation; stored items need more cooling before dew forms.'):
   c('空气尚未饱和，物品是否结露取决于表面温度。','Air is below saturation; condensation depends on the surface temperature of stored items.');
  if(preset==='snow')details.push(c('积雪融化后的水分会增加储存前的干燥需求。','Meltwater adds moisture that needs to dry before storage.'));
  else if(rain)details.push(c('淋雨后的物品会把表面水分带入储存空间。','Rain-exposed items can carry surface moisture into storage.'));
  if(end.dewSpread<m.dewSpread-.5&&end.humidity>=80)details.push(c('后段空气更接近饱和，结露条件会增强。','Air nears saturation later, increasing the chance of dew.'));
  if(dew)details.push(c('从低温环境移出的物品，表面可能比空气更冷。','Items moved from a cold space may be colder than the air.'));
  else details.push(c('室外气象读数不代表储物柜内已经潮湿。','Outdoor readings do not establish humidity inside a cupboard.'));
 }else if(purpose==='drying'){
  lead=rain?c('雨水会重新打湿露天衣物，晾晒容易中断。','Rain can rewet outdoor laundry and interrupt drying.'):
   m.humidity>=80||m.vpd<.5?c('空气偏湿，衣物和潮湿物品不易干透。','Humid air slows the drying of laundry and damp household items.'):
   c('空气仍有蒸发余量，流动的风有助带走织物表面的水汽。','Air has room for evaporation; moving air carries moisture away from fabric.');
  if(night)details.push(c('夜间没有日照加热，干燥主要依靠空气流动。','There is no solar heating at night; drying relies on airflow.'));
  else if(m.radiation?.total>0)details.push(c('日照可提供热量，但干燥速度也受湿度影响。','Sunlight adds heat, but humidity still affects drying.'));
  if(gust)details.push(c('阵风会让轻薄织物明显摆动。','Gusts can strongly move light fabric.'));
 }else if(purpose==='stars'){
  lead=window?.available===false?c('当前日期没有对应的暗夜窗口，不能套用常规观测时段。','This date has no matching dark-night window; a standard observing period does not apply.'):
   cloud>80?c('云层遮挡较多，夜空可见的开口有限。','Cloud cover leaves limited openings in the night sky.'):
   c('云层仍有开口，能否看清暗弱目标也取决于现场光害。','Clouds leave openings; faint targets also depend on local light pollution.');
  if(dew)details.push(c('冷镜片可能结露，让星点变得模糊。','Dew on cool optics may blur star images.'));
  if(m.moon?.illumination>.6)details.push(c('月面较亮，但月光影响还取决于月亮是否已升起。','The Moon is bright, but its effect depends on whether it is above the horizon.'));
  details.push(c('现场光害未测，暂不能判断暗空等级。','Local light pollution has not been measured.'));
 }else if(['sunset','dawn','blue'].includes(purpose)){
  const time={sunset:c('傍晚','Evening'),dawn:c('清晨','Dawn'),blue:c('蓝调时段','Blue hour')}[purpose];
  lead=window?.available===false?c('当前日期没有对应的光照交点，不能套用固定拍摄时段。','This date has no matching light crossing; a fixed photo window would be misleading.'):
   time+(lowCloud?c('低云遮挡较多，地平线附近的光线可能被挡住。',' has thick low cloud that may obscure horizon light.'):
   m.visibility<8?c('远景层次偏弱，轮廓比细节更容易保留下来。',' has muted distant detail; silhouettes may read more clearly.'):
   c('光线处在转换中，云隙和地平线会影响画面层次。',' light is changing; cloud gaps and the horizon shape the scene.'));
  if(gust)details.push(c('阵风会让枝叶和长焦画面更易晃动。','Gusts can move foliage and shake telephoto framing.'));
  if(dew)details.push(c('冷镜片可能结露，画面会因此发雾。','Dew on cool optics can fog the image.'));
  else if(m.radiation?.total>0&&m.radiation.diffuse/m.radiation.total>.7)details.push(c('光线以漫射为主，阴影边缘较柔和。','Diffuse light softens shadow edges.'));
 }else if(purpose==='horizon'){
  lead=m.visibility<8?c('远处细节和层次偏弱，近景轮廓更容易辨认。','Distant detail is muted; nearby outlines are easier to distinguish.'):
   c('远景具备一定清晰度，细节仍受云雾和地表热差影响。','Distant views have some definition, with detail affected by haze and surface heating.');
  if(Math.abs(m.surfaceDelta)>=6)details.push(c('地表与空气温差较大，长焦远景可能发虚。','Surface heating may soften distant telephoto detail.'));
  if(rain)details.push(c('降水会进一步削弱远处景物的对比度。','Rain can further reduce distant contrast.'));
  if(dew)details.push(c('近地空气潮湿，冷镜片也可能结露。','Humid surface air may leave dew on cool optics.'));
 }else if(purpose==='camp'){
  lead=rain?c('露天环境有雨，帐篷外表和暴露地面会被打湿。','Rain can wet tent exteriors and exposed ground.'):
   dew?c('近地空气潮湿，冷帐篷面和器材表面可能结露。','Humid surface air may leave dew on cool tents and equipment.'):
   c('近地环境尚未饱和，夜间变化仍需看降温和湿度。','Surface air is below saturation; later cooling and humidity still matter.');
  if(end.temperature<m.temperature-1.5)details.push(c('后段气温走低，停留时会逐渐感觉更凉。','Temperatures fall later, making a long stay feel cooler.'));
  if(gust)details.push(c('阵风会让帐布和轻物持续受扰动。','Gusts can move tent fabric and light objects.'));
 }
 return lead?[lead,...details]:null;
}
function planningInterpretationText(parts,language){
 const en=language==='en',limit=en?180:84;let text=parts[0];
 for(const detail of parts.slice(1)){const next=text+(en?' ':'')+detail;if([...next].length<=limit)text=next;}
 return text;
}
export function planningAdvice(view,language='zh'){
 const purposeText=planningPurposeInterpretation(view,language);
 if(purposeText)return planningInterpretationText(purposeText,language);
 const {m,end}=view,en=language==='en',c=(zh,english)=>en?english:zh,details=[];
 if(m.dewSpread<3&&m.humidity>=80)details.push(c('近地空气接近饱和，冷表面可能结露。','Air is near saturation; cool surfaces may collect dew.'));
 if(m.wetBulb>=25||(m.temperature>=28&&m.humidity>=70))details.push(c('高湿度让汗液不易蒸发，体感比气温更闷。','High humidity slows evaporation, making the air feel muggy.'));
 if(m.pwv>40&&m.visibility<10)details.push(c('整层水汽较多，远处景物层次偏弱。','Moist air reduces the definition of distant scenery.'));
 else if((m.sun?.elevation??0)<-6&&m.low>85)details.push(c('低云遮挡较多，夜空目标不易辨认。','Low cloud obscures much of the night sky.'));
 else if(m.radiation?.total>0&&m.radiation.diffuse/m.radiation.total>.7)details.push(c('日光以漫射为主，阴影边缘较柔和。','Diffuse light softens the edges of shadows.'));
 else if(Math.abs(m.surfaceDelta)>=6)details.push(c('地表与空气温差大，长焦远景可能发虚。','Surface heating may soften distant telephoto detail.'));
 if(details.length<2&&end.temperature<m.temperature-1.5&&end.temperature>=m.temperature-3)details.push(c('时段后段还会降温，体感比眼下更凉。','The later part of this window is cooler.'));
 if(m.windGust>=35)details.push(c('阵风会让枝叶、轻物和拍摄画面更易晃动。','Gusts can shake foliage, light objects and camera framing.'));
 if(details.length<3&&m.dewSpread>=8&&m.humidity<60)details.push(c('空气离饱和较远，冷表面需要降温更多才会结露。','Air is farther from saturation; surfaces need more cooling before dew forms.'));
 if(!details.length){const cloud=Math.max(m.low??0,m.mid??0,m.high??0);details.push(cloud>80?c('云层遮挡较多，天空开阔程度有限。','Clouds obscure much of the sky.'):cloud<30?c('云层遮挡较少，天空比较开阔。','Cloud cover leaves much of the sky open.'):c('天空有云，明暗会受到云层遮挡影响。','Partial cloud cover changes the light reaching the ground.'));}
 return planningInterpretationText([planningInterpretationLead(view,language),...details],language);
}
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
      if(endHeight!==undefined&&!endEvent)return {day,start:now,end:Math.min(1560,now+120),nextDay:false,available:false};
      if(endEvent){const q=skyLocalParts(endEvent.at,tz);end=+q.hour*60+ +q.minute;if(end<start)end+=1440;}else end=start+120;
      if(date!==day.date&&day.nextSkyDay?.date!==date){
        const thresholds=Object.fromEntries(Object.keys(day.thresholds).map(height=>[height,solarCrossings(date,tz,day.coordinates,+height)]));
        day.nextSkyDay={...day,date,thresholds,cache:new Map(),nextSkyDay:null};
      }
      return {day:date===day.date?day:day.nextSkyDay,start,end:Math.min(1560,end),nextDay,available:true};
    }
    return {day,start,end,nextDay:false,available:false};
  }
  return {day,start,end,nextDay,available:true};
}
export function makePlanningView(day,purpose,language='zh'){
  if(!SKY_PURPOSES.some(p=>p[0]===purpose))purpose='commute';
  const en=language==='en',photo=['sunset','dawn','blue'].includes(purpose),night=['stars','camp'].includes(purpose),air=['drying','gear'].includes(purpose);
  const window=planningWindow(day,purpose),at=minute=>({...skyAt(window.day,minute),...instrumentEnvironment(window.day,minute)}),m=at(window.start),end=at(window.end),samples=Array.from({length:25},(_,i)=>at(window.start+(window.end-window.start)*i/24));
  const lightStage=instrumentLightStage(day,purpose,window),rainSamples=Array.from({length:9},(_,i)=>instrumentEnvironment(window.day,window.start+i*15).rainRate);
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
  const metrics={photo,night,air};
  const trendKey=photo?'glow':purpose==='horizon'?'visibility':air?'humidity':purpose==='camp'||purpose==='outing'?'temperature':'precipitation';
  const barKey=photo?'low':purpose==='stars'?'high':air?'humidity':'precipitation';
  return {purpose,m,end,samples,window,lightStage,rainSamples,primary,secondary,metrics,trendKey,barKey,
    interval:time(m.epoch)+'–'+time(end.epoch),dayLabel:window.nextDay?c('明日','TOMORROW'):c('今日','TODAY'),
    trend:samples.map(v=>v[trendKey]),barValue:planningClamp(m[barKey],0,100),
    preset:day.preset,location:en?day.weather.cityEn:day.weather.cityZh};
}
