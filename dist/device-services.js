import { APP_META } from './app-meta.js';
const ownedCaches={weather:'sundown-weather-v1',maps:'sundown-maps-v1'};
function nativeResult(method,...args){
  try { const value=window.SundownDevice?.[method]?.(...args);return typeof value==='string'?JSON.parse(value):value; }
  catch { return null; }
}
export const deviceServices={
  version(){return nativeResult('getVersionInfo')??{version:APP_META.version,build:APP_META.build,versionCode:APP_META.versionCode};},
  location(){return nativeResult('getLocationState')??{enabled:false,status:'preview'};},
  setLocation(enabled){return nativeResult('setLocationEnabled',enabled)??{enabled:false,status:'preview'};},
  async cache(){
    const native=nativeResult('getCacheInfo');if(native)return native;if(window.SundownDevice)throw new Error('Native cache unavailable');
    const sizes={weather:0,maps:0};
    if(!window.caches)return {...sizes,total:0};
    const names=await caches.keys();
    for(const [kind,name] of Object.entries(ownedCaches))if(names.includes(name)){
      const cache=await caches.open(name);for(const request of await cache.keys()){const response=await cache.match(request);if(response)sizes[kind]+=(await response.arrayBuffer()).byteLength;}
    }
    return {...sizes,total:sizes.weather+sizes.maps};
  },
  async clearCache(){
    const native=nativeResult('clearDataCache');if(native)return native;if(window.SundownDevice)throw new Error('Native cache clear failed');
    if(window.caches)for(const name of Object.values(ownedCaches))await caches.delete(name);
    return this.cache();
  },
  async copy(text){
    if(window.SundownDevice)return Boolean(nativeResult('copyFeedback',text));
    if(!navigator.clipboard?.writeText)return false;
    try {await navigator.clipboard.writeText(text);return true;}catch{return false;}
  },
  external(url){
    if(!/^https:\/\//.test(url))return false;
    if(window.SundownDevice)return Boolean(nativeResult('openExternal',url));
    try {window.open(url,'_blank','noopener,noreferrer');return true;}catch{return false;}
  }
};
