#!/usr/bin/env python3
"""Inspect a real APK, optionally an actual downloaded copy. Never publish a release."""
import argparse,hashlib,json,pathlib,re,shutil,subprocess,tempfile,urllib.request,zipfile
p=argparse.ArgumentParser();p.add_argument('apk',type=pathlib.Path);p.add_argument('--build-tools',type=pathlib.Path,required=True);p.add_argument('--aapt',type=pathlib.Path);p.add_argument('--output',type=pathlib.Path,required=True);p.add_argument('--url');p.add_argument('--manifest-url');a=p.parse_args()
apk=a.apk.resolve();raw=apk.read_bytes();digest=hashlib.sha256(raw).hexdigest()
verified=subprocess.check_output([str(a.build_tools/'apksigner'),'verify','--verbose','--print-certs',str(apk)],text=True)
cert=re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]{64})',verified)[1]
meta=subprocess.check_output([str(a.aapt or a.build_tools/'aapt'),'dump','badging',str(apk)],text=True)
identity=re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'",meta)
package,code,version=identity.groups();assert package=='studio.sundown.weather'
with zipfile.ZipFile(apk) as z:
 assert z.testzip() is None
 names=z.namelist()
 assert not any(re.search(rb'Lstudio/sundown/weather/(?:Native[^;/]*Test|UpdateFixtureTest|NativeTestEnvironment|DemoFactory);',z.read(name)) for name in names if re.fullmatch(r'classes[0-9]*\.dex',name)),'Test/sample code unexpectedly bundled'
 assert not any(name.endswith('live-brisbane.json') for name in names),'Recorded test response unexpectedly bundled'
 for name in names:
  if name.startswith('assets/layouts/') and name.endswith('.json') and not name.endswith('/icons.json'):
   nodes=json.loads(z.read(name))['nodes']
   assert all('text' not in n for value in nodes.values() for n in (value if isinstance(value,list) else [value])),'Example text unexpectedly bundled in layout geometry'
 assert any(n.endswith('shaders/weather.frag') for n in names);assert any(n.endswith('shaders/instrument.frag') for n in names)
 assert not any(n.endswith('index.html') for n in names),'Web UI unexpectedly bundled'
 for name in ('Inter.ttf','RobotoMono.ttf','MiSansVF.ttf'):assert any(n.endswith('fonts/'+name) for n in names)
 identity_asset=json.loads(z.read('assets/build-identity.json')) if 'assets/build-identity.json' in names else None
 launcher_identity=json.loads(z.read('assets/launcher-identity.json')) if 'assets/launcher-identity.json' in names else None
 if launcher_identity:
  from PIL import Image
  import io
  assert launcher_identity['selection']=='G1b'
  root=pathlib.Path(__file__).resolve().parents[1]
  resources=subprocess.check_output([str(a.aapt or a.build_tools/'aapt'),'dump','--values','resources',str(apk)],text=True)
  for resource,source in [('drawable/baro_cloud_g1b','android/app/src/main/res/drawable-nodpi/baro_cloud_g1b.png'),('mipmap/ic_launcher','branding/icons/v10/G1b-darker/icon-512.png')]:
   entry=re.search(r'\bresource 0x[a-f0-9]+ studio\.sundown\.weather:'+re.escape(resource)+r': [^\n]+\n\s+\(string8\) "([^"]+\.png)"',resources)[1]
   packaged=Image.open(io.BytesIO(z.read(entry))).convert('RGBA');expected=Image.open(root/source).convert('RGBA')
   # AAPT canonicalizes unused RGB channels in fully transparent pixels.
   assert packaged.size==expected.size and all(x==y or x[3]==y[3]==0 for x,y in zip(packaged.getdata(),expected.getdata())),'Selected G1b visible launcher pixels differ in APK'
  assert 'studio.sundown.weather:drawable/ic_launcher:' not in resources,'Obsolete sunrise launcher remains'
  launcher_id=re.search(r'spec resource (0x[a-f0-9]+) studio\.sundown\.weather:mipmap/ic_launcher:',resources)[1]
  manifest_tree=subprocess.check_output([str(a.aapt or a.build_tools/'aapt'),'dump','xmltree',str(apk),'AndroidManifest.xml'],text=True)
  assert re.search(r'android:icon\([^)]*\)=@'+launcher_id,manifest_tree),'Manifest does not reference the selected launcher'
record=dict(sourceIdentity=identity_asset,packageName=package,versionCode=int(code),versionName=version,size=len(raw),sha256=digest,certificateSha256=cert,debuggable='application-debuggable' in meta,signatureVerified=True,productionSampleDataAbsent=True,publicDownloadVerified=False)
if launcher_identity:record['launcherIdentity']=launcher_identity;record['selectedLauncherPixelsVerified']=True
if identity_asset:
 assert identity_asset['packageName']==package and identity_asset['versionCode']==int(code) and identity_asset['versionName']==version
if a.url:
 with tempfile.TemporaryDirectory() as tmp:
  copied=pathlib.Path(tmp)/'download.apk'
  with urllib.request.urlopen(a.url,timeout=20) as response, copied.open('wb') as out:shutil.copyfileobj(response,out)
  assert hashlib.sha256(copied.read_bytes()).hexdigest()==digest
 record['downloadCopyVerified']=True;record['downloadUrl']=a.url
 record['publicDownloadVerified']=a.url.startswith('https://github.com/dpan538/baro/releases/download/')
if a.manifest_url:
 assert a.manifest_url.startswith('https://github.com/dpan538/baro/releases/download/')
 record['url']=a.manifest_url
 record['manifestNotice']='A URL in this local manifest does not establish that an asset exists; verify actual publication separately.'
a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(record,indent=2)+'\n');print(json.dumps(record,indent=2))
