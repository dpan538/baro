import { APP_META } from './app-meta.js';

function releaseVersionParts(value){
  if(typeof value!=='string')return null;
  const match=/^v?(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([\da-zA-Z-]+(?:\.[\da-zA-Z-]+)*))?(?:\+[\da-zA-Z-]+(?:\.[\da-zA-Z-]+)*)?$/.exec(value);
  if(!match)return null;
  const core=match.slice(1,4).map(Number),pre=match[4]?.split('.')??[];
  if(core.some(x=>!Number.isSafeInteger(x))||pre.some(x=>/^\d+$/.test(x)&&(!Number.isSafeInteger(+x)||/^0\d/.test(x))))return null;
  return {core,pre};
}
// Return the ordering, or null when a repository uses a non-semantic release tag.
export function compareReleaseVersions(a,b){
  const left=releaseVersionParts(a),right=releaseVersionParts(b);if(!left||!right)return null;
  for(let i=0;i<3;i++)if(left.core[i]!==right.core[i])return Math.sign(left.core[i]-right.core[i]);
  if(!left.pre.length||!right.pre.length)return Math.sign(right.pre.length-left.pre.length);
  for(let i=0;i<Math.max(left.pre.length,right.pre.length);i++){
    const x=left.pre[i],y=right.pre[i];if(x===y)continue;if(x===undefined)return -1;if(y===undefined)return 1;
    const nx=/^\d+$/.test(x),ny=/^\d+$/.test(y);
    if(nx&&ny)return Math.sign(+x-+y);if(nx!==ny)return nx?-1:1;return x<y?-1:1;
  }
  return 0;
}
export function releaseInformation(payload,currentVersion){
  if(!payload||payload.draft||payload.prerelease||typeof payload.tag_name!=='string'||!payload.tag_name.trim())throw new Error('Invalid published release');
  const tag=payload.tag_name.trim(),comparison=compareReleaseVersions(tag,currentVersion);
  return {state:comparison===null?'published':comparison>0?'available':'current',tag,
    url:APP_META.updateUrl+'/tag/'+encodeURIComponent(tag),
    publishedAt:typeof payload.published_at==='string'&&Number.isFinite(Date.parse(payload.published_at))?payload.published_at:null};
}
export async function checkLatestRelease(currentVersion,{fetcher=fetch,signal,timeoutMs=8000}={}){
  const controller=new AbortController(),abort=()=>controller.abort();
  if(signal?.aborted)throw new DOMException('Cancelled','AbortError');
  signal?.addEventListener('abort',abort,{once:true});
  const timeout=setTimeout(abort,timeoutMs);
  try{
    const repository=new URL(APP_META.repositoryUrl),path=repository.pathname.replace(/\/+$/,'');
    const response=await fetcher('https://api.github.com/repos'+path+'/releases/latest',{
      headers:{Accept:'application/vnd.github+json'},credentials:'omit',cache:'no-store',signal:controller.signal
    });
    if(response.status===404)return {state:'empty',tag:null,url:APP_META.updateUrl,publishedAt:null};
    if(!response.ok)throw new Error('Release check failed: '+response.status);
    return releaseInformation(await response.json(),currentVersion);
  }finally{clearTimeout(timeout);signal?.removeEventListener('abort',abort);}
}
