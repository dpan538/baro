import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath,pathToFileURL} from 'node:url';
import crypto from 'node:crypto';

const root=path.dirname(fileURLToPath(import.meta.url));
const module=process.env.BARO_ICON_RASTERIZER;
const {default:sharp}=await import(module?pathToFileURL(module).href:'sharp');
const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const source=path.join(root,'../v8/S1a-refined-soft');
const foreground=await fs.readFile(path.join(source,'foreground-4096.png'));
const cloudHref=`data:image/png;base64,${foreground.toString('base64')}`;
const variants=[
 {id:'G1a',name:'dimmed',label:'压低亮部',note:'保留斜光，亮部更收敛',type:'linear',x1:'0%',y1:'0%',x2:'100%',y2:'100%',stops:[[0,'#2b2b2b'],[.48,'#1d1d1d'],[1,'#111111']]},
 {id:'G1b',name:'darker',label:'更暗一些',note:'进一步缩小黑灰跨度',type:'linear',x1:'0%',y1:'0%',x2:'100%',y2:'100%',stops:[[0,'#252525'],[.48,'#1a1a1a'],[1,'#121212']]},
 {id:'G1c',name:'low-contrast',label:'低对比柔黑',note:'最轻的渐变，保留一点层次',type:'linear',x1:'0%',y1:'0%',x2:'100%',y2:'100%',stops:[[0,'#212121'],[.48,'#191919'],[1,'#141414']]}
];
function gradient(v){
 const attributes=v.type==='linear'?`x1="${v.x1}" y1="${v.y1}" x2="${v.x2}" y2="${v.y2}"`:`cx="${v.cx}" cy="${v.cy}" r="${v.r}"`;
 const tag=v.type==='linear'?'linearGradient':'radialGradient';
 return `<defs><${tag} id="background" ${attributes} color-interpolation="sRGB">${v.stops.map(([offset,color])=>`<stop offset="${offset}" stop-color="${color}"/>`).join('')}</${tag}></defs><rect width="1024" height="1024" rx="224" fill="url(#background)"/>`;
}
const wrap=(body,title)=>`<svg xmlns="http://www.w3.org/2000/svg" width="4096" height="4096" viewBox="0 0 1024 1024"><title>BARO · 晴雨 · ${title}</title>${body}</svg>\n`;
async function write(file,data){await fs.mkdir(path.dirname(file),{recursive:true});await fs.writeFile(file,data);}
await write(path.join(root,'foreground-4096.png'),foreground);
const baselineSource=path.join(root,'../v9/G1-graphite-diagonal');
const baselineFiles=[];
for(const name of ['background.svg','icon.svg','icon-4096.png','icon-1024.png','icon-512.png'])baselineFiles.push({file:name,sha256:sha(await fs.readFile(path.join(baselineSource,name)))});
const cards=[{id:'G1',label:'原版保留',note:'原版斜向黑灰渐变',preview:(await fs.readFile(path.join(baselineSource,'icon-512.png'))).toString('base64')}];
for(const v of variants){
 const dir=path.join(root,`${v.id}-${v.name}`),background=wrap(gradient(v),`${v.id} background`);
 await write(path.join(dir,'background.svg'),background);
 const master=await sharp(Buffer.from(background)).composite([{input:foreground}]).png().toBuffer();
 await write(path.join(dir,'icon-4096.png'),master);
 for(const size of [2048,1024,512,192,48,32])await write(path.join(dir,`icon-${size}.png`),await sharp(master).resize(size,size,{kernel:'lanczos3'}).png().toBuffer());
 await write(path.join(dir,'icon.svg'),wrap(`${gradient(v)}<image width="1024" height="1024" href="${cloudHref}"/>`,`${v.id} / S1a cloud`));
 cards.push({...v,preview:(await sharp(master).resize(512,512).png().toBuffer()).toString('base64')});
 console.log(`Composited ${v.id} with the exact S1a foreground at 4096px`);
}
const preview=`<svg xmlns="http://www.w3.org/2000/svg" width="1400" height="1600" viewBox="0 0 1400 1600"><rect width="1400" height="1600" fill="#f2f2ed"/><g font-family="Inter,Arial,sans-serif" fill="#22221f"><text x="95" y="83" font-size="46">BARO</text><text x="263" y="80" font-size="30">晴雨</text><text x="95" y="132" font-size="23" fill="#65655f">保留 G1 · 压暗亮部 / 缩小明暗跨度 · S1a 云体不变</text>${cards.map((v,i)=>{const x=95+i%2*665,y=210+Math.floor(i/2)*680;return `<image x="${x}" y="${y}" width="545" height="545" href="data:image/png;base64,${v.preview}"/><text x="${x}" y="${y+594}" font-size="28">${v.id} / ${v.label}</text><text x="${x}" y="${y+633}" font-size="21" fill="#65655f">${v.note}</text>`;}).join('')}</g></svg>\n`;
await write(path.join(root,'preview.svg'),preview);
await write(path.join(root,'preview.png'),await sharp(Buffer.from(preview)).png().toBuffer());
const files=[];
async function collect(dir){for(const e of await fs.readdir(dir,{withFileTypes:true})){const file=path.join(dir,e.name);if(e.isDirectory())await collect(file);else if(!['build.mjs','manifest.json','README.md'].includes(e.name)){const b=await fs.readFile(file);files.push({file:path.relative(root,file),bytes:b.length,sha256:sha(b)});}}}
await collect(root);
await write(path.join(root,'manifest.json'),JSON.stringify({brand:{en:'BARO',zh:'晴雨'},version:10,status:'S1a provisionally selected; G1 retained; darker and narrower-range G1 background studies',source:'Exact v8/S1a native foreground plus project-authored vector background gradients',imageGenerationUsed:false,cloudModified:false,canvas:4096,cloudWidthPercent:74,cornerRadius:896,foregroundSource:{file:'../v8/S1a-refined-soft/foreground-4096.png',sha256:sha(foreground)},baseline:{source:'../v9/G1-graphite-diagonal',stops:[[0,'#393939'],[.48,'#202020'],[1,'#090909']],files:baselineFiles},variants,files:files.sort((a,b)=>a.file.localeCompare(b.file))},null,2)+'\n');
