import { APP_META } from './app-meta.js';
import { WEATHER_PRESETS, resolveTimeContext } from './weather-art.js';
import { sceneName } from './i18n.js';
import { resolveWeatherIcon, weatherIconMarkup } from './weather-icons.js';
import { formatUnitWeather } from './weather-units.js';
import { deviceServices } from './device-services.js';
const SETTING_COPY=Object.freeze({
  sourceFooter:['数据来源 Open-Meteo · BARO','Powered by Open-Meteo · BARO'],
  appearanceNav:['界面','Appearance'],unitsNav:['单位','Units'],aboutNav:['关于','About'],
  celsius:['摄氏','Celsius'],fahrenheit:['华氏','Fahrenheit'],knotsNote:['kts 表示节，即海里／小时。','kts = knots, or nautical miles per hour.'],
  checkUpdates:['检查更新','Check updates'],clearCache:['清除缓存','Clear cache'],
  sourceLicenses:['数据来源与许可','Sources & licenses'],feedback:['意见反馈','Feedback'],moreMenu:['更多','More'],
  typefaces:['字体','Typefaces'],fontLicenses:['MiSans 使用小米原始字体与许可；Inter、Roboto Mono 使用 SIL Open Font License 1.1。','Original Xiaomi MiSans files and license; Inter and Roboto Mono use SIL Open Font License 1.1.'],
  feedbackPrompt:['描述你遇到的问题或希望改进的地方。','Describe a problem or an improvement you would like.'],
  feedbackNote:['可在 GitHub 提交反馈，或复制内容后自行粘贴。','Submit feedback on GitHub, or copy the text and paste it yourself.'],
  copyFeedback:['复制反馈','Copy feedback'],githubFeedback:['在 GitHub 反馈','Feedback on GitHub'],
  dataPreview:['此版本使用示例天气；实时天气计划采用 Open-Meteo。','This build uses sample weather. Live weather is planned with Open-Meteo.'],
  locationNote:['仅在前台跟随定位；当前天气仍为示例。','Follows location while the app is visible. Weather remains sample data.'],
  sceneCount:['11 种天气场景','11 WEATHER SCENES'],
  scenesNote:['不同城市、不同时刻的示例；选择后同步更新天气信息与像素画。','Samples from different cities and local times. Select one to update the information and artwork.'],
  settings:['设置','Settings'],fine:['精细','Fine'],block:['方块','Block'],
  openSettings:['打开设置','Open settings'],closeSettings:['关闭设置','Close settings'],backSettings:['返回设置','Back to settings'],
  scenes:['天气场景','Weather scenes'],closeScenes:['关闭天气场景','Close weather scenes'],
  locationOff:['定位未开启','Location is off'],locationPreview:['请在 Android 安装包中启用定位。','Location is available in the Android app.'],
  locationWaiting:['正在等待定位…','Waiting for a location fix…'],locationPermission:['等待定位授权…','Waiting for location permission…'],
  locationDenied:['定位未获授权，可再次开启重试。','Location permission was not granted. Turn on to retry.'],locationSystemOff:['系统定位未开启，请在系统设置中开启。','System location is off. Enable it in Android settings.'],
  locationUnavailable:['暂时无法获取定位。','Location is temporarily unavailable.'],locationFix:['当前位置','Current location'],
  updateUnconfigured:['GitHub 仓库尚未配置。','The GitHub repository has not been configured.'],updateOpened:['请在浏览器中查看版本发布页。','View the release page in your browser.'],
  cacheWeather:['天气','Weather'],cacheMaps:['地图','Maps'],cacheBundle:['安装资源不计入数据缓存。','Bundled assets are excluded.'],cacheCleared:['数据缓存已清除，设置已保留。','Data cache cleared. Preferences kept.'],cacheFailed:['暂时无法读取或清理缓存，请重试。','Unable to read or clear cache. Try again.'],
  feedbackEmpty:['请先填写反馈内容。','Please enter your feedback first.'],feedbackCopied:['反馈已复制。','Feedback copied.'],feedbackFailed:['复制失败，请选择文本手动复制。','Copy failed. Select the text to copy manually.']
});
export function settingText(key,language){const pair=SETTING_COPY[key];if(!pair)throw new Error('Missing settings copy: '+key);return language==='en'?pair[1]:pair[0];}
export function formatBytes(bytes){if(!Number.isFinite(bytes)||bytes<=0)return '0 B';if(bytes<1024)return bytes+' B';if(bytes<1048576)return (bytes/1024).toFixed(1)+' KB';return (bytes/1048576).toFixed(1)+' MB';}
export class SettingsController{
  constructor({getState,change,selectScene}){
    this.getState=getState;this.change=change;this.selectScene=selectScene;this.view='home';this.detail=null;this.returnTo='home';this.locationState=deviceServices.location();this.version=deviceServices.version();this.cacheInfo={weather:0,maps:0,total:0};this.status={};
    this.home=document.querySelector('.information-panel');this.panel=document.querySelector('#settings-panel');this.scenes=document.querySelector('#scenes-panel');
    document.querySelector('#settings-open').addEventListener('click',()=>this.open('settings'));
    document.querySelector('#settings-close').addEventListener('click',()=>this.close());
    document.querySelector('#settings-back').addEventListener('click',()=>this.showDetail(null));
    document.querySelector('#scenes-open').addEventListener('click',()=>{this.returnTo='home';this.open('scenes');});
    document.querySelector('#settings-more').addEventListener('click',()=>this.showDetail('more'));
    document.querySelector('#scenes-close').addEventListener('click',()=>{if(this.returnTo==='settings')this.open('settings');else this.close();});
    document.querySelector('#scenes-settings').addEventListener('click',()=>this.open('settings'));
    document.addEventListener('keydown',event=>{if(event.key==='Escape'&&this.view!=='home'){event.preventDefault();if(this.detail)this.showDetail(null);else if(this.view==='scenes'&&this.returnTo==='settings')this.open('settings');else this.close();}});
    const bindings=[['language','languageButton'],['pixelStyle','pixelButton'],['temperatureUnit','temperatureButton'],['windUnit','windButton']];
    for(const [key,attribute] of bindings)for(const button of document.querySelectorAll('[data-'+attribute.replace(/[A-Z]/g,letter=>'-'+letter.toLowerCase())+']'))button.addEventListener('click',()=>change(key,button.dataset[attribute]));
    document.querySelector('#animation-toggle').addEventListener('click',()=>change('motionEnabled',!getState().motionEnabled));
    for(const button of document.querySelectorAll('[data-settings-jump]'))button.addEventListener('click',()=>document.querySelector('#category-'+button.dataset.settingsJump).scrollIntoView({block:'start',behavior:'instant'}));
    for(const button of document.querySelectorAll('[data-detail]'))button.addEventListener('click',()=>this.showDetail(button.dataset.detail));
    for(const button of document.querySelectorAll('[data-external]'))button.addEventListener('click',()=>deviceServices.external(button.dataset.external));
    document.querySelector('#location-toggle').addEventListener('click',()=>{this.locationState=deviceServices.setLocation(!this.locationState.enabled);this.render();});
    window.addEventListener('sundown-device',event=>{this.locationState=event.detail;this.render();});
    document.querySelector('#clear-cache').addEventListener('click',async()=>{const button=document.querySelector('#clear-cache');button.disabled=true;try{this.cacheInfo=await deviceServices.clearCache();this.status.cache='cacheCleared';}catch{this.status.cache='cacheFailed';}finally{button.disabled=false;this.render();}});
    document.querySelector('#check-updates').addEventListener('click',()=>{this.status.update=APP_META.updateUrl?(deviceServices.external(APP_META.updateUrl)?'updateOpened':'updateUnconfigured'):'updateUnconfigured';this.render();});
    document.querySelector('#copy-feedback').addEventListener('click',async()=>{const message=document.querySelector('#feedback-message').value.trim();if(!message){this.status.feedback='feedbackEmpty';this.render();return;}const state=getState();const report=`${APP_META.name} v${this.version.version} (${this.version.build})\nLanguage: ${state.language}; Pixels: ${state.pixelStyle}; Units: ${state.temperatureUnit.toUpperCase()}, ${state.windUnit}\n\n${message}`;this.status.feedback=await deviceServices.copy(report)?'feedbackCopied':'feedbackFailed';this.render();});
    for(const [key,weather] of Object.entries(WEATHER_PRESETS)){
      const button=document.createElement('button');button.type='button';button.className='scene-card';button.dataset.sceneButton=key;button.innerHTML='<svg viewBox="0 0 32 32" aria-hidden="true">'+weatherIconMarkup(resolveWeatherIcon(weather))+'</svg><span class="scene-name"></span><span class="scene-city"></span><span class="scene-time"></span>';
      button.addEventListener('click',()=>{selectScene(key);this.close('scenes-open');});document.querySelector('#scene-grid').append(button);
    }
  }
  text(key){return settingText(key,this.getState().language);}
  async refreshCache(){try{this.cacheInfo=await deviceServices.cache();}catch{this.status.cache='cacheFailed';}this.render();}
  open(view){this.view=view;this.detail=null;this.home.hidden=view!=='home';this.panel.hidden=view!=='settings';this.scenes.hidden=view!=='scenes';document.querySelector('#settings-open').setAttribute('aria-expanded',String(view==='settings'));document.querySelector('#scenes-open').setAttribute('aria-expanded',String(view==='scenes'));this.render();document.querySelector(view==='scenes'?'#scenes-close':'#settings-close').focus({preventScroll:true});if(view==='settings'){this.locationState=deviceServices.location();this.refreshCache();}}
  close(focusId){const prior=this.view;this.view='home';this.detail=null;this.home.hidden=false;this.panel.hidden=true;this.scenes.hidden=true;document.querySelector('#settings-open').setAttribute('aria-expanded','false');document.querySelector('#scenes-open').setAttribute('aria-expanded','false');document.querySelector('#'+(focusId??(prior==='scenes'?'scenes-open':'settings-open'))).focus({preventScroll:true});}
  showDetail(detail){this.detail=detail;this.render();document.querySelector(detail?'#settings-back':'#settings-close').focus({preventScroll:true});}
  render(){
    const state=this.getState(),english=state.language==='en';
    for(const node of document.querySelectorAll('[data-copy]'))node.textContent=this.text(node.dataset.copy);
    for(const [id,key] of Object.entries({'settings-open':'openSettings','settings-close':'closeSettings','settings-back':'backSettings','scenes-open':'scenes','settings-more':'moreMenu','scenes-close':'closeScenes'})){const node=document.querySelector('#'+id);node.setAttribute('aria-label',this.text(key));node.title=this.text(key);}
    document.querySelector('#pixel-fine-label').textContent=this.text('fine');document.querySelector('#pixel-block-label').textContent=this.text('block');
    for(const [attribute,value] of [['languageButton',state.language],['pixelButton',state.pixelStyle],['temperatureButton',state.temperatureUnit],['windButton',state.windUnit]])for(const button of document.querySelectorAll('[data-'+attribute.replace(/[A-Z]/g,letter=>'-'+letter.toLowerCase())+']'))button.setAttribute('aria-pressed',String(button.dataset[attribute]===value));
    document.querySelector('#animation-toggle').setAttribute('aria-checked',String(state.motionEnabled));
    document.querySelector('#settings-scroll').hidden=Boolean(this.detail);document.querySelector('.settings-nav').hidden=Boolean(this.detail);document.querySelector('#settings-detail').hidden=!this.detail;document.querySelector('#settings-back').hidden=!this.detail;
    for(const article of document.querySelectorAll('[data-detail-content]'))article.hidden=article.dataset.detailContent!==this.detail;
    const ver='v'+this.version.version+' · '+this.version.build;document.querySelector('#version-value').textContent=ver;document.querySelector('.about-version').textContent=ver;
    for(const [id,key] of [['cache-status','cache'],['update-status','update'],['feedback-status','feedback']])document.querySelector('#'+id).textContent=this.status[key]?this.text(this.status[key]):'';
    const c=this.cacheInfo;document.querySelector('#cache-size').textContent=formatBytes(c.total);document.querySelector('#cache-detail').textContent=this.text('cacheWeather')+' '+formatBytes(c.weather)+' · '+this.text('cacheMaps')+' '+formatBytes(c.maps)+'\n'+this.text('cacheBundle');
    const loc=this.locationState;document.querySelector('#location-toggle').setAttribute('aria-checked',String(Boolean(loc.enabled)));
    const locationKeys={off:'locationOff',preview:'locationPreview',waiting:'locationWaiting',permission:'locationPermission',denied:'locationDenied',system_off:'locationSystemOff',unavailable:'locationUnavailable'};
    document.querySelector('#location-status').textContent=loc.status==='fix'?this.text('locationFix')+' '+loc.latitude.toFixed(2)+', '+loc.longitude.toFixed(2)+' · ±'+Math.round(loc.accuracy)+'m':this.text(locationKeys[loc.status]??'locationOff');
    for(const button of document.querySelectorAll('[data-scene-button]')){
      const key=button.dataset.sceneButton,weather=WEATHER_PRESETS[key],time=resolveTimeContext(weather),unitWeather=formatUnitWeather(weather,state);
      button.setAttribute('aria-pressed',String(key===state.presetKey));button.querySelector('.scene-name').textContent=sceneName(key,state.language);button.querySelector('.scene-city').textContent=english?weather.cityEn:state.language==='dual'?weather.cityZh+' · '+weather.cityEn:weather.cityZh;button.querySelector('.scene-time').textContent=time.date.slice(5).replace('-','/')+' '+time.clock24+' · '+Math.round(unitWeather.temperature)+'°'+unitWeather.temperatureUnit;
    }
  }
}
