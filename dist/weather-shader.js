import { PixelWeatherRenderer, resolveWeatherScene, PIXEL_TONE } from './weather-art.js';

export const FINE_MOTION = Object.freeze({ columns: 1536, coarseColumns: 96, fps: 24, frames: 192 });

export function resolvePixelGrid(width, height, pixelStyle = 'fine', maxTextureSize = 4096) {
  width = Math.max(1, Math.round(width)); height = Math.max(1, Math.round(height));
  const limit = Math.max(1, Math.floor(maxTextureSize));
  let columns = Math.min(pixelStyle === 'block' ? FINE_MOTION.coarseColumns : Math.min(FINE_MOTION.columns, width), limit);
  let rows = Math.max(1, Math.round(columns * height / width));
  if (rows > limit) { rows = limit; columns = Math.max(1, Math.round(rows * width / height)); }
  return { columns, rows };
}
const VERTEX = `#version 300 es
out vec2 texCoord;
void main(){ vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2)); texCoord=p; gl_Position=vec4(p*2.-1.,0.,1.); }`;
const COPY = `#version 300 es
precision highp float;
in vec2 texCoord; uniform sampler2D image; out vec4 outputColor;
void main(){ outputColor=texture(image,texCoord); }`;
const ART = `#version 300 es
precision highp float;
uniform vec2 resolution;
uniform float phase;
uniform int scene;
uniform bool night;
uniform bool coarse;
out vec4 outputColor;
const float PI=3.14159265359;
const int BAYER[16]=int[16](0,8,2,10,12,4,14,6,3,11,1,9,15,7,13,5);
float threshold;
vec2 p;
float aspect;
float hash(vec2 v){ return fract(sin(dot(v,vec2(127.1,311.7)))*43758.5453); }
float coverage(float distance){
  if(coarse)return step(0.,distance);
  float edge=.65/resolution.x;
  return smoothstep(-edge,edge,distance);
}
vec3 ramp(vec3 a,vec3 b,vec3 c,vec3 d,float t){
  float divisions=coarse?1.:10.;
  float level=floor(clamp(t,0.,1.)*3.*divisions+threshold)/divisions;
  return level<1.?mix(a,b,level):level<2.?mix(b,c,level-1.):mix(c,d,level-2.);
}
float ellipse(vec2 q,vec2 radius){return (1.-length(q/radius))*min(radius.x,radius.y);}
float bank(vec2 q,float scale){
  q/=scale;
  float d=ellipse(q,vec2(.37,.074));
  d=max(d,ellipse(q-vec2(-.19,-.045),vec2(.12,.096)));
  d=max(d,ellipse(q-vec2(-.055,-.091),vec2(.15,.139)));
  d=max(d,ellipse(q-vec2(.115,-.068),vec2(.13,.115)));
  d=max(d,ellipse(q-vec2(.265,-.018),vec2(.12,.073)));
  // Fine contours use the analytic shape; only the block style adds roughness.
  return d*scale + (coarse?(hash(floor(p*80.))-.5)*.004:0.);
}
vec3 cloud(vec3 background,vec2 center,float scale,float depth){
  vec2 q=p-vec2(center.x,center.y*aspect);
  if(scene==7)q*=vec2(.74,1.35);
  float d=bank(q,scale),edgeCoverage=coverage(d); if(edgeCoverage<=0.)return background;
  float upper=bank(q+vec2(.009,.014),scale);
  float shape=clamp(.52-q.y/(scale*.24)+d*2.5+(hash(floor(p*256.))-.5)*(coarse?.075:.008),0.,1.);
  vec3 lo=night?vec3(.20,.28,.40):mix(vec3(.50,.61,.72),vec3(.48,.55,.65),depth);
  lo*=night?.94:.86;
  vec3 mid=night?vec3(.29,.38,.51):mix(vec3(.71,.78,.82),vec3(.61,.68,.74),depth);
  vec3 high=night?vec3(.42,.50,.62):mix(vec3(.88,.91,.90),vec3(.78,.82,.83),depth);
  vec3 rim=night?vec3(.56,.62,.72):(scene==2||scene==3)?vec3(.85,.89,.91):scene==5?vec3(.97,.98,.97):vec3(.99,.97,.87);
  vec3 color=ramp(lo,mid,high,rim,shape);
  if(coarse){if(d<.01 && upper>d+.004 && threshold<.7)color=rim;}
  else color=mix(color,rim,(1.-smoothstep(0.,.006,d))*smoothstep(.003,.012,upper-d)*.35);
  return mix(background,color,edgeCoverage);
}
float segment(vec2 q,vec2 a,vec2 b){vec2 v=b-a;return length(q-a-v*clamp(dot(q-a,v)/dot(v,v),0.,1.));}
void main(){
  vec2 cell=floor(vec2(gl_FragCoord.x,resolution.y-gl_FragCoord.y));
  vec2 uv=(cell+.5)/resolution;
  aspect=resolution.y/resolution.x; p=vec2(uv.x,uv.y*aspect);
  threshold=coarse?.5:(float(BAYER[int(mod(cell.y,4.))*4+int(mod(cell.x,4.))])+.5)/16.;
  float wave=sin(phase*2.*PI), drift=wave*.025;
  bool warm=scene==9||scene==10;
  vec3 a=vec3(.53,.68,.79),b=vec3(.65,.76,.83),c=vec3(.77,.83,.85),d=vec3(.88,.87,.80);
  if(scene==2||scene==3){a=vec3(.43,.53,.65);b=vec3(.54,.64,.73);c=vec3(.65,.73,.78);d=vec3(.78,.82,.82);}
  if(scene==5){a=vec3(.62,.72,.81);b=vec3(.73,.81,.87);c=vec3(.84,.88,.91);d=vec3(.91,.94,.94);}
  if(scene==11){a=vec3(.66,.71,.73);b=vec3(.72,.76,.77);c=vec3(.79,.81,.81);d=vec3(.86,.86,.83);}
  if(scene==6){a=vec3(.64,.72,.73);b=vec3(.73,.78,.78);c=vec3(.81,.84,.82);d=vec3(.89,.89,.84);}
  if(night||scene==4||scene==8){a=vec3(.075,.12,.21);b=vec3(.12,.18,.29);c=vec3(.20,.27,.39);d=vec3(.32,.38,.48);}
  if(warm){a=vec3(.49,.61,.76);b=vec3(.68,.65,.77);c=vec3(.87,.69,.69);d=vec3(.99,.79,.57);}
  if(scene==10){a=vec3(.43,.61,.76);b=vec3(.64,.74,.81);c=vec3(.84,.81,.76);d=vec3(.99,.86,.64);}
  vec3 color=ramp(a,b,c,d,uv.y*.91+.055*sin(uv.x*3.));
  vec2 solar=vec2(warm?.55:.68,(warm?.67:.27)*aspect);
  solar.y+=wave*(scene==9?.009:scene==10?-.009:.002);
  float r=length(p-solar);
  if(scene==0||scene==1||scene==7||warm){
    float halo=exp(-r*r/(warm?.085:.065));
    vec3 haloColor=warm?vec3(.99,.79,.52):vec3(.93,.89,.70);
    color=mix(color,haloColor,floor(halo*5.+threshold)*.08);
    // Sparse fan-shaped sunlight, visible between the cloud layers.
    float angle=atan(p.x-solar.x,p.y-solar.y);
    if(p.y>solar.y && r>.11 && r<.64 && sin(angle*29.+.1*wave)>.94 && threshold<.15)color=vec3(.89,.87,.75);
    float radius=warm?.12:.135;
    color=mix(color,ramp(vec3(.94,.63,.32),vec3(.99,.75,.40),vec3(1.,.85,.56),vec3(1.,.93,.73),.5-(p.y-solar.y)/(radius*2.)),coverage(radius-r));
  }
  if(scene==8){
    vec2 starCell=floor(uv*vec2(22.,27.));vec2 starLocal=fract(uv*vec2(22.,27.));
    float seed=hash(starCell);
    float twinkle=.5+.5*sin(phase*2.*PI+seed*31.);
    float starSize=(seed>.985?.075:.026);
    if(seed>.89 && length(starLocal-.5)<starSize && threshold<.4+twinkle*.6)color=vec3(.89,.86,.72);
    float moon=length(p-solar),cut=length(p-solar-vec2(.064,-.035));
    color=mix(color,ramp(vec3(.69,.70,.68),vec3(.82,.80,.68),vec3(.91,.87,.73),vec3(.98,.92,.77),.8-(p.y-solar.y)*2.),coverage(min(.135-moon,cut-.124)));
  }
  if(scene==1||scene==7){
    color=cloud(color,vec2(.11+drift*.7,.64),.92,0.);
    color=cloud(color,vec2(.70-drift,.46),1.04,.08);
    color=cloud(color,vec2(1.07+drift,.83),.85,.04);
  }
  if(scene==2||scene==3||scene==4||scene==5){
    color=cloud(color,vec2(.18+drift*.6,.27),1.1,.25);
    color=cloud(color,vec2(.82-drift,.39),1.18,scene==4?.95:.4);
    color=cloud(color,vec2(.17+drift,.47),.93,scene==4?.85:.3);
    if(scene==2)color=cloud(color,vec2(.8+drift*.5,.81),1.05,.15);
  }
  if(scene==0)color=cloud(color,vec2(-.18+drift,.82),.85,0.);
  if(scene==8)color=cloud(color,vec2(-.15+drift,.77),.75,.1);
  if(warm){
    // Two quiet horizons keep dawn and dusk distinct without breaking the pixel vocabulary.
    float horizon=.72+.017*sin(uv.x*8.)+.012*sin(uv.x*18.);
    if(uv.y>horizon)color=scene==10?ramp(vec3(.54,.61,.63),vec3(.67,.68,.64),vec3(.79,.74,.65),vec3(.90,.79,.63),1.-(uv.y-horizon)*2.8):ramp(vec3(.54,.39,.48),vec3(.69,.44,.49),vec3(.84,.51,.47),vec3(.94,.64,.47),1.-(uv.y-horizon)*2.8);
    color=cloud(color,vec2(-.13+drift,.32),.8,.05);
    color=cloud(color,vec2(.94-drift,.20),.57,.07);
  }
  if(scene==3||scene==4){
    vec2 rainUV=vec2(uv.x+uv.y*.18,uv.y);
    vec2 group=floor(rainUV*vec2(37.,15.));
    float speed=scene==4?9.:6.;
    vec2 drop=vec2(fract(rainUV.x*37.),fract(rainUV.y*15.-phase*speed+hash(vec2(group.x,1.))*11.));
    if(uv.y>.46 && hash(vec2(group.x,4.))>.22 && drop.x<.15 && drop.y<.22)color=night?vec3(.53,.65,.76):vec3(.72,.84,.89);
  }
  if(scene==4){
    float flash=step(.295,phase)*step(phase,.317);
    vec2 q=vec2(uv.x,uv.y);
    float bolt=min(min(segment(q,vec2(.64,.40),vec2(.54,.56)),segment(q,vec2(.54,.56),vec2(.62,.55))),segment(q,vec2(.62,.55),vec2(.47,.76)));
    if(flash>0. && bolt<.008)color=vec3(1.,.88,.62);
    else if(flash>0. && bolt<.027 && threshold<.25)color=vec3(.69,.68,.64);
  }
  if(scene==5){
    for(int layer=0;layer<2;layer++){
      float n=float(layer);vec2 v=vec2(uv.x+wave*.018*(n+1.),uv.y-phase*(1.+n));
      vec2 tile=floor(v*vec2(23.+n*7.,17.+n*8.));vec2 flake=fract(v*vec2(23.+n*7.,17.+n*8.))-.5;
      float size=n==0.?.065:.033;
      tile.y=mod(tile.y,17.+n*8.);
      if(uv.y>.35&&hash(tile)>.61 && (length(flake)<size||(hash(tile)>.93&&min(abs(flake.x),abs(flake.y))<.026&&max(abs(flake.x),abs(flake.y))<.14)))color=vec3(.97,.98,.96);
    }
  }
  if(scene==6){
    for(int layer=0;layer<5;layer++){
      float n=float(layer),line=.22+n*.15+.016*sin(uv.x*4.+phase*PI*2.+n*2.);
      float band=exp(-pow((uv.y-line)/.025,2.));
      if(threshold<band*.65)color=n<2.?vec3(.80,.84,.83):vec3(.90,.91,.86);
    }
    if(threshold<exp(-pow((uv.y-.68)/.25,2.))*.16)color=vec3(.86,.88,.84);
  }
  if(scene==7){
    for(int i=0;i<5;i++){
      float n=float(i),line=.56+n*.064;float x=fract(uv.x-phase*2.+n*.27);
      if(abs(uv.y-line)<.0028 && x<.20+n*.011)color=vec3(.94,.94,.85);
    }
  }
  // Grade luminance rather than RGB channels independently to retain the weather palette.
  float luminance=dot(color,vec3(.2126,.7152,.0722));
  float lift=smoothstep(${PIXEL_TONE.lowKnee},${PIXEL_TONE.highKnee},luminance);
  float graded=mix(luminance*${PIXEL_TONE.shadowGain},1.-(1.-luminance)*${PIXEL_TONE.highlightRemainder},lift);
  color=clamp(color*graded/max(luminance,.001),0.,1.);
  outputColor=vec4(color,1.);
}`;
const SCENES = ['clear','partly','overcast','rain','storm','snow','fog','wind','night','sunset','dawn','unknown'];

