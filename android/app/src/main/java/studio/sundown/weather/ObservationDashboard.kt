package studio.sundown.weather

import android.content.Context
import android.graphics.*
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import java.time.Instant
import kotlin.math.*

class ObservationDashboard(context:Context,model:AppModel):Dashboard(context,model){
    // Keep the approved full-page composition at every display-density setting.
    // This is one shared coordinate space, not smaller fonts or a taller scroll canvas.
    override val density get()=if(width>0)width/(if(portrait)1136f else 1704f)else resources.displayMetrics.density
    private var wx:Weather?=null
    private var at=0L
    private var title=26f
    private val pad=12f
    // Observation-only contrast refinement; other pages retain their approved palette.
    private val paper=Color.rgb(255,255,251)
    private val ink=Color.rgb(18,20,18)
    private val charcoal=Color.rgb(24,24,23)
    private val frame=Color.rgb(11,11,10)
    private val dialSurface=Color.rgb(14,14,13)
    private val groupSurface=Color.rgb(232,232,224)
    private val pale=Color.rgb(245,245,238)
    private var lunarEvents:Pair<String,List<Pair<Long,Boolean>>>?=null
    private var contentProgress=1f
    private var contentAnimator:ValueAnimator?=null
    val contentAnimating get()=contentAnimator?.isRunning==true
    fun animatePurposeChange(){
        val from=if(contentAnimating)max(.45f,contentProgress)else .45f
        contentAnimator?.cancel()
        if(!ValueAnimator.areAnimatorsEnabled()){contentProgress=1f;invalidate();return}
        contentAnimator=ValueAnimator.ofFloat(from,1f).apply{duration=150;interpolator=LinearInterpolator();addUpdateListener{contentProgress=it.animatedValue as Float;invalidate()};start()}
    }
    override fun onDetachedFromWindow(){contentAnimator?.cancel();super.onDetachedFromWindow()}
    private fun v(k:String)=wx?.value(k,at)
    private fun n(k:String,d:Int=0)=Units.number(v(k),d)
    private fun t(k:String,d:Int=1)=model.temp(v(k),d)
    private fun heading(zh:String,en:String)=tr(zh,en)
    private fun titleText(content:String,x:Float,y:Float,size:Float=26f,fg:Int=ink){text(content,x,y,size,fg,Typeface.create(chinese,450,false))}
    private var activeId=""
    private fun color(css:String,fallback:Int):Int {
        val values=Regex("[0-9.]+").findAll(css).map{it.value.toFloat()}.toList()
        if(values.size<3)return fallback
        val rgb=values.take(3).map{it.toInt()}
        return when(rgb){
            listOf(48,48,45)->charcoal;listOf(32,32,30)->dialSurface;listOf(30,30,28)->frame
            listOf(252,252,248)->paper;listOf(232,232,223)->pale;listOf(240,240,232)->0xfff7f7ef.toInt()
            listOf(32,31,27)->ink;else->Color.rgb(rgb[0],rgb[1],rgb[2])
        }
    }
    private fun measuredText(part:String,content:String,overrideRect:RectF?=null):Float? {
        val m=metrics?:return null;val key="$activeId:$part";val node=m.node(key)?:return null
        val b=RectF(overrideRect?:m.rect(key));if(b.isEmpty)return null
        val module=m.rect(activeId);val right=module.right-12
        if(part=="heading")b.right=right
        else b.right=min(b.right,right)
        val font=when{part=="heading"->chinese;m.font(key).trim().startsWith("MiSans")->chinese;m.font(key).contains("Roboto")->mono;else->languageFont(content)}
        val size=m.size(key)
        val fg=color(node.optString("color"),ink)
        p.reset();p.isAntiAlias=true;p.typeface=Typeface.create(font,m.weight(key),false);p.textSize=size;p.color=fg
        p.textAlign=Paint.Align.LEFT
        // Unicode symbols do not select a different family. Shape the whole string in one run.
        p.letterSpacing=if(part=="reading")0f else m.spacing(key)/size
        if(part in listOf("heading","detail","note")&&p.measureText(content)>b.width()) {
            paragraph(content,RectF(b.left,b.top,right,module.bottom-8),size,fg,2)
        } else {
            val baseline=b.top+(m.line(key)-(p.fontMetrics.descent-p.fontMetrics.ascent))/2-p.fontMetrics.ascent
            c!!.drawText(content,b.left,baseline,p)
        }
        return p.measureText(content)
    }
    private fun note(content:String,b:RectF,dark:Boolean=false){
        if(measuredText("detail",content)==null){
            val font=languageFont(content);val rows=wrapped(content,b.width(),16f,font)
            drawInkRows(rows,b.left,b.bottom-rowsHeight(rows,16f,font)-2,16f,if(dark)paper else ink,font)
        }
    }
    private fun base(b:RectF,name:String,dark:Boolean=false,color:Int?=null):RectF {
        val fg=if(dark)paper else ink;val node=metrics?.node(activeId)
        val background=node?.optString("bg")?.let{this.color(it,color?:if(dark)charcoal else paper)}?:color?:if(dark)charcoal else paper
        if(node!=null){p.reset();p.isAntiAlias=true;p.color=background;c!!.drawPath(metrics!!.outline(activeId,b),p)}else rect(b,background)
        if(measuredText("heading",name)==null)paragraph(name,RectF(b.left+pad,b.top+pad,b.right-pad,b.top+pad+title*2.3f),title,fg,if(model.lang=="en"&&name.length*title*.53>b.width()-24)2 else 1)
        val head=metrics?.rect("$activeId:heading")
        return RectF(b.left+pad,head?.takeUnless{it.isEmpty}?.bottom?.plus(10)?:b.top+pad+title+10,b.right-pad,b.bottom-pad)
    }
    private fun value(b:RectF,number:String,unit:String="",size:Float=42f,dark:Boolean=false){
        val m=metrics!!;val nr=m.rect("$activeId:reading");val ur=m.rect("$activeId:unit")
        val fg=if(dark)paper else ink
        val numberSize=if(nr.isEmpty)size else m.size("$activeId:reading")
        val unitSize=if(ur.isEmpty)max(16f,numberSize*.38f)else m.size("$activeId:unit")
        p.typeface=inter;p.textSize=unitSize;val uw=if(unit.isEmpty())0f else p.measureText(unit)+6
        val left=if(nr.isEmpty)b.left else nr.left
        val right=min(b.right,m.rect(activeId).right-12)
        p.textSize=numberSize;val nw=p.measureText(number)
        val top=if(nr.isEmpty)b.top else nr.top
        if(nw+uw>right-left){
            val ni=inkBounds(number,numberSize,inter);val ui=inkBounds(unit,unitSize,inter)
            val groupHeight=ni.height()+ui.height()+6f
            val start=min(top,m.rect(activeId).bottom-28-groupHeight)
            numberInk(number,unit,RectF(left,start,right,start+groupHeight),numberSize,unitSize,fg)
            return
        }
        text(number,left,top,numberSize,fg,inter)
        p.typeface=inter;p.textSize=numberSize
        text(unit,left+p.measureText(number)+6,top+numberSize*.44f,unitSize,fg,inter)
    }
    private fun scalar(b:RectF,name:String,number:String,unit:String="",dark:Boolean=false){
        if(portrait&&b.width()<240&&b.height()>75){val body=base(b,name,dark,if(dark)charcoal else pale);value(body,number,unit,32f,dark);return}
        val fg=if(dark)paper else ink
        val node=metrics?.node(activeId)
        val bg=node?.optString("bg")?.let{color(it,if(dark)charcoal else pale)}?:if(dark)charcoal else pale
        base(b,"",dark,bg)
        val ns=if(activeId=="ins-optical")27f else 32f
        val us=16f
        p.typeface=inter;p.textSize=ns;val nw=p.measureText(number)
        p.textSize=us;val uw=if(unit.isEmpty())0f else p.measureText(unit)+6
        val x=b.right-12-nw-uw;val rowY=if(activeId=="ins-thermal")b.top+28 else b.centerY();val y=rowY-ns*.58f
        text(number,x,y,ns,fg,inter);text(unit,x+nw+6,y+ns*.44f,us,fg,inter)
        val labelSize=if(w>=1500)26f else 24f
        val font=Typeface.create(chinese,450,false)
        val labels=wrapped(name,max(1f,x-b.left-20),labelSize,font)
        drawInkRows(labels,b.left+12,rowY-rowsHeight(labels,labelSize,font)/2,labelSize,fg,font)
    }
    private fun inkBounds(content:String,size:Float,font:Typeface):Rect {
        p.reset();p.typeface=font;p.textSize=size
        return Rect().also{p.getTextBounds(content,0,content.length,it)}
    }
    private fun inkText(content:String,left:Float,top:Float,size:Float,fg:Int,font:Typeface):RectF {
        val ink=inkBounds(content,size,font)
        p.isAntiAlias=true;p.color=fg
        c!!.drawText(content,left-ink.left,top-ink.top,p)
        return RectF(left,top,left+ink.width(),top+ink.height())
    }
    private fun figure(b:RectF,width:Float):RectF=RectF(b.right-12-width,b.top+b.height()*.06f,b.right-12,b.bottom-b.height()*.06f)
    private fun columnFigure(b:RectF,name:String,preferredWidth:Float,number:String?=null,readingSize:Float?=null):RectF{
        val m=metrics!!;val font=Typeface.create(chinese,m.weight("$activeId:heading"),false)
        p.reset();p.typeface=font;p.textSize=m.size("$activeId:heading")
        val longest=if(name.any{it.code in 0x3400..0x9fff})p.measureText(name.take(4))else name.split(' ').maxOf{p.measureText(it)}
        val numberWidth=number?.let{inkBounds(it,readingSize?:m.size("$activeId:reading"),inter).width().toFloat()}?:0f
        return figure(b,min(preferredWidth,(b.width()-34-max(longest,numberWidth)).coerceAtLeast(56f)))
    }
    private fun wrapped(content:String,width:Float,size:Float,font:Typeface):List<String>{
        p.reset();p.typeface=font;p.textSize=size
        val words=if(content.any{it.code in 0x3400..0x9fff})when(content){
            "太阳高度角"->listOf("太阳","高度角")
            "表层土壤水分"->listOf("表层","土壤水分")
            else->content.map{it.toString()}
        }else content.split(' ')
        val separator=if(content.any{it.code in 0x3400..0x9fff})""else " "
        val result=mutableListOf<String>();var row=""
        for(word in words){
            val candidate=if(row.isEmpty())word else row+separator+word
            if(row.isNotEmpty()&&p.measureText(candidate)>width){result.add(row);row=word}else row=candidate
        }
        if(row.isNotEmpty())result.add(row)
        return result
    }
    private fun rowsHeight(rows:List<String>,size:Float,font:Typeface)=rows.sumOf{inkBounds(it,size,font).height().toDouble()}.toFloat()+4f*(rows.size-1)
    private fun drawInkRows(rows:List<String>,left:Float,top:Float,size:Float,fg:Int,font:Typeface){var y=top;for(row in rows)y=inkText(row,left,y,size,fg,font).bottom+4}
    private data class ColumnInk(val area:RectF,val reading:RectF,val factsTop:Float)
    private fun numberInk(number:String,unit:String,b:RectF,size:Float,unitSize:Float,fg:Int):RectF{
        val nr=inkBounds(number,size,inter);val ur=inkBounds(unit,unitSize,inter)
        p.textSize=size;val nw=p.measureText(number);p.textSize=unitSize;val uw=p.measureText(unit)
        val inline=unit.isEmpty()||nw+uw+6<=b.width()
        val shift=size*.12f
        val top=if(inline)min(nr.top.toFloat(),ur.top-shift)else nr.top.toFloat()
        val bottom=if(inline)max(nr.bottom.toFloat(),ur.bottom-shift)else nr.bottom+6f+ur.height()
        val baseline=b.centerY()-(bottom+top)/2
        p.reset();p.isAntiAlias=true;p.typeface=inter;p.textSize=size;p.color=fg
        c!!.drawText(number,b.left-nr.left,baseline,p)
        val result=RectF(b.left,baseline+nr.top,b.left+nr.width(),baseline+nr.bottom)
        if(unit.isNotEmpty()){
            p.textSize=unitSize
            val x=if(inline)b.left-nr.left+nw+6 else b.left-ur.left
            val y=if(inline)baseline-shift else baseline+nr.bottom+6-ur.top
            c!!.drawText(unit,x,y,p);result.union(RectF(x+ur.left,y+ur.top,x+ur.right,y+ur.bottom))
        }
        return result
    }
    private fun figureColumn(b:RectF,gr:RectF,name:String,number:String?=null,unit:String="",facts:List<String> = emptyList(),dark:Boolean=false,extraFooter:Float=0f,readingSize:Float?=null):ColumnInk {
        val m=metrics!!;val fg=if(dark)paper else ink
        val area=RectF(b.left+12,b.top+b.height()*.075f,gr.left-10,b.bottom-b.height()*.075f)
        val font=Typeface.create(chinese,m.weight("$activeId:heading"),false)
        val size=m.size("$activeId:heading")
        val titleRows=wrapped(name,area.width(),size,font)
        drawInkRows(titleRows,area.left,area.top,size,fg,font)
        val titleBottom=area.top+rowsHeight(titleRows,size,font)
        val factRows=facts.flatMap{fact->
            val face=if(fact.startsWith("Bortle")||fact.startsWith("↑")||fact.startsWith("↓")||fact.startsWith("300 hPa")||fact.startsWith("Δv"))mono else languageFont(fact)
            wrapped(fact,area.width(),16f,face).map{it to face}
        }
        val factHeight=factRows.sumOf{(row,face)->inkBounds(row,16f,face).height().toDouble()}.toFloat()+4f*(factRows.size-1).coerceAtLeast(0)
        val factTop=area.bottom-factHeight
        var y=factTop;for((row,face)in factRows)y=inkText(row,area.left,y,16f,fg,face).bottom+4
        val reading=if(number!=null)numberInk(number,unit,RectF(area.left,titleBottom+8,area.right,factTop-8-extraFooter),readingSize?:m.size("$activeId:reading"),m.size("$activeId:unit"),fg)else RectF()
        return ColumnInk(area,reading,factTop)
    }
    private fun wind(b:RectF,primary:Boolean=false){
        val m=metrics!!;val dark=!primary;val fg=if(dark)paper else ink
        val bg=if(primary)CORAL else charcoal
        base(b,"",dark,bg)
        val headingKey="$activeId:heading"
        val left=m.rect(headingKey).left
        val side=left-b.left
        // A full-height compass and an independent text lane. Captions never take
        // height away from the instrument, and no type is reduced to fit the lane.
        val radius=b.height()*.43f
        val cx=b.right-side-radius;val cy=b.centerY()
        val textRight=cx-radius-12
        val textWidth=textRight-left
        val inset=b.height()*.085f
        val rowGap=5f
        fun rows(content:String,size:Float,font:Typeface):List<String>{
            p.reset();p.typeface=font;p.textSize=size
            val result=mutableListOf<String>();var row=""
            for(word in content.split(' ')){
                val candidate=if(row.isEmpty())word else "$row $word"
                if(row.isNotEmpty()&&p.measureText(candidate)>textWidth){result.add(row);row=word}else row=candidate
            }
            if(row.isNotEmpty())result.add(row)
            return result
        }
        fun height(lines:List<String>,size:Float,font:Typeface)=lines.sumOf{inkBounds(it,size,font).height().toDouble()}.toFloat()+rowGap*(lines.size-1)
        fun drawRows(lines:List<String>,top:Float,size:Float,font:Typeface,color:Int=fg){var y=top;for(row in lines){y=inkText(row,left,y,size,color,font).bottom+rowGap}}
        val kind=if(primary)heading("阵风","Wind gust")else heading("风速","Wind")
        val headingFont=Typeface.create(chinese,m.weight(headingKey),false)
        val headingSize=m.size(headingKey)
        val titleRows=rows(kind,headingSize,headingFont)
        val titleBottom=b.top+inset+height(titleRows,headingSize,headingFont)
        val headingColor=m.node(headingKey)?.optString("color")?.let{color(it,fg)}?:fg
        drawRows(titleRows,b.top+inset,headingSize,headingFont,headingColor)
        val mean=(if(primary)heading("风速","Mean")else heading("阵风","Gust"))+" "+model.speed(v(if(primary)"wind_speed_10m"else "wind_gusts_10m"))+" "+model.windUnit
        val direction=heading("来向","From")+" "+n("wind_direction_10m")+"°"
        val noteSize=18f
        val meanFont=languageFont(mean);val fromFont=languageFont(direction)
        val meanRows=rows(mean,noteSize,meanFont);val fromRows=rows(direction,noteSize,fromFont)
        val meanTop=b.bottom-inset-height(meanRows,noteSize,meanFont)
        val fromTop=meanTop-10-height(fromRows,noteSize,fromFont)
        drawRows(meanRows,meanTop,noteSize,meanFont)
        drawRows(fromRows,fromTop,noteSize,fromFont)

        val number=model.speed(v(if(primary)"wind_gusts_10m"else "wind_speed_10m"))
        val numberSize=m.size("$activeId:reading");val unitSize=m.size("$activeId:unit")
        val ni=inkBounds(number,numberSize,inter);val ui=inkBounds(model.windUnit,unitSize,inter)
        p.typeface=inter;p.textSize=numberSize;val numberAdvance=p.measureText(number)
        p.textSize=unitSize;val unitAdvance=p.measureText(model.windUnit)
        val inline=numberAdvance+6+unitAdvance<=textWidth
        val unitShift=numberSize*.12f
        val groupTop=if(inline)min(ni.top.toFloat(),ui.top-unitShift)else ni.top.toFloat()
        val groupBottom=if(inline)max(ni.bottom.toFloat(),ui.bottom-unitShift)else ni.bottom+6f+ui.height()
        val readingTop=titleBottom+10
        val readingBottom=fromTop-10
        val baseline=(readingTop+readingBottom-(groupBottom-groupTop))/2-groupTop
        p.reset();p.isAntiAlias=true;p.typeface=inter;p.textSize=numberSize;p.color=fg
        c!!.drawText(number,left-ni.left,baseline,p)
        p.textSize=unitSize
        if(inline)c!!.drawText(model.windUnit,left-ni.left+numberAdvance+6,baseline-unitShift,p)
        else c!!.drawText(model.windUnit,left-ui.left,baseline+ni.bottom+6-ui.top,p)

        val tickColor=if(dark)0xffbdbdb1.toInt()else ink
        repeat(72){i->
            val a=i*PI/36
            val major=i%6==0;val half=i%3==0
            val inner=radius*(if(major).82f else if(half).89f else .94f)
            line(cx+cos(a).toFloat()*inner,cy+sin(a).toFloat()*inner,cx+cos(a).toFloat()*radius,cy+sin(a).toFloat()*radius,tickColor,if(major)1.25f else if(half).95f else .7f)
        }
        arrow(cx,cy,radius*.7f,v("wind_direction_10m"),fg)
        if(v("wind_direction_10m")!=null){circle(cx,cy,2.8f,fg);circle(cx,cy,1.1f,bg)}
    }
    private fun pressure(b:RectF){
        val name=heading("气压","Pressure");base(b,"",true)
        val radius=b.height()*.86f
        val sideBySide=b.width()-2*radius-34>=150
        val cx=if(sideBySide)b.right-12-radius else b.centerX()
        val cy=b.top+b.height()*.07f+radius
        val gr=RectF(cx-radius,cy-radius,cx+radius,cy)
        val ring=RectF(cx-radius,cy-radius,cx+radius,cy+radius)
        arc(ring,180f,180f,paper,2.2f)
        repeat(81){i->val a=PI-i*PI/80;val inner=radius*(if(i%10==0).78f else if(i%5==0).84f else .91f)
            line(cx+cos(a).toFloat()*inner,cy-sin(a).toFloat()*inner,cx+cos(a).toFloat()*radius*.97f,cy-sin(a).toFloat()*radius*.97f,paper,if(i%10==0)1.15f else .65f)}
        v("pressure_msl")?.let{q->val a=PI-(q-960).coerceIn(0.0,80.0)/80*PI
            line(cx+cos(a).toFloat()*radius*.73f,cy-sin(a).toFloat()*radius*.73f,cx+cos(a).toFloat()*radius*.98f,cy-sin(a).toFloat()*radius*.98f,CORAL,2.8f)
            circle(cx+cos(a).toFloat()*radius*.98f,cy-sin(a).toFloat()*radius*.98f,2.8f,CORAL)}
        val delta=wx?.difference("pressure_msl",at,3)
        val change=(if(delta!=null&&delta>0)"+"else "")+Units.number(delta,1)+" hPa / 3h"
        if(sideBySide){figureColumn(b,gr,name,n("pressure_msl",1),"hPa",listOf(change),true)}else{
            val face=Typeface.create(chinese,450,false);val hs=metrics!!.size("$activeId:heading")
            val hi=inkBounds(name,hs,face);inkText(name,cx-hi.width()/2,cy-radius*.65f,hs,paper,face)
            val reading=n("pressure_msl",1);val ns=metrics!!.size("$activeId:reading");val us=metrics!!.size("$activeId:unit")
            p.typeface=inter;p.textSize=ns;val numberWidth=p.measureText(reading);p.textSize=us
            val groupWidth=numberWidth+6+p.measureText("hPa")
            numberInk(reading,"hPa",RectF(cx-groupWidth/2,cy-radius*.46f,cx+groupWidth/2+1,cy-radius*.20f),ns,us,paper)
            val ci=inkBounds(change,16f,mono);inkText(change,cx-ci.width()/2,cy-22,16f,paper,mono)
        }
        val labelY=cy-22
        val li=inkBounds("960",16f,mono);val ri=inkBounds("1040",16f,mono)
        inkText("960",cx-radius*.67f-li.width()/2,labelY,16f,paper,mono)
        inkText("1040",cx+radius*.67f-ri.width()/2,labelY,16f,paper,mono)
    }

