import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import crypto from 'node:crypto';

const root=path.dirname(fileURLToPath(import.meta.url));
const clamp=x=>Math.max(0,Math.min(1,x));
const mix=(a,b,t)=>Array.isArray(a)?a.map((x,i)=>x+(b[i]-x)*t):a+(b-a)*t;
const smooth=(a,b,x)=>{const t=clamp((x-a)/(b-a));return t*t*(3-2*t);};
const normal=v=>{const d=Math.hypot(...v)||1;return v.map(x=>x/d);};
const dot=(a,b)=>a.reduce((s,x,i)=>s+x*b[i],0);
const fract=x=>x-Math.floor(x);
const hash=(x,y)=>fract(Math.sin(x*127.1+y*311.7)*43758.5453);
const bayer=[0,8,2,10,12,4,14,6,3,11,1,9,15,7,13,5];
const palette=[[.26,.42,.62],[.58,.74,.85],[.90,.94,.96],[.99,.97,.89]];
// Cloud bank, lobe normals, volume mix and four-stop palette follow the
// authored weather-shader.js. Only icon framing/layer placement is adapted.
const lobes=[[0,0,.37,.074,.09],[-.19,-.045,.12,.096,.11],[-.055,-.091,.15,.139,.15],[.115,-.068,.13,.115,.13],[.265,-.018,.12,.073,.09]];
function field(x,y,shape,blend){
 let distance=-1,volume=null;
 for(const [cx,cy,rx,ry,thickness] of shape){
  const u=x-cx,v=y-cy,cross=1-(u/rx)**2-(v/ry)**2;
  distance=Math.max(distance,(1-Math.hypot(u/rx,v/ry))*Math.min(rx,ry));
  const z=Math.sqrt(Math.max(0,cross)),lobe=cross>0?{n:normal([u/(rx*rx),v/(ry*ry),z/thickness]),h:z*thickness}:{n:[0,0,1],h:-1};
  if(!volume){volume=lobe;continue;}
  const h=clamp(.5+.5*(volume.h-lobe.h)/blend);
  volume={n:normal(mix(lobe.n,volume.n,h)),h:mix(lobe.h,volume.h,h)+blend*h*(1-h)};
 }
 return {distance,n:volume.n};
}
function ramp(t,coarse,x,y){
 const divisions=coarse?1:32,threshold=coarse?.5:(bayer[(y%4)*4+x%4]+.5)/16;
 const level=Math.floor(clamp(t)*3*divisions+threshold)/divisions;
 return level<1?mix(palette[0],palette[1],level):level<2?mix(palette[1],palette[2],level-1):mix(palette[2],palette[3],level-2);
}
const hex=c=>'#'+c.map(x=>Math.round(clamp(x)*255).toString(16).padStart(2,'0')).join('');
const light=normal([-.30,-.45,.70]);
function cloud({coarse=false,cx=512,cy=604,scale=980,stretch=1.18,brightness=1,shape=lobes,volumeMix=.28,blend=.05}){
 const cell=coarse?24:2,columns=Math.ceil(1024/cell),pixels=[];
 for(let y=0;y<columns;y++)for(let x=0;x<columns;x++){
  const u=((x+.5)*cell-cx)/scale,v=((y+.5)*cell-cy)/(scale*stretch),f=field(u,v,shape,blend);
  const d=f.distance+(coarse?(hash(Math.floor(u*80),Math.floor(v*80))-.5)*.004:0);
  const edge=.65*cell/scale,alpha=coarse?(d>=0?1:0):smooth(-edge,edge,d);
  if(alpha<=.005)continue;
  const diffuse=Math.max(0,dot(f.n,light)),underside=smooth(-.015,.07,v);
  const volume=clamp(.25+diffuse*.60+.10*(1-f.n[1])-underside*.11),original=clamp(.52-v/.24+d*2.5);
  const color=ramp(mix(original,volume,volumeMix),coarse,x,y).map(c=>c*brightness);
  pixels.push({x:x*cell,y:y*cell,w:cell,h:cell,color:hex(color),alpha:+alpha.toFixed(3)});
 }
 return pixels;
}
function paths(pixels){
 const groups=new Map();let run;
 const flush=()=>{if(!run)return;const key=run.color+'/'+run.alpha;groups.set(key,(groups.get(key)||'')+`M${run.x} ${run.y}h${run.w}v${run.h}h-${run.w}z`);};
 for(const p of pixels){if(run&&p.y===run.y&&p.x===run.x+run.w&&p.h===run.h&&p.color===run.color&&p.alpha===run.alpha)run.w+=p.w;else{flush();run={...p};}}flush();
 return [...groups].map(([key,d])=>{const [color,alpha]=key.split('/');return `<path fill="${color}"${alpha==='1'?'':` fill-opacity="${alpha}"`} d="${d}"/>`;}).join('');
}
const layerSVG=layers=>layers.map(p=>`<g shape-rendering="crispEdges">${paths(p)}</g>`).join('');
const tile=layers=>`<rect width="1024" height="1024" rx="224" fill="#000000"/>${layerSVG(layers)}`;
const masterSVG=(layers,foreground=false)=>`<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024"><title>BARO · 晴雨 · Cloud icon</title>${foreground?layerSVG(layers):tile(layers)}</svg>\n`;
async function write(file,data){await fs.mkdir(path.dirname(file),{recursive:true});await fs.writeFile(file,data);}

