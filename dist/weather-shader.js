import { PixelWeatherRenderer, resolveWeatherScene, PIXEL_TONE, WEATHER_LOOP, weatherLoopPhase } from './weather-art.js';

export const FINE_MOTION = Object.freeze({ columns: 1536, coarseColumns: 96, fps: 24 });

export function resolvePixelGrid(width, height, pixelStyle = 'fine', maxTextureSize = 4096, coarseColumns = FINE_MOTION.coarseColumns) {
  width = Math.max(1, Math.round(width)); height = Math.max(1, Math.round(height));
  const limit = Math.max(1, Math.floor(maxTextureSize));
  let columns = Math.min(pixelStyle === 'block' ? Math.max(1, Math.round(coarseColumns)) : Math.min(FINE_MOTION.columns, width), limit);
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
uniform float loopTime;
float elapsed;
uniform int scene;
uniform bool night;
uniform bool coarse;
out vec4 outputColor;
const float PI=3.14159265359;
const int BAYER[16]=int[16](0,8,2,10,12,4,14,6,3,11,1,9,15,7,13,5);
float threshold;
vec2 p;
float aspect;
vec2 lightOrigin;
float cloudCoverMask;
float solarVisibility;
float hash(vec2 v){ return fract(sin(dot(v,vec2(127.1,311.7)))*43758.5453); }
float surfaceNoise(vec2 v){vec2 cell=floor(v),f=fract(v);f=f*f*(3.-2.*f);return mix(mix(hash(cell),hash(cell+vec2(1.,0.)),f.x),mix(hash(cell+vec2(0.,1.)),hash(cell+vec2(1.)),f.x),f.y);}
vec3 light(vec3 base,vec3 glow){return 1.-(1.-base)*(1.-clamp(glow,0.,1.));}
float pulse(float center,float width){return exp(-pow((elapsed-center)/width,2.));}
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
  return d*scale + (coarse?(hash(floor(q*scale*80.))-.5)*.004:0.);
}
vec4 lobe(vec2 q,vec2 radius,float thickness){
  float crossSection=1.-dot(q/radius,q/radius);
  if(crossSection<=0.)return vec4(0.,0.,1.,-1.);
  float z=sqrt(crossSection);
  return vec4(normalize(vec3(q/(radius*radius),z/thickness)),z*thickness);
}
vec4 mergeVolume(vec4 a,vec4 b){
  float h=clamp(.5+.5*(a.w-b.w)/.025,0.,1.);
  return vec4(normalize(mix(b.xyz,a.xyz,h)),mix(b.w,a.w,h)+.025*h*(1.-h));
}
vec3 cloudNormal(vec2 q){
  vec4 volume=lobe(q,vec2(.37,.074),.09);
  volume=mergeVolume(volume,lobe(q-vec2(-.19,-.045),vec2(.12,.096),.11));
  volume=mergeVolume(volume,lobe(q-vec2(-.055,-.091),vec2(.15,.139),.15));
  volume=mergeVolume(volume,lobe(q-vec2(.115,-.068),vec2(.13,.115),.13));
  volume=mergeVolume(volume,lobe(q-vec2(.265,-.018),vec2(.12,.073),.09));
  return volume.xyz;
}
float cloudX(float start,float speed){return mod(start+.85+elapsed*speed,2.7)-.85;}
float ellipseBottom(float x,float centerX,float centerY,vec2 radius){
  float u=(x-centerX)/radius.x;
  return abs(u)<1.?centerY+radius.y*sqrt(max(0.,1.-u*u)):-2.;
}
float cloudBottom(float x,vec2 center,float scale){
  float local=(x-center.x)/scale;
  float bottom=ellipseBottom(local,0.,0.,vec2(.37,.074));
  bottom=max(bottom,ellipseBottom(local,-.19,-.045,vec2(.12,.096)));
  bottom=max(bottom,ellipseBottom(local,-.055,-.091,vec2(.15,.139)));
  bottom=max(bottom,ellipseBottom(local,.115,-.068,vec2(.13,.115)));
  bottom=max(bottom,ellipseBottom(local,.265,-.018,vec2(.12,.073)));
  return bottom> -1.?center.y+bottom*scale/aspect:-1.;
}
float rainOrigin(float x){
  return max(max(cloudBottom(x,vec2(cloudX(.18,.003),.27),1.1),cloudBottom(x,vec2(cloudX(.82,.005),.39),1.18)),cloudBottom(x,vec2(cloudX(.17,.008),.47),.93));
}
float sunBlocked(vec2 center,float scale){
  vec2 q=lightOrigin-vec2(center.x,center.y*aspect);
  if(scene==7)q*=vec2(.74,1.35+(scale-.92)*.45);
  return smoothstep(-.10,.10,bank(q,scale));
}
vec3 cloud(vec3 background,vec2 center,float scale,float depth){
  vec2 q=p-vec2(center.x,center.y*aspect);
  if(scene==7)q*=vec2(.74+depth*.20,1.35+(scale-.92)*.45);
  float d=bank(q,scale),edgeCoverage=coverage(d); if(edgeCoverage<=0.)return background;
  cloudCoverMask=max(cloudCoverMask,edgeCoverage);
  vec3 normal=cloudNormal(q/scale),lightDirection=normalize(vec3(lightOrigin-vec2(center.x,center.y*aspect),.70));
  float diffuse=max(0.,dot(normal,lightDirection));
  float underside=smoothstep(-.015,.07,q.y/scale);
  float volume=clamp(.25+diffuse*.60+.10*(1.-normal.y)-underside*.11,0.,1.);
  float original=clamp(.52-q.y/(scale*.24)+d*2.5,0.,1.);
  float shape=mix(original,volume,.16);
  vec3 lo=vec3(.26,.42,.62),mid=vec3(.58,.74,.85),high=vec3(.90,.94,.96),rim=vec3(.99,.97,.89);
  if(scene==2){lo=vec3(.16,.26,.40);mid=vec3(.39,.54,.67);high=vec3(.76,.86,.91);rim=vec3(.95,.97,.97);}
  if(scene==3){lo=vec3(.10,.20,.34);mid=vec3(.30,.47,.62);high=vec3(.62,.76,.87);rim=vec3(.87,.93,.96);}
  if(night){lo=vec3(.025,.035,.15);mid=vec3(.10,.18,.43);high=vec3(.36,.44,.78);rim=vec3(.70,.83,1.);}
  if(scene==4){lo=vec3(.075,.11,.21);mid=vec3(.18,.25,.38);high=vec3(.39,.47,.62);rim=vec3(.65,.73,.86);}
  if(scene==5){lo=vec3(.25,.44,.68);mid=vec3(.58,.75,.90);high=vec3(.88,.95,.98);rim=vec3(.99);}
  if(scene==9){lo=vec3(.19,.08,.33);mid=vec3(.56,.22,.43);high=vec3(.96,.52,.29);rim=vec3(.99,.87,.48);}
  if(scene==10){lo=vec3(.35,.39,.52);mid=vec3(.63,.63,.72);high=vec3(.94,.80,.70);rim=vec3(.99,.93,.79);}
  lo*=1.-depth*.15;
  vec3 color=ramp(lo,mid,high,rim,shape);
  float rimLight=pow(1.-max(0.,normal.z),3.)*max(0.,dot(normal.xy,lightDirection.xy));
  color=mix(color,rim,rimLight*.10*(.7+.3*solarVisibility));
  return mix(background,color,edgeCoverage);
}
vec3 cirrus(vec3 background,vec2 uv,vec2 center,float width,float offset){
  float x=(uv.x-cloudX(center.x,.0015))/width;
  float spine=center.y+.018*sin(x*3.+offset)+.012*x;
  float feather=exp(-pow((uv.y-spine)/.009,2.))*(1.-smoothstep(.25,.6,abs(x)));
  feather*=.8+.2*cos(x*11.+offset);
  return mix(background,scene==10?vec3(.99,.87,.73):vec3(.93,.94,.95),feather*.23);
}
float segment(vec2 q,vec2 a,vec2 b){vec2 v=b-a;return length(q-a-v*clamp(dot(q-a,v)/dot(v,v),0.,1.));}
vec3 birds(vec3 background,vec2 uv,bool morning){
  float epoch=floor(elapsed/83.),seed=hash(vec2(epoch,float(scene)));
  float flight=(elapsed-epoch*83.-4.-seed*8.)/(morning?27.:36.);
  if(flight<0.||flight>1.)return background;
  for(int i=0;i<5;i++){
    if(morning&&i>=2)break;
    float n=float(i),spread=morning?.048:.035;
    vec2 center=vec2(morning?flight*1.5-.25+n*spread:1.25-flight*1.5+n*spread,(morning?.43:.36)+n*.014+seed*.06);
    center.y+=.022*(flight-.5)*(flight-.5)+.002*sin(elapsed*1.3+n);
    vec2 q=vec2(uv.x-center.x,(uv.y-center.y)*aspect);
    float size=(morning?.007:.010)*(1.-n*.07),flap=sin(elapsed*(6.2+n*.17)+n*2.)*size*.62;
    vec2 leftTip=vec2(-size,flap),leftBend=vec2(-size*.48,flap*.30-.001);
    vec2 rightTip=vec2(size,flap),rightBend=vec2(size*.48,flap*.30-.001);
    float edge=min(min(segment(q,leftTip,leftBend),segment(q,leftBend,vec2(0.,.001))),min(segment(q,rightTip,rightBend),segment(q,rightBend,vec2(0.,.001))));
    float opacity=coverage((morning?.00065:.00085)-edge)*(morning?.60:.82);
    background=mix(background,morning?vec3(.22,.27,.34):vec3(.14,.13,.22),opacity);
  }
  return background;
}
vec3 sea(vec2 uv,float horizon,float reflectionCenter){
  float depth=clamp((uv.y-horizon)/(1.-horizon),0.,1.);
  bool morning=scene==10;
  vec3 near=morning?vec3(.12,.23,.34):vec3(.10,.15,.25);
  vec3 far=morning?vec3(.44,.53,.60):vec3(.43,.34,.39);
  vec3 color=ramp(near,mix(near,far,.3),mix(near,far,.68),far,1.-sqrt(depth));
  // Perspective compresses distant wavelets; horizontal fragments widen toward the viewer.
  float rows=coarse?10.:30.,rowPosition=sqrt(depth)*rows,row=floor(rowPosition);
  float crestPosition=.46+.08*sin(elapsed*.65+row*1.7);
  float crest=1.-smoothstep(.055,.16,abs(fract(rowPosition)-crestPosition));
  float columns=mix(58.,11.,depth),x=uv.x+.007*sin(elapsed*.53+row*.8);
  float tile=floor(x*columns+hash(vec2(row,3.))*9.);
  float local=fract(x*columns+hash(vec2(row,3.))*9.);
  float seed=hash(vec2(tile,row));
  float fragment=step(.16,local)*(1.-step(.46+seed*.34,local));
  float shimmer=.64+.36*sin(elapsed*(.93+seed*.27)+seed*19.+row);
  float wavelet=crest*fragment;
  color=mix(color,mix(far,vec3(.46,.56,.65),.4),wavelet*(.09+.08*depth));
  float spread=.045+depth*.22,center=reflectionCenter+.009*sin(elapsed*.41+row*2.1);
  float reflection=exp(-pow((uv.x-center)/spread,2.))*wavelet*step(.24,seed)*shimmer;
  vec3 gold=mix(vec3(.94,.51,.24),vec3(1.,.85,.47),1.-depth*.65);
  color=mix(color,gold,reflection*(.72-.17*depth));
  float glint=pow(max(0.,sin(elapsed*(1.13+seed*.31)+seed*31.+row*.9)),14.)*step(.78,seed);
  color=light(color,vec3(1.,.85,.52)*reflection*glint*.24);
  return light(color,vec3(.85,.47,.22)*exp(-depth*65.)*.10);
}
float fogShore(vec2 uv){
  vec2 q=(uv-vec2(.67,.802))/vec2(.235,.042);
  float angle=atan(q.y,q.x);
  return length(q)-1.+.025*sin(angle*7.)+.015*sin(angle*13.+.7);
}
vec3 fogCoast(vec3 color,vec2 uv){
  float waterline=.748,shore=fogShore(uv);
  if(uv.y>waterline){
    float depth=clamp((uv.y-waterline)/(1.-waterline),0.,1.);
    vec3 water=mix(vec3(.47,.56,.59),vec3(.28,.39,.44),sqrt(depth));
    float rows=sqrt(depth)*29.,row=floor(rows),columns=mix(58.,15.,depth);
    float x=uv.x+.004*sin(elapsed*.27+row*1.8);
    float tile=floor(x*columns+hash(vec2(row,31.))*7.);
    float seed=hash(vec2(tile,row)),local=fract(x*columns+hash(vec2(row,31.))*7.);
    float crest=.46+.12*sin(elapsed*(.34+seed*.15)+row*1.7);
    float ripple=(1.-smoothstep(.035,.14,abs(fract(rows)-crest)))*smoothstep(.10,.18,local)*(1.-smoothstep(.43+seed*.23,.55+seed*.23,local));
    water=mix(water,vec3(.57,.64,.65),ripple*(.14+.10*depth));
    float reflection=exp(-pow((uv.x-.68)/(.016+depth*.10),2.))*exp(-depth*5.);
    water=light(water,vec3(.87,.68,.35)*reflection*ripple*.17);
    // Expanding, broken shoreline ripples bind the rock skirt to the surrounding water.
    float coastal=pow(max(0.,sin(shore*26.-elapsed*.75)),10.)*exp(-max(0.,shore)*3.);
    coastal*=smoothstep(-.03,.08,shore)*smoothstep(.790,.816,uv.y)*(.55+.45*sin(uv.x*91.+1.));
    water=mix(water,vec3(.67,.71,.68),coastal*.16);
    color=mix(color,water,smoothstep(waterline,.786,uv.y));
  }
  vec2 lamp=vec2(.68,.584);
  // Project a slow back-and-forth beacon through the fog, fading as it faces the viewer.
  float sweep=sin(elapsed*.15-.7),dx=uv.x-lamp.x;
  float reach=.08+.47*abs(sweep),axis=lamp.y+dx*.10*sin(elapsed*.11);
  float beam=exp(-pow((uv.y-axis)/(.008+abs(dx)*.085),2.));
  beam*=(1.-smoothstep(reach*.65,reach,abs(dx)))*smoothstep(.021,.035,abs(dx));
  beam*=smoothstep(-.04,.04,sweep*dx)*abs(sweep);
  color=light(color,vec3(1.,.81,.43)*beam*.48);
  float land=1.-smoothstep(-.035,.050,shore);
  if(land>0.){
    vec2 ground=(uv-vec2(.67,.802))/vec2(.235,.042);
    float hill=sqrt(max(0.,1.-dot(ground,ground)));
    vec3 groundNormal=normalize(vec3(ground.x*.35,ground.y*.55,hill+.15));
    float diffuse=clamp(dot(groundNormal,normalize(vec3(-.4,-.35,.85))),0.,1.);
    vec3 rock=mix(vec3(.31,.39,.39),vec3(.46,.49,.43),diffuse*.80);
    float wet=smoothstep(-.28,.035,shore);
    rock=mix(rock,vec3(.30,.39,.40),wet*.55);
    // A contact shadow starts below the tower and stretches across the same island surface.
    vec2 contact=(uv-vec2(.686,.787))/vec2(.044,.008);
    float contactShadow=exp(-dot(contact,contact));
    float castShadow=exp(-pow(segment(uv,vec2(.684,.781),vec2(.75,.803))/.010,2.));
    rock*=1.-contactShadow*.25-castShadow*.13;
    color=mix(color,rock,land);
  }
  for(int i=0;i<4;i++){
    float n=float(i),seed=hash(vec2(n,53.));
    vec2 center=vec2(.50+n*.105,.820+.004*sin(n*2.1));
    vec2 q=(uv-center)/vec2(.014+seed*.009,.0035+seed*.0015);
    float shape=1.-dot(q,q);
    if(shape>0.){
      float glint=clamp(.55-.24*q.x-.18*q.y,0.,1.);
      vec3 wetRock=mix(vec3(.25,.34,.36),vec3(.43,.48,.45),glint);
      color=mix(color,wetRock,coverage(shape*.004)*.75);
    }
  }
  // A tapered cylinder: stable side shading gives the tower volume without dirty noise.
  float height=clamp((.777-uv.y)/.173,0.,1.),halfWidth=mix(.030,.020,height);
  float tower=min(halfWidth-abs(dx),min(uv.y-.604,.777-uv.y)*aspect);
  if(tower>-.65/resolution.x){
    float side=clamp(dx/halfWidth,-1.,1.);
    float illumination=clamp(.30+.61*sqrt(max(0.,1.-side*side))-.28*side,0.,1.);
    vec3 stone=mix(vec3(.42,.49,.50),vec3(.88,.87,.75),illumination);
    if((uv.y>.647&&uv.y<.664)||(uv.y>.710&&uv.y<.728))stone=mix(vec3(.37,.29,.27),vec3(.75,.45,.32),illumination);
    if(abs(dx-.005)<.003&&uv.y>.680&&uv.y<.696)stone=vec3(.26,.32,.33);
    color=mix(color,stone,coverage(tower));
  }
  float foundation=min(.036-abs(dx),min(uv.y-.773,.783-uv.y)*aspect);
  vec3 foundationStone=mix(vec3(.63,.65,.57),vec3(.37,.45,.44),smoothstep(-.026,.027,dx));
  color=mix(color,foundationStone,coverage(foundation));
  float stepBase=min(.041-abs(dx+.001),min(uv.y-.781,.790-uv.y)*aspect);
  color=mix(color,foundationStone*.90,coverage(stepBase));
  // A narrow foreground gravel lip occludes the plinth's bottom edge, embedding it in the ground.
  float gravel=exp(-pow((uv.y-(.789+.0018*sin(uv.x*210.)))/.0032,2.))*(1.-smoothstep(.034,.052,abs(dx)));
  color=mix(color,vec3(.36,.42,.39),gravel*.70);
  float lantern=min(.025-abs(dx),min(uv.y-.566,.604-uv.y)*aspect);
  if(lantern>0.){
    float glass=step(abs(dx),.019)*step(uv.y,.598)*step(.571,uv.y);
    vec3 lanternColor=mix(vec3(.28,.35,.36),vec3(.98,.80,.38),glass);
    if(abs(abs(dx)-.013)<.0015)lanternColor=vec3(.39,.42,.38);
    color=lanternColor;
  }
  float balcony=min(.033-abs(dx),min(uv.y-.601,.609-uv.y)*aspect);
  color=mix(color,vec3(.35,.41,.41),coverage(balcony));
  float roof=min(.034-abs(dx),min(uv.y-(.547+abs(dx)*.48),.569-uv.y)*aspect);
  color=mix(color,mix(vec3(.28,.35,.36),vec3(.45,.50,.46),clamp(.5-dx*12.,0.,1.)),coverage(roof));
  float glow=exp(-dot((uv-lamp)*vec2(1.,aspect),(uv-lamp)*vec2(1.,aspect))/.00065);
  return light(color,vec3(1.,.75,.30)*glow*.25);
}
vec3 paint(float sceneTime){
  elapsed=sceneTime;
  vec2 cell=floor(vec2(gl_FragCoord.x,resolution.y-gl_FragCoord.y));
  vec2 uv=(cell+.5)/resolution;
  aspect=resolution.y/resolution.x; p=vec2(uv.x,uv.y*aspect);
  cloudCoverMask=0.;solarVisibility=1.;
  threshold=coarse?.5:(float(BAYER[int(mod(cell.y,4.))*4+int(mod(cell.x,4.))])+.5)/16.;
  float wave=sin(elapsed*.09);
  bool warm=scene==9||scene==10;
  vec2 solar=scene==10?vec2(.32,.72*aspect):scene==9?vec2(.66,.68*aspect):vec2(.68,.27*aspect);
  float solarTravel=(1.-exp(-elapsed/600.))*.025*aspect;
  if(scene==10)solar.y-=solarTravel;
  if(scene==9)solar.y+=solarTravel;
  lightOrigin=solar;
  if(scene==1||scene==7){
    solarVisibility=1.-max(max(sunBlocked(vec2(cloudX(.11,scene==7?.025:.004),.64),.92),sunBlocked(vec2(cloudX(.70,scene==7?.018:.003),.46),1.04)),sunBlocked(vec2(cloudX(1.07,scene==7?.035:.007),.83),.85));
  }
  float airTransmission=.97+.025*sin(elapsed*.023)+.005*sin(elapsed*.061);
  float sceneLight=airTransmission*(.94+.06*solarVisibility);
  vec3 a=vec3(.18,.37,.72),b=vec3(.24,.53,.82),c=vec3(.45,.68,.86),d=vec3(.76,.84,.87);
  if(scene==2){a=vec3(.16,.29,.46);b=vec3(.28,.47,.63);c=vec3(.49,.66,.77);d=vec3(.78,.87,.91);}
  if(scene==3){a=vec3(.07,.16,.32);b=vec3(.14,.31,.52);c=vec3(.27,.47,.66);d=vec3(.54,.70,.83);}
  if(scene==5){a=vec3(.20,.39,.66);b=vec3(.37,.59,.79);c=vec3(.59,.76,.88);d=vec3(.88,.94,.96);}
  if(scene==11){a=vec3(.66,.71,.73);b=vec3(.72,.76,.77);c=vec3(.79,.81,.81);d=vec3(.86,.86,.83);}
  if(scene==6){a=vec3(.34,.40,.47);b=vec3(.51,.58,.63);c=vec3(.71,.75,.75);d=vec3(.87,.88,.83);}
  if(night||scene==4||scene==8){a=vec3(.025,.025,.075);b=vec3(.07,.08,.18);c=vec3(.13,.20,.35);d=vec3(.29,.38,.53);}
  if(scene==4){a=vec3(.04,.06,.13);b=vec3(.08,.13,.25);c=vec3(.16,.25,.39);d=vec3(.29,.40,.56);}
  if(scene==9){a=vec3(.18,.10,.37);b=vec3(.43,.18,.44);c=vec3(.88,.36,.30);d=vec3(.98,.69,.30);}
  if(scene==10){a=vec3(.16,.27,.48);b=vec3(.38,.48,.65);c=vec3(.78,.67,.70);d=vec3(.98,.79,.55);}
  float atmosphere=uv.y*.91+.04*sin(uv.x*3.)+.012*sin(elapsed*.027+uv.x*4.);
  vec3 color=ramp(a,b,c,d,atmosphere);
  if(!night&&!warm&&(scene==0||scene==1||scene==7)){
    float haze=exp(-pow((uv.x-.15)/.7,2.)-pow((uv.y-.82)/.38,2.));
    color=mix(color,vec3(.98,.79,.59),haze*.14);
    color=mix(color,vec3(.39,.35,.71),pow(1.-uv.y,3.)*uv.x*.08);
    color=cirrus(color,uv,vec2(.17,.16),.65,1.3);
    if(scene==0||scene==7)color=cirrus(color,uv,vec2(.76,.11),.72,4.7);
  }
  if(scene==10){
    color=cirrus(color,uv,vec2(.22,.16),.72,2.3);
    color=cirrus(color,uv,vec2(.78,.28),.50,5.1);
    float morningGlow=exp(-pow((uv.y-.735)/.12,2.));
    color=light(color,vec3(1.,.61,.29)*morningGlow*.085);
  }
  float r=length(p-solar);
  if(scene==0||scene==1||scene==7||warm){
    float halo=exp(-r*r/(warm?.105:.075));
    vec3 haloColor=scene==10?vec3(1.,.73,.35):scene==9?vec3(1.,.48,.10):vec3(1.,.61,.17);
    color=light(color,haloColor*halo*(.15+.025*wave));
    // Broad, uneven shafts dissolve into the atmosphere; clouds occlude the light.
    float angle=atan(p.x-solar.x,p.y-solar.y);
    float rays=.70*exp(-pow((angle+.78+wave*.025)/.21,2.))+.38*exp(-pow((angle+.24-wave*.018)/.13,2.))+.48*exp(-pow((angle-.62+wave*.012)/.20,2.));
    rays*=smoothstep(.11,.26,r)*(1.-smoothstep(.48,1.15,r))*(.85+.15*sin(elapsed*.07+r*9.));
    rays=coarse?rays:floor(rays*24.+threshold)/24.;
    if(p.y>solar.y)color=light(color,haloColor*rays*.20*solarVisibility);
    float radius=scene==10?.105:scene==7?.115:warm?.12:.135;
    if(r<radius+.65/resolution.x){
    vec2 sunLocal=(p-solar)/radius;
    float limb=sqrt(max(0.,1.-dot(sunLocal,sunLocal)));
    float granules=(surfaceNoise(sunLocal*25.+vec2(elapsed*.004,0.))-.5)*.09+(surfaceNoise(sunLocal*67.)-.5)*.025;
    float sunTone=clamp(.52-sunLocal.y*.25+limb*.17+granules,0.,1.);
    if(!warm)sunTone=clamp(.46-sunLocal.y*.27+limb*.11+granules,0.,1.);
    vec3 sunColor=warm?ramp(vec3(.97,.43,.09),vec3(.99,.72,.19),vec3(1.,.89,.47),vec3(1.,.98,.82),sunTone):ramp(vec3(.94,.22,.07),vec3(1.,.44,.08),vec3(1.,.73,.25),vec3(1.,.94,.65),sunTone);
    if(scene==7)sunColor=ramp(vec3(.87,.55,.24),vec3(.97,.73,.40),vec3(.99,.87,.62),vec3(1.,.97,.83),clamp(.64-sunLocal.y*.15+limb*.10+granules*.5,0.,1.));
    color=mix(color,sunColor,coverage(radius-r));
    }
  }
  if(scene==7){
    float filteredLight=exp(-pow((uv.y-(.263+.011*sin((uv.x-elapsed*.012)*8.)))/.010,2.));
    color=mix(color,vec3(.84,.89,.92),filteredLight*.10);
  }
  if(scene==8){
    float nightBand=exp(-pow((uv.x-.16-uv.y*.43)/.24,2.));
    color=light(color,vec3(.17,.13,.30)*nightBand*(.18+.05*surfaceNoise(uv*4.)));
    for(int layer=0;layer<3;layer++){
      float n=float(layer);vec2 field=vec2(65.-n*22.,79.-n*26.);
      vec2 starCell=floor(uv*field),starLocal=fract(uv*field);
      float seed=hash(starCell+vec2(n*91.,n*37.));
      float rate=.24+seed*.67+n*.11;
      float twinkle=.5+.35*sin(elapsed*rate+seed*31.)+.15*sin(elapsed*(rate*3.7+1.)+seed*17.);
      float starSize=(.055+n*.010)*(.8+twinkle*.2);
      vec2 sparkle=abs(starLocal-.5);
      float star=1.-smoothstep(starSize*.45,starSize,length(starLocal-.5));
      if(layer==2&&seed>.991)star=max(star,(1.-smoothstep(.007,.017,min(sparkle.x,sparkle.y)))*(1.-smoothstep(.045,.14,max(sparkle.x,sparkle.y))));
      if(seed>.960-n*.014){
        float warmth=hash(starCell+vec2(47.+n*13.,91.));
        vec3 starColor=warmth>.70?mix(vec3(1.,.66,.24),vec3(1.,.91,.55),twinkle):mix(vec3(.52,.67,.90),vec3(.93,.96,1.),seed);
        color=light(color,starColor*star*(.64+n*.23)*(.30+twinkle*.70));
        if(layer==2&&warmth>.86)color=light(color,starColor*exp(-dot(starLocal-.5,starLocal-.5)/.012)*twinkle*.06);
      }
    }
    float moon=length(p-solar),cut=length(p-solar-vec2(.064,-.035));
    float lunarTransmission=.91+.07*sin(elapsed*.08)+.02*sin(elapsed*.19);
    color=light(color,vec3(.38,.43,.61)*exp(-moon*moon/.05)*(.13+.015*wave)*lunarTransmission);
    if(moon<.136&&cut>.123){
    vec2 lunar=(p-solar)/.135;
    float lunarZ=sqrt(max(0.,1.-dot(lunar,lunar)));
    vec3 lunarNormal=normalize(vec3(lunar,lunarZ+.001));
    float maria=smoothstep(.42,.73,surfaceNoise(lunar*3.2+vec2(7.,4.)))*.12;
    float craterDetail=0.;
    for(int i=0;i<9;i++){
      float n=float(i);vec2 center=vec2(hash(vec2(n,23.)),hash(vec2(41.,n)))*1.5-.75;
      float radius=.035+hash(vec2(n,62.))*.075,distance=length(lunar-center)/radius;
      craterDetail+=exp(-pow((distance-.88)/.12,2.))*.035-exp(-distance*distance*2.)*.025;
    }
    float lunarTone=.42+.38*max(0.,dot(lunarNormal,normalize(vec3(-.55,-.38,.75))))-maria+craterDetail;
    vec3 lunarColor=ramp(vec3(.42,.49,.62),vec3(.65,.71,.78),vec3(.84,.85,.79),vec3(.99,.97,.86),lunarTone);
    color=mix(color,lunarColor*lunarTransmission,coverage(min(.135-moon,cut-.124)));
    }
    float epoch=floor(elapsed/53.),seedMeteor=hash(vec2(epoch,71.));
    float meteor=(elapsed-epoch*53.-6.-seedMeteor*25.)/(1.8+seedMeteor);
    if(meteor>0.&&meteor<1.){
      vec2 start=vec2(.05+seedMeteor*.28,.07+hash(vec2(epoch,19.))*.08);
      vec2 head=start+vec2(.47,.22)*meteor,tail=head-vec2(.13,.054);
      float trail=exp(-pow(segment(uv,tail,head)/.0025,2.))*sin(meteor*PI);
      color=light(color,vec3(.53,.86,1.)*trail*.85);
    }
  }
  if(scene==1||scene==7){
    color=cloud(color,vec2(cloudX(.11,scene==7?.025:.004),.64),.92,0.);
    color=cloud(color,vec2(cloudX(.70,scene==7?.018:.003),.46),1.04,.08);
    color=cloud(color,vec2(cloudX(1.07,scene==7?.035:.007),.83),.85,.04);
  }
  if(scene==2||scene==3||scene==4||scene==5){
    color=cloud(color,vec2(cloudX(.18,.003),.27),1.1,.25);
    color=cloud(color,vec2(cloudX(.82,.005),.39),1.18,scene==4?.95:.4);
    color=cloud(color,vec2(cloudX(.17,.008),.47),.93,scene==4?.85:.3);
    if(scene==2)color=cloud(color,vec2(cloudX(.8,.010),.81),1.05,.15);
  }
  if(scene==0)color=cloud(color,vec2(cloudX(-.18,.005),.82),.85,0.);
  if(scene==8)color=cloud(color,vec2(cloudX(-.15,.003),.77),.75,.1);
  if(warm){
    float horizon=.735;
    if(uv.y>=horizon)color=sea(uv,horizon,solar.x);
    if(scene==10){
      float seaMist=exp(-pow((uv.y-.749-.005*sin((uv.x-elapsed*.004)*7.))/.016,2.));
      color=mix(color,vec3(.86,.84,.78),seaMist*.13);
      color=cloud(color,vec2(cloudX(.10,.003),.39),.62,.20);
      color=cloud(color,vec2(cloudX(.91,.005),.24),.70,.25);
    }else{
      color=cloud(color,vec2(cloudX(-.08,.003),.29),.90,.18);
      color=cloud(color,vec2(cloudX(1.04,.004),.47),.78,.25);
    }
    color=birds(color,uv,scene==10);
  }
  if(scene==3||scene==4){
    float footprint=rainOrigin(uv.x),origin=footprint;
    float sourceX=uv.x+max(0.,uv.y-max(0.,origin))*.16;
    origin=rainOrigin(sourceX);
    float fall=uv.y-origin;
    // Drifted source coordinates must not create rain beyond the visible cloud footprint.
    float rainMask=footprint>=0.&&origin>=0.&&fall>=0.?(1.-cloudCoverMask):0.;
    for(int layer=0;layer<3;layer++){
      float n=float(layer),columns=47.-n*10.,rows=18.-n*3.,speed=(scene==4?1.18:.81)+n*.37;
      vec2 rainUV=vec2(sourceX,fall);
      float column=floor(rainUV.x*columns),seed=hash(vec2(column,n+1.));
      vec2 drop=vec2(fract(rainUV.x*columns),fract(rainUV.y*rows-elapsed*speed+seed*11.));
      float intensity=.78+.15*sin(elapsed*.045)+.07*sin(elapsed*.11+n);
      float tail=(1.-smoothstep(0.,.32+n*.06,drop.y))*(.22+n*.18)*intensity;
      if(rainMask>0.&&seed>.18&&drop.x<.075+n*.04){
        float rain=drop.y<.32+n*.06?tail:0.;
        rain+=exp(-max(0.,fall)*40.)*.07;
        color=mix(color,vec3(.61,.73,.87),clamp(rain*rainMask,0.,1.));
      }
    }
  }
  if(scene==4){
    float epoch=floor(elapsed/32.),strike=epoch*32.+3.+hash(vec2(epoch,7.))*6.;
    float strike2=epoch*32.+17.+hash(vec2(epoch,29.))*8.;
    float flash=min(1.,pulse(strike,.13)+.62*pulse(strike+.22,.075));
    float secondary=pulse(strike2,.16);
    vec2 q=uv-vec2((hash(vec2(epoch,11.))-.5)*.30,0.);
    float bolt=min(min(segment(q,vec2(.64,.40),vec2(.54,.56)),segment(q,vec2(.54,.56),vec2(.62,.55))),segment(q,vec2(.62,.55),vec2(.47,.76)));
    bolt=min(bolt,min(segment(q,vec2(.58,.50),vec2(.72,.58)),segment(q,vec2(.72,.58),vec2(.76,.69))));
    vec2 q2=uv-vec2((hash(vec2(epoch,43.))-.5)*.32,0.);
    float bolt2=min(segment(q2,vec2(.27,.43),vec2(.35,.61)),segment(q2,vec2(.35,.61),vec2(.23,.80)));
    color=light(color,vec3(.39,.43,1.)*(flash*.22+secondary*.16));
    color=light(color,vec3(.38,.52,1.)*(exp(-pow(bolt/.025,2.))*flash+exp(-pow(bolt2/.022,2.))*secondary)*.58);
    color=mix(color,vec3(.94,.99,1.),max(coverage(.0035-bolt)*flash,coverage(.003-bolt2)*secondary));
  }
  if(scene==5){
    float snowOrigin=rainOrigin(uv.x);
    for(int layer=0;layer<3;layer++){
      float n=float(layer);vec2 v=vec2(uv.x+wave*.018*(n+1.),uv.y-elapsed*(.07+n*.037));
      vec2 tile=floor(v*vec2(23.+n*7.,17.+n*8.));vec2 flake=fract(v*vec2(23.+n*7.,17.+n*8.))-.5;
      float size=n==0.?.075:n==1.?.045:.028;
      if(snowOrigin>=0.&&uv.y>snowOrigin&&cloudCoverMask<.01&&hash(tile)>.61 && (length(flake)<size||(hash(tile)>.93&&min(abs(flake.x),abs(flake.y))<.026&&max(abs(flake.x),abs(flake.y))<.14)))color=vec3(.97,.98,.96);
    }
  }
  if(scene==6){
    float ridge=.62+.025*sin(uv.x*9.)+.012*sin(uv.x*23.);
    color=mix(color,vec3(.27,.37,.42),smoothstep(ridge-.009,ridge+.009,uv.y)*.42);
    color=fogCoast(color,uv);
    for(int layer=0;layer<5;layer++){
      float n=float(layer),shift=cloudX(-.25+n*.35,.006+n*.003);
      float line=.23+n*.15+.035*sin((uv.x-shift)*4.+n*2.);
      float envelope=exp(-pow((uv.x-shift)/(.53+n*.035),2.));
      float band=exp(-pow((uv.y-line)/(.060+n*.013),2.))*(.38+.62*envelope);
      float density=coarse?floor(band*4.+.5)/4.:floor(band*48.+threshold)/48.;
      color=mix(color,n<2.?vec3(.76,.81,.83):vec3(.94,.94,.89),density*(.30+n*.060));
    }
    float valleyMist=exp(-pow((uv.y-(.66+.030*sin((uv.x-elapsed*.009)*5.+1.)))/.045,2.));
    float foregroundMist=exp(-pow((uv.y-(.84+.038*sin((uv.x-elapsed*.017)*6.)))/.060,2.));
    float mist=coarse?floor((valleyMist*.25+foregroundMist*.36)*8.+.5)/8.:floor((valleyMist*.25+foregroundMist*.36)*48.+threshold)/48.;
    color=mix(color,vec3(.91,.93,.89),mist);
    color=light(color,vec3(.73,.64,.43)*exp(-dot(uv-vec2(.28,.58),uv-vec2(.28,.58))/.08)*.055);
  }
  if(scene==7){
    float gust=.70+.18*sin(elapsed*.11)+.12*sin(elapsed*.037);
    for(int layer=0;layer<3;layer++)for(int i=0;i<4;i++){
      float n=float(layer),index=float(i),seed=hash(vec2(index,n+17.));
      float head=mod(elapsed*(.10+n*.065+seed*.025)+seed*1.6,1.6)-.20;
      float length=.12-n*.026+seed*.025,tail=uv.x-head;
      float line=.38+hash(vec2(index+7.,n+29.))*.55+.006*sin(uv.x*5.+elapsed*.08+seed*19.);
      float width=max((.00065+n*.00040),.65/resolution.y);
      float feather=1.-smoothstep(width*.20,width,abs(uv.y-line));
      float taper=smoothstep(-length,-length*.45,tail)*(1.-smoothstep(-.008,.002,tail));
      color=light(color,vec3(.76,.85,.91)*feather*taper*(.14+n*.10)*gust);
    }
  }
  color*=sceneLight;
  // Grade luminance rather than RGB channels independently to retain the weather palette.
  float luminance=dot(color,vec3(.2126,.7152,.0722));
  color=clamp(mix(vec3(luminance),color,${PIXEL_TONE.saturation}),0.,1.);
  luminance=dot(color,vec3(.2126,.7152,.0722));
  float lift=smoothstep(${PIXEL_TONE.lowKnee},${PIXEL_TONE.highKnee},luminance);
  float graded=mix(luminance*${PIXEL_TONE.shadowGain},1.-(1.-luminance)*${PIXEL_TONE.highlightRemainder},lift);
  color=clamp(color*graded/max(luminance,.001),0.,1.)*${PIXEL_TONE.peak};
  return color;
}
void main(){
  vec3 color=paint(loopTime);
  // Advance both timelines forward; blend into a short pre-roll so the wrap is continuous.
  if(loopTime>${WEATHER_LOOP.seconds-WEATHER_LOOP.blendSeconds}.){
    float blend=smoothstep(${WEATHER_LOOP.seconds-WEATHER_LOOP.blendSeconds}.,${WEATHER_LOOP.seconds}.,loopTime);
    color=mix(color,paint(loopTime-${WEATHER_LOOP.seconds}.),blend);
  }
  outputColor=vec4(color,1.);
}`;
const SCENES = ['clear','partly','overcast','rain','storm','snow','fog','wind','night','sunset','dawn','unknown'];

// Native-resolution presentation; only the art pass runs on the fine logical grid.
export class FineWeatherRenderer {
  constructor(canvas, weather, {pixelStyle='fine'}={}) {
    this.pixelStyle=pixelStyle==='block'?'block':'fine';this.columns=this.pixelStyle==='block'?FINE_MOTION.coarseColumns:FINE_MOTION.columns;
    this.canvas=canvas; this.weather=weather; this.animated=true; this.visible=true; this.frame=0;this.elapsed=0;this.tick=0;
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
    this.locations=Object.fromEntries(['resolution','loopTime','scene','night','coarse'].map(key=>[key,gl.getUniformLocation(this.art,key)]));
    this.texture=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,this.texture);
    this.maxTextureSize=gl.getParameter(gl.MAX_TEXTURE_SIZE);
    gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);
    gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
    this.fbo=gl.createFramebuffer();gl.bindFramebuffer(gl.FRAMEBUFFER,this.fbo);gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.COLOR_ATTACHMENT0,gl.TEXTURE_2D,this.texture,0);
    this.canvas.dataset.renderer='webgl2-'+this.columns+(this.pixelStyle==='block'?'-flat':'-bayer4');
  }
  resize(){
    if(this.presentationActive===false)return;
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
    if(this.presentationActive===false)return;
    const gl=this.gl;if(gl.isContextLost()||!this.rows)return;
    this.frame=frame;const scene=resolveWeatherScene(this.weather);
    gl.bindVertexArray(this.vao);gl.bindFramebuffer(gl.FRAMEBUFFER,this.fbo);gl.viewport(0,0,this.columns,this.rows);gl.useProgram(this.art);
    gl.uniform2f(this.locations.resolution,this.columns,this.rows);
    const phase=weatherLoopPhase(this.reduced.matches?0:this.elapsed);
    gl.uniform1f(this.locations.loopTime,phase.time);
    gl.uniform1i(this.locations.coarse,this.pixelStyle==='block'?1:0);gl.uniform1i(this.locations.scene,SCENES.indexOf(scene));gl.uniform1i(this.locations.night,this.weather.isDay===false&&!['dawn','sunset','fog'].includes(scene)?1:0);
    // The art pass must not sample its own render target.
    gl.bindTexture(gl.TEXTURE_2D,null);gl.drawArrays(gl.TRIANGLES,0,3);
    gl.bindFramebuffer(gl.FRAMEBUFFER,null);gl.viewport(0,0,this.canvas.width,this.canvas.height);gl.useProgram(this.copy);gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,this.texture);gl.drawArrays(gl.TRIANGLES,0,3);
    this.canvas.dataset.scene=scene;this.canvas.dataset.frame=String(frame);this.canvas.dataset.elapsed=this.elapsed.toFixed(2);this.canvas.dataset.loopSeconds=String(WEATHER_LOOP.seconds);this.canvas.dataset.loopTime=phase.time.toFixed(2);
  }
  setPresentationActive(value){this.presentationActive=value;if(this.fallback)this.fallback.setPresentationActive(value);if(value){this.resize();this.sync();}else cancelAnimationFrame(this.raf);}
  setPixelStyle(style){if(!['fine','block'].includes(style))return;this.pixelStyle=style;this.columns=style==='block'?FINE_MOTION.coarseColumns:FINE_MOTION.columns;if(!this.fallback)this.resize();}
  setWeather(weather){this.weather=weather;if(this.fallback)return this.fallback.setWeather(weather);this.draw(this.reduced.matches?0:this.frame);}
  setAnimated(value){if(this.fallback)return this.fallback.setAnimated(value);if(value&&!this.animated)this.origin=performance.now()-this.elapsed*1000;this.animated=value;this.sync();}
  sync(){
    cancelAnimationFrame(this.raf);if(this.presentationActive===false||!this.animated||document.hidden||!this.visible||this.gl.isContextLost())return;
    if(this.reduced.matches){this.draw(0);return;}
    const loop=now=>{const tick=Math.floor((now-this.origin)*FINE_MOTION.fps/1000);if(tick!==this.tick){this.tick=tick;this.elapsed=tick/FINE_MOTION.fps;this.draw(tick);}this.raf=requestAnimationFrame(loop);};this.raf=requestAnimationFrame(loop);
  }
  destroy(){
    if(this.fallback)return this.fallback.destroy();
    cancelAnimationFrame(this.raf);this.resizeObserver.disconnect();this.intersectionObserver.disconnect();
    document.removeEventListener('visibilitychange',this.onVisibility);this.reduced.removeEventListener('change',this.onMotion);
    this.canvas.removeEventListener('webglcontextlost',this.onLost);this.canvas.removeEventListener('webglcontextrestored',this.onRestored);
    const gl=this.gl;gl.deleteProgram(this.art);gl.deleteProgram(this.copy);gl.deleteTexture(this.texture);gl.deleteFramebuffer(this.fbo);gl.deleteVertexArray(this.vao);
  }
}
