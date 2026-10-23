package studio.sundown.weather

import android.content.Context
import android.graphics.*
import java.time.Instant
import kotlin.math.*

class ObservationDashboard(context:Context,model:AppModel):Dashboard(context,model){
    private var wx:Weather?=null
    private var at=0L
    private var title=26f
    private val pad=12f
    private val pale=Color.rgb(237,237,230)
    private var lunarEvents:Pair<String,List<Pair<Long,Boolean>>>?=null
    private fun v(k:String)=wx?.value(k,at)
    private fun n(k:String,d:Int=0)=Units.number(v(k),d)
    private fun t(k:String,d:Int=1)=model.temp(v(k),d)
    private fun heading(zh:String,en:String)=tr(zh,en)
    private fun titleText(content:String,x:Float,y:Float,size:Float=26f,fg:Int=INK){text(content,x,y,size,fg,Typeface.create(chinese,450,false))}
    private var activeId=""
    private fun color(css:String,fallback:Int):Int {val values=Regex("[0-9.]+").findAll(css).map{it.value.toFloat()}.toList();return if(values.size>=3)Color.rgb(values[0].toInt(),values[1].toInt(),values[2].toInt())else fallback}
    private fun measuredText(part:String,content:String,overrideRect:RectF?=null):Float? {
        val m=metrics?:return null;val key="$activeId:$part";val node=m.node(key)?:return null
        val b=RectF(overrideRect?:m.rect(key));if(b.isEmpty)return null
        val module=m.rect(activeId);val right=module.right-12
        if(part=="heading")b.right=right
        else b.right=min(b.right,right)
        val font=when{part=="heading"->chinese;m.font(key).trim().startsWith("MiSans")->chinese;m.font(key).contains("Roboto")->mono;else->languageFont(content)}
        val size=m.size(key)
        val fg=color(node.optString("color"),INK)
        p.reset();p.isAntiAlias=true;p.typeface=Typeface.create(font,m.weight(key),false);p.textSize=size;p.color=fg
        p.textAlign=Paint.Align.LEFT
        // Unicode symbols do not select a different family. Shape the whole string in one run.
        p.letterSpacing=if(part=="reading")0f else m.spacing(key)/size
        if(part in listOf("heading","detail","note")&&p.measureText(content)>b.width()) {
            paragraph(content,RectF(b.left,b.top,right,min(module.bottom-8,b.top+m.line(key)*2)),size,fg,2)
        } else {
            val baseline=b.top+(m.line(key)-(p.fontMetrics.descent-p.fontMetrics.ascent))/2-p.fontMetrics.ascent
            c!!.drawText(content,b.left,baseline,p)
        }
        return p.measureText(content)
    }
    private fun note(content:String,b:RectF,dark:Boolean=false){
        if(measuredText("detail",content)==null)fit(content,b.left,b.bottom-20,b.width(),16f,if(dark)PAPER else INK)
    }
    private fun graphRect(fallback:RectF):RectF = metrics?.rect("$activeId:graphic")?.takeUnless{it.isEmpty}?:fallback
    private fun base(b:RectF,name:String,dark:Boolean=false,color:Int?=null):RectF {
        val fg=if(dark)PAPER else INK;val node=metrics?.node(activeId)
        val background=node?.optString("bg")?.let{this.color(it,color?:if(dark)CHARCOAL else PAPER)}?:color?:if(dark)CHARCOAL else PAPER
        val radii=node?.optJSONArray("radii")
        if(radii!=null){val rs=FloatArray(8){idx->radii.getDouble(idx/2).toFloat()*(metrics?.sx?:1f)};val path=Path().apply{addRoundRect(b,rs,Path.Direction.CW)};p.reset();p.isAntiAlias=true;p.color=background;c!!.drawPath(path,p)}else rect(b,background)
        if(measuredText("heading",name)==null)paragraph(name,RectF(b.left+pad,b.top+pad,b.right-pad,b.top+pad+title*2.3f),title,fg,if(model.lang=="en"&&name.length*title*.53>b.width()-24)2 else 1)
        val head=metrics?.rect("$activeId:heading")
        return RectF(b.left+pad,head?.takeUnless{it.isEmpty}?.bottom?.plus(10)?:b.top+pad+title+10,b.right-pad,b.bottom-pad)
    }
    private fun value(b:RectF,number:String,unit:String="",size:Float=42f,dark:Boolean=false){
        val m=metrics!!;val nr=m.rect("$activeId:reading");val ur=m.rect("$activeId:unit")
        val fg=if(dark)PAPER else INK
        var numberSize=if(nr.isEmpty)size else m.size("$activeId:reading")
        val unitSize=if(ur.isEmpty)max(16f,numberSize*.38f)else m.size("$activeId:unit")
        p.typeface=inter;p.textSize=unitSize;val uw=if(unit.isEmpty())0f else p.measureText(unit)+6
        val left=if(nr.isEmpty)b.left else nr.left
        val right=min(b.right,m.rect(activeId).right-12)
        p.textSize=numberSize;val nw=p.measureText(number)
        if(nw+uw>right-left)numberSize=max(24f,numberSize*(right-left-uw)/max(1f,nw))
        val top=if(nr.isEmpty)b.top else nr.top
        text(number,left,top,numberSize,fg,inter)
        p.typeface=inter;p.textSize=numberSize
        text(unit,left+p.measureText(number)+6,top+numberSize*.44f,unitSize,fg,inter)
    }
    private fun scalar(b:RectF,name:String,number:String,unit:String="",dark:Boolean=false){
        if(portrait&&b.width()<240&&b.height()>75){val body=base(b,name,dark,if(dark)CHARCOAL else pale);value(body,number,unit,32f,dark);return}
        val fg=if(dark)PAPER else INK
        val node=metrics?.node(activeId)
        val bg=node?.optString("bg")?.let{color(it,if(dark)CHARCOAL else pale)}?:if(dark)CHARCOAL else pale
        base(b,"",dark,bg)
        val ns=if(activeId=="ins-optical")27f else 32f
        val us=16f
        p.typeface=inter;p.textSize=ns;val nw=p.measureText(number)
        p.textSize=us;val uw=if(unit.isEmpty())0f else p.measureText(unit)+6
        val x=b.right-12-nw-uw;val rowY=if(activeId=="ins-thermal")b.top+28 else b.centerY();val y=rowY-ns*.58f
        text(number,x,y,ns,fg,inter);text(unit,x+nw+6,y+ns*.44f,us,fg,inter)
        val labelSize=if(w>=1500)26f else 24f
        fit(name,b.left+12,rowY-labelSize*.58f,max(0f,x-b.left-28),labelSize,fg,Typeface.create(chinese,450,false))
    }
    private fun series(b:RectF,key:String,low:Double?=null,high:Double?=null,dark:Boolean=false){
        val points=wx?.hourly?.range(key,at,at+7200)?:return
        val values=points.mapNotNull{it.second};if(values.isEmpty())return
        val min=low?:min(0.0,values.min());val max=high?:max(min+1,values.max())
        val color=if(dark)0xffd9a18c.toInt() else 0xff864731.toInt()
        line(b.left,b.bottom,b.right,b.bottom,if(dark)0xff888880.toInt() else 0xffb4b4a9.toInt())
        points.forEach{(epoch,value)->if(value!=null){val x=b.left+(epoch-at)/7200f*b.width();val y=b.bottom-((value-min)/(max-min)).toFloat()*b.height();line(x,b.bottom,x,y,color,max(2f,b.width()/45))}}
    }
    private fun wind(b:RectF,primary:Boolean=false){
        val kind=if(primary)heading("阵风","Wind gust") else heading("风速","Wind")
        val body=base(b,kind,true,if(primary)CORAL else null);val dark=!primary
        val gauge=graphRect(RectF(body.right-body.height()*.75f,body.top,body.right,body.bottom-42))
        val cx=gauge.centerX();val cy=gauge.centerY();val r=min(gauge.width(),gauge.height())*.43f
        repeat(72){i->val a=i*PI/36;val outer=if(i%6==0)r*.8f else r*.89f;line(cx+cos(a).toFloat()*outer,cy+sin(a).toFloat()*outer,cx+cos(a).toFloat()*r,cy+sin(a).toFloat()*r,if(dark)0xffa5a59a.toInt() else INK,.8f)}
        arrow(cx,cy,r*.7f,v("wind_direction_10m"),if(dark)PAPER else INK)
        value(RectF(body.left,body.top,gauge.left-8,body.bottom-42),model.speed(v(if(primary)"wind_gusts_10m" else "wind_speed_10m")),model.windUnit,if(primary)58f else 44f,dark)
        text((if(primary)heading("风速","Mean")else heading("阵风","Gust"))+" "+model.speed(v(if(primary)"wind_speed_10m"else "wind_gusts_10m"))+" "+model.windUnit,body.left,body.bottom-35,18f,if(dark)PAPER else INK)
        text(heading("来向","From")+" "+n("wind_direction_10m")+"°",body.right,body.bottom-35,18f,if(dark)PAPER else INK,chinese,Paint.Align.RIGHT)
    }
    private fun pressure(b:RectF){
        val body=base(b,heading("气压","Pressure"),true)
        val gr=graphRect(RectF(body.left+20,body.top,body.right-20,body.bottom-70))
        val scale=min(gr.width()/300f,gr.height()/160f)
        val ox=gr.centerX()-150*scale;val oy=gr.centerY()-68*scale
        val cx=ox+150*scale;val cy=oy+132*scale;val r=120*scale
        arc(RectF(cx-r,cy-r,cx+r,cy+r),180f,180f,PAPER,12*scale)
        repeat(41){i->val angle=PI-i*PI/40;val inner=(if(i%5==0)82 else 91)*scale;val outer=104*scale;line(cx+cos(angle).toFloat()*inner,cy-sin(angle).toFloat()*inner,cx+cos(angle).toFloat()*outer,cy-sin(angle).toFloat()*outer,PAPER,1.2f*scale)}
        v("pressure_msl")?.let{q->val angle=PI-(q-960).coerceIn(0.0,80.0)/80*PI;line(cx+cos(angle).toFloat()*109*scale,cy-sin(angle).toFloat()*109*scale,cx+cos(angle).toFloat()*137*scale,cy-sin(angle).toFloat()*137*scale,0xffd28a70.toInt(),4*scale);circle(cx+cos(angle).toFloat()*137*scale,cy-sin(angle).toFloat()*137*scale,4*scale,0xffd28a70.toInt())}
        value(body,n("pressure_msl",1),"hPa",40f,true)
        val delta=wx?.difference("pressure_msl",at,3);val label=(if(delta!=null&&delta>0)"+"else "")+Units.number(delta,1)+" hPa / 3h"
        if(measuredText("detail",label)==null)text(label,body.left,body.bottom-20,16f,PAPER,mono)
        text("960",gr.left,gr.bottom-4,14f,PAPER,mono);text("1040",gr.right,gr.bottom-4,14f,PAPER,mono,Paint.Align.RIGHT)
    }
    private fun svgPlot(rect:RectF,vx:Float,vy:Float,vw:Float,vh:Float,stretch:Boolean=false,draw:()->Unit){
        val sx=rect.width()/vw;val sy=rect.height()/vh;val scale=min(sx,sy);c!!.save()
        if(stretch){c!!.translate(rect.left-vx*sx,rect.top-vy*sy);c!!.scale(sx,sy)}else{c!!.translate(rect.centerX()-(vx+vw/2)*scale,rect.centerY()-(vy+vh/2)*scale);c!!.scale(scale,scale)}
        draw();c!!.restore()
    }
    private fun vector(d:String,color:Int=INK,width:Float=1f,fill:Boolean=false,alpha:Int=255){
        val path=androidx.core.graphics.PathParser.createPathFromPathData(d)?:return;p.reset();p.isAntiAlias=true;p.textAlign=Paint.Align.LEFT;p.color=color;p.alpha=alpha;p.style=if(fill)Paint.Style.FILL else Paint.Style.STROKE;p.strokeWidth=width;c!!.drawPath(path,p)
    }
    private fun dew(b:RectF){
        val body=base(b,heading("露点差","Dew-point spread"),true)
        val temp=v("temperature_2m");val td=v("dew_point_2m")?:Physics.dew(temp,v("relative_humidity_2m"));val spread=if(temp!=null&&td!=null)temp-td else null
        value(body,model.temp(spread,1,true),model.tempUnit,40f,true)
        measuredText("detail",heading("露点 ","Dew point ")+model.temp(td,1)+model.tempUnit)
        val gr=graphRect(RectF(body.left,body.top+62,body.right,body.bottom-20))
        val points=wx?.hourly?.times?.filter{it in at..at+7200}?.map{epoch->val t=wx?.hourly?.at("temperature_2m",epoch);val d=wx?.hourly?.at("dew_point_2m",epoch);(epoch-at)/7200.0 to if(t!=null&&d!=null)t-d else null}?:emptyList()
        if(points.size<2||points.any{it.second==null})return
        val max=max(3.0,ceil(points.maxOf{it.second!!}))
        svgPlot(gr,0f,0f,300f,120f,true){
            val path=Path();points.forEachIndexed{i,(time,value)->val x=(8+time*284).toFloat();val y=(107-value!!/max*92).toFloat();if(i==0)path.moveTo(x,y)else path.lineTo(x,y)}
            val area=Path(path).apply{lineTo(292f,112f);lineTo(8f,112f);close()}
            c!!.save();c!!.clipPath(area);for(x in -120..420 step 6)line(x.toFloat(),120f,x+120f,0f,0x73fcfcf8,.7f);c!!.restore()
            p.reset();p.isAntiAlias=true;p.style=Paint.Style.STROKE;p.strokeWidth=3f;p.color=0xffed7c55.toInt();c!!.drawPath(path,p)
            val y=(107-2/max*92).toFloat();p.pathEffect=DashPathEffect(floatArrayOf(3f,5f),0f);p.strokeWidth=1f;p.color=0x59fcfcf8;c!!.drawLine(8f,y,292f,y,p);p.pathEffect=null
        }
        text(model.temp(2.0,1,true)+model.tempUnit,gr.right,gr.bottom-18,16f,PAPER,inter,Paint.Align.RIGHT)
    }
    private fun humidity(b:RectF,precip:Boolean=false){
        val body=base(b,if(precip)heading("降水概率","Precipitation")else heading("相对湿度","Humidity"))
        val number=if(precip)wx?.precip(at)?.second else v("relative_humidity_2m");value(body,Units.number(number),"%",38f)
        val gr=graphRect(RectF(body.left,body.top+32,body.right,body.bottom-20));val plot=RectF(gr.left,gr.top,gr.right,gr.bottom-20)
        fun xx(v:Float)=plot.left+v/252*plot.width()
        fun yy(v:Float)=plot.top+v/110*plot.height()
        repeat(42){i->val x=12+i*5.5f;val height=22+i*1.65f;val active=number!=null&&number>0&&i/41.0<=number/100;line(xx(x),yy(99f),xx(x),yy(99-height),if(active)0xff864731.toInt()else 0xffe0ded7.toInt(),plot.width()/252*1.8f)}
        line(xx(8f),yy(103f),xx(242f),yy(103f),0xffb4a89b.toInt(),.7f)
        number?.let{n->val marker=xx(12+225.5f*n.toFloat()/100);val path=Path().apply{moveTo(marker-4,yy(4f));lineTo(marker,yy(10f));lineTo(marker+4,yy(4f));close()};p.reset();p.color=0xff864731.toInt();c!!.drawPath(path,p);p.pathEffect=DashPathEffect(floatArrayOf(1f,3f),0f);p.style=Paint.Style.STROKE;p.strokeWidth=.6f;c!!.drawLine(marker,yy(12f),marker,yy(101f),p);p.pathEffect=null}
        repeat(6){i->text((i*20).toString()+if(i==5)"%"else "",gr.left+i*gr.width()/5,gr.bottom-18,16f,0xff655442.toInt(),mono,if(i==5)Paint.Align.RIGHT else if(i==0)Paint.Align.LEFT else Paint.Align.CENTER)}
    }
    private fun clouds(b:RectF){
        val body=base(b,heading("云层分布","Cloud layers"));val hh=body.height()/3
        listOf("cloud_cover_high","cloud_cover_mid","cloud_cover_low").forEachIndexed{i,key->
            val y=body.top+i*hh;text(listOf(heading("高云","High"),heading("中云","Mid"),heading("低云","Low"))[i],body.left,y,18f)
            val r=RectF(body.left+58,y+12,body.right-80,y+18);rect(r,0xffdeded7.toInt(),2f);v(key)?.let{n->rect(RectF(r.left,r.top,r.left+r.width()*n.toFloat()/100,r.bottom),listOf(0xff864731,0xff756448,0xff373f36)[i].toInt(),2f)}
            text(n(key)+" %",body.right,y-2,26f,INK,inter,Paint.Align.RIGHT)
        }
    }
    private fun pwv(b:RectF){
        val body=base(b,heading("整层水汽 PWV","Column water"));value(RectF(body.left,body.top,body.right-65,body.bottom),n("total_column_integrated_water_vapour",1),"mm",42f)
        val r=RectF(body.right-44,body.top,body.right-14,body.bottom-14);rect(r,0xffeeeee6.toInt());v("total_column_integrated_water_vapour")?.let{n->rect(RectF(r.left,r.bottom-r.height()*(n/70).toFloat().coerceIn(0f,1f),r.right,r.bottom),CORAL)}
        repeat(15){i->val y=r.top+r.height()*i/14;line(r.left-4,y,r.right,y,0xff76766d.toInt(),.5f)};text("70",r.right+1,r.top-6,14f);text("0",r.right+1,r.bottom-14,14f)
    }
    private fun soil(b:RectF){
        val body=base(b,heading("表层土壤水分","Surface soil water"));value(RectF(body.left,body.top,body.right*.01f+body.centerX(),body.bottom-25),n("soil_moisture_0_to_1cm",2),"m³/m³",38f)
        val gr=RectF(body.centerX()+30,body.top,body.right,body.bottom-30);val count=v("soil_moisture_0_to_1cm")?.times(40)?.roundToInt()
        repeat(40){i->val cw=gr.width()/8;val ch=gr.height()/5;rect(RectF(gr.left+(i%8)*cw+1,gr.top+(i/8)*ch+1,gr.left+(i%8+1)*cw-1,gr.top+(i/8+1)*ch-1),if(count!=null&&i<count)0xff5a5445.toInt() else 0xffe3e3da.toInt())}
        text(heading("0–1 cm · 体积含水率","0–1 cm · volumetric"),body.left,body.bottom-20,16f)
    }
    private fun solar(b:RectF){
        val body=base(b,heading("太阳高度角","Solar elevation"));val sun=wx?.let{Astronomy.sun(at,it.place)}
        value(body,Units.number(sun?.elevation,1),"°",40f)
        val gr=metrics?.rect("$activeId:svg")?.takeUnless{it.isEmpty}?:RectF(body.centerX(),body.top-22,body.right,body.bottom-40)
        svgPlot(gr,70f,-5f,120f,150f){
            vector("M110 10A60 60 0 0 1 110 130M30 70H190",0x4d201f1b)
            for(deg in -90..90 step 10){val a=Math.toRadians(deg.toDouble());val r=if(deg%30==0)51 else 55;line((110+r*cos(a)).toFloat(),(70-r*sin(a)).toFloat(),(110+60*cos(a)).toFloat(),(70-60*sin(a)).toFloat(),INK,.8f)}
            sun?.let{val angle=Math.toRadians(it.elevation);val x=(110+59*cos(angle)).toFloat();val y=(70-59*sin(angle)).toFloat();line(110f,70f,x,y,0xff924b35.toInt(),2f);circle(x,y,5f,0xff924b35.toInt());circle(110f,70f,2f,INK)}
        }
        text("90°",gr.right-25,gr.top,16f);text("0°",gr.right-25,gr.centerY()-8,16f);text("−90°",gr.right-25,gr.bottom-20,16f)
        if(wx!=null){val elevations=(0..12).map{Astronomy.sun(at+it*600,wx!!.place).elevation};val lo=elevations.min()-2;val hi=elevations.max()+2
            val rr=RectF(body.left,body.bottom-48,body.right,body.bottom-26)
            svgPlot(rr,0f,0f,300f,32f){vector("M4 29H296",0x26201f1b);val points=elevations.mapIndexed{i,v->(if(i==0)"M"else "L")+(4+i*292/12.0)+" "+(27-(v-lo)/(hi-lo)*22)}.joinToString("");vector(points,INK,1.5f)}
            text(Units.number(elevations.first(),1)+"° → "+Units.number(elevations.last(),1)+"°",body.right,body.bottom-18,14f,INK,inter,Paint.Align.RIGHT)
        }
    }
    private fun bearing(b:RectF){
        val body=base(b,heading("太阳方位","Solar azimuth"),color=pale);val az=wx?.let{Astronomy.sun(at,it.place).azimuth}
        value(RectF(body.left,body.top,body.centerX(),body.bottom),Units.number(az),"°",42f)
        val r=body.height()*.7f;val cx=body.right+20;val cy=body.centerY()
        circle(cx,cy,r,CORAL);repeat(13){i->val a=PI+(i-6)*PI/24;line(cx+cos(a).toFloat()*r*.83f,cy+sin(a).toFloat()*r*.83f,cx+cos(a).toFloat()*r*.95f,cy+sin(a).toFloat()*r*.95f,INK,1f)}
    }
    private fun radiation(b:RectF){
        rect(b,CHARCOAL,20f);titleText(heading("直射 / 漫射","Direct / diffuse"),b.left+12,b.top+10,26f,PAPER);val body=RectF(b.left+12,b.top+44,b.right-12,b.bottom-12)
        val direct=v("direct_radiation_instant");val diffuse=v("diffuse_radiation_instant");val total=if(direct!=null&&diffuse!=null)direct+diffuse else null
        val reading=Units.number(direct)+" / "+Units.number(diffuse)
        val rsize=if(portrait)30f else 32f
        p.typeface=inter;p.textSize=rsize;val readingWidth=p.measureText(reading)
        // Title and reading use separate rows when the actual pair will not fit.
        val rx=b.right-12-readingWidth
        val top=b.top+44
        text(reading,rx,top,rsize,PAPER,inter)
        text("W/m²",b.left+12,top+9,16f,PAPER)
        val gr=RectF(body.left,b.top+84,body.right,body.bottom-22)
        if(total!=null){val day=total>0;val f=if(day)diffuse!!/total else 0.0;val horizon=if(day)77f else 38f
            svgPlot(gr,0f,0f,320f,105f){
                c!!.save();c!!.clipRect(14f,horizon,306f,horizon+10);for(x in -100..400 step 8)line(x.toFloat(),horizon+10,x+10f,horizon,0x4dfcfcf8,1f);c!!.restore();line(14f,horizon,306f,horizon,PAPER,1.5f)
                if(day){circle(55f,13f,7f,0xffd99975.toInt());for(i in 0..6){val x=30+i*11f;val angle=Math.toRadians(25+i*22.0);if(direct!!>0)vector("M$x 22l32 47",0xffd99975.toInt(),(1+(1-f)*2.5).toFloat(),alpha=((.25+(1-f)*.75)*255).toInt());if(diffuse!!>0)vector("M${240+60*cos(angle)} ${75-58*sin(angle)}Q240 35 240 73",0xffd9d3bb.toInt(),(.7+f*2.2).toFloat(),alpha=((.25+f*.75)*255).toInt())};rect(RectF(14f,94f,(14+292*(1-f)).toFloat(),99f),0xffd99975.toInt());rect(RectF((14+292*(1-f)).toFloat(),94f,306f,99f),0xffd9d3bb.toInt())}
                else{vector("M24 53H132M188 53H296",0x40fcfcf8);circle(160f,65f,12f,0xffd99975.toInt());vector("M160 42V47M138 65H143M177 65H182",0xffd99975.toInt())}
            }
            val caption=if(day)heading("漫射占比 ","Diffuse share ")+Units.number(f*100)+"%"else heading("夜间 · 无太阳短波辐射","Night · no solar flux")
            fit(caption,body.left,body.bottom-18,body.width(),16f,PAPER)
        }
    }
    private fun lunar(b:RectF,contextOnly:Boolean=false){
        val focus=activeId=="ins-focus"
        val body=base(b,if(focus)heading("月面照明 ≈","Moon illumination ≈")else if(contextOnly)heading("月升落 ≈","Moon crossings ≈") else heading("月相 ≈","Moon phase ≈"))
        if(!focus)text("Bortle —",body.right,b.top+(if(contextOnly)48 else 14),16f,INK,mono,Paint.Align.RIGHT)
        val moon=wx?.let{Astronomy.moon(at)}
        if(!contextOnly){
            value(body,Units.number(moon?.illumination?.times(100)),"%",40f)
            val moonRect=metrics?.rect("$activeId:svg")?.takeUnless{it.isEmpty};val radius=if(moonRect!=null)min(moonRect.width(),moonRect.height())*.46f else min(64f,min(body.height()*.4f,body.width()*.22f));val x=moonRect?.centerX()?:body.right-radius-6;val y=moonRect?.centerY()?:body.top+radius+14
            circle(x,y,radius,0xff35352f.toInt())
            if(moon!=null){val path=Path();val waxing=moon.fraction<=.5;val cosPhase=cos(2*PI*moon.fraction)
                for(i in 0..80){val yy=-radius+2*radius*i/80;val limb=sqrt(max(0f,radius*radius-yy*yy));val xx=if(waxing)limb else -limb;if(i==0)path.moveTo(x+xx,y+yy)else path.lineTo(x+xx,y+yy)}
                for(i in 80 downTo 0){val yy=-radius+2*radius*i/80;val limb=sqrt(max(0f,radius*radius-yy*yy));val xx=cosPhase*limb*(if(waxing)1 else -1);path.lineTo(x+xx.toFloat(),y+yy)};path.close();p.reset();p.isAntiAlias=true;p.color=0xfffff4c9.toInt();c!!.drawPath(path,p)}
            val phases=if(model.lang=="zh")listOf("新月","蛾眉月","上弦月","盈凸月","满月","亏凸月","下弦月","残月")else listOf("New","Wax. crescent","First quarter","Wax. gibbous","Full","Wan. gibbous","Last quarter","Wan. crescent");text(moon?.let{phases[(it.fraction*8).roundToInt()%8]}?:"—",body.left,body.top+48,16f)
        }
        if(focus){note(heading("平均朔望月近似","Mean lunation estimate"),body);return}
        if(wx!=null){val date=Instant.ofEpochSecond(at).atZone(wx!!.zone).toLocalDate();val key=date.toString()+wx!!.place.key
            if(lunarEvents?.first!=key)lunarEvents=key to Astronomy.crossings(date,wx!!.place,0.0,true)
            val events=lunarEvents!!.second
            text("↑ "+wx!!.clock(events.find{it.second}?.first)+" ≈",body.left,body.bottom-40,16f,INK,mono)
            text("↓ "+wx!!.clock(events.find{!it.second}?.first)+" ≈",body.left,body.bottom-20,16f,INK,mono)
        }
    }
    private fun upper(b:RectF){
        val body=base(b,heading("高空风 / 风差","Upper wind / Δv"),true)
        value(RectF(body.left,body.top,body.centerX(),body.bottom-24),model.speed(v("wind_speed_300hPa")),model.windUnit,42f,true)
        val upper=v("wind_speed_300hPa");val surface=v("wind_speed_10m");val largest=max(1.0,max(upper?:0.0,surface?:0.0))
        arrow(body.right-55,body.top+20,(upper?.div(largest)?.times(45)?:0.0).toFloat(),v("wind_direction_300hPa")?.plus(180),CORAL)
        arrow(body.right-55,body.top+55,(surface?.div(largest)?.times(45)?:0.0).toFloat(),v("wind_direction_10m")?.plus(180),PAPER)
        val delta=Physics.windDelta(surface,v("wind_direction_10m"),upper,v("wind_direction_300hPa"))
        text("300 hPa · "+n("wind_direction_300hPa")+"° · Δv "+model.speed(delta),body.left,body.bottom-18,14f,PAPER,mono)
    }
    private fun light(b:RectF){
        rect(b,PAPER)
        val label=heading("光影倒计时","Light timer")
        titleText(label,b.left+12,b.top+10)
        val w=wx
        val now=System.currentTimeMillis()/1000
        val date=Instant.ofEpochSecond(now).atZone(w?.zone?:java.time.ZoneId.systemDefault()).toLocalDate()
        val angle=if(model.purpose==6)-4.0 else 6.0
        val event=w?.let{(0L..2L).flatMap{i->Astronomy.crossings(date.plusDays(i),it.place,angle)}.filter{it.first>now}.minByOrNull{it.first}}
        val sec=event?.first?.minus(now)?.coerceAtLeast(0)
        val count=sec?.let{String.format(java.util.Locale.US,"%02d:%02d:%02d",it/3600,(it%3600)/60,it%60)}?:"—"
        val phase=if(model.purpose==6)heading("蓝调时刻","Blue hour")else heading("黄金时刻","Golden hour")
        text(count,b.left+12,b.bottom-47,32f,INK,inter)
        text(phase,b.right-12,b.top+12,16f,INK,languageFont(phase),Paint.Align.RIGHT)
        text((w?.clock(event?.first)?:"—")+" ≈",b.right-12,b.bottom-30,16f,INK,mono,Paint.Align.RIGHT)
    }
    private fun rain(b:RectF,totalB:RectF){
        val body=base(b,heading("短时降水","Rain trend"),true)
        val samples=wx?.hourly?.times?.filter{it>at&&it<=at+7200}?:emptyList()
        val values=samples.map{wx?.hourly?.at("precipitation",it)}
        val last=values.firstOrNull();text(Units.number(last,1)+" mm/h",body.right,body.top-7,30f,PAPER,inter,Paint.Align.RIGHT)
        val chart=RectF(body.left,body.top+38,body.right,body.bottom-40);series(chart,"precipitation",0.0,max(1.0,values.filterNotNull().maxOrNull()?:1.0),true)
        text(heading("每小时模型 · 非雷达","Hourly model · not radar"),body.left,body.bottom-20,14f,PAPER)
        val total=if(values.size==2&&values.all{it!=null})values.sumOf{it!!} else null
        activeId="ins-rain-total";scalar(totalB,heading("2h 累计降水","2h rain total"),Units.number(total,1),"mm",true)
    }
    private fun primary(b:RectF){
        when(model.purpose){
            2->wind(b,true)
            7->{lunar(b);return}
            10,11->{humidity(b);fit(heading("逐小时模型预报","Hourly model forecast"),b.left+12,b.top+55,b.width()*.66f,16f);return}
            else->{val kind=when(model.purpose){4,5,6->"glow";8->"visibility";9,3->"temperature";10,11->"humidity";else->"precipitation"}
                val label=when(kind){"glow"->heading("霞光条件指数","Glow condition score");"visibility"->heading("能见度","Visibility");"temperature"->heading("气温","Temp.");"humidity"->heading("相对湿度","Humidity");else->heading("降水概率","Precipitation")}
                rect(b,CORAL,20f);val body=base(b,label,color=CORAL)
                val value=when(kind){"glow"->Physics.glow(v("cloud_cover_high"),v("cloud_cover_mid"),v("cloud_cover_low"),v("visibility"),v("relative_humidity_2m"));"visibility"->v("visibility")?.div(1000);"temperature"->Units.temperature(v("temperature_2m"),model.fahrenheit);"humidity"->v("relative_humidity_2m");else->wx?.precip(at)?.second}
                val unit=when(kind){"glow"->"/100";"visibility"->"km";"temperature"->model.tempUnit;else->"%"}
                value(RectF(body.left,body.top,body.right,body.bottom-30),Units.number(value,if(kind=="temperature"||kind=="visibility")1 else 0),unit,if(portrait)58f else 70f)
                if(kind=="precipitation")series(RectF(body.centerX(),body.top+10,body.right,body.bottom-28),"precipitation_probability",0.0,100.0)
                if(kind=="temperature"){val day=wx?.today(at);val lo=day?.low;val hi=day?.high;val r=RectF(body.left,body.bottom-12,body.right,body.bottom-5);rect(r,0xff56534d.toInt());v("temperature_2m")?.let{temp->if(lo!=null&&hi!=null){val pos=if(hi>lo)((temp-lo)/(hi-lo)).coerceIn(0.0,1.0) else .5;val xx=r.left+r.width()*pos.toFloat();line(xx,r.top-3,xx,r.bottom+3,PAPER,3f)}}}
                fit(if(kind=="glow")heading("云层与能见度推算","From cloud + visibility")else heading("逐小时模型预报","Hourly model forecast"),body.left,body.bottom-(if(kind=="temperature")44 else 22),body.width(),16f)
            }
        }
    }
    private fun wheel(b:RectF){
        dialRect=b;rect(b,0xff20201e.toInt(),24f)
        p.reset();p.isAntiAlias=true;p.color=0xff676dba.toInt();p.style=Paint.Style.STROKE;p.strokeWidth=1f;c!!.drawRoundRect(b,24f,24f,p)
        c!!.save();c!!.clipRect(RectF(b.left,b.top+16,b.right,b.bottom-44))
        val en=model.lang=="en";val radius=b.height()*.70f;val labelX=b.width()*(if(en).29f else .35f);val centerX=b.left+labelX-radius;val centerY=b.centerY()
        val font=max(if(en)18f else 24f,min(if(en)32f else 44f,b.width()*(if(en).069f else .09f)))
        val outer=radius+b.width()*.49f
        arc(RectF(centerX-outer,centerY-outer,centerX+outer,centerY+outer),-75f,150f,0xff565650.toInt(),.8f)
        for(step in -40..40){val angle=step/4.0*8.5;val a=Math.toRadians(angle);val r=radius+b.width()*.43f;val len=if(step%4==0)8f else 3f;line(centerX+cos(a).toFloat()*(r-len),centerY+sin(a).toFloat()*(r-len),centerX+cos(a).toFloat()*r,centerY+sin(a).toFloat()*r,if(step%4==0)0xff92928b.toInt()else 0xff45453f.toInt(),.8f)}
        for(offset in -6..6){val index=((model.purpose+offset)%12+12)%12;val angle=offset*8.5f;val a=Math.toRadians(angle.toDouble());val x=centerX+cos(a).toFloat()*radius;val y=centerY+sin(a).toFloat()*radius
            c!!.save();c!!.rotate(angle,x,y);text(model.purposeName(index),x,y-font*.55f,font*(if(offset==0)1.05f else 1f),if(offset==0)PAPER else 0xffb8b8af.toInt(),Typeface.create(chinese,450,false));c!!.restore()}
        circle(b.left+b.width()*.12f,centerY,max(10f,min(16f,b.width()*.043f)),0xff2459ff.toInt());c!!.restore()
        text(heading("拖动选择","DRAG"),b.left+16,b.bottom-28,14f,0xffb8b8af.toInt());text(String.format(java.util.Locale.US,"%02d / 12",model.purpose+1),b.right-16,b.bottom-28,14f,0xffb8b8af.toInt(),mono,Paint.Align.RIGHT)
    }
    private var metricsKey=""
    private var metrics:DesignMetrics?=null
    override fun drawDashboard(){
        wx=model.weather;val win=wx?.let{observationWindow(it,model.purpose)};at=win?.start?:System.currentTimeMillis()/1000
        title=if(w>=1500)26f else 24f
        val key="observation-${if(portrait)"portrait" else "landscape"}-${model.lang}-${model.purpose}"
        if(metricsKey!=key){metrics=DesignMetrics(context,key);metricsKey=key}
        val m=metrics!!;m.configure(w,h,(context as MainActivity).root.paddingTop/density)
        fun r(name:String)=m.rect(name)
        fun draw(name:String,action:(RectF)->Unit){val b=r(name);val previous=activeId;activeId=name;c!!.save();c!!.clipRect(b);action(b);c!!.restore();activeId=previous}
        rect(RectF(0f,0f,w,h),FRAME)
        val panorama=r("ins-panorama");rect(panorama,PAPER,26f)
        artRect=RectF(panorama).apply{inset(5f,5f)}
        draw("ins-focus",::primary)
        val ctx=r("ins-context");val wxx=wx
        val date=wxx?.let{Instant.ofEpochSecond(at).atZone(it.zone).toLocalDate()}
        val name=model.placeName()
        fit(name+(date?.let{" · ${it.monthValue}/${it.dayOfMonth}"}?:""),ctx.left+8,ctx.top+3,ctx.width()*.58f,if(portrait)25f else 22f,PAPER)
        text(if(win?.available==true)wxx!!.clock(win.start)+"–"+wxx.clock(win.end)+(if(Instant.ofEpochSecond(win.start).atZone(wxx.zone).toLocalDate()!=Instant.ofEpochSecond(win.end).atZone(wxx.zone).toLocalDate())" +1"else "")else "—",ctx.right-8,ctx.top+3,if(portrait)25f else 22f,PAPER,inter,Paint.Align.RIGHT)
        fit(model.source(),ctx.left+8,ctx.bottom-22,ctx.width()-16,16f,PAPER)
        val thermal=if(model.purpose in listOf(3,9))"apparent_temperature"else "temperature_2m"
        draw("ins-thermal"){
            val label=if(thermal=="temperature_2m")heading("气温","Temp.")else heading("体感","Feels like")
            if(portrait){val body=base(it,label,color=pale);value(body,t(thermal),model.tempUnit,32f)}else scalar(it,label,t(thermal),model.tempUnit)
            if(!m.rect("ins-thermal:graphic").isEmpty){
            val points=listOfNotNull(v(thermal))+(wx?.hourly?.range(thermal,at,at+7200)?.mapNotNull{pair->pair.second}?:emptyList())
            val lo=points.minOrNull();val hi=points.maxOrNull()
            val rail=RectF(it.left+12,it.bottom-42,it.right-12,it.bottom-33)
            rect(rail,CHARCOAL);v(thermal)?.let{temp->if(lo!=null&&hi!=null){val x=rail.left+rail.width()*(if(hi>lo)((temp-lo)/(hi-lo)).coerceIn(0.0,1.0)else .5).toFloat();line(x,rail.top-2,x,rail.bottom+2,PAPER,2f)}}
            text("L "+model.temp(lo,1)+"°",rail.left,it.bottom-29,16f);text("H "+model.temp(hi,1)+"°",rail.right,it.bottom-29,16f,INK,inter,Paint.Align.RIGHT)
            }
        }
        draw("ins-optical"){
            if(model.purpose in 4..6){
                val body=base(it,heading("色温","CCT"),color=pale);value(body,"—","K ≈",27f);note(heading("无现场光谱测量","No spectral measurement"),body)
            }else if(portrait){val body=base(it,"PM2.5",color=pale);value(body,Units.number(wx?.pm25),"μg/m³",30f);note(wx?.airAt?.let{epoch->"CAMS · "+wx!!.clock(epoch)}?:heading("空气模型暂无数据","Air model unavailable"),body)}
            else scalar(it,"PM2.5",Units.number(wx?.pm25),"μg/m³")
        }
        draw("ins-freezing"){scalar(it,heading("零度层","0°C level"),n("freezing_level_height"),"m")}
        draw("ins-signal"){if(wx?.scene()=="storm"){val body=base(it,heading("对流抑制","CIN"));value(body,n("convective_inhibition"),"J/kg",32f);note(heading("模型能量","Model energy"),body)}else{val body=base(it,heading("凝结高度 ≈","LCL height ≈"));value(body,Units.number(Physics.lcl(v("temperature_2m"),v("dew_point_2m")?:Physics.dew(v("temperature_2m"),v("relative_humidity_2m")))),"m",32f);note(heading("距地高度 · 近似","AGL · approx."),body)}}
        draw("ins-cape"){val body=base(it,heading("对流位能","CAPE"));value(body,n("cape"),"J/kg",32f);note(heading("模型能量","Model energy"),body)}
        activeId="ins-rain";rain(r("ins-rain"),r("ins-rain-total"));activeId=""
        val assembly=r("ins-assembly");rect(assembly,PAPER,28f)
        draw("ins-wind"){if(model.purpose==2){val body=base(it,heading("降水概率","Precipitation"),true);value(body,Units.number(wx?.precip(at)?.second),"%",42f,true)}else wind(it)}
        draw("ins-pressure",::pressure);draw("ins-dew",::dew)
        draw("ins-moisture"){humidity(it,model.purpose in listOf(10,11))};draw("ins-clouds",::clouds);draw("ins-column",::pwv);draw("ins-soil",::soil)
        draw("ins-distance"){if(model.purpose==8)scalar(it,heading("体感温度","Feels like"),t("apparent_temperature"),model.tempUnit)else scalar(it,heading("能见度","Visibility"),Units.number(v("visibility")?.div(1000),1),"km")}
        draw("ins-solar",::solar);draw("ins-bearing",::bearing);draw("ins-radiation",::radiation);draw("ins-stage",::light);draw("ins-lunar"){lunar(it,model.purpose==7)};draw("ins-drift",::upper)
        draw("ins-seeing"){val a=v("soil_temperature_0cm");val b=v("temperature_2m");scalar(it,heading("地表温差","Surface ΔT"),(if(a!=null&&b!=null&&a>b)"+"else "")+model.temp(if(a!=null&&b!=null)a-b else null,1,true),model.tempUnit,true)}
        draw("ins-stability"){if(wx?.scene()=="storm")scalar(it,heading("凝结高度 ≈","LCL height ≈"),Units.number(Physics.lcl(v("temperature_2m"),v("dew_point_2m")?:Physics.dew(v("temperature_2m"),v("relative_humidity_2m")))),"m",true)else scalar(it,heading("对流抑制","CIN"),n("convective_inhibition"),"J/kg",true)}
        draw("ins-micro"){micro->
            val temp=v("temperature_2m");val rh=v("relative_humidity_2m");val isVpd=model.purpose in listOf(10,11);val isTrend=model.purpose in listOf(7,9)
            val body=base(micro,when{isVpd->heading("饱和差 VPD","VPD");isTrend->heading("气温变化","Temp. trend");else->heading("湿球温度 ≈","Wet bulb ≈")})
            val mv=when{isVpd->Physics.vpd(temp,rh);isTrend->wx?.let{val a=it.hourly.at("temperature_2m",at);val b=it.hourly.at("temperature_2m",at+7200);if(a!=null&&b!=null)(b-a)/2 else null};else->Physics.wetBulb(temp,rh)}
            value(RectF(body.left,body.top,body.right,body.bottom-26),if(isVpd)Units.number(mv,2)else model.temp(mv,1,isTrend),if(isVpd)"kPa"else model.tempUnit+if(isTrend)"/h"else "",38f)
            note(if(isTrend)heading("2h 温变均值","2h mean change")else if(isVpd)heading("由气温、湿度计算","From air temp. + RH")else heading("Stull 近似","Stull approximation"),body)
        }
        draw("ins-advice"){val body=base(it,heading("环境解读","Conditions summary"),color=0xfff0f0e8.toInt());val content=interpretation(model,wx,at);val textRect=m.rect("ins-advice:advice").takeUnless{it.isEmpty}?:body;paragraph(content,textRect,if(w>=1500)22f else 20f,INK,4)}
        wheel(r("ins-wheel"))
        // The approved paper groups use short inset rules rather than card outlines.
        for(name in listOf("ins-clouds","ins-column","ins-soil","ins-stage","ins-lunar")){val b=r(name);val half=min(168f,b.width()*.66f)/2;line(b.centerX()-half,b.top-3,b.centerX()+half,b.top-3,0xffb8b8ad.toInt(),.7f)}
        gearRect=RectF();paletteRect=RectF();placeRect=RectF()
    }
}