    private fun dew(b:RectF){
        val name=heading("露点差","Dew-point spread");base(b,"",true)
        val temp=v("temperature_2m");val td=v("dew_point_2m")?:Physics.dew(temp,v("relative_humidity_2m"));val spread=if(temp!=null&&td!=null)temp-td else null
        val number=model.temp(spread,1,true);val gr=columnFigure(b,name,b.width()*.49f,number)
        figureColumn(b,gr,name,number,model.tempUnit,listOf(heading("露点 ","Dew point ")+model.temp(td,1)+model.tempUnit,heading("2h 模型轨迹","2h model trend")),true)
        val points=wx?.hourly?.times?.filter{it in at..at+7200}?.map{epoch->val t=wx?.hourly?.at("temperature_2m",epoch);val d=wx?.hourly?.at("dew_point_2m",epoch);(epoch-at)/7200.0 to if(t!=null&&d!=null)t-d else null}?:emptyList()
        val maximum=max(3.0,ceil(points.mapNotNull{it.second}.maxOrNull()?:3.0))
        c!!.save();c!!.clipRect(gr)
        repeat(5){j->val yy=gr.top+gr.height()*j/4;line(gr.left,yy,gr.right,yy,0x55fcfcf8,.65f)}
        line(gr.left,gr.top,gr.left,gr.bottom,0xff8b8b80.toInt(),.8f)
        if(points.size>=2&&points.all{it.second!=null}){
            val curve=Path();points.forEachIndexed{i,(time,value)->val x=gr.left+time.toFloat()*gr.width();val y=gr.bottom-(value!!/maximum).toFloat()*gr.height();if(i==0)curve.moveTo(x,y)else curve.lineTo(x,y)}
            val fill=Path(curve).apply{lineTo(gr.right,gr.bottom);lineTo(gr.left,gr.bottom);close()}
            c!!.save();c!!.clipPath(fill);for(x in -gr.height().toInt()..gr.width().toInt() step 7)line(gr.left+x,gr.bottom,gr.left+x+gr.height(),gr.top,0x55fcfcf8,.6f);c!!.restore()
            p.reset();p.isAntiAlias=true;p.style=Paint.Style.STROKE;p.strokeWidth=2.2f;p.color=0xffed7c55.toInt();c!!.drawPath(curve,p)
        }
        val threshold=gr.bottom-(2/maximum).toFloat()*gr.height()
        p.reset();p.isAntiAlias=true;p.color=0x99fcfcf8.toInt();p.strokeWidth=.9f;p.pathEffect=DashPathEffect(floatArrayOf(3f,4f),0f);c!!.drawLine(gr.left,threshold,gr.right,threshold,p);p.pathEffect=null
        c!!.restore()
        val label=model.temp(2.0,1,true)+model.tempUnit;val bounds=inkBounds(label,16f,inter)
        rect(RectF(gr.right-bounds.width()-7,threshold-bounds.height()-7,gr.right,threshold-1),charcoal)
        inkText(label,gr.right-bounds.width()-3,threshold-bounds.height()-4,16f,paper,inter)
    }

