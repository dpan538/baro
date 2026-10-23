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
 {id:'G1',name:'graphite-diagonal',label:'墨灰斜光',note:'左上柔灰 → 右下近黑',type:'linear',x1:'0%',y1:'0%',x2:'100%',y2:'100%',stops:[[0,'#393939'],[.48,'#202020'],[1,'#090909']]},
 {id:'G2',name:'soft-graphite',label:'柔灰聚光',note:'上方柔亮，四周渐深',type:'radial',cx:'36%',cy:'16%',r:'96%',stops:[[0,'#414141'],[.46,'#242424'],[1,'#090909']]},
 {id:'G3',name:'midnight-blue',label:'午夜深蓝',note:'上方黑色 → 下方深蓝',type:'linear',x1:'50%',y1:'0%',x2:'50%',y2:'100%',stops:[[0,'#070a11'],[.45,'#101d30'],[1,'#1d3654']]},
 {id:'G4',name:'blue-black-halo',label:'蓝黑微光',note:'上方克制的蓝光，边缘近黑',type:'radial',cx:'64%',cy:'18%',r:'94%',stops:[[0,'#2a405e'],[.43,'#14243b'],[1,'#070a11']]},
 {id:'G5',name:'deep-blue-diagonal',label:'深蓝斜光',note:'左上深蓝 → 右下墨黑',type:'linear',x1:'0%',y1:'0%',x2:'100%',y2:'100%',stops:[[0,'#2a405e'],[.48,'#132239'],[1,'#06090f']]}
];
function gradient(v){
 const attributes=v.type==='linear'?`x1="${v.x1}" y1="${v.y1}" x2="${v.x2}" y2="${v.y2}"`:`cx="${v.cx}" cy="${v.cy}" r="${v.r}"`;
 const tag=v.type==='linear'?'linearGradient':'radialGradient';
 return `<defs><${tag} id="background" ${attributes} color-interpolation="sRGB">${v.stops.map(([offset,color])=>`<stop offset="${offset}" stop-color="${color}"/>`).join('')}</${tag}></defs><rect width="1024" height="1024" rx="224" fill="url(#background)"/>`;
}
const wrap=(body,title)=>`<svg xmlns="http://www.w3.org/2000/svg" width="4096" height="4096" viewBox="0 0 1024 1024"><title>BARO · 晴雨 · ${title}</title>${body}</svg>\n`;
async function write(file,data){await fs.mkdir(path.dirname(file),{recursive:true});await fs.writeFile(file,data);}
await write(path.join(root,'foreground-4096.png'),foreground);
const baselineFiles=[];
for(const name of ['foreground-4096.png','icon.svg','icon-4096.png','icon-1024.png','icon-512.png'])baselineFiles.push({file:name,sha256:sha(await fs.readFile(path.join(source,name)))});
const cards=[{id:'S1a',label:'原版柔黑',note:'暂定云形 / 原版背景保留',preview:(await fs.readFile(path.join(source,'icon-512.png'))).toString('base64')}];
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
const preview=`<svg xmlns="http://www.w3.org/2000/svg" width="1800" height="1480" viewBox="0 0 1800 1480"><rect width="1800" height="1480" fill="#f2f2ed"/><g font-family="Inter,Arial,sans-serif" fill="#22221f"><text x="95" y="83" font-size="46">BARO</text><text x="263" y="80" font-size="30">晴雨</text><text x="95" y="132" font-size="23" fill="#65655f">暂定 S1a · 仅比较背景 · 云形 / 明暗 / 占比完全一致</text>${cards.map((v,i)=>{const x=95+i%3*570,y=210+Math.floor(i/3)*640;return `<image x="${x}" y="${y}" width="465" height="465" href="data:image/png;base64,${v.preview}"/><text x="${x}" y="${y+515}" font-size="28">${v.id} / ${v.label}</text><text x="${x}" y="${y+554}" font-size="21" fill="#65655f">${v.note}</text>`;}).join('')}</g></svg>\n`;
await write(path.join(root,'preview.svg'),preview);
await write(path.join(root,'preview.png'),await sharp(Buffer.from(preview)).png().toBuffer());
const files=[];
async function collect(dir){for(const e of await fs.readdir(dir,{withFileTypes:true})){const file=path.join(dir,e.name);if(e.isDirectory())await collect(file);else if(!['build.mjs','manifest.json','README.md'].includes(e.name)){const b=await fs.readFile(file);files.push({file:path.relative(root,file),bytes:b.length,sha256:sha(b)});}}}
await collect(root);
await write(path.join(root,'manifest.json'),JSON.stringify({brand:{en:'BARO',zh:'晴雨'},version:9,status:'S1a provisionally selected; background exploration only',source:'Exact v8/S1a native foreground plus project-authored vector background gradients',imageGenerationUsed:false,cloudModified:false,canvas:4096,cloudWidthPercent:74,cornerRadius:896,baseline:{source:'../v8/S1a-refined-soft',background:'#151515',files:baselineFiles},variants,files:files.sort((a,b)=>a.file.localeCompare(b.file))},null,2)+'\n');
