import { WEATHER_PRESETS, resolveWeatherScene, resolveTimeContext } from './weather-art.js';
import { FineWeatherRenderer } from './weather-shader.js';
import { formatScreenWeather, airQualityStatus } from './open-meteo.js';
import { LANGUAGES, labelPair, conditionPair, uvText, sceneName, localWindow } from './i18n.js';
import { resolveWeatherIcon, weatherIconMarkup } from './weather-icons.js';
import { formatUnitWeather, WIND_UNITS } from './weather-units.js';
import { SettingsController } from './settings.js';
import { APP_META } from './app-meta.js';

const rays = document.querySelector('#sun-rays');
for(let i=0;i<=12;i++){
  const angle=Math.PI+i*Math.PI/12;
  const point=r=>[86+Math.cos(angle)*r,98+Math.sin(angle)*r].join(' ');
  const path=document.createElementNS('http://www.w3.org/2000/svg','path');
  path.setAttribute('d','M'+point(29)+'L'+point(65)+'M'+point(73)+'L'+point(80));rays.append(path);
}
const rootElement=document.documentElement;
const appParams=new URL(location.href).searchParams;
const embedded=parent!==window;
let preferences={};
if(!embedded){try{preferences=JSON.parse(localStorage.getItem('sundown.settings.v1')??'{}')??{};}catch{preferences={};}}
let presetKey=appParams.get('weather')??rootElement.dataset.preset;
if(!Object.hasOwn(WEATHER_PRESETS,presetKey))presetKey='partly';
let language=appParams.get('lang')??preferences.language??rootElement.dataset.language;
if(!LANGUAGES.includes(language))language='zh';
let weather=WEATHER_PRESETS[presetKey];
let pixelStyle=appParams.get('pixels')??preferences.pixelStyle??'fine';
if(!['fine','block'].includes(pixelStyle))pixelStyle='fine';
let motionEnabled=appParams.has('motion')?appParams.get('motion')!=='off':preferences.motionEnabled!==false;
let temperatureUnit=(appParams.get('temperature')??preferences.temperatureUnit)==='f'?'f':'c';
let windUnit=appParams.get('wind')??preferences.windUnit??'kmh';
if(!['kmh','ms','mph'].includes(windUnit))windUnit='kmh';
let galleryPlaying=true;
const renderer=new FineWeatherRenderer(document.querySelector('#weather-pixels'),weather,{pixelStyle});
renderer.setAnimated(motionEnabled);
function rememberSelection(){
  if(!['http:','https:'].includes(location.protocol))return;
  const next=new URL(location.href);next.searchParams.set('weather',presetKey);next.searchParams.set('lang',language);next.searchParams.set('pixels',pixelStyle);next.searchParams.set('motion',motionEnabled?'on':'off');next.searchParams.set('temperature',temperatureUnit);next.searchParams.set('wind',windUnit);history.replaceState(null,'',next);
}
function gauge(selector,value,maximum,label){
  const element=document.querySelector(selector);element.hidden=!Number.isFinite(value);element.setAttribute('aria-label',label);
  if(!element.hidden)element.querySelector('i').style.top=(95-Math.max(0,Math.min(maximum,value))/maximum*90)+'%';
}
function savePreferences(){
  if(!embedded){try{localStorage.setItem('sundown.settings.v1',JSON.stringify({language,pixelStyle,motionEnabled,temperatureUnit,windUnit}));}catch{/* Private storage can be unavailable in previews. */}}
  rememberSelection();
}
const settingsController=new SettingsController({
  getState:()=>({language,pixelStyle,motionEnabled,temperatureUnit,windUnit,weather,presetKey}),
  change:(key,value)=>{
    if(key==='language'){setLanguage(value);return;}
    if(key==='pixelStyle'){pixelStyle=value;renderer.setPixelStyle(value);}
    if(key==='motionEnabled'){motionEnabled=value;renderer.setAnimated(value&&galleryPlaying);}
    if(key==='temperatureUnit')temperatureUnit=value;
    if(key==='windUnit')windUnit=value;
    renderScreen();savePreferences();
  },
  selectScene:key=>{if(!Object.hasOwn(WEATHER_PRESETS,key))return;presetKey=key;weather=WEATHER_PRESETS[key];renderer.setWeather(weather);renderScreen();rememberSelection();}
});
function renderScreen(){
  rootElement.dataset.language=language;rootElement.lang=language==='en'?'en':'zh-CN';
  const english=language==='en',time=resolveTimeContext(weather),display=formatScreenWeather(formatUnitWeather(weather,{temperatureUnit,windUnit}));
  document.title=english?APP_META.name+' — Weather':APP_META.name+' · '+APP_META.nameZh;
  display.uvLabel=uvText(weather.uvLabel,language);display.precipitationWindow=localWindow(weather.precipitationWindow,language);
  document.querySelector('#solar-event-label').dataset.label=time.event??'unknown';
  for(const field of document.querySelectorAll('[data-field]'))if(display[field.dataset.field]!==undefined)field.textContent=display[field.dataset.field];
  for(const label of document.querySelectorAll('[data-label]')){
    const copy=labelPair(label.dataset.label,language);label.querySelector('.label-primary').textContent=copy.primary;
    const secondary=label.querySelector('.label-secondary');if(secondary){secondary.textContent=copy.secondary;secondary.hidden=!copy.secondary;}
  }
  const condition=conditionPair(weather.condition,language);
  document.querySelector('#condition-primary').textContent=condition.primary;
  const secondary=document.querySelector('#condition-secondary');secondary.textContent=condition.secondary;secondary.hidden=!condition.secondary;
  const iconKey=resolveWeatherIcon(weather),conditionIcon=document.querySelector('#condition-icon');conditionIcon.innerHTML=weatherIconMarkup(iconKey);conditionIcon.dataset.icon=iconKey;
  for(const unit of document.querySelectorAll('[data-unit-for]'))unit.hidden=!Number.isFinite(weather[unit.dataset.unitFor]);
  document.querySelector('#temperature-range').setAttribute('aria-label',english?'Today high '+display.temperatureMax+'°, low '+display.temperatureMin+'°':'今日最高 '+display.temperatureMax+'°，最低 '+display.temperatureMin+'°');
  document.querySelector('#location-label').textContent=(english?weather.cityEn:language==='dual'?weather.cityZh+' '+weather.cityEn:weather.cityZh)+(english?' · SAMPLE':' · 示例');
  const [year,month,day]=time.date.split('-');
  const dateLabel=document.querySelector('#scene-date');dateLabel.dateTime=time.date;dateLabel.textContent=english?day+'/'+month+'/'+year:month+'月'+day+'日';
  const clock=document.querySelector('#scene-clock');clock.dateTime=new Date(weather.validAt*1000).toISOString();clock.textContent=time.clock24;
  clock.setAttribute('aria-label',english?'Sample local time '+time.clock24:'示例当地时间 '+time.clock24);
  let eventTime='—',period='';
  if(Number.isFinite(time.eventAt)){
    const parts=Object.fromEntries(new Intl.DateTimeFormat('en-US',{timeZone:weather.timezone,hour:'numeric',minute:'2-digit',hourCycle:english?'h12':'h23',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date(time.eventAt*1000)).map(part=>[part.type,part.value]));
    eventTime=(english?parts.hour:parts.hour.padStart(2,'0'))+':'+parts.minute;period=english?parts.dayPeriod.toLowerCase():'';
    const tomorrow=[parts.year,parts.month,parts.day].join('-')!==time.date;
    document.querySelector('#solar-caption').textContent=tomorrow?(english?'TOMORROW':language==='dual'?'明日 / TOMORROW':'明日'):'';
  }else document.querySelector('#solar-caption').textContent=english?'SOLAR DATA UNAVAILABLE':'日出日落数据暂缺';
  document.querySelector('#solar-event-time').textContent=eventTime;document.querySelector('#solar-period').textContent=period;
  const solar=document.querySelector('#solar-icon');solar.dataset.event=time.event??'unavailable';
  solar.setAttribute('aria-label',(english?time.event==='sunrise'?'Next sunrise ':'Sunset ':time.event==='sunrise'?'下次日出 ':'日落 ')+eventTime);
  document.querySelector('#solar-caption').hidden=!document.querySelector('#solar-caption').textContent;
  settingsController.render();
  document.querySelector('#wind-unit-label').textContent=WIND_UNITS[windUnit].label;
  document.querySelector('[data-temperature-scale]').textContent=temperatureUnit.toUpperCase();
  document.querySelector('.current-temperature').dataset.long=String(display.temperature.length>=3);
  const aq=airQualityStatus(weather.airQuality),aqElement=document.querySelector('#air-quality');aqElement.hidden=!aq;
  if(aq){aqElement.style.setProperty('--aq-color',aq.color);aqElement.dataset.warning=String(aq.warning);aqElement.textContent=aq.warning?'US AQI '+aq.value:'';aqElement.setAttribute('aria-label','US AQI '+aq.value+', '+(english?aq.en:aq.zh));aqElement.title=aqElement.getAttribute('aria-label');}
  document.querySelector('.current-temperature').setAttribute('aria-label',(english?'Temperature ':'当前温度 ')+display.temperature+(temperatureUnit==='f'?(english?' degrees Fahrenheit':' 华氏度'):(english?' degrees Celsius':' 摄氏度')));
  document.querySelector('.weather-page').setAttribute('aria-label',english?'Weather preview with sample data':'天气首页，示例天气数据');
  document.querySelector('.primary-metrics').setAttribute('aria-label',english?'Feels-like temperature and wind':'体感与风速');
  document.querySelector('.conditions-grid').setAttribute('aria-label',english?'Weather conditions':'环境指标');
  const cover=document.querySelector('.cover-track');cover.setAttribute('aria-label',english?'Cloud cover':'云量');
  if(Number.isFinite(weather.cloudCover)){cover.setAttribute('aria-valuenow',weather.cloudCover);cover.removeAttribute('aria-valuetext');}
  else{cover.removeAttribute('aria-valuenow');cover.setAttribute('aria-valuetext',english?'Unavailable':'暂无数据');}
  document.querySelector('.cover-fill').style.width=(weather.cloudCover??0)+'%';
  gauge('.gauge-uv',weather.uvIndex,11,(english?'UV index ':'紫外线指数 ')+display.uvIndex+', '+display.uvLabel);
  gauge('.gauge-humidity',weather.humidity,100,(english?'Humidity ':'湿度 ')+display.humidity+'%');
  gauge('.gauge-precipitation',weather.precipitationProbability,100,display.precipitationWindow+(english?' precipitation chance ':' 降水概率 ')+display.precipitationProbability+'%');
  gauge('.gauge-visibility',weather.visibility,50,(english?'Visibility ':'能见度 ')+display.visibility+' km');
  const scene=resolveWeatherScene(weather);document.querySelector('.weather-page').dataset.weather=scene;
  document.querySelector('.weather-artwork').setAttribute('aria-label',sceneName(scene,language)+(english?(pixelStyle==='fine'?' animated fine pixel weather':' animated block pixel weather'):(pixelStyle==='fine'?'精细像素天气动画':'大块像素天气动画')));
  document.querySelector('.visual-panel').setAttribute('aria-label',english?'Pixel weather artwork':'像素天气画面');

}
function setLanguage(next){if(!LANGUAGES.includes(next))return;language=next;renderScreen();savePreferences();}
window.addEventListener('message',event=>{if(event.source!==parent||parent===window||!event.data)return;if(event.data.type==='sundown-language')setLanguage(event.data.language);if(event.data.type==='sundown-motion'&&typeof event.data.playing==='boolean'){galleryPlaying=event.data.playing;renderer.setAnimated(motionEnabled&&galleryPlaying);}});
renderScreen();window.addEventListener('pagehide',()=>renderer.destroy(),{once:true});