    private fun humidity(b:RectF,precip:Boolean=false,dark:Boolean=false){
        val name=if(precip)heading("降水概率","Precipitation")else heading("相对湿度","Humidity")
        base(b,"",dark,if(activeId=="ins-focus")CORAL else null)
        val number=if(precip)wx?.precip(at)?.second else v("relative_humidity_2m")
        val value=Units.number(number);val gr=columnFigure(b,name,b.width()*.52f,value)
        figureColumn(b,gr,name,value,"%",listOf(heading("逐小时模型","Hourly model"),"0–100%"),dark)
        val fg=if(dark)paper else ink;val activeColor=if(dark)CORAL else 0xff864731.toInt();val inactive=if(dark)0xff68685e.toInt()else 0xffdedbd2.toInt()
        val top=gr.top;val bottom=gr.bottom-20;val chartHeight=bottom-top
        repeat(42){i->val x=gr.left+3+(gr.width()-6)*i/41;val height=chartHeight*(.20f+.80f*i/41)
            line(x,bottom,x,bottom-height,if(number!=null&&number>0&&i/41.0<=number/100)activeColor else inactive,max(.9f,gr.width()/252*1.8f))}
        line(gr.left,top,gr.left,gr.bottom,if(dark)0xff94948a.toInt()else 0xffb4a89b.toInt(),.7f)
        line(gr.left,bottom,gr.right,bottom,if(dark)0xff94948a.toInt()else 0xffb4a89b.toInt(),.7f)
        for(i in 0..4){val x=gr.left+gr.width()*i/4;line(x,bottom,x,bottom+3,fg,.7f)}
        number?.let{val x=gr.left+3+(gr.width()-6)*it.toFloat()/100
            val triangle=Path().apply{moveTo(x-4,top+1);lineTo(x,top+7);lineTo(x+4,top+1);close()};p.reset();p.color=activeColor;c!!.drawPath(triangle,p)
            p.reset();p.color=activeColor;p.style=Paint.Style.STROKE;p.strokeWidth=.75f;p.pathEffect=DashPathEffect(floatArrayOf(1f,3f),0f);c!!.drawLine(x,top+9,x,bottom,p);p.pathEffect=null}
        inkText("0",gr.left,gr.bottom-12,16f,fg,mono);val end=inkBounds("100%",16f,mono);inkText("100%",gr.right-end.width(),gr.bottom-12,16f,fg,mono)
    }

