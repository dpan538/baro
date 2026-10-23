package studio.sundown.weather

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.text.StaticLayout
import android.text.Layout
import android.view.*
import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import kotlin.math.*

val PAPER=Color.rgb(252,252,248)
val INK=Color.rgb(29,31,29)
val CHARCOAL=Color.rgb(38,38,36)
val FRAME=Color.rgb(22,22,21)
val CORAL=Color.rgb(203,136,112)
val BLUE=Color.rgb(21,156,255)
val ORANGE=Color.rgb(255,122,26)
val YELLOW=Color.rgb(255,220,31)

abstract class Dashboard(context:Context,val model:AppModel):View(context){
    protected val density=resources.displayMetrics.density
    protected val p=Paint(Paint.ANTI_ALIAS_FLAG)
    protected val inter=Typeface.createFromAsset(context.assets,"fonts/Inter.ttf")
    protected val mono=Typeface.createFromAsset(context.assets,"fonts/RobotoMono.ttf")
    protected val chinese=Typeface.createFromAsset(context.assets,"fonts/MiSansVF.ttf")
    protected var c:Canvas?=null
    var artRect=RectF();protected set
    var artCornerRadius=0f;protected set
    var frameOutline:Path?=null;protected set
    var dialRect=RectF();protected set
    var gearRect=RectF();protected set
    var paletteRect=RectF();protected set
    var placeRect=RectF();protected set
    var onSettings:(()->Unit)?=null
    var onPlace:(()->Unit)?=null
    var onPurpose:(()->Unit)?=null
    private var down=PointF();private var dialStart=0
    private var dialMoved=false
    var dialDragging=false;private set
    private var pressed=""
    private val pressedBounds=RectF()
    private var pressAlpha=0f
    private var pressAnimator:ValueAnimator?=null
    private fun press(to:Float){pressAnimator?.cancel();if(!ValueAnimator.areAnimatorsEnabled()){pressAlpha=to;invalidate();return};pressAnimator=ValueAnimator.ofFloat(pressAlpha,to).apply{duration=if(to>0)65 else 110;interpolator=LinearInterpolator();addUpdateListener{pressAlpha=it.animatedValue as Float;invalidate()};start()}}
    private val detents=DialDetents()
    private var dialAnimator:ValueAnimator?=null
    private var lastTick=0L
    var detentFeedbackCount=0;private set
    protected val dialPhase get()=if(dialDragging||dialAnimator?.isRunning==true)detents.selected-detents.position else 0f
    val dialSettling get()=dialAnimator?.isRunning==true
    private fun selectPurpose(index:Int,tick:Boolean=true){
        val value=((index%12)+12)%12
        if(model.purpose==value)return
        model.purpose=value
        if(tick){detentFeedbackCount++;val now=SystemClock.uptimeMillis();if(now-lastTick>=35){
            val effect=if(android.os.Build.VERSION.SDK_INT>=34){if(now-lastTick<90)HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.SEGMENT_TICK}else HapticFeedbackConstants.CLOCK_TICK
            performHapticFeedback(effect);lastTick=now
        }}
        onPurpose?.invoke();invalidate()
    }
    private fun settleDial(){
        dialAnimator?.cancel()
        if(!ValueAnimator.areAnimatorsEnabled()){detents.visual(detents.selected.toFloat());invalidate();return}
        dialAnimator=ValueAnimator.ofFloat(detents.position,detents.selected.toFloat()).apply{duration=110;interpolator=LinearInterpolator();addUpdateListener{detents.visual(it.animatedValue as Float);invalidate()};start()}
    }
    private fun stepDial(delta:Int){if(delta==0)return;dialAnimator?.cancel();detents.begin(model.purpose);val from=detents.position;detents.move(delta.toFloat());selectPurpose(detents.index);detents.visual(from);settleDial()}
    val w get()=width/density
    val h get()=height/density
    val portrait get()=height>width
    protected val tr get()=model::tr
    protected fun languageFont(s:String)=if(s.any{it.code in 0x3400..0x9fff})chinese else inter
    protected fun text(s:String,x:Float,y:Float,size:Float,color:Int=INK,font:Typeface=chinese,align:Paint.Align=Paint.Align.LEFT){
        p.reset();p.isAntiAlias=true;p.color=color;p.textSize=size;p.typeface=if(font==chinese)languageFont(s)else font;p.textAlign=align
        c!!.drawText(s,x,y-p.fontMetrics.ascent,p)
    }
    protected fun fit(s:String,x:Float,y:Float,maxWidth:Float,size:Float,color:Int=INK,font:Typeface=chinese){
        p.reset();p.typeface=if(font==chinese)languageFont(s)else font;p.textSize=size
        val width=p.measureText(s)
        val fitted=if(width>maxWidth&&width>0)size*maxWidth.coerceAtLeast(1f)/width else size
        text(s,x,y,fitted,color,font)
    }
    protected fun paragraph(s:String,b:RectF,size:Float,color:Int=INK,maxLines:Int=4){
        val tp=TextPaint(Paint.ANTI_ALIAS_FLAG).apply{this.color=color;textSize=size;typeface=languageFont(s)}
        val layout=StaticLayout.Builder.obtain(s,0,s.length,tp,max(1,b.width().toInt())).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setMaxLines(maxLines).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
        c!!.save();c!!.clipRect(b);c!!.translate(b.left,b.top);layout.draw(c);c!!.restore()
    }
    protected fun rect(r:RectF,color:Int,radius:Float=0f){p.reset();p.isAntiAlias=true;p.color=color;c!!.drawRoundRect(r,radius,radius,p)}
    protected fun line(x:Float,y:Float,xx:Float,yy:Float,color:Int=INK,stroke:Float=1f){p.reset();p.isAntiAlias=true;p.color=color;p.strokeWidth=stroke;c!!.drawLine(x,y,xx,yy,p)}
    protected fun circle(x:Float,y:Float,r:Float,color:Int){p.reset();p.isAntiAlias=true;p.color=color;c!!.drawCircle(x,y,r,p)}
    protected fun arc(r:RectF,start:Float,sweep:Float,color:Int,stroke:Float=1f){p.reset();p.isAntiAlias=true;p.style=Paint.Style.STROKE;p.color=color;p.strokeWidth=stroke;c!!.drawArc(r,start,sweep,false,p)}
    protected fun gear(r:RectF){
        val x=r.centerX();val y=r.centerY();val radius=min(r.width(),r.height())*.27f
        circle(x,y,radius,INK);circle(x,y,radius*.67f,PAPER);circle(x,y,radius*.27f,INK)
        repeat(8){val a=it*PI/4;val dx=cos(a).toFloat();val dy=sin(a).toFloat();line(x+dx*radius*.9f,y+dy*radius*.9f,x+dx*radius*1.25f,y+dy*radius*1.25f,INK,radius*.23f)}
    }
    protected fun dots(r:RectF){val radius=min(7f,r.height()/8);listOf(ORANGE,YELLOW,BLUE).forEachIndexed{i,col->circle(r.left+12+i*(radius*2+6),r.centerY(),radius,col)}}
    protected fun arrow(x:Float,y:Float,r:Float,from:Double?,color:Int){
        if(from==null)return
        val a=Math.toRadians(from-90);val dx=cos(a).toFloat();val dy=sin(a).toFloat()
        line(x-dx*r*.7f,y-dy*r*.7f,x+dx*r,y+dy*r,color,2f)
        val endX=x+dx*r;val endY=y+dy*r
        line(endX,endY,endX-dx*9-dy*4,endY-dy*9+dx*4,color,2f);line(endX,endY,endX-dx*9+dy*4,endY-dy*9-dx*4,color,2f)
    }
    override fun onDraw(canvas:Canvas){c=canvas;canvas.save();canvas.scale(density,density);drawDashboard();if(pressAlpha>0f&&!pressedBounds.isEmpty)rect(pressedBounds,((22*pressAlpha).roundToInt() shl 24),8f);canvas.restore()}
    abstract fun drawDashboard()
    override fun onKeyDown(keyCode:Int,event:KeyEvent):Boolean {
        if(dialRect.isEmpty)return super.onKeyDown(keyCode,event)
        val next=when(keyCode){KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT->model.purpose+1;KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_LEFT->model.purpose-1;KeyEvent.KEYCODE_MOVE_HOME->0;KeyEvent.KEYCODE_MOVE_END->11;else->return super.onKeyDown(keyCode,event)}
        var delta=next-model.purpose;if(delta>6)delta-=12 else if(delta< -6)delta+=12
        stepDial(delta);return true
    }
    override fun onGenericMotionEvent(e:MotionEvent):Boolean {
        if(e.actionMasked==MotionEvent.ACTION_SCROLL&&dialRect.contains(e.x/density,e.y/density)){val delta=e.getAxisValue(MotionEvent.AXIS_VSCROLL);if(delta!=0f)stepDial(if(delta>0)-1 else 1);return true};return super.onGenericMotionEvent(e)
    }
    override fun onTouchEvent(e:MotionEvent):Boolean{
        val x=e.x/density;val y=e.y/density
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{down=PointF(x,y);dialDragging=dialRect.contains(x,y);dialMoved=false;dialStart=model.purpose;if(dialDragging){dialAnimator?.cancel();detents.begin(model.purpose);isFocusable=true;isFocusableInTouchMode=true;requestFocus()};pressed=when{gearRect.contains(x,y)->"gear";paletteRect.contains(x,y)->"palette";placeRect.contains(x,y)->"place";else->""};pressedBounds.set(when(pressed){"gear"->gearRect;"palette"->paletteRect;"place"->placeRect;else->RectF()});press(if(pressed.isNotEmpty())1f else 0f);isPressed=true;invalidate();return true}
            MotionEvent.ACTION_POINTER_DOWN,MotionEvent.ACTION_CANCEL->{if(dialDragging){val position=detents.position;detents.cancel();detents.visual(position);selectPurpose(dialStart,false);settleDial()};dialDragging=false;pressed="";press(0f);isPressed=false;invalidate();return true}
            MotionEvent.ACTION_MOVE->{if(dialDragging){if(abs(down.y-y)>7)dialMoved=true;val travel=(down.y-y)/max(38f,(dialRect.height()*.70f*Math.toRadians(8.5)).toFloat());val crossed=detents.move(travel);if(crossed.isNotEmpty())selectPurpose(crossed.last());invalidate()}else if(pressed.isNotEmpty()&&!pressedBounds.contains(x,y)){pressed="";press(0f)};return true}
            MotionEvent.ACTION_UP->{if(dialDragging){if(!dialMoved){val radius=dialRect.height()*.70;val offset=(Math.toDegrees(asin(((y-dialRect.centerY())/radius).coerceIn(-1.0,1.0)))/8.5).roundToInt();detents.move(offset.toFloat());selectPurpose(detents.index)};settleDial()};if(!dialDragging&&hypot(x-down.x,y-down.y)<12){performClick();if(pressed.isNotEmpty())PressFeedback.click(this);when(pressed){"gear"->if(gearRect.contains(x,y))onSettings?.invoke();"palette"->if(paletteRect.contains(x,y))onPlace?.invoke();"place"->if(placeRect.contains(x,y))onPlace?.invoke()}};dialDragging=false;pressed="";press(0f);isPressed=false;invalidate();return true}
        };return true
    }
    override fun performClick():Boolean=super.performClick()
    override fun onDetachedFromWindow(){dialAnimator?.cancel();pressAnimator?.cancel();super.onDetachedFromWindow()}
}

