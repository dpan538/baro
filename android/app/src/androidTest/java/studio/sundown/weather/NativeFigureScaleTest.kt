package studio.sundown.weather

import android.content.Context
import android.graphics.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.*

/** Actual figure strokes/fills must remain large; text and card backgrounds do not count. */
class NativeFigureScaleTest {
    @Test fun everyMainInstrumentFigureUsesAtLeastEightyFivePercentHeight(){
        val i=InstrumentationRegistry.getInstrumentation()
        i.targetContext.getSharedPreferences("baro-native",Context.MODE_PRIVATE).edit().putBoolean("motion",false).commit()
        val bitmap=Bitmap.createBitmap(3408,4260,Bitmap.Config.ARGB_8888)
        val results=JSONArray();val issues=mutableListOf<String>()
        ActivityScenario.launch(MainActivity::class.java).use{s->s.onActivity{a->
            a.repository.cancelWeather()
            val recorded=WeatherJson.parse(i.context.assets.open("live-brisbane.json").bufferedReader().use{it.readText()},a.model.place)
            a.model.now=recorded.validAt
            val top=a.root.paddingTop
            val landscapeTop=a.window.decorView.rootWindowInsets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.statusBars()).top
            for(lang in listOf("zh","en"))for(profile in listOf(Triple(false,1704,1136),Triple(true,1136,1704),Triple(true,909,2130)))for(purpose in 0..11){
                val (portrait,width,height)=profile
                a.model.lang=lang;a.model.purpose=purpose;a.model.weather=recorded
                val d=a.resources.displayMetrics.density
                val inset=if(portrait)0 else landscapeTop;a.root.setPadding(0,inset,0,0)
                val m=DesignMetrics(a,"observation-${if(portrait)"portrait"else "landscape"}-$lang-$purpose");m.configure(width.toFloat(),height-inset/d,inset/d)
                val names=mutableListOf("ins-wind","ins-pressure","ins-dew","ins-moisture","ins-clouds","ins-column","ins-soil","ins-solar","ins-bearing","ins-radiation","ins-drift","ins-rain")
                if(purpose!=7)names.add("ins-lunar")
                if(purpose!=8)names.add("ins-focus")
                val modules=names.associateWith{m.rect(it)};val figures=mutableMapOf<String,RectF>()
                class Recorder:Canvas(bitmap){
                    fun shape(bounds:RectF,paint:Paint){
                        if(paint.alpha==0)return
                        for((name,module)in modules){
                            if(!RectF.intersects(bounds,module))continue
                            if(paint.style==Paint.Style.FILL&&bounds.width()>=module.width()*.95f&&bounds.height()>=module.height()*.95f)continue
                            val visible=RectF(bounds);if(!visible.intersect(module))continue
                            if(!module.contains(bounds.centerX(),bounds.centerY())&&name!="ins-bearing")continue
                            figures.getOrPut(name){RectF()}.union(visible)
                        }
                    }
                    override fun drawLine(x:Float,y:Float,xx:Float,yy:Float,paint:Paint){val edge=paint.strokeWidth/2;shape(RectF(min(x,xx)-edge,min(y,yy)-edge,max(x,xx)+edge,max(y,yy)+edge),paint);super.drawLine(x,y,xx,yy,paint)}
                    override fun drawRoundRect(r:RectF,rx:Float,ry:Float,paint:Paint){shape(r,paint);super.drawRoundRect(r,rx,ry,paint)}
                    override fun drawCircle(x:Float,y:Float,r:Float,paint:Paint){
                        var bounds=RectF(x-r,y-r,x+r,y+r)
                        val bearing=modules["ins-bearing"]!!
                        if(x>bearing.right&&y in bearing.top..bearing.bottom){val visibleHalf=sqrt(max(0f,r*r-(x-bearing.right).pow(2)));bounds=RectF(bearing.right-r,y-visibleHalf,bearing.right,y+visibleHalf)}
                        shape(bounds,paint);super.drawCircle(x,y,r,paint)
                    }
                    override fun drawArc(r:RectF,start:Float,sweep:Float,useCenter:Boolean,paint:Paint){val p=Path().apply{addArc(r,start,sweep)};val b=RectF();p.computeBounds(b,true);b.inset(-paint.strokeWidth/2,-paint.strokeWidth/2);shape(b,paint);super.drawArc(r,start,sweep,useCenter,paint)}
                    override fun drawPath(path:Path,paint:Paint){val b=RectF();path.computeBounds(b,true);shape(b,paint);super.drawPath(path,paint)}
                }
                val board=ObservationDashboard(a,a.model);board.layout(0,0,(width*d).toInt(),(height*d).toInt()-inset);board.draw(Recorder())
                for((name,module)in modules){
                    val figure=figures[name]?:RectF();val ratio=figure.height()/module.height()
                    if(ratio<.85f)issues+="$lang/$portrait/$purpose/$name: $ratio"
                    results.put(JSONObject().put("language",lang).put("portrait",portrait).put("widthDp",width).put("heightDp",height).put("purpose",purpose).put("component",name).put("card",module.toString()).put("figure",figure.toString()).put("heightFraction",ratio))
                }
            }
            a.root.setPadding(0,top,0,0)
        }}
        bitmap.recycle()
        File(i.targetContext.getExternalFilesDir(null),"main-figure-audit.json").writeText(JSONObject().put("cases",results).put("issues",JSONArray(issues)).toString())
        assertTrue(issues.take(20).joinToString("\n"),issues.isEmpty())
    }
}
