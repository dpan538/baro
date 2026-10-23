// Every metric has one owner on the page. Related values and their trend stay
// together; selecting a purpose replaces a family rather than duplicating it.
const INSTRUMENT_FIELDS=Object.freeze({
 precipitation:['precipitation'],wind:['windSpeed','windGust','windFrom'],pressure:['pressure','pressureTrend'],
 dew:['dewSpread','dewPoint'],temperature:['temperature'],apparent:['feelsLike'],humidity:['humidity'],
 visibility:['visibility'],clouds:['high','mid','low'],solar:['solarElevation'],bearing:['solarAzimuth'],
 light:['colorTemperature'],airQuality:['pm25'],moon:['moonIllumination','moonAge'],glow:['glow']
});
export function instrumentPrimaryKind(purpose){
 if(['sunset','dawn','blue'].includes(purpose))return 'glow';
 return {cycling:'wind',outing:'temperature',camp:'temperature',stars:'moon',horizon:'visibility',drying:'humidity',gear:'humidity'}[purpose]??'precipitation';
}
export function composeInstrumentContent(view){
 const primary=instrumentPrimaryKind(view.purpose),night=view.metrics.night;
 const modules=[
  ['wind',primary==='wind'?'precipitation':'wind'],['pressure','pressure'],['dew','dew'],
  ['thermal',primary==='temperature'?'apparent':'temperature'],
  ['optical',night&&primary!=='moon'?'moon':view.metrics.photo&&Number.isFinite(view.m.colorTemperature)?'light':'airQuality'],
  ['moisture',primary==='humidity'?'precipitation':'humidity'],
  ['distance',primary==='visibility'?'apparent':'visibility'],['clouds','clouds'],['solar','solar'],['bearing','bearing']
 ].map(([slot,kind])=>({slot,kind,fields:INSTRUMENT_FIELDS[kind]}));
 const owners=new Map();
 for(const card of [{slot:'primary',kind:primary,fields:INSTRUMENT_FIELDS[primary]},...modules])for(const field of card.fields){
  if(owners.has(field))throw new Error('Duplicate instrument metric: '+field);
  owners.set(field,card.slot);
 }
 return {primary:{kind:primary,fields:INSTRUMENT_FIELDS[primary]},modules,owners:Object.fromEntries(owners)};
}
export function instrumentGraphicData(kind,view){
 const m=view.m,samples=view.samples,limit=(v,a,b)=>Math.max(a,Math.min(b,v));
 switch(kind){
  case 'wind':return {mean:m.windSpeed,gust:m.windGust,from:m.windFrom,angle:m.windFrom*Math.PI/180};
  case 'pressure':return {start:m.pressure-m.pressureTrend,end:m.pressure,delta:m.pressureTrend};
  case 'dew':return {value:m.dewSpread,dewPoint:m.dewPoint,series:samples.map(p=>p.dewSpread),threshold:2,maximum:Math.max(3,Math.ceil(Math.max(...samples.map(p=>p.dewSpread))))};
  case 'temperature':return {value:m.temperature,low:Math.min(...samples.map(p=>p.temperature)),high:Math.max(...samples.map(p=>p.temperature))};
  case 'apparent':return {value:m.feelsLike};
  case 'humidity':return {value:m.humidity,ratio:limit(m.humidity/100,0,1)};
  case 'precipitation':return {value:m.precipitation,series:samples.map(p=>limit(p.precipitation/100,0,1))};
  case 'visibility':return {value:m.visibility,markers:[1,5,10,25,50].map(km=>({km,visible:km<=m.visibility}))};
  case 'clouds':return {layers:['high','mid','low'].map(field=>({field,value:m[field],ratio:limit(m[field]/100,0,1)}))};
  case 'solar':return {value:m.sun?.elevation??null,angle:(90-(m.sun?.elevation??0))*Math.PI/180};
  case 'bearing':{const value=m.sun?.azimuth??0,reference=(Math.round(value/90)*90)%360;return {value,reference,referenceOffset:((reference-value+540)%360)-180};}
  case 'moon':return {...m.moon,value:m.moon.illumination*100};
  case 'light':return {value:m.colorTemperature,ratio:limit((m.colorTemperature-2000)/8000,0,1)};
  case 'airQuality':return {value:m.pm25};
  case 'glow':return {value:m.glow,ratio:limit(m.glow/100,0,1)};
  default:throw new Error('Unsupported instrument family: '+kind);
 }
}
