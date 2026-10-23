import { APP_META } from './app-meta.js';
import { WEATHER_PRESETS, resolveTimeContext } from './weather-art.js';
import { sceneName } from './i18n.js';
import { resolveWeatherIcon, weatherIconMarkup } from './weather-icons.js';
import { formatUnitWeather } from './weather-units.js';
import { deviceServices } from './device-services.js';
import { checkLatestRelease } from './release-service.js';
const SETTING_COPY=Object.freeze({
  sourceFooter:['数据来源 Open-Meteo · BARO','Powered by Open-Meteo · BARO'],
  appearanceNav:['界面','Appearance'],unitsNav:['单位','Units'],aboutNav:['关于','About'],
  settingsCategories:['设置分类','Settings categories'],
  settingsList:['设置列表','Settings list'],
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
  updateTitle:['软件更新','Software updates'],closeUpdates:['关闭更新窗口','Close updates'],
  installedVersion:['当前版本','Installed'],latestRelease:['最新发布','Latest release'],viewReleases:['查看发布页','View releases'],tryAgain:['重试','Try again'],
  updateChecking:['正在检查 GitHub 上的最新发布…','Checking the latest release on GitHub…'],
  updateAvailable:['有新版本可用。可在发布页查看更新内容。','A new version is available. View the release notes on GitHub.'],
  updateCurrent:['当前版本已是最新。','You’re up to date.'],
  updatePublished:['发现已发布版本。可在 GitHub 查看版本详情。','A release is published. View its details on GitHub.'],
  updateEmpty:['仓库尚未发布正式版本。后续更新会出现在发布页。','No release has been published yet. Future updates will appear on GitHub.'],
  updateFailed:['暂时无法连接 GitHub。可以重试，或直接查看发布页。','Couldn’t reach GitHub. Try again, or open the release page.'],
  updateDate:['发布时间','Published'],
  cacheWeather:['天气','Weather'],cacheMaps:['地图','Maps'],cacheBundle:['安装资源不计入数据缓存。','Bundled assets are excluded.'],cacheCleared:['数据缓存已清除，设置已保留。','Data cache cleared. Preferences kept.'],cacheFailed:['暂时无法读取或清理缓存，请重试。','Unable to read or clear cache. Try again.'],
  feedbackEmpty:['请先填写反馈内容。','Please enter your feedback first.'],feedbackCopied:['反馈已复制。','Feedback copied.'],feedbackFailed:['复制失败，请选择文本手动复制。','Copy failed. Select the text to copy manually.']
});
export function settingText(key,language){const pair=SETTING_COPY[key];if(!pair)throw new Error('Missing settings copy: '+key);return language==='en'?pair[1]:pair[0];}
export function formatBytes(bytes){if(!Number.isFinite(bytes)||bytes<=0)return '0 B';if(bytes<1024)return bytes+' B';if(bytes<1048576)return (bytes/1024).toFixed(1)+' KB';return (bytes/1048576).toFixed(1)+' MB';}
export class SettingsController{
  constructor({getState,change,selectScene}){
    this.getState=getState;this.change=change;this.selectScene=selectScene;this.view='home';this.detail=null;this.returnTo='home';this.locationState=deviceServices.location();this.version=deviceServices.version();this.cacheInfo={weather:0,maps:0,total:0};this.status={};
    this.home=document.querySelector('.information-panel');this.panel=document.querySelector('#settings-panel');this.scenes=document.querySelector('#scenes-panel');
    this.reducedMotion=matchMedia('(prefers-reduced-motion: reduce)');this.viewRevision=0;this.viewAnimations=[];this.activeCategory='appearance';
    this.updateCard=document.querySelector('#update-card');this.updateState='idle';this.updateOpen=false;this.updateRevision=0;
    document.querySelector('#settings-open').addEventListener('click',()=>this.open('settings'));
    document.querySelector('#settings-close').addEventListener('click',()=>this.close());
    document.querySelector('#settings-back').addEventListener('click',()=>this.showDetail(null));
    document.querySelector('#scenes-open').addEventListener('click',()=>{this.returnTo='home';this.open('scenes');});
    document.querySelector('#settings-more').addEventListener('click',()=>this.showDetail('more'));
    document.querySelector('#scenes-close').addEventListener('click',()=>{if(this.returnTo==='settings')this.open('settings');else this.close();});
    document.querySelector('#scenes-settings').addEventListener('click',()=>this.open('settings'));
    document.addEventListener('keydown',event=>{if(event.key==='Escape'&&this.view!=='home'){event.preventDefault();if(this.updateOpen)this.closeUpdates();else if(this.detail)this.showDetail(null);else if(this.view==='scenes'&&this.returnTo==='settings')this.open('settings');else this.close();}});
    const bindings=[['language','languageButton'],['pixelStyle','pixelButton'],['temperatureUnit','temperatureButton'],['windUnit','windButton']];
    for(const [key,attribute] of bindings)for(const button of document.querySelectorAll('[data-'+attribute.replace(/[A-Z]/g,letter=>'-'+letter.toLowerCase())+']'))button.addEventListener('click',()=>change(key,button.dataset[attribute]));
    document.querySelector('#animation-toggle').addEventListener('click',()=>change('motionEnabled',!getState().motionEnabled));
    for(const button of document.querySelectorAll('[data-settings-jump]'))button.addEventListener('click',()=>this.scrollToCategory(button.dataset.settingsJump));
    const scroll=document.querySelector('#settings-scroll');scroll.addEventListener('scroll',()=>{if(!this.scrollAnimation)this.syncCategory();},{passive:true});
    for(const event of ['wheel','touchstart','pointerdown'])scroll.addEventListener(event,()=>this.stopScroll(),{passive:true});
    scroll.addEventListener('keydown',event=>{if(['ArrowUp','ArrowDown','PageUp','PageDown','Home','End'].includes(event.key))this.stopScroll();});
    document.querySelector('.settings-nav').addEventListener('keydown',event=>{if(!['ArrowLeft','ArrowRight','Home','End'].includes(event.key))return;event.preventDefault();const buttons=[...document.querySelectorAll('[data-settings-jump]')],index=Math.max(0,buttons.indexOf(event.target.closest('button'))),next=event.key==='Home'?0:event.key==='End'?buttons.length-1:(index+(event.key==='ArrowRight'?1:-1)+buttons.length)%buttons.length;this.scrollToCategory(buttons[next].dataset.settingsJump);buttons[next].focus({preventScroll:true});});
    this.navObserver=new ResizeObserver(()=>this.renderCategory());for(const button of document.querySelectorAll('[data-settings-jump]'))this.navObserver.observe(button);
    for(const button of document.querySelectorAll('[data-detail]'))button.addEventListener('click',()=>this.showDetail(button.dataset.detail));
    for(const button of document.querySelectorAll('[data-external]'))button.addEventListener('click',()=>deviceServices.external(button.dataset.external));
    document.querySelector('#location-toggle').addEventListener('click',()=>{this.locationState=deviceServices.setLocation(!this.locationState.enabled);this.render();});
    window.addEventListener('sundown-device',event=>{this.locationState=event.detail;this.render();});
    document.querySelector('#clear-cache').addEventListener('click',async()=>{const button=document.querySelector('#clear-cache');button.disabled=true;try{this.cacheInfo=await deviceServices.clearCache();this.status.cache='cacheCleared';}catch{this.status.cache='cacheFailed';}finally{button.disabled=false;this.render();}});
    document.querySelector('#check-updates').addEventListener('click',()=>this.checkUpdates());
    document.querySelector('#update-close').addEventListener('click',()=>this.closeUpdates());
    document.querySelector('#update-retry').addEventListener('click',()=>this.checkUpdates());
    document.querySelector('#update-releases').addEventListener('click',()=>deviceServices.external(this.release?.url??APP_META.updateUrl));
    document.querySelector('#copy-feedback').addEventListener('click',async()=>{const message=document.querySelector('#feedback-message').value.trim();if(!message){this.status.feedback='feedbackEmpty';this.render();return;}const state=getState();const report=`${APP_META.name} v${this.version.version} (${this.version.build})\nLanguage: ${state.language}; Pixels: ${state.pixelStyle}; Units: ${state.temperatureUnit.toUpperCase()}, ${state.windUnit}\n\n${message}`;this.status.feedback=await deviceServices.copy(report)?'feedbackCopied':'feedbackFailed';this.render();});
    for(const [key,weather] of Object.entries(WEATHER_PRESETS)){
      const button=document.createElement('button');button.type='button';button.className='scene-card';button.dataset.sceneButton=key;button.innerHTML='<svg viewBox="0 0 32 32" aria-hidden="true">'+weatherIconMarkup(resolveWeatherIcon(weather))+'</svg><span class="scene-name"></span><span class="scene-city"></span><span class="scene-time"></span>';
      button.addEventListener('click',()=>{selectScene(key);this.close('scenes-open');});document.querySelector('#scene-grid').append(button);
    }
  }
  text(key){return settingText(key,this.getState().language);}
  async refreshCache(){try{this.cacheInfo=await deviceServices.cache();}catch{this.status.cache='cacheFailed';}this.render();}
  switchView(view){
    const views={home:this.home,settings:this.panel,scenes:this.scenes},prior=views[this.view],next=views[view],revision=++this.viewRevision;
    for(const animation of this.viewAnimations)animation.cancel();this.viewAnimations=[];this.stopScroll();this.view=view;
    for(const node of Object.values(views)){node.hidden=node!==next&&node!==prior;node.inert=node!==next;node.style.zIndex=node===next?'3':'2';}
    document.querySelector('#settings-open').setAttribute('aria-expanded',String(view==='settings'));document.querySelector('#scenes-open').setAttribute('aria-expanded',String(view==='scenes'));
    const finish=()=>{if(revision!==this.viewRevision)return;for(const node of Object.values(views))node.hidden=node!==next;this.viewAnimations=[];};
    if(prior===next||this.reducedMotion.matches){finish();return;}
    this.viewAnimations=[prior.animate([{opacity:1},{opacity:0}],{duration:220,easing:'linear'}),next.animate([{opacity:0,transform:'translateX('+(view==='home'?-12:12)+'px)'},{opacity:1,transform:'translateX(0)'}],{duration:220,easing:'linear'})];
    Promise.allSettled(this.viewAnimations.map(animation=>animation.finished)).then(finish);
  }
  open(view){if(view!=='settings')this.closeUpdates(false);this.detail=null;this.switchView(view);this.render();document.querySelector(view==='scenes'?'#scenes-close':'#settings-close').focus({preventScroll:true});if(view==='settings'){this.locationState=deviceServices.location();this.refreshCache();}}
  close(focusId){const prior=this.view;this.closeUpdates(false);this.detail=null;this.switchView('home');document.querySelector('#'+(focusId??(prior==='scenes'?'scenes-open':'settings-open'))).focus({preventScroll:true});}
  showDetail(detail){this.stopScroll();this.closeUpdates(false);this.detail=detail;this.render();const content=document.querySelector(detail?'#settings-detail':'#settings-scroll');this.detailAnimation?.cancel();if(!this.reducedMotion.matches)this.detailAnimation=content.animate([{opacity:0,transform:'translateY(8px)'},{opacity:1,transform:'translateY(0)'}],{duration:180,easing:'linear'});document.querySelector(detail?'#settings-back':'#settings-close').focus({preventScroll:true});}
  stopScroll(){if(this.scrollAnimation)cancelAnimationFrame(this.scrollAnimation);this.scrollAnimation=0;}
  scrollToCategory(category){
    this.stopScroll();const scroll=document.querySelector('#settings-scroll'),target=document.querySelector('#category-'+category),from=scroll.scrollTop;
    const to=Math.max(0,Math.min(scroll.scrollHeight-scroll.clientHeight,from+target.getBoundingClientRect().top-scroll.getBoundingClientRect().top));
    this.activeCategory=category;this.renderCategory();if(this.reducedMotion.matches){scroll.scrollTop=to;return;}
    const start=performance.now(),step=now=>{const progress=Math.min(1,(now-start)/240);scroll.scrollTop=from+(to-from)*progress;if(progress<1)this.scrollAnimation=requestAnimationFrame(step);else this.scrollAnimation=0;};this.scrollAnimation=requestAnimationFrame(step);
  }
  syncCategory(){const scroll=document.querySelector('#settings-scroll');if(scroll.hidden)return;if(scroll.scrollTop+scroll.clientHeight>=scroll.scrollHeight-2)this.activeCategory='about';else{const top=scroll.getBoundingClientRect().top;for(const category of document.querySelectorAll('.settings-category'))if(category.getBoundingClientRect().top<=top+32)this.activeCategory=category.id.replace('category-','');}this.renderCategory();}
  renderCategory(){const nav=document.querySelector('.settings-nav');nav.setAttribute('aria-label',this.text('settingsCategories'));for(const button of nav.querySelectorAll('button')){const active=button.dataset.settingsJump===this.activeCategory;button.setAttribute('aria-current',String(active));if(active&&!nav.hidden){nav.style.setProperty('--nav-left',button.offsetLeft+'px');nav.style.setProperty('--nav-width',button.offsetWidth+'px');}}}
  async checkUpdates(){
    this.updateAbort?.abort();const revision=++this.updateRevision;this.updateAbort=new AbortController();const opening=!this.updateOpen;
    this.updateOpen=true;this.updateState='checking';this.release=null;this.updateAnimation?.cancel();this.updateCard.hidden=false;this.renderUpdates();
    if(opening&&!this.reducedMotion.matches)this.updateAnimation=this.updateCard.animate([{opacity:0,transform:'translateY(-12px)'},{opacity:1,transform:'translateY(0)'}],{duration:220,easing:'linear'});
    document.querySelector('#update-title').focus({preventScroll:true});
    try{const release=await checkLatestRelease(this.version.version,{signal:this.updateAbort.signal});if(revision!==this.updateRevision||!this.updateOpen)return;this.release=release;this.updateState=release.state;}
    catch{if(revision!==this.updateRevision||!this.updateOpen)return;this.updateState='failed';}
    this.renderUpdates();
  }
  closeUpdates(restoreFocus=true){
    const wasOpen=this.updateOpen;this.updateOpen=false;this.updateState='idle';const revision=++this.updateRevision;this.updateAbort?.abort();this.updateAnimation?.cancel();this.renderUpdates();
    const finish=()=>{if(revision===this.updateRevision&&!this.updateOpen)this.updateCard.hidden=true;};
    if(wasOpen&&restoreFocus&&!this.reducedMotion.matches){this.updateAnimation=this.updateCard.animate([{opacity:1,transform:'translateY(0)'},{opacity:0,transform:'translateY(-8px)'}],{duration:160,easing:'linear'});this.updateAnimation.finished.then(finish,()=>{});}else finish();
    if(wasOpen&&restoreFocus&&this.view==='settings')document.querySelector('#check-updates').focus({preventScroll:true});
  }
  renderUpdates(){
    const pending=this.updateOpen&&this.updateState==='checking',button=document.querySelector('#check-updates');button.disabled=pending;button.setAttribute('aria-expanded',String(this.updateOpen));
    this.updateCard.inert=!this.updateOpen;this.updateCard.dataset.state=this.updateState;this.updateCard.setAttribute('aria-busy',String(pending));
    document.querySelector('#update-close').setAttribute('aria-label',this.text('closeUpdates'));
    const key={checking:'updateChecking',available:'updateAvailable',current:'updateCurrent',published:'updatePublished',empty:'updateEmpty',failed:'updateFailed'}[this.updateState];
    if(key)document.querySelector('#update-message').textContent=this.text(key);
    document.querySelector('#update-installed').textContent='v'+this.version.version;document.querySelector('#update-latest').textContent=this.release?.tag??'—';
    document.querySelector('#update-retry').hidden=this.updateState!=='failed';
    const date=document.querySelector('#update-published');date.hidden=!this.release?.publishedAt;
    if(!date.hidden)date.textContent=this.text('updateDate')+' · '+new Intl.DateTimeFormat(this.getState().language==='en'?'en-GB':'zh-CN',{dateStyle:'medium'}).format(new Date(this.release.publishedAt));
  }
  render(){
    const state=this.getState(),english=state.language==='en';
    for(const node of document.querySelectorAll('[data-copy]'))node.textContent=this.text(node.dataset.copy);
    for(const [id,key] of Object.entries({'settings-open':'openSettings','settings-close':'closeSettings','settings-back':'backSettings','scenes-open':'scenes','settings-more':'moreMenu','scenes-close':'closeScenes'})){const node=document.querySelector('#'+id);node.setAttribute('aria-label',this.text(key));node.title=this.text(key);}
    document.querySelector('#pixel-fine-label').textContent=this.text('fine');document.querySelector('#pixel-block-label').textContent=this.text('block');
    for(const [attribute,value] of [['languageButton',state.language],['pixelButton',state.pixelStyle],['temperatureButton',state.temperatureUnit],['windButton',state.windUnit]])for(const button of document.querySelectorAll('[data-'+attribute.replace(/[A-Z]/g,letter=>'-'+letter.toLowerCase())+']'))button.setAttribute('aria-pressed',String(button.dataset[attribute]===value));
    for(const group of document.querySelectorAll('.setting-options')){const buttons=[...group.querySelectorAll('button')];group.style.setProperty('--option-count',buttons.length);group.style.setProperty('--active-index',Math.max(0,buttons.findIndex(button=>button.getAttribute('aria-pressed')==='true')));}
    this.renderUpdates();
    document.querySelector('#animation-toggle').setAttribute('aria-checked',String(state.motionEnabled));
    document.querySelector('#settings-scroll').setAttribute('aria-label',this.text('settingsList'));
    document.querySelector('#settings-scroll').hidden=Boolean(this.detail);document.querySelector('.settings-nav').hidden=Boolean(this.detail);document.querySelector('#settings-detail').hidden=!this.detail;document.querySelector('#settings-back').hidden=!this.detail;
    this.renderCategory();
    for(const article of document.querySelectorAll('[data-detail-content]'))article.hidden=article.dataset.detailContent!==this.detail;
    const ver='v'+this.version.version+' · '+this.version.build;document.querySelector('#version-value').textContent=ver;document.querySelector('.about-version').textContent=ver;
    for(const [id,key] of [['cache-status','cache'],['feedback-status','feedback']])document.querySelector('#'+id).textContent=this.status[key]?this.text(this.status[key]):'';
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
