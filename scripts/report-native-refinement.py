#!/usr/bin/env python3
"""Build a local review page from completed native checks; never invent acceptance rows."""
from pathlib import Path
import argparse,html,json,shutil,statistics
p=argparse.ArgumentParser();p.add_argument('directory',type=Path);p.add_argument('--previous',type=Path);p.add_argument('--historical-soak',type=Path);args=p.parse_args();out=args.directory
read=lambda name:json.loads((out/name).read_text())
identity=read('build-info.json');source=identity['sourceCommit']
assert 'OK (38 tests)' in (out/'native-regression.log').read_text()
assert 'OK (1 test)' in (out/'small-regression.log').read_text()
text=read('text-audit.json');variants=read('variant-text-audit.json');wind=read('wind-composition-audit.json');figures=read('main-figure-audit.json');surfaces=read('surface-audit.json')
assert all(not x['issues'] for x in text+variants)
assert all(not x['issues'] for x in [wind,figures,surfaces])
files={}
for orientation in ['landscape','portrait']:
 for lang in ['zh','en']:
  for purpose in range(12):
   name=f'native-observation-{orientation}-{lang}'+(f'-purpose-{purpose}' if purpose else '')+'.png';assert (out/name).is_file();files[f'{orientation}-{lang}-{purpose}']=name
  assert (out/f'native-weather-{orientation}-{lang}.png').is_file()
  if args.previous:shutil.copy2(args.previous/f'native-observation-{orientation}-{lang}.png',out/f'previous-{orientation}-{lang}.png')
small_names=[]
for orientation in ['landscape','portrait']:
 for lang in ['zh','en']:
  for suffix in ['', '-purpose-2','-purpose-11']:
   name=f'small-{orientation}-{lang}{suffix}.png';assert (out/name).is_file();small_names.append(name)
  name=f'small-weather-{orientation}-{lang}.png';assert (out/name).is_file();small_names.append(name)
profiles=read('production-profiles/profiles.json');assert len(profiles)==8 and all(not x['instrumentation'] for x in profiles)
svg=['<svg xmlns="http://www.w3.org/2000/svg" width="1050" height="440" viewBox="0 0 1050 440"><rect width="1050" height="440" fill="#171715"/><g font-family="sans-serif" fill="#fffffb"><text x="24" y="34" font-size="20">当前 APK · 八种独立进程内存采样（PSS MiB）</text>']
maximum=max(x['foregroundMemory']['totalPssKiB']/1024 for x in profiles)
for n,row in enumerate(profiles):
 y=76+n*43;value=row['foregroundMemory']['totalPssKiB']/1024;label=('天气'if row['page']==0 else'观测')+' · '+row['pixels']+' · '+('动态'if row['motion'] else'静态')
 svg.append(f'<text x="24" y="{y+16}" font-size="15">{label}</text><rect x="240" y="{y}" width="{value/maximum*660:.2f}" height="24" rx="5" fill="#cb8870"/><text x="{250+value/maximum*660:.2f}" y="{y+17}" font-size="15">{value:.2f}</text>')
svg.append('<text x="24" y="430" font-size="13" fill="#cecec4">软件模拟器 · 无测试进程 · 不是小米真机功耗或续航测量</text></g></svg>');(out/'resource-chart.svg').write_text(''.join(svg))
resource_rows=''.join('<tr><td>'+html.escape(x['name'])+'</td><td>'+f"{x['foregroundMemory']['totalPssKiB']/1024:.2f}"+'</td><td>'+f"{x['foregroundCpu']['percentOneCore']:.2f}%"+'</td><td>'+f"{x['backgroundCpu']['percentOneCore']:.2f}%"+'</td><td>'+f"{x['backgroundSettled']['cpu']['percentOneCore']:.2f}%"+'</td></tr>'for x in profiles)
history=''
if args.historical_soak:
 rows=[json.loads(x)for x in (args.historical_soak/'rounds.jsonl').read_text().splitlines()];assert len(rows)==300 and all(x['status']=='pass'for x in rows)
 relative='../'+args.historical_soak.name
 history=f'<details><summary>历史 300 轮打开/运行记录 · 应用源码 {rows[0]["source"][:7]}</summary><p>这是前一应用版本的 300 轮记录。本次视觉修订的检查数量在上方单独列出，不能将这些历史轮次改称当前 APK 的 300 轮。</p><img loading="lazy" src="{relative}/rounds-chart.png" alt="真实300轮记录图"/><p><a href="{relative}/report.html">300 轮可筛选明细</a> · <a href="{relative}/rounds.csv">CSV</a> · <a href="{relative}/rounds.jsonl">原始逐轮记录</a></p></details>'
