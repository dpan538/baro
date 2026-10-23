import { makeSkyDay, skyDirection, skyClock } from './sky-model.js';
import { SKY_PURPOSES, makePlanningView } from './planning-model.js';
import { composeInstrumentContent, instrumentGraphicData, instrumentPriority } from './instrument-content.js';
import { InstrumentRenderer } from './instrument-art.js';
import { WIND_UNITS } from './weather-units.js';

const insN=(v,n=0)=>{if(v===null||v===undefined||!Number.isFinite(+v))return '—';const fixed=(+v).toFixed(n);return +fixed===0?(0).toFixed(n):fixed;};
const insEscape=v=>String(v).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const insCountdown=v=>Number.isFinite(v)?[Math.floor(v/3600),Math.floor(v%3600/60),Math.floor(v%60)].map(x=>String(x).padStart(2,'0')).join(':'):'—';
const insSvg=(body,view='0 0 300 120')=>`<svg aria-hidden="true" viewBox="${view}">${body}</svg>`;
const insLabel={
 precipitation:['降水概率','Precipitation'],wind:['风速','Wind'],pressure:['气压','Pressure'],dew:['露点差','Dew-point spread'],
 temperature:['气温','Air temperature'],apparent:['体感温度','Feels like'],humidity:['相对湿度','Humidity'],visibility:['能见度','Visibility'],
 clouds:['云层分布','Cloud layers'],solar:['太阳高度角','Solar elevation'],bearing:['太阳方位','Solar azimuth'],
 cin:['对流抑制 CIN','CIN'],lcl:['凝结高度 ≈','LCL height ≈'],vpd:['水汽压差 VPD','Vapour deficit'],wetBulb:['湿球温度 ≈','Wet bulb ≈'],cooling:['时段温变','Temperature trend'],stage:['光影倒计时','Light countdown'],lunar:['月相 ≈','Moon phase ≈'],darkness:['暗夜 / 月升落','Dark sky / Moon'],drift:['高空风 / 风差','Upper wind / Δv'],column:['整层水汽 PWV','Column water'],freezing:['零度层高度','Freezing level'],cape:['对流位能 CAPE','CAPE'],soil:['表层土壤水分','Surface soil water'],radiation:['直射 / 漫射','Direct / diffuse'],seeing:['地表温差','Surface contrast'],rain:['短时降水','Rain trend'],
 light:['参考色温 ≈','Palette CCT ≈'],airQuality:['PM2.5','PM2.5'],moon:['月面照明 ≈','Moon illumination ≈'],glow:['霞光条件指数','Glow condition score']
};
function insMoonGraphic(g){
 const r=46,cx=150,cy=58,k=-Math.cos(g.fraction*2*Math.PI),waxing=g.fraction<.5;let d='';
 for(let i=0;i<=50;i++){const y=-r+i*2*r/50,x=Math.sqrt(Math.max(0,r*r-y*y));d+=(i?'L':'M')+(cx+(waxing?x:-x))+' '+(cy+y);}
 for(let i=50;i>=0;i--){const y=-r+i*2*r/50,x=Math.sqrt(Math.max(0,r*r-y*y));d+='L'+(cx+(waxing?-k:k)*x)+' '+(cy+y);}
 return insSvg(`<circle cx="150" cy="58" r="47" fill="#3f494b"/><path d="${d}Z" fill="#eee1b7"/>`,'96 3 108 108');
}
function insMetric(kind,v,s,primary=false){
 const en=s.language==='en',c=(zh,english)=>en?english:zh,g=instrumentGraphicData(kind,v),n=insN;
 const temp=x=>Number.isFinite(x)?n(s.temperatureUnit==='f'?x*1.8+32:x,1):'—',delta=x=>Number.isFinite(x)?n(s.temperatureUnit==='f'?x*1.8:x,1):'—',tu=s.temperatureUnit==='f'?'°F':'°C';
 const wu=WIND_UNITS[s.windUnit]??WIND_UNITS.kmh,wind=x=>n(x/wu.divisor,s.windUnit==='ms'?1:0);
 let label=insLabel[kind][en?1:0],value=g.value,unit='',graphic='',detail='',note='';
 if(kind==='wind'){
  label=primary?c('阵风','Wind gust'):label;value=wind(primary?g.gust:g.mean);unit=wu.label;
  detail=`<span>${primary?c('风速','Mean'):c('阵风','Gust')} ${wind(primary?g.mean:g.gust)} ${wu.label}</span><span>${c('来向','From')} ${n(g.from)}°</span>`;
  const x=40+Math.sin(g.angle)*25,y=40-Math.cos(g.angle)*25;
  graphic=insSvg(`<circle cx="40" cy="40" r="30" fill="none" stroke="currentColor" opacity=".25"/><path d="M40 5V12M40 68V75M5 40H12M68 40H75" stroke="currentColor"/><path d="M40 40L${x} ${y}" stroke="currentColor" stroke-width="3"/><path d="M0 -6L-4 6H4Z" transform="translate(${x} ${y}) rotate(${g.from})" fill="currentColor"/>`,'0 0 80 80');
 }else if(kind==='pressure'){
  value=n(g.end,1);unit='hPa';detail=`${g.delta>0?'+':''}${n(g.delta,1)} hPa / 3h`;

 }else if(kind==='dew'){
  value=delta(g.value);unit=tu;detail=`${c('露点','Dew point')} ${temp(g.dewPoint)}${tu}`;
  const coords=g.series.map((x,i)=>`${i?'L':'M'}${8+i*284/(g.series.length-1)} ${107-x/g.maximum*92}`).join(''),threshold=107-g.threshold/g.maximum*92;
  graphic=insSvg(`<path d="M8 ${threshold}H292" stroke="currentColor" stroke-dasharray="3 5" opacity=".35"/><path d="${coords}L292 112H8Z" fill="#ed7c55" opacity=".22"/><path d="${coords}" fill="none" stroke="#ed7c55" stroke-width="3"/>`)+`<span class="ins-dew-threshold" style="top:${threshold/120*100}%">${delta(g.threshold)}${tu}</span>`;
 }else if(kind==='temperature'){
  value=temp(g.value);unit=tu;
  const low=g.low-1,high=g.high+1,p=(g.value-low)/(high-low)*100;
  graphic=`<div class="ins-thermal-bar"><i style="left:${p}%"></i></div><div class="ins-thermal-ends"><span>${c('低','L')} ${temp(g.low)}°</span><span>${c('高','H')} ${temp(g.high)}°</span></div>`;
 }else if(kind==='apparent'){value=temp(g.value);unit=tu;
 }else if(kind==='humidity'){
  value=n(g.value);unit='%';graphic=`<div class="ins-humidity-bars">${Array.from({length:28},(_,i)=>`<i class="${i/28<g.ratio?'active':''}"></i>`).join('')}</div>`;
 }else if(kind==='precipitation'){
  value=n(g.value);unit='%';let steps='M4 '+(108-g.series[0]*91);
  g.series.forEach((p,i)=>{steps+='H'+(4+i*292/(g.series.length-1))+'V'+(108-p*91);});
  graphic=insSvg(`<path d="M4 108H296M4 17H296" stroke="currentColor" opacity=".16"/><path d="${steps}V108H4Z" fill="currentColor" opacity=".45"/><path d="${steps}" fill="none" stroke="currentColor" stroke-width="3"/>`);
 }else if(kind==='visibility'){value=n(g.value,1);unit='km';
 }else if(kind==='clouds'){
 return `<h2>${label}</h2><div class="ins-cloud-columns">${g.layers.map((p,i)=>`<div><span>${(en?['High','Mid','Low']:['高云','中云','低云'])[i]}</span><div class="ins-cloud-track"><i style="width:${p.ratio*100}%"></i></div><strong data-field="${p.field}">${n(p.value)}<small>%</small></strong></div>`).join('')}</div>`;
 }else if(kind==='solar'){
  value=n(g.value,1);unit='°';let ticks='';for(let i=0;i<=18;i++){const a=i/18*Math.PI,x=Math.cos(a),y=Math.sin(a);ticks+=`<path d="M${150+x*103} ${116-y*103}L${150+x*(i%3?112:118)} ${116-y*(i%3?112:118)}"/>`;}
  graphic=insSvg(`<g stroke="currentColor" stroke-width="2">${ticks}</g><path d="M150 116L${150+Math.cos(g.angle)*96} ${116-Math.sin(g.angle)*96}" stroke="#ed7c55" stroke-width="4"/><circle cx="150" cy="116" r="5" fill="currentColor"/><text x="15" y="139" fill="currentColor" font-size="14">−90</text><text x="150" y="-7" text-anchor="middle" fill="currentColor" font-size="18">0</text><text x="263" y="139" fill="currentColor" font-size="14">90</text>`,'0 -24 300 170');
 }else if(kind==='bearing'){
  value=n(g.value);unit='°';detail=skyDirection(g.value,s.language);let ticks='';
  for(let i=-6;i<=6;i++){const a=(Math.round(g.value/10)*10+i*10-g.value)*Math.PI/180,x=280-110*Math.cos(a),y=72+110*Math.sin(a),xx=280-(i%3?119:127)*Math.cos(a),yy=72+(i%3?119:127)*Math.sin(a);ticks+=`<path d="M${x} ${y}L${xx} ${yy}"/>`;}
  const refAngle=g.referenceOffset*Math.PI/180,refName=(en?['N','E','S','W']:['北','东','南','西'])[Math.round(g.reference/90)%4];
  graphic=insSvg(`<circle cx="280" cy="72" r="125" fill="#ed7c55"/><g stroke="#171411" stroke-width="2">${ticks}</g><path d="M141 72H181" stroke="#171411" stroke-width="4"/><text x="${280-93*Math.cos(refAngle)}" y="${83+93*Math.sin(refAngle)}" text-anchor="middle" fill="#171411" font-size="29">${refName}</text>`,'0 0 300 145');
 }else if(kind==='moon'){value=n(g.value);unit='%';note=c('朔望月近似','Mean lunation');graphic=insMoonGraphic(g);
 }else if(kind==='light'){value=n(g.value);unit='K';graphic=`<div class="ins-light-swatches"><i></i><i></i><i></i><i></i><i></i><i></i><b style="left:${g.ratio*100}%"></b></div>`;
 }else if(kind==='airQuality'){value=n(g.value);unit='µg/m³';
 }else if(kind==='glow'){
  value=n(g.value);unit='/100';note=c('规则指数 · 非概率','Heuristic · not probability');
  graphic=insSvg(Array.from({length:20},(_,i)=>`<path d="M${i*15+1} 95L${i*15+12} 20" stroke="currentColor" stroke-width="6" opacity="${i/20<g.ratio?1:.15}"/>`).join(''));
 }
 if(kind==='stage'){
  const stageNames={golden:c('黄金时刻','Golden hour'),sunset:c('晚霞窗口','Sunset light'),dawn:c('晨光窗口','Dawn light'),blue:c('蓝调时刻','Blue hour')};
  return `<h2>${label}</h2><div class="ins-stage-name"><span>${stageNames[g.key]??c('无对应窗口','No crossing')}</span><b>${skyClock(g.at,v.window.day.weather.timezone)}</b></div><strong class="ins-countdown" data-countdown-seconds="${g.seconds??''}">${insCountdown(g.seconds)}</strong><small>${g.active?c('距结束 ≈','Until end ≈'):c('距开始 ≈','Until start ≈')}</small>`;
 }
 if(kind==='lunar'||kind==='darkness'){
  const phases=en?['New','Waxing crescent','First quarter','Waxing gibbous','Full','Waning gibbous','Last quarter','Waning crescent']:['新月','蛾眉月','上弦月','盈凸月','满月','亏凸月','下弦月','残月'];
  const phase=phases[Math.round(g.fraction*8)%8],tz=v.window.day.weather.timezone;
  return `<h2>${label}</h2>${kind==='lunar'?`<div class="ins-lunar-line"><strong>${n(g.value)}<small>%</small></strong>${insMoonGraphic(g)}<span>${phase}</span></div>`:`<div class="ins-bortle">Bortle <strong>—</strong><small>${c('测光未接入','Unmeasured')}</small></div>`}<div class="ins-moon-times"><span>↑ ${skyClock(g.rise,tz)} ≈</span><span>↓ ${skyClock(g.set,tz)} ≈</span></div>${kind==='lunar'?'<small>Bortle —</small>':''}`;
 }
 if(kind==='drift'){
  value=wind(g.speed);unit=wu.label;detail=`300 hPa · ${n(g.from)}° · Δv ${wind(g.difference)} ${wu.label}`;
  const vectorScale=Math.max(g.speed,g.surfaceSpeed,1),upperLength=Math.max(4,24*g.speed/vectorScale),surfaceLength=Math.max(4,24*g.surfaceSpeed/vectorScale);
  graphic=insSvg(`<path d="M8 25H110M8 75H110" stroke="currentColor" opacity=".2"/><g transform="translate(57 25) rotate(${g.from+180})"><path d="M0 ${upperLength}V-${upperLength}L-6 -12M0 -${upperLength}L6 -12" fill="none" stroke="#ed7c55" stroke-width="3"/></g><g transform="translate(57 75) rotate(${g.surfaceFrom+180})"><path d="M0 ${surfaceLength}V-${surfaceLength}L-5 ${-surfaceLength+8}M0 -${surfaceLength}L5 ${-surfaceLength+8}" fill="none" stroke="currentColor" stroke-width="2"/></g>`,'0 0 120 100');
 }
 if(kind==='column') {value=n(g.value,1);unit='mm';graphic=`<div class="ins-water-scale"><i style="width:${g.ratio*100}%"></i><b></b><b></b></div>`;detail=c('整层积分','Full atmospheric column');}
 if(kind==='freezing'){value=n(g.value);unit='m';detail=c('海平面以上','Above sea level');}
 if(kind==='cape'){value=n(g.value);unit='J/kg';graphic=`<div class="ins-energy-scale"><i style="left:${g.ratio*100}%"></i></div>`;detail=c('能量 · 非概率','Energy · not probability');}
 if(kind==='soil'){value=n(g.value,2);unit='m³/m³';detail=c('0–1 cm · 体积含水率','0–1 cm · volume fraction');graphic=`<div class="ins-soil-cells">${Array.from({length:20},(_,i)=>`<i class="${i/20<g.ratio?'active':''}"></i>`).join('')}</div>`;}
 if(kind==='radiation'){
  return `<h2>${label}<small class="ins-flux-state">${g.total===0?c('夜间','Night'):g.ratio===null?'—':n(g.ratio,1)+':1'}</small></h2><div class="ins-flux-values"><strong>${n(g.direct)}<small> / </small>${n(g.diffuse)}</strong><span>W/m²<small>${c('水平面','Horizontal')}</small></span></div><div class="ins-flux-bars"><div><i style="width:${Math.max(0,g.direct/1100*100)}%"></i></div><div><i style="width:${Math.max(0,g.diffuse/1100*100)}%"></i></div></div>`;
 }
 if(kind==='seeing'){
  value=(g.value>0?'+':'')+delta(g.value);unit=tu;detail=`${c('地表','Skin')} ${temp(g.surface)}${tu} · ${c('热差代理','proxy')}`;
  const p=Math.min(50,Math.abs(g.value)/15*50);graphic=`<div class="ins-contrast-scale"><b></b><i style="left:${g.value<0?50-p:50}%;width:${p}%"></i></div>`;
 }
 if(kind==='rain'){
  return `<h2>${label}</h2><div class="ins-reading"><strong>${n(g.value,1)}</strong><span>mm/h</span></div><div class="ins-rain-bars">${g.series.map(x=>`<i style="height:${Math.max(2,x/g.maximum*100)}%"></i>`).join('')}</div><div class="ins-rain-keys"><span>+0</span><span>+60</span><span>+120 min</span></div><small>${c('15 分钟步长 · 示意','15-min steps · demo')}</small>`;
 }
 if(kind==='cin'){value=n(g.value);unit='J/kg';detail=c('抑制能量 · 示例','Inhibition · demo');}
 if(kind==='lcl'){value=n(g.value);unit='m';detail=c('地面以上 · 近似','Above ground · approx.');}
 if(kind==='vpd'){value=n(g.value,2);unit='kPa';detail=c('近地水汽压差','Near-surface deficit');}
 if(kind==='wetBulb'){value=temp(g.value);unit=tu;detail=c('近地近似值','Surface approximation');}
 if(kind==='cooling'){value=(g.value>0?'+':'')+delta(g.value);unit=tu+'/h';detail=c('所选时段平均变化','Mean over selected window');}
 if(['precipitation','dew','glow'].includes(kind))graphic=graphic.replace('<svg ','<svg preserveAspectRatio="none" ');
 return `<h2>${label}</h2><div class="ins-reading"><strong data-field="${kind}">${value??'—'}</strong><span>${unit}</span></div>${detail?`<div class="ins-detail">${detail}</div>`:''}${graphic?`<div class="ins-graphic ins-graphic-${kind}">${graphic}</div>`:''}${note?`<div class="ins-note">${note}</div>`:''}`;
}
export class ObservatoryController{
 constructor({getState,renderer,onPage}){
  Object.assign(this,{getState,renderer,onPage});this.page=document.querySelector('#sky-page');this.home=document.querySelector('.weather-page');this.canvas=renderer.canvas;this.canvasHome=this.canvas.parentElement;this.index=0;this.active=false;
  this.page.innerHTML=`<div class="instrument-console">
   <figure class="ins-panorama ins-module"><div id="ins-pixel-host"></div><figcaption id="ins-panorama-caption"></figcaption><button id="ins-home" class="ins-home" type="button">›</button></figure>
   <article class="ins-focus ins-module" id="ins-focus"></article>
   <section class="ins-context ins-module" id="ins-context"></section>
   <div class="ins-signal-stack"><article class="ins-signal-top ins-module ins-cell" id="ins-signal"></article><article class="ins-cape-top ins-module ins-cell" id="ins-cape"></article></div>
   <article class="ins-rain-top ins-module ins-cell" id="ins-rain"></article>
   <div class="ins-assembly">${['wind','pressure','dew','thermal','optical','moisture','distance','clouds','solar','bearing','stage','lunar','drift','column','freezing','stability','soil','radiation','seeing','micro'].map(slot=>`<article class="ins-module ins-cell ins-slot-${slot}" id="ins-${slot}"></article>`).join('')}</div>
   <section class="ins-wheel ins-module" id="ins-wheel" role="listbox" tabindex="0"><svg aria-hidden="true" viewBox="0 0 720 574" preserveAspectRatio="xMidYMid slice" id="ins-wheel-svg"></svg><div id="ins-wheel-options" class="ins-live"></div><div class="ins-wheel-foot"><span id="ins-wheel-hint"></span><span id="ins-wheel-position"></span></div></section>
  </div><span class="ins-live" id="ins-live" aria-live="polite"></span>`;
  this.wheel=this.el('wheel');this.wheelResize=new ResizeObserver(()=>{if(this.active)this.renderWheel();});this.wheelResize.observe(this.wheel);document.querySelector('#observatory-open').addEventListener('click',()=>this.open());this.el('home').addEventListener('click',()=>this.close());
  this.wheel.addEventListener('keydown',e=>{if(['ArrowUp','ArrowDown','Home','End','ArrowLeft','ArrowRight'].includes(e.key)){e.preventDefault();this.select(e.key==='Home'?0:e.key==='End'?SKY_PURPOSES.length-1:this.index+(['ArrowDown','ArrowRight'].includes(e.key)?1:-1));}});
  this.wheel.addEventListener('wheel',e=>{e.preventDefault();const now=performance.now();if(now-(this.wheelAt??0)<110)return;this.wheelAt=now;this.select(this.index+(e.deltaY>0?1:-1));},{passive:false});
  this.wheel.addEventListener('pointerdown',e=>{if(e.button!==0)return;this.drag={y:e.clientY,position:this.position??this.index,moved:false,target:e.target.closest('[data-purpose-index]')?.dataset.purposeIndex};this.wheel.setPointerCapture(e.pointerId);this.wheel.classList.add('dragging');});
  this.wheel.addEventListener('pointermove',e=>{if(!this.drag)return;const dy=e.clientY-this.drag.y;if(Math.abs(dy)>7)this.drag.moved=true;const p=this.drag.position-dy/(this.wheel.clientHeight/9);this.position=p;const index=((Math.round(p)%SKY_PURPOSES.length)+SKY_PURPOSES.length)%SKY_PURPOSES.length;if(index!==this.index){this.index=index;this.render();}else this.renderWheel();});
  const finish=()=>{if(!this.drag)return;if(!this.drag.moved&&this.drag.target!==undefined)this.select(+this.drag.target);this.drag=null;this.position=this.index;this.renderWheel();this.wheel.classList.remove('dragging');};this.wheel.addEventListener('pointerup',finish);this.wheel.addEventListener('pointercancel',finish);
  let swipe=null;document.addEventListener('pointerdown',e=>{if(e.button!==0||e.target.closest('button,input,select,textarea,a,#ins-wheel'))return;swipe={x:e.clientX,y:e.clientY};});document.addEventListener('pointerup',e=>{if(!swipe)return;const dx=e.clientX-swipe.x,dy=e.clientY-swipe.y;swipe=null;if(Math.abs(dx)>100&&Math.abs(dx)>Math.abs(dy)*1.7){if(!this.active&&dx>0&&!document.querySelector('#settings-panel:not([hidden]),#scenes-panel:not([hidden])'))this.open();else if(this.active&&dx<0)this.close();}});
  document.addEventListener('keydown',e=>{if(e.key==='Escape'&&this.active){e.preventDefault();this.close();}});this.syncState();
 }
 el(id){return this.page.querySelector('#ins-'+id);}
 syncState(){const s=this.getState(),portal=document.querySelector('#observatory-open');portal.textContent=(s.language==='en'?'OBSERVATORY':'环境观测')+' ↗';portal.title=s.language==='en'?'Swipe right to open':'右滑进入';if(this.day?.weather!==s.weather)this.day=makeSkyDay(s.weather,s.presetKey);if(this.active)this.render();}
 open(){if(this.active)return;this.active=true;this.countdownStarted=performance.now();this.countdownTimer=setInterval(()=>this.updateCountdown(),1000);this.renderer.setPresentationActive(false);this.home.hidden=true;this.page.hidden=false;this.canvas.style.visibility='hidden';this.el('pixel-host').append(this.canvas);this.art=new InstrumentRenderer(this.renderer);this.render();this.canvas.style.visibility='';this.onPage?.('sky');this.wheel.focus({preventScroll:true});}
 close(){if(!this.active)return;this.active=false;clearInterval(this.countdownTimer);this.art?.destroy();this.art=null;this.canvasHome.append(this.canvas);this.page.hidden=true;this.home.hidden=false;this.renderer.setPresentationActive(true);this.renderer.setWeather(this.getState().weather);this.onPage?.('weather');document.querySelector('#observatory-open').focus({preventScroll:true});}
 destroy(){clearInterval(this.countdownTimer);this.art?.destroy();this.wheelResize.disconnect();}
 updateCountdown(){if(!this.active)return;const elapsed=Math.floor((performance.now()-this.countdownStarted)/1000);for(const el of this.page.querySelectorAll('[data-countdown-seconds]'))el.textContent=el.dataset.countdownSeconds===''?'—':insCountdown(Math.max(0,+el.dataset.countdownSeconds-elapsed));}
 select(i){this.index=((i%SKY_PURPOSES.length)+SKY_PURPOSES.length)%SKY_PURPOSES.length;this.position=this.index;if(this.active)this.render();}
 renderWheel(){
  const en=this.getState().language==='en',position=this.position??this.index,center=Math.round(position);
  const width=this.wheel.clientWidth||720,height=this.wheel.clientHeight||574,ratio=width/height;
  // Preserve the reference geometry at 720×574, while keeping the active label
  // and blue selector inside a tall tablet panel without stretching letters.
  const blue=width*Math.min(.306,Math.max(.14,ratio*.244)),labelX=blue+width*.125,radius=height*.627;
  const font=Math.max(24,Math.min(40,width*.08));let labels='';
  for(let offset=-6;offset<=6;offset++){
   const absolute=center+offset,index=((absolute%SKY_PURPOSES.length)+SKY_PURPOSES.length)%SKY_PURPOSES.length;
   const angle=(absolute-position)*8.5,radian=angle*Math.PI/180,x=labelX-radius+radius*Math.cos(radian),y=height/2+radius*Math.sin(radian);
   labels+=`<text data-purpose-index="${index}" x="${x}" y="${y+font*.35}" transform="rotate(${angle} ${x} ${y})">${insEscape(SKY_PURPOSES[index][en?2:1])}</text>`;
  }
  const svg=this.el('wheel-svg');svg.setAttribute('viewBox',`0 0 ${width} ${height}`);svg.style.fontSize=font+'px';
  svg.innerHTML=`<g>${labels}</g><circle cx="${blue}" cy="${height/2}" r="${Math.max(12,Math.min(18,width*.0222))}" fill="#144fff"/>`;
  this.el('wheel-options').innerHTML=SKY_PURPOSES.map((p,i)=>`<div role="option" id="ins-purpose-${i}" aria-selected="${i===this.index}">${p[en?2:1]}</div>`).join('');
  this.wheel.setAttribute('aria-activedescendant','ins-purpose-'+this.index);
  this.wheel.setAttribute('aria-label',en?'Purpose dial. Drag to choose what matters.':'目的拨轮，拖动选择关注内容');
  this.el('wheel-hint').textContent=en?'DRAG · WHAT MATTERS':'拖动 · 选择关注';this.el('wheel-position').textContent=String(this.index+1).padStart(2,'0')+' / 12';
 }