class WeatherDashboard(context:Context,model:AppModel):Dashboard(context,model){
    private var metrics:DesignMetrics?=null
    private var metricsKey=""
    private val icons=org.json.JSONObject(context.assets.open("layouts/icons.json").bufferedReader().use{it.readText()})
    private val nativeInk=Color.rgb(21,29,28)
    private fun css(key:String,value:String,index:Int=0,overrideRect:RectF?=null,sizeFactor:Float=1f,maxRight:Float?=null):Float {
        val m=metrics!!;val r=overrideRect?:m.rect(key,index);if(r.isEmpty)return 0f
        val baseSize=m.size(key,index)
        val font=when{value.any{it.code in 0x3400..0x9fff}->chinese;m.font(key,index).contains("Roboto")->mono;else->inter}
        p.reset();p.isAntiAlias=true;p.color=nativeInk;p.typeface=Typeface.create(font,m.weight(key,index),false);p.textSize=baseSize*sizeFactor
        p.letterSpacing=m.spacing(key,index)/baseSize
        val measured=p.measureText(value)
        val factor=if(maxRight!=null&&measured>maxRight-r.left)sizeFactor*(maxRight-r.left).coerceAtLeast(1f)/max(1f,measured)else sizeFactor
        val size=baseSize*factor;p.textSize=size
        val fm=p.fontMetrics;val line=m.line(key,index)*(if(maxRight==null)factor else 1f)
        val baseline=r.top+(line-(fm.descent-fm.ascent))/2-fm.ascent
        p.textAlign=Paint.Align.LEFT;c!!.drawText(value,r.left,baseline,p)

        return p.measureText(value)
    }
    private fun svg(key:String,r:RectF,vw:Float=32f,vh:Float=32f,stroke:Float=2.8f){
        if(r.isEmpty)return
        val xml=if(key=="gear")"<g><path d=\"M13 4h6l.8 3.2 2.4 1.4 3.2-.9 3 5.2-2.4 2.3v2.8l2.4 2.3-3 5.2-3.2-.9-2.4 1.4L19 29h-6l-.8-3.2-2.4-1.4-3.2.9-3-5.2L6 17.8V15l-2.4-2.3 3-5.2 3.2.9 2.4-1.4Z\"/><circle cx=\"16\" cy=\"16.5\" r=\"4.5\"/></g>" else icons.optString(key)
        if(xml.isEmpty())return
        val scale=min(r.width()/vw,r.height()/vh);c!!.save();c!!.translate(r.centerX()-vw*scale/2,r.centerY()-vh*scale/2);c!!.scale(scale,scale)
        val parser=android.util.Xml.newPullParser();parser.setInput(java.io.StringReader(xml))
        while(parser.eventType!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT){
            if(parser.eventType==org.xmlpull.v1.XmlPullParser.START_TAG){
                p.reset();p.isAntiAlias=true;p.color=nativeInk;p.style=Paint.Style.STROKE;p.strokeWidth=stroke/scale;p.strokeJoin=Paint.Join.ROUND;p.strokeCap=Paint.Cap.BUTT
                if(parser.name=="path"){
                    val path=androidx.core.graphics.PathParser.createPathFromPathData(parser.getAttributeValue(null,"d"))
                    if(path!=null){if(parser.getAttributeValue(null,"fill")=="var(--paper)"){p.style=Paint.Style.FILL;p.color=PAPER;c!!.drawPath(path,p);p.style=Paint.Style.STROKE;p.color=nativeInk};c!!.drawPath(path,p)}
                }else if(parser.name=="circle"){val x=parser.getAttributeValue(null,"cx").toFloat();val y=parser.getAttributeValue(null,"cy").toFloat();val radius=parser.getAttributeValue(null,"r").toFloat();c!!.drawCircle(x,y,radius,p)}
            };parser.next()
        };c!!.restore()
    }
    override fun drawDashboard(){
        val key="weather-${if(portrait)"portrait" else "landscape"}-${model.lang}"
        if(metricsKey!=key){metrics=DesignMetrics(context,key);metricsKey=key}
        val m=metrics!!;val inset=(context as MainActivity).root.paddingTop/density;m.configure(w,h,inset)
        val page=m.rect(".weather-page");val info=m.rect(".information-panel");artRect=m.rect(".visual-panel");frameOutline=m.outline(".weather-page",page)
        rect(info,PAPER)
        val wx=model.weather;val scene=wx?.scene()?:"unknown"
        val stroke=(info.width()*.0034f).coerceIn(2f,3.4f)
        gearRect=m.rect("#settings-open");paletteRect=RectF();placeRect=m.rect(".heading-bottom").let{if(it.isEmpty)RectF(info.left,info.top+90,info.right,info.top+160)else it}
        css("#weather-title .label-primary",tr("今日天气","TODAY’S WEATHER"))
        val dotColors=listOf(ORANGE,YELLOW,BLUE)
        for(i in 0..2){val r=m.rect(".palette-dots i",i);circle(r.centerX(),r.centerY(),r.width()/2,dotColors[i])}
        svg("gear",m.rect("#settings-open svg"),stroke=stroke)
        css("#location-label",model.placeName(),maxRight=m.rect("#scene-date").left-24)
        val stamp=wx?.let{java.time.Instant.ofEpochSecond(it.validAt).atZone(it.zone)}
        val date=stamp?.let{if(model.lang=="zh")"${it.monthValue}月${it.dayOfMonth}日" else String.format(java.util.Locale.US,"%02d/%02d/%d",it.dayOfMonth,it.monthValue,it.year)}?:"—"
        css("#scene-date",date);css("#scene-clock",wx?.clock(wx.validAt)?:"—")
        if(wx?.aqi!=null){val r=m.rect("#air-quality");circle(r.left+4,r.centerY(),3.5f,if(wx.aqi<=100)0xff6c8c63.toInt()else ORANGE)}
        val header=m.rect(".panel-heading");line(info.left,header.bottom-1,info.right,header.bottom-1,0xffbec6b8.toInt())
        val tempR=m.rect(".temperature-number");val number=model.temp(wx?.value("temperature_2m"));val factor=if(number.length>2).88f else 1f
        val numberWidth=css(".temperature-number",number,sizeFactor=factor)
        val degree=m.rect(".temperature-degree");val degreeMoved=RectF(degree).apply{offset(tempR.left+numberWidth+tempR.width()*.02f-left,0f)}
        css(".temperature-degree",model.tempUnit,overrideRect=degreeMoved,sizeFactor=factor)
        val day=wx?.today();css(".temperature-range","↑ ${model.temp(day?.high)}°  ↓ ${model.temp(day?.low)}°")
        val icon=when(scene){"clear"->"sun";"night"->"moon";"dawn"->"sunrise";"sunset"->"sunset";else->scene}
        svg(icon,m.rect("#condition-icon"),stroke=stroke)
        css("#condition-primary",model.conditionName(wx),maxRight=m.rect("#solar-event-time").left-24)
        val event=wx?.nextSolar();css("#solar-event-label .label-primary",if(event==null)tr("暂无事件","NO EVENT")else if(event.first)tr("日出时间","SUNRISE")else tr("日落时间","SUNSET"))
        val solar=event?.second?.let{java.time.Instant.ofEpochSecond(it).atZone(wx!!.zone)}
        val solarClock=if(model.lang=="en")solar?.format(java.time.format.DateTimeFormatter.ofPattern("h:mm",java.util.Locale.US))?:"—"else wx?.clock(event?.second)?:"—"
        css("#solar-event-time",solarClock)
        if(model.lang=="en"&&solar!=null)css("#solar-period",if(solar.hour>=12)"pm"else "am")
        if(solar!=null&&stamp!=null&&solar.toLocalDate()!=stamp.toLocalDate()){
            val sr=m.rect("#solar-event-time");val heroBounds=m.rect(".weather-hero")
            text(tr("次日","Tomorrow"),sr.left,min(sr.bottom+2,heroBounds.bottom-20),16f,0xff67716a.toInt(),languageFont(tr("次日","Tomorrow")))
        }
        if(event!=null)svg(if(event.first)"solarSunrise"else "solarSunset",m.rect("#solar-icon"),172f,100f,stroke)
        val hero=m.rect(".weather-hero");line(info.left,hero.bottom-1,info.right,hero.bottom-1,0xffbec6b8.toInt())
        val facts=listOf(tr("体感温度","FEELS LIKE"),tr("风速","WIND SPEED"))
        for(i in 0..1){val r=m.rect(".primary-metric",i);css(".primary-metric .label-primary",facts[i],i)
            val value=if(i==0)model.temp(wx?.value("apparent_temperature"))else model.speed(wx?.value("wind_speed_10m"))
            val vr=m.rect(".primary-metric p",i);val valueWidth=css(".primary-metric p",value,i)
            val unitKey=if(i==0)".primary-metric .metric-degree"else ".primary-metric .metric-unit";val ur=m.rect(unitKey);if(!ur.isEmpty){ur.offset(vr.left+valueWidth+6-ur.left,0f);css(unitKey,if(i==0)"°"else model.windUnit,overrideRect=ur)}
            line(r.left,r.bottom,r.right,r.bottom,0xffbec6b8.toInt());if(i==0)line(r.right,r.top,r.right,r.bottom,0xffbec6b8.toInt())
        }
        val chance=wx?.precip();val values=listOf(Units.number(wx?.value("uv_index")),Units.number(wx?.value("relative_humidity_2m")),Units.number(chance?.second),Units.number(wx?.value("visibility")?.div(1000),if(wx?.value("visibility")?.rem(1000.0)==0.0)0 else 1))
        val labels=listOf(tr("紫外线指数","UV INDEX"),tr("相对湿度","HUMIDITY"),tr("降水概率","PRECIP CHANCE"),tr("能见度","VISIBILITY"))
        for(i in 0..3){val r=m.rect(".condition",i);css(".condition .label-primary",labels[i],i)
            val vr=m.rect(".condition p",i);val valueWidth=css(".condition p",values[i],i)
            val sub=when(i){0->{val uv=wx?.value("uv_index");if(uv==null)""else if(uv>=11)tr("极高","Extreme")else if(uv>=8)tr("很高","Very high")else if(uv>=6)tr("高","High")else if(uv>=3)tr("中等","Moderate")else tr("低","Low")};1->"%";2->"% "+(chance?.let{wx!!.clock(it.first-3600).take(2).toInt().toString()+"–"+wx.clock(it.first).take(2).toInt()+(if(model.lang=="zh")"时"else "h") }?:"");else->"km"}
            val subSize=m.size(".condition p",i)*(if(i==0).44f else .65f)
            p.reset();p.isAntiAlias=true;p.color=nativeInk;p.textSize=subSize;p.typeface=if(sub.any{it.code in 0x3400..0x9fff})chinese else inter
            val baseline=vr.top+(m.line(".condition p",i)-(p.fontMetrics.descent-p.fontMetrics.ascent))/2-p.fontMetrics.ascent
            c!!.drawText(sub,vr.left+valueWidth+6,baseline,p)
            val g=m.rect(".condition .gauge",i)
            val colors=when(i){0->intArrayOf(ORANGE,YELLOW,0xffc1d6eb.toInt(),BLUE);1->intArrayOf(BLUE,0xff9dc5ec.toInt(),0xfff4f8fd.toInt());2->intArrayOf(0xff0878e8.toInt(),0xffa6c5df.toInt(),YELLOW);else->intArrayOf(YELLOW,0xffd1dfeb.toInt(),BLUE)}
            p.shader=LinearGradient(g.left,g.top,g.left,g.bottom,colors,null,Shader.TileMode.CLAMP);c!!.drawRoundRect(g,g.width()/2,g.width()/2,p);p.shader=null
            val fraction=when(i){0->wx?.value("uv_index")?.div(11);1->wx?.value("relative_humidity_2m")?.div(100);2->chance?.second?.div(100);else->wx?.value("visibility")?.div(50000)}
            fraction?.let{f->val y=g.bottom-g.height()*f.toFloat().coerceIn(0f,1f);line(g.centerX()-info.width()*.021f,y,g.centerX()+info.width()*.021f,y,nativeInk,max(2f,info.width()*.0025f))}
            line(r.left,r.bottom,r.right,r.bottom,0xffbec6b8.toInt());if(i%2==0)line(r.right,r.top,r.right,r.bottom,0xffbec6b8.toInt())
        }
        if(!portrait){
            css(".cloud-cover .label-primary",tr("云量","CLOUD COVER"));val track=m.rect(".cover-track");val radius=info.width()*.014f
            p.reset();p.isAntiAlias=true;p.shader=LinearGradient(track.left,0f,track.right,0f,intArrayOf(0xfffff2bf.toInt(),0xffeef1f2.toInt(),0xffc8e7fd.toInt()),null,Shader.TileMode.CLAMP);c!!.drawRoundRect(track,radius,radius,p);p.shader=null
            wx?.value("cloud_cover")?.let{cloud->val fill=RectF(track.left,track.top,track.left+track.width()*cloud.toFloat()/100,track.bottom);p.shader=LinearGradient(track.left,0f,track.right,0f,intArrayOf(YELLOW,0xffffbf47.toInt(),0xffb8cadf.toInt(),BLUE),null,Shader.TileMode.CLAMP);c!!.drawRoundRect(fill,radius,radius,p);p.shader=null}
            val cover=m.rect(".cover-reading");val number=Units.number(wx?.value("cloud_cover"));val size=m.size(".cover-reading");p.reset();p.isAntiAlias=true;p.typeface=inter;p.textSize=size;p.color=nativeInk;val baseline=cover.centerY()-(p.fontMetrics.ascent+p.fontMetrics.descent)/2;c!!.drawText(number,cover.left,baseline,p);val nw=p.measureText(number);p.textSize=size*.52f;c!!.drawText("%",cover.left+nw+4,baseline,p)
        }
        // Preserve the exact approved outer corners; artwork itself remains a live EGL surface.
        val mask=Path().apply{fillType=Path.FillType.EVEN_ODD;addRect(0f,0f,w,h,Path.Direction.CW);addPath(frameOutline!!)}
        p.reset();p.isAntiAlias=true;p.color=0xff191e1d.toInt();c!!.drawPath(mask,p)
        if(wx==null||wx.stale(System.currentTimeMillis()/1000)||model.status=="offline"){text(model.source(),artRect.left+16,artRect.bottom-32,16f,PAPER,mono)}
    }
}

