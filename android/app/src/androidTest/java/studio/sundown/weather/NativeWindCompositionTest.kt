package studio.sundown.weather

import android.content.Context
import android.graphics.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Checks actual drawn ink/strokes, rather than a layout rectangle that can hide a small dial. */
class NativeWindCompositionTest {
    @Test fun fullHeightWindDialsKeepOriginalTypeAndBalancedInkMargins(){
        val i=InstrumentationRegistry.getInstrumentation()
        i.targetContext.getSharedPreferences("baro-native",Context.MODE_PRIVATE).edit().putBoolean("motion",false).commit()
        val results=JSONArray();val issues=mutableListOf<String>()
        val bitmap=Bitmap.createBitmap(3408,4260,Bitmap.Config.ARGB_8888)
        ActivityScenario.launch(MainActivity::class.java).use{s->s.onActivity{a->
            a.repository.cancelWeather()
            val weather=WeatherJson.parse(i.context.assets.open("live-brisbane.json").bufferedReader().use{it.readText()},a.model.place)
            a.model.now=weather.validAt
            val originalTop=a.root.paddingTop
            val landscapeTop=a.window.decorView.rootWindowInsets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.statusBars()).top
            data class Run(val content:String,val ink:RectF,val size:Float)
            class Recorder:Canvas(bitmap){
                val runs=mutableListOf<Run>();val strokes=mutableListOf<RectF>()
                override fun drawText(text:String,x:Float,y:Float,paint:Paint){
                    if(text.isNotBlank()&&paint.alpha>0){
                        val bounds=Rect();paint.getTextBounds(text,0,text.length,bounds)
                        val advance=paint.measureText(text)
                        val left=x-when(paint.textAlign){Paint.Align.RIGHT->advance;Paint.Align.CENTER->advance/2;else->0f}
                        runs.add(Run(text,RectF(bounds).apply{offset(left,y)},paint.textSize))
                    };super.drawText(text,x,y,paint)
                }
                override fun drawLine(x:Float,y:Float,xx:Float,yy:Float,paint:Paint){
                    val edge=paint.strokeWidth/2
                    strokes.add(RectF(minOf(x,xx)-edge,minOf(y,yy)-edge,maxOf(x,xx)+edge,maxOf(y,yy)+edge))
                    super.drawLine(x,y,xx,yy,paint)
                }
            }
            for(lang in listOf("zh","en"))for(profile in listOf(Triple(false,1704,1136),Triple(true,1136,1704),Triple(true,909,1363)))for(primary in listOf(false,true))for(wind in listOf("kmh","mph"))for(stress in listOf(false,true)){
                val (portrait,width,height)=profile
                a.model.lang=lang;a.model.purpose=if(primary)2 else 0;a.model.wind=wind
                val high=mapOf("wind_gusts_10m" to 250.0,"wind_speed_10m" to 125.0,"wind_direction_10m" to 359.0)
                a.model.weather=if(stress)weather.copy(current=weather.current+high,hourly=weather.hourly.copy(fields=weather.hourly.fields+high.mapValues{(_,v)->weather.hourly.times.map{v}}))else weather
                val d=a.resources.displayMetrics.density
                val inset=if(portrait)0 else landscapeTop;a.root.setPadding(0,inset,0,0)
                val board=ObservationDashboard(a,a.model);board.layout(0,0,(width*d).toInt(),(height*d).toInt()-inset)
                val canvas=Recorder();board.draw(canvas)
                val m=DesignMetrics(a,"observation-${if(portrait)"portrait"else "landscape"}-$lang-${a.model.purpose}");m.configure(board.w,board.h,inset/board.coordinateScale)
                val key=if(primary)"ins-focus"else "ins-wind";val module=m.rect(key)
                val runs=canvas.runs.filter{module.contains(it.ink)}
                val headingText=a.model.tr(if(primary)"阵风"else "风速",if(primary)"Wind gust"else "Wind")
                val headingRuns=runs.filter{(it.content==headingText||it.content in headingText.split(' '))&&abs(it.size-m.size("$key:heading"))<.1f}
                assertTrue("Missing complete heading: $lang/$width/$primary",headingRuns.isNotEmpty()&&headingRuns.joinToString(" "){it.content}==headingText)
                val headingInk=RectF();headingRuns.forEach{headingInk.union(it.ink)}
                val factPrefix=a.model.tr(if(primary)"风速"else "阵风",if(primary)"Mean"else "Gust")
                val footer=runs.first{it.content.startsWith(factPrefix)}
                val from=runs.first{it.content.startsWith(a.model.tr("来向","From"))}
                val tickInk=RectF()
                // The 72 short radial strokes surround a full-height wind dial; exclude needle shafts.
                val ticks=canvas.strokes.filter{module.contains(it)&&it.width()<module.height()*.18f&&it.height()<module.height()*.18f}
                ticks.forEach{tickInk.union(it)}
                val ratio=tickInk.height()/module.height()
                val topGap=headingInk.top-module.top
                val bottomGap=module.bottom-runs.filter{it.ink.top>=footer.ink.top}.maxOf{it.ink.bottom}
                val identity="$lang/$portrait/$width/$primary/$wind/$stress"
                if(ratio<.85f)issues+="$identity dial occupies $ratio of card height"
                if(abs(topGap-bottomGap)>1f)issues+="$identity unequal title/footer ink gaps $topGap / $bottomGap"
                if(headingRuns.any{it.size+.1f<m.size("$key:heading")})issues+="$identity heading was shrunk"
                val number=a.model.speed(a.model.weather!!.value(if(primary)"wind_gusts_10m"else "wind_speed_10m",a.model.window(a.model.weather!!).start))
                val reading=runs.first{it.content==number}
                if(reading.size+.1f<m.size("$key:reading"))issues+="$identity number was shrunk"
                if(canvas.strokes.any{module.contains(it)&&RectF.intersects(it,from.ink)})issues+="$identity direction text intersects compass strokes"
                results.put(JSONObject().put("case",identity).put("card",module.toString()).put("dialInk",tickInk.toString()).put("dialHeightFraction",ratio).put("titleTopGap",topGap).put("footerBottomGap",bottomGap).put("headingSize",headingRuns.minOf{it.size}).put("headingRows",headingRuns.size).put("readingSize",reading.size))
            }
            a.root.setPadding(0,originalTop,0,0)
        }}
        bitmap.recycle()
        File(i.targetContext.getExternalFilesDir(null),"wind-composition-audit.json").writeText(JSONObject().put("cases",results).put("issues",JSONArray(issues)).toString())
        assertTrue(issues.take(12).joinToString("\n"),issues.isEmpty())
    }
}