 render(){
  const s=this.getState(),en=s.language==='en',c=(zh,english)=>en?english:zh;
  const purpose=SKY_PURPOSES[this.index][0],v=makePlanningView(this.day,purpose,s.language),content=composeInstrumentContent(v);
  this.page.dataset.purpose=purpose;this.page.dataset.environment=s.presetKey;
  this.page.setAttribute('aria-label',c('环境观测仪器面板，示例数据','Environmental instrument panel, demo data'));
  const primary=this.el('focus');primary.dataset.kind=content.primary.kind;primary.dataset.priority='core';primary.dataset.fields=content.primary.fields.join(' ');
  primary.innerHTML=insMetric(content.primary.kind,v,s,true)+`<div class="ins-advice">${v.suggestions.map(t=>`<span>${t}</span>`).join('')}</div>`;
  this.el('context').innerHTML=`<span class="ins-context-place">${insEscape(v.location)} · ${v.window.day.date.slice(5).replace('-','/')}</span><h2>${c('观测时段','Observation window')}</h2><strong data-field="window">${v.window.available?v.interval:c('无对应交点','No crossing')}${v.window.available&&v.window.end>=1440?'<small>+1</small>':''}</strong><span class="ins-source">${c('示例 · 气象趋势为示意','Demo · illustrative weather trend')}</span>`;
  this.el('panorama-caption').textContent=c('大气剖面 · 示意','Atmospheric transect · illustration');
  for(const module of content.modules){const el=this.el(module.slot);el.dataset.kind=module.kind;el.dataset.priority=instrumentPriority(v,module.kind);el.dataset.fields=module.fields.join(' ');el.innerHTML=insMetric(module.kind,v,s);}
  this.el('home').setAttribute('aria-label',c('返回天气首页','Return to weather'));
  this.el('live').textContent=SKY_PURPOSES[this.index][en?2:1];this.renderWheel();this.updateCountdown();this.art?.update(v,s);
 }
}
