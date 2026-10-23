// Every metric has one owner on the page. Related values and their trend stay
// together; selecting a purpose replaces a family rather than duplicating it.
const INSTRUMENT_FIELDS=Object.freeze({
 precipitation:['precipitation'],wind:['windSpeed','windGust','windFrom'],pressure:['pressure','pressureTrend'],
 dew:['dewSpread','dewPoint'],temperature:['temperature'],apparent:['feelsLike'],humidity:['humidity'],
 visibility:['visibility'],clouds:['high','mid','low'],solar:['solarElevation'],bearing:['solarAzimuth'],
 light:['colorTemperature'],airQuality:['pm25'],moon:['moonIllumination','moonAge'],glow:['glow'],
 stage:['lightStage'],lunar:['moonIllumination','moonAge','moonRise','moonSet','bortle'],darkness:['bortle','moonRise','moonSet'],
 drift:['upperWind','windDifference'],column:['pwv'],freezing:['freezing'],cape:['cape'],soil:['soil'],radiation:['directHorizontal','diffuseHorizontal'],seeing:['surfaceDelta','surfaceTemperature'],rain:['rainRate'],cin:['cin'],lcl:['lcl'],vpd:['vpd'],wetBulb:['wetBulb'],cooling:['coolingRate']
});
export function instrumentPrimaryKind(purpose){
 if(['sunset','dawn','blue'].includes(purpose))return 'glow';
 return {cycling:'wind',outing:'temperature',camp:'temperature',stars:'moon',horizon:'visibility',drying:'humidity',gear:'humidity'}[purpose]??'precipitation';
}
export function composeInstrumentContent(view){
 const primary=instrumentPrimaryKind(view.purpose);
 const modules=[
  ['wind',primary==='wind'?'precipitation':'wind'],['pressure','pressure'],['dew','dew'],
  ['thermal',primary==='temperature'?'apparent':'temperature'],
  ['optical',view.metrics.photo&&Number.isFinite(view.m.colorTemperature)?'light':'airQuality'],
  ['moisture',primary==='humidity'?'precipitation':'humidity'],
  ['distance',primary==='visibility'?'apparent':'visibility'],['clouds','clouds'],['solar','solar'],['bearing','bearing'],
  ['stage','stage'],['lunar',primary==='moon'?'darkness':'lunar'],['drift','drift'],['column','column'],['freezing','freezing'],['cape','cape'],['soil','soil'],['radiation','radiation'],['seeing','seeing'],['rain','rain'],
  ['signal',view.preset==='storm'?'cin':'lcl'],['stability',view.preset==='storm'?'lcl':'cin'],['micro',view.metrics.air?'vpd':view.metrics.night?'cooling':'wetBulb']
 ].map(([slot,kind])=>({slot,kind,fields:INSTRUMENT_FIELDS[kind]}));
 const owners=new Map();
 for(const card of [{slot:'primary',kind:primary,fields:INSTRUMENT_FIELDS[primary]},...modules])for(const field of card.fields){
  if(owners.has(field))throw new Error('Duplicate instrument metric: '+field);
  owners.set(field,card.slot);
 }
 return {primary:{kind:primary,fields:INSTRUMENT_FIELDS[primary]},modules,owners:Object.fromEntries(owners)};
}
export function instrumentPriority(view,kind){
 const core=new Set(),secondary=new Set();
 if(view.metrics.photo){['stage','seeing','radiation'].forEach(k=>core.add(k));['clouds','column','drift','visibility','lcl','light'].forEach(k=>secondary.add(k));}
 else if(view.purpose==='stars'){['lunar','darkness'].forEach(k=>core.add(k));['stage','clouds','column','seeing','cooling'].forEach(k=>secondary.add(k));}
 else if(view.metrics.air){['dew','vpd'].forEach(k=>core.add(k));['wind','soil','radiation','column'].forEach(k=>secondary.add(k));}
 else if(view.purpose==='camp'){['soil','cooling'].forEach(k=>core.add(k));['dew','wind','freezing','rain','lunar'].forEach(k=>secondary.add(k));}
 else if(view.purpose==='horizon'){['seeing'].forEach(k=>core.add(k));['column','drift','lcl'].forEach(k=>secondary.add(k));}
 else{['wind','rain'].forEach(k=>core.add(k));['temperature','apparent','wetBulb','humidity'].forEach(k=>secondary.add(k));}
 if(view.preset==='storm'){core.add('cape');secondary.add('cin');secondary.add('pressure');}
 if(view.preset==='snow'){core.add('freezing');secondary.add('wetBulb');}
 return core.has(kind)?'core':secondary.has(kind)?'secondary':'support';
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
  case 'visibility':return {value:m.visibility};
  case 'clouds':return {layers:['high','mid','low'].map(field=>({field,value:m[field],ratio:limit(m[field]/100,0,1)}))};
  case 'solar':return {value:m.sun?.elevation??null,angle:(90-(m.sun?.elevation??0))*Math.PI/180};
  case 'bearing':{const value=m.sun?.azimuth??0,reference=(Math.round(value/90)*90)%360;return {value,reference,referenceOffset:((reference-value+540)%360)-180};}
  case 'moon':return {...m.moon,value:m.moon.illumination*100};
  case 'light':return {value:m.colorTemperature,ratio:limit((m.colorTemperature-2000)/8000,0,1)};
  case 'airQuality':return {value:m.pm25};
  case 'glow':return {value:m.glow,ratio:limit(m.glow/100,0,1)};
  case 'stage':return view.lightStage;
  case 'lunar':case 'darkness':return {...m.moon,value:m.moon.illumination*100,...m.moonEvents,bortle:m.bortle};
  case 'drift':return {...m.upper,difference:m.windDifference,surfaceFrom:m.windFrom,surfaceSpeed:m.windSpeed};
  case 'column':return {value:m.pwv,ratio:limit(m.pwv/70,0,1)};
  case 'freezing':return {value:m.freezing};
  case 'cape':return {value:m.cape,ratio:limit(m.cape/4000,0,1)};
  case 'soil':return {value:m.soil,ratio:limit(m.soil,0,1)};
  case 'radiation':return m.radiation;
  case 'seeing':return {value:m.surfaceDelta,surface:m.surfaceTemperature};
  case 'rain':return {value:m.rainRate,series:view.rainSamples,maximum:Math.max(1,...view.rainSamples)};
  case 'cin':return {value:m.cin};
  case 'lcl':return {value:m.lcl};
  case 'vpd':return {value:m.vpd};
  case 'wetBulb':return {value:m.wetBulb};
  case 'cooling':return {value:(view.end.temperature-m.temperature)/((view.end.epoch-m.epoch)/3600)};
  default:throw new Error('Unsupported instrument family: '+kind);
 }
}
