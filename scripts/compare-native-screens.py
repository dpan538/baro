#!/usr/bin/env python3
"""Measured visual QA; never auto-accept native UI from a low aggregate score."""
import argparse,hashlib,json,pathlib
import numpy as np
from PIL import Image
p=argparse.ArgumentParser();p.add_argument('--native',type=pathlib.Path,required=True);p.add_argument('--reference',type=pathlib.Path,required=True);p.add_argument('--reference-prefix',default='');p.add_argument('--output',type=pathlib.Path,required=True);a=p.parse_args();a.output.mkdir(parents=True,exist_ok=True)
rows=[]
for source in sorted(a.native.glob('native-*.png')):
 case=source.stem.removeprefix('native-');ref=a.reference/(a.reference_prefix+case+'.png')
 if not ref.exists():continue
 original=Image.open(source).convert('RGB');reference=Image.open(ref).convert('RGB')
 assert (original.width>original.height)==('landscape' in case),f'Wrong actual orientation: {case}'
 normalized=original.resize(reference.size,Image.Resampling.LANCZOS);delta=np.abs(np.asarray(normalized,dtype=np.float32)-np.asarray(reference,dtype=np.float32))
 Image.blend(reference,normalized,.5).save(a.output/('overlay-'+case+'.png'));Image.fromarray(np.uint8(np.clip(delta*3,0,255))).save(a.output/('difference-'+case+'.png'))
 rows.append(dict(case=case,nativeSize=original.size,referenceSize=reference.size,nativeSha256=hashlib.sha256(source.read_bytes()).hexdigest(),referenceSha256=hashlib.sha256(ref.read_bytes()).hexdigest(),meanAbsoluteRGBDifference=float(delta.mean()),fractionPixelsOver16=float((delta.max(axis=2)>16).mean()),accepted=False,notice='Manual geometry/type/data/animation review is still required; system overlays and missing content are rejected.'))
(a.output/'comparison.json').write_text(json.dumps(rows,indent=2)+'\n');print(json.dumps(rows,indent=2))