class NativePage(context:Context,val dashboard:Dashboard,val art:PixelArt):android.widget.ScrollView(context){
    private val layer=FrameLayout(context)
    private var contentHeight=0
    private var dialTouch=false
    fun pageMotion(progress:Float,engaged:Boolean){
        val fraction=if(ValueAnimator.areAnimatorsEnabled())progress.coerceIn(0f,1f)else 0f;val d=resources.displayMetrics.density
        val type=if(engaged)View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE
        if(dashboard.layerType!=type)dashboard.setLayerType(type,null)
        layer.pivotX=width/2f;layer.pivotY=scrollY+height/2f
        layer.scaleX=1f-fraction*.006f;layer.scaleY=1f-fraction*.006f;layer.translationY=fraction*3*d
    }
    override fun onMeasure(widthSpec:Int,heightSpec:Int){
        val d=resources.displayMetrics.density;val width=MeasureSpec.getSize(widthSpec);val height=MeasureSpec.getSize(heightSpec)
        val widthDp=width/d;val heightDp=height/d
        val fallback=dashboard is ObservationDashboard&&height>width&&widthDp>700&&heightDp<=1550
        contentHeight=if(fallback)max(height,(1704f*max(1f,1136f/widthDp)*d).roundToInt())else height
        layer.layoutParams.height=contentHeight
        layer.minimumHeight=contentHeight
        super.onMeasure(widthSpec,heightSpec)
    }
    override fun onInterceptTouchEvent(e:MotionEvent):Boolean {
        if(e.actionMasked==MotionEvent.ACTION_DOWN)dialTouch=dashboard.dialRect.contains(e.x/resources.displayMetrics.density,(e.y+scrollY)/resources.displayMetrics.density)
        if(dialTouch||contentHeight<=height)return false
        return super.onInterceptTouchEvent(e)
    }
    private val caption=android.widget.TextView(context).apply{setTextColor(PAPER);textSize=16f;setPadding(12,4,12,4);setBackgroundColor(0x80000000.toInt())}
    init {isFillViewport=true;overScrollMode=OVER_SCROLL_NEVER;setBackgroundColor(if(dashboard is WeatherDashboard)0xff191e1d.toInt()else FRAME);addView(layer,LayoutParams(-1,-1));layer.addView(dashboard,FrameLayout.LayoutParams(-1,-1));layer.addView(art);layer.addView(caption);clipChildren=true;art.clipToOutline=true;art.outlineProvider=object:android.view.ViewOutlineProvider(){override fun getOutline(v:View,outline:android.graphics.Outline){outline.setRoundRect(0,0,v.width,v.height,dashboard.artCornerRadius*resources.displayMetrics.density)}}}
    fun updateState(){
        val m=dashboard.model;val wx=m.weather
        val known=wx!=null&&(dashboard !is ObservationDashboard||wx.scene(m.window(wx).start)!="unknown")
        art.visibility=if(known)View.VISIBLE else View.INVISIBLE
        val source=if(dashboard is ObservationDashboard&&!known)m.tr("该时段无天气数据","No weather data for this window")else m.source()
        if(caption.text.toString()!=source)caption.text=source
        caption.visibility=if(dashboard is ObservationDashboard&&known)View.GONE else View.VISIBLE
    }