summary={'source':source,'nativeMethods':39,'standardSuiteMethods':38,'highScalingMethods':1,'artworkOverlayStates':64,'singleViewportStates':48,'standardGlyphStates':len(text),'variantGlyphStates':len(variants),'windStates':len(wind['cases']),'mainFigureMeasurements':len(figures['cases']),'minimumMainFigureHeightFraction':min(x['heightFraction']for x in figures['cases']),'tileCornerChecks':len(surfaces['cases']),'individualCorners':4*len(surfaces['cases']),'nativeImages':68,'productionProfiles':8,'productionPssMiB':[min(x['foregroundMemory']['totalPssKiB']/1024 for x in profiles),maximum],'historical300CurrentApk':False}
(out/'verification-summary.json').write_text(json.dumps(summary,indent=2)+'\n')
references=[]
reference_root=Path(__file__).resolve().parent.parent/'android/verification'
for original in sorted(reference_root.glob('browser-reference-*.png')):
 shutil.copy2(original,out/original.name)
 references.append(f'<figure><figcaption>{html.escape(original.stem)}</figcaption><a href="{original.name}"><img loading="lazy" src="{original.name}" alt="冻结视觉参考"></a></figure>')
frozen='<details><summary>原始冻结视觉参考</summary><p>这些是设计原型的浏览器参考，包含历史示例读数；只用于核对结构、圆角和比例。真实 API、明确授权的深色与图形精度调整另行检查。</p>'+''.join(references)+'</details>'
page='''<!doctype html><html lang="zh"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>BARO · 原生视觉复核</title><style>
*{box-sizing:border-box}body{margin:0;background:#0b0b0a;color:#fffffb;font:16px/1.6 system-ui,sans-serif}main{max-width:1680px;margin:auto;padding:28px}h1{font-size:28px;margin:0}h2{font-size:21px;margin-top:32px}p{color:#cecec4}a{color:#e6b49d}select,button{font:inherit;border:1px solid #68685e;border-radius:8px;background:#181817;color:#fffffb;padding:8px 12px}label{margin-right:12px}.controls{display:flex;flex-wrap:wrap;gap:12px;position:sticky;top:0;background:#0b0b0af5;padding:14px 0;z-index:2}.screens{display:flex;align-items:flex-start;gap:16px}.screens img{display:block;width:100%;height:auto;border:1px solid #44443d;border-radius:10px}.screens figure{margin:0;min-width:0;flex:1}.screens figure[hidden]{display:none}figcaption{font-size:14px;color:#cecec4;margin:6px 0}img{max-width:100%}details{border:1px solid #44443d;border-radius:10px;padding:16px;margin:22px 0}summary{cursor:pointer}video{width:100%;max-width:1100px;border-radius:10px}table{border-collapse:collapse;font-size:14px}td,th{padding:8px 14px;text-align:left;border-bottom:1px solid #44443d}code{overflow-wrap:anywhere}small{color:#cecec4}.stats{background:#181817;padding:14px 18px;border-radius:10px}@media(max-width:800px){main{padding:16px}.screens{display:block}.screens figure{margin-bottom:20px}}
</style><main><h1>BARO · 小圆角、对比度与大尺寸仪表</h1><p>实际 Android 原生截图与测试记录。源码 <code>__SOURCE__</code> · 开发预览 0.10.0 / code 12。</p><div class="stats">39 个原生测试方法 · __GLYPHS__ 种文字状态 · __FIGURES__ 次主图测量（最低 __MIN__%） · __CORNERS__ 块卡片四角检查 · 68 张新版截图</div><div class="controls"><select id="lang"><option value="en">English</option><option value="zh">中文</option></select><select id="orientation"><option value="landscape">横屏</option><option value="portrait">竖屏</option></select><select id="purpose"></select><label><input type="checkbox" id="weather">天气页</label><label><input type="checkbox" id="compare">并排对照 R2</label></div><p id="caption"></p><div class="screens"><figure id="old" hidden><figcaption>R2：前一轮实际截图，API 时段不同</figcaption><a id="old-link"><img id="old-img" alt="前一轮原生截图"></a></figure><figure><figcaption id="new-caption">当前版</figcaption><a id="new-link"><img id="new-img" alt="当前版原生截图"></a></figure></div><p>点击图片打开原始分辨率。R2 对照仅用于圆角、底色和排版观察；不同时间的真实 API 读数不用于整图像素一致性判定。</p><details><summary>高缩放横竖屏：完整固定一屏</summary><p>真实 400-dpi 显示覆盖两种语言、横竖屏、十二用途；检查上下拖动后页面不位移，拨轮仍能选择用途，左右滑动仍能切页。以下截图包含天气页和三个代表用途。</p><div class="screens">__SMALL__</div></details>__FROZEN__<h2>拨轮交互录像</h2><p>下方为当前 APK 的实际拨轮操作录像，未补帧。录像含测试初始化及关闭 Activity 的过程，另存为<a href="refinement-interaction.mp4">诊断原片</a>。软件模拟器录屏不代表真机刷新率或物理振动。</p><video controls preload="metadata" src="refinement-interaction.mp4"></video><h2>当前版资源采样</h2><img loading="lazy" src="resource-chart.svg" alt="八种独立进程PSS采样图"><p>CPU 为多个线程合计，可超过 100%；后台列为 HOME 后早期采样，Fine 软件渲染可能仍在完成已提交帧。早期采样后继续等待 Activity STOPPED 且 CPU 低于 2%，再切换配置。PSS、CPU 和模拟电池不能换算成真实瓦数或续航。前一 a27b9bb 版本采样中的模拟系统重启保存在历史目录，未混入本版资源数据。</p><table><thead><tr><th>配置</th><th>PSS MiB</th><th>前台 CPU</th><th>早期后台 CPU</th><th>停稳后 CPU</th></tr></thead><tbody>__RESOURCES__</tbody></table>__HISTORY__<h2>可核对文件</h2><p><a href="__APK__">当前开发 APK</a> · <a href="apk-inspection.json">签名与下载核验</a> · <a href="verification-summary.json">本轮汇总</a> · <a href="reviewed-captures.json">截图检查清单</a></p><p><a href="native-regression.log">38 项原生回归</a> · <a href="small-regression.log">高缩放测试</a> · <a href="text-audit.json">常规文字</a> · <a href="variant-text-audit.json">异常文字</a> · <a href="main-figure-audit.json">主图高度</a> · <a href="surface-audit.json">圆角像素</a> · <a href="wind-composition-audit.json">风向盘与留白</a></p><p><a href="live-visual-provenance.json">真实数据来源</a> · <a href="live-visual-response.json">该轮原始 API 响应</a> · <a href="production-profiles/profiles.json">独立进程资源原始记录</a></p><small>实际执行覆盖限定于这些记录；不作所有设备无缺陷、真机功耗、真机触感或公开服务已上线的推断。</small></main><script>
const files=__FILES__;const names=['通勤','步行','骑行','户外停留','晚霞拍摄','晨光拍摄','蓝调时段','观星','远眺','露营','居家建议','储存建议'];const el=id=>document.getElementById(id);names.forEach((s,i)=>el('purpose').add(new Option(`${String(i+1).padStart(2,'0')} · ${s}`,i)));function update(){const l=el('lang').value,o=el('orientation').value,p=el('purpose').value,w=el('weather').checked;const src=w?`native-weather-${o}-${l}.png`:files[`${o}-${l}-${p}`];el('new-img').src=src;el('new-link').href=src;el('old').hidden=!(el('compare').checked&&!w&&p==='0');el('old-img').src=`previous-${o}-${l}.png`;el('old-link').href=el('old-img').src;el('caption').textContent=`${o==='landscape'?'3408×2272':'2272×3408'} · ${l==='zh'?'中文':'English'} · ${w?'天气':names[p]}`;el('purpose').disabled=w;}['lang','orientation','purpose','weather','compare'].forEach(id=>el(id).addEventListener('change',update));update();
</script></html>'''
small=''.join(f'<figure><figcaption>{html.escape(name)}</figcaption><a href="{name}"><img loading="lazy" src="{name}" alt="高缩放单屏截图"></a></figure>'for name in small_names)

for key,value in {'APK':f'BARO-{identity["versionName"]}-{source[:7]}.apk','SOURCE':html.escape(source),'GLYPHS':str(len(text)+len(variants)),'FIGURES':str(len(figures['cases'])),'MIN':f"{summary['minimumMainFigureHeightFraction']*100:.1f}",'CORNERS':str(len(surfaces['cases'])),'SMALL':small,'FROZEN':frozen,'RESOURCES':resource_rows,'HISTORY':history,'FILES':json.dumps(files)}.items():page=page.replace('__'+key+'__',value)
(out/'gallery.html').write_text(page)
print(json.dumps(summary,ensure_ascii=False,indent=2))
