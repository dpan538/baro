package studio.sundown.weather

import android.graphics.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

/** Controlled model scenarios rendered by actual native Canvas/fonts, not live weather captures. */
class NativeConditionsTest {
    companion object { @org.junit.BeforeClass @JvmStatic fun environment(){NativeTestEnvironment.install()} }
    @Test fun adviceAndLightInstrumentsStayReadableAcrossPurposes(){
        val i=InstrumentationRegistry.getInstrumentation();val records=JSONArray();var errors=0
        ActivityScenario.launch(MainActivity::class.java).use{s->s.onActivity{a->
            a.repository.cancelWeather();a.model.motion=false;a.model.page=1
            val place=Place("Brisbane",-27.47,153.03,"Australia/Brisbane")
            fun at(hour:Int)=LocalDateTime.parse(String.format(java.util.Locale.US,"2026-10-25T%02d:30",hour)).atZone(ZoneId.of(place.zone)).toEpochSecond()
            val base=mapOf("temperature_2m" to 20.0,"relative_humidity_2m" to 55.0,"dew_point_2m" to 10.0,"weather_code" to 0.0,
                "wind_speed_10m" to 5.0,"wind_gusts_10m" to 8.0,"cloud_cover" to 20.0,"cloud_cover_low" to 10.0,"visibility" to 20000.0,
                "soil_moisture_0_to_1cm" to .2,"precipitation_probability" to 0.0,"direct_radiation_instant" to 520.0,"diffuse_radiation_instant" to 80.0)
            val scenarios=mapOf("direct" to (12 to base),"diffuse" to (12 to (base+mapOf("direct_radiation_instant" to 20.0,"diffuse_radiation_instant" to 180.0))),
                "wet" to (12 to (base+mapOf("soil_moisture_0_to_1cm" to .45))),"dew" to (5 to (base+mapOf("dew_point_2m" to 19.0,"relative_humidity_2m" to 95.0,"soil_moisture_0_to_1cm" to .4))),
                "night" to (22 to (base+mapOf("direct_radiation_instant" to 0.0,"diffuse_radiation_instant" to 0.0))),"missing" to (12 to emptyMap()),
                "cycling-night" to (22 to (base+mapOf("weather_code" to 80.0,"visibility" to 3300.0,"cape" to 70.0,"convective_inhibition" to 11.0,"soil_moisture_0_to_1cm" to .39,"direct_radiation_instant" to 0.0,"diffuse_radiation_instant" to 0.0))),
                "convective" to (12 to (base+mapOf("weather_code" to 95.0,"visibility" to 800.0,"cape" to 2500.0,"convective_inhibition" to 150.0,"wind_gusts_10m" to 50.0))))
            data class Run(val text:String,val rect:RectF)
            for((scenario,sample)in scenarios)for(lang in listOf("zh","en"))for(portrait in listOf(false,true))for(purpose in 0..11){
                val epoch=at(sample.first);val times=(0L..60L).map{epoch+it*3600}
                a.model.lang=lang;a.model.purpose=purpose;a.model.place=place;a.model.now=epoch
                a.model.weather=Weather(place,epoch,epoch,sample.second,Series(times,sample.second.mapValues{(_,v)->times.map{v}}),emptyList(),null,null,null)
                val width=if(portrait)2272 else 3408;val height=if(portrait)3408 else 2272
                val board=ObservationDashboard(a,a.model);board.layout(0,0,width,height)
                val scale=board.coordinateScale
                val metrics=DesignMetrics(a,"observation-${if(portrait)"portrait"else "landscape"}-$lang-$purpose");metrics.configure(board.w,board.h,0f)
                val nodes=listOf("ins-bearing","ins-radiation","ins-stage","ins-optical","ins-advice").associateWith{metrics.rect(it).apply{left*=scale;top*=scale;right*=scale;bottom*=scale}}
                val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                val runs=mutableListOf<Run>();val issues=JSONArray()
                val canvas=object:Canvas(bitmap){
                    fun record(value:String,x:Float,y:Float,paint:Paint){
                        if(value.isBlank()||paint.alpha==0)return
                        val bounds=Rect();paint.getTextBounds(value,0,value.length,bounds);val advance=paint.measureText(value)
                        val xx=x-when(paint.textAlign){Paint.Align.RIGHT->advance;Paint.Align.CENTER->advance/2;else->0f}
                        val rect=RectF(bounds).apply{offset(xx,y)};val matrix=Matrix();getMatrix(matrix);matrix.mapRect(rect)
                        if(nodes.values.none{it.contains(rect.centerX(),rect.centerY())})return
                        runs.add(Run(value,rect))
                        val owner=nodes.entries.first{it.value.contains(rect.centerX(),rect.centerY())}
                        if(!owner.value.contains(RectF(rect).apply{inset(.5f,.5f)}))issues.put("Out of ${owner.key}: $value")
                        if(value.contains('…'))issues.put("Ellipsis: $value")
                    }
                    override fun drawText(text:String,x:Float,y:Float,paint:Paint){record(text,x,y,paint);super.drawText(text,x,y,paint)}
                    override fun drawText(text:CharArray,index:Int,count:Int,x:Float,y:Float,paint:Paint){record(String(text,index,count),x,y,paint);super.drawText(text,index,count,x,y,paint)}
                    override fun drawTextRun(text:CharSequence,start:Int,end:Int,contextStart:Int,contextEnd:Int,x:Float,y:Float,isRtl:Boolean,paint:Paint){record(text.subSequence(start,end).toString(),x,y,paint);super.drawTextRun(text,start,end,contextStart,contextEnd,x,y,isRtl,paint)}
                    override fun drawTextRun(text:CharArray,index:Int,count:Int,contextIndex:Int,contextCount:Int,x:Float,y:Float,isRtl:Boolean,paint:Paint){record(String(text,index,count),x,y,paint);super.drawTextRun(text,index,count,contextIndex,contextCount,x,y,isRtl,paint)}
                }
                board.draw(canvas)
                for(x in runs.indices)for(y in x+1 until runs.size){val one=runs[x];val two=runs[y];val overlap=RectF(one.rect)
                    if(overlap.intersect(two.rect)&&overlap.width()>4&&overlap.height()>minOf(one.rect.height(),two.rect.height())*.25f)issues.put("Overlap: ${one.text} / ${two.text}")
                }
                val selected=a.model.window(a.model.weather!!);val content=conditionsAdvice(a.model.weather,purpose,selected.start,lang,selected.available)
                // StaticLayout can draw through the MeasuredText Canvas overload,
                // so inspect the actual returned layout rather than missing runs.
                val layout=board.adviceTextLayout!!
                if(layout.text.toString()!=content.text||layout.getLineEnd(layout.lineCount-1)!=content.text.length||
                    (0 until layout.lineCount).any{layout.getEllipsisCount(it)>0}||layout.height>board.adviceTextBounds.height()+1)
                    issues.put("Incomplete advice (${layout.height}/${board.adviceTextBounds.height()}, ${layout.lineCount} lines): ${content.text}")
                if(scenario in listOf("direct","diffuse")){
                    val radiation=runs.filter{nodes.getValue("ins-radiation").contains(it.rect.centerX(),it.rect.centerY())}.joinToString(" "){it.text}
                    if(!radiation.contains(if(lang=="zh")"主导"else "-led"))issues.put("Daylight interpretation absent: $radiation")
                }
                records.put(JSONObject().put("scenario",scenario).put("lang",lang).put("portrait",portrait).put("purpose",purpose).put("text",content.text).put("lines",layout.lineCount).put("lastLineFraction",layout.getLineWidth(layout.lineCount-1)/layout.width).put("reasons",JSONArray(content.reasons)).put("issues",issues));errors+=issues.length()
                if((scenario=="direct"&&purpose==4)||(scenario=="wet"&&purpose==3)||(scenario=="dew"&&purpose==5)||(scenario in listOf("cycling-night","convective")&&purpose==2)){
                    File(i.targetContext.getExternalFilesDir(null),"conditions-controlled-$scenario-${if(portrait)"portrait"else"landscape"}-$lang.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                }
                bitmap.recycle()
            }
        }}
        File(i.targetContext.getExternalFilesDir(null),"conditions-native-audit.json").writeText(records.toString(2))
        assertEquals("Inspect conditions-native-audit.json for actual glyph/layout failures",0,errors)
    }
}