    private fun clouds(b:RectF){
        val name=heading("云层分布","Cloud layers");base(b,"")
        val gr=columnFigure(b,name,b.width()*.64f)
        figureColumn(b,gr,name,facts=listOf(heading("逐小时模型","Hourly model"),"0–100%"))
        line(gr.left-5,gr.top,gr.left-5,gr.bottom,0xffaaa99d.toInt(),.7f)
        val names=listOf(heading("高云","High"),heading("中云","Mid"),heading("低云","Low"))
        listOf("cloud_cover_high","cloud_cover_mid","cloud_cover_low").forEachIndexed{i,key->
            val row=gr.top+(gr.height()-30)*i/2
            inkText(names[i],gr.left,row,18f,ink,languageFont(names[i]));val value=n(key)+" %";val vi=inkBounds(value,26f,inter);inkText(value,gr.right-vi.width(),row-1,26f,ink,inter)
            val y=if(i==2)gr.bottom-4 else row+28
            val track=RectF(gr.left,y-4,gr.right,y+2);rect(track,0xffdeded7.toInt(),2f)
            v(key)?.let{q->rect(RectF(track.left,track.top,track.left+track.width()*q.toFloat()/100,track.bottom),listOf(0xff864731,0xff756448,0xff373f36)[i].toInt(),2f)}
            repeat(5){j->val x=track.left+track.width()*j/4;line(x,track.bottom+1,x,track.bottom+4,0xff9b9b8f.toInt(),.6f)}
        }
    }

    private fun pwv(b:RectF){
        val name=heading("整层水汽 PWV","Column water");base(b,"")
        val number=n("total_column_integrated_water_vapour",1);val gr=columnFigure(b,name,62f,number)
        figureColumn(b,gr,name,number,"mm",listOf(heading("整层水汽柱","Column water vapour")))
        val bar=RectF(gr.left,gr.top,gr.right-24,gr.bottom)
        rect(bar,0xffe7e5db.toInt());v("total_column_integrated_water_vapour")?.let{q->rect(RectF(bar.left,bar.bottom-bar.height()*(q/70).toFloat().coerceIn(0f,1f),bar.right,bar.bottom),CORAL)}
        repeat(29){i->val y=bar.top+bar.height()*i/28;line(bar.left-(if(i%4==0)5 else 2),y,bar.right,y,0xff76766d.toInt(),if(i%4==0).8f else .5f)}
        inkText("70",gr.right-20,gr.top,16f,ink,inter);val zero=inkBounds("0",16f,inter);inkText("0",gr.right-20,gr.bottom-zero.height(),16f,ink,inter)
    }

    private fun soil(b:RectF){
        val name=heading("表层土壤水分","Surface soil water");base(b,"")
        val number=n("soil_moisture_0_to_1cm",2);val gr=columnFigure(b,name,b.width()*.43f,number)
        figureColumn(b,gr,name,number,"m³/m³",if(model.lang=="zh")listOf("0–1 cm","体积含水率")else listOf("0–1 cm · volumetric"))
        val count=v("soil_moisture_0_to_1cm")?.times(40)?.roundToInt()
        repeat(40){i->val cw=gr.width()/8;val ch=gr.height()/5;val cell=RectF(gr.left+(i%8)*cw+.7f,gr.top+(i/8)*ch+.7f,gr.left+(i%8+1)*cw-.7f,gr.top+(i/8+1)*ch-.7f)
            rect(cell,if(count!=null&&i<count)0xff5a5445.toInt()else 0xffe3e3da.toInt())
            if(count!=null&&i==count-1){line(cell.left,cell.top,cell.right,cell.top,0xffa48163.toInt(),.8f)}}
    }

