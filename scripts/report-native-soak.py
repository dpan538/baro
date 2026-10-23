#!/usr/bin/env python3
"""Render actual round rows as CSV, self-contained HTML and a simple SVG chart."""
import argparse,csv,html,json,statistics
from collections import Counter
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('directory',type=Path);p.add_argument('--partial',action='store_true');args=p.parse_args();out=args.directory
rows=[json.loads(x)for x in (out/'rounds.jsonl').read_text().splitlines()if x.strip()]
if not args.partial:
 assert len(rows)==300 and [r['round']for r in rows]==list(range(1,301))
 assert all(r['status']=='pass'for r in rows)
 assert len({r['pid']for r in rows})==1,'Default continuous acceptance requires one process'
 assert all(r['afterClose']['artThreads']==0 for r in rows)
assert rows
fields=['round','status','weatherState','language','orientation','purpose','pixels','motion','pid','launchToFirstArtMs','durationMs','postInteractionCpuPercent','foregroundPssMiB','closedPssMiB','rssMiB','javaHeapMiB','nativeHeapMiB','fdCount','closedFdCount','closedArtThreads','providerFetchedAt','cacheBytes']
table=[]
for r in rows:
 f=r.get('foreground',{});c=r.get('afterClose',{});table.append(dict(round=r['round'],status=r['status'],weatherState=r.get('weatherState','ready'),language=r['language'],orientation=r.get('orientation'),purpose=r['purpose'],pixels=r['pixels'],motion=r['motion'],pid=r['pid'],launchToFirstArtMs=r.get('launchToFirstArtMs'),durationMs=r['durationMs'],postInteractionCpuPercent=round(100*r.get('idleCpuMs',0)/max(1,r.get('idleWindowMs',1)),2),foregroundPssMiB=round(f.get('pssKiB',0)/1024,2),closedPssMiB=round(c.get('pssKiB',0)/1024,2),rssMiB=round(f.get('rssKiB',0)/1024,2),javaHeapMiB=round(f.get('javaHeapKiB',0)/1024,2),nativeHeapMiB=round(f.get('nativeHeapKiB',0)/1024,2),fdCount=f.get('fdCount'),closedFdCount=c.get('fdCount'),closedArtThreads=c.get('artThreads'),providerFetchedAt=r.get('providerFetchedAt'),cacheBytes=r.get('cacheBytes')))
with (out/'rounds.csv').open('w')as f:w=csv.DictWriter(f,fieldnames=fields);w.writeheader();w.writerows(table)
def stats(key):
 v=sorted(r[key]for r in table);return {'min':v[0],'median':statistics.median(v),'p95':v[min(len(v)-1,int(.95*len(v)))],'max':v[-1]}
summary={'rounds':len(rows),'passed':sum(r['status']=='pass'for r in rows),'source':rows[0]['source'],'processCount':len({r['pid']for r in rows}),'continuous':len({r['pid']for r in rows})==1,'totalMeasuredCycleSeconds':sum(r['durationMs']for r in rows)/1000,'foregroundPssMiB':stats('foregroundPssMiB'),'closedPssMiB':stats('closedPssMiB'),'launchToFirstArtMs':stats('launchToFirstArtMs'),'postInteractionCpuPercent':stats('postInteractionCpuPercent'),'closedThreadsAllZero':all(r['closedArtThreads']==0 for r in table),'first30ClosedPssMedianMiB':statistics.median(r['closedPssMiB']for r in table[:30]),'last30ClosedPssMedianMiB':statistics.median(r['closedPssMiB']for r in table[-30:]),'maxClosedFdCount':max(r['closedFdCount']for r in table),'providerFetchTimestamps':sorted({r['providerFetchedAt']for r in rows}),'cacheByteMaximum':max(r['cacheBytes']for r in rows),'powerMeasured':False,'powerLimitation':'Emulator charging battery and unsupported CPU/GPU energy counters cannot measure watts, mAh or hardware battery life.','instrumentationOverhead':'Round memory includes instrumentation and periodic screenshots. Production-only meminfo samples are separate. CPU sums all app threads, so >100% is possible.'}
summary['matrix']={key:dict(Counter(str(row[key]).lower()for row in rows))for key in ['language','orientation','purpose','pixels','motion']}
summary['checks']=dict(Counter(check for row in rows for check in row.get('checks',[])))
summary['backgroundResumeGroups']=sum(row.get('backgroundResume',False)for row in rows)
summary['recreationGroups']=sum(row.get('recreated',False)for row in rows)
summary['closedFdRange']=[min(r['closedFdCount']for r in table),max(r['closedFdCount']for r in table)]
summary['rssMiB']=stats('rssMiB')
summary['providerFetchIntervalsSeconds']=[b-a for a,b in zip(summary['providerFetchTimestamps'],summary['providerFetchTimestamps'][1:])]
summary['providerResponseCount']=len({r['providerResponseSha256']for r in rows})
summary['weatherStates']=dict(Counter(r.get('weatherState','ready')for r in rows))
summary['memoryInterpretation']='First/last medians and descriptor counts describe this bounded run. They do not prove the absence of every possible memory leak.'
(out/'summary.json').write_text(json.dumps(summary,indent=2))
W,H=1200,1000;left,right=95,1160;width=right-left
svg=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}"><rect width="100%" height="100%" fill="#fafaf7"/><style>text{{font-family:Arial,sans-serif;fill:#252525;font-size:16px}}.small{{font-size:13px;fill:#555}}</style>',f'<text x="45" y="38" font-size="25">BARO · {len(rows)} actual native launch / interaction / close cycles</text>',f'<text x="45" y="65" class="small">Source {rows[0]["source"][:12]} · same process · 3408×2272 / 2272×3408 software emulator · not hardware power</text>']
for top,title,keys,colors,unit in [(120,'Process memory (includes instrumentation)',['foregroundPssMiB','closedPssMiB'],['#17679a','#b75c2a'],'MiB'),(400,'Post-interaction process CPU, 1-second window',['postInteractionCpuPercent'],['#336b49'],'%'),(680,'Launch to first real GLES art frame',['launchToFirstArtMs'],['#75458a'],'s')]:
 height=180;values=[r[k]/(1000 if k=='launchToFirstArtMs'else 1)for r in table for k in keys];maximum=max(values)*1.12 or 1
 svg.append(f'<text x="45" y="{top-30}" font-size="21">{title}</text>')
 for n in range(5):
  y=top+height-n*height/4;v=maximum*n/4;svg.extend([f'<path d="M{left} {y}H{right}" stroke="#d9dcd9"/>',f'<text x="{left-12}" y="{y+5}" text-anchor="end" class="small">{v:.0f} {unit}</text>'])
 for n in [1,50,100,150,200,250,300]:
  x=left+(n-1)/299*width;svg.extend([f'<path d="M{x} {top}V{top+height}" stroke="#eceeeb"/>',f'<text x="{x}" y="{top+height+24}" text-anchor="middle" class="small">{n}</text>'])
 for key,color in zip(keys,colors):
  points=' '.join(f'{left+(r["round"]-1)/299*width:.2f},{top+height-(r[key]/(1000 if key=="launchToFirstArtMs"else 1))/maximum*height:.2f}'for r in table)
  svg.append(f'<polyline points="{points}" fill="none" stroke="{color}" stroke-width="1.6"/>')
  for r in table:
   if r['pixels']=='fine':svg.append(f'<circle cx="{left+(r["round"]-1)/299*width}" cy="{top+height-(r[key]/(1000 if key=="launchToFirstArtMs"else 1))/maximum*height}" r="2.2" fill="{color}"/>')
 if len(keys)==2:svg.append(f'<text x="{left}" y="{top-7}" class="small">Blue: foreground PSS · Orange: after close PSS · dots: Fine mode</text>')
