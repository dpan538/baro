#version 300 es
precision highp float;
uniform vec2 screen,grid;
uniform float clock,cloud,rain,fog,elevation,bearing,moonPhase;
uniform int scene,weatherScene;
uniform bool coarse;
out vec4 outputColor;
const float PI=3.14159265359;
const int BAYER[16]=int[16](0,8,2,10,12,4,14,6,3,11,1,9,15,7,13,5);
float threshold,aspect,pixel;
vec2 cell,uv,p,lightPoint;
float hash(vec2 v){return fract(sin(dot(v,vec2(127.1,311.7)))*43758.5453);}
float noise(vec2 v){vec2 i=floor(v),f=fract(v);f=f*f*(3.-2.*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1)),f.x),f.y);}
float fbm(vec2 v){return noise(v)*.57+noise(v*2.07+3.1)*.27+noise(v*4.13+7.6)*.11+noise(v*8.21+19.)*.05;}
float cover(float d){return coarse?step(0.,d):smoothstep(-pixel*.55,pixel*.55,d);}
float rect(vec2 q,vec2 a,vec2 b){return cover(min(min(q.x-a.x,b.x-q.x),min(q.y-a.y,b.y-q.y)));}
float segment(vec2 q,vec2 a,vec2 b){vec2 d=b-a;return length(q-a-d*clamp(dot(q-a,d)/max(.00001,dot(d,d)),0.,1.));}
float stroke(vec2 q,vec2 a,vec2 b,float width){return cover(max(width,pixel*(coarse?.52:.30))-segment(q,a,b));}
float circle(vec2 q,vec2 c,float radius){return cover(radius-length(q-c));}
float cross2(vec2 a,vec2 b){return a.x*b.y-a.y*b.x;}
float triangle(vec2 q,vec2 a,vec2 b,vec2 c){float signum=sign(cross2(b-a,c-a));return cover(min(min(signum*cross2(b-a,q-a)/length(b-a),signum*cross2(c-b,q-b)/length(c-b)),signum*cross2(a-c,q-c)/length(a-c)));}
float quad(vec2 q,vec2 a,vec2 b,vec2 c,vec2 d){return max(triangle(q,a,b,c),triangle(q,a,c,d));}
vec3 glow(vec3 base,vec3 add){return 1.-(1.-base)*(1.-clamp(add,0.,1.));}
vec3 ramp(vec3 a,vec3 b,vec3 c,vec3 d,float t){float levels=coarse?6.:13.;float v=floor(clamp(t,0.,1.)*3.*levels+threshold)/levels;return v<1.?mix(a,b,v):v<2.?mix(b,c,v-1.):mix(c,d,v-2.);}
float ellipse(vec2 q,vec2 r){return (1.-length(q/r))*min(r.x,r.y);}
vec4 ellipsoid(vec2 q,vec2 r,float thickness){float k=1.-dot(q/r,q/r);if(k<=0.)return vec4(0,0,1,-1);float z=sqrt(k);return vec4(normalize(vec3(q/(r*r),z/thickness)),z*thickness);}
vec4 unionVolume(vec4 a,vec4 b){float k=clamp(.5+.5*(a.w-b.w)/.035,0.,1.);return vec4(normalize(mix(b.xyz,a.xyz,k)),mix(b.w,a.w,k)+.035*k*(1.-k));}
vec3 drawCloud(vec3 col,vec2 at,vec2 scale,float seed,float depth){
 vec2 q=(p-at)/scale;float d=ellipse(q,vec2(.37,.074));vec4 volume=ellipsoid(q,vec2(.37,.074),.09);
 vec2 c1=vec2(-.19,.045),c2=vec2(-.055,.091),c3=vec2(.115,.068),c4=vec2(.265,.018);
 d=max(d,ellipse(q-c1,vec2(.12,.096)));d=max(d,ellipse(q-c2,vec2(.15,.139)));d=max(d,ellipse(q-c3,vec2(.13,.115)));d=max(d,ellipse(q-c4,vec2(.12,.073)));
 volume=unionVolume(volume,ellipsoid(q-c1,vec2(.12,.096),.11));volume=unionVolume(volume,ellipsoid(q-c2,vec2(.15,.139),.15));volume=unionVolume(volume,ellipsoid(q-c3,vec2(.13,.115),.13));volume=unionVolume(volume,ellipsoid(q-c4,vec2(.12,.073),.09));
 if(coarse){d=ellipse(q,vec2(.48,.10));volume=ellipsoid(q,vec2(.48,.10),.15);for(int i=0;i<5;i++){float n=float(i),x=-.36+n*.18,y=.05+.09*sin(n*1.4+seed),r=.12+.04*sin(n*2.2+seed);vec2 c=vec2(x,y),radius=vec2(r,r*(1.05+.18*sin(n)));d=max(d,ellipse(q-c,radius));volume=unionVolume(volume,ellipsoid(q-c,radius,.14+n*.009));}}
 float mask=cover(d*min(scale.x,scale.y));if(mask<=0.)return col;
 vec3 direction=normalize(vec3(lightPoint-at,.65)),normal=volume.xyz;
 float diffuse=max(0.,dot(normal,direction)),height=clamp(.52+q.y/.24+d*2.5,0.,1.);
 if(coarse)height=clamp(.32+q.y*.93+d*1.2,0.,1.);
 float tone=clamp(height*.82+diffuse*.18-.08*depth,0.,1.);
 vec3 lo=vec3(.34,.49,.63),mid=vec3(.63,.76,.83),hi=vec3(.89,.92,.90),rim=vec3(.99,.97,.84);
 if(scene==2){lo=vec3(.095,.16,.27);mid=vec3(.28,.42,.58);hi=vec3(.53,.66,.78);rim=vec3(.82,.87,.92);}
 if(scene==1||scene==8){lo=vec3(.26,.36,.42);mid=vec3(.48,.59,.62);hi=vec3(.73,.78,.76);rim=vec3(.91,.91,.82);}
 if(scene==5){lo=vec3(.25,.20,.37);mid=vec3(.53,.32,.45);hi=vec3(.89,.52,.39);rim=vec3(1.,.83,.53);}
 if(scene==6){lo=vec3(.34,.41,.58);mid=vec3(.61,.66,.77);hi=vec3(.88,.80,.79);rim=vec3(1.,.92,.75);}
 if(scene==4){lo=vec3(.04,.08,.18);mid=vec3(.14,.23,.36);hi=vec3(.30,.38,.51);rim=vec3(.56,.62,.72);}
 float edge=pow(max(0.,1.-normal.z),3.)*max(0.,dot(normal.xy,direction.xy));
 vec3 material=ramp(lo,mid,hi,rim,tone);material=mix(material,rim,edge*.18);return mix(col,material,mask);
}
float mountain(float x,float seed,float rough){return .10*sin(x*3.1+seed)+.055*sin(x*7.3+seed*2.)+.024*sin(x*16.7+seed*.5)+rough*(fbm(vec2(x*19.,seed))-.5);}
vec3 stone(vec2 q,vec3 dark,vec3 light){
 float rows=coarse?32.:135.,columns=coarse?24.:83.;float row=floor(q.y*rows),brick=floor(q.x*columns+mod(row,2.)*.5);vec2 local=vec2(fract(q.x*columns+mod(row,2.)*.5),fract(q.y*rows));
 float mortar=(1.-smoothstep(.03,.12,min(local.x,local.y)))*.16;
 float face=hash(vec2(brick,row))*.35+.35+.15*noise(q*730.);
 return mix(dark,light,face)*(1.-mortar);
}
vec3 terrainMaterial(vec2 q,float ridge,int layer,bool snow){
 float depth=clamp((ridge-q.y)*3.,0.,1.),slope=(mountain(q.x+.002,float(layer)+1.,0.)-mountain(q.x-.002,float(layer)+1.,0.))/.004;
 float surface=fbm(q*vec2(12.,24.)),detail=noise(q*vec2(190.,120.));
 vec3 a=vec3(.09,.22,.26),b=vec3(.27,.41,.39),c=vec3(.42,.53,.44),d=vec3(.61,.64,.48);
 if(layer==0){a=vec3(.28,.42,.51);b=vec3(.39,.52,.57);c=vec3(.51,.61,.63);d=vec3(.66,.70,.68);}
 if(scene==5){a=vec3(.15,.17,.27);b=vec3(.30,.28,.37);c=vec3(.47,.35,.39);d=vec3(.63,.43,.38);}
 if(scene==2){a=vec3(.045,.11,.18);b=vec3(.12,.23,.31);c=vec3(.24,.35,.41);d=vec3(.40,.46,.47);}
 if(scene==4){a=vec3(.025,.065,.12);b=vec3(.08,.15,.23);c=vec3(.15,.24,.32);d=vec3(.26,.32,.38);}
 if(scene==7||scene==10){a=vec3(.24,.25,.17);b=vec3(.42,.40,.25);c=vec3(.60,.56,.32);d=vec3(.77,.70,.42);}
 float tone=clamp(.35+surface*.28+detail*.035-slope*.08*exp(-depth*5.)-depth*.16,0.,1.);
 vec3 col=ramp(a,b,c,d,tone);
 // Rock strata and small vegetation clusters follow the terrain rather than a screen grid.
 float strata=pow(max(0.,sin(q.y*180.+surface*3.+q.x*6.)),18.);
 col=mix(col,a,strata*.07);
 float speck=step(.72,noise(q*vec2(380.,280.)))*step(.43,surface);col=mix(col,c,speck*.22);
 if(snow){float snowLine=ridge-.07-.04*noise(vec2(q.x*29.,float(layer)));float mask=smoothstep(snowLine-.012,snowLine+.006,q.y);vec3 ice=ramp(vec3(.41,.57,.67),vec3(.64,.74,.78),vec3(.82,.86,.84),vec3(.98,.95,.87),clamp(.55-slope*.08+surface*.2,0.,1.));col=mix(col,ice,mask);}
 return col;
}
void pine(inout vec3 col,vec2 at,float h,float seed,bool snow){
 vec2 q=p-at;float y=q.y/h;if(y<0.||y>1.||abs(q.x)>h*.30)return;
 float width=0.;for(int i=0;i<7;i++){float n=float(i),cy=.14+n*.12;float b=(1.-cy)*h*.30;float part=(1.-abs((y-cy)/.15));width=max(width,b*max(0.,part));}
 width*=.94+.06*sin(q.y*230.+seed);float mask=cover(width-abs(q.x));
 float tone=clamp(.5-q.x/h*.9+noise(q*140.+seed)*.17,0.,1.);
 vec3 ink=ramp(vec3(.025,.10,.13),vec3(.08,.20,.22),vec3(.20,.32,.28),vec3(.37,.43,.32),tone);
 if(snow){float branch=step(.66,fract(y*8.));ink=mix(ink,vec3(.80,.87,.86),branch*.85);}
 col=mix(col,ink,mask);col=mix(col,vec3(.13,.18,.16),rect(q,vec2(-.004,0),vec2(.004,h*.4)));
}
void cabin(inout vec3 col,vec2 at,float scale,bool raised,bool snow){
 vec2 q=(p-at)/scale;float base=raised?.09:0.;
 // Foundation blocks and a front facade with layered masonry.
 col=mix(col,stone(q,vec3(.16,.22,.25),vec3(.43,.47,.41)),rect(q,vec2(-.25,base-.045),vec2(.21,base+.025)));
 col=mix(col,stone(q,vec3(.31,.32,.29),vec3(.70,.65,.51)),rect(q,vec2(-.22,base+.02),vec2(.14,base+.19)));

 float roof=cover(min(.27-abs(q.x+.01),min(q.y-(base+.19),(base+.30-abs(q.x+.015)*.39)-q.y)));
 float shingles=.25+.11*noise(q*210.)+.14*step(.13,fract(q.y*145.));
 vec3 roofColor=mix(vec3(.10,.18,.24),vec3(.33,.43,.45),shingles);
 if(snow)roofColor=mix(roofColor,vec3(.88,.92,.89),step(base+.22,q.y));col=mix(col,roofColor,roof);
 col=mix(col,vec3(.09,.15,.19),stroke(q,vec2(-.28,base+.19),vec2(.26,base+.19),.008));
 col=mix(col,vec3(.13,.19,.20),rect(q,vec2(-.045,base+.02),vec2(.025,base+.14)));
 col=mix(col,vec3(.70,.51,.29),rect(q,vec2(-.035,base+.03),vec2(.015,base+.13)));
 for(int i=0;i<2;i++){float x=i==0?-.16:.065;col=mix(col,vec3(.12,.17,.18),rect(q,vec2(x-.007,base+.063),vec2(x+.065,base+.154)));col=mix(col,vec3(.98,.69,.29),rect(q,vec2(x,base+.07),vec2(x+.055,base+.145)));if(!coarse)col=mix(col,vec3(.32,.24,.16),stroke(q,vec2(x+.028,base+.068),vec2(x+.028,base+.147),.003)+stroke(q,vec2(x,base+.105),vec2(x+.056,base+.105),.003));}
 col=mix(col,vec3(.25,.30,.29),rect(q,vec2(.14,base+.265),vec2(.185,base+.37)));
 col=mix(col,vec3(.48,.45,.36),rect(q,vec2(.13,base+.365),vec2(.193,base+.38)));
 // Timber deck, guardrail and non-repeating stone steps meet the same ground plane.
 col=mix(col,vec3(.40,.39,.31),stroke(q,vec2(-.31,base+.025),vec2(.29,base+.025),.006));
 for(int i=0;i<7;i++){float x=-.29+float(i)*.095;col=mix(col,vec3(.43,.42,.32),stroke(q,vec2(x,base+.023),vec2(x,base+.09),.003));}
 col=mix(col,vec3(.58,.54,.40),stroke(q,vec2(-.30,base+.09),vec2(.29,base+.09),.003));
 for(int i=0;i<5;i++){float n=float(i);vec2 a=vec2(-.045+n*.018,base-.013-n*.016);col=mix(col,vec3(.40,.45,.41),rect(q,a,a+vec2(.13+n*.009,.012)));}
 if(snow&&!coarse){for(int i=0;i<8;i++){float x=-.23+float(i)*.063;float h=.010+.015*hash(vec2(float(i),21.));col=mix(col,vec3(.75,.85,.87),stroke(q,vec2(x,base+.19),vec2(x,base+.19-h),.001));}col=mix(col,vec3(.78,.77,.65),rect(q,vec2(.32,0),vec2(.334,.31)));for(int i=0;i<6;i++){float y=float(i)*.05;col=mix(col,vec3(.77,.27,.16),rect(q,vec2(.32,y),vec2(.334,y+.022)));}}
 if(raised){col=mix(col,vec3(.20,.28,.25),stroke(q,vec2(-.20,0),vec2(-.20,.10),.009)+stroke(q,vec2(.18,0),vec2(.18,.10),.009)+stroke(q,vec2(-.20,.01),vec2(.18,.09),.004));}
 // Instrument mast, anemometer cups, screen louvers, cables and mounting plates.
 vec2 m=vec2(-.12,base+.30);col=mix(col,vec3(.60,.66,.60),stroke(q,m,m+vec2(0,.30),.003));
 col=mix(col,vec3(.22,.29,.31),stroke(q,m+vec2(0,.02),m+vec2(.075,-.10),.0015));
 col=mix(col,vec3(.72,.66,.45),stroke(q,m+vec2(-.07,.25),m+vec2(.07,.25),.002));
 for(int i=0;i<3;i++){float a=float(i)*PI*2./3.+clock*.25;vec2 end=m+vec2(sin(a)*.067,.25+cos(a)*.015);col=mix(col,vec3(.17,.24,.27),circle(q,end,.009));}
 col=mix(col,vec3(.77,.79,.69),rect(q,m+vec2(-.031,.05),m+vec2(.03,.115)));
 for(int i=0;i<5;i++){if(coarse&&i%2==1)continue;float y=m.y+.058+float(i)*.011;col=mix(col,vec3(.34,.44,.45),stroke(q,vec2(m.x-.032,y),vec2(m.x+.031,y),.0017));}
}
void islandStation(inout vec3 col,vec2 at){
 vec2 q=p-at,back=vec2(.10,.055);vec2 a=vec2(-.27,0),b=vec2(.18,0),c=vec2(.18,.20),d=vec2(-.27,.20);
 float shadow=exp(-pow((q.x-.02)/.33,2.)-pow((q.y+.02)/.025,2.));col*=1.-shadow*.35;
 col=mix(col,stone(q,vec3(.20,.27,.29),vec3(.53,.53,.43)),rect(q,vec2(-.30,-.04),vec2(.22,.02)));
 col=mix(col,vec3(.80,.81,.71),quad(q,a,b,c,d));
 col=mix(col,vec3(.67,.72,.65),rect(q,vec2(-.285,.185),vec2(.20,.217)));
 col=mix(col,vec3(.24,.40,.43),rect(q,vec2(-.055,.01),vec2(.012,.143)));
 for(int i=0;i<2;i++){float x=i==0?-.208:.075;col=mix(col,vec3(.15,.31,.37),rect(q,vec2(x,.065),vec2(x+.062,.15)));col=mix(col,vec3(.50,.70,.72),rect(q,vec2(x+.006,.073),vec2(x+.056,.143)));col=mix(col,vec3(.20,.43,.46),rect(q,vec2(x-.027,.062),vec2(x-.006,.153))+rect(q,vec2(x+.067,.062),vec2(x+.087,.153)));if(!coarse)for(int j=0;j<5;j++){float y=.073+float(j)*.015;col=mix(col,vec3(.12,.32,.37),stroke(q,vec2(x-.026,y),vec2(x-.007,y),.001)+stroke(q,vec2(x+.068,y),vec2(x+.086,y),.001));}}
 vec2 dome=vec2(-.15,.254);vec2 v=(q-dome)/vec2(.067,.064);float mask=cover((1.-length(v))*.064)*step(dome.y-.012,q.y);float z=sqrt(max(0.,1.-dot(v,v)));vec3 white=ramp(vec3(.40,.53,.58),vec3(.64,.73,.73),vec3(.84,.87,.78),vec3(.97,.96,.85),clamp(.5-v.x*.18+z*.24,0.,1.));col=mix(col,white,mask);
 col=mix(col,vec3(.38,.48,.47),stroke(q,vec2(.095,.217),vec2(.095,.51),.0025));col=mix(col,vec3(.20,.31,.34),stroke(q,vec2(.025,.46),vec2(.165,.46),.002));
 for(int i=0;i<3;i++){float a=float(i)*PI*2./3.+clock*.22;col=mix(col,vec3(.22,.32,.34),circle(q,vec2(.095+sin(a)*.06,.46+cos(a)*.012),.008));}
 col=mix(col,vec3(.76,.78,.65),rect(q,vec2(.058,.29),vec2(.13,.347)));for(int i=0;i<4;i++){float y=.30+float(i)*.012;col=mix(col,vec3(.36,.48,.48),stroke(q,vec2(.056,y),vec2(.132,y),.0016));}
 for(int i=0;i<5;i++){float n=float(i);vec2 a=vec2(-.055+n*.015,-.018-n*.014);col=mix(col,vec3(.50,.55,.47),rect(q,a,a+vec2(.12,.010)));}
 if(!coarse){col=mix(col,vec3(.89,.86,.69),stroke(q,vec2(-.269,.01),vec2(-.269,.19),.0015));col=mix(col,vec3(.31,.45,.47),stroke(q,vec2(.095,.36),vec2(.20,.23),.001));}
}
void stormBunker(inout vec3 col,vec2 at){
 vec2 q=p-at,back=vec2(.11,.045);vec2 a=vec2(-.29,0),b=vec2(.18,0),c=vec2(.18,.15),d=vec2(-.29,.15);
 float shadow=exp(-pow(q.x/.34,2.)-pow((q.y+.02)/.022,2.));col*=1.-shadow*.48;
 col=mix(col,stone(q,vec3(.10,.18,.24),vec3(.37,.42,.41)),rect(q,vec2(-.32,-.04),vec2(.22,.015)));
 col=mix(col,vec3(.40,.47,.46),quad(q,a,b,c,d));
 col=mix(col,vec3(.24,.32,.36),rect(q,vec2(-.315,.15),vec2(.205,.178)));
 col=mix(col,vec3(.62,.38,.26),stroke(q,d-vec2(.03,.006),c+vec2(.03,.006),.008));
 col=mix(col,vec3(.18,.27,.30),rect(q,vec2(-.04,.008),vec2(.028,.12)));col=mix(col,vec3(.64,.65,.51),circle(q,vec2(.014,.065),.004));
 for(int i=0;i<2;i++){float x=i==0?-.235:.075;col=mix(col,vec3(.10,.18,.22),rect(q,vec2(x,.066),vec2(x+.085,.121)));col=mix(col,vec3(.92,.67,.29),rect(q,vec2(x+.006,.073),vec2(x+.078,.114)));if(!coarse)col=mix(col,vec3(.30,.29,.23),stroke(q,vec2(x+.043,.071),vec2(x+.043,.116),.0018));}
 for(int i=0;i<4;i++){float y=.035+float(i)*.025;col=mix(col,vec3(.08,.19,.26),stroke(q,vec2(.20,y),vec2(.27,y+.025),.002));}
 vec2 base=vec2(-.18,.16);col=mix(col,vec3(.63,.66,.59),stroke(q,base+vec2(-.025,0),base+vec2(-.008,.43),.0025)+stroke(q,base+vec2(.025,0),base+vec2(.008,.43),.0025));
 for(int i=0;i<5;i++){float n=float(i),y=n*.08,w=.025-y*.04;col=mix(col,vec3(.39,.50,.53),stroke(q,base+vec2(-w,y),base+vec2(w-.003,y+.075),.0015));}
 col=mix(col,vec3(.77,.73,.52),stroke(q,base+vec2(-.075,.39),base+vec2(.075,.39),.002));for(int i=0;i<3;i++){float a=float(i)*PI*2./3.+clock*.28;col=mix(col,vec3(.15,.25,.32),circle(q,base+vec2(sin(a)*.066,.39+cos(a)*.014),.009));}
 col=mix(col,vec3(.67,.72,.66),rect(q,base+vec2(.045,.10),base+vec2(.105,.17)));for(int i=0;i<4;i++){float y=.11+float(i)*.015;col=mix(col,vec3(.26,.39,.44),stroke(q,base+vec2(.044,y),base+vec2(.106,y),.0015));}
 if(!coarse){col=mix(col,vec3(.55,.59,.54),stroke(q,vec2(.04,.20),vec2(.10,.39),.001)+stroke(q,vec2(.10,.39),vec2(.19,.20),.001));for(int i=0;i<5;i++){float x=-.24+float(i)*.09;col=mix(col,vec3(.39,.44,.42),stroke(q,vec2(x,.16),vec2(x+.09,.20),.001));}}
}
void valleyShelter(inout vec3 col,vec2 at){
 vec2 q=p-at,back=vec2(.09,.05);float shadow=exp(-pow(q.x/.34,2.)-pow((q.y-.01)/.025,2.));col*=1.-shadow*.35;
 col=mix(col,stone(q,vec3(.18,.27,.28),vec3(.52,.54,.44)),rect(q,vec2(-.28,-.025),vec2(.23,.045)));
 col=mix(col,vec3(.66,.62,.45),rect(q,vec2(-.25,.035),vec2(.21,.20)));
 col=mix(col,vec3(.13,.28,.35),rect(q,vec2(-.215,.085),vec2(.13,.173)));col=mix(col,vec3(.44,.61,.63),rect(q,vec2(-.205,.094),vec2(.12,.163)));
 for(int i=0;i<4;i++){float x=-.20+float(i)*.105;col=mix(col,vec3(.22,.34,.36),stroke(q,vec2(x,.092),vec2(x,.165),.003));}
 col=mix(col,vec3(.21,.32,.32),rect(q,vec2(.15,.045),vec2(.20,.175)));
 col=mix(col,vec3(.36,.46,.37),rect(q,vec2(-.285,.20),vec2(.245,.24)));col=mix(col,vec3(.77,.71,.48),rect(q,vec2(-.285,.189),vec2(.245,.207)));
 col=mix(col,vec3(.30,.44,.44),rect(q,vec2(-.25,.005),vec2(.24,.03)));for(int i=0;i<8;i++){float x=-.23+float(i)*.065;col=mix(col,vec3(.51,.54,.42),stroke(q,vec2(x,.006),vec2(x,.074),.002));}col=mix(col,vec3(.63,.61,.43),stroke(q,vec2(-.24,.074),vec2(.24,.074),.002));
 col=mix(col,vec3(.62,.70,.66),stroke(q,vec2(.08,.25),vec2(.08,.51),.0025));col=mix(col,vec3(.18,.31,.35),stroke(q,vec2(.015,.46),vec2(.145,.46),.002));
 for(int i=0;i<3;i++){float a=float(i)*PI*2./3.+clock*.17;col=mix(col,vec3(.22,.32,.34),circle(q,vec2(.08+sin(a)*.06,.46+cos(a)*.012),.008));}
 for(int i=0;i<4;i++){float n=float(i);vec2 a=vec2(.13+n*.018,-.006-n*.012);col=mix(col,vec3(.47,.51,.42),rect(q,a,a+vec2(.11,.009)));}
 if(!coarse){for(int i=0;i<12;i++){float x=-.26+float(i)*.045;col=mix(col,vec3(.51,.58,.39),stroke(q,vec2(x,.219),vec2(x+.012,.235),.001));}col=mix(col,vec3(.73,.76,.63),rect(q,vec2(-.12,.24),vec2(-.05,.28)));}
}
void forestTower(inout vec3 col,vec2 at){
 vec2 q=p-at,back=vec2(.075,.035);float shadow=exp(-pow(q.x/.27,2.)-pow(q.y/.025,2.));col*=1.-shadow*.35;
 vec3 dark=vec3(.16,.26,.25),timber=vec3(.40,.40,.29),edge=vec3(.58,.51,.34);
 col=mix(col,dark,stroke(q,vec2(-.12,0),vec2(-.12,.30),.011)+stroke(q,vec2(.11,0),vec2(.11,.30),.011));
 col=mix(col,timber,stroke(q,vec2(-.12,.035),vec2(.11,.23),.004)+stroke(q,vec2(.11,.035),vec2(-.12,.23),.004));
 col=mix(col,timber,rect(q,vec2(-.17,.235),vec2(.16,.255)));

 col=mix(col,timber,rect(q,vec2(-.13,.27),vec2(.11,.43)));col=mix(col,vec3(.86,.66,.32),rect(q,vec2(-.117,.304),vec2(.095,.415)));
 for(int i=0;i<4;i++){float x=-.12+float(i)*.073;col=mix(col,dark,stroke(q,vec2(x,.298),vec2(x,.42),.003));}
 float roof=triangle(q,vec2(-.18,.43),vec2(.15,.43),vec2(-.015,.535));col=mix(col,vec3(.18,.29,.30),roof);
 col=mix(col,edge,stroke(q,vec2(-.18,.43),vec2(.15,.43),.004));
 for(int i=0;i<6;i++){float x=-.17+float(i)*.068;col=mix(col,timber,stroke(q,vec2(x,.24),vec2(x,.31),.002));}col=mix(col,edge,stroke(q,vec2(-.18,.31),vec2(.18,.31),.003));
 col=mix(col,dark,stroke(q,vec2(-.25,0),vec2(-.15,.24),.004));for(int i=0;i<9;i++){float t=float(i)/8.;vec2 a=mix(vec2(-.25,0),vec2(-.15,.24),t);col=mix(col,edge,stroke(q,a,a+vec2(.072,0),.002));}
 col=mix(col,vec3(.33,.32,.23),stroke(q,vec2(.15,.41),vec2(.225,.41),.002));col=mix(col,vec3(.96,.72,.33),rect(q,vec2(.205,.35),vec2(.235,.395)));
 if(!coarse){col=mix(col,vec3(.43,.47,.34),stroke(q,vec2(.21,.40),vec2(.22,.42),.001));for(int i=0;i<7;i++){float y=.28+float(i)*.021;col=mix(col,dark,stroke(q,vec2(-.129,y),vec2(-.117,y),.001));}}
}
void turbine(inout vec3 col,vec2 at,float h,float phase){
 vec2 hub=at+vec2(0,h);float taper=.004+.006*clamp((hub.y-p.y)/h,0.,1.);
 col=mix(col,vec3(.70,.75,.70),stroke(p,at,hub,taper));col=mix(col,vec3(.32,.41,.43),stroke(p,at+vec2(.005,0),hub+vec2(.002,0),.002));
 for(int i=0;i<3;i++){float a=phase+float(i)*PI*2./3.;vec2 direction=vec2(sin(a),cos(a)),perp=vec2(direction.y,-direction.x);float length=h*.34;vec2 q=p-hub;float along=dot(q,direction),side=dot(q,perp),width=.008*(1.-clamp(along/length,0.,1.));float mask=cover(min(min(along,length-along),width-abs(side)));col=mix(col,vec3(.88,.87,.74),mask);}
 col=mix(col,vec3(.79,.76,.62),circle(p,hub,.012));
}
vec3 water(vec2 q,float horizon,float sunX){
 float depth=clamp((horizon-q.y)/horizon,0.,1.);vec3 near=scene==5?vec3(.09,.14,.24):vec3(.07,.20,.29),far=scene==5?vec3(.47,.34,.38):vec3(.37,.50,.57);
 vec3 col=ramp(near,mix(near,far,.30),mix(near,far,.70),far,1.-sqrt(depth));
 float rows=sqrt(depth)*(coarse?15.:48.),row=floor(rows),columns=mix(95.,17.,depth),x=q.x+.003*sin(clock*.38+row*1.3);
 float tile=floor(x*columns+hash(vec2(row,7.))*8.),seed=hash(vec2(tile,row));float local=fract(x*columns+hash(vec2(row,7.))*8.);
 float crest=1.-smoothstep(.03,.13,abs(fract(rows)-(.45+.10*sin(clock*.42+row*1.7))));float fragment=step(.12,local)*(1.-step(.44+seed*.35,local));
 float shimmer=.66+.34*sin(clock*(.5+seed*.2)+row*1.5+seed*20.);float ripple=crest*fragment;
 col=mix(col,vec3(.45,.59,.64),ripple*(.10+depth*.13));
 float reflection=exp(-pow((q.x-sunX)/(.025+depth*.17),2.))*ripple*shimmer*step(.24,seed);
 vec3 light=scene==5?vec3(.99,.64,.29):scene==6?vec3(.93,.78,.53):vec3(.64,.75,.76);col=mix(col,light,reflection*(scene==5?.7:.20));
 float foam=pow(max(0.,sin(q.y*170.+q.x*3.+sin(q.x*22.-clock*.3))),18.)*step(.85,noise(q*vec2(40.,170.)));return mix(col,vec3(.67,.76,.76),foam*.20);
}
void radioDish(inout vec3 col,vec2 at){
 vec2 q=p-at;float a=-.34;vec2 r=mat2(cos(a),-sin(a),sin(a),cos(a))*q;
 float curve=-.115+r.x*r.x*3.8,mask=cover(min(min(r.y-curve,.065-r.y),.21-abs(r.x)));
 float ribs=pow(abs(sin(atan(r.y+.20,r.x)*18.)),24.);
 vec3 silver=ramp(vec3(.20,.30,.38),vec3(.39,.51,.58),vec3(.64,.72,.73),vec3(.85,.85,.73),clamp(.55-r.x*.6+r.y*.4-ribs*.11,0.,1.));col=mix(col,silver,mask);
 col=mix(col,vec3(.13,.23,.30),stroke(r,vec2(-.21,.065),vec2(.21,.065),.005));
 for(int i=0;i<7;i++){float x=-.18+float(i)*.06;col=mix(col,vec3(.27,.38,.43),stroke(r,vec2(x,-.115+x*x*3.8),vec2(0,.20),.0015));}
 col=mix(col,vec3(.57,.65,.66),stroke(r,vec2(-.20,.06),vec2(0,.20),.003)+stroke(r,vec2(.20,.06),vec2(0,.20),.003));
 col=mix(col,vec3(.79,.24,.16),circle(r,vec2(0,.20),.007));
 col=mix(col,vec3(.24,.33,.37),stroke(q,vec2(0,-.11),vec2(0,-.25),.022)+stroke(q,vec2(-.12,-.25),vec2(.12,-.25),.012));
}
void main(){
 cell=floor(gl_FragCoord.xy/screen*grid);uv=(cell+.5)/grid;aspect=screen.x/screen.y;p=vec2(uv.x*aspect,uv.y);pixel=1./grid.y;
 threshold=coarse?.5:(float(BAYER[int(mod(cell.y,4.))*4+int(mod(cell.x,4.))])+.5)/16.;
 bool storm=scene==2||weatherScene==2,snow=scene==3||weatherScene==3,night=scene==4,warm=scene==5||scene==6,misty=scene==9;
 vec3 a=vec3(.15,.36,.61),b=vec3(.28,.53,.73),c=vec3(.58,.74,.81),d=vec3(.87,.85,.72);
 if(storm){a=vec3(.035,.07,.15);b=vec3(.09,.18,.32);c=vec3(.29,.40,.53);d=vec3(.64,.58,.53);}
 if(scene==1||scene==8){a=vec3(.23,.32,.40);b=vec3(.37,.48,.54);c=vec3(.58,.66,.66);d=vec3(.80,.81,.73);}
 if(snow){a=vec3(.20,.38,.61);b=vec3(.42,.59,.76);c=vec3(.70,.77,.84);d=vec3(.95,.84,.75);}
 if(night){a=vec3(.015,.025,.08);b=vec3(.04,.08,.18);c=vec3(.10,.18,.32);d=vec3(.27,.34,.43);}
 if(scene==5){a=vec3(.13,.11,.29);b=vec3(.35,.20,.39);c=vec3(.75,.37,.36);d=vec3(.99,.70,.40);}
 if(scene==6){a=vec3(.17,.31,.51);b=vec3(.38,.51,.68);c=vec3(.73,.70,.74);d=vec3(.99,.86,.67);}
 if(misty){a=vec3(.34,.44,.46);b=vec3(.47,.58,.56);c=vec3(.66,.73,.67);d=vec3(.83,.84,.74);}
 if(rain>0.&& !night){float wet=min(.8,rain);a=mix(a,vec3(.16,.24,.34),wet);b=mix(b,vec3(.30,.39,.47),wet);c=mix(c,vec3(.48,.57,.61),wet);d=mix(d,vec3(.65,.70,.69),wet);}
 vec3 col=ramp(a,b,c,d,1.-uv.y*.92);
 lightPoint=vec2(aspect*(scene==6?.24:.74),warm?.43:.82);
 float halo=exp(-dot(p-lightPoint,p-lightPoint)/(warm?.15:.09));
 if(warm||scene==0||scene==7||scene==10){col=glow(col,vec3(1.,.64,.26)*halo*(warm?.20:.08));float radius=warm?.068:.047;float r=length(p-lightPoint);float tone=clamp(.66+(p.y-lightPoint.y)*2.+noise((p-lightPoint)*115.)*.05,0.,1.);vec3 sun=ramp(vec3(.97,.43,.10),vec3(1.,.66,.22),vec3(1.,.86,.49),vec3(1.,.98,.81),tone);col=mix(col,sun,cover(radius-r));}
 if(night){
  float band=exp(-pow((uv.x-.18-uv.y*.48)/.16,2.));col=glow(col,vec3(.19,.16,.29)*band*(.25+.18*fbm(uv*15.)));
  for(int layer=0;layer<3;layer++){float n=float(layer);vec2 field=vec2(78.-n*21.,41.-n*10.),tile=floor(uv*field),local=fract(uv*field)-.5;float seed=hash(tile+n*41.);if(seed>.95-n*.008){float size=.060+n*.020,star=1.-smoothstep(size*.4,size,length(local));float twinkle=.60+.25*sin(clock*(.3+seed*.8)+seed*31.)+.15*sin(clock*1.7+seed*15.);vec3 color=seed>.985?vec3(.99,.78,.41):vec3(.60,.76,.97);col=glow(col,color*star*twinkle);}}
  vec2 mq=(p-vec2(aspect*.78,.78))/.080;float r=length(mq);if(r<1.){float z=sqrt(max(0.,1.-dot(mq,mq))),terminator=-cos(moonPhase*PI*2.)*sqrt(max(0.,1.-mq.y*mq.y));bool lit=moonPhase<.5?mq.x>terminator:mq.x<-terminator;float maria=smoothstep(.40,.72,fbm(mq*3.+8.))*.15;float tone=.52+.32*max(0.,dot(normalize(vec3(mq,z)),normalize(vec3(-.4,.3,.8))))-maria;vec3 lunar=ramp(vec3(.36,.43,.55),vec3(.62,.68,.72),vec3(.83,.83,.75),vec3(.99,.96,.83),tone);col=mix(vec3(.08,.15,.24),lunar,lit?1.:.12);}
 }
 // Composition is unique to each environment; cloud bodies stay analytic in both modes.
 float drift=clock*.002;
 if(storm){col=drawCloud(col,vec2(aspect*.17+sin(drift)*.03,.82),vec2(aspect*.88,.92),2.,.35);col=drawCloud(col,vec2(aspect*.86,.68),vec2(aspect*.72,1.08),5.,.15);}
 else if(scene==1||scene==8||snow||rain>.01){for(int i=0;i<3;i++){float n=float(i);col=drawCloud(col,vec2(aspect*(.13+n*.40)+sin(drift+n)*.03,.71+n*.08),vec2(aspect*.70,.65),n+2.,.15+n*.12);}}
 else if(scene==5||scene==6){col=drawCloud(col,vec2(aspect*.10+sin(drift)*.03,.75),vec2(aspect*.72,.78),2.,.18);col=drawCloud(col,vec2(aspect*1.04,.60),vec2(aspect*.68,.65),5.,.25);}
 else if(scene==10){for(int i=0;i<3;i++){float n=float(i);col=drawCloud(col,vec2(aspect*(-.08+n*.49)+sin(drift+n)*.03,.63+n*.13),vec2(aspect*.66,.26),n+2.,.20);}}
 else if(scene==0||scene==7){col=drawCloud(col,vec2(aspect*.24+sin(drift)*.025,.75),vec2(aspect*.61,.72),2.,.0);col=drawCloud(col,vec2(aspect*.87,.65),vec2(aspect*.52,.57),5.,.12);}
 else if(night)col=drawCloud(col,vec2(aspect*.08,.49),vec2(aspect*.48,.22),4.,.7);
 // Seven explicitly lit depth planes, with coherent rock, snow and vegetation materials.
 for(int layer=0;layer<5;layer++){float n=float(layer),ridge=.39-n*.045+mountain(p.x,n+1.,.12)*(.50+n*.10);if(snow)ridge+=.12*abs(sin(p.x*3.5+n));if(p.y<ridge){vec3 land=terrainMaterial(p,ridge,layer,snow);float haze=(4.-n)*.105;col=mix(land,mix(c,d,.5),haze);}}
 bool coastal=scene==0||storm||scene==5;
 if(coastal){float horizon=.265;if(p.y<horizon)col=water(uv,horizon,lightPoint.x/aspect);float edge=.37+.09*sin(p.y*7.)+.025*noise(vec2(p.y*73.,3.));float top=.18+mountain(p.x,8.,.05)*.15;if(uv.x<edge&&p.y<top){col=stone(p*vec2(1.,.8),vec3(.045,.10,.15),vec3(.40,.43,.35));float moss=step(.58,fbm(p*27.));col=mix(col,vec3(.25,.35,.23),moss*.36);float lip=exp(-pow((top-p.y)/.008,2.));col=mix(col,vec3(.42,.45,.30),lip*.65);float fissure=pow(max(0.,sin(p.x*167.+p.y*10.+noise(p*30.)*3.)),18.);col*=1.-fissure*.25;}}
 else{float foreground=.13+mountain(p.x,7.,.05)*.20;if(p.y<foreground){col=terrainMaterial(p,foreground,5,snow);float stems=step(.88,hash(vec2(floor(p.x*390.),0.)))*step(.5,fract(p.y*83.+p.x*8.));col=mix(col,scene==10?vec3(.66,.61,.33):vec3(.25,.36,.24),stems*.35);}}
 // Fine-only field details stay tied to each environment and use the same
 // analytic pixel primitives as the architecture, never image textures.
 if(!coarse){
  if(coastal){
   for(int i=0;i<8;i++){float n=float(i),x=aspect*(.04+n*.038),y=.12+mountain(x,8.,.05)*.11;vec2 q=p-vec2(x,y);float size=.006+hash(vec2(n,5.))*.005;col=mix(col,vec3(.41,.45,.38),cover(ellipse(q,vec2(size*1.8,size))));}
   if(scene==5){vec2 q=p-vec2(aspect*.40,.18);col=mix(col,vec3(.30,.28,.27),rect(q,vec2(-.055,.02),vec2(.055,.031))+rect(q,vec2(-.047,-.012),vec2(-.040,.02))+rect(q,vec2(.040,-.012),vec2(.047,.02)));}
   if(storm||scene==0){vec2 q=p-vec2(aspect*.08,.19);col=mix(col,vec3(.57,.61,.56),rect(q,vec2(-.010,0),vec2(.010,.052))+stroke(q,vec2(0,0),vec2(0,-.035),.0014));col=mix(col,vec3(.17,.28,.31),stroke(q,vec2(-.015,.052),vec2(.015,.052),.002));}
  }else if(!snow&&scene!=7){
   for(int i=0;i<18;i++){float n=float(i),x=aspect*(n+.5)/18.,y=.12+mountain(x,7.,.05)*.12;vec2 q=p-vec2(x,y);float h=.012+hash(vec2(n,8.))*.018;col=mix(col,vec3(.29,.40,.27),stroke(q,vec2(0,0),vec2(.002,h),.0008)+stroke(q,vec2(.001,h*.5),vec2(-.006,h*.7),.0008));}
  }
 }
 // Foreground silhouettes carry individual branch/shadow structure, not noise outlines.
 if(snow||night||misty||scene==1||scene==6){for(int i=0;i<15;i++){float n=float(i),x=aspect*n/14.,seed=hash(vec2(n,7.));float h=.09+seed*.16,base=.10+mountain(x,7.,.05)*.12;pine(col,vec2(x,base),h,n,snow);}}
 if(snow)cabin(col,vec2(aspect*.68,.16),.77,false,true);
 if(scene==0)islandStation(col,vec2(aspect*.23,.15));
 if(storm)stormBunker(col,vec2(aspect*.23,.15));
 if(scene==6)valleyShelter(col,vec2(aspect*.71,.14));
 if(misty)forestTower(col,vec2(aspect*.72,.13));
 if(scene==1){
  vec2 q=p-vec2(aspect*.25,.15);float contact=exp(-pow(q.x/.34,2.)-pow((q.y+.015)/.024,2.));col*=1.-contact*.42;
  float front=rect(q,vec2(-.28,.015),vec2(.28,.215));float roof=triangle(q,vec2(-.29,.215),vec2(.29,.215),vec2(0,.40));
  col=mix(col,stone(q,vec3(.13,.22,.25),vec3(.47,.49,.41)),rect(q,vec2(-.305,-.032),vec2(.305,.025)));
  vec3 glass=ramp(vec3(.12,.25,.29),vec3(.26,.42,.43),vec3(.48,.60,.54),vec3(.77,.75,.54),clamp(.42+q.y+noise(q*43.)*.06,0.,1.));float warm=exp(-pow(q.x/.24,2.)-pow((q.y-.10)/.18,2.));glass=mix(glass,vec3(.80,.62,.32),warm*.36);col=mix(col,glass,front);col=mix(col,glass*1.08,roof);
  // Interior plants sit behind a regular front glazing grid.
  for(int i=0;i<6;i++){float x=-.235+float(i)*.094;float h=.055+.025*hash(vec2(float(i),3.));col=mix(col,vec3(.09,.26,.22),stroke(q,vec2(x,.03),vec2(x,.03+h),.0018));col=mix(col,vec3(.15,.34,.25),stroke(q,vec2(x,.06),vec2(x-.024,.08),.003)+stroke(q,vec2(x,.075),vec2(x+.025,.10),.003));}
  vec3 frame=vec3(.10,.23,.27),edge=vec3(.55,.64,.59);
  for(int i=0;i<9;i++){float x=-.28+float(i)*.07;col=mix(col,frame,stroke(q,vec2(x,.02),vec2(x,.215),.0025));float top=.40-abs(x)*(.185/.29);col=mix(col,edge*.78,stroke(q,vec2(x,.215),vec2(x,top),.002));}
  for(int i=1;i<4;i++){float y=.025+float(i)*.047;col=mix(col,frame,stroke(q,vec2(-.28,y),vec2(.28,y),.002));}
  col=mix(col,edge,stroke(q,vec2(-.30,.215),vec2(.30,.215),.003)+stroke(q,vec2(-.29,.215),vec2(0,.40),.003)+stroke(q,vec2(0,.40),vec2(.29,.215),.003));
  col=mix(col,frame,rect(q,vec2(-.054,.02),vec2(.054,.18)));col=mix(col,vec3(.86,.67,.34),rect(q,vec2(-.043,.03),vec2(.043,.166)));col=mix(col,frame,stroke(q,vec2(0,.03),vec2(0,.166),.002));
  for(int i=0;i<4;i++){float n=float(i);vec2 a=vec2(-.066-n*.008,-.012-n*.011);col=mix(col,vec3(.40,.46,.40),rect(q,a,a+vec2(.132+n*.016,.009)));}
  if(!coarse){
   // Two front-facing propagation trays and a fine ridge vent distinguish the greenhouse.
   for(int i=0;i<2;i++){float x=i==0?-.20:.20;col=mix(col,vec3(.43,.31,.23),rect(q,vec2(x-.047,.027),vec2(x+.047,.046)));col=mix(col,vec3(.24,.39,.26),stroke(q,vec2(x-.04,.049),vec2(x+.04,.049),.003));}
   col=mix(col,vec3(.65,.70,.62),stroke(q,vec2(-.047,.374),vec2(.047,.374),.0013));
  }
 }
 if(night)radioDish(col,vec2(aspect*.29,.32));
 if(scene==5){
  vec2 q=p-vec2(aspect*.23,.16);float contact=exp(-pow(q.x/.30,2.)-pow((q.y+.025)/.022,2.));col*=1.-contact*.48;
  col=mix(col,stone(q,vec3(.20,.25,.28),vec3(.58,.49,.36)),rect(q,vec2(-.255,-.035),vec2(.255,.018)));
  // Three frontal stone piers, two open bays, one centred pitched roof.
  for(int i=0;i<3;i++){float x=-.205+float(i)*.205;vec3 face=stone(q,vec3(.30,.32,.31),vec3(.70,.56,.37));col=mix(col,face,rect(q,vec2(x-.018,.018),vec2(x+.018,.252)));col=mix(col,vec3(.62,.52,.36),rect(q,vec2(x-.028,.015),vec2(x+.028,.037))+rect(q,vec2(x-.027,.235),vec2(x+.027,.26)));}
  col=mix(col,vec3(.44,.41,.34),rect(q,vec2(-.23,.25),vec2(.23,.276)));
  float tiles=.53+.11*step(.15,fract(q.y*(coarse?50.:155.)))+.05*noise(q*220.);float roof=triangle(q,vec2(-.285,.277),vec2(.285,.277),vec2(0,.405));col=mix(col,mix(vec3(.10,.19,.27),vec3(.39,.43,.43),tiles),roof);col=mix(col,vec3(.47,.44,.34),stroke(q,vec2(-.29,.275),vec2(.29,.275),.004));
  col=mix(col,vec3(.68,.51,.29),stroke(q,vec2(0,.405),vec2(0,.46),.002));
  for(int i=0;i<5;i++){float n=float(i);vec2 a=vec2(-.075-n*.007,-.015-n*.012);col=mix(col,vec3(.45,.43,.35),rect(q,a,a+vec2(.15+n*.014,.009)));}
 }
 if(scene==7){for(int i=0;i<4;i++){float n=float(i);vec2 q=p-vec2(aspect*(.20+n*.18),.19-n*.018);vec2 r=vec2(q.x,q.y+q.x*.18);float panel=rect(r,vec2(0,0),vec2(.22,.10));vec3 blue=ramp(vec3(.045,.14,.23),vec3(.11,.25,.37),vec3(.24,.40,.52),vec3(.55,.67,.70),.4+q.y);float cells=max(step(.92,fract(r.x*28.)),step(.90,fract(r.y*40.)));blue=mix(blue,vec3(.57,.67,.68),cells*.55);col=mix(col,blue,panel);col=mix(col,vec3(.34,.38,.33),stroke(q,vec2(.10,-.02),vec2(.10,-.08),.005));}}
 if(scene==8||scene==10){for(int i=0;i<4;i++){float n=float(i);turbine(col,vec2(aspect*(.17+n*.23),.17+n*.006),.25-n*.03,clock*(scene==10?.22:.07)+n*.8);}if(scene==10){vec2 at=vec2(aspect*.22,.18);col=mix(col,vec3(.46,.53,.48),stroke(p,at,at+vec2(0,.40),.004));vec2 q=p-at-vec2(0,.36);float tail=cover(min(min(q.x,.18-q.x),(.025-q.x*.07)-abs(q.y+.006*sin(clock*1.5+q.x*20.))));col=mix(col,mix(vec3(.85,.33,.13),vec3(.92,.86,.66),step(.5,fract(q.x*24.))),tail);}}
 if(misty){vec2 lamp=vec2(aspect*.72+.22,.50);float light=exp(-dot(p-lamp,p-lamp)/.0012);col=glow(col,vec3(.95,.65,.28)*light*.24);}
 // Weather overlays originate below cloud base and use independent depth/speed layers.
 if(rain>.01){float base=.53+.035*sin(uv.x*9.);for(int i=0;i<3;i++){float n=float(i),columns=79.-n*19.,rows=23.-n*4.,origin=uv.x+max(0.,base-uv.y)*.08;float column=floor(origin*columns),seed=hash(vec2(column,n+2.));vec2 drop=vec2(fract(origin*columns),fract((base-uv.y)*rows-clock*(.65+n*.27)+seed*11.));if(uv.y<base&&seed>.27&&drop.x<.045+n*.025){float tail=(1.-smoothstep(0.,.30,drop.y))*(.18+n*.11)*rain;col=mix(col,vec3(.64,.76,.85),tail);}}}
 if(storm){float epoch=floor(clock/29.),at=epoch*29.+5.+hash(vec2(epoch,3.))*9.;float flash=exp(-pow((clock-at)/.13,2.))+.5*exp(-pow((clock-at-.24)/.08,2.));vec2 q=uv-vec2((hash(vec2(epoch,7.))-.5)*.15,0);float bolt=min(min(segment(q,vec2(.78,.58),vec2(.73,.43)),segment(q,vec2(.73,.43),vec2(.78,.45))),segment(q,vec2(.78,.45),vec2(.69,.28)));col=glow(col,vec3(.44,.54,.79)*flash*.13);col=mix(col,vec3(.89,.94,1.),cover(.0018-bolt)*min(1.,flash));}
 if(snow){for(int i=0;i<3;i++){float n=float(i);vec2 field=vec2(37.+n*13.,21.+n*8.),v=uv+vec2(sin(clock*.08+n)*.008,-clock*(.045+n*.023));vec2 tile=floor(v*field),flake=fract(v*field)-.5;float mask=(1.-smoothstep(.02,.055-n*.01,length(flake)))*step(.59,hash(tile+n));col=mix(col,vec3(.94,.97,.96),mask*.8);}}
 if(misty||scene==6){for(int i=0;i<3;i++){float n=float(i);float veil=exp(-pow((uv.y-(.23+n*.08+sin(uv.x*6.+clock*.035+n)*.015))/.045,2.))*(.4+.6*noise(vec2(uv.x*5.-clock*.005,uv.y*21.+n)));col=mix(col,vec3(.75,.81,.77),veil*(misty?.25:.10));}}
 col*=.98+.012*sin(clock*.021)+.008*sin(clock*.053);outputColor=vec4(clamp(col,0.,.98),1.);
}
