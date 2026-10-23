import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { WEATHER_PRESETS } from '../dist/weather-art.js';
import { sceneName } from '../dist/i18n.js';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const read = file => fs.readFile(path.join(root, file), 'utf8');
const inline = value => JSON.stringify(value).replace(/</g, '\\u003c');
let cjk = await read('dist/assets/fonts/misans.css');
for (const match of [...cjk.matchAll(/url\('([^']+)'\)/g)]) {
  const bytes = await fs.readFile(path.join(root, 'dist/assets/fonts', match[1]));
  cjk = cjk.replace(match[0], `url('data:font/woff2;base64,${bytes.toString('base64')}')`);
}
let css = ((await read('dist/styles.css'))+'\n'+(await read('dist/observatory.css'))).replace("@import url('assets/fonts/misans.css');", cjk);
for (const name of ['Inter.ttf', 'RobotoMono.ttf', 'SpaceGrotesk.ttf', 'Rajdhani.ttf', 'IBMPlexMono.ttf', 'InstrumentSC.woff2']) {
  const bytes = await fs.readFile(path.join(root, 'dist/assets/fonts', name));
  css = css.replace(`url('assets/fonts/${name}')`, `url('data:font/${name.endsWith('.woff2')?'woff2':'ttf'};base64,${bytes.toString('base64')}')`);
}
const modules = ['weather-art.js', 'weather-shader.js', 'open-meteo.js', 'i18n.js', 'weather-icons.js', 'app-meta.js', 'weather-units.js', 'device-services.js', 'settings.js', 'sky-model.js', 'instrument-physics.js', 'planning-model.js', 'instrument-content.js', 'instrument-art.js', 'observatory.js', 'app.js'];
const instrumentLicense=await read('dist/licenses/SunCalc-BSD-2-Clause.txt');
const js = '/* '+instrumentLicense+' */\n'+(await Promise.all(modules.map(file => read('dist/' + file))))
  .map(code => code.replace(/^import .*;\n/gm, '').replace(/^export /gm, '')).join('\n');
const template = (await read('dist/index.html'))
  .replace(/  <link[^>]+>\n/g, '')
  .replace('  <script src="app.js" type="module"></script>', `<style>${css}</style><script type="module">${js}</script>`);
const scenes = ['partly', 'rain', 'storm', 'snow', 'night', 'sunset', 'dawn', 'clear', 'overcast', 'fog', 'wind']
  .map(key => ({ key, name: sceneName(key, 'zh'), motion: WEATHER_PRESETS[key].cityZh + ' · ' + WEATHER_PRESETS[key].at.slice(0,16).replace('T',' ') + ' · ' + WEATHER_PRESETS[key].motion }));
const shell = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>BARO · 十一种天气完整页面 / Full-screen weather gallery</title><style>
${cjk}
*{box-sizing:border-box}body{margin:0;padding:28px;background:#e9ece6;color:#222b29;font-family:MiSans,Arial,sans-serif}header{max-width:1900px;margin:0 auto 24px;display:flex;justify-content:space-between;align-items:center;gap:24px}h1{font-size:28px;font-weight:500;margin:0 0 8px}header p{font-size:16px;line-height:1.6;color:#5c6a61;margin:0}.controls{display:flex;align-items:center;gap:12px;flex-shrink:0}button,select{border:1px solid #aab6a9;border-radius:6px;background:#f7f7f3;color:#24302a;font:16px MiSans,Arial,sans-serif;padding:10px 14px;cursor:pointer}button:focus-visible,select:focus-visible{outline:2px solid #376989;outline-offset:3px}.gallery{max-width:1900px;margin:auto;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:24px}.card{min-width:0}.card-heading{display:flex;align-items:center;justify-content:space-between;gap:14px;margin:0 0 10px}h2{font-size:19px;font-weight:500;margin:0}.card-heading button{font-size:14px;padding:6px 11px}.preview-window{position:relative;width:100%;aspect-ratio:3/2;overflow:hidden;background:#242828;border-radius:12px}iframe{position:absolute;top:0;left:0;width:1440px;height:960px;transform-origin:top left;border:0}.caption{font-size:14px;line-height:1.6;color:#5e7064;margin:9px 0 0}.card.expanded{grid-column:1/-1}footer{max-width:1900px;margin:26px auto 0;font-size:14px;line-height:1.8;color:#68776c}@media(max-width:1000px){body{padding:16px}header{display:block}.controls{margin-top:18px;flex-wrap:wrap}.gallery{grid-template-columns:1fr}h1{font-size:24px}}
</style></head><body><header><div><h1>BARO · 晴雨</h1><p>Half data, half sky.<br>不同城市与当地时刻 · 日出／日落状态切替 · 最高 1536 列自适应精细像素 · 96 列方块像素 · 场景库与完整设置。<br>每张都是可操作的完整页面，可以放大查看并切换语言。</p></div><div class="controls"><label for="page-view">页面 / Page</label><select id="page-view"><option value="weather">今日天气</option><option value="sky">环境观测</option></select><label for="language">界面语言 / Language</label><select id="language"><option value="zh">中文</option><option value="en">English</option></select><button id="motion" aria-pressed="false">暂停全部动画</button></div></header><main class="gallery" id="gallery"></main><footer>示例天气数据 · 11 种精细像素动画 · 中文 / English · 高清绘制随设备像素比调整。<br>天气页使用 MiSans / Inter / Roboto Mono；观测页独立使用 Space Grotesk / Rajdhani / IBM Plex Mono / Noto Sans SC 子集。原始字体与许可随项目保留。</footer><script>
const template=${inline(template)},scenes=${inline(scenes)};
const gallery=document.querySelector('#gallery');
let language='zh',playing=true,page=new URL(location.href).searchParams.get('page')==='sky'?'sky':'weather';document.querySelector('#page-view').value=page;
const frames=[];
const visibleScenes=new URL(location.href).searchParams.get('view')==='six'?scenes.slice(0,6):scenes;
if(visibleScenes.length===6)document.querySelector('h1').textContent='BARO · 六种天气对照';
for(const scene of visibleScenes){
 const card=document.createElement('section');card.className='card';card.dataset.scene=scene.key;
 const heading=document.createElement('div');heading.className='card-heading';
 const title=document.createElement('h2');title.textContent=scene.name;
 const expand=document.createElement('button');expand.textContent='放大查看';expand.setAttribute('aria-expanded','false');
 expand.addEventListener('click',()=>{const active=card.classList.toggle('expanded');expand.setAttribute('aria-expanded',String(active));expand.textContent=active?'恢复对照':'放大查看';});
 heading.append(title,expand);
 const window=document.createElement('div');window.className='preview-window';
 const frame=document.createElement('iframe');frame.title=scene.name+'完整天气页面';
 frame.srcdoc=template.replace('data-language="zh"','data-language="zh" data-preset="'+scene.key+'"');
 frame.addEventListener('load',()=>{frame.contentWindow.postMessage({type:'sundown-language',language},'*');frame.contentWindow.postMessage({type:'sundown-motion',playing},'*');frame.contentWindow.postMessage({type:'baro-page',page},'*');});
 window.append(frame);frames.push(frame);
 new ResizeObserver(entries=>{frame.style.transform='scale('+(entries[0].contentRect.width/1440)+')';}).observe(window);
 const caption=document.createElement('p');caption.className='caption';caption.textContent=scene.motion;
 card.append(heading,window,caption);gallery.append(card);
}
document.querySelector('#page-view').addEventListener('change',event=>{page=event.target.value;const next=new URL(location.href);next.searchParams.set('page',page);history.replaceState(null,'',next);for(const frame of frames)frame.contentWindow.postMessage({type:'baro-page',page},'*');});
document.querySelector('#language').addEventListener('change',event=>{language=event.target.value;for(const frame of frames)frame.contentWindow.postMessage({type:'sundown-language',language},'*');});
document.querySelector('#motion').addEventListener('click',event=>{playing=!playing;for(const frame of frames)frame.contentWindow.postMessage({type:'sundown-motion',playing},'*');event.currentTarget.textContent=playing?'播放中 · 暂停全部动画':'播放全部动画';event.currentTarget.setAttribute('aria-pressed',String(!playing));});
</script></body></html>`;
await fs.mkdir(path.join(root, 'output'), {recursive:true});
await fs.writeFile(path.join(root, 'output/BARO-gallery.html'), shell);
console.log('Built self-contained gallery of eleven full weather pages.');
