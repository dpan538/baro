"""Reproduce native GPU sources from the frozen, reviewed JS shader programs."""
from pathlib import Path
import re,hashlib,json
root=Path(__file__).resolve().parents[1]
out=root/'android/app/src/main/assets/shaders'
out.mkdir(parents=True,exist_ok=True)
items=[('dist/weather-shader.js','VERTEX','weather.vert'),('dist/weather-shader.js','COPY','copy.frag'),('dist/weather-shader.js','ART','weather.frag'),('dist/instrument-art.js','INSTRUMENT_VERTEX','instrument.vert'),('dist/instrument-art.js','INSTRUMENT_FRAGMENT','instrument.frag')]
manifest=[]
for source,name,target in items:
 text=(root/source).read_text()
 shader=re.search(r'const '+name+r'\s*=\s*`(.*?)`;',text,re.S)[1]
 shader=shader.replace('${WEATHER_LOOP.seconds-WEATHER_LOOP.blendSeconds}','41').replace('${WEATHER_LOOP.blendSeconds}','4').replace('${WEATHER_LOOP.seconds}','45')
 for key,value in dict(saturation='1.01',peak='.97',shadowGain='.74',highlightRemainder='.74',lowKnee='.16',highKnee='.76').items(): shader=shader.replace('${PIXEL_TONE.'+key+'}',value)
 if '${' in shader: raise ValueError('Unresolved shader interpolation')
 shader=shader.rstrip()
 (out/target).write_text(shader+'\n')
 manifest.append(dict(source=source,constant=name,target=target,sha256=hashlib.sha256((shader+'\n').encode()).hexdigest()))
(out/'provenance.json').write_text(json.dumps(manifest,indent=2)+'\n')
