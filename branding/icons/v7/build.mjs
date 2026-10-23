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
 {id:'S1',name:'subtle',label:'轻微细化',note:'最接近 A2，前侧体积稍明确',frontBlend:.030,frontStrength:.34,volumeMix:.46,underside:.003},
 {id:'S2',name:'soft-volume',label:'柔和体积',note:'补清前侧云团，保持柔和过渡',frontBlend:.036,frontStrength:.53,volumeMix:.49,underside:.006},
 {id:'S3',name:'clear-volume',label:'清晰体积',note:'前后关系更明确，底部略深',frontBlend:.032,frontStrength:.66,volumeMix:.52,underside:.011}
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
 // Conservative screen bounds only skip empty space; every visible texel is
 // evaluated independently in the authored geometry at native 4096px.
 for(let y=1000;y<3080;y++)for(let x=460;x<3630;x++){
  const sx=((x+.5)*nativeStep-512)/framing.scale+framing.center[0];
  const sy=((y+.5)*nativeStep-512)/framing.scale+framing.center[1];
  const u=(sx-512)/990,w=(sy-612)/(990*1.2);
  const base=volume(u,w,originalLobes,.05,true);
  const edge=.65*nativeStep/(990*framing.scale),alpha=smooth(-edge,edge,base.distance);
  if(alpha<=0)continue;
  const front=volume(u,w,frontLobes,v.frontBlend);
  const contact=clamp(.5+.5*(front.h-base.h)/v.frontBlend);
  const frontMask=smooth(-.015,.030,w)*contact*v.frontStrength;
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
for(const [id,dir] of [['A2','A2-74-soft-black'],['B1','B1-80-black']]){
 const source=path.join(root,'../v4',dir),files=[];
 for(const name of ['icon.svg','icon-1024.png','icon-512.png','icon-48.png','icon-32.png'])files.push({file:name,sha256:sha(await fs.readFile(path.join(source,name)))});
 references.push({id,source:`../v4/${dir}`,files,preview:(await fs.readFile(path.join(source,'icon-512.png'))).toString('base64')});
}
const cards=[...references.map(r=>({id:r.id,label:'原版保留',note:'保持原文件不变',image:r.preview})),...variants.map((v,i)=>({...v,image:bodies[i]}))];
const places=[[95,190],[665,190],[95,790],[665,790],[1235,790]];
const preview=`<svg xmlns="http://www.w3.org/2000/svg" width="1800" height="1400" viewBox="0 0 1800 1400"><rect width="1800" height="1400" fill="#f2f2ed"/><g font-family="Inter,Arial,sans-serif" fill="#22221f"><text x="95" y="83" font-size="46">BARO</text><text x="263" y="80" font-size="30">晴雨</text><text x="95" y="132" font-size="23" fill="#65655f">延续 A2 原有质感 · 补清前侧云团 · 4096px 原生母版</text><text x="1235" y="363" font-size="26">轮廓 / 占比 / 背景保持一致</text><text x="1235" y="411" font-size="22" fill="#65655f">仅细化云自身的体积与遮挡</text><text x="1235" y="453" font-size="22" fill="#65655f">从源码按 4096px 独立采样</text>${cards.map((v,i)=>{const [x,y]=places[i];return `<image x="${x}" y="${y}" width="465" height="465" href="data:image/png;base64,${v.image}"/><text x="${x}" y="${y+515}" font-size="28">${v.id} / ${v.label}</text><text x="${x}" y="${y+554}" font-size="21" fill="#65655f">${v.note}</text>`;}).join('')}</g></svg>\n`;
await write(path.join(root,'preview.svg'),preview);await write(path.join(root,'preview.png'),await sharp(Buffer.from(preview)).png().toBuffer());
const files=[];async function collect(dir){for(const e of await fs.readdir(dir,{withFileTypes:true})){const f=path.join(dir,e.name);if(e.isDirectory())await collect(f);else if(!['README.md','build.mjs','manifest.json'].includes(e.name)){const b=await fs.readFile(f);files.push({file:path.relative(root,f),bytes:b.length,sha256:sha(b)});}}}await collect(root);
await write(path.join(root,'manifest.json'),JSON.stringify({brand:{en:'BARO',zh:'晴雨'},version:7,source:'Project-native cloud geometry; original A2 surface with restrained front-volume clarification',imageGenerationUsed:false,paintedRippleOverlayUsed:false,nativeRenderResolution:RES,upscaledSource:false,originalLobes,frontLobes,framing,light,palette,variants,references:references.map(({preview,...r})=>r),files:files.sort((a,b)=>a.file.localeCompare(b.file))},null,2)+'\n');