// The preferred v3/rounded (02) is unchanged; only scale, centring and the
// neutral background vary throughout the 3×3 comparison.
const base=cloud({scale:990,stretch:1.2,cy:612,volumeMix:.42,shape:[[0,0,.34,.10,.12],[-.20,-.045,.12,.115,.13],[-.07,-.105,.16,.17,.18],[.12,-.075,.15,.145,.16],[.26,-.02,.105,.09,.11]]});
const visible=base.filter(p=>p.alpha>=.5);
const bounds={left:Math.min(...visible.map(p=>p.x)),right:Math.max(...visible.map(p=>p.x+p.w)),top:Math.min(...visible.map(p=>p.y)),bottom:Math.max(...visible.map(p=>p.y+p.h))};
const center=[(bounds.left+bounds.right)/2,(bounds.top+bounds.bottom)/2];
const cloudSource=layerSVG([base]);
const sizes=[74,80,86];
const backgrounds=[{name:'black',label:'纯黑',color:'#000000'},{name:'soft-black',label:'柔黑',color:'#151515'},{name:'charcoal',label:'炭灰',color:'#292929'}];
const transform=size=>`translate(512 512) scale(${1024*size/100/(bounds.right-bounds.left)}) translate(${-center[0]} ${-center[1]})`;
const frame=(size,background,reference=false)=>`<rect width="1024" height="1024" rx="224" fill="${background}"/><g transform="${transform(size)}">${reference?'<use href="#rounded-cloud"/>':cloudSource}</g>`;
const wrap=body=>`<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024"><title>BARO · 晴雨 · Rounded cloud</title>${body}</svg>\n`;
let sharp;
if(process.argv.includes('--png')){const module=process.env.BARO_ICON_RASTERIZER;({default:sharp}=await import(module?pathToFileURL(module).href:'sharp'));}
const records=[];
for(let row=0;row<sizes.length;row++){
 const size=sizes[row];
 await write(path.join(root,`foreground-${size}.svg`),wrap(`<g transform="${transform(size)}">${cloudSource}</g>`));
 let radius=0,scale=1024*size/100/(bounds.right-bounds.left);
 for(const p of base)for(const [x,y] of [[p.x,p.y],[p.x+p.w,p.y],[p.x,p.y+p.h],[p.x+p.w,p.y+p.h]])radius=Math.max(radius,Math.hypot((x-center[0])*scale,(y-center[1])*scale)*72/1024);
 for(let col=0;col<backgrounds.length;col++){
  const background=backgrounds[col],id=String.fromCharCode(65+row)+(col+1),name=`${id}-${size}-${background.name}`,master=wrap(frame(size,background.color));
  await write(path.join(root,name,'icon.svg'),master);
  if(sharp)for(const resolution of [1024,512,48,32])await write(path.join(root,name,`icon-${resolution}.png`),await sharp(Buffer.from(master.replace('width="1024" height="1024"',`width="${resolution}" height="${resolution}"`))).png().toBuffer());
  records.push({id,name,cloudWidthPercent:size,background:background.color,adaptiveExtentRadiusDp:+radius.toFixed(2),safeZoneRadiusDp:33});
 }
}
const preview=`<svg xmlns="http://www.w3.org/2000/svg" width="1800" height="1960" viewBox="0 0 1800 1960"><defs><g id="rounded-cloud">${cloudSource}</g></defs><rect width="1800" height="1960" fill="#f2f2ed"/><g font-family="Inter,Arial,sans-serif" fill="#22221f"><text x="140" y="96" font-size="48" letter-spacing="-1.5">BARO</text><text x="308" y="94" font-size="30">晴雨</text><text x="140" y="142" font-size="20" fill="#6b6b64">02 圆润云 / 三种占比 × 三种背景</text>${backgrounds.map((b,col)=>`<text x="${140+col*560}" y="212" font-size="25">${b.label} <tspan font-size="18" fill="#6b6b64">${b.color}</tspan></text>`).join('')}${records.map((v,i)=>{const row=Math.floor(i/3),col=i%3,x=140+col*560,y=250+row*590;return `<g transform="translate(${x} ${y}) scale(${400/1024})">${frame(v.cloudWidthPercent,v.background,true)}</g><text x="${x}" y="${y+448}" font-size="26">${v.id} <tspan font-size="21" fill="#6b6b64">/ 云宽 ${v.cloudWidthPercent}%</tspan></text>`;}).join('')}</g></svg>\n`;
await write(path.join(root,'preview.svg'),preview);
if(sharp)await write(path.join(root,'preview.png'),await sharp(Buffer.from(preview)).png().toBuffer());
const files=[];
async function collect(dir){for(const e of await fs.readdir(dir,{withFileTypes:true})){const file=path.join(dir,e.name);if(e.isDirectory())await collect(file);else if(!['build.mjs','manifest.json','README.md'].includes(e.name)){const bytes=await fs.readFile(file);files.push({file:path.relative(root,file),bytes:bytes.length,sha256:crypto.createHash('sha256').update(bytes).digest('hex')});}}}
await collect(root);
await write(path.join(root,'manifest.json'),JSON.stringify({brand:{en:'BARO',zh:'晴雨'},version:4,basedOn:'v3/rounded (02)',status:'Nine scale/background candidates; no live launcher replacement selected',containsSun:false,canvas:1024,cornerRadius:224,scaleDefinition:'Cloud width at alpha >= 0.5 divided by tile width; optical centring uses the cloud bounds',backgrounds,variants:records,files:files.sort((a,b)=>a.file.localeCompare(b.file))},null,2)+'\n');
console.log('Created nine rounded-cloud size/background candidates.');
