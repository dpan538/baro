#!/usr/bin/env python3
"""Dedicated-emulator, real-GLES 300-cycle collection. No fixtures or uiOnly override."""
from pathlib import Path
import argparse, hashlib, json, subprocess, time, zipfile, shutil, re

ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser()
p.add_argument('--output', type=Path, required=True)
p.add_argument('--start', type=int, default=1)
p.add_argument('--end', type=int, default=300)
p.add_argument('--batch', type=int, default=15)
a = p.parse_args()
assert 1 <= a.start <= a.end <= 300 and a.batch > 0
out = a.output.resolve(); out.mkdir(parents=True, exist_ok=True)
(out/'batches').mkdir(exist_ok=True); (out/'samples').mkdir(exist_ok=True)
adb = [str(ROOT/'android/.tooling/sdk/platform-tools/adb'), '-P', '5038', '-s', 'emulator-5566']
package = 'studio.sundown.weather'
remote = '/sdcard/Android/data/'+package+'/files/'
def call(*args, timeout=90):
    r = subprocess.run(adb+list(args), capture_output=True, timeout=timeout)
    if r.returncode: raise RuntimeError(r.stderr.decode(errors='replace'))
    return r.stdout.decode(errors='replace')
def pull(name, target): call('pull', remote+name, str(target))
def shell(command): return call('shell', command)
def write(name, content): (out/name).write_text(content)
apk = ROOT/'android/app/build/outputs/apk/debug/app-debug.apk'
with zipfile.ZipFile(apk) as z: identity = json.loads(z.read('assets/build-identity.json'))
identity['apkSha256'] = hashlib.sha256(apk.read_bytes()).hexdigest()
identity['testApkSha256'] = hashlib.sha256((ROOT/'android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk').read_bytes()).hexdigest()
write('identity.json',json.dumps(identity,indent=2));shutil.copy2(apk,out/'BARO-native-tested.apk')
for f in [apk,ROOT/'android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']: call('install','-r',str(f))
call('shell','wm','density','320');call('shell','settings','put','secure','immersive_mode_confirmations','confirmed')
call('shell','settings','put','global','animator_duration_scale','1')
write('battery-before.txt',shell('dumpsys battery'))
write('batterystats-before.txt',shell('dumpsys batterystats '+package))
write('logcat-before.txt',shell('logcat -d -v epoch -t 200'))
rows=[];cold=[]
for start in range(a.start,a.end+1,a.batch):
    count=min(a.batch,a.end-start+1)
    orientation=0 if (start-1)//75%2==0 else 1
    call('shell','wm','user-rotation','lock',str(orientation))
    # Production-only cold process: Android-reported first draw and dumpsys, no test runner.
    call('shell','am','force-stop',package)
    began=time.time();launch=shell('am start -W -n '+package+'/.MainActivity');time.sleep(2)
    mem=shell('dumpsys meminfo '+package);pid=shell('pidof '+package).strip()
    cpu0=shell('run-as '+package+' cat /proc/'+pid+'/stat');time.sleep(1)
    cpu1=shell('run-as '+package+' cat /proc/'+pid+'/stat')
    sample={'beforeRound':start,'epochMs':int(began*1000),'orientation':orientation,'pid':int(pid),'amStart':launch,'meminfo':mem,'procStatBefore':cpu0,'procStatAfter':cpu1}
    cold.append(sample);write('cold-starts.json',json.dumps(cold,indent=2))
    write(f'samples/cold-{start}-gfxinfo.txt',shell('dumpsys gfxinfo '+package))
    call('shell','am','force-stop',package)
    log=out/'batches'/f'{start:03}-{start+count-1:03}.log'
    command=adb+['shell','am','instrument','-w','-e','class',package+'.NativeSoakTest','-e','startRound',str(start),'-e','roundCount',str(count),package+'.test/androidx.test.runner.AndroidJUnitRunner']
    with log.open('w') as f:
        process=subprocess.Popen(command,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
        for line in process.stdout:
            f.write(line);f.flush()
            if 'soakRound=' in line: print(line.strip(),flush=True)
        process.wait()
    target=out/'batches'/f'{start:03}-{start+count-1:03}.jsonl';pull(f'soak-{start}.jsonl',target)
    batch=[json.loads(x) for x in target.read_text().splitlines() if x.strip()]
    rows.extend(batch);write('rounds.jsonl',''.join(json.dumps(row)+'\n'for row in rows))
    write(f'samples/batterystats-after-{start+count-1}.txt',shell('dumpsys batterystats '+package))
    for n in range(start,start+count):
        if n%25==0:
            for page in ['observation','weather']:pull(f'soak-{n}-{page}.png',out/f'soak-{n}-{page}.png')
    assert process.returncode==0 and f'OK (1 test)' in log.read_text() and len(batch)==count and all(row['status']=='pass'for row in batch),f'Batch failed; preserved evidence: {log}'
write('battery-after.txt',shell('dumpsys battery'))
write('batterystats-after.txt',shell('dumpsys batterystats '+package))
write('logcat-after.txt',shell('logcat -d -v epoch -t 4000'))
assert len(rows)==a.end-a.start+1
print(f'Completed {len(rows)} actual launch/interact/close cycles; {len(cold)} production cold starts.',flush=True)
