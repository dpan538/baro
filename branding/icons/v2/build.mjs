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
function field(x,y){
 let distance=-1,volume=null;
 for(const [cx,cy,rx,ry,thickness] of lobes){
  const u=x-cx,v=y-cy,cross=1-(u/rx)**2-(v/ry)**2;
  distance=Math.max(distance,(1-Math.hypot(u/rx,v/ry))*Math.min(rx,ry));
  const z=Math.sqrt(Math.max(0,cross)),lobe=cross>0?{n:normal([u/(rx*rx),v/(ry*ry),z/thickness]),h:z*thickness}:{n:[0,0,1],h:-1};
  if(!volume){volume=lobe;continue;}
  const h=clamp(.5+.5*(volume.h-lobe.h)/.025);
  volume={n:normal(mix(lobe.n,volume.n,h)),h:mix(lobe.h,volume.h,h)+.025*h*(1-h)};
 }
 return {distance,n:volume.n};
}
function ramp(t,coarse,x,y){
 const divisions=coarse?1:10,threshold=coarse?.5:(bayer[(y%4)*4+x%4]+.5)/16;
 const level=Math.floor(clamp(t)*3*divisions+threshold)/divisions;
 return level<1?mix(palette[0],palette[1],level):level<2?mix(palette[1],palette[2],level-1):mix(palette[2],palette[3],level-2);
}
const hex=c=>'#'+c.map(x=>Math.round(clamp(x)*255).toString(16).padStart(2,'0')).join('');
const light=normal([-.30,-.45,.70]);
function cloud({coarse,cx=512,cy=604,scale=980,stretch=1.18,brightness=1}){
 const cell=coarse?24:4,columns=Math.ceil(1024/cell),pixels=[];
 for(let y=0;y<columns;y++)for(let x=0;x<columns;x++){
  const u=((x+.5)*cell-cx)/scale,v=((y+.5)*cell-cy)/(scale*stretch),f=field(u,v);
  const d=f.distance+(coarse?(hash(Math.floor(u*80),Math.floor(v*80))-.5)*.004:0);
  const edge=.65*cell/scale,alpha=coarse?(d>=0?1:0):smooth(-edge,edge,d);
  if(alpha<=.005)continue;
  const diffuse=Math.max(0,dot(f.n,light)),underside=smooth(-.015,.07,v);
  const volume=clamp(.25+diffuse*.60+.10*(1-f.n[1])-underside*.11),original=clamp(.52-v/.24+d*2.5);
  const color=ramp(mix(original,volume,.16),coarse,x,y).map(c=>c*brightness);
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
function silhouette(layers){
 const rows=new Map();
 for(const layer of layers)for(const p of layer){if(p.alpha<.5)continue;const key=p.y+'/'+p.h;if(!rows.has(key))rows.set(key,[]);rows.get(key).push([p.x,p.x+p.w]);}
 const paths=[];
 for(const [key,spans] of rows){const [y,h]=key.split('/').map(Number);spans.sort((a,b)=>a[0]-b[0]);let current;
  for(const span of spans){if(current&&span[0]<=current[1])current[1]=Math.max(current[1],span[1]);else{if(current)paths.push(`M${current[0]} ${y}h${current[1]-current[0]}v${h}h-${current[1]-current[0]}z`);current=[...span];}}
  if(current)paths.push(`M${current[0]} ${y}h${current[1]-current[0]}v${h}h-${current[1]-current[0]}z`);
 }
 return paths.join('');
}
async function write(file,data){await fs.mkdir(path.dirname(file),{recursive:true});await fs.writeFile(file,data);}
const variants=[
 {name:'pixel-cloud',label:'像素单云',coarse:true,double:false},
 {name:'fine-cloud',label:'精细单云',coarse:false,double:false},
 {name:'pixel-double-cloud',label:'像素双云',coarse:true,double:true},
 {name:'fine-double-cloud',label:'精细双云',coarse:false,double:true}
].map(v=>({...v,layers:v.double?[cloud({coarse:v.coarse,cx:608,cy:486,scale:760,stretch:1.2,brightness:.86}),cloud({coarse:v.coarse,cx:430,cy:720,scale:870,stretch:1.16})]:[cloud({coarse:v.coarse})]}));
const density=[['mdpi',1],['hdpi',1.5],['xhdpi',2],['xxhdpi',3],['xxxhdpi',4]];
let sharp;
if(process.argv.includes('--png')){const module=process.env.BARO_ICON_RASTERIZER;({default:sharp}=await import(module?pathToFileURL(module).href:'sharp'));}
const records=[];
for(const v of variants){
 const dir=path.join(root,v.name),master=masterSVG(v.layers),foreground=masterSVG(v.layers,true),res=path.join(dir,'android','res');
 await write(path.join(dir,'icon.svg'),master);await write(path.join(dir,'foreground.svg'),foreground);
 await write(path.join(res,'drawable','baro_icon_background.xml'),'<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle"><solid android:color="#000000"/></shape>\n');
 await write(path.join(res,'drawable','baro_icon_monochrome.xml'),`<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108"><group android:translateX="18" android:translateY="18" android:scaleX="0.0703125" android:scaleY="0.0703125"><path android:fillColor="#ffffff" android:pathData="${silhouette(v.layers)}"/></group></vector>\n`);
 for(const [qualifier,mono] of [['mipmap-anydpi-v26',false],['mipmap-anydpi-v33',true]])await write(path.join(res,qualifier,'baro_launcher.xml'),`<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@drawable/baro_icon_background"/><foreground android:drawable="@drawable/baro_icon_foreground"/>${mono?'<monochrome android:drawable="@drawable/baro_icon_monochrome"/>':''}</adaptive-icon>\n`);
 if(sharp){
  for(const size of [1024,512,192,96,48,32])await write(path.join(dir,`icon-${size}.png`),await sharp(Buffer.from(master.replace('width="1024" height="1024"',`width="${size}" height="${size}"`))).png().toBuffer());
  const adaptive=`<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108"><g transform="translate(18 18) scale(0.0703125)">${layerSVG(v.layers)}</g></svg>`;
  for(const [name,scale] of density){
   const legacySize=48*scale,adaptiveSize=108*scale;
   await write(path.join(res,`mipmap-${name}`,'baro_launcher.png'),await sharp(Buffer.from(master.replace('width="1024" height="1024"',`width="${legacySize}" height="${legacySize}"`))).png().toBuffer());
   await write(path.join(res,`drawable-${name}`,'baro_icon_foreground.png'),await sharp(Buffer.from(adaptive.replace('width="108" height="108"',`width="${adaptiveSize}" height="${adaptiveSize}"`))).png().toBuffer());
  }
 }
 let maxRadius=0;
 for(const layer of v.layers)for(const p of layer)for(const [x,y] of [[p.x,p.y],[p.x+p.w,p.y],[p.x,p.y+p.h],[p.x+p.w,p.y+p.h]])maxRadius=Math.max(maxRadius,Math.hypot(x-512,y-512)*72/1024);
 records.push({name:v.name,label:v.label,clouds:v.layers.length,gridStep:v.coarse?24:4,adaptiveExtentRadiusDp:+maxRadius.toFixed(2),safeZoneRadiusDp:33});
}
const sample=(v,x,y,size)=>`<g transform="translate(${x} ${y}) scale(${size/1024})"><use href="#cloud-${v.name}"/></g>`;
const board=`<svg xmlns="http://www.w3.org/2000/svg" width="1600" height="1800" viewBox="0 0 1600 1800"><rect width="1600" height="1800" fill="#f2f2ed"/><g fill="#22221f" font-family="Inter,Arial,sans-serif"><text x="160" y="102" font-size="48" letter-spacing="-1.5">BARO</text><text x="328" y="100" font-family="sans-serif" font-size="30">晴雨</text><text x="160" y="146" font-size="16" fill="#6b6b64" letter-spacing="2">CLOUD STUDIES / APP ICON</text>${variants.map((v,i)=>{const x=i%2?960:160,y=i<2?210:1030;return sample(v,x,y,480)+`<text x="${x}" y="${y+538}" font-size="24">0${i+1} / ${v.label}</text>`+sample(v,x,y+594,64)+sample(v,x+116,y+602,48)+sample(v,x+216,y+610,32)+`<text x="${x}" y="${y+694}" font-size="14" fill="#6b6b64">64 px</text><text x="${x+116}" y="${y+694}" font-size="14" fill="#6b6b64">48 px</text><text x="${x+216}" y="${y+694}" font-size="14" fill="#6b6b64">32 px</text>`}).join('')}</g></svg>\n`;
const preview=board.replace('</svg>',`<defs>${variants.map(v=>`<g id="cloud-${v.name}">${tile(v.layers)}</g>`).join('')}</defs></svg>`);
await write(path.join(root,'preview.svg'),preview);
if(sharp)await write(path.join(root,'preview.png'),await sharp(Buffer.from(preview)).png().toBuffer());
const files=[];
async function collect(dir){for(const e of await fs.readdir(dir,{withFileTypes:true})){const file=path.join(dir,e.name);if(e.isDirectory())await collect(file);else if(!['build.mjs','manifest.json','README.md'].includes(e.name)){const bytes=await fs.readFile(file);files.push({file:path.relative(root,file),bytes:bytes.length,sha256:crypto.createHash('sha256').update(bytes).digest('hex')});}}}
await collect(root);
await write(path.join(root,'manifest.json'),JSON.stringify({brand:{en:'BARO',zh:'晴雨'},version:2,status:'Four cloud-only design candidates; no launcher replacement selected',background:'#000000',canvas:1024,cornerRadius:224,source:'Authored cloud bank(), lobe normals, lighting mix, coarse/fine ramp and Bayer pattern from dist/weather-shader.js; framing and two-cloud composition adapted for launcher use.',containsSun:false,variants:records,adaptive:{viewport:108,visibleReference:72,offset:18},files:files.sort((a,b)=>a.file.localeCompare(b.file))},null,2)+'\n');
console.log('Created four cloud-only BARO / 晴雨 icon candidates'+(sharp?', PNG sizes and Android resource packs.':'.'));