    private fun solar(b:RectF){
        val name=heading("太阳高度角","Solar elevation");base(b,"")
        val sun=wx?.let{Astronomy.sun(at,it.place)};val radius=b.height()*.43f
        val gr=figure(b,radius+52)
        val elevations=wx?.let{w->(0..12).map{Astronomy.sun(at+it*600,w.place).elevation}}
        val caption=elevations?.let{Units.number(it.first(),1)+"° → "+Units.number(it.last(),1)+"°"}?:"—"
        val miniHeight=min(30f,b.height()*.14f)
        val column=figureColumn(b,gr,name,Units.number(sun?.elevation,1),"°",listOf(caption),extraFooter=miniHeight+8)
        val cx=gr.left+8;val cy=b.centerY()
        arc(RectF(cx-radius,cy-radius,cx+radius,cy+radius),270f,180f,0xff9d9e93.toInt(),.85f)
        line(cx-8,cy,cx+radius+6,cy,0xff8e9086.toInt(),.8f)
        for(deg in -90..90 step 5){val a=Math.toRadians(deg.toDouble());val inner=radius*(if(deg%30==0).86f else if(deg%10==0).92f else .96f)
            line(cx+cos(a).toFloat()*inner,cy-sin(a).toFloat()*inner,cx+cos(a).toFloat()*radius,cy-sin(a).toFloat()*radius,ink,if(deg%30==0)1f else .6f)}
        sun?.let{val a=Math.toRadians(it.elevation);val x=cx+cos(a).toFloat()*radius*.96f;val y=cy-sin(a).toFloat()*radius*.96f
            line(cx,cy,x,y,0xff924b35.toInt(),2f);circle(x,y,3.5f,0xff924b35.toInt());circle(cx,cy,2f,ink)}
        listOf("90°" to gr.top,"0°" to (cy-6),"−90°" to (gr.bottom-12)).forEach{(label,y)->val labelInk=inkBounds(label,16f,inter);inkText(label,gr.right-labelInk.width(),y,16f,ink,inter)}
        if(elevations!=null){
            val lo=elevations.min()-2;val hi=elevations.max()+2
            val rr=RectF(column.area.left,column.factsTop-miniHeight-7,column.area.right,column.factsTop-7)
            line(rr.left,rr.bottom,rr.right,rr.bottom,0xff9d9d90.toInt(),.7f)
            val curve=Path();elevations.forEachIndexed{i,v->val x=rr.left+rr.width()*i/12;val y=rr.bottom-((v-lo)/(hi-lo)).toFloat()*rr.height();if(i==0)curve.moveTo(x,y)else curve.lineTo(x,y)}
            p.reset();p.isAntiAlias=true;p.style=Paint.Style.STROKE;p.strokeWidth=1.3f;p.color=ink;c!!.drawPath(curve,p)
        }
    }

    private fun bearing(b:RectF){
        val name=heading("太阳方位","Solar azimuth");base(b,"",color=pale)
        val az=wx?.let{Astronomy.sun(at,it.place).azimuth}
        val offset=20f;val radius=sqrt((b.height()*.43f).pow(2)+offset*offset)
        val cx=b.right+offset;val cy=b.centerY();val gr=figure(b,radius-offset)
        figureColumn(b,gr,name,Units.number(az),"°")
        circle(cx,cy,radius,CORAL)
        for(i in -18..18){val a=PI+i*PI/72;val outer=radius*.98f;val inner=radius*(if(i%6==0).80f else if(i%3==0).86f else .93f)
            line(cx+cos(a).toFloat()*inner,cy+sin(a).toFloat()*inner,cx+cos(a).toFloat()*outer,cy+sin(a).toFloat()*outer,ink,if(i%6==0)1f else .6f)}
    }

    private fun radiation(b:RectF){
        val name=heading("直射 / 漫射","Direct / diffuse");base(b,"",true)
        val direct=v("direct_radiation_instant");val diffuse=v("diffuse_radiation_instant");val total=if(direct!=null&&diffuse!=null)direct+diffuse else null
        val reading=Units.number(direct)+" / "+Units.number(diffuse);val size=if(portrait)30f else 32f
        val gr=columnFigure(b,name,b.width()*.44f,reading,size)
        val day=total!=null&&total>0;val f=if(day)diffuse!!/total!! else 0.0
        val caption=if(total==null)heading("模型暂无数据","Model unavailable")else if(day)heading("漫射占比 ","Diffuse share ")+Units.number(f*100)+"%"else heading("夜间 · 无太阳短波辐射","Night · no solar flux")
        figureColumn(b,gr,name,reading,"W/m²",listOf(caption),true,readingSize=size)
        val ground=gr.bottom-6
        line(gr.left,ground,gr.right,ground,0xffaba99a.toInt(),.9f)
        repeat(12){i->val x=gr.left+gr.width()*i/12;line(x,ground+2,x+4,gr.bottom,0x66fcfcf8,.65f)}
        if(day){
            val sx=gr.left+10;val sy=gr.top+8
            circle(sx,sy,6f,0xffd99975.toInt())
            repeat(6){i->val x=gr.left+4+gr.width()*.05f*i;val endX=x+gr.width()*.37f
                if(direct!!>0){line(x,gr.top+21,endX,ground-7,0xffd99975.toInt(),(1+(1-f)*1.5).toFloat())
                    line(endX,ground-7,endX-4,ground-13,0xffd99975.toInt(),1f)}}
            if(diffuse!!>0)repeat(7){i->
                val x=gr.left+gr.width()*(.44f+i*.075f);val y=gr.top+8+abs(i-3)*3
                val path=Path().apply{moveTo(x,y);quadTo(gr.right+gr.width()*.08f,gr.centerY(),gr.left+gr.width()*.78f,ground-7)}
                p.reset();p.isAntiAlias=true;p.color=0xffd9d3bb.toInt();p.style=Paint.Style.STROKE;p.strokeWidth=(.7+f*1.5).toFloat();c!!.drawPath(path,p)}
            rect(RectF(gr.left,ground-3,gr.left+gr.width()*(1-f).toFloat(),ground),0xffd99975.toInt())
            rect(RectF(gr.left+gr.width()*(1-f).toFloat(),ground,gr.right,ground+2),0xffd9d3bb.toInt())
        }else{
            line(gr.left,gr.top,gr.right,gr.top,0x66fcfcf8,.75f)
            line(gr.left,gr.top,gr.left,ground,0x40fcfcf8,.7f)
            line(gr.right,gr.top,gr.right,ground,0x40fcfcf8,.7f)
            if(total!=null){circle(gr.centerX(),gr.bottom-21,6f,0xffd99975.toInt());line(gr.centerX()-18,gr.bottom-31,gr.centerX()+18,gr.bottom-31,0x66fcfcf8,.8f)}
        }
    }