// Native-resolution presentation; only the art pass runs on the fine logical grid.
export class FineWeatherRenderer {
  constructor(canvas, weather, {pixelStyle='fine'}={}) {
    this.pixelStyle=pixelStyle==='block'?'block':'fine';this.columns=this.pixelStyle==='block'?FINE_MOTION.coarseColumns:FINE_MOTION.columns;
    this.canvas=canvas; this.weather=weather; this.animated=true; this.visible=true; this.frame=0;
    this.origin=performance.now(); this.reduced=matchMedia('(prefers-reduced-motion: reduce)');
    try {
      this.gl=canvas.getContext('webgl2',{alpha:false,antialias:false,depth:false,stencil:false,preserveDrawingBuffer:false,powerPreference:'low-power'});
      if(!this.gl)throw new Error('WebGL2 unavailable');
      this.initialize();
    } catch(error) {
      console.warn('Fine pixel renderer unavailable; using Canvas fallback.',error.message);
      this.gl?.getExtension('WEBGL_lose_context')?.loseContext();this.gl=null;
      const replacement=canvas.cloneNode(false); canvas.replaceWith(replacement);
      this.canvas=replacement; this.fallback=new PixelWeatherRenderer(replacement,weather);
      replacement.dataset.renderer='canvas-fallback'; return;
    }
    this.onVisibility=()=>this.sync(); this.onMotion=()=>{if(this.reduced.matches)this.draw(0);this.sync();};
    this.onLost=event=>{event.preventDefault();cancelAnimationFrame(this.raf);this.canvas.dataset.renderer='webgl2-restoring';};
    this.onRestored=()=>{this.initialize();this.resize();this.sync();};
    canvas.addEventListener('webglcontextlost',this.onLost);canvas.addEventListener('webglcontextrestored',this.onRestored);
    document.addEventListener('visibilitychange',this.onVisibility);this.reduced.addEventListener('change',this.onMotion);
    this.resizeObserver=new ResizeObserver(()=>this.resize());this.resizeObserver.observe(canvas);
    this.intersectionObserver=new IntersectionObserver(entries=>{this.visible=entries[0].isIntersecting;this.sync();});this.intersectionObserver.observe(canvas);
    this.resize();this.sync();
  }
  initialize(){
    const gl=this.gl;
    const shader=(type,source)=>{const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS)){const error=gl.getShaderInfoLog(s);gl.deleteShader(s);throw new Error(error);}return s;};
    const program=fragment=>{const v=shader(gl.VERTEX_SHADER,VERTEX),f=shader(gl.FRAGMENT_SHADER,fragment),p=gl.createProgram();gl.attachShader(p,v);gl.attachShader(p,f);gl.linkProgram(p);gl.deleteShader(v);gl.deleteShader(f);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw new Error(gl.getProgramInfoLog(p));return p;};
    this.art=program(ART);this.copy=program(COPY);this.vao=gl.createVertexArray();gl.bindVertexArray(this.vao);
    this.locations=Object.fromEntries(['resolution','phase','scene','night','coarse'].map(key=>[key,gl.getUniformLocation(this.art,key)]));
    this.texture=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,this.texture);
    this.maxTextureSize=gl.getParameter(gl.MAX_TEXTURE_SIZE);
    gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);
    gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
    this.fbo=gl.createFramebuffer();gl.bindFramebuffer(gl.FRAMEBUFFER,this.fbo);gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.COLOR_ATTACHMENT0,gl.TEXTURE_2D,this.texture,0);
    this.canvas.dataset.renderer='webgl2-'+this.columns+(this.pixelStyle==='block'?'-flat':'-bayer4');
  }
  resize(){
    if(this.fallback)return this.fallback.resize();
    if(this.gl.isContextLost())return;
    const box=this.canvas.getBoundingClientRect();if(!box.width||!box.height)return;
    const dpr=devicePixelRatio||1;this.canvas.width=Math.round(box.width*dpr);this.canvas.height=Math.round(box.height*dpr);
    const grid=resolvePixelGrid(this.canvas.width,this.canvas.height,this.pixelStyle,this.maxTextureSize);
    this.columns=grid.columns;this.rows=grid.rows;
    const gl=this.gl;gl.bindTexture(gl.TEXTURE_2D,this.texture);
    const filter=this.pixelStyle==='block'?gl.NEAREST:gl.LINEAR;
    gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,filter);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,filter);
    gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA8,this.columns,this.rows,0,gl.RGBA,gl.UNSIGNED_BYTE,null);
    gl.bindFramebuffer(gl.FRAMEBUFFER,this.fbo);
    if(gl.checkFramebufferStatus(gl.FRAMEBUFFER)!==gl.FRAMEBUFFER_COMPLETE)throw new Error('Incomplete pixel framebuffer');
    this.canvas.dataset.resolution=this.canvas.width+'×'+this.canvas.height;this.canvas.dataset.grid=this.columns+'×'+this.rows;this.canvas.dataset.renderer='webgl2-'+this.columns+(this.pixelStyle==='block'?'-flat':'-bayer4');this.canvas.dataset.pixelStyle=this.pixelStyle;
    this.draw(this.frame);
  }
  draw(frame){
    const gl=this.gl;if(gl.isContextLost()||!this.rows)return;
    this.frame=frame;const scene=resolveWeatherScene(this.weather);
    gl.bindVertexArray(this.vao);gl.bindFramebuffer(gl.FRAMEBUFFER,this.fbo);gl.viewport(0,0,this.columns,this.rows);gl.useProgram(this.art);
    gl.uniform2f(this.locations.resolution,this.columns,this.rows);gl.uniform1f(this.locations.phase,frame/FINE_MOTION.frames);
    gl.uniform1i(this.locations.coarse,this.pixelStyle==='block'?1:0);gl.uniform1i(this.locations.scene,SCENES.indexOf(scene));gl.uniform1i(this.locations.night,this.weather.isDay===false&&!['dawn','sunset','fog'].includes(scene)?1:0);
    // The art pass must not sample its own render target.
    gl.bindTexture(gl.TEXTURE_2D,null);gl.drawArrays(gl.TRIANGLES,0,3);
    gl.bindFramebuffer(gl.FRAMEBUFFER,null);gl.viewport(0,0,this.canvas.width,this.canvas.height);gl.useProgram(this.copy);gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,this.texture);gl.drawArrays(gl.TRIANGLES,0,3);
    this.canvas.dataset.scene=scene;this.canvas.dataset.frame=String(frame);
  }
  setPixelStyle(style){if(!['fine','block'].includes(style))return;this.pixelStyle=style;this.columns=style==='block'?FINE_MOTION.coarseColumns:FINE_MOTION.columns;if(!this.fallback)this.resize();}
  setWeather(weather){this.weather=weather;if(this.fallback)return this.fallback.setWeather(weather);this.draw(this.reduced.matches?0:this.frame);}
  setAnimated(value){if(this.fallback)return this.fallback.setAnimated(value);if(value&&!this.animated)this.origin=performance.now()-this.frame*1000/FINE_MOTION.fps;this.animated=value;this.sync();}
  sync(){
    cancelAnimationFrame(this.raf);if(!this.animated||document.hidden||!this.visible||this.gl.isContextLost())return;
    if(this.reduced.matches){this.draw(0);return;}
    const loop=now=>{const frame=Math.floor((now-this.origin)*FINE_MOTION.fps/1000)%FINE_MOTION.frames;if(frame!==this.frame)this.draw(frame);this.raf=requestAnimationFrame(loop);};this.raf=requestAnimationFrame(loop);
  }
  destroy(){
    if(this.fallback)return this.fallback.destroy();
    cancelAnimationFrame(this.raf);this.resizeObserver.disconnect();this.intersectionObserver.disconnect();
    document.removeEventListener('visibilitychange',this.onVisibility);this.reduced.removeEventListener('change',this.onMotion);
    this.canvas.removeEventListener('webglcontextlost',this.onLost);this.canvas.removeEventListener('webglcontextrestored',this.onRestored);
    const gl=this.gl;gl.deleteProgram(this.art);gl.deleteProgram(this.copy);gl.deleteTexture(this.texture);gl.deleteFramebuffer(this.fbo);gl.deleteVertexArray(this.vao);
  }
}
