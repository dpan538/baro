import { resolvePixelGrid } from './weather-shader.js';

// A separate panoramic scene and program; the GL context is shared to keep the
// eleven-scene gallery within browser context limits. No weather artwork is drawn.
const INSTRUMENT_VERTEX=`#version 300 es
void main(){vec2 p=vec2(float((gl_VertexID<<1)&2),float(gl_VertexID&2));gl_Position=vec4(p*2.-1.,0.,1.);}`;
const INSTRUMENT_FRAGMENT=`#version 300 es
precision highp float;
uniform vec2 screen,grid;
uniform float clock,cloud,rain,fog,elevation,bearing;
uniform bool coarse;
out vec4 outputColor;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1)),f.x),f.y);}
float terrain(float x,float seed){return .19*sin(x*5.+seed)+.06*sin(x*17.+seed*2.)+.025*sin(x*43.);}
void main(){
 vec2 cell=floor(gl_FragCoord.xy/screen*grid),uv=(cell+.5)/grid;float aspect=screen.x/screen.y;
 float night=1.-smoothstep(-12.,2.,elevation),warm=(1.-smoothstep(3.,18.,abs(elevation)))*(1.-night*.5);
 vec3 top=mix(vec3(.24,.41,.52),vec3(.035,.055,.12),night),bottom=mix(vec3(.84,.77,.61),vec3(.22,.28,.36),night);
 bottom=mix(bottom,vec3(.91,.49,.31),warm*.76);vec3 col=mix(bottom,top,pow(uv.y,.6));
 float sx=.25+.5*fract(bearing/360.);vec2 sun=vec2(sx,.31+clamp(elevation,-8.,45.)*.012);
 float radius=length(vec2((uv.x-sun.x)*aspect,uv.y-sun.y));
 col+=vec3(.8,.32,.12)*exp(-radius*radius*3.)*warm*.2;
 if(elevation>-.83){float disc=1.-smoothstep(.105,.115,radius);col=mix(col,vec3(1.,.78,.36),disc*(1.-cloud*.65));}
 for(int i=0;i<3;i++){
  float layer=float(i),a=noise(vec2(uv.x*8.+clock*(.007+layer*.004)+layer*9.,uv.y*9.+layer));
  float band=exp(-pow((uv.y-(.54+layer*.13))/.07,2.));
  float mask=smoothstep(.48,.75,a)*band*cloud*(.52-layer*.08);
  col=mix(col,mix(vec3(.75,.75,.68),vec3(.27,.31,.42),night),mask);
 }
 float ridge=.29+terrain(uv.x,3.)*.33;
 if(uv.y<ridge)col=mix(vec3(.23,.29,.30),vec3(.12,.16,.24),night);
 float nearRidge=.15+terrain(uv.x,8.)*.19;
 if(uv.y<nearRidge)col=mix(vec3(.10,.18,.20),vec3(.055,.09,.15),night);
 // Thin reflective water lines use their own flow, independent of the cloud bands.
 if(uv.y<.13){float ripple=sin(uv.y*330.+sin(uv.x*21.+clock*.16)*1.8+clock*.26);col+=vec3(.14,.17,.15)*smoothstep(.87,1.,ripple)*(.3+.7*noise(vec2(uv.x*130.,uv.y*20.)));}
 float mist=noise(vec2(uv.x*4.-clock*.012,uv.y*9.+clock*.007));
 col=mix(col,vec3(.71,.75,.70),fog*(.24+.5*mist)*exp(-pow((uv.y-.28)/.36,2.)));
 float rainLine=step(.963,hash(vec2(floor((uv.x+uv.y*.012)*grid.x/2.),0.)))*step(.68,fract(uv.y*14.+clock*.95+uv.x*40.));
 col=mix(col,vec3(.61,.74,.78),rainLine*rain*.42);
 if(night>.5&&uv.y>.45){float star=step(.9992,hash(cell));col+=vec3(.8,.68,.4)*star*(.65+.35*sin(clock*.4+cell.x))*(1.-cloud);}
 // Native-density ordered dither, distinct from the first page's artwork.
 const float bayer[16]=float[16](0.,8.,2.,10.,12.,4.,14.,6.,3.,11.,1.,9.,15.,7.,13.,5.);
 float threshold=(bayer[int(mod(cell.x,4.))+int(mod(cell.y,4.))*4]+.5)/16.;
 float levels=coarse?18.:42.;col=floor(clamp(col,0.,1.)*levels+(coarse?.5:threshold))/levels;
 outputColor=vec4(col,1.);
}`;
export class InstrumentRenderer{
 constructor(renderer){
  this.owner=renderer;this.canvas=renderer.canvas;this.gl=renderer.gl;this.elapsed=0;this.visible=true;this.reduced=matchMedia('(prefers-reduced-motion: reduce)');
  this.onVisibility=()=>this.sync();document.addEventListener('visibilitychange',this.onVisibility);this.reduced.addEventListener('change',this.onVisibility);
  this.onRestore=()=>{this.initialize();this.resize();};this.canvas.addEventListener('webglcontextrestored',this.onRestore);
  if(this.gl)this.initialize();this.observer=new ResizeObserver(()=>this.resize());this.observer.observe(this.canvas);this.visibilityObserver=new IntersectionObserver(entries=>{this.visible=entries[0].isIntersecting;this.sync();});this.visibilityObserver.observe(this.canvas);
 }
 initialize(){
  const gl=this.gl,compile=(type,source)=>{const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw new Error(gl.getShaderInfoLog(s));return s;};
  const v=compile(gl.VERTEX_SHADER,INSTRUMENT_VERTEX),f=compile(gl.FRAGMENT_SHADER,INSTRUMENT_FRAGMENT);this.program=gl.createProgram();gl.attachShader(this.program,v);gl.attachShader(this.program,f);gl.linkProgram(this.program);gl.deleteShader(v);gl.deleteShader(f);if(!gl.getProgramParameter(this.program,gl.LINK_STATUS))throw new Error(gl.getProgramInfoLog(this.program));
  this.vao=gl.createVertexArray();this.locations=Object.fromEntries(['screen','grid','clock','cloud','rain','fog','elevation','bearing','coarse'].map(k=>[k,gl.getUniformLocation(this.program,k)]));
 }
 update(view,{pixelStyle,motionEnabled,galleryPlaying=true}){this.view=view;this.pixelStyle=pixelStyle;this.animated=motionEnabled&&galleryPlaying;this.resize();this.sync();}
 resize(){const box=this.canvas.getBoundingClientRect();if(!box.width||!box.height)return;this.canvas.width=Math.round(box.width*(devicePixelRatio||1));this.canvas.height=Math.round(box.height*(devicePixelRatio||1));this.grid=resolvePixelGrid(this.canvas.width,this.canvas.height,this.pixelStyle,this.gl?.getParameter(this.gl.MAX_TEXTURE_SIZE)??4096);this.draw();}
 draw(){
  if(!this.view||!this.grid)return;
  const m=this.view.m,gl=this.gl;
  if(gl){
   if(gl.isContextLost())return;gl.bindFramebuffer(gl.FRAMEBUFFER,null);gl.viewport(0,0,this.canvas.width,this.canvas.height);gl.bindVertexArray(this.vao);gl.useProgram(this.program);const u=this.locations;
   gl.uniform2f(u.screen,this.canvas.width,this.canvas.height);gl.uniform2f(u.grid,this.grid.columns,this.grid.rows);gl.uniform1f(u.clock,this.reduced.matches?0:this.elapsed);gl.uniform1f(u.cloud,Math.max(m.high,m.mid,m.low)/100);gl.uniform1f(u.rain,['rain','storm'].includes(this.view.preset)?m.precipitation/100:0);gl.uniform1f(u.fog,this.view.preset==='fog'?.92:Math.max(0,1-m.visibility/15)*.5);gl.uniform1f(u.elevation,m.sun?.elevation??0);gl.uniform1f(u.bearing,m.sun?.azimuth??0);gl.uniform1i(u.coarse,this.pixelStyle==='block'?1:0);gl.drawArrays(gl.TRIANGLES,0,3);
  }else{
   const ctx=this.owner.fallback?.ctx;if(!ctx)return;const w=this.canvas.width,h=this.canvas.height,g=ctx.createLinearGradient(0,0,0,h);g.addColorStop(0,'#243e51');g.addColorStop(1,'#c0af91');ctx.fillStyle=g;ctx.fillRect(0,0,w,h);for(let layer=0;layer<3;layer++){ctx.fillStyle=['#6b7978','#415b60','#233b43'][layer];ctx.beginPath();ctx.moveTo(0,h);for(let x=0;x<=w;x+=3)ctx.lineTo(x,h*(.68+layer*.1)+Math.sin(x/w*12+layer)*h*.06);ctx.lineTo(w,h);ctx.fill();}
  }
  Object.assign(this.canvas.dataset,{renderer:gl?'instrument-webgl2':'instrument-canvas-fallback',scene:'atmospheric-transect',purpose:this.view.purpose,grid:this.grid.columns+'×'+this.grid.rows,resolution:this.canvas.width+'×'+this.canvas.height,elapsed:this.elapsed.toFixed(2),pixelStyle:this.pixelStyle});
 }
 sync(){cancelAnimationFrame(this.raf);if(!this.animated||!this.visible||document.hidden||this.reduced.matches){this.draw();return;}this.origin=performance.now()-this.elapsed*1000;let tick=-1;const loop=now=>{const next=Math.floor((now-this.origin)/1000*12);if(next!==tick){tick=next;this.elapsed=next/12;this.draw();}this.raf=requestAnimationFrame(loop);};this.raf=requestAnimationFrame(loop);}
 destroy(){cancelAnimationFrame(this.raf);this.observer.disconnect();this.visibilityObserver.disconnect();document.removeEventListener('visibilitychange',this.onVisibility);this.reduced.removeEventListener('change',this.onVisibility);this.canvas.removeEventListener('webglcontextrestored',this.onRestore);if(this.gl){this.gl.deleteProgram(this.program);this.gl.deleteVertexArray(this.vao);}}
}