    private fun lunar(b:RectF,contextOnly:Boolean=false){
        val focus=activeId=="ins-focus"
        val name=if(focus)heading("月面照明 ≈","Moon illumination ≈")else if(contextOnly)heading("月升落 ≈","Moon crossings ≈")else heading("月相 ≈","Moon phase ≈")
        if(contextOnly){
            val body=base(b,name);text("Bortle —",body.right,b.top+48,16f,ink,mono,Paint.Align.RIGHT)
            if(wx!=null){val date=Instant.ofEpochSecond(at).atZone(wx!!.zone).toLocalDate();val key=date.toString()+wx!!.place.key
                if(lunarEvents?.first!=key)lunarEvents=key to Astronomy.crossings(date,wx!!.place,0.0,true)
                val events=lunarEvents!!.second
                text("↑ "+wx!!.clock(events.find{it.second}?.first)+" ≈",body.left,body.bottom-40,16f,ink,mono)
                text("↓ "+wx!!.clock(events.find{!it.second}?.first)+" ≈",body.left,body.bottom-20,16f,ink,mono)}
            return
        }
        base(b,"",color=if(focus)CORAL else null)
        val moon=wx?.let{Astronomy.moon(at)};val diameter=b.height()*.86f
        val gr=figure(b,diameter)
        val phases=if(model.lang=="zh")listOf("新月","蛾眉月","上弦月","盈凸月","满月","亏凸月","下弦月","残月")else listOf("New","Wax. crescent","First quarter","Wax. gibbous","Full","Wan. gibbous","Last quarter","Wan. crescent")
        val facts=mutableListOf<String>()
        if(focus)facts.add(heading("平均朔望月近似","Mean lunation estimate"))else{
            facts.add(moon?.let{phases[(it.fraction*8).roundToInt()%8]}?:"—");facts.add("Bortle —")
            if(wx!=null){val date=Instant.ofEpochSecond(at).atZone(wx!!.zone).toLocalDate();val key=date.toString()+wx!!.place.key
                if(lunarEvents?.first!=key)lunarEvents=key to Astronomy.crossings(date,wx!!.place,0.0,true)
                val events=lunarEvents!!.second
                facts.add("↑ "+wx!!.clock(events.find{it.second}?.first)+" ≈");facts.add("↓ "+wx!!.clock(events.find{!it.second}?.first)+" ≈")}
        }
        figureColumn(b,gr,name,Units.number(moon?.illumination?.times(100)),"%",facts)
        val radius=diameter/2;val x=gr.centerX();val y=gr.centerY()
        circle(x,y,radius,0xff35352f.toInt())
        if(moon!=null){val path=Path();val waxing=moon.fraction<=.5;val cosPhase=cos(2*PI*moon.fraction)
            for(i in 0..120){val yy=-radius+2*radius*i/120;val limb=sqrt(max(0f,radius*radius-yy*yy));val xx=if(waxing)limb else -limb;if(i==0)path.moveTo(x+xx,y+yy)else path.lineTo(x+xx,y+yy)}
            for(i in 120 downTo 0){val yy=-radius+2*radius*i/120;val limb=sqrt(max(0f,radius*radius-yy*yy));val xx=cosPhase*limb*(if(waxing)1 else -1);path.lineTo(x+xx.toFloat(),y+yy)}
            path.close();p.reset();p.isAntiAlias=true;p.color=0xfffff4c9.toInt();c!!.drawPath(path,p)}
    }

    private fun upper(b:RectF){
        val name=heading("高空风 / 风差","Upper wind / Δv");base(b,"",true)
        val upper=v("wind_speed_300hPa");val surface=v("wind_speed_10m");val largest=max(1.0,max(upper?:0.0,surface?:0.0))
        val number=model.speed(upper);val gr=columnFigure(b,name,b.width()*.39f,number)
        val delta=Physics.windDelta(surface,v("wind_direction_10m"),upper,v("wind_direction_300hPa"))
        figureColumn(b,gr,name,number,model.windUnit,listOf("300 hPa · "+n("wind_direction_300hPa")+"°","Δv "+model.speed(delta)+" "+model.windUnit),true)
        val radius=min(gr.width()/2-2,gr.height()*.225f)
        val centers=listOf(gr.top+radius,gr.bottom-radius)
        centers.forEach{y->
            line(gr.centerX(),y-radius,gr.centerX(),y+radius,0xff77776b.toInt(),.65f)
            line(gr.centerX()-radius,y,gr.centerX()+radius,y,0xff77776b.toInt(),.65f)
            for(k in 0..3){val a=k*PI/2;line(gr.centerX()+cos(a).toFloat()*radius*.85f,y+sin(a).toFloat()*radius*.85f,gr.centerX()+cos(a).toFloat()*radius,y+sin(a).toFloat()*radius,0xffa7a79c.toInt(),.8f)}
        }
        if(upper!=null&&upper>0)arrow(gr.centerX(),centers[0],radius*(upper/largest*.88).toFloat(),v("wind_direction_300hPa")?.plus(180),CORAL)
        else if(upper==0.0)circle(gr.centerX(),centers[0],1.8f,CORAL)
        if(surface!=null&&surface>0)arrow(gr.centerX(),centers[1],radius*(surface/largest*.88).toFloat(),v("wind_direction_10m")?.plus(180),paper)
        else if(surface==0.0)circle(gr.centerX(),centers[1],1.8f,paper)
    }