    override fun dispatchDraw(canvas:Canvas){
        if(dashboard is WeatherDashboard&&dashboard.frameOutline!=null){
            val d=resources.displayMetrics.density
            val path=Path(dashboard.frameOutline!!).apply{transform(Matrix().apply{setScale(d,d)})}
            canvas.save();canvas.clipPath(path);super.dispatchDraw(canvas)
            val border=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=0xff828981.toInt();style=Paint.Style.STROKE;strokeWidth=2*d};canvas.drawPath(path,border);canvas.restore()
        }else super.dispatchDraw(canvas)
    }
    override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int){
        super.onLayout(changed,l,t,r,b)
        dashboard.layout(0,0,width,contentHeight)
        // Geometry is derived from the actual dp viewport, then converted once to pixels.
        val bitmap=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);dashboard.draw(Canvas(bitmap));bitmap.recycle()
        val d=resources.displayMetrics.density;val a=dashboard.artRect
        val artLeft=(a.left*d).roundToInt();val artTop=(a.top*d).roundToInt();val artRight=(a.right*d).roundToInt();val artBottom=(a.bottom*d).roundToInt()
        art.measure(MeasureSpec.makeMeasureSpec(artRight-artLeft,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(artBottom-artTop,MeasureSpec.EXACTLY))
        art.layout(artLeft,artTop,artRight,artBottom);art.invalidateOutline()
        caption.measure(MeasureSpec.makeMeasureSpec((a.width()*d).toInt(),MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(80,MeasureSpec.AT_MOST));caption.layout((a.left*d).toInt()+24,(a.bottom*d).toInt()-caption.measuredHeight-20,(a.left*d).toInt()+24+caption.measuredWidth,(a.bottom*d).toInt()-20)
    }
}
