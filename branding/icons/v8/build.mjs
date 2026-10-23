import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import crypto from 'node:crypto';

const root=path.dirname(fileURLToPath(import.meta.url));
const module=process.env.BARO_ICON_RASTERIZER;
const {default:sharp}=await import(module?pathToFileURL(module).href:'sharp');
const RES=4096;
const clamp=x=>Math.max(0,Math.min(1,x));
const mix=(a,b,t)=>Array.isArray(a)?a.map((x,i)=>x+(b[i]-x)*t):a+(b-a)*t;
const normal=v=>{const d=Math.hypot(...v)||1;return v.map(x=>x/d);};
const dot=(a,b)=>a.reduce((s,x,i)=>s+x*b[i],0);
const smooth=(a,b,x)=>{const t=clamp((x-a)/(b-a));return t*t*(3-2*t);};
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const palette=[[.26,.42,.62],[.58,.74,.85],[.90,.94,.96],[.99,.97,.89]];
const light=normal([-.30,-.45,.70]);
const originalLobes=[[0,0,.34,.10,.12],[-.20,-.045,.12,.115,.13],[-.07,-.105,.16,.17,.18],[.12,-.075,.15,.145,.16],[.26,-.02,.105,.09,.11]];
// These three shallow volumes clarify the small puffs already suggested on
// A2's lower face. They alter the surface only, not the approved silhouette.
const frontLobes=[[-.165,.031,.125,.067,.112],[-.015,.043,.142,.061,.122],[.146,.031,.112,.062,.108]];
const framing={scale:1.0856160458452722,center:[525,508],widthPercent:74,background:'#151515'};
const variants=[
 {id:'S1a',name:'refined-soft',label:'柔光细化',note:'沿 S1 补清前侧小体积，保持轻柔',frontBlend:.033,frontStrength:.46,volumeMix:.475,underside:.004,frontThickness:1.035,maskLow:-.020,maskHigh:.028},
 {id:'S3a',name:'refined-depth',label:'体积细化',note:'沿 S3 加强前后关系，柔化交叠边缘',frontBlend:.036,frontStrength:.78,volumeMix:.54,underside:.012,frontThickness:1.055,maskLow:-.024,maskHigh:.026}
];
function volume(x,y,lobes,blend,needSilhouette=false){
 let distance=-1,body=null;
 for(const [cx,cy,rx,ry,thickness] of lobes){
  const u=x-cx,v=y-cy,cross=1-(u/rx)**2-(v/ry)**2;
  if(needSilhouette)distance=Math.max(distance,(1-Math.hypot(u/rx,v/ry))*Math.min(rx,ry));
  if(cross<=0)continue;
  const z=Math.sqrt(cross),part={n:normal([u/(rx*rx),v/(ry*ry),z/thickness]),h:z*thickness};
  if(!body){body=part;continue;}
  const h=clamp(.5+.5*(body.h-part.h)/blend);
  body={n:normal(mix(part.n,body.n,h)),h:mix(part.h,body.h,h)+blend*h*(1-h)};
 }
 return {distance,n:body?.n??[0,0,1],h:body?.h??-1};
}
function colour(t){
 // Full-resolution floating-point shading, not a magnified 1024px grid.
 // No procedural grain, pattern, stripes or painted wave overlay is used.
 const level=clamp(t)*3;
 return level<1?mix(palette[0],palette[1],level):level<2?mix(palette[1],palette[2],level-1):mix(palette[2],palette[3],level-2);
}
function render(v){
 const data=Buffer.alloc(RES*RES*4),nativeStep=1024/RES;
 const refinedFront=frontLobes.map(([x,y,rx,ry,z])=>[x,y,rx,ry,z*v.frontThickness]);
 // Conservative screen bounds only skip empty space; every visible texel is
 // evaluated independently in the authored geometry at native 4096px.
 for(let y=1000;y<3080;y++)for(let x=460;x<3630;x++){
  const sx=((x+.5)*nativeStep-512)/framing.scale+framing.center[0];
  const sy=((y+.5)*nativeStep-512)/framing.scale+framing.center[1];
  const u=(sx-512)/990,w=(sy-612)/(990*1.2);
  const base=volume(u,w,originalLobes,.05,true);
  const edge=.65*nativeStep/(990*framing.scale),alpha=smooth(-edge,edge,base.distance);
  if(alpha<=0)continue;
  const front=volume(u,w,refinedFront,v.frontBlend);
  const contact=clamp(.5+.5*(front.h-base.h)/v.frontBlend);
  const frontMask=smooth(v.maskLow,v.maskHigh,w)*contact*v.frontStrength;
  const n=normal(mix(base.n,front.n,frontMask));
  const underside=smooth(-.015,.07,w),diffuse=Math.max(0,dot(n,light));
  const surface=clamp(.25+diffuse*.60+.10*(1-n[1])-underside*.11);
  const original=clamp(.52-w/.24+base.distance*2.5);
  const tone=mix(original,surface,v.volumeMix)-underside*v.underside;
  const c=colour(tone),index=(y*RES+x)*4;
  for(let channel=0;channel<3;channel++)data[index+channel]=Math.round(clamp(c[channel])*255);
  data[index+3]=Math.round(alpha*255);
 }
 return data;
}
async function write(file,data){await fs.mkdir(path.dirname(file),{recursive:true});await fs.writeFile(file,data);}
const frameSvg=`<svg xmlns="http://www.w3.org/2000/svg" width="4096" height="4096"><rect width="4096" height="4096" rx="896" fill="#151515"/></svg>`;
const bodies=[];
for(const v of variants){
 const dir=path.join(root,`${v.id}-${v.name}`),data=render(v);
 const foreground=await sharp(data,{raw:{width:RES,height:RES,channels:4}}).png().toBuffer();
 await write(path.join(dir,'foreground-4096.png'),foreground);
 const master=await sharp(Buffer.from(frameSvg)).composite([{input:foreground}]).png().toBuffer();
 await write(path.join(dir,'icon-4096.png'),master);
 for(const size of [2048,1024,512,192,48,32])await write(path.join(dir,`icon-${size}.png`),await sharp(master).resize(size,size,{kernel:'lanczos3'}).png().toBuffer());
 const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="4096" height="4096" viewBox="0 0 1024 1024"><title>BARO · 晴雨 · ${v.id} · Native 4096px cloud study</title><rect width="1024" height="1024" rx="224" fill="#151515"/><image width="1024" height="1024" href="data:image/png;base64,${foreground.toString('base64')}"/></svg>\n`;
 await write(path.join(dir,'icon.svg'),svg);
 bodies.push((await sharp(master).resize(512,512).png().toBuffer()).toString('base64'));
 console.log(`Rendered ${v.id} directly at ${RES}×${RES}`);
}
const references=[];
const refInfo=[
 ['A2','../v4/A2-74-soft-black','原版保留','74% 云宽 / 柔黑'],
 ['B1','../v4/B1-80-black','原版保留','80% 云宽 / 纯黑'],
 ['S1','../v7/S1-subtle','上一轮保留','轻微细化'],
 ['S3','../v7/S3-clear-volume','上一轮保留','清晰体积']
];
for(const [id,relative,label,note] of refInfo){
 const source=path.join(root,relative),files=[];
 for(const name of ['icon.svg','icon-1024.png','icon-512.png','icon-48.png','icon-32.png'])files.push({file:name,sha256:sha(await fs.readFile(path.join(source,name)))});
 references.push({id,source:relative,label,note,files,preview:(await fs.readFile(path.join(source,'icon-512.png'))).toString('base64')});
}
const refs=Object.fromEntries(references.map(r=>[r.id,{...r,image:r.preview}]));
const revised=variants.map((v,i)=>({...v,image:bodies[i]}));
const cards=[refs.A2,refs.S1,revised[0],refs.B1,refs.S3,revised[1]];
const places=[[95,230],[665,230],[1235,230],[95,870],[665,870],[1235,870]];
const preview=`<svg xmlns="http://www.w3.org/2000/svg" width="1800" height="1490" viewBox="0 0 1800 1490"><rect width="1800" height="1490" fill="#f2f2ed"/><g font-family="Inter,Arial,sans-serif" fill="#22221f"><text x="95" y="83" font-size="46">BARO</text><text x="263" y="80" font-size="30">晴雨</text><text x="95" y="132" font-size="23" fill="#65655f">S1 / S3 继续细化 · 六版同尺寸对照 · 4096px 原生母版</text>${['原版 / 保留','上一轮 / 保留','本轮 / 继续细化'].map((label,i)=>`<text x="${95+i*570}" y="197" font-size="23">${label}</text>`).join('')}${cards.map((v,i)=>{const [x,y]=places[i];return `<image x="${x}" y="${y}" width="465" height="465" href="data:image/png;base64,${v.image}"/><text x="${x}" y="${y+515}" font-size="28">${v.id} / ${v.label}</text><text x="${x}" y="${y+554}" font-size="21" fill="#65655f">${v.note}</text>`;}).join('')}</g></svg>\n`;
await write(path.join(root,'preview.svg'),preview);await write(path.join(root,'preview.png'),await sharp(Buffer.from(preview)).png().toBuffer());
const files=[];async function collect(dir){for(const e of await fs.readdir(dir,{withFileTypes:true})){const f=path.join(dir,e.name);if(e.isDirectory())await collect(f);else if(!['README.md','build.mjs','manifest.json'].includes(e.name)){const b=await fs.readFile(f);files.push({file:path.relative(root,f),bytes:b.length,sha256:sha(b)});}}}await collect(root);
await write(path.join(root,'manifest.json'),JSON.stringify({brand:{en:'BARO',zh:'晴雨'},version:8,source:'Project-native continuation of v7/S1 and v7/S3; front-cloud thickness, overlap softness and front-mask refinement',imageGenerationUsed:false,paintedRippleOverlayUsed:false,nativeRenderResolution:RES,upscaledSource:false,originalLobes,frontLobes,framing,light,palette,variants,references:references.map(({preview,...r})=>r),files:files.sort((a,b)=>a.file.localeCompare(b.file))},null,2)+'\n');