    private fun light(b:RectF){
        rect(b,paper)
        val label=heading("光影倒计时","Light timer")
        titleText(label,b.left+12,b.top+10)
        val w=wx
        val now=model.now
        val target=w?.let{model.light(it)}
        val sec=target?.epoch?.minus(now)?.coerceAtLeast(0)
        val count=sec?.let{String.format(java.util.Locale.US,"%02d:%02d:%02d",it/3600,(it%3600)/60,it%60)}?:"—"
        val phase=when(target?.kind){"blue"->heading("蓝调时段","Blue hour");"dawn"->heading("晨光时段","Dawn window");"end"->heading("时段结束","Window ends");"golden"->heading("黄金时刻","Golden hour");else->heading("无对应事件","No event")}
        text(count,b.left+12,b.bottom-47,32f,ink,inter)
        text(phase,b.right-12,b.top+12,16f,ink,languageFont(phase),Paint.Align.RIGHT)
        text((w?.clock(target?.epoch)?:"—")+" ≈",b.right-12,b.bottom-30,16f,ink,mono,Paint.Align.RIGHT)
    }
    private fun forecastChart(gr:RectF,key:String,maximum:Double,dark:Boolean){
        val fg=if(dark)paper else ink;val muted=if(dark)0xff77776c.toInt()else 0xffb4b1a5.toInt()
        val points=wx?.hourly?.range(key,at,at+7200)?:emptyList()
        val scale=if(key=="precipitation")max(maximum,points.mapNotNull{it.second}.maxOrNull()?:0.0)*1.1 else maximum
        val barWidth=max(2f,gr.width()/22)
        val plot=RectF(gr.left+barWidth/2+2,gr.top+6,gr.right-barWidth/2-2,gr.bottom-26)
        c!!.save();c!!.clipRect(gr.left,gr.top,gr.right,plot.bottom+1)
        repeat(5){j->val y=plot.top+plot.height()*j/4;line(plot.left,y,plot.right,y,muted,.85f)}
        repeat(5){j->val x=plot.left+plot.width()*j/4;line(x,plot.top,x,plot.bottom,muted,.6f)}
        for((epoch,value)in points)if(value!=null){val x=plot.left+(epoch-at)/7200f*plot.width();val y=plot.bottom-(value/scale).toFloat().coerceIn(0f,1f)*plot.height()
            line(x,plot.bottom,x,y,if(dark)CORAL else 0xff864731.toInt(),barWidth)
        }
        line(plot.left,plot.bottom,plot.right,plot.bottom,muted,.85f);c!!.restore()
        val start=wx?.clock(at)?:"—";val end=wx?.clock(at+7200)?:"—"
        inkText(start,gr.left,plot.bottom+8,16f,fg,mono);val ei=inkBounds(end,16f,mono);inkText(end,gr.right-ei.width(),plot.bottom+8,16f,fg,mono)
    }
    private fun rain(b:RectF,totalB:RectF){
        c!!.save();c!!.clipPath(metrics!!.outline(activeId,b))
        val name=heading("短时降水","Rain trend");base(b,"",true)
        val samples=wx?.hourly?.times?.filter{it>at&&it<=at+7200}?:emptyList()
        val values=samples.map{wx?.hourly?.at("precipitation",it)}
        val last=wx?.hourly?.at("precipitation",at);val reading=Units.number(last,1)
        val gr=columnFigure(b,name,b.width()*.48f,reading,30f)
        figureColumn(b,gr,name,reading,"mm/h",listOf(heading("每小时模型 · 非雷达","Hourly model · not radar")),true,readingSize=30f)
        forecastChart(gr,"precipitation",max(1.0,values.filterNotNull().maxOrNull()?:1.0),true)
        c!!.restore()
        val total=if(values.size==2&&values.all{it!=null})values.sumOf{it!!}else null
        activeId="ins-rain-total";scalar(totalB,heading("2h 累计降水","2h rain total"),Units.number(total,1),"mm",true)
    }
    private fun primary(b:RectF){
        when(model.purpose){
            2->{wind(b,true);return}
            7->{lunar(b);return}
            10,11->{humidity(b);return}
        }
        val kind=when(model.purpose){4,5,6->"glow";8->"visibility";9,3->"temperature";else->"precipitation"}
        val name=when(kind){"glow"->heading("霞光条件指数","Glow condition score");"visibility"->heading("能见度","Visibility");"temperature"->heading("气温","Temp.");else->heading("降水概率","Precipitation")}
        base(b,"",color=CORAL)
        val q=when(kind){"glow"->Physics.glow(v("cloud_cover_high"),v("cloud_cover_mid"),v("cloud_cover_low"),v("visibility"),v("relative_humidity_2m"));"visibility"->v("visibility")?.div(1000);"temperature"->Units.temperature(v("temperature_2m"),model.fahrenheit);else->wx?.precip(at)?.second}
        val number=Units.number(q,if(kind in listOf("temperature","visibility"))1 else 0)
        val unit=when(kind){"glow"->"/100";"visibility"->"km";"temperature"->model.tempUnit;else->"%"}
        if(kind=="visibility"){
            val m=metrics!!;val headingSize=m.size("$activeId:heading");val face=Typeface.create(chinese,450,false)
            val readingLeft=m.rect("$activeId:reading").left
            val hi=inkText(name,b.left+12,b.top+b.height()*.15f,headingSize,ink,face)
            val rows=wrapped(heading("逐小时模型","Hourly model"),readingLeft-b.left-28,16f,languageFont(heading("逐小时模型","Hourly model")))
            drawInkRows(rows,b.left+12,hi.bottom+9,16f,ink,languageFont(heading("逐小时模型","Hourly model")))
            numberInk(number,unit,RectF(readingLeft,b.top+6,b.right-12,b.bottom-6),m.size("$activeId:reading"),m.size("$activeId:unit"),ink)
            return
        }
        val gr=if(kind=="precipitation")columnFigure(b,name,b.width()*.48f,number)else columnFigure(b,name,82f,number)
        val fact=if(kind=="glow")heading("云层与能见度推算","From cloud + visibility")else heading("逐小时模型预报","Hourly model forecast")
        figureColumn(b,gr,name,number,unit,listOf(fact))
        if(kind=="precipitation"){forecastChart(gr,"precipitation_probability",100.0,false);return}
        val bar=RectF(gr.left+8,gr.top,gr.left+27,gr.bottom)
        rect(bar,charcoal,2f)
        val position=if(kind=="glow")q?.div(100)else{
            val day=wx?.today(at);val lo=day?.low;val hi=day?.high
            v("temperature_2m")?.let{temp->if(lo!=null&&hi!=null){if(hi>lo)(temp-lo)/(hi-lo)else .5}else null}
        }
        repeat(21){i->val y=bar.top+bar.height()*i/20;line(bar.right+2,y,bar.right+(if(i%5==0)8 else 5),y,ink,if(i%5==0).9f else .6f)}
        position?.let{val y=bar.bottom-bar.height()*it.toFloat().coerceIn(0f,1f);line(bar.left-4,y,bar.right+4,y,paper,3f)}
        val top=if(kind=="glow")"100"else model.temp(wx?.today(at)?.high,0)+"°"
        val bottom=if(kind=="glow")"0"else model.temp(wx?.today(at)?.low,0)+"°"
        inkText(top,bar.right+11,gr.top,16f,ink,inter);val bi=inkBounds(bottom,16f,inter);inkText(bottom,bar.right+11,gr.bottom-bi.height(),16f,ink,inter)
    }
    private fun wheel(b:RectF){
        dialRect=b;val outline=metrics!!.outline("ins-wheel",b)
        p.reset();p.isAntiAlias=true;p.color=dialSurface;c!!.drawPath(outline,p)
        p.color=0xff515570.toInt();p.style=Paint.Style.STROKE;p.strokeWidth=.8f;c!!.drawPath(outline,p)
        c!!.save();c!!.clipRect(RectF(b.left,b.top+16,b.right,b.bottom-44))
        val en=model.lang=="en";val radius=b.height()*.70f;val labelX=b.width()*(if(en).29f else .35f);val centerX=b.left+labelX-radius;val centerY=b.centerY()
        val font=max(if(en)18f else 24f,min(if(en)32f else 44f,b.width()*(if(en).069f else .09f)))
        val outer=radius+b.width()*.49f
        arc(RectF(centerX-outer,centerY-outer,centerX+outer,centerY+outer),-75f,150f,0xff73736a.toInt(),.8f)
        for(step in -40..40){val angle=(step/4.0+dialPhase)*8.5;val a=Math.toRadians(angle);val r=radius+b.width()*.43f;val len=if(step%4==0)8f else 3f;line(centerX+cos(a).toFloat()*(r-len),centerY+sin(a).toFloat()*(r-len),centerX+cos(a).toFloat()*r,centerY+sin(a).toFloat()*r,if(step%4==0)0xffc4c4b9.toInt()else 0xff78786f.toInt(),.8f)}
        for(offset in -6..6){val index=((model.purpose+offset)%12+12)%12;val distance=offset+dialPhase;val angle=distance*8.5f;val a=Math.toRadians(angle.toDouble());val x=centerX+cos(a).toFloat()*radius;val y=centerY+sin(a).toFloat()*radius
            val emphasis=(1-abs(distance)).coerceIn(0f,1f);val fade=(1-(abs(angle)-42).coerceAtLeast(0f)/20).coerceIn(0f,1f)
            val color=androidx.core.graphics.ColorUtils.blendARGB(0xffcecec4.toInt(),paper,emphasis).let{androidx.core.graphics.ColorUtils.setAlphaComponent(it,(255*fade).roundToInt())}
            val label=model.purposeName(index);val face=Typeface.create(chinese,450,false)
            var labelSize=font*(1+emphasis*.035f)
            val safe=RectF(b.left+12,b.top+18,b.right-12,b.bottom-46)
            fun ink():RectF{
                p.reset();p.typeface=face;p.textSize=labelSize
                val bounds=Rect();p.getTextBounds(label,0,label.length,bounds)
                return RectF(bounds).apply{offset(x,y-font*.55f-p.fontMetrics.ascent);Matrix().apply{setRotate(angle,x,y)}.mapRect(this)}
            }
            var bounds=ink()
            if(bounds.width()>safe.width()){labelSize*=safe.width()/bounds.width();bounds=ink()}
            val shift=if(bounds.left<safe.left)safe.left-bounds.left else if(bounds.right>safe.right)safe.right-bounds.right else 0f
            bounds.offset(shift,0f)
            if(bounds.top<safe.top||bounds.bottom>safe.bottom)continue
            val edge=min(bounds.top-safe.top,safe.bottom-bounds.bottom).div(12).coerceIn(0f,1f)
            val wholeLabelColor=androidx.core.graphics.ColorUtils.setAlphaComponent(color,(Color.alpha(color)*edge).roundToInt())
            rect(RectF(bounds).apply{inset(-2f,-2f)},dialSurface)
            c!!.save();c!!.translate(shift,0f);c!!.rotate(angle,x,y);text(label,x,y-font*.55f,labelSize,wholeLabelColor,face);c!!.restore()}
        circle(b.left+b.width()*.12f,centerY,max(10f,min(16f,b.width()*.043f)),0xff2459ff.toInt());c!!.restore()
        text(heading("拖动选择","DRAG"),b.left+16,b.bottom-28,16f,0xffcecec4.toInt());text(String.format(java.util.Locale.US,"%02d / 12",model.purpose+1),b.right-16,b.bottom-28,16f,0xffcecec4.toInt(),mono,Paint.Align.RIGHT)
    }
    private var metricsKey=""
    private var metrics:DesignMetrics?=null
    override fun drawDashboard(){
        wx=model.weather;val win=wx?.let{model.window(it)};at=win?.start?:System.currentTimeMillis()/1000
        title=if(w>=1500)26f else 24f
        val key="observation-${if(portrait)"portrait" else "landscape"}-${model.lang}-${model.purpose}"
        if(metricsKey!=key){metrics=DesignMetrics(context,key);metricsKey=key}
        val m=metrics!!;m.configure(w,h,(context as MainActivity).root.paddingTop/density)
        fun r(name:String)=m.rect(name)
        fun draw(name:String,action:(RectF)->Unit){
            val b=r(name);val previous=activeId;activeId=name;c!!.save();c!!.clipPath(m.outline(name,b))
            val animated=contentProgress<1f&&name in setOf("ins-focus","ins-thermal","ins-optical","ins-micro","ins-moisture","ins-wind","ins-advice")
            if(animated){
                p.reset();p.color=color(m.node(name)?.optString("bg")?:"",paper);c!!.drawRect(b,p)
                c!!.saveLayerAlpha(b,(contentProgress*255).roundToInt());c!!.translate(0f,(1-contentProgress)*4)
            }
            action(b);if(animated)c!!.restore();c!!.restore();activeId=previous
        }
        rect(RectF(0f,0f,w,h),frame)
        val panorama=r("ins-panorama");p.reset();p.isAntiAlias=true;p.color=paper;c!!.drawPath(m.outline("ins-panorama",panorama),p)
        val rim=5f*m.sx
        artRect=RectF(panorama).apply{inset(rim,rim)}
        artCornerRadius=max(0f,(m.node("ins-panorama")?.optJSONArray("radii")?.optDouble(0,26.0)?.toFloat()?:26f)*m.sx-rim)
        draw("ins-focus",::primary)
        val ctx=r("ins-context");val wxx=wx
        val date=wxx?.let{Instant.ofEpochSecond(at).atZone(it.zone).toLocalDate()}
        val name=model.placeName()
        val location=name+(date?.let{" · ${it.monthValue}/${it.dayOfMonth}"}?:"")
        val window=if(win?.available==true)wxx!!.clock(win.start)+"–"+wxx.clock(win.end)+(if(Instant.ofEpochSecond(win.start).atZone(wxx.zone).toLocalDate()!=Instant.ofEpochSecond(win.end).atZone(wxx.zone).toLocalDate())" +1"else "")else "—"
        val contextSize=if(portrait)25f else 22f
        p.reset();p.typeface=languageFont(location);p.textSize=contextSize;val locationWidth=p.measureText(location)
        p.typeface=inter;val windowWidth=p.measureText(window)
        if(locationWidth+windowWidth+24>ctx.width()){
            val size=if(ctx.height()>=72)20f else 16f
            fit(location,ctx.left+8,ctx.top,ctx.width()-16,size,paper)
            text(window,ctx.left+8,ctx.top+size*1.2f,size,paper,inter)
        }else{
            fit(location,ctx.left+8,ctx.top+3,ctx.width()-windowWidth-32,contextSize,paper)
            text(window,ctx.right-8,ctx.top+3,contextSize,paper,inter,Paint.Align.RIGHT)
        }
        fit(model.source(),ctx.left+8,ctx.bottom-20,ctx.width()-16,16f,paper)
        val thermal=if(model.purpose in listOf(3,9))"apparent_temperature"else "temperature_2m"
        draw("ins-thermal"){
            val label=if(thermal=="temperature_2m")heading("气温","Temp.")else heading("体感","Feels like")
            if(portrait){val body=base(it,label,color=pale);value(body,t(thermal),model.tempUnit,32f)}else scalar(it,label,t(thermal),model.tempUnit)
            if(!m.rect("ins-thermal:graphic").isEmpty){
            val points=listOfNotNull(v(thermal))+(wx?.hourly?.range(thermal,at,at+7200)?.mapNotNull{pair->pair.second}?:emptyList())
            val lo=points.minOrNull();val hi=points.maxOrNull()
            val rail=RectF(it.left+12,it.bottom-42,it.right-12,it.bottom-33)
            rect(rail,charcoal);v(thermal)?.let{temp->if(lo!=null&&hi!=null){val x=rail.left+rail.width()*(if(hi>lo)((temp-lo)/(hi-lo)).coerceIn(0.0,1.0)else .5).toFloat();line(x,rail.top-2,x,rail.bottom+2,paper,2f)}}
            text((if(rail.width()<150)""else "L ")+model.temp(lo,1)+"°",rail.left,it.bottom-29,16f);text((if(rail.width()<150)""else "H ")+model.temp(hi,1)+"°",rail.right,it.bottom-29,16f,ink,inter,Paint.Align.RIGHT)
            }
        }
        draw("ins-optical"){
            if(model.purpose in 4..6){
                if(it.height()<96){
                    base(it,"",color=pale)
                    titleText(heading("色温","CCT"),it.left+12,it.top+5,24f)
                    text("— K",it.right-12,it.top+5,24f,ink,inter,Paint.Align.RIGHT)
                    fit(heading("未测量","Unmeasured"),it.left+12,it.bottom-22,it.width()-24,16f)
                }else{
                    val body=base(it,heading("色温","CCT"),color=pale);value(body,"—","K",27f);note(heading("未测量","Unmeasured"),body)
                }
            }else if(portrait){val body=base(it,"PM2.5",color=pale);value(body,Units.number(wx?.pm25),"μg/m³",30f);note(wx?.airAt?.let{epoch->"CAMS · "+wx!!.clock(epoch)}?:heading("空气模型暂无数据","Air model unavailable"),body)}
            else scalar(it,"PM2.5",Units.number(wx?.pm25),"μg/m³")
        }
        draw("ins-freezing"){scalar(it,heading("零度层","0°C level"),n("freezing_level_height"),"m")}
        draw("ins-signal"){if(wx?.scene()=="storm"){val body=base(it,heading("对流抑制","CIN"));value(body,n("convective_inhibition"),"J/kg",32f);note(heading("模型能量","Model energy"),body)}else{val body=base(it,heading("凝结高度 ≈","LCL height ≈"));value(body,Units.number(Physics.lcl(v("temperature_2m"),v("dew_point_2m")?:Physics.dew(v("temperature_2m"),v("relative_humidity_2m")))),"m",32f);note(heading("距地高度 · 近似","AGL · approx."),body)}}
        draw("ins-cape"){val body=base(it,heading("对流位能","CAPE"));value(body,n("cape"),"J/kg",32f);note(heading("模型能量","Model energy"),body)}
        activeId="ins-rain";rain(r("ins-rain"),r("ins-rain-total"));activeId=""
        val assembly=r("ins-assembly");p.reset();p.isAntiAlias=true;p.color=groupSurface;c!!.drawPath(m.outline("ins-assembly",assembly),p)
        draw("ins-wind"){if(model.purpose==2)humidity(it,true,true)else wind(it)}
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
        draw("ins-advice"){val body=base(it,heading("环境解读","Conditions summary"),color=0xfff0f0e8.toInt());val content=interpretation(model,wx,at);val textRect=(m.rect("ins-advice:advice").takeUnless{it.isEmpty}?:body).apply{right=body.right;bottom=body.bottom};paragraph(content,textRect,if(w>=1500)22f else 20f,ink,4)}
        wheel(r("ins-wheel"))
        // The approved paper groups use short inset rules rather than card outlines.
        for(name in listOf("ins-clouds","ins-column","ins-soil","ins-stage","ins-lunar")){val b=r(name);val half=min(168f,b.width()*.66f)/2;line(b.centerX()-half,b.top-3,b.centerX()+half,b.top-3,0xff9d9d90.toInt(),.7f)}
        gearRect=RectF();paletteRect=RectF();placeRect=RectF()
    }
}