svg.extend([f'<text x="45" y="970" class="small">Passed {summary["passed"]}/{len(rows)} · art threads after every close: 0 · CPU may exceed 100% across cores · raw rows in rounds.csv</text>','</svg>']);(out/'rounds-chart.svg').write_text(''.join(svg))
links='<a href="rounds.csv">300轮 CSV</a> · <a href="rounds.jsonl">原始 JSONL</a> · <a href="summary.json">统计摘要</a> · <a href="identity.json">APK 身份</a> · <a href="BARO-native-tested.apk">受测 APK</a>'
body=f'''<!doctype html><html lang="zh"><meta charset="utf-8"><title>BARO 300轮实测</title><style>body{{font:16px system-ui,sans-serif;margin:32px;background:#fafaf7;color:#222}}main{{max-width:1280px;margin:auto}}a{{color:#17679a}}img{{width:100%;height:auto}}table{{border-collapse:collapse;width:100%;font-variant-numeric:tabular-nums}}td,th{{padding:9px;border-bottom:1px solid #ddd;text-align:right;white-space:nowrap}}th{{position:sticky;top:0;background:#f0f1eb}}.scroll{{overflow:auto;max-height:600px}}p{{line-height:1.6}}select{{font:inherit;padding:8px}}</style><main><h1>BARO · {summary['passed']} / {len(rows)} 组通过</h1><p>每一行都是一次真实打开、交互和关闭。连续进程：{summary['continuous']}。原始日志、逐轮数据和 APK 身份均可核对。</p><p>{links}</p><p>图中内存包含测试进程与定期截图开销；独立生产冷启动另有 dumpsys 原始采样。ready 表示最新结果／有效缓存，offline 表示已明确标示的真实过期缓存；offline 轮次不算新的 API 读取。CPU 为所有线程合计，可能超过 100%。模拟器电量和能量计数器不支持真实功耗测量，因此没有换算瓦数、耗电量或续航。</p><img src="rounds-chart.svg" alt="全部300轮内存、CPU和启动到实际绘画首帧的曲线"><p>筛选：<select id="filter"><option value="all">全部轮次</option><option value="fine">Fine</option><option value="block">Block</option><option value="fail">失败</option></select></p><div class="scroll"><table><thead><tr><th>轮次</th><th>语言／方向</th><th>模式</th><th>首帧 s</th><th>PSS MiB</th><th>关闭 MiB</th><th>CPU %</th><th>关闭渲染线程</th><th>数据状态</th><th>结果</th></tr></thead><tbody>'''
for r in table:body+=f'<tr data-mode="{r["pixels"]}" data-status="{r["status"]}"><td>{r["round"]}</td><td>{r["language"]} / {r["orientation"]}</td><td>{r["pixels"]}</td><td>{r["launchToFirstArtMs"]/1000:.2f}</td><td>{r["foregroundPssMiB"]}</td><td>{r["closedPssMiB"]}</td><td>{r["postInteractionCpuPercent"]}</td><td>{r["closedArtThreads"]}</td><td>{r["weatherState"]}</td><td>{r["status"]}</td></tr>'
body+='</tbody></table></div><script>document.querySelector("#filter").onchange=e=>document.querySelectorAll("tbody tr").forEach(r=>r.hidden=!(e.target.value==="all"||r.dataset.mode===e.target.value||r.dataset.status===e.target.value));</script></main></html>'
(out/'report.html').write_text(body);print(json.dumps(summary,indent=2))
